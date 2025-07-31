package com.playstudio.aiteacher.profile

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.util.Date

@Dao
interface UsageAnalyticsDao {
    
    @Query("SELECT * FROM usage_analytics WHERE user_id = :userId ORDER BY date DESC")
    fun getUsageAnalyticsByUser(userId: Long): Flow<List<UsageAnalyticsEntity>>
    
    @Query("SELECT * FROM usage_analytics WHERE user_id = :userId AND date = :date")
    suspend fun getUsageForDate(userId: Long, date: Date): UsageAnalyticsEntity?
    
    @Query("SELECT * FROM usage_analytics WHERE user_id = :userId AND date BETWEEN :startDate AND :endDate ORDER BY date DESC")
    fun getUsageForDateRange(userId: Long, startDate: Date, endDate: Date): Flow<List<UsageAnalyticsEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUsageAnalytics(usage: UsageAnalyticsEntity): Long
    
    @Update
    suspend fun updateUsageAnalytics(usage: UsageAnalyticsEntity)
    
    @Delete
    suspend fun deleteUsageAnalytics(usage: UsageAnalyticsEntity)
    
    @Query("SELECT SUM(messages_sent) FROM usage_analytics WHERE user_id = :userId")
    suspend fun getTotalMessagesSent(userId: Long): Int
    
    @Query("SELECT SUM(messages_received) FROM usage_analytics WHERE user_id = :userId")
    suspend fun getTotalMessagesReceived(userId: Long): Int
    
    @Query("SELECT SUM(tokens_consumed) FROM usage_analytics WHERE user_id = :userId")
    suspend fun getTotalTokensConsumed(userId: Long): Int
    
    @Query("SELECT SUM(chat_sessions_started) FROM usage_analytics WHERE user_id = :userId")
    suspend fun getTotalChatSessionsStarted(userId: Long): Int
    
    @Query("SELECT SUM(total_time_spent_minutes) FROM usage_analytics WHERE user_id = :userId")
    suspend fun getTotalTimeSpent(userId: Long): Int
    
    @Query("SELECT SUM(storage_used_mb) FROM usage_analytics WHERE user_id = :userId")
    suspend fun getTotalStorageUsed(userId: Long): Double
    
    @Query("SELECT SUM(messages_sent) FROM usage_analytics WHERE user_id = :userId AND date BETWEEN :startDate AND :endDate")
    suspend fun getMessagesSentInPeriod(userId: Long, startDate: Date, endDate: Date): Int
    
    @Query("SELECT SUM(tokens_consumed) FROM usage_analytics WHERE user_id = :userId AND date BETWEEN :startDate AND :endDate")
    suspend fun getTokensConsumedInPeriod(userId: Long, startDate: Date, endDate: Date): Int
    
    @Query("SELECT * FROM usage_analytics WHERE user_id = :userId ORDER BY date DESC LIMIT 30")
    fun getLastThirtyDaysUsage(userId: Long): Flow<List<UsageAnalyticsEntity>>
    
    @Query("SELECT * FROM usage_analytics WHERE user_id = :userId ORDER BY date DESC LIMIT 7")
    fun getLastSevenDaysUsage(userId: Long): Flow<List<UsageAnalyticsEntity>>
    
    @Query("DELETE FROM usage_analytics WHERE user_id = :userId AND date < :cutoffDate")
    suspend fun deleteOldUsageData(userId: Long, cutoffDate: Date)
}