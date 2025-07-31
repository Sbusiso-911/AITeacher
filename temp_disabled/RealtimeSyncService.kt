package com.playstudio.aiteacher.backend

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.google.firebase.firestore.*
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.playstudio.aiteacher.profile.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import java.util.*

/**
 * Real-time synchronization service using Firestore for live updates
 * Handles bidirectional sync between local database and cloud
 * Provides conflict resolution and offline support
 */
class RealtimeSyncService : LifecycleService() {
    
    companion object {
        private const val TAG = "RealtimeSyncService"
        private const val USERS_COLLECTION = "users"
        private const val CHAT_SESSIONS_COLLECTION = "chatSessions"
        private const val CHAT_MESSAGES_COLLECTION = "chatMessages"
        private const val SYNC_METADATA_COLLECTION = "syncMetadata"
        
        // Sync status constants
        const val SYNC_STATUS_IDLE = "idle"
        const val SYNC_STATUS_SYNCING = "syncing"
        const val SYNC_STATUS_ERROR = "error"
        const val SYNC_STATUS_CONFLICT = "conflict"
    }
    
    private lateinit var authService: AuthenticationService
    private lateinit var profileManager: ProfileManager
    private lateinit var unifiedClient: UnifiedBackendClient
    private lateinit var firestore: FirebaseFirestore
    
    private val _syncStatus = MutableStateFlow(SYNC_STATUS_IDLE)
    val syncStatus: StateFlow<String> = _syncStatus.asStateFlow()
    
    private val _lastSyncTime = MutableStateFlow<Date?>(null)
    val lastSyncTime: StateFlow<Date?> = _lastSyncTime.asStateFlow()
    
    private val _conflictsCount = MutableStateFlow(0)
    val conflictsCount: StateFlow<Int> = _conflictsCount.asStateFlow()
    
    private var userDocumentListener: ListenerRegistration? = null
    private var chatSessionsListener: ListenerRegistration? = null
    private var syncJob: Job? = null
    
    data class SyncConflict(
        val id: String,
        val type: ConflictType,
        val localData: Map<String, Any>,
        val cloudData: Map<String, Any>,
        val timestamp: Date
    )
    
    enum class ConflictType {
        PROFILE_UPDATE,
        CHAT_SESSION_UPDATE,
        MESSAGE_UPDATE,
        SUBSCRIPTION_UPDATE
    }
    
    private val pendingConflicts = mutableListOf<SyncConflict>()
    
    override fun onCreate() {
        super.onCreate()
        
        authService = AuthenticationService(this)
        profileManager = ProfileManager(this)
        unifiedClient = UnifiedBackendClient(this)
        firestore = Firebase.firestore
        
        Log.i(TAG, "RealtimeSyncService created")
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        
        lifecycleScope.launch {
            startRealtimeSync()
        }
        
        return START_STICKY // Restart if killed
    }
    
    override fun onDestroy() {
        super.onDestroy()
        stopRealtimeSync()
        Log.i(TAG, "RealtimeSyncService destroyed")
    }
    
    /**
     * Start real-time synchronization
     */
    private suspend fun startRealtimeSync() {
        try {
            val currentUser = authService.getCurrentUser()
            if (currentUser == null) {
                Log.w(TAG, "No user logged in, cannot start real-time sync")
                return
            }
            
            _syncStatus.value = SYNC_STATUS_SYNCING
            
            // Set up Firestore listeners
            setupUserProfileListener(currentUser.userId)
            setupChatSessionsListener(currentUser.userId)
            
            // Perform initial sync
            performInitialSync()
            
            // Start periodic sync job
            startPeriodicSync()
            
            _syncStatus.value = SYNC_STATUS_IDLE
            _lastSyncTime.value = Date()
            
            Log.i(TAG, "Real-time sync started for user ${currentUser.userId}")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start real-time sync", e)
            _syncStatus.value = SYNC_STATUS_ERROR
        }
    }
    
