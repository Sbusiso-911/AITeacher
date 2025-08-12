package com.playstudio.aiteacher.profile

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import androidx.room.TypeConverters
// Converters are defined at database level
import java.util.Date

@Entity(tableName = "users")
@TypeConverters(DatabaseConverters::class)
data class UserEntity(
    @PrimaryKey
    @ColumnInfo(name = "user_id")
    val userId: String, // Changed to String to use Firebase UID directly
    
    @ColumnInfo(name = "email")
    val email: String,
    
    @ColumnInfo(name = "password_hash")
    val passwordHash: String? = null, // Nullable for Google Sign-In users
    
    @ColumnInfo(name = "full_name")
    val fullName: String,
    
    @ColumnInfo(name = "profile_picture_url")
    val profilePictureUrl: String? = null,
    
    @ColumnInfo(name = "preferred_ai_models")
    val preferredAiModels: List<String> = emptyList(),
    
    @ColumnInfo(name = "theme_preference")
    val themePreference: String = "system", // system, light, dark
    
    @ColumnInfo(name = "language_setting")
    val languageSetting: String = "en",
    
    @ColumnInfo(name = "notification_enabled")
    val notificationEnabled: Boolean = true,
    
    @ColumnInfo(name = "auto_backup_enabled")
    val autoBackupEnabled: Boolean = true,
    
    @ColumnInfo(name = "created_at")
    val createdAt: Date = Date(),
    
    @ColumnInfo(name = "updated_at")
    val updatedAt: Date = Date(),
    
    @ColumnInfo(name = "last_login")
    val lastLogin: Date? = null,
    
    @ColumnInfo(name = "is_active")
    val isActive: Boolean = true,
    
    // Google Sign-In fields
    @ColumnInfo(name = "google_id")
    val googleId: String? = null,
    
    @ColumnInfo(name = "auth_provider")
    val authProvider: String = "email", // email, google
    
    // Email subscription preferences
    @ColumnInfo(name = "newsletter_subscribed")
    val newsletterSubscribed: Boolean = false,
    
    @ColumnInfo(name = "product_updates_subscribed")
    val productUpdatesSubscribed: Boolean = false,
    
    @ColumnInfo(name = "promotional_emails_subscribed")
    val promotionalEmailsSubscribed: Boolean = false
)