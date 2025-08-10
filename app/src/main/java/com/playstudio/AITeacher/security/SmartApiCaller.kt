package com.playstudio.aiteacher.security

import android.util.Log
import kotlinx.coroutines.delay
import okhttp3.*
import java.io.IOException

class SmartApiCaller {
    
    companion object {
        private const val TAG = "SmartApiCaller"
        private const val MAX_RETRIES = 2
    }
    
    private val keyManager = FirestoreKeyManager.getInstance()
    
    /**
     * Execute API call with automatic key refresh and retry on auth errors
     */
    suspend fun executeWithSmartRetry(
        okHttpClient: OkHttpClient,
        requestBuilder: () -> Request,
        onKeyRefresh: (() -> Request)? = null
    ): Response {
        
        var lastException: Exception? = null
        
        for (attempt in 0 until MAX_RETRIES) {
            try {
                // Build request (gets current cached key)
                val request = if (attempt == 0) {
                    requestBuilder()
                } else {
                    // Use refreshed request builder if provided, otherwise original
                    onKeyRefresh?.invoke() ?: requestBuilder()
                }
                
                Log.d(TAG, "API call attempt ${attempt + 1}")
                val response = okHttpClient.newCall(request).execute()
                
                // Check if we got auth error indicating expired key
                if (keyManager.isKeyExpiredError(response.code, response.message)) {
                    Log.w(TAG, "API key appears expired (${response.code}), refreshing keys...")
                    
                    // Close current response
                    response.close()
                    
                    // Try to refresh keys from Firestore
                    val refreshed = keyManager.forceRefreshKeys()
                    if (!refreshed) {
                        Log.e(TAG, "Failed to refresh keys from Firestore")
                        throw Exception("API key expired and refresh failed")
                    }
                    
                    Log.d(TAG, "Keys refreshed successfully, retrying...")
                    
                    // Add small delay before retry
                    delay(500)
                    continue
                }
                
                // Success or non-auth error
                return response
                
            } catch (e: IOException) {
                lastException = e
                Log.w(TAG, "Network error on attempt ${attempt + 1}: ${e.message}")
                
                // Don't retry on network errors, just rethrow
                throw e
            } catch (e: Exception) {
                lastException = e
                Log.w(TAG, "Error on attempt ${attempt + 1}: ${e.message}")
                
                // Add delay before retry
                if (attempt < MAX_RETRIES - 1) {
                    delay(1000)
                }
            }
        }
        
        // All retries failed
        Log.e(TAG, "All API retry attempts failed")
        throw lastException ?: Exception("API call failed after $MAX_RETRIES attempts")
    }
}