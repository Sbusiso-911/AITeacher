package com.playstudio.AITeacher

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.ktx.functions
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Service for seamless switching between mobile app and webapp
 * Handles authentication bridge and data synchronization
 */
class WebappSwitchingService(private val context: Context) {
    
    private val functions = Firebase.functions
    private val auth = FirebaseAuth.getInstance()
    private val TAG = "WebappSwitchingService"
    
    companion object {
        private const val WEBAPP_BASE_URL = "https://aiteacher-webapp.web.app"
        private const val TOKEN_EXPIRY_HOURS = 1
    }
    
    /**
     * Generate a secure token and switch to webapp
     */
    fun switchToWebapp(
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val currentUser = auth.currentUser
                if (currentUser == null) {
                    withContext(Dispatchers.Main) {
                        onError("Please sign in to switch to webapp")
                    }
                    return@launch
                }
                
                Log.d(TAG, "Generating webapp token for user: ${currentUser.uid}")
                
                // Sync profile data first
                syncProfileToWebapp()
                
                // Sync chat history
                syncChatHistoryToWebapp()
                
                // Generate webapp access token
                val tokenResult = functions
                    .getHttpsCallable("generateWebappToken")
                    .call()
                    .await()
                
                val tokenData = tokenResult.data as? Map<String, Any>
                val webappUrl = tokenData?.get("webappUrl") as? String
                
                if (webappUrl != null) {
                    Log.d(TAG, "Generated webapp URL: $webappUrl")
                    
                    withContext(Dispatchers.Main) {
                        // Open webapp in browser
                        openWebappInBrowser(webappUrl)
                        onSuccess(webappUrl)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        onError("Failed to generate webapp access URL")
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error switching to webapp", e)
                withContext(Dispatchers.Main) {
                    val errorMessage = when {
                        e.message?.contains("not-found") == true -> "User account not found"
                        e.message?.contains("unauthenticated") == true -> "Please sign in first"
                        e.message?.contains("network") == true -> "Network error. Check your connection."
                        else -> "Error switching to webapp: ${e.message}"
                    }
                    onError(errorMessage)
                }
            }
        }
    }
    
    /**
     * Sync mobile profile data to webapp format
     */
    private suspend fun syncProfileToWebapp() {
        try {
            val currentUser = auth.currentUser ?: return
            val sharedPrefs = context.getSharedPreferences("user_profile", Context.MODE_PRIVATE)
            
            val profileData = mapOf(
                "fullName" to (currentUser.displayName ?: ""),
                "profilePictureUrl" to (currentUser.photoUrl?.toString() ?: ""),
                "themePreference" to sharedPrefs.getString("theme_preference", "system"),
                "languageSetting" to sharedPrefs.getString("language", "en"),
                "preferredAiModels" to listOf(
                    sharedPrefs.getString("preferred_model_1", "gpt-4o-mini-2024-07-18"),
                    sharedPrefs.getString("preferred_model_2", "gemini-1.5-flash-latest")
                ).filterNotNull(),
                "subscriptionStatus" to getSubscriptionStatus(),
                "authProvider" to "mobile_app"
            )
            
            functions
                .getHttpsCallable("syncMobileProfile")
                .call(mapOf("profileData" to profileData))
                .await()
            
            Log.d(TAG, "Profile synced to webapp successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing profile to webapp", e)
        }
    }
    
    /**
     * Sync mobile chat history to webapp format
     */
    private suspend fun syncChatHistoryToWebapp() {
        try {
            // Get chat history from local database
            val chatHistory = ChatHistoryUtils.getAllChatHistory(context)
            
            // Convert to webapp format
            val webappChatHistory = chatHistory.map { message ->
                mapOf(
                    "senderType" to if (message.isUser) "user" else "ai",
                    "content" to message.message,
                    "timestamp" to message.timestamp,
                    "aiModel" to (message.aiModel ?: "gpt-3.5-turbo"),
                    "provider" to (message.provider ?: "openai"),
                    "messageId" to message.id.toString(),
                    "fromMobile" to true
                )
            }
            
            functions
                .getHttpsCallable("syncMobileChatHistory")
                .call(mapOf("chatHistory" to webappChatHistory))
                .await()
            
            Log.d(TAG, "Chat history synced to webapp: ${webappChatHistory.size} messages")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing chat history to webapp", e)
        }
    }
    
