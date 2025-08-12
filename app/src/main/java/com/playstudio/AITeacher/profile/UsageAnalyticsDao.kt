package com.playstudio.aiteacher.profile

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.util.Date

@Dao
interface UsageAnalyticsDao {
    
    @Query("SELECT * FROM usage_analytics WHERE user_id = :userId ORDER BY date DESC")
    fun getUsageAnalyticsByUser(userId: String): Flow<List<UsageAnalyticsEntity>>
    
    @Query("SELECT * FROM usage_analytics WHERE user_id = :userId AND date = :date")
    suspend fun getUsageForDate(userId: String, date: Date): UsageAnalyticsEntity?
    
    @Query("SELECT * FROM usage_analytics WHERE user_id = :userId AND date BETWEEN :startDate AND :endDate ORDER BY date DESC")
    fun getUsageForDateRange(userId: String, startDate: Date, endDate: Date): Flow<List<UsageAnalyticsEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUsageAnalytics(usage: UsageAnalyticsEntity): Long
    
    @Update
    suspend fun updateUsageAnalytics(usage: UsageAnalyticsEntity)
    
    @Delete
    suspend fun deleteUsageAnalytics(usage: UsageAnalyticsEntity)
    
    @Query("SELECT SUM(messages_sent) FROM usage_analytics WHERE user_id = :userId")
    suspend fun getTotalMessagesSent(userId: String): Int
    
    @Query("SELECT SUM(messages_received) FROM usage_analytics WHERE user_id = :userId")
    suspend fun getTotalMessagesReceived(userId: String): Int
    
    @Query("SELECT SUM(tokens_consumed) FROM usage_analytics WHERE user_id = :userId")
    suspend fun getTotalTokensConsumed(userId: String): Int
    
    @Query("SELECT SUM(chat_sessions_started) FROM usage_analytics WHERE user_id = :userId")
    suspend fun getTotalChatSessionsStarted(userId: String): Int
    
    @Query("SELECT SUM(total_time_spent_minutes) FROM usage_analytics WHERE user_id = :userId")
    suspend fun getTotalTimeSpent(userId: String): Int
    
    @Query("SELECT SUM(storage_used_mb) FROM usage_analytics WHERE user_id = :userId")
    suspend fun getTotalStorageUsed(userId: String): Double
    
    @Query("SELECT SUM(messages_sent) FROM usage_analytics WHERE user_id = :userId AND date BETWEEN :startDate AND :endDate")
    suspend fun getMessagesSentInPeriod(userId: String, startDate: Date, endDate: Date): Int
    
    @Query("SELECT SUM(tokens_consumed) FROM usage_analytics WHERE user_id = :userId AND date BETWEEN :startDate AND :endDate")
    suspend fun getTokensConsumedInPeriod(userId: String, startDate: Date, endDate: Date): Int
    
    @Query("SELECT * FROM usage_analytics WHERE user_id = :userId ORDER BY date DESC LIMIT 30")
    fun getLastThirtyDaysUsage(userId: String): Flow<List<UsageAnalyticsEntity>>
    
    @Query("SELECT * FROM usage_analytics WHERE user_id = :userId ORDER BY date DESC LIMIT 7")
    fun getLastSevenDaysUsage(userId: String): Flow<List<UsageAnalyticsEntity>>
    
    @Query("DELETE FROM usage_analytics WHERE user_id = :userId AND date < :cutoffDate")
    suspend fun deleteOldUsageData(userId: String, cutoffDate: Date)
}