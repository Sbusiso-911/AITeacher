package com.playstudio.AITeacher

import android.content.Context
import android.content.SharedPreferences
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
 * Handles unified user profile management across mobile and webapp platforms
 */
class ProfileIntegration(private val context: Context) {
    
    private val functions = Firebase.functions
    private val auth = FirebaseAuth.getInstance()
    private val TAG = "ProfileIntegration"
    
    private val profilePrefs: SharedPreferences = 
        context.getSharedPreferences("unified_profile", Context.MODE_PRIVATE)
    
    /**
     * Data class for unified user profile
     */
    data class UnifiedProfile(
        val userId: String,
        val email: String,
        val fullName: String,
        val profilePictureUrl: String,
        val themePreference: String,
        val languageSetting: String,
        val preferredAiModels: List<String>,
        val subscriptionStatus: String,
        val subscriptionTier: String,
        val lastSyncTimestamp: Long,
        val authProvider: String,
        val dailyQueryCount: Int,
        val totalQueries: Int,
        val favoriteFeatures: List<String>
    )
    
    /**
     * Create unified profile from current user data
     */
    fun createUnifiedProfile(): UnifiedProfile? {
        val currentUser = auth.currentUser ?: return null
        
        return UnifiedProfile(
            userId = currentUser.uid,
            email = currentUser.email ?: "",
            fullName = currentUser.displayName ?: "",
            profilePictureUrl = currentUser.photoUrl?.toString() ?: "",
            themePreference = profilePrefs.getString("theme_preference", "system") ?: "system",
            languageSetting = profilePrefs.getString("language", "en") ?: "en",
            preferredAiModels = getPreferredModels(),
            subscriptionStatus = getSubscriptionStatus(),
            subscriptionTier = getSubscriptionTier(),
            lastSyncTimestamp = System.currentTimeMillis(),
            authProvider = "mobile_app",
            dailyQueryCount = profilePrefs.getInt("daily_query_count", 0),
            totalQueries = profilePrefs.getInt("total_queries", 0),
            favoriteFeatures = getFavoriteFeatures()
        )
    }
    
    /**
     * Save unified profile to local storage
     */
    fun saveProfile(profile: UnifiedProfile) {
        with(profilePrefs.edit()) {
            putString("user_id", profile.userId)
            putString("email", profile.email)
            putString("full_name", profile.fullName)
            putString("profile_picture_url", profile.profilePictureUrl)
            putString("theme_preference", profile.themePreference)
            putString("language", profile.languageSetting)
            putString("subscription_status", profile.subscriptionStatus)
            putString("subscription_tier", profile.subscriptionTier)
            putLong("last_sync_timestamp", profile.lastSyncTimestamp)
            putString("auth_provider", profile.authProvider)
            putInt("daily_query_count", profile.dailyQueryCount)
            putInt("total_queries", profile.totalQueries)
            
            // Save preferred models as a comma-separated string
            putString("preferred_models", profile.preferredAiModels.joinToString(","))
            
            // Save favorite features as a comma-separated string
            putString("favorite_features", profile.favoriteFeatures.joinToString(","))
            
            apply()
        }
        
        Log.d(TAG, "Saved unified profile locally for user: ${profile.userId}")
    }
    
    /**
     * Load unified profile from local storage
     */
    fun loadProfile(): UnifiedProfile? {
        val userId = profilePrefs.getString("user_id", null) ?: return null
        
        return UnifiedProfile(
            userId = userId,
            email = profilePrefs.getString("email", "") ?: "",
            fullName = profilePrefs.getString("full_name", "") ?: "",
            profilePictureUrl = profilePrefs.getString("profile_picture_url", "") ?: "",
            themePreference = profilePrefs.getString("theme_preference", "system") ?: "system",
            languageSetting = profilePrefs.getString("language", "en") ?: "en",
            preferredAiModels = profilePrefs.getString("preferred_models", "")
                ?.split(",")?.filter { it.isNotBlank() } ?: emptyList(),
            subscriptionStatus = profilePrefs.getString("subscription_status", "free") ?: "free",
            subscriptionTier = profilePrefs.getString("subscription_tier", "free") ?: "free",
            lastSyncTimestamp = profilePrefs.getLong("last_sync_timestamp", 0),
            authProvider = profilePrefs.getString("auth_provider", "mobile_app") ?: "mobile_app",
            dailyQueryCount = profilePrefs.getInt("daily_query_count", 0),
            totalQueries = profilePrefs.getInt("total_queries", 0),
            favoriteFeatures = profilePrefs.getString("favorite_features", "")
                ?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
        )
    }
    
