package com.playstudio.aiteacher.history

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

class InMemoryConversationDao : ConversationDao {
    private val conversations = mutableListOf<ConversationEntity>()
    private val conversationsFlow = MutableStateFlow<List<ConversationEntity>>(emptyList())

    override fun getConversations(): Flow<List<ConversationEntity>> {
        return conversationsFlow.asStateFlow()
    }

    override suspend fun getConversation(id: String): ConversationEntity? {
        return conversations.find { it.id == id }
    }

    override suspend fun insertConversation(conversation: ConversationEntity) {
        conversations.removeAll { it.id == conversation.id }
        conversations.add(conversation)
        conversations.sortByDescending { it.lastUpdated }
        conversationsFlow.value = conversations.toList()
    }

    override suspend fun deleteConversation(conversation: ConversationEntity) {
        conversations.removeAll { it.id == conversation.id }
        conversationsFlow.value = conversations.toList()
    }

    override suspend fun deleteAllConversations() {
        conversations.clear()
        conversationsFlow.value = emptyList()
    }
}

class InMemoryMessageDao : MessageDao {
    private val messages = mutableListOf<MessageEntity>()
    private val messagesFlow = MutableStateFlow<List<MessageEntity>>(emptyList())

    override fun getMessages(conversationId: String): Flow<List<MessageEntity>> {
        return messagesFlow.map { messageList ->
            messageList.filter { it.conversationId == conversationId }
                .sortedBy { it.timestamp }
        }
    }

    override suspend fun insertMessage(message: MessageEntity) {
        messages.removeAll { it.id == message.id }
        messages.add(message)
        messagesFlow.value = messages.toList()
    }
}

class InMemoryDatabase {
    private val conversationDao = InMemoryConversationDao()
    private val messageDao = InMemoryMessageDao()

    fun conversationDao(): ConversationDao = conversationDao
    fun messageDao(): MessageDao = messageDao

    companion object {
        @Volatile
        private var INSTANCE: InMemoryDatabase? = null

        fun getDatabase(context: Context): InMemoryDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = InMemoryDatabase()
                INSTANCE = instance
                instance
            }
        }
    }
}