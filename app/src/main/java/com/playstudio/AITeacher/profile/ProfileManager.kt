package com.playstudio.aiteacher.profile

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.playstudio.aiteacher.firestore.FirestoreChatManager
import com.playstudio.aiteacher.profile.FirestoreSubscriptionManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class ProfileManager(private val context: Context) {
    
    private val database = com.playstudio.aiteacher.profile.ProfileDatabase.getDatabase(context)
    private val userDao = database.userDao()
    private val chatSessionDao = database.chatSessionDao()
    private val chatMessageDao = database.chatMessageDao()
    private val subscriptionDao = database.subscriptionDao()
    private val usageAnalyticsDao = database.usageAnalyticsDao()
    private val authService = AuthenticationService(context)
    private val firestoreChatManager = FirestoreChatManager.getInstance()
    private val firestoreSubscriptionManager = FirestoreSubscriptionManager(context)
    
    companion object {
        private const val TAG = "ProfileManager"
        private const val PROFILE_PICTURES_DIR = "profile_pictures"
        private const val EXPORT_DIR = "chat_exports"
    }
    
    /**
     * Get current Firebase user ID as string - safe for millions of users
     */
    private fun getCurrentFirebaseUserId(): String? {
        return com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
    }
    
    /**
     * Create a UserEntity from current Firebase user for compatibility with existing methods
     * TODO: Remove this once we fully migrate to Firestore-only architecture
     */
    private fun getCurrentUserEntity(): UserEntity? {
        val firebaseUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser ?: return null
        return UserEntity(
            userId = firebaseUser.uid,
            email = firebaseUser.email ?: "unknown@example.com",
            fullName = firebaseUser.displayName ?: "User",
            createdAt = Date(firebaseUser.metadata?.creationTimestamp ?: System.currentTimeMillis()),
            lastLogin = Date()
        )
    }
    
    data class ProfileData(
        val user: UserEntity,
        val subscription: SubscriptionEntity?,
        val totalChats: Int,
        val totalMessages: Int,
        val totalTokens: Int,
        val favoriteChats: Int,
        val storageUsed: Double,
        val joinDate: Date,
        val lastActivity: Date?
    )
    
    data class ChatExportData(
        val sessionId: Long,
        val title: String,
        val aiModel: String,
        val category: String,
        val createdAt: Date,
        val messages: List<ChatMessageEntity>,
        val messageCount: Int,
        val tokenCount: Int
    )
    
    // Profile Data Management
    suspend fun getProfileData(): ProfileData? {
        return try {
            val userId = getCurrentFirebaseUserId() ?: return null
            Log.d(TAG, "Getting profile data for Firebase user: $userId")
            
            // Try to get subscription data from Firestore
            val subscription = try {
                firestoreSubscriptionManager.getSubscription()?.let { sub ->
                    SubscriptionEntity(
                        subscriptionId = 0L,
                        userId = sub.userId,
                        planType = sub.planType,
                        status = sub.status,
                        startDate = Date(sub.startDate),
                        endDate = Date(sub.endDate),
                        billingCycle = sub.billingCycle,
                        pricePaid = sub.pricePaid,
                        currency = sub.currency,
                        autoRenew = sub.autoRenew,
                        trialEndDate = sub.trialEndDate?.let { Date(it) },
                        createdAt = Date(sub.createdAt),
                        updatedAt = Date(sub.updatedAt),
                        featuresIncluded = sub.features
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to get subscription data", e)
                null
            }
            
            // Get statistics using Firebase UID
            val totalChats = chatSessionDao.getActiveChatSessionCount(userId)
            val totalMessages = chatMessageDao.getTotalUserMessages(userId)
            val totalTokens = chatMessageDao.getTotalTokensUsed(userId)
            val favoriteChatsFlow = chatSessionDao.getFavoriteChatSessions(userId)
            var favoriteChats = 0
            favoriteChatsFlow.collect { favoriteChats = it.size }
            val storageUsed = try {
                usageAnalyticsDao.getTotalStorageUsed(userId)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to get storage usage", e)
                0.0
            }
            
            // Create a UserEntity for compatibility (but we're moving away from this)
            val firebaseUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
            val userEntity = UserEntity(
                userId = userId, // Now String
                email = firebaseUser?.email ?: "unknown@example.com",
                fullName = firebaseUser?.displayName ?: "User",
                createdAt = Date(firebaseUser?.metadata?.creationTimestamp ?: System.currentTimeMillis()),
                lastLogin = Date()
            )
            
            ProfileData(
                user = userEntity,
                subscription = subscription,
                totalChats = totalChats,
                totalMessages = totalMessages,
                totalTokens = totalTokens,
                favoriteChats = favoriteChats,
                storageUsed = storageUsed,
                joinDate = userEntity.createdAt,
                lastActivity = userEntity.lastLogin
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error getting profile data", e)
            null
        }
    }
    
    suspend fun updateProfilePicture(bitmap: Bitmap): String? {
        return try {
            val userId = getCurrentFirebaseUserId() ?: return null
            
            // Create profile pictures directory
            val profileDir = File(context.filesDir, PROFILE_PICTURES_DIR)
            if (!profileDir.exists()) {
                profileDir.mkdirs()
            }
            
            // Generate unique filename
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val filename = "profile_${userId}_$timestamp.jpg"
            val file = File(profileDir, filename)
            
            // Save bitmap to file
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
            
            // Update user profile
            val profileUrl = file.absolutePath
            userDao.updateProfilePicture(userId, profileUrl)
            
            profileUrl
        } catch (e: Exception) {
            Log.e(TAG, "Error updating profile picture", e)
            null
        }
    }
    
    suspend fun getProfilePicture(): String? {
        return try {
            val user = getCurrentUserEntity()
            user?.profilePictureUrl
        } catch (e: Exception) {
            Log.e(TAG, "Error getting profile picture", e)
            null
        }
    }
    
    suspend fun updateThemePreference(theme: String): Boolean {
        return try {
            val user = getCurrentUserEntity() ?: return false
            userDao.updateThemePreference(user.userId, theme)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error updating theme preference", e)
            false
        }
    }
    
    suspend fun updateLanguageSetting(language: String): Boolean {
        return try {
            val user = getCurrentUserEntity() ?: return false
            userDao.updateLanguageSetting(user.userId, language)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error updating language setting", e)
            false
        }
    }
    
    suspend fun updateNotificationSettings(enabled: Boolean): Boolean {
        return try {
            val user = getCurrentUserEntity() ?: return false
            userDao.updateNotificationSettings(user.userId, enabled)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error updating notification settings", e)
            false
        }
    }
    
    // Chat History Management
    fun getChatHistory(): Flow<List<ChatSessionEntity>> {
        return flow {
            try {
                val user = getCurrentUserEntity()
                if (user != null) {
                    // First try to get from Firestore (primary source for cross-device sync)
                    try {
                        val firestoreSessions = firestoreChatManager.getChatSessions()
                        if (firestoreSessions.isNotEmpty()) {
                            // Convert Firestore sessions to ChatSessionEntity format
                            val convertedSessions = firestoreSessions.map { firestoreSession ->
                                ChatSessionEntity(
                                    sessionId = firestoreSession.sessionId.hashCode().toLong(), // Convert string to long
                                    userId = user.userId,
                                    title = firestoreSession.title,
                                    aiModelUsed = firestoreSession.aiModelUsed,
                                    category = firestoreSession.category,
                                    createdAt = firestoreSession.createdAt,
                                    updatedAt = firestoreSession.updatedAt,
                                    isFavorite = firestoreSession.isFavorite,
                                    isArchived = firestoreSession.isArchived,
                                    messageCount = firestoreSession.messageCount,
                                    lastMessagePreview = firestoreSession.lastMessagePreview
                                )
                            }
                            emit(convertedSessions)
                            Log.d(TAG, "Retrieved ${convertedSessions.size} chat sessions from Firestore")
                        } else {
                            // Fallback to local database
                            Log.d(TAG, "No Firestore sessions found, falling back to local database")
                            chatSessionDao.getActiveChatSessions(user.userId).collect { sessions ->
                                emit(sessions)
                            }
                        }
                    } catch (firestoreError: Exception) {
                        Log.w(TAG, "Firestore retrieval failed, falling back to local database", firestoreError)
                        // Fallback to local database
                        chatSessionDao.getActiveChatSessions(user.userId).collect { sessions ->
                            emit(sessions)
                        }
                    }
                } else {
                    emit(emptyList())
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting chat history", e)
                emit(emptyList())
            }
        }
    }
    
    fun getFavoriteChatHistory(): Flow<List<ChatSessionEntity>> {
        return flow {
            try {
                val user = getCurrentUserEntity()
                if (user != null) {
                    // Try to get from Firestore first
                    try {
                        val firestoreSessions = firestoreChatManager.getChatSessions()
                        val favoriteSessions = firestoreSessions.filter { it.isFavorite }
                        if (favoriteSessions.isNotEmpty()) {
                            val convertedSessions = favoriteSessions.map { firestoreSession ->
                                ChatSessionEntity(
                                    sessionId = firestoreSession.sessionId.hashCode().toLong(),
                                    userId = user.userId,
                                    title = firestoreSession.title,
                                    aiModelUsed = firestoreSession.aiModelUsed,
                                    category = firestoreSession.category,
                                    createdAt = firestoreSession.createdAt,
                                    updatedAt = firestoreSession.updatedAt,
                                    isFavorite = firestoreSession.isFavorite,
                                    isArchived = firestoreSession.isArchived,
                                    messageCount = firestoreSession.messageCount,
                                    lastMessagePreview = firestoreSession.lastMessagePreview
                                )
                            }
                            emit(convertedSessions)
                        } else {
                            // Fallback to local database
                            chatSessionDao.getFavoriteChatSessions(user.userId).collect { sessions ->
                                emit(sessions)
                            }
                        }
                    } catch (firestoreError: Exception) {
                        Log.w(TAG, "Firestore favorite retrieval failed, falling back to local database", firestoreError)
                        chatSessionDao.getFavoriteChatSessions(user.userId).collect { sessions ->
                            emit(sessions)
                        }
                    }
                } else {
                    emit(emptyList())
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting favorite chat history", e)
                emit(emptyList())
            }
        }
    }
    
    fun searchChatHistory(query: String): Flow<List<ChatSessionEntity>> {
        return flow {
            try {
                val user = getCurrentUserEntity()
                if (user != null) {
                    chatSessionDao.searchChatSessions(user.userId, query).collect { sessions ->
                        emit(sessions)
                    }
                } else {
                    emit(emptyList())
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error searching chat history", e)
                emit(emptyList())
            }
        }
    }
    
    fun getChatHistoryByCategory(category: String): Flow<List<ChatSessionEntity>> {
        return flow {
            try {
                val user = getCurrentUserEntity()
                if (user != null) {
                    chatSessionDao.getChatSessionsByCategory(user.userId, category).collect { sessions ->
                        emit(sessions)
                    }
                } else {
                    emit(emptyList())
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting chat history by category", e)
                emit(emptyList())
            }
        }
    }
    
    fun getChatHistoryByModel(model: String): Flow<List<ChatSessionEntity>> {
        return flow {
            try {
                val user = getCurrentUserEntity()
                if (user != null) {
                    chatSessionDao.getChatSessionsByModel(user.userId, model).collect { sessions ->
                        emit(sessions)
                    }
                } else {
                    emit(emptyList())
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting chat history by model", e)
                emit(emptyList())
            }
        }
    }
    
    suspend fun toggleChatFavorite(sessionId: Long): Boolean {
        return try {
            val session = chatSessionDao.getChatSessionById(sessionId)
            if (session != null) {
                chatSessionDao.updateFavoriteStatus(sessionId, !session.isFavorite)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error toggling chat favorite", e)
            false
        }
    }
    
    suspend fun archiveChat(sessionId: Long): Boolean {
        return try {
            chatSessionDao.updateArchiveStatus(sessionId, true)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error archiving chat", e)
            false
        }
    }
    
    suspend fun deleteChat(sessionId: Long): Boolean {
        return try {
            val session = chatSessionDao.getChatSessionById(sessionId)
            if (session != null) {
                chatSessionDao.deleteChatSession(session)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting chat", e)
            false
        }
    }
    
    suspend fun bulkDeleteChats(sessionIds: List<Long>): Boolean {
        return try {
            val user = getCurrentUserEntity() ?: return false
            chatSessionDao.deleteChatSessions(user.userId, sessionIds)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error bulk deleting chats", e)
            false
        }
    }
    
    // Chat Export
    suspend fun exportChat(sessionId: Long): ChatExportData? {
        return try {
            val session = chatSessionDao.getChatSessionById(sessionId)
            if (session != null) {
                val messages = chatMessageDao.getMessagesBySession(sessionId)
                val messageList = mutableListOf<ChatMessageEntity>()
                messages.collect { messageList.addAll(it) }
                
                val tokenCount = messageList.sumOf { it.tokenCount }
                
                ChatExportData(
                    sessionId = session.sessionId,
                    title = session.title,
                    aiModel = session.aiModelUsed,
                    category = session.category,
                    createdAt = session.createdAt,
                    messages = messageList,
                    messageCount = messageList.size,
                    tokenCount = tokenCount
                )
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error exporting chat", e)
            null
        }
    }
    
    suspend fun exportChatAsText(sessionId: Long): String? {
        return try {
            val exportData = exportChat(sessionId) ?: return null
            
            val sb = StringBuilder()
            sb.appendLine("Chat Export")
            sb.appendLine("=".repeat(50))
            sb.appendLine("Title: ${exportData.title}")
            sb.appendLine("AI Model: ${exportData.aiModel}")
            sb.appendLine("Category: ${exportData.category}")
            sb.appendLine("Created: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(exportData.createdAt)}")
            sb.appendLine("Messages: ${exportData.messageCount}")
            sb.appendLine("Tokens: ${exportData.tokenCount}")
            sb.appendLine("=".repeat(50))
            sb.appendLine()
            
            exportData.messages.forEach { message ->
                val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(message.timestamp)
                val sender = if (message.senderType == "user") "You" else "AI"
                sb.appendLine("[$timestamp] $sender:")
                sb.appendLine(message.content)
                sb.appendLine()
            }
            
            sb.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error exporting chat as text", e)
            null
        }
    }
    
    suspend fun exportAllChatsAsJson(): String? {
        return try {
            val user = getCurrentUserEntity() ?: return null
            
            val sessions = mutableListOf<ChatSessionEntity>()
            chatSessionDao.getActiveChatSessions(user.userId).collect { sessions.addAll(it) }
            
            val exportData = sessions.map { session ->
                exportChat(session.sessionId)
            }.filterNotNull()
            
            // Convert to JSON (simplified - in real app use proper JSON library)
            val jsonBuilder = StringBuilder()
            jsonBuilder.append("{")
            jsonBuilder.append("\"user_id\":\"${user.userId}\",")
            jsonBuilder.append("\"export_date\":\"${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}\",")
            jsonBuilder.append("\"total_chats\":${exportData.size},")
            jsonBuilder.append("\"chats\":[")
            
            exportData.forEachIndexed { index, chat ->
                jsonBuilder.append("{")
                jsonBuilder.append("\"session_id\":${chat.sessionId},")
                jsonBuilder.append("\"title\":\"${chat.title}\",")
                jsonBuilder.append("\"ai_model\":\"${chat.aiModel}\",")
                jsonBuilder.append("\"category\":\"${chat.category}\",")
                jsonBuilder.append("\"created_at\":\"${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(chat.createdAt)}\",")
                jsonBuilder.append("\"message_count\":${chat.messageCount},")
                jsonBuilder.append("\"token_count\":${chat.tokenCount}")
                jsonBuilder.append("}")
                if (index < exportData.size - 1) jsonBuilder.append(",")
            }
            
            jsonBuilder.append("]")
            jsonBuilder.append("}")
            
            jsonBuilder.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error exporting all chats as JSON", e)
            null
        }
    }
    
    suspend fun saveExportToFile(content: String, filename: String): String? {
        return try {
            val exportDir = File(context.getExternalFilesDir(null), EXPORT_DIR)
            if (!exportDir.exists()) {
                exportDir.mkdirs()
            }
            
            val file = File(exportDir, filename)
            file.writeText(content)
            
            file.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Error saving export to file", e)
            null
        }
    }
    
    // Statistics
    suspend fun getUserStatistics(): Map<String, Any> {
        return try {
            val user = getCurrentUserEntity() ?: return mutableMapOf<String, Any>()
            
            val totalChats = chatSessionDao.getActiveChatSessionCount(user.userId)
            val totalMessages = chatMessageDao.getTotalUserMessages(user.userId)
            val totalAiMessages = chatMessageDao.getTotalAiMessages(user.userId)
            val totalTokens = chatMessageDao.getTotalTokensUsed(user.userId)
            val averageProcessingTime = chatMessageDao.getAverageProcessingTime(user.userId)
            val storageUsed = usageAnalyticsDao.getTotalStorageUsed(user.userId)
            
            val categories = mutableListOf<String>()
            chatSessionDao.getCategoriesForUser(user.userId).collect { categories.addAll(it) }
            
            val modelsUsed = mutableListOf<String>()
            chatSessionDao.getModelsUsedByUser(user.userId).collect { modelsUsed.addAll(it) }
            
            mutableMapOf<String, Any>().apply {
                put("total_chats", totalChats)
                put("total_user_messages", totalMessages)
                put("total_ai_messages", totalAiMessages)
                put("total_tokens", totalTokens)
                put("average_processing_time_ms", averageProcessingTime)
                put("storage_used_mb", storageUsed)
                put("categories_used", categories)
                put("models_used", modelsUsed)
                put("account_age_days", (System.currentTimeMillis() - user.createdAt.time) / (24 * 60 * 60 * 1000))
                put("last_activity", user.lastLogin ?: Date(0))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting user statistics", e)
            mutableMapOf<String, Any>()
        }
    }
    
    // Webapp Integration Methods - TODO: Replace with UnifiedBackendClient functionality
    /*
    suspend fun getRecentChatHistory(limit: Int = 50): List<WebappSwitchingService.MobileChatMessage> {
        return try {
            val user = getCurrentUserEntity() ?: return emptyList()
            
            // Get recent chat sessions
            val sessions = chatSessionDao.getRecentChatSessions(user.userId, limit)
            val messages = mutableListOf<WebappSwitchingService.MobileChatMessage>()
            
            sessions.forEach { session ->
                val sessionMessages = chatMessageDao.getMessagesForSession(session.sessionId)
                sessionMessages.forEach { message ->
                    messages.add(
                        WebappSwitchingService.MobileChatMessage(
                            messageId = message.messageId.toString(),
                            content = message.content,
                            isUser = message.senderType == "user",
                            timestamp = message.timestamp,
                            aiModel = session.aiModelUsed,
                            provider = extractProviderFromModel(session.aiModelUsed)
                        )
                    )
                }
            }
            
            // Sort by timestamp and limit
            messages.sortedBy { it.timestamp }.takeLast(limit)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error getting recent chat history", e)
            emptyList()
        }
    }
    */
    
    private fun extractProviderFromModel(modelName: String): String {
        return when {
            modelName.contains("gpt", ignoreCase = true) -> "openai"
            modelName.contains("claude", ignoreCase = true) -> "anthropic"
            modelName.contains("gemini", ignoreCase = true) -> "google"
            else -> "openai"
        }
    }
    
    /**
     * Get real-time chat history from Firestore
     * This provides live updates across all devices
     */
    fun getChatHistoryFlow(): Flow<List<ChatSessionEntity>> {
        return flow {
            try {
                val user = getCurrentUserEntity()
                if (user != null) {
                    firestoreChatManager.getChatSessionsFlow().collect { firestoreSessions ->
                        val convertedSessions = firestoreSessions.map { firestoreSession ->
                            ChatSessionEntity(
                                sessionId = firestoreSession.sessionId.hashCode().toLong(),
                                userId = user.userId,
                                title = firestoreSession.title,
                                aiModelUsed = firestoreSession.aiModelUsed,
                                category = firestoreSession.category,
                                createdAt = firestoreSession.createdAt,
                                updatedAt = firestoreSession.updatedAt,
                                isFavorite = firestoreSession.isFavorite,
                                isArchived = firestoreSession.isArchived,
                                messageCount = firestoreSession.messageCount,
                                lastMessagePreview = firestoreSession.lastMessagePreview
                            )
                        }
                        emit(convertedSessions)
                    }
                } else {
                    emit(emptyList())
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting real-time chat history from Firestore", e)
                emit(emptyList())
            }
        }
    }
    
    /**
     * Get chat statistics from Firestore for accurate cross-device counts
     */
    suspend fun getChatStatisticsFromFirestore(): Map<String, Int> {
        return try {
            var stats = firestoreChatManager.getChatStatistics()
            Log.d(TAG, "Retrieved chat statistics from Firestore: $stats")

            if (stats["totalSessions"] == 0) {
                // Push any local data then refresh stats
                syncChatHistoryToFirestore()
                stats = firestoreChatManager.getChatStatistics()
            }

            if (stats["totalSessions"] == 0) {
                // Still no data in the cloud – fallback to local cache
                val sharedPrefStats = getChatStatisticsFromSharedPrefs()
                if (sharedPrefStats["totalSessions"]!! > 0) return sharedPrefStats
            }

            stats
        } catch (e: Exception) {
            Log.e(TAG, "Error getting chat statistics from Firestore", e)
            mutableMapOf<String, Int>()
        }
    }
    
    /**
     * Initialize profile with cross-device sync
     * Call this when user logs in to ensure their data is properly synced
     */
    suspend fun initializeUserProfile(): Boolean {
        return try {
            val user = getCurrentUserEntity()
            if (user == null) {
                Log.w(TAG, "Cannot initialize profile - no user logged in")
                return false
            }
            
            Log.i(TAG, "Initializing user profile for: ${user.email}")
            
            // Step 1: Force sync current SharedPreferences data to Firestore
            val syncSuccess = forceSyncCurrentChatHistoryToFirestore()
            
            // Step 2: Update user statistics in Firestore
            val firestoreStats = getChatStatisticsFromFirestore()
            
            // Step 3: Log success
            Log.i(TAG, "Profile initialization complete. Sync: $syncSuccess, Stats: ${firestoreStats.isNotEmpty()}")
            
            syncSuccess || firestoreStats.isNotEmpty()
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing user profile", e)
            false
        }
    }
    
    /**
     * Force sync current SharedPreferences data to Firestore, overwriting old data
     * This ensures the latest chat history is always in Firestore
     */
    suspend fun forceSyncCurrentChatHistoryToFirestore(): Boolean {
        return try {
            Log.e(TAG, "=== FORCE SYNCING CURRENT SHAREDPREFS TO FIRESTORE ===")
            
            // First, let's see what we have in SharedPreferences
            val sharedPreferences = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
            val savedChatsJson = sharedPreferences.getString("chat_history", "[]") ?: "[]"
            Log.e(TAG, "Force sync - SharedPrefs chat_history length: ${savedChatsJson.length}")
            Log.e(TAG, "Force sync - SharedPrefs sample: ${savedChatsJson.take(100)}")
            
            val syncResult = syncChatHistoryToFirestore()
            
            if (syncResult) {
                Log.e(TAG, "=== FORCE SYNC COMPLETED SUCCESSFULLY ===")
            } else {
                Log.e(TAG, "=== FORCE SYNC FAILED OR HAD NO DATA ===")
            }
            
            syncResult
        } catch (e: Exception) {
            Log.e(TAG, "Error in force sync", e)
            false
        }
    }
    
    /**
     * Clear old Firestore data and force a completely fresh sync
     * Use this to resolve issues with old/stale data
     */
    suspend fun clearFirestoreAndFreshSync(): Boolean {
        return try {
            Log.e(TAG, "=== CLEARING OLD FIRESTORE DATA AND DOING FRESH SYNC ===")

            firestoreChatManager.deleteChatHistory()

            Log.e(TAG, "Old Firestore data cleared, now doing fresh sync...")

            val syncResult = firestoreChatManager.syncChatData()

            if (syncResult) {
                Log.e(TAG, "=== FRESH SYNC COMPLETED SUCCESSFULLY ===")
            } else {
                Log.e(TAG, "=== FRESH SYNC FAILED ===")
            }

            syncResult
        } catch (e: Exception) {
            Log.e(TAG, "Error in clear and fresh sync", e)
            false
        }
    }
    
    /**
     * Completely clear all chat history from both SharedPreferences and Firestore
     * This is useful for resolving data conflicts and timing issues
     */
    suspend fun clearAllChatHistory(): Boolean {
        return try {
            Log.e(TAG, "=== CLEARING ALL CHAT HISTORY (SharedPreferences + Firestore) ===")
            
            var sharedPrefSuccess = false
            var firestoreSuccess = false
            var localDbSuccess = false
            
            // 1. Clear SharedPreferences chat history
            try {
                val sharedPreferences = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
                with(sharedPreferences.edit()) {
                    remove("chat_history")
                    remove("chatHistory") // Also remove the old key if it exists
                    apply()
                }
                sharedPrefSuccess = true
                Log.e(TAG, "SharedPreferences chat history cleared successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear SharedPreferences", e)
            }
            
            // 2. Clear Firestore chat history
            try {
                firestoreSuccess = firestoreChatManager.deleteChatHistory()
                if (firestoreSuccess) {
                    Log.e(TAG, "Firestore chat history cleared successfully")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear Firestore", e)
            }
            
            // 3. Clear local Room database chat history
            try {
                val user = getCurrentUserEntity()
                if (user != null) {
                    // Delete all chat sessions for this user
                    val allSessions = mutableListOf<ChatSessionEntity>()
                    chatSessionDao.getActiveChatSessions(user.userId).collect { sessions ->
                        allSessions.addAll(sessions)
                    }
                    
                    // Delete all messages and sessions for the user
                    chatMessageDao.deleteAllMessagesForUser(user.userId)
                    chatSessionDao.deleteAllForUser(user.userId)
                    
                    localDbSuccess = true
                    Log.e(TAG, "Local database chat history cleared successfully")
                } else {
                    Log.w(TAG, "No authenticated user, skipping local database deletion")
                    localDbSuccess = true // Not an error if user not authenticated
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear local database", e)
            }
            
            val overallSuccess = sharedPrefSuccess && firestoreSuccess && localDbSuccess
            
            Log.e(TAG, "=== CHAT HISTORY DELETION SUMMARY ===")
            Log.e(TAG, "SharedPreferences: ${if (sharedPrefSuccess) "✓ SUCCESS" else "✗ FAILED"}")
            Log.e(TAG, "Firestore: ${if (firestoreSuccess) "✓ SUCCESS" else "✗ FAILED"}")
            Log.e(TAG, "Local Database: ${if (localDbSuccess) "✓ SUCCESS" else "✗ FAILED"}")
            Log.e(TAG, "Overall: ${if (overallSuccess) "✓ SUCCESS" else "✗ PARTIAL/FAILED"}")
            
            overallSuccess
        } catch (e: Exception) {
            Log.e(TAG, "Error in clearAllChatHistory", e)
            false
        }
    }
    
    /**
     * Get recent chat activity for profile display
     */
    suspend fun getRecentChatActivity(limit: Int = 5): List<ChatSessionEntity> {
        return try {
            // Use Firebase Auth directly like getChatStatistics does
            val firebaseUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
            if (firebaseUser == null) {
                Log.e(TAG, "getRecentChatActivity: No Firebase user logged in")
                return emptyList()
            }
            
            Log.e(TAG, "getRecentChatActivity: Getting chat sessions for Firebase user ${firebaseUser.uid}")
            
            // Attempt to load sessions from Firestore first for true cross-device history
            var firestoreSessions = firestoreChatManager.getChatSessions()
            if (firestoreSessions.isEmpty()) {
                // No cloud data yet – push any local history then try again
                syncChatHistoryToFirestore()
                firestoreSessions = firestoreChatManager.getChatSessions()
            }

            if (firestoreSessions.isNotEmpty()) {
                val recentSessions = firestoreSessions
                    .sortedByDescending { it.updatedAt }
                    .take(limit)
                    .map { firestoreSession ->
                        ChatSessionEntity(
                            sessionId = firestoreSession.sessionId.hashCode().toLong(),
                            userId = firebaseUser.uid,
                            title = firestoreSession.title,
                            aiModelUsed = firestoreSession.aiModelUsed,
                            category = firestoreSession.category,
                            createdAt = firestoreSession.createdAt,
                            updatedAt = firestoreSession.updatedAt,
                            isFavorite = firestoreSession.isFavorite,
                            isArchived = firestoreSession.isArchived,
                            messageCount = firestoreSession.messageCount,
                            lastMessagePreview = firestoreSession.lastMessagePreview
                        )
                    }
                Log.d(TAG, "Retrieved ${recentSessions.size} recent chat sessions from Firestore")
                return recentSessions
            }

            // Cloud had no data; fall back to any locally cached history for offline access
            val sharedPrefSessions = getRecentChatFromSharedPrefs(limit)
            if (sharedPrefSessions.isNotEmpty()) {
                // Sync in background to make this data available on other devices
                kotlinx.coroutines.GlobalScope.launch { syncChatHistoryToFirestore() }
            }
            Log.d(TAG, "getRecentChatActivity: Returning ${sharedPrefSessions.size} sessions from SharedPreferences")
            sharedPrefSessions.take(limit)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting recent chat activity", e)
            emptyList()
        }
    }
    
    /**
     * Get recent chat sessions from SharedPreferences (fallback when Firestore is empty)
     */
    private suspend fun getRecentChatFromSharedPrefs(limit: Int): List<ChatSessionEntity> {
        return try {
            val sharedPreferences = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
            val savedChatsJson = sharedPreferences.getString("chat_history", "[]") ?: "[]"
            Log.e(TAG, "getRecentChatFromSharedPrefs: Raw data length: ${savedChatsJson.length}")
            if (savedChatsJson.length > 50) {
                Log.e(TAG, "getRecentChatFromSharedPrefs: First 500 chars: ${savedChatsJson.take(500)}")
                Log.e(TAG, "getRecentChatFromSharedPrefs: Last 200 chars: ${savedChatsJson.takeLast(200)}")
            }
            
            val sessions = mutableListOf<ChatSessionEntity>()
            
            // Use Firebase Auth directly since we're in Firestore-only mode
            val firebaseUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
            if (firebaseUser == null) {
                Log.e(TAG, "getRecentChatFromSharedPrefs: No Firebase user logged in - exiting early")
                return emptyList()
            }
            val userId = firebaseUser.uid // Use Firebase UID directly as String - safe for millions of users
            Log.e(TAG, "getRecentChatFromSharedPrefs: Got Firebase user: $userId")
            
            Log.e(TAG, "getRecentChatFromSharedPrefs: About to parse JSON array")
            val savedChatsArray = try {
                org.json.JSONArray(savedChatsJson)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse JSON array from SharedPreferences", e)
                Log.e(TAG, "Raw JSON that failed: ${savedChatsJson}")
                return emptyList()
            }
            
            Log.e(TAG, "getRecentChatFromSharedPrefs: Processing ${savedChatsArray.length()} chat objects")
            
            for (i in 0 until savedChatsArray.length()) {
                try {
                    val chatObject = savedChatsArray.getJSONObject(i)
                    val conversationId = chatObject.optString("id", "")
                    val messagesArray = chatObject.optJSONArray("messages")
                    
                    Log.e(TAG, "Processing chat $i: id=$conversationId, hasMessages=${messagesArray != null}")
                    
                    if (messagesArray == null) {
                        Log.e(TAG, "Chat $i has no messages array, skipping")
                        continue
                    }
                    
                    Log.e(TAG, "Chat $i: messages count=${messagesArray.length()}")
                    
                    if (messagesArray.length() > 0) {
                    // Use the title from the chat object if available, otherwise use first message
                    val chatTitle = chatObject.optString("title", "")
                    val firstMessage = messagesArray.getJSONObject(0)
                    val lastMessage = messagesArray.getJSONObject(messagesArray.length() - 1)
                    
                    val title = if (chatTitle.isNotEmpty()) {
                        chatTitle.take(50)
                    } else {
                        firstMessage.optString("content", "Untitled Chat").take(50)
                    }
                    val lastMessagePreview = lastMessage.optString("content", "").take(100)
                    
                    Log.e(TAG, "Creating session: title='$title', messages=${messagesArray.length()}")
                    
                    val session = ChatSessionEntity(
                        sessionId = conversationId.hashCode().toLong(),
                        userId = userId,
                        title = title,
                        aiModelUsed = "gpt-3.5-turbo", // Default model
                        category = "general",
                        createdAt = Date(System.currentTimeMillis()),
                        updatedAt = Date(System.currentTimeMillis()),
                        isFavorite = false,
                        isArchived = false,
                        messageCount = messagesArray.length(),
                        lastMessagePreview = lastMessagePreview
                    )
                    
                    sessions.add(session)
                    Log.e(TAG, "Added session to list: ${sessions.size} total sessions")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing chat $i", e)
                }
            }
            
            Log.e(TAG, "getRecentChatFromSharedPrefs: Returning ${sessions.size} sessions")
            // Return most recent sessions first, limited by the specified limit
            sessions.sortedByDescending { it.updatedAt }.take(limit)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting chat from SharedPreferences", e)
            emptyList()
        }
    }
    
    /**
     * Sync chat history from SharedPreferences to Firestore for cross-device access
     */
    suspend fun syncChatHistoryToFirestore(): Boolean = try {
        firestoreChatManager.syncChatData()
    } catch (e: Exception) {
        Log.e(TAG, "Error syncing chat history to Firestore", e)
        false
    }
    
    /**
     * Get chat statistics from SharedPreferences
     */
    private fun getChatStatisticsFromSharedPrefs(): Map<String, Int> {
        return try {
            val sharedPreferences = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
            val savedChatsJson = sharedPreferences.getString("chat_history", "[]") ?: "[]"
            
            val savedChatsArray = org.json.JSONArray(savedChatsJson)
            var totalMessages = 0
            
            for (i in 0 until savedChatsArray.length()) {
                val chatObject = savedChatsArray.getJSONObject(i)
                val messagesArray = chatObject.optJSONArray("messages")
                if (messagesArray != null) {
                    totalMessages += messagesArray.length()
                }
            }
            
            mutableMapOf<String, Int>().apply {
                put("totalSessions", savedChatsArray.length())
                put("totalMessages", totalMessages)
                put("favoriteChats", 0) // Not tracked in SharedPreferences
                put("archivedChats", 0)  // Not tracked in SharedPreferences
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting statistics from SharedPreferences", e)
            mutableMapOf<String, Int>().apply {
                put("totalSessions", 0)
                put("totalMessages", 0)
                put("favoriteChats", 0)
                put("archivedChats", 0)
            }
        }
    }
    
    /**
     * Debug method to inspect all SharedPreferences content
     */
    suspend fun debugSharedPreferences() {
        try {
            Log.e(TAG, "=== DEBUGGING ALL SHAREDPREFS CONTENT ===")
            val sharedPrefs = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
            val allKeys = sharedPrefs.all
            
            Log.e(TAG, "Total SharedPrefs keys: ${allKeys.size}")
            for ((key, value) in allKeys) {
                if (key.contains("chat") || key.contains("history") || key.contains("message")) {
                    val valueStr = value.toString()
                    Log.e(TAG, "Key: $key, Value length: ${valueStr.length}")
                    if (valueStr.length > 20) {
                        Log.e(TAG, "Key: $key, Sample: ${valueStr.take(200)}")
                    } else {
                        Log.e(TAG, "Key: $key, Full value: $valueStr")
                    }
                }
            }
            
            // Check if there are any other keys that might contain chat data
            Log.e(TAG, "All keys in SharedPrefs:")
            allKeys.keys.forEach { key ->
                Log.e(TAG, "  - $key")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error debugging SharedPreferences", e)
        }
    }
}