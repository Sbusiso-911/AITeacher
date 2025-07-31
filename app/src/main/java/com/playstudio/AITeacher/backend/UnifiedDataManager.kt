package com.playstudio.aiteacher.backend

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.playstudio.aiteacher.profile.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.*

/**
 * Unified Data Manager - Single source of truth for all app data
 * Provides offline-first architecture with automatic cloud sync
 * Handles data caching, conflict resolution, and seamless fallback
 */
class UnifiedDataManager private constructor(private val context: Context) {
    
    companion object {
        private const val TAG = "UnifiedDataManager"
        
        @Volatile
        private var INSTANCE: UnifiedDataManager? = null
        
        fun getInstance(context: Context): UnifiedDataManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: UnifiedDataManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    // Core services
    private val authService = AuthenticationService(context)
    private val profileManager = ProfileManager(context)
    private val backendClient = UnifiedBackendClient(context)
    private val subscriptionManager = SubscriptionManager(context)
    
    // Data state flows
    private val _userProfile = MutableStateFlow<UserEntity?>(null)
    val userProfile: StateFlow<UserEntity?> = _userProfile.asStateFlow()
    
    private val _chatHistory = MutableStateFlow<List<ChatSessionEntity>>(emptyList())
    val chatHistory: StateFlow<List<ChatSessionEntity>> = _chatHistory.asStateFlow()
    
    private val _subscriptionStatus = MutableStateFlow("free")
    val subscriptionStatus: StateFlow<String> = _subscriptionStatus.asStateFlow()
    
    private val _syncStatus = MutableStateFlow(DataSyncStatus.IDLE)
    val syncStatus: StateFlow<DataSyncStatus> = _syncStatus.asStateFlow()
    
    private val _isOnline = MutableStateFlow(true)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()
    
    // Cache management
    private val dataCache = mutableMapOf<String, CacheEntry<Any>>()
    private val cacheTtl = 5 * 60 * 1000L // 5 minutes
    
    enum class DataSyncStatus {
        IDLE,
        SYNCING,
        ERROR,
        CONFLICT_DETECTED,
        OFFLINE
    }
    
    data class CacheEntry<T>(
        val data: T,
        val timestamp: Long,
        val source: DataSource
    )
    
    enum class DataSource {
        LOCAL,
        CACHE,
        CLOUD,
        MERGED
    }
    
    init {
        // Start monitoring connectivity and sync status
        startDataMonitoring()
    }
    
    /**
     * Initialize the data manager and perform initial data load
     */
    suspend fun initialize(): Boolean {
        return try {
            Log.i(TAG, "Initializing UnifiedDataManager")
            
            _syncStatus.value = DataSyncStatus.SYNCING
            
            // Load user profile
            loadUserProfile()
            
            // Load chat history
            loadChatHistory()
            
            // Load subscription status
            loadSubscriptionStatus()
            
            // Attempt cloud sync if online
            if (isNetworkAvailable()) {
                performCloudSync()
            } else {
                _syncStatus.value = DataSyncStatus.OFFLINE
            }
            
            _syncStatus.value = DataSyncStatus.IDLE
            Log.i(TAG, "UnifiedDataManager initialized successfully")
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize UnifiedDataManager", e)
            _syncStatus.value = DataSyncStatus.ERROR
            false
        }
    }
    
    /**
     * Authentication Methods
     */
    suspend fun login(email: String, password: String, rememberMe: Boolean = false): AuthenticationService.AuthResult {
        val result = authService.login(email, password, rememberMe)
        if (result.success) {
            // Reload data for the logged-in user
            initialize()
        }
        return result
    }
    
    suspend fun register(registrationData: AuthenticationService.RegistrationData): AuthenticationService.AuthResult {
        val result = authService.register(registrationData)
        if (result.success) {
            // Initialize data for the new user
            initialize()
        }
        return result
    }
    
    suspend fun logout(): Boolean {
        val result = authService.logout()
        if (result) {
            // Clear all cached data
            clearCache()
            _userProfile.value = null
            _chatHistory.value = emptyList()
            _subscriptionStatus.value = "free"
        }
        return result
    }
    
    fun isLoggedIn(): Boolean = authService.isLoggedIn()
    
    /**
     * Profile Data Methods
     */
    suspend fun getUserProfile(forceRefresh: Boolean = false): UserEntity? {
        return getCachedOrLoad("user_profile", forceRefresh) {
            authService.getCurrentUser()
        }?.also {
            _userProfile.value = it
        }
    }
    
    suspend fun updateUserProfile(updates: Map<String, Any>): Boolean {
        return try {
            val result = authService.updateProfile(updates)
            if (result.success && result.user != null) {
                _userProfile.value = result.user
                invalidateCache("user_profile")
                
                // Sync to cloud in background if online
                if (isNetworkAvailable()) {
                    CoroutineScope(Dispatchers.IO).launch {
                        backendClient.syncProfileToCloud()
                    }
                }
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating user profile", e)
            false
        }
    }
    
    suspend fun getProfileData(): ProfileManager.ProfileData? {
        return getCachedOrLoad("profile_data") {
            profileManager.getProfileData()
        }
    }
    
    /**
     * Chat History Methods
     */
    fun getChatHistoryFlow(): Flow<List<ChatSessionEntity>> {
        return profileManager.getChatHistory().onEach { sessions ->
            _chatHistory.value = sessions
            cacheData("chat_history", sessions, DataSource.LOCAL)
        }
    }
    
    suspend fun getChatHistory(forceRefresh: Boolean = false): List<ChatSessionEntity> {
        return getCachedOrLoad("chat_history", forceRefresh) {
            profileManager.getChatHistory().first()
        } ?: emptyList()
    }
    
    suspend fun searchChatHistory(query: String): List<ChatSessionEntity> {
        return getCachedOrLoad("search_$query") {
            profileManager.searchChatHistory(query).first()
        } ?: emptyList()
    }
    
    suspend fun getFavoriteChatHistory(): List<ChatSessionEntity> {
        return getCachedOrLoad("favorite_chats") {
            profileManager.getFavoriteChatHistory().first()
        } ?: emptyList()
    }
    
    suspend fun toggleChatFavorite(sessionId: Long): Boolean {
        val result = profileManager.toggleChatFavorite(sessionId)
        if (result) {
            invalidateCache("chat_history")
            invalidateCache("favorite_chats")
            
            // Reload chat history to update UI
            loadChatHistory()
            
            // Sync change to cloud
            if (isNetworkAvailable()) {
                CoroutineScope(Dispatchers.IO).launch {
                    backendClient.syncChatHistoryToCloud()
                }
            }
        }
        return result
    }
    
    suspend fun archiveChat(sessionId: Long): Boolean {
        val result = profileManager.archiveChat(sessionId)
        if (result) {
            invalidateCache("chat_history")
            loadChatHistory()
        }
        return result
    }
    
    suspend fun deleteChat(sessionId: Long): Boolean {
        val result = profileManager.deleteChat(sessionId)
        if (result) {
            invalidateCache("chat_history")
            invalidateCache("favorite_chats")
            loadChatHistory()
            
            // Sync deletion to cloud
            if (isNetworkAvailable()) {
                CoroutineScope(Dispatchers.IO).launch {
                    backendClient.syncChatHistoryToCloud()
                }
            }
        }
        return result
    }
    
    suspend fun exportChat(sessionId: Long): ProfileManager.ChatExportData? {
        return profileManager.exportChat(sessionId)
    }
    
    /**
     * Subscription Methods
     */
    suspend fun getSubscriptionStatus(forceRefresh: Boolean = false): String {
        return getCachedOrLoad("subscription_status", forceRefresh) {
            when {
                subscriptionManager.hasFeature("premium_features") -> "premium"
                subscriptionManager.hasFeature("pro_features") -> "pro"
                else -> "free"
            }
        }?.also {
            _subscriptionStatus.value = it
        } ?: "free"
    }
    
    suspend fun hasFeature(feature: String): Boolean {
        return subscriptionManager.hasFeature(feature)
    }
    
    /**
     * Webapp Integration Methods
     */
    suspend fun generateWebappToken(): UnifiedBackendClient.WebappTokenResult {
        return backendClient.generateWebappToken()
    }
    
    /**
     * Sync Methods
     */
    suspend fun performFullSync(): Boolean {
        return try {
            _syncStatus.value = DataSyncStatus.SYNCING
            
            if (!isNetworkAvailable()) {
                _syncStatus.value = DataSyncStatus.OFFLINE
                return false
            }
            
            val result = backendClient.performFullSync()
            _syncStatus.value = if (result.success) {
                DataSyncStatus.IDLE
            } else {
                DataSyncStatus.ERROR
            }
            
            if (result.success) {
                // Refresh all cached data
                clearCache()
                loadUserProfile()
                loadChatHistory()
                loadSubscriptionStatus()
            }
            
            result.success
            
        } catch (e: Exception) {
            Log.e(TAG, "Full sync failed", e)
            _syncStatus.value = DataSyncStatus.ERROR
            false
        }
    }
    
    suspend fun isBackendReachable(): Boolean {
        val reachable = backendClient.isBackendReachable()
        _isOnline.value = reachable
        return reachable
    }
    
    /**
     * Statistics and Analytics
     */
    suspend fun getUserStatistics(): Map<String, Any> {
        return getCachedOrLoad("user_statistics") {
            profileManager.getUserStatistics()
        } ?: emptyMap()
    }
    
    /**
     * Private Helper Methods
     */
    private suspend fun loadUserProfile() {
        try {
            val user = authService.getCurrentUser()
            _userProfile.value = user
            user?.let { cacheData("user_profile", it, DataSource.LOCAL) }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading user profile", e)
        }
    }
    
    private suspend fun loadChatHistory() {
        try {
            profileManager.getChatHistory().first().let { sessions ->
                _chatHistory.value = sessions
                cacheData("chat_history", sessions, DataSource.LOCAL)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading chat history", e)
        }
    }
    
    private suspend fun loadSubscriptionStatus() {
        try {
            val status = when {
                subscriptionManager.hasFeature("premium_features") -> "premium"
                subscriptionManager.hasFeature("pro_features") -> "pro"
                else -> "free"
            }
            _subscriptionStatus.value = status
            cacheData("subscription_status", status, DataSource.LOCAL)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading subscription status", e)
        }
    }
    
    private suspend fun performCloudSync() {
        try {
            if (isNetworkAvailable()) {
                val result = backendClient.performFullSync()
                if (!result.success) {
                    Log.w(TAG, "Cloud sync failed: ${result.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Cloud sync error", e)
        }
    }
    
    private fun startDataMonitoring() {
        // Monitor network connectivity
        CoroutineScope(Dispatchers.IO).launch {
            while (true) {
                delay(30000) // Check every 30 seconds
                val wasOnline = _isOnline.value
                val isNowOnline = isNetworkAvailable()
                _isOnline.value = isNowOnline
                
                if (!wasOnline && isNowOnline) {
                    // Just came online, perform sync
                    Log.i(TAG, "Device came online, performing sync")
                    performCloudSync()
                } else if (wasOnline && !isNowOnline) {
                    Log.i(TAG, "Device went offline")
                    _syncStatus.value = DataSyncStatus.OFFLINE
                }
            }
        }
    }
    
    private fun isNetworkAvailable(): Boolean {
        return try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            val activeNetwork = connectivityManager.activeNetworkInfo
            activeNetwork?.isConnectedOrConnecting == true
        } catch (e: Exception) {
            Log.e(TAG, "Error checking network availability", e)
            false
        }
    }
    
    /**
     * Cache Management
     */
    private fun <T> cacheData(key: String, data: T, source: DataSource) {
        dataCache[key] = CacheEntry(data as Any, System.currentTimeMillis(), source)
    }
    
    private suspend fun <T> getCachedOrLoad(
        key: String, 
        forceRefresh: Boolean = false,
        loader: suspend () -> T?
    ): T? {
        if (!forceRefresh) {
            val cached = dataCache[key]
            if (cached != null && (System.currentTimeMillis() - cached.timestamp) < cacheTtl) {
                @Suppress("UNCHECKED_CAST")
                return cached.data as T
            }
        }
        
        return try {
            val data = loader()
            data?.let { cacheData(key, it, DataSource.LOCAL) }
            data
        } catch (e: Exception) {
            Log.e(TAG, "Error loading data for key: $key", e)
            null
        }
    }
    
    private fun invalidateCache(key: String) {
        dataCache.remove(key)
    }
    
    private fun clearCache() {
        dataCache.clear()
    }
    
    /**
     * Get cache status for debugging
     */
    fun getCacheStatus(): Map<String, Any> {
        return mapOf(
            "cacheSize" to dataCache.size,
            "cacheKeys" to dataCache.keys.toList(),
            "oldestEntry" to (dataCache.values.minByOrNull { it.timestamp }?.timestamp ?: 0),
            "newestEntry" to (dataCache.values.maxByOrNull { it.timestamp }?.timestamp ?: 0)
        )
    }
}