    /**
     * Sync profile to webapp and cloud storage
     */
    fun syncToCloud(
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val profile = createUnifiedProfile()
                if (profile == null) {
                    withContext(Dispatchers.Main) {
                        onError("No user profile found")
                    }
                    return@launch
                }
                
                // Sync to cloud
                val syncData = mapOf(
                    "platform" to "mobile",
                    "userData" to mapOf(
                        "fullName" to profile.fullName,
                        "profilePictureUrl" to profile.profilePictureUrl,
                        "themePreference" to profile.themePreference,
                        "languageSetting" to profile.languageSetting,
                        "preferredAiModels" to profile.preferredAiModels,
                        "subscriptionStatus" to profile.subscriptionStatus,
                        "subscriptionTier" to profile.subscriptionTier,
                        "authProvider" to profile.authProvider,
                        "dailyQueryCount" to profile.dailyQueryCount,
                        "totalQueries" to profile.totalQueries,
                        "favoriteFeatures" to profile.favoriteFeatures,
                        "lastSyncTimestamp" to profile.lastSyncTimestamp
                    )
                )
                
                functions
                    .getHttpsCallable("syncUserData")
                    .call(syncData)
                    .await()
                
                // Update local sync timestamp
                profilePrefs.edit()
                    .putLong("last_cloud_sync", System.currentTimeMillis())
                    .apply()
                
                withContext(Dispatchers.Main) {
                    onSuccess()
                }
                
                Log.d(TAG, "Successfully synced profile to cloud")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error syncing profile to cloud", e)
                withContext(Dispatchers.Main) {
                    onError("Sync failed: ${e.message}")
                }
            }
        }
    }
    
    /**
     * Sync profile from webapp and cloud storage
     */
    fun syncFromCloud(
        onSuccess: (UnifiedProfile) -> Unit,
        onError: (String) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val currentUser = auth.currentUser
                if (currentUser == null) {
                    withContext(Dispatchers.Main) {
                        onError("Please sign in first")
                    }
                    return@launch
                }
                
                // Get profile from webapp
                val profileResult = functions
                    .getHttpsCallable("getWebappProfile")
                    .call()
                    .await()
                
                val profileData = (profileResult.data as? Map<String, Any>)?.get("profile") as? Map<String, Any>
                
                if (profileData != null) {
                    val syncedProfile = UnifiedProfile(
                        userId = currentUser.uid,
                        email = profileData["email"] as? String ?: "",
                        fullName = profileData["fullName"] as? String ?: "",
                        profilePictureUrl = profileData["profilePictureUrl"] as? String ?: "",
                        themePreference = profileData["themePreference"] as? String ?: "system",
                        languageSetting = profileData["languageSetting"] as? String ?: "en",
                        preferredAiModels = (profileData["preferredAiModels"] as? List<String>) ?: emptyList(),
                        subscriptionStatus = profileData["subscriptionStatus"] as? String ?: "free",
                        subscriptionTier = profileData["subscriptionTier"] as? String ?: "free",
                        lastSyncTimestamp = System.currentTimeMillis(),
                        authProvider = "webapp_sync",
                        dailyQueryCount = (profileData["dailyQueryCount"] as? Number)?.toInt() ?: 0,
                        totalQueries = (profileData["totalQueries"] as? Number)?.toInt() ?: 0,
                        favoriteFeatures = (profileData["favoriteFeatures"] as? List<String>) ?: emptyList()
                    )
                    
                    // Save synced profile locally
                    saveProfile(syncedProfile)
                    
                    withContext(Dispatchers.Main) {
                        onSuccess(syncedProfile)
                    }
                    
                    Log.d(TAG, "Successfully synced profile from cloud")
                } else {
                    withContext(Dispatchers.Main) {
                        onError("No profile data found in cloud")
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error syncing profile from cloud", e)
                withContext(Dispatchers.Main) {
                    onError("Sync failed: ${e.message}")
                }
            }
        }
    }
    
    /**
     * Get preferred AI models from settings
     */
    private fun getPreferredModels(): List<String> {
        val defaultModels = listOf("gpt-4o-mini-2024-07-18", "gemini-1.5-flash-latest")
        val savedModels = profilePrefs.getString("preferred_models", "")
            ?.split(",")?.filter { it.isNotBlank() }
        
        return savedModels?.takeIf { it.isNotEmpty() } ?: defaultModels
    }
    
    /**
     * Get current subscription status
     */
    private fun getSubscriptionStatus(): String {
        val subscriptionPrefs = context.getSharedPreferences("subscription", Context.MODE_PRIVATE)
        return when {
            subscriptionPrefs.getBoolean("is_premium", false) -> "premium"
            subscriptionPrefs.getBoolean("is_pro", false) -> "pro"
            else -> "free"
        }
    }
    
    /**
     * Get subscription tier details
     */
    private fun getSubscriptionTier(): String {
        val subscriptionPrefs = context.getSharedPreferences("subscription", Context.MODE_PRIVATE)
        return subscriptionPrefs.getString("subscription_tier", "free") ?: "free"
    }
    
    /**
     * Get user's favorite features
     */
    private fun getFavoriteFeatures(): List<String> {
        val featuresString = profilePrefs.getString("favorite_features", "")
        return featuresString?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
    }
    
    /**
     * Update favorite features
     */
    fun updateFavoriteFeatures(features: List<String>) {
        profilePrefs.edit()
            .putString("favorite_features", features.joinToString(","))
            .apply()
    }
    
    /**
     * Update daily query count
     */
    fun incrementQueryCount() {
        val currentCount = profilePrefs.getInt("daily_query_count", 0)
        val totalCount = profilePrefs.getInt("total_queries", 0)
        
        profilePrefs.edit()
            .putInt("daily_query_count", currentCount + 1)
            .putInt("total_queries", totalCount + 1)
            .apply()
    }
    
    /**
     * Reset daily query count (call this daily)
     */
    fun resetDailyCount() {
        profilePrefs.edit()
            .putInt("daily_query_count", 0)
            .apply()
    }
    
    /**
     * Check if profile needs sync (based on last sync time)
     */
    fun needsSync(): Boolean {
        val lastSync = profilePrefs.getLong("last_cloud_sync", 0)
        val currentTime = System.currentTimeMillis()
        val sixHoursInMillis = 6 * 60 * 60 * 1000 // 6 hours
        
        return (currentTime - lastSync) > sixHoursInMillis
    }
    
    /**
     * Auto-sync profile if needed
     */
    fun autoSyncIfNeeded() {
        if (needsSync()) {
            syncToCloud(
                onSuccess = {
                    Log.d(TAG, "Auto-sync completed successfully")
                },
                onError = { error ->
                    Log.w(TAG, "Auto-sync failed: $error")
                }
            )
        }
    }
}