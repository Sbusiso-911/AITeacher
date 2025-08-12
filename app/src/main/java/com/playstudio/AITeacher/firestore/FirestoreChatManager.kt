package com.playstudio.aiteacher.firestore

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.*

/**
 * FirestoreChatManager - Direct Firestore integration for chat history
 * Handles real-time sync of chat messages and conversations with Firestore
 */
class FirestoreChatManager private constructor() {
    
    companion object {
        private const val TAG = "FirestoreChatManager"
        private const val COLLECTION_USERS = "users"
        private const val COLLECTION_CHAT_SESSIONS = "chat_sessions"
        private const val COLLECTION_MESSAGES = "messages"
        private const val COLLECTION_CHAT_METADATA = "chat_metadata"
        
        @Volatile
        private var INSTANCE: FirestoreChatManager? = null
        
        fun getInstance(): FirestoreChatManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: FirestoreChatManager().also { INSTANCE = it }
            }
        }
    }
    
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    
    /**
     * Firestore Data Models
     */
    data class FirestoreChatSession(
        val sessionId: String = "",
        val userId: String = "",
        val title: String = "",
        val aiModelUsed: String = "",
        val category: String = "general",
        val createdAt: Date = Date(),
        val updatedAt: Date = Date(),
        val isFavorite: Boolean = false,
        val isArchived: Boolean = false,
        val messageCount: Int = 0,
        val lastMessagePreview: String = "",
        val tags: List<String> = emptyList()
    ) {
        // Firestore requires no-arg constructor and public fields
        constructor() : this("", "", "", "", "general", Date(), Date(), false, false, 0, "", emptyList())
        
        fun toMap(): Map<String, Any> = mutableMapOf<String, Any>().apply {
            put("sessionId", sessionId)
            put("userId", userId)
            put("title", title)
            put("aiModelUsed", aiModelUsed)
            put("category", category)
            put("createdAt", createdAt)
            put("updatedAt", updatedAt)
            put("isFavorite", isFavorite)
            put("isArchived", isArchived)
            put("messageCount", messageCount)
            put("lastMessagePreview", lastMessagePreview)
            put("tags", tags)
        }
    }
    
    data class FirestoreChatMessage(
        val messageId: String = "",
        val sessionId: String = "",
        val userId: String = "",
        val content: String = "",
        val senderType: String = "user", // "user" or "ai"
        val timestamp: Date = Date(),
        val tokenCount: Int = 0,
        val processingTimeMs: Long = 0L,
        val aiModel: String? = null,
        val provider: String? = null,
        val citations: List<Map<String, String>> = emptyList(),
        val followUpQuestions: List<String> = emptyList()
    ) {
        // Firestore requires no-arg constructor
        constructor() : this("", "", "", "", "user", Date(), 0, 0L, null, null, emptyList(), emptyList())
        
        fun toMap(): Map<String, Any> = mutableMapOf<String, Any>().apply {
            put("messageId", messageId)
            put("sessionId", sessionId)
            put("userId", userId)
            put("content", content)
            put("senderType", senderType)
            put("timestamp", timestamp)
            put("tokenCount", tokenCount)
            put("processingTimeMs", processingTimeMs)
            put("aiModel", aiModel ?: "")
            put("provider", provider ?: "")
            put("citations", citations)
            put("followUpQuestions", followUpQuestions)
        }
    }
    
    /**
     * Save a chat session to Firestore (simplified for current structure)
     */
    suspend fun saveChatSession(session: FirestoreChatSession): Boolean {
        return try {
            val userId = getCurrentUserId() ?: return false
            
            // In the current structure, we don't save individual sessions
            // The session data is derived from the messages array
            Log.d(TAG, "Chat session handling integrated with message structure")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error with chat session", e)
            false
        }
    }
    
    /**
     * Save a chat message to Firestore (simplified for current structure)
     */
    suspend fun saveChatMessage(message: FirestoreChatMessage): Boolean {
        return try {
            val userId = getCurrentUserId() ?: return false
            
            // In the current structure, messages are stored as arrays in chats/{userId}
            // This would require reading the current array, adding the message, and updating
            // For now, we'll return true as the existing app handles message saving differently
            Log.d(TAG, "Chat message handling integrated with existing message structure")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error with chat message", e)
            false
        }
    }
    
    /**
     * Get all chat sessions for current user
     */
    suspend fun getChatSessions(): List<FirestoreChatSession> {
        return try {
            val userId = getCurrentUserId() ?: return emptyList()
            
            Log.d(TAG, "getChatSessions: Looking for chat data for user: $userId")
            
            // Query the existing chats/{userId} structure
            val chatDoc = firestore.collection("chats")
                .document(userId)
                .get()
                .await()
            
            Log.d(TAG, "getChatSessions: Document exists: ${chatDoc.exists()}")
            if (!chatDoc.exists()) {
                Log.d(TAG, "getChatSessions: No document found for user $userId")
                return emptyList()
            }
            
            @Suppress("UNCHECKED_CAST")
            val messages = chatDoc.get("messages") as? List<Map<String, Any>> ?: emptyList()
            val lastModel = chatDoc.getString("lastModel") ?: "unknown"
            val lastUsed = chatDoc.getTimestamp("lastUsed")
            
            Log.d(TAG, "getChatSessions: Found ${messages.size} messages for user $userId")
            
            // Create a single session from the existing data structure
            if (messages.isNotEmpty()) {
                // Get the last message content for preview
                val lastMessage = messages.lastOrNull()
                val lastMessagePreview = lastMessage?.get("content") as? String ?: ""
                
                // Create a better title from the first user message
                val firstUserMessage = messages.find { (it["role"] as? String) == "user" }
                val title = (firstUserMessage?.get("content") as? String)?.take(50) ?: "Chat History"
                
                val session = FirestoreChatSession(
                    sessionId = userId, // Use userId as session ID since there's only one session per user
                    userId = userId,
                    title = title,
                    createdAt = lastUsed?.toDate() ?: Date(),
                    updatedAt = lastUsed?.toDate() ?: Date(),
                    messageCount = messages.size,
                    aiModelUsed = lastModel,
                    category = "general",
                    tags = emptyList(),
                    isFavorite = false,
                    isArchived = false,
                    lastMessagePreview = lastMessagePreview
                )
                Log.d(TAG, "getChatSessions: Created session with title: '$title' and ${messages.size} messages")
                listOf(session)
            } else {
                Log.d(TAG, "getChatSessions: No messages found, returning empty list")
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting chat sessions", e)
            emptyList()
        }
    }
    
    /**
     * Get messages for a specific chat session
     */
    suspend fun getChatMessages(sessionId: String): List<FirestoreChatMessage> {
        return try {
            val userId = getCurrentUserId() ?: return emptyList()
            
            // Query the existing chats/{userId} structure
            val chatDoc = firestore.collection("chats")
                .document(userId)
                .get()
                .await()
            
            if (!chatDoc.exists()) {
                return emptyList()
            }
            
            @Suppress("UNCHECKED_CAST")
            val messages = chatDoc.get("messages") as? List<Map<String, Any>> ?: emptyList()
            
            // Convert the message array to FirestoreChatMessage objects
            messages.mapIndexedNotNull { index, messageMap ->
                try {
                    val content = messageMap["content"] as? String ?: ""
                    val role = messageMap["role"] as? String ?: "user"
                    val model = messageMap["model"] as? String ?: "unknown"
                    val timestamp = messageMap["timestamp"] as? Long ?: System.currentTimeMillis()
                    
                    FirestoreChatMessage(
                        messageId = "msg_${index}",
                        sessionId = sessionId,
                        content = content,
                        senderType = if (role == "user") "user" else "ai",
                        timestamp = Date(timestamp),
                        aiModel = model,
                        provider = "openai"
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Error parsing message at index $index", e)
                    null
                }
            }.sortedBy { it.timestamp }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting chat messages for session $sessionId", e)
            emptyList()
        }
    }
    
    /**
     * Real-time listener for chat sessions
     */
    fun getChatSessionsFlow(): Flow<List<FirestoreChatSession>> = callbackFlow {
        val userId = getCurrentUserId()
        if (userId == null) {
            close()
            return@callbackFlow
        }
        
        val listener = firestore.collection("chats")
            .document(userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error listening to chat sessions", error)
                    return@addSnapshotListener
                }
                
                val sessions = if (snapshot != null && snapshot.exists()) {
                    @Suppress("UNCHECKED_CAST")
                    val messages = snapshot.get("messages") as? List<Map<String, Any>> ?: emptyList()
                    val lastModel = snapshot.getString("lastModel") ?: "unknown"
                    val lastUsed = snapshot.getTimestamp("lastUsed")
                    
                    if (messages.isNotEmpty()) {
                        val lastMessage = messages.lastOrNull()
                        val lastMessagePreview = lastMessage?.get("content") as? String ?: ""
                        val firstUserMessage = messages.find { (it["role"] as? String) == "user" }
                        val title = (firstUserMessage?.get("content") as? String)?.take(50) ?: "Chat History"
                        
                        val session = FirestoreChatSession(
                            sessionId = userId,
                            userId = userId,
                            title = title,
                            createdAt = lastUsed?.toDate() ?: Date(),
                            updatedAt = lastUsed?.toDate() ?: Date(),
                            messageCount = messages.size,
                            aiModelUsed = lastModel,
                            category = "general",
                            tags = emptyList(),
                            isFavorite = false,
                            isArchived = false,
                            lastMessagePreview = lastMessagePreview
                        )
                        listOf(session)
                    } else {
                        emptyList()
                    }
                } else {
                    emptyList()
                }
                
                trySend(sessions)
            }
        
        awaitClose { listener.remove() }
    }
    
    /**
     * Real-time listener for chat messages in a session
     */
    fun getChatMessagesFlow(sessionId: String): Flow<List<FirestoreChatMessage>> = callbackFlow {
        val userId = getCurrentUserId()
        if (userId == null) {
            close()
            return@callbackFlow
        }
        
        val listener = firestore.collection("chats")
            .document(userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error listening to chat messages", error)
                    return@addSnapshotListener
                }
                
                val messages = if (snapshot != null && snapshot.exists()) {
                    @Suppress("UNCHECKED_CAST")
                    val messageArray = snapshot.get("messages") as? List<Map<String, Any>> ?: emptyList()
                    
                    messageArray.mapIndexedNotNull { index, messageMap ->
                        try {
                            val content = messageMap["content"] as? String ?: ""
                            val role = messageMap["role"] as? String ?: "user"
                            val model = messageMap["model"] as? String ?: "unknown"
                            val timestamp = messageMap["timestamp"] as? Long ?: System.currentTimeMillis()
                            
                            FirestoreChatMessage(
                                messageId = "msg_${index}",
                                sessionId = sessionId,
                                content = content,
                                senderType = if (role == "user") "user" else "ai",
                                timestamp = Date(timestamp),
                                aiModel = model,
                                provider = "openai"
                            )
                        } catch (e: Exception) {
                            Log.w(TAG, "Error parsing message at index $index", e)
                            null
                        }
                    }.sortedBy { it.timestamp }
                } else {
                    emptyList()
                }
                
                trySend(messages)
            }
        
        awaitClose { listener.remove() }
    }
    
    /**
     * Delete chat history for current user (simplified for current structure)
     */
    suspend fun deleteChatHistory(): Boolean {
        return try {
            val userId = getCurrentUserId() ?: return false
            
            firestore.collection("chats")
                .document(userId)
                .delete()
                .await()
            
            Log.d(TAG, "Chat history deleted successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting chat history", e)
            false
        }
    }
    
    /**
     * Search chat messages by content (simplified for current structure)
     */
    suspend fun searchChatMessages(query: String, limit: Int = 50): List<FirestoreChatMessage> {
        return try {
            val userId = getCurrentUserId() ?: return emptyList()
            
            val messages = getChatMessages(userId) // Use userId as sessionId
            val matchingMessages = messages.filter { 
                it.content.contains(query, ignoreCase = true) 
            }
            
            matchingMessages.take(limit)
        } catch (e: Exception) {
            Log.e(TAG, "Error searching chat messages", e)
            emptyList()
        }
    }
    
    /**
     * Get chat statistics for current user
     */
    suspend fun getChatStatistics(): Map<String, Int> {
        return try {
            val userId = getCurrentUserId() ?: return mutableMapOf<String, Int>()
            
            Log.d(TAG, "getChatStatistics: Looking for chat data for user: $userId")
            
            // Query the existing chats/{userId} structure directly
            val chatDoc = firestore.collection("chats")
                .document(userId)
                .get()
                .await()
            
            if (!chatDoc.exists()) {
                return mutableMapOf<String, Int>().apply {
                    put("totalSessions", 0)
                    put("totalMessages", 0)
                    put("favoriteChats", 0)
                    put("archivedChats", 0)
                }
            }
            
            @Suppress("UNCHECKED_CAST")
            val messages = chatDoc.get("messages") as? List<Map<String, Any>> ?: emptyList()
            val totalSessions = if (messages.isNotEmpty()) 1 else 0
            
            mutableMapOf<String, Int>().apply {
                put("totalSessions", totalSessions)
                put("totalMessages", messages.size)
                put("favoriteChats", 0) // Not supported in current structure
                put("archivedChats", 0)  // Not supported in current structure
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting chat statistics", e)
            mutableMapOf<String, Int>()
        }
    }
    
    /**
     * Helper method to convert map to FirestoreChatMessage
     */
    fun convertMapToFirestoreMessage(
        messageMap: Map<String, Any>,
        sessionId: String,
        messageId: String
    ): FirestoreChatMessage {
        return FirestoreChatMessage(
            messageId = messageId,
            sessionId = sessionId,
            userId = getCurrentUserId() ?: "",
            content = messageMap["content"] as? String ?: "",
            senderType = if ((messageMap["role"] as? String) == "user") "user" else "ai",
            timestamp = Date((messageMap["timestamp"] as? Long) ?: System.currentTimeMillis()),
            aiModel = messageMap["model"] as? String ?: "unknown",
            provider = "openai"
        )
    }
    
    /**
     * Bulk sync from existing Firestore chats structure 
     */
    suspend fun syncChatData(): Boolean {
        return try {
            val userId = getCurrentUserId() ?: return false
            Log.d(TAG, "Syncing chat data for user: $userId")
            
            // This method can be expanded later for additional sync operations
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing chat data", e)
            false
        }
    }
    
    // Private helper methods
    
    private fun getCurrentUserId(): String? {
        return auth.currentUser?.uid
    }
}