package com.playstudio.AITeacher

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.playstudio.aiteacher.history.DatabaseProvider
import com.playstudio.aiteacher.history.MessageEntity
import com.playstudio.aiteacher.history.ConversationEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first

object ChatHistoryUtils {

    private const val PREFS_NAME = "ChatPrefs"
    private const val CHAT_HISTORY_KEY = "chatHistory"
    private const val TAG = "ChatHistoryUtils"

    data class ChatMessage(
        val id: String = "",
        val message: String,
        val isUser: Boolean,
        val timestamp: Long,
        val aiModel: String? = null,
        val provider: String? = null,
        val conversationId: String = ""
    )

    fun saveChatHistory(context: Context, chatHistory: List<String>) {
        val sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        val json = Gson().toJson(chatHistory)
        editor.putString(CHAT_HISTORY_KEY, json)
        editor.apply()
    }

    fun getChatHistory(context: Context): List<String> {
        val sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = sharedPreferences.getString(CHAT_HISTORY_KEY, null)
        return if (json != null) {
            val type = object : TypeToken<List<String>>() {}.type
            Gson().fromJson(json, type)
        } else {
            emptyList()
        }
    }

    /**
     * Get all chat history from Room database for webapp sync
     */
    suspend fun getAllChatHistory(context: Context): List<ChatMessage> {
        return try {
            val database = DatabaseProvider.database
            val conversations = database.conversationDao().getConversations().first()
            val allMessages = mutableListOf<ChatMessage>()
            
            conversations.forEach { conversation ->
                val messages = database.messageDao().getMessages(conversation.id).first()
                val chatMessages = messages.map { messageEntity ->
                    ChatMessage(
                        id = messageEntity.id,
                        message = messageEntity.content,
                        isUser = messageEntity.isUser,
                        timestamp = messageEntity.timestamp,
                        aiModel = null,
                        provider = null,
                        conversationId = messageEntity.conversationId
                    )
                }
                allMessages.addAll(chatMessages)
            }
            
            allMessages.sortedBy { it.timestamp }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting all chat history", e)
            emptyList()
        }
    }

    /**
     * Sync chat history from webapp format to local database
     */
    fun syncFromWebapp(context: Context, webappMessages: List<Map<String, Any>>) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val database = DatabaseProvider.database
                
                // Create or get a conversation for webapp sync
                val conversationTitle = "Synced from Webapp - ${System.currentTimeMillis()}"
                val conversation = ConversationEntity(
                    id = "webapp_sync_${System.currentTimeMillis()}",
                    title = conversationTitle,
                    lastUpdated = System.currentTimeMillis()
                )
                
                database.conversationDao().insertConversation(conversation)
                val conversationId = conversation.id
                
                // Convert webapp messages to local format
                val localMessages = webappMessages.mapIndexed { index, webappMsg ->
                    MessageEntity(
                        id = "webapp_msg_${System.currentTimeMillis()}_$index",
                        conversationId = conversationId,
                        content = webappMsg["content"] as? String ?: "",
                        isUser = (webappMsg["role"] as? String) == "user",
                        timestamp = (webappMsg["timestamp"] as? Number)?.toLong() ?: System.currentTimeMillis()
                    )
                }
                
                // Insert messages
                localMessages.forEach { message ->
                    database.messageDao().insertMessage(message)
                }
                
                Log.d(TAG, "Successfully synced ${localMessages.size} messages from webapp")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error syncing chat history from webapp", e)
            }
        }
    }

    /**
     * Get recent chat history for specific conversation
     */
    suspend fun getConversationHistory(context: Context, conversationId: String): List<ChatMessage> {
        return try {
            val database = DatabaseProvider.database
            val messageEntities = database.messageDao().getMessages(conversationId).first()
            
            messageEntities.map { messageEntity ->
                ChatMessage(
                    id = messageEntity.id,
                    message = messageEntity.content,
                    isUser = messageEntity.isUser,
                    timestamp = messageEntity.timestamp,
                    aiModel = null,
                    provider = null,
                    conversationId = messageEntity.conversationId
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting conversation history", e)
            emptyList()
        }
    }

    /**
     * Save new message to database and sync to cloud if needed
     */
    fun saveMessageWithSync(
        context: Context,
        message: String,
        isUser: Boolean,
        conversationId: String,
        aiModel: String? = null,
        provider: String? = null
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val database = DatabaseProvider.database
                val messageEntity = MessageEntity(
                    id = "msg_${System.currentTimeMillis()}_${(0..999).random()}",
                    conversationId = conversationId,
                    content = message,
                    isUser = isUser,
                    timestamp = System.currentTimeMillis()
                )
                
                database.messageDao().insertMessage(messageEntity)
                
                // Auto-sync to cloud periodically
                val sharedPrefs = context.getSharedPreferences("sync_status", Context.MODE_PRIVATE)
                val lastSync = sharedPrefs.getLong("last_chat_sync", 0)
                val currentTime = System.currentTimeMillis()
                
                // Sync every 30 minutes
                if (currentTime - lastSync > 30 * 60 * 1000) {
                    syncToCloud(context)
                    sharedPrefs.edit().putLong("last_chat_sync", currentTime).apply()
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error saving message with sync", e)
            }
        }
    }

    /**
     * Sync local chat history to cloud
     */
    private suspend fun syncToCloud(context: Context) {
        try {
            // Cloud sync will be implemented later
            // This will be called automatically by the switching service
            Log.d(TAG, "Chat history sync to cloud initiated")
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing chat history to cloud", e)
        }
    }

    /**
     * Clear all chat history (both local and request cloud cleanup)
     */
    fun clearAllHistory(context: Context, onComplete: (Boolean) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val database = DatabaseProvider.database
                database.conversationDao().deleteAllConversations()
                
                // Also clear shared preferences
                val sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                sharedPreferences.edit().clear().apply()
                
                withContext(Dispatchers.Main) {
                    onComplete(true)
                }
                
                Log.d(TAG, "Successfully cleared all chat history")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error clearing chat history", e)
                withContext(Dispatchers.Main) {
                    onComplete(false)
                }
            }
        }
    }

    /**
     * Get conversation statistics
     */
    suspend fun getConversationStats(context: Context): Map<String, Int> {
        return try {
            val database = DatabaseProvider.database
            val conversations = database.conversationDao().getConversations().first()
            val totalConversations = conversations.size
            var totalMessages = 0
            
            conversations.forEach { conversation ->
                val messages = database.messageDao().getMessages(conversation.id).first()
                totalMessages += messages.size
            }
            
            mapOf(
                "totalConversations" to totalConversations,
                "totalMessages" to totalMessages,
                "todayMessages" to 0 // TODO: Implement today message counting
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error getting conversation stats", e)
            mapOf(
                "totalConversations" to 0,
                "totalMessages" to 0,
                "todayMessages" to 0
            )
        }
    }
}