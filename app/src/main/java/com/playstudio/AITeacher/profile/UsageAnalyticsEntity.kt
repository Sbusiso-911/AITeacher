package com.playstudio.aiteacher.profile

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import androidx.room.ColumnInfo
import androidx.room.TypeConverters
// Converters are defined at database level
import java.util.Date

@Entity(
    tableName = "usage_analytics",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["user_id"],
            childColumns = ["user_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
@TypeConverters(DatabaseConverters::class)
data class UsageAnalyticsEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "usage_id")
    val usageId: Long = 0,
    
    @ColumnInfo(name = "user_id")
    val userId: Long,
    
    @ColumnInfo(name = "date")
    val date: Date,
    
    @ColumnInfo(name = "messages_sent")
    val messagesSent: Int = 0,
    
    @ColumnInfo(name = "messages_received")
    val messagesReceived: Int = 0,
    
    @ColumnInfo(name = "models_used")
    val modelsUsed: Map<String, Int> = emptyMap(), // model_name -> count
    
    @ColumnInfo(name = "features_accessed")
    val featuresAccessed: List<String> = emptyList(),
    
    @ColumnInfo(name = "tokens_consumed")
    val tokensConsumed: Int = 0,
    
    @ColumnInfo(name = "chat_sessions_started")
    val chatSessionsStarted: Int = 0,
    
    @ColumnInfo(name = "elite_tools_used")
    val eliteToolsUsed: Map<String, Int> = emptyMap(),
    
    @ColumnInfo(name = "total_time_spent_minutes")
    val totalTimeSpentMinutes: Int = 0,
    
    @ColumnInfo(name = "voice_messages_sent")
    val voiceMessagesSent: Int = 0,
    
    @ColumnInfo(name = "images_processed")
    val imagesProcessed: Int = 0,
    
    @ColumnInfo(name = "files_uploaded")
    val filesUploaded: Int = 0,
    
    @ColumnInfo(name = "storage_used_mb")
    val storageUsedMb: Double = 0.0
)