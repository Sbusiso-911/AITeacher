package com.playstudio.aiteacher.profile

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.util.Date

@Dao
interface UserDao {
    
    @Query("SELECT * FROM users WHERE user_id = :userId")
    suspend fun getUserById(userId: String): UserEntity?
    
    @Query("SELECT * FROM users WHERE email = :email")
    suspend fun getUserByEmail(email: String): UserEntity?
    
    @Query("SELECT * FROM users WHERE email = :email AND password_hash = :passwordHash")
    suspend fun authenticateUser(email: String, passwordHash: String): UserEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)
    
    @Update
    suspend fun updateUser(user: UserEntity)
    
    @Delete
    suspend fun deleteUser(user: UserEntity)
    
    @Query("UPDATE users SET last_login = :loginTime WHERE user_id = :userId")
    suspend fun updateLastLogin(userId: String, loginTime: Date)
    
    @Query("UPDATE users SET profile_picture_url = :url WHERE user_id = :userId")
    suspend fun updateProfilePicture(userId: String, url: String)
    
    @Query("UPDATE users SET theme_preference = :theme WHERE user_id = :userId")
    suspend fun updateThemePreference(userId: String, theme: String)
    
    @Query("UPDATE users SET language_setting = :language WHERE user_id = :userId")
    suspend fun updateLanguageSetting(userId: String, language: String)
    
    @Query("UPDATE users SET notification_enabled = :enabled WHERE user_id = :userId")
    suspend fun updateNotificationSettings(userId: String, enabled: Boolean)
    
    @Query("UPDATE users SET google_id = :googleId WHERE user_id = :userId")
    suspend fun updateGoogleId(userId: String, googleId: String)
    
    @Query("UPDATE users SET newsletter_subscribed = :subscribed WHERE user_id = :userId")
    suspend fun updateNewsletterSubscription(userId: String, subscribed: Boolean)
    
    @Query("UPDATE users SET product_updates_subscribed = :subscribed WHERE user_id = :userId")
    suspend fun updateProductUpdatesSubscription(userId: String, subscribed: Boolean)
    
    @Query("UPDATE users SET promotional_emails_subscribed = :subscribed WHERE user_id = :userId")
    suspend fun updatePromotionalEmailsSubscription(userId: String, subscribed: Boolean)
    
    @Query("SELECT COUNT(*) FROM users WHERE is_active = 1")
    suspend fun getActiveUserCount(): Int
    
    @Query("SELECT * FROM users WHERE is_active = 1 ORDER BY created_at DESC")
    fun getAllActiveUsers(): Flow<List<UserEntity>>
}