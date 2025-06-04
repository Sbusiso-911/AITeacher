package com.playstudio.aiteacher.history

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class HistoryRepository(private val db: AppDatabase) {
    private val mutex = Mutex()

    fun getConversations(): Flow<List<ConversationEntity>> =
        db.conversationDao().getConversations()

    fun getMessages(conversationId: String): Flow<List<MessageEntity>> =
        db.messageDao().getMessages(conversationId)

    suspend fun addMessage(conversationId: String, isUser: Boolean, content: String) {
        mutex.withLock {
                val timestamp = System.currentTimeMillis()
                val message = MessageEntity(
                    id = java.util.UUID.randomUUID().toString(),
                    conversationId = conversationId,
                    isUser = isUser,
                    content = content,
                    timestamp = timestamp
                )

                db.withTransaction {
                    val existing = db.conversationDao().getConversation(conversationId)
                    val title = existing?.title?.takeIf { it.isNotEmpty() } ?: if (isUser) content.take(50) else (existing?.title ?: "")
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
}