    /**
     * Get current subscription status from app
     */
    private fun getSubscriptionStatus(): String {
        val sharedPrefs = context.getSharedPreferences("subscription", Context.MODE_PRIVATE)
        val isPremium = sharedPrefs.getBoolean("is_premium", false)
        val subscriptionType = sharedPrefs.getString("subscription_type", "free")
        
        return when {
            isPremium -> subscriptionType ?: "premium"
            else -> "free"
        }
    }
    
    /**
     * Open webapp in browser with seamless authentication
     */
    private fun openWebappInBrowser(webappUrl: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(webappUrl)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            Log.d(TAG, "Opened webapp in browser")
        } catch (e: Exception) {
            Log.e(TAG, "Error opening webapp in browser", e)
        }
    }
    
    /**
     * Sync data back from webapp to mobile (for when user returns)
     */
    fun syncFromWebapp(
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val currentUser = auth.currentUser
                if (currentUser == null) {
                    withContext(Dispatchers.Main) {
                        onError("Please sign in to sync from webapp")
                    }
                    return@launch
                }
                
                // Get updated profile from webapp
                val profileResult = functions
                    .getHttpsCallable("getWebappProfile")
                    .call()
                    .await()
                
                val profileData = (profileResult.data as? Map<String, Any>)?.get("profile") as? Map<String, Any>
                
                if (profileData != null) {
                    // Update local preferences
                    val sharedPrefs = context.getSharedPreferences("user_profile", Context.MODE_PRIVATE)
                    with(sharedPrefs.edit()) {
                        putString("theme_preference", profileData["themePreference"] as? String ?: "system")
                        putString("language", profileData["languageSetting"] as? String ?: "en")
                        putString("subscription_status", profileData["subscriptionStatus"] as? String ?: "free")
                        apply()
                    }
                }
                
                // Get updated chat history from webapp
                val chatResult = functions
                    .getHttpsCallable("getChatHistory")
                    .call()
                    .await()
                
                val chatData = chatResult.data as? Map<String, Any>
                val messages = chatData?.get("messages") as? List<Map<String, Any>>
                
                if (messages != null) {
                    // Update local chat history
                    ChatHistoryUtils.syncFromWebapp(context, messages)
                }
                
                withContext(Dispatchers.Main) {
                    onSuccess()
                }
                
                Log.d(TAG, "Successfully synced data from webapp")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error syncing from webapp", e)
                withContext(Dispatchers.Main) {
                    onError("Error syncing from webapp: ${e.message}")
                }
            }
        }
    }
    
    /**
     * Check if user has pending webapp data to sync
     */
    fun checkForWebappUpdates(callback: (Boolean) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val currentUser = auth.currentUser
                if (currentUser == null) {
                    withContext(Dispatchers.Main) { callback(false) }
                    return@launch
                }
                
                // Check if there are updates from webapp
                val profileResult = functions
                    .getHttpsCallable("getWebappProfile")
                    .call()
                    .await()
                
                val profileData = (profileResult.data as? Map<String, Any>)?.get("profile") as? Map<String, Any>
                val lastSynced = profileData?.get("lastSyncedFromMobile") as? Long ?: 0
                
                val sharedPrefs = context.getSharedPreferences("sync_status", Context.MODE_PRIVATE)
                val lastLocalSync = sharedPrefs.getLong("last_webapp_sync", 0)
                
                withContext(Dispatchers.Main) {
                    callback(lastSynced > lastLocalSync)
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error checking for webapp updates", e)
                withContext(Dispatchers.Main) { callback(false) }
            }
        }
    }
}