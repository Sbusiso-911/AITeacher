package com.playstudio.aiteacher.profile

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.util.Date

@Dao
interface ChatMessageDao {
    
    @Query("SELECT * FROM chat_messages WHERE session_id = :sessionId ORDER BY timestamp ASC")
    fun getMessagesBySession(sessionId: Long): Flow<List<ChatMessageEntity>>
    
    @Query("SELECT * FROM chat_messages WHERE message_id = :messageId")
    suspend fun getMessageById(messageId: Long): ChatMessageEntity?
    
    @Query("SELECT * FROM chat_messages WHERE session_id = :sessionId AND content LIKE '%' || :query || '%' ORDER BY timestamp ASC")
    fun searchMessagesInSession(sessionId: Long, query: String): Flow<List<ChatMessageEntity>>
    
    @Query("SELECT * FROM chat_messages WHERE session_id IN (SELECT session_id FROM chat_sessions WHERE user_id = :userId) AND content LIKE '%' || :query || '%' ORDER BY timestamp DESC")
    fun searchAllUserMessages(userId: Long, query: String): Flow<List<ChatMessageEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<ChatMessageEntity>)
    
    @Update
    suspend fun updateMessage(message: ChatMessageEntity)
    
    @Delete
    suspend fun deleteMessage(message: ChatMessageEntity)
    
    @Query("DELETE FROM chat_messages WHERE session_id = :sessionId")
    suspend fun deleteMessagesInSession(sessionId: Long)
    
    @Query("DELETE FROM chat_messages WHERE session_id IN (:sessionIds)")
    suspend fun deleteMessagesInSessions(sessionIds: List<Long>)
    
    @Query("SELECT COUNT(*) FROM chat_messages WHERE session_id = :sessionId")
    suspend fun getMessageCountInSession(sessionId: Long): Int
    
    @Query("SELECT COUNT(*) FROM chat_messages WHERE session_id IN (SELECT session_id FROM chat_sessions WHERE user_id = :userId) AND sender_type = 'user'")
    suspend fun getTotalUserMessages(userId: Long): Int
    
    @Query("SELECT COUNT(*) FROM chat_messages WHERE session_id IN (SELECT session_id FROM chat_sessions WHERE user_id = :userId) AND sender_type = 'ai'")
    suspend fun getTotalAiMessages(userId: Long): Int
    
    @Query("SELECT SUM(token_count) FROM chat_messages WHERE session_id IN (SELECT session_id FROM chat_sessions WHERE user_id = :userId)")
    suspend fun getTotalTokensUsed(userId: Long): Int
    
    @Query("SELECT * FROM chat_messages WHERE session_id = :sessionId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastMessageInSession(sessionId: Long): ChatMessageEntity?
    
    @Query("SELECT AVG(processing_time_ms) FROM chat_messages WHERE session_id IN (SELECT session_id FROM chat_sessions WHERE user_id = :userId) AND sender_type = 'ai'")
    suspend fun getAverageProcessingTime(userId: Long): Double
    
    // Webapp integration methods
    @Query("SELECT * FROM chat_messages WHERE session_id = :sessionId ORDER BY timestamp ASC")
    suspend fun getMessagesForSession(sessionId: Long): List<ChatMessageEntity>
}