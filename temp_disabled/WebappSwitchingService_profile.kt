package com.playstudio.aiteacher.profile

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.lifecycle.lifecycleScope
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.ktx.functions
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Date

/**
 * Service for seamless switching from mobile app to webapp
 * Handles authentication bridge, data synchronization, and URL generation
 */
class WebappSwitchingService(private val context: Context) {
    
    companion object {
        private const val TAG = "WebappSwitchingService"
        private const val WEBAPP_BASE_URL = "https://aiteacher-75856.web.app"
    }
    
    private val functions: FirebaseFunctions = Firebase.functions
    private val authService = AuthenticationService(context)
    private val profileManager = ProfileManager(context)
    
    data class WebappSwitchResult(
        val success: Boolean,
        val webappUrl: String? = null,
        val message: String = "",
        val expiresIn: Int = 0
    )
    
    /**
     * Generate webapp access token and prepare for switching
     * This is the main method to call when user wants to switch to webapp
     */
    suspend fun prepareWebappSwitch(): WebappSwitchResult {
        return try {
            // Check if user is logged in
            val currentUser = authService.getCurrentUser()
            if (currentUser == null) {
                return WebappSwitchResult(
                    success = false,
                    message = "Please log in to switch to webapp"
                )
            }
            
            // Sync profile data first
            val profileSynced = syncProfileToWebapp(currentUser)
            if (!profileSynced) {
                Log.w(TAG, "Profile sync failed, but continuing with token generation")
            }
            
            // Sync chat history
            val chatSynced = syncChatHistoryToWebapp()
            if (!chatSynced) {
                Log.w(TAG, "Chat history sync failed, but continuing with token generation")
            }
            
            // Generate webapp token
            val result = generateWebappToken()
            
            if (result.success) {
                Log.i(TAG, "Successfully prepared webapp switch for user ${currentUser.userId}")
            }
            
            result
            
        } catch (e: Exception) {
            Log.e(TAG, "Error preparing webapp switch", e)
            WebappSwitchResult(
                success = false,
                message = "Failed to prepare webapp switch: ${e.message}"
            )
        }
    }
    
    /**
     * Generate temporary webapp access token via Firebase Function
     */
    private suspend fun generateWebappToken(): WebappSwitchResult {
        return try {
            val result = functions
                .getHttpsCallable("generateWebappToken")
                .call()
                .await()
            
            val data = result.data as? Map<String, Any> ?: throw Exception("Invalid response format")
            
            WebappSwitchResult(
                success = true,
                webappUrl = data["webappUrl"] as? String,
                message = "Webapp access token generated successfully",
                expiresIn = (data["expiresIn"] as? Number)?.toInt() ?: 3600
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error generating webapp token", e)
            WebappSwitchResult(
                success = false,
                message = "Failed to generate webapp token: ${e.message}"
            )
        }
    }
    
    /**
     * Sync mobile profile data to webapp-compatible format
     */
    private suspend fun syncProfileToWebapp(user: UserEntity): Boolean {
        return try {
            val profileData = mapOf(
                "fullName" to user.fullName,
                "profilePictureUrl" to user.profilePictureUrl,
                "themePreference" to user.themePreference,
                "languageSetting" to user.languageSetting,
                "preferredAiModels" to user.preferredAiModels,
                "authProvider" to user.authProvider,
                "subscriptionStatus" to getSubscriptionStatus(),
                "notificationEnabled" to user.notificationEnabled,
                "autoBackupEnabled" to user.autoBackupEnabled
            )
            
            val result = functions
                .getHttpsCallable("syncMobileProfile")
                .call(mapOf("profileData" to profileData))
                .await()
            
            val data = result.data as? Map<String, Any>
            val success = data?.get("success") as? Boolean ?: false
            
            if (success) {
                Log.i(TAG, "Profile data synced successfully")
            }
            
            success
            
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing profile to webapp", e)
            false
        }
    }
    
    /**
     * Sync mobile chat history to webapp
     */
    private suspend fun syncChatHistoryToWebapp(): Boolean {
        return try {
            // Get recent chat history from mobile app
            val chatHistory = profileManager.getRecentChatHistory(limit = 50)
            
            if (chatHistory.isEmpty()) {
                Log.i(TAG, "No chat history to sync")
                return true
            }
            
            // Convert to webapp format
            val webappChatHistory = chatHistory.map { message ->
                mapOf(
                    "senderType" to (if (message.isUser) "user" else "ai"),
                    "content" to message.content,
                    "timestamp" to message.timestamp.time,
                    "aiModel" to (message.aiModel ?: "gpt-3.5-turbo"),
                    "provider" to (message.provider ?: "openai"),
                    "messageId" to message.messageId
                )
            }
            
            val result = functions
                .getHttpsCallable("syncMobileChatHistory")
                .call(mapOf("chatHistory" to webappChatHistory))
                .await()
            
            val data = result.data as? Map<String, Any>
            val success = data?.get("success") as? Boolean ?: false
            val messagesSynced = (data?.get("messagesSynced") as? Number)?.toInt() ?: 0
            
            if (success) {
                Log.i(TAG, "Chat history synced successfully: $messagesSynced messages")
            }
            
            success
            
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing chat history to webapp", e)
            false
        }
    }
    
    /**
     * Get current subscription status from mobile app
     */
    private suspend fun getSubscriptionStatus(): String {
        return try {
            val subscriptionManager = SubscriptionManager(context)
            when {
                subscriptionManager.hasFeature("premium_features") -> "premium"
                subscriptionManager.hasFeature("pro_features") -> "pro"
                else -> "free"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting subscription status", e)
            "free"
        }
    }
    
    /**
     * Open webapp in browser with generated token
     */
    fun openWebappInBrowser(webappUrl: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(webappUrl))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            Log.i(TAG, "Opened webapp in browser: $webappUrl")
        } catch (e: Exception) {
            Log.e(TAG, "Error opening webapp in browser", e)
            throw e
        }
    }
    
    /**
     * Get webapp URL for sharing or other purposes
     */
    fun getWebappUrl(): String {
        return WEBAPP_BASE_URL
    }
    
    /**
     * Data class for mobile chat message
     */
    data class MobileChatMessage(
        val messageId: String?,
        val content: String,
        val isUser: Boolean,
        val timestamp: Date,
        val aiModel: String?,
        val provider: String?
    )
    
    /**
     * Check if webapp switching is available (user is logged in)
     */
    fun isWebappSwitchingAvailable(): Boolean {
        return authService.isLoggedIn()
    }
    
    /**
     * Get user's preferred webapp URL with customizations
     */
    suspend fun getPersonalizedWebappUrl(): String {
        val user = authService.getCurrentUser()
        val baseUrl = WEBAPP_BASE_URL
        
        if (user != null) {
            val params = buildString {
                append("?")
                append("theme=${user.themePreference}")
                append("&lang=${user.languageSetting}")
                if (user.preferredAiModels.isNotEmpty()) {
                    append("&model=${user.preferredAiModels.firstOrNull()}")
                }
            }
            return baseUrl + params
        }
        
        return baseUrl
    }
}