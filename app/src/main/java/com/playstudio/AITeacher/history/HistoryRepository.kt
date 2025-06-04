package com.playstudio.aiteacher.history

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class HistoryRepository(private val db: AppDatabase) {

    fun getConversations(): Flow<List<ConversationEntity>> =
        db.conversationDao().getConversations()

    fun getMessages(conversationId: String): Flow<List<MessageEntity>> =
        db.messageDao().getMessages(conversationId)

    suspend fun addMessage(conversationId: String, isUser: Boolean, content: String) {
        val timestamp = System.currentTimeMillis()
        val message = MessageEntity(
            id = UUID.randomUUID().toString(),
            conversationId = conversationId,
            isUser = isUser,
            content = content,
            timestamp = timestamp
        )

        db.withTransaction {
            val existing = db.conversationDao().getConversation(conversationId)
            val title = existing?.title?.takeIf { it.isNotEmpty() } ?: content.take(50)
            val conversation = ConversationEntity(
                id = conversationId,
                title = title,
                lastUpdated = timestamp
            )
            db.conversationDao().insertConversation(conversation)
            db.messageDao().insertMessage(message)
        }
    }
}
