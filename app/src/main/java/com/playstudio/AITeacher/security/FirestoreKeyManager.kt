package com.playstudio.aiteacher.security

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class FirestoreKeyManager private constructor() {
    
    // In-memory cache for instant access (moved to instance level)
    private var cachedKeys: Map<String, String> = emptyMap()
    private var lastFetchTime: Long = 0
    private val firestore = FirebaseFirestore.getInstance()
    
    companion object {
        private const val KEYS_COLLECTION = "api_keys"
        private const val CONFIG_DOC = "active_config"
        private const val CACHE_DURATION = 30 * 60 * 1000L // 30 minutes
        
        @Volatile
        private var INSTANCE: FirestoreKeyManager? = null
        
        fun getInstance(): FirestoreKeyManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: FirestoreKeyManager().also { INSTANCE = it }
            }
        }
    }
    
    // Get key instantly from memory cache
    fun getApiKey(provider: String): String? {
        val key = cachedKeys[provider.lowercase()]
        android.util.Log.e("FirestoreKeyManager", "🔍 Getting key for provider: $provider")
        android.util.Log.e("FirestoreKeyManager", "🔍 Available cached keys: ${cachedKeys.keys}")
        android.util.Log.e("FirestoreKeyManager", "🔍 Cache size: ${cachedKeys.size}")
        android.util.Log.e("FirestoreKeyManager", "🔍 Key found: ${key?.takeLast(4) ?: "null"}")
        return key
    }
    
    // One-time fetch on app start - stores in memory
    suspend fun fetchAndCacheKeys(): Boolean {
        return try {
            android.util.Log.e("FirestoreKeyManager", "🔄 Starting to fetch keys from Firestore...")
            val document = firestore.collection(KEYS_COLLECTION)
                .document(CONFIG_DOC)
                .get()
                .await()
            
            android.util.Log.e("FirestoreKeyManager", "📄 Document exists: ${document.exists()}")
            
            if (document.exists()) {
                val rawData = document.data
                android.util.Log.e("FirestoreKeyManager", "📊 Raw document data: $rawData")
                
                val keys = rawData?.mapKeys { it.key.lowercase().trim() } ?: emptyMap()
                android.util.Log.e("FirestoreKeyManager", "🔑 Keys after mapping: $keys")
                
                cachedKeys = keys.mapValues { 
                    val originalValue = it.value.toString()
                    val cleanedValue = originalValue
                        .replace("\\s".toRegex(), "") // Remove all whitespace characters including newlines
                        .trim()
                    android.util.Log.e("FirestoreKeyManager", "🧹 Cleaning key ${it.key}: '${originalValue.takeLast(10)}' -> '${cleanedValue.takeLast(4)}'")
                    cleanedValue
                }
                android.util.Log.e("FirestoreKeyManager", "💾 Cached keys: ${cachedKeys.keys}")
                android.util.Log.e("FirestoreKeyManager", "💾 OpenAI key cached: ${cachedKeys["openai"]?.takeLast(4)}")
                
                lastFetchTime = System.currentTimeMillis()
                true
            } else {
                android.util.Log.e("FirestoreKeyManager", "❌ Document does not exist")
                false
            }
        } catch (e: Exception) {
            android.util.Log.e("FirestoreKeyManager", "💥 Exception during fetch: ${e.message}", e)
            false
        }
    }
    
    // Background refresh if needed
    suspend fun refreshKeysIfNeeded(): Boolean {
        return if (System.currentTimeMillis() - lastFetchTime > CACHE_DURATION) {
            fetchAndCacheKeys()
        } else {
            true
        }
    }
    
    // Check if we have cached keys
    fun hasValidCache(): Boolean {
        return cachedKeys.isNotEmpty() && 
               (System.currentTimeMillis() - lastFetchTime) < CACHE_DURATION
    }
    
    // Force refresh keys (called when API returns 401/403)
    suspend fun forceRefreshKeys(): Boolean {
        return try {
            android.util.Log.e("FirestoreKeyManager", "🔄 Force refreshing keys...")
            val document = firestore.collection(KEYS_COLLECTION)
                .document(CONFIG_DOC)
                .get()
                .await()
            
            if (document.exists()) {
                val keys = document.data?.mapKeys { it.key.lowercase().trim() } ?: emptyMap()
                cachedKeys = keys.mapValues { 
                    it.value.toString()
                        .replace("\\s".toRegex(), "") // Remove all whitespace characters including newlines
                        .trim()
                }
                lastFetchTime = System.currentTimeMillis()
                android.util.Log.e("FirestoreKeyManager", "✅ Force refresh successful, cached ${cachedKeys.size} keys")
                true
            } else {
                android.util.Log.e("FirestoreKeyManager", "❌ Force refresh failed - document does not exist")
                false
            }
        } catch (e: Exception) {
            android.util.Log.e("FirestoreKeyManager", "💥 Force refresh exception: ${e.message}", e)
            false
        }
    }
    
    // Check if error indicates expired key
    fun isKeyExpiredError(responseCode: Int, errorMessage: String?): Boolean {
        return when (responseCode) {
            401 -> true // Unauthorized - typically expired key
            403 -> errorMessage?.contains("api", ignoreCase = true) == true // Forbidden API key
            else -> false
        }
    }
    
    // Fallback to BuildConfig if cache fails
    fun getApiKeyWithFallback(provider: String): String? {
        val firestoreKey = getApiKey(provider)
        val buildConfigKey = when (provider.lowercase()) {
            "openai" -> com.playstudio.aiteacher.BuildConfig.API_KEY
            "anthropic" -> com.playstudio.aiteacher.BuildConfig.ANTHROPIC_API_KEY
            "google" -> com.playstudio.aiteacher.BuildConfig.GOOGLE_API_KEY
            "grok" -> com.playstudio.aiteacher.BuildConfig.GROK_API_KEY
            "deepseek" -> com.playstudio.aiteacher.BuildConfig.DEEPSEEK_API_KEY
            else -> null
        }
        
        android.util.Log.e("FirestoreKeyManager", "🔍 Provider: $provider")
        android.util.Log.e("FirestoreKeyManager", "🔥 Firestore key: ${firestoreKey?.takeLast(4) ?: "null"}")
        android.util.Log.e("FirestoreKeyManager", "📦 BuildConfig key: ${buildConfigKey?.takeLast(4) ?: "null"}")
        android.util.Log.e("FirestoreKeyManager", "✅ Using: ${(firestoreKey ?: buildConfigKey)?.takeLast(4) ?: "null"}")
        
        return firestoreKey ?: buildConfigKey
    }
}