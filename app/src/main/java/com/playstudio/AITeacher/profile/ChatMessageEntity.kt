package com.playstudio.aiteacher.profile

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import androidx.room.ColumnInfo
import androidx.room.TypeConverters
// Converters are defined at database level
import java.util.Date

@Entity(
    tableName = "chat_messages",
    foreignKeys = [
        ForeignKey(
            entity = ChatSessionEntity::class,
            parentColumns = ["session_id"],
            childColumns = ["session_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
@TypeConverters(DatabaseConverters::class)
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "message_id")
    val messageId: Long = 0,
    
    @ColumnInfo(name = "session_id")
    val sessionId: Long,
    
    @ColumnInfo(name = "sender_type")
    val senderType: String, // user, ai
    
    @ColumnInfo(name = "content")
    val content: String,
    
    @ColumnInfo(name = "timestamp")
    val timestamp: Date = Date(),
    
    @ColumnInfo(name = "model_response_data")
    val modelResponseData: Map<String, Any>? = null,
    
    @ColumnInfo(name = "message_type")
    val messageType: String = "text", // text, image, audio, file
    
    @ColumnInfo(name = "attachments")
    val attachments: List<String> = emptyList(),
    
    @ColumnInfo(name = "is_edited")
    val isEdited: Boolean = false,
    
    @ColumnInfo(name = "edited_at")
    val editedAt: Date? = null,
    
    @ColumnInfo(name = "token_count")
    val tokenCount: Int = 0,
    
    @ColumnInfo(name = "processing_time_ms")
    val processingTimeMs: Long = 0
)