    /**
     * Set up Firestore listener for user profile changes
     */
    private fun setupUserProfileListener(userId: Long) {
        userDocumentListener = firestore
            .collection(USERS_COLLECTION)
            .document(userId.toString())
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "User profile listener error", error)
                    return@addSnapshotListener
                }
                
                if (snapshot != null && snapshot.exists()) {
                    lifecycleScope.launch {
                        handleUserProfileUpdate(snapshot.data ?: emptyMap())
                    }
                }
            }
    }
    
    /**
     * Set up Firestore listener for chat sessions changes
     */
    private fun setupChatSessionsListener(userId: Long) {
        chatSessionsListener = firestore
            .collection(CHAT_SESSIONS_COLLECTION)
            .whereEqualTo("userId", userId)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.e(TAG, "Chat sessions listener error", error)
                    return@addSnapshotListener
                }
                
                snapshots?.documentChanges?.forEach { change ->
                    when (change.type) {
                        DocumentChange.Type.ADDED,
                        DocumentChange.Type.MODIFIED -> {
                            lifecycleScope.launch {
                                handleChatSessionUpdate(change.document.data)
                            }
                        }
                        DocumentChange.Type.REMOVED -> {
                            lifecycleScope.launch {
                                handleChatSessionDeletion(change.document.id)
                            }
                        }
                    }
                }
            }
    }
    
    /**
     * Handle user profile updates from cloud
     */
    private suspend fun handleUserProfileUpdate(cloudData: Map<String, Any>) {
        try {
            val currentUser = authService.getCurrentUser() ?: return
            
            // Check for conflicts
            val localUpdateTime = currentUser.updatedAt.time
            val cloudUpdateTime = cloudData["updatedAt"] as? Long ?: 0
            
            if (cloudUpdateTime > localUpdateTime) {
                // Cloud data is newer, apply updates
                val updates = mutableMapOf<String, Any>()
                
                cloudData["fullName"]?.let { updates["fullName"] = it }
                cloudData["themePreference"]?.let { updates["themePreference"] = it }
                cloudData["languageSetting"]?.let { updates["languageSetting"] = it }
                cloudData["notificationEnabled"]?.let { updates["notificationEnabled"] = it }
                cloudData["autoBackupEnabled"]?.let { updates["autoBackupEnabled"] = it }
                
                if (updates.isNotEmpty()) {
                    authService.updateProfile(updates)
                    Log.i(TAG, "Applied profile updates from cloud: ${updates.keys}")
                }
            } else if (localUpdateTime > cloudUpdateTime) {
                // Local data is newer, push to cloud
                lifecycleScope.launch {
                    unifiedClient.syncProfileToCloud()
                }
            } else if (localUpdateTime == cloudUpdateTime) {
                // Same timestamp but different data = conflict
                val conflict = SyncConflict(
                    id = "profile_${currentUser.userId}",
                    type = ConflictType.PROFILE_UPDATE,
                    localData = mapOf(
                        "fullName" to currentUser.fullName,
                        "themePreference" to currentUser.themePreference,
                        "languageSetting" to currentUser.languageSetting,
                        "updatedAt" to currentUser.updatedAt.time
                    ),
                    cloudData = cloudData,
                    timestamp = Date()
                )
                addConflict(conflict)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error handling profile update", e)
        }
    }
    
    /**
     * Handle chat session updates from cloud
     */
    private suspend fun handleChatSessionUpdate(cloudData: Map<String, Any>) {
        try {
            val sessionId = cloudData["sessionId"] as? Long ?: return
            
            // Check if session exists locally - use first() to get current value
            val chatHistoryFlow = profileManager.getChatHistory()
            val currentSessions = chatHistoryFlow.first()
            val localSession = currentSessions.find { it.sessionId == sessionId }
            
            if (localSession == null) {
                // New session from cloud, create locally
                createLocalChatSession(cloudData)
            } else {
                // Check for conflicts
                val localUpdateTime = localSession.updatedAt.time
                val cloudUpdateTime = cloudData["updatedAt"] as? Long ?: 0
                
                if (cloudUpdateTime > localUpdateTime) {
                    // Cloud data is newer, update locally
                    updateLocalChatSession(sessionId, cloudData)
                } else if (localUpdateTime > cloudUpdateTime) {
                    // Local data is newer, push to cloud
                    pushChatSessionToCloud(localSession)
                } else {
                    // Potential conflict
                    val conflict = SyncConflict(
                        id = "chat_session_$sessionId",
                        type = ConflictType.CHAT_SESSION_UPDATE,
                        localData = mapOf(
                            "title" to localSession.title,
                            "category" to localSession.category,
                            "updatedAt" to localSession.updatedAt.time
                        ),
                        cloudData = cloudData,
                        timestamp = Date()
                    )
                    addConflict(conflict)
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error handling chat session update", e)
        }
    }
    
    /**
     * Handle chat session deletion from cloud
     */
    private suspend fun handleChatSessionDeletion(sessionId: String) {
        try {
            val sessionIdLong = sessionId.toLongOrNull() ?: return
            profileManager.deleteChat(sessionIdLong)
            Log.i(TAG, "Deleted local chat session $sessionId based on cloud deletion")
        } catch (e: Exception) {
            Log.e(TAG, "Error handling chat session deletion", e)
        }
    }
    
    /**
     * Create local chat session from cloud data
     */
    private suspend fun createLocalChatSession(cloudData: Map<String, Any>) {
        // Implementation would convert cloud data to ChatSessionEntity and insert
        Log.i(TAG, "Creating local chat session from cloud data")
    }
    
    /**
     * Update local chat session with cloud data
     */
    private suspend fun updateLocalChatSession(sessionId: Long, cloudData: Map<String, Any>) {
        // Implementation would update the local ChatSessionEntity
        Log.i(TAG, "Updating local chat session $sessionId with cloud data")
    }
    
    /**
     * Push local chat session to cloud
     */
    private suspend fun pushChatSessionToCloud(session: ChatSessionEntity) {
        // Implementation would push session data to Firestore
        Log.i(TAG, "Pushing chat session ${session.sessionId} to cloud")
    }
    
    /**
     * Perform initial synchronization
     */
    private suspend fun performInitialSync() {
        try {
            val syncResult = unifiedClient.performFullSync()
            if (!syncResult.success) {
                Log.w(TAG, "Initial sync failed: ${syncResult.message}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Initial sync error", e)
        }
    }
    
    /**
     * Start periodic sync job
     */
    private fun startPeriodicSync() {
        syncJob = lifecycleScope.launch {
            while (isActive) {
                delay(5 * 60 * 1000L) // 5 minutes
                
                try {
                    if (unifiedClient.isBackendReachable()) {
                        val result = unifiedClient.performFullSync()
                        if (result.success) {
                            _lastSyncTime.value = Date()
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Periodic sync failed", e)
                }
            }
        }
    }
    
    /**
     * Stop real-time synchronization
     */
    private fun stopRealtimeSync() {
        userDocumentListener?.remove()
        chatSessionsListener?.remove()
        syncJob?.cancel()
        
        _syncStatus.value = SYNC_STATUS_IDLE
    }
    
    /**
     * Add conflict to pending list
     */
    private fun addConflict(conflict: SyncConflict) {
        pendingConflicts.add(conflict)
        _conflictsCount.value = pendingConflicts.size
        _syncStatus.value = SYNC_STATUS_CONFLICT
        
        Log.w(TAG, "Sync conflict detected: ${conflict.type} - ${conflict.id}")
    }
    
    /**
     * Resolve conflict with user choice
     */
    suspend fun resolveConflict(conflictId: String, useLocal: Boolean): Boolean {
        return try {
            val conflict = pendingConflicts.find { it.id == conflictId }
            if (conflict != null) {
                if (useLocal) {
                    // Push local data to cloud
                    when (conflict.type) {
                        ConflictType.PROFILE_UPDATE -> {
                            authService.updateProfile(conflict.localData)
                            unifiedClient.syncProfileToCloud()
                        }
                        ConflictType.CHAT_SESSION_UPDATE -> {
                            // Handle chat session conflict resolution
                        }
                        else -> {
                            Log.w(TAG, "Unhandled conflict type: ${conflict.type}")
                        }
                    }
                } else {
                    // Accept cloud data
                    when (conflict.type) {
                        ConflictType.PROFILE_UPDATE -> {
                            authService.updateProfile(conflict.cloudData)
                        }
                        ConflictType.CHAT_SESSION_UPDATE -> {
                            // Handle chat session conflict resolution
                        }
                        else -> {
                            Log.w(TAG, "Unhandled conflict type: ${conflict.type}")
                        }
                    }
                }
                
                pendingConflicts.remove(conflict)
                _conflictsCount.value = pendingConflicts.size
                
                if (pendingConflicts.isEmpty()) {
                    _syncStatus.value = SYNC_STATUS_IDLE
                }
                
                Log.i(TAG, "Resolved conflict $conflictId - used ${if (useLocal) "local" else "cloud"} data")
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving conflict", e)
            false
        }
    }
    
    /**
     * Get all pending conflicts
     */
    fun getPendingConflicts(): List<SyncConflict> = pendingConflicts.toList()
    
    /**
     * Force immediate sync
     */
    suspend fun forceSync(): Boolean {
        return try {
            _syncStatus.value = SYNC_STATUS_SYNCING
            val result = unifiedClient.performFullSync()
            _syncStatus.value = if (result.success) SYNC_STATUS_IDLE else SYNC_STATUS_ERROR
            _lastSyncTime.value = Date()
            result.success
        } catch (e: Exception) {
            Log.e(TAG, "Force sync failed", e)
            _syncStatus.value = SYNC_STATUS_ERROR
            false
        }
    }
}