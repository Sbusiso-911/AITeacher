package com.playstudio.aiteacher.profile

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
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
    
    companion object {
        private const val TAG = "ProfileManager"
        private const val PROFILE_PICTURES_DIR = "profile_pictures"
        private const val EXPORT_DIR = "chat_exports"
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
            val user = authService.getCurrentUser() ?: return null
            val subscription = subscriptionDao.getActiveSubscription(user.userId)
            
            // Get statistics
            val totalChats = chatSessionDao.getActiveChatSessionCount(user.userId)
            val totalMessages = chatMessageDao.getTotalUserMessages(user.userId)
            val totalTokens = chatMessageDao.getTotalTokensUsed(user.userId)
            val favoriteChatsFlow = chatSessionDao.getFavoriteChatSessions(user.userId)
            var favoriteChats = 0
            favoriteChatsFlow.collect { favoriteChats = it.size }
            val storageUsed = usageAnalyticsDao.getTotalStorageUsed(user.userId)
            
            ProfileData(
                user = user,
                subscription = subscription,
                totalChats = totalChats,
                totalMessages = totalMessages,
                totalTokens = totalTokens,
                favoriteChats = favoriteChats,
                storageUsed = storageUsed,
                joinDate = user.createdAt,
                lastActivity = user.lastLogin
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error getting profile data", e)
            null
        }
    }
    
    suspend fun updateProfilePicture(bitmap: Bitmap): String? {
        return try {
            val user = authService.getCurrentUser() ?: return null
            
            // Create profile pictures directory
            val profileDir = File(context.filesDir, PROFILE_PICTURES_DIR)
            if (!profileDir.exists()) {
                profileDir.mkdirs()
            }
            
            // Generate unique filename
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val filename = "profile_${user.userId}_$timestamp.jpg"
            val file = File(profileDir, filename)
            
            // Save bitmap to file
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
            
            // Update user profile
            val profileUrl = file.absolutePath
            userDao.updateProfilePicture(user.userId, profileUrl)
            
            profileUrl
        } catch (e: Exception) {
            Log.e(TAG, "Error updating profile picture", e)
            null
        }
    }
    
    suspend fun getProfilePicture(): String? {
        return try {
            val user = authService.getCurrentUser()
            user?.profilePictureUrl
        } catch (e: Exception) {
            Log.e(TAG, "Error getting profile picture", e)
            null
        }
    }
    
    suspend fun updateThemePreference(theme: String): Boolean {
        return try {
            val user = authService.getCurrentUser() ?: return false
            userDao.updateThemePreference(user.userId, theme)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error updating theme preference", e)
            false
        }
    }
    
    suspend fun updateLanguageSetting(language: String): Boolean {
        return try {
            val user = authService.getCurrentUser() ?: return false
            userDao.updateLanguageSetting(user.userId, language)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error updating language setting", e)
            false
        }
    }
    
    suspend fun updateNotificationSettings(enabled: Boolean): Boolean {
        return try {
            val user = authService.getCurrentUser() ?: return false
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
                val user = authService.getCurrentUser()
                if (user != null) {
                    chatSessionDao.getActiveChatSessions(user.userId).collect { sessions ->
                        emit(sessions)
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
                val user = authService.getCurrentUser()
                if (user != null) {
                    chatSessionDao.getFavoriteChatSessions(user.userId).collect { sessions ->
                        emit(sessions)
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
                val user = authService.getCurrentUser()
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
                val user = authService.getCurrentUser()
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
                val user = authService.getCurrentUser()
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
            val user = authService.getCurrentUser() ?: return false
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
            val user = authService.getCurrentUser() ?: return null
            
            val sessions = mutableListOf<ChatSessionEntity>()
            chatSessionDao.getActiveChatSessions(user.userId).collect { sessions.addAll(it) }
            
            val exportData = sessions.map { session ->
                exportChat(session.sessionId)
            }.filterNotNull()
            
            // Convert to JSON (simplified - in real app use proper JSON library)
            val jsonBuilder = StringBuilder()
            jsonBuilder.append("{")
            jsonBuilder.append("\"user_id\":${user.userId},")
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
            val user = authService.getCurrentUser() ?: return emptyMap()
            
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
            
            mapOf(
                "total_chats" to totalChats,
                "total_user_messages" to totalMessages,
                "total_ai_messages" to totalAiMessages,
                "total_tokens" to totalTokens,
                "average_processing_time_ms" to averageProcessingTime,
                "storage_used_mb" to storageUsed,
                "categories_used" to categories,
                "models_used" to modelsUsed,
                "account_age_days" to ((System.currentTimeMillis() - user.createdAt.time) / (24 * 60 * 60 * 1000)),
                "last_activity" to (user.lastLogin ?: Date(0))
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error getting user statistics", e)
            emptyMap()
        }
    }
    
    // Webapp Integration Methods - TODO: Replace with UnifiedBackendClient functionality
    /*
    suspend fun getRecentChatHistory(limit: Int = 50): List<WebappSwitchingService.MobileChatMessage> {
        return try {
            val user = authService.getCurrentUser() ?: return emptyList()
            
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
}