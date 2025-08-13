package com.playstudio.aiteacher.profile

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.util.Date

@Dao
interface ChatSessionDao {
    
    @Query("SELECT * FROM chat_sessions WHERE user_id = :userId ORDER BY updated_at DESC")
    fun getChatSessionsByUser(userId: String): Flow<List<ChatSessionEntity>>
    
    @Query("SELECT * FROM chat_sessions WHERE session_id = :sessionId")
    suspend fun getChatSessionById(sessionId: Long): ChatSessionEntity?
    
    @Query("SELECT * FROM chat_sessions WHERE user_id = :userId AND is_favorite = 1 ORDER BY updated_at DESC")
    fun getFavoriteChatSessions(userId: String): Flow<List<ChatSessionEntity>>
    
    @Query("SELECT * FROM chat_sessions WHERE user_id = :userId AND is_archived = 0 ORDER BY updated_at DESC")
    fun getActiveChatSessions(userId: String): Flow<List<ChatSessionEntity>>
    
    @Query("SELECT * FROM chat_sessions WHERE user_id = :userId AND category = :category ORDER BY updated_at DESC")
    fun getChatSessionsByCategory(userId: String, category: String): Flow<List<ChatSessionEntity>>
    
    @Query("SELECT * FROM chat_sessions WHERE user_id = :userId AND ai_model_used = :model ORDER BY updated_at DESC")
    fun getChatSessionsByModel(userId: String, model: String): Flow<List<ChatSessionEntity>>
    
    @Query("SELECT * FROM chat_sessions WHERE user_id = :userId AND (title LIKE '%' || :query || '%' OR conversation_summary LIKE '%' || :query || '%') ORDER BY updated_at DESC")
    fun searchChatSessions(userId: String, query: String): Flow<List<ChatSessionEntity>>
    
    @Query("SELECT * FROM chat_sessions WHERE user_id = :userId AND created_at BETWEEN :startDate AND :endDate ORDER BY updated_at DESC")
    fun getChatSessionsByDateRange(userId: String, startDate: Date, endDate: Date): Flow<List<ChatSessionEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChatSession(session: ChatSessionEntity): Long
    
    @Update
    suspend fun updateChatSession(session: ChatSessionEntity)
    
    @Delete
    suspend fun deleteChatSession(session: ChatSessionEntity)
    
    @Query("DELETE FROM chat_sessions WHERE user_id = :userId AND session_id IN (:sessionIds)")
    suspend fun deleteChatSessions(userId: String, sessionIds: List<Long>)
    
    @Query("DELETE FROM chat_sessions WHERE user_id = :userId")
    suspend fun deleteAllForUser(userId: String)
    
    @Query("UPDATE chat_sessions SET is_favorite = :isFavorite WHERE session_id = :sessionId")
    suspend fun updateFavoriteStatus(sessionId: Long, isFavorite: Boolean)
    
    @Query("UPDATE chat_sessions SET is_archived = :isArchived WHERE session_id = :sessionId")
    suspend fun updateArchiveStatus(sessionId: Long, isArchived: Boolean)
    
    @Query("UPDATE chat_sessions SET message_count = :count, updated_at = :updatedAt WHERE session_id = :sessionId")
    suspend fun updateMessageCount(sessionId: Long, count: Int, updatedAt: Date)
    
    @Query("SELECT COUNT(*) FROM chat_sessions WHERE user_id = :userId AND is_archived = 0")
    suspend fun getActiveChatSessionCount(userId: String): Int
    
    @Query("SELECT DISTINCT category FROM chat_sessions WHERE user_id = :userId")
    fun getCategoriesForUser(userId: String): Flow<List<String>>
    
    @Query("SELECT DISTINCT ai_model_used FROM chat_sessions WHERE user_id = :userId")
    fun getModelsUsedByUser(userId: String): Flow<List<String>>
    
    // Webapp integration methods
    @Query("SELECT * FROM chat_sessions WHERE user_id = :userId ORDER BY updated_at DESC LIMIT :limit")
    suspend fun getRecentChatSessions(userId: String, limit: Int): List<ChatSessionEntity>
}