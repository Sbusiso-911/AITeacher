package com.playstudio.aiteacher.firestore

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import java.util.Date

/**
 * FirestoreChatManager - Direct Firestore integration for chat history
 * Stores chat sessions and messages in a structure that supports multiple
 * conversations per user:
 * users/{userId}/chat_sessions/{sessionId}/messages/{messageId}
 */
class FirestoreChatManager private constructor() {

    companion object {
        private const val TAG = "FirestoreChatManager"
        private const val COLLECTION_USERS = "users"
        private const val COLLECTION_CHAT_SESSIONS = "chat_sessions"
        private const val COLLECTION_MESSAGES = "messages"

        @Volatile
        private var INSTANCE: FirestoreChatManager? = null

        fun getInstance(): FirestoreChatManager =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: FirestoreChatManager().also { INSTANCE = it }
            }
    }

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

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
        fun toMap() = mapOf(
            "sessionId" to sessionId,
            "userId" to userId,
            "title" to title,
            "aiModelUsed" to aiModelUsed,
            "category" to category,
            "createdAt" to createdAt,
            "updatedAt" to updatedAt,
            "isFavorite" to isFavorite,
            "isArchived" to isArchived,
            "messageCount" to messageCount,
            "lastMessagePreview" to lastMessagePreview,
            "tags" to tags
        )
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
        fun toMap() = mapOf(
            "messageId" to messageId,
            "sessionId" to sessionId,
            "userId" to userId,
            "content" to content,
            "senderType" to senderType,
            "timestamp" to timestamp,
            "tokenCount" to tokenCount,
            "processingTimeMs" to processingTimeMs,
            "aiModel" to aiModel,
            "provider" to provider,
            "citations" to citations,
            "followUpQuestions" to followUpQuestions
        )
    }

    private fun sessionsCollection(userId: String) =
        firestore.collection(COLLECTION_USERS).document(userId).collection(COLLECTION_CHAT_SESSIONS)

    private fun messagesCollection(userId: String, sessionId: String) =
        sessionsCollection(userId).document(sessionId).collection(COLLECTION_MESSAGES)

    /** Save or update a chat session document. */
    suspend fun saveChatSession(session: FirestoreChatSession): Boolean {
        return try {
            val userId = getCurrentUserId() ?: return false
            sessionsCollection(userId).document(session.sessionId)
                .set(session.copy(userId = userId).toMap(), SetOptions.merge()).await()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error saving chat session", e)
            false
        }
    }

    /** Save a single chat message under its session and update session metadata. */
    suspend fun saveChatMessage(message: FirestoreChatMessage): Boolean {
        return try {
            val userId = getCurrentUserId() ?: return false
            val messageData = message.copy(userId = userId)

            messagesCollection(userId, messageData.sessionId)
                .document(messageData.messageId)
                .set(messageData.toMap(), SetOptions.merge()).await()

            sessionsCollection(userId).document(messageData.sessionId)
                .set(
                    mapOf(
                        "updatedAt" to messageData.timestamp,
                        "messageCount" to FieldValue.increment(1),
                        "lastMessagePreview" to messageData.content.take(100),
                        "aiModelUsed" to (messageData.aiModel ?: ""),
                    ),
                    SetOptions.merge(),
                ).await()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error saving chat message", e)
            false
        }
    }

    /** Retrieve all chat sessions for the current user. */
    suspend fun getChatSessions(): List<FirestoreChatSession> {
        return try {
            val userId = getCurrentUserId() ?: return emptyList()
            sessionsCollection(userId)
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .get().await().documents.mapNotNull { it.toObject(FirestoreChatSession::class.java) }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting chat sessions", e)
            emptyList()
        }
    }

    /** Retrieve all messages for a specific chat session. */
    suspend fun getChatMessages(sessionId: String): List<FirestoreChatMessage> {
        return try {
            val userId = getCurrentUserId() ?: return emptyList()
            messagesCollection(userId, sessionId)
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .get().await().documents.mapNotNull { it.toObject(FirestoreChatMessage::class.java) }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting chat messages", e)
            emptyList()
        }
    }

    /** Real-time updates for chat sessions. */
    fun getChatSessionsFlow(): Flow<List<FirestoreChatSession>> = callbackFlow {
        val userId = getCurrentUserId()
        if (userId == null) {
            close()
            return@callbackFlow
        }
        val listener = sessionsCollection(userId)
            .orderBy("updatedAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error listening to chat sessions", error)
                    return@addSnapshotListener
                }
                val sessions = snapshot?.documents?.mapNotNull { it.toObject(FirestoreChatSession::class.java) } ?: emptyList()
                trySend(sessions)
            }
        awaitClose { listener.remove() }
    }

    /** Delete all chat history for the current user. */
    suspend fun deleteChatHistory(): Boolean {
        return try {
            val userId = getCurrentUserId() ?: return false
            val sessions = sessionsCollection(userId).get().await()
            for (session in sessions.documents) {
                val messages = session.reference.collection(COLLECTION_MESSAGES).get().await()
                for (msg in messages.documents) {
                    msg.reference.delete().await()
                }
                session.reference.delete().await()
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting chat history", e)
            false
        }
    }

    /** Aggregate simple chat statistics. */
    suspend fun getChatStatistics(): Map<String, Int> {
        return try {
            val userId = getCurrentUserId() ?: return emptyMap()
            val sessionsSnapshot = sessionsCollection(userId).get().await()
            var totalMessages = 0
            var favorites = 0
            var archived = 0
            for (doc in sessionsSnapshot.documents) {
                totalMessages += (doc.getLong("messageCount") ?: 0L).toInt()
                if (doc.getBoolean("isFavorite") == true) favorites++
                if (doc.getBoolean("isArchived") == true) archived++
            }
            mapOf(
                "totalSessions" to sessionsSnapshot.size(),
                "totalMessages" to totalMessages,
                "favoriteChats" to favorites,
                "archivedChats" to archived,
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error getting chat statistics", e)
            emptyMap()
        }
    }

    /** Bulk sync local Room history to Firestore. */
    suspend fun syncChatData(): Boolean {
        return try {
            val userId = getCurrentUserId() ?: return false
            val conversations = com.playstudio.aiteacher.history.DatabaseProvider.database
                .conversationDao().getConversations().first()
            for (conversation in conversations) {
                val messages = com.playstudio.aiteacher.history.DatabaseProvider.database
                    .messageDao().getMessages(conversation.id).first()

                val session = FirestoreChatSession(
                    sessionId = conversation.id,
                    userId = userId,
                    title = conversation.title,
                    aiModelUsed = "gpt-3.5-turbo",
                    createdAt = Date(messages.firstOrNull()?.timestamp ?: System.currentTimeMillis()),
                    updatedAt = Date(messages.lastOrNull()?.timestamp ?: System.currentTimeMillis()),
                    messageCount = messages.size,
                    lastMessagePreview = messages.lastOrNull()?.content?.take(100) ?: "",
                )
                saveChatSession(session)

                for (msg in messages) {
                    val fsMessage = FirestoreChatMessage(
                        messageId = msg.id,
                        sessionId = conversation.id,
                        userId = userId,
                        content = msg.content,
                        senderType = if (msg.isUser) "user" else "ai",
                        timestamp = Date(msg.timestamp),
                        aiModel = "gpt-3.5-turbo",
                        provider = "openai",
                    )
                    saveChatMessage(fsMessage)
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing chat data", e)
            false
        }
    }

    private fun getCurrentUserId(): String? = auth.currentUser?.uid
}

