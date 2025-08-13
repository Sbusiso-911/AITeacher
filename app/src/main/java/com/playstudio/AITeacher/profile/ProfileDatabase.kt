package com.playstudio.aiteacher.profile

import android.content.Context
import androidx.room.*
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        UserEntity::class,
        ChatSessionEntity::class,
        ChatMessageEntity::class,
        SubscriptionEntity::class,
        UsageAnalyticsEntity::class
    ],
    version = 2, // Incremented due to changing userId from Long to String
    exportSchema = false
)
@TypeConverters(DatabaseConverters::class)
abstract class ProfileDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun chatSessionDao(): ChatSessionDao
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun subscriptionDao(): SubscriptionDao
    abstract fun usageAnalyticsDao(): UsageAnalyticsDao

    companion object {
        @Volatile
        private var INSTANCE: ProfileDatabase? = null

        fun getDatabase(context: Context): ProfileDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ProfileDatabase::class.java,
                    "profile_database"
                )
                .fallbackToDestructiveMigration() // Safe since we're migrating to Firestore-only architecture
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}