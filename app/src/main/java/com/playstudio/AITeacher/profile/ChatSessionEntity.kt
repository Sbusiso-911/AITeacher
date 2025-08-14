package com.playstudio.aiteacher.profile

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import androidx.room.ColumnInfo
import androidx.room.TypeConverters
// Converters are defined at database level
import java.util.Date

@Entity(
    tableName = "chat_sessions"
    // Removed foreign key constraint since we're using Firebase UID strings
)
@TypeConverters(DatabaseConverters::class)
data class ChatSessionEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "session_id")
    val sessionId: Long = 0,

    // Firestore document ID for cross-device retrieval
    @ColumnInfo(name = "firestore_id")
    val firestoreId: String? = null,

    @ColumnInfo(name = "user_id")
    val userId: String, // Changed to String to match Firebase UID
    
    @ColumnInfo(name = "title")
    val title: String,
    
    @ColumnInfo(name = "ai_model_used")
    val aiModelUsed: String,
    
    @ColumnInfo(name = "category")
    val category: String = "general", // general, work, personal, creative, education
    
    @ColumnInfo(name = "tags")
    val tags: List<String> = emptyList(),
    
    @ColumnInfo(name = "is_favorite")
    val isFavorite: Boolean = false,
    
    @ColumnInfo(name = "is_archived")
    val isArchived: Boolean = false,
    
    @ColumnInfo(name = "message_count")
    val messageCount: Int = 0,
    
    @ColumnInfo(name = "created_at")
    val createdAt: Date = Date(),
    
    @ColumnInfo(name = "updated_at")
    val updatedAt: Date = Date(),
    
    @ColumnInfo(name = "last_message_preview")
    val lastMessagePreview: String? = null,
    
    @ColumnInfo(name = "conversation_summary")
    val conversationSummary: String? = null
)