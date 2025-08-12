package com.playstudio.aiteacher.profile

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.util.Date

@Dao
interface SubscriptionDao {
    
    @Query("SELECT * FROM subscriptions WHERE user_id = :userId ORDER BY created_at DESC")
    fun getSubscriptionsByUser(userId: String): Flow<List<SubscriptionEntity>>
    
    @Query("SELECT * FROM subscriptions WHERE user_id = :userId AND status = 'active' ORDER BY created_at DESC LIMIT 1")
    suspend fun getActiveSubscription(userId: String): SubscriptionEntity?
    
    @Query("SELECT * FROM subscriptions WHERE user_id = :userId AND status = 'active' ORDER BY created_at DESC LIMIT 1")
    fun getActiveSubscriptionFlow(userId: String): Flow<SubscriptionEntity?>
    
    @Query("SELECT * FROM subscriptions WHERE subscription_id = :subscriptionId")
    suspend fun getSubscriptionById(subscriptionId: Long): SubscriptionEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubscription(subscription: SubscriptionEntity): Long
    
    @Update
    suspend fun updateSubscription(subscription: SubscriptionEntity)
    
    @Delete
    suspend fun deleteSubscription(subscription: SubscriptionEntity)
    
    @Query("UPDATE subscriptions SET status = :status WHERE subscription_id = :subscriptionId")
    suspend fun updateSubscriptionStatus(subscriptionId: Long, status: String)
    
    @Query("UPDATE subscriptions SET auto_renew = :autoRenew WHERE subscription_id = :subscriptionId")
    suspend fun updateAutoRenew(subscriptionId: Long, autoRenew: Boolean)
    
    @Query("UPDATE subscriptions SET end_date = :endDate WHERE subscription_id = :subscriptionId")
    suspend fun updateEndDate(subscriptionId: Long, endDate: Date)
    
    @Query("SELECT COUNT(*) FROM subscriptions WHERE user_id = :userId AND status = 'active'")
    suspend fun getActiveSubscriptionCount(userId: String): Int
    
    @Query("SELECT * FROM subscriptions WHERE end_date < :currentDate AND status = 'active'")
    suspend fun getExpiredSubscriptions(currentDate: Date): List<SubscriptionEntity>
    
    @Query("SELECT * FROM subscriptions WHERE end_date BETWEEN :startDate AND :endDate AND status = 'active'")
    suspend fun getSubscriptionsExpiringBetween(startDate: Date, endDate: Date): List<SubscriptionEntity>
    
    @Query("SELECT SUM(price_paid) FROM subscriptions WHERE user_id = :userId")
    suspend fun getTotalRevenue(userId: String): Double
    
    @Query("SELECT plan_type, COUNT(*) as count FROM subscriptions WHERE user_id = :userId GROUP BY plan_type")
    suspend fun getSubscriptionHistory(userId: String): List<PlanTypeCount>
}