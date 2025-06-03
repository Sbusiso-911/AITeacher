package com.playstudio.aiteacher.history

import kotlinx.coroutines.flow.Flow
import java.util.UUID

class HistoryRepository(private val db: AppDatabase) {

    fun getConversations(): Flow<List<ConversationEntity>> =
        db.conversationDao().getConversations()

    fun getMessages(conversationId: String): Flow<List<MessageEntity>> =
        db.messageDao().getMessages(conversationId)

    suspend fun addMessage(conversationId: String, isUser: Boolean, content: String) {
        val message = MessageEntity(
            id = UUID.randomUUID().toString(),
            conversationId = conversationId,
            isUser = isUser,
            content = content,
            timestamp = System.currentTimeMillis()
        )
        db.messageDao().insertMessage(message)

        val existing = db.conversationDao().getConversation(conversationId)
        val title = existing?.title?.takeIf { it.isNotEmpty() }
            ?: content.take(50)
        val conversation = ConversationEntity(
            id = conversationId,
            title = title ?: content.take(50),
            lastUpdated = System.currentTimeMillis()
        )
        db.conversationDao().insertConversation(conversation)
    }
}
