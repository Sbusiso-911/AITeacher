package com.playstudio.aiteacher.credits

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.util.*

/**
 * Credit tracking entities for the unified token credit system
 */

@Entity(tableName = "user_credits")
data class UserCreditEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val userId: String,
    val date: String, // yyyy-MM-dd format
    val subscriptionTier: String,
    val dailyAllowance: Double,
    val creditsUsed: Double = 0.0,
    val rolloverCredits: Double = 0.0,
    val emergencyCreditsUsed: Double = 0.0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "credit_usage_logs",
    indices = [Index(value = ["userId", "timestamp"]), Index(value = ["modelName"])]
)
data class CreditUsageLogEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val userId: String,
    val modelName: String,
    val inputTokens: Int,
    val outputTokens: Int,
    val creditCost: Double,
    val subscriptionTier: String,
    val timestamp: Long = System.currentTimeMillis(),
    val conversationId: String? = null,
    val messageId: String? = null
)

@Entity(
    tableName = "credit_transactions",
    indices = [Index(value = ["userId", "timestamp"])]
)
data class CreditTransactionEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val userId: String,
    val transactionType: String, // DAILY_RESET, MESSAGE_COST, ROLLOVER, EMERGENCY, PURCHASE
    val amount: Double, // Positive for credits added, negative for credits used
    val balanceBefore: Double,
    val balanceAfter: Double,
    val description: String,
    val metadata: String? = null, // JSON for additional data
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * DAOs for credit system
 */
@Dao
interface UserCreditDao {
    @Query("SELECT * FROM user_credits WHERE userId = :userId AND date = :date")
    suspend fun getCreditsByUserAndDate(userId: String, date: String): UserCreditEntity?
    
    @Query("SELECT * FROM user_credits WHERE userId = :userId ORDER BY date DESC LIMIT 7")
    suspend fun getRecentCredits(userId: String): List<UserCreditEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(credit: UserCreditEntity)
    
    @Query("DELETE FROM user_credits WHERE userId = :userId AND date < :cutoffDate")
    suspend fun cleanupOldRecords(userId: String, cutoffDate: String)
}

@Dao
interface CreditUsageLogDao {
    @Query("""
        SELECT * FROM credit_usage_logs 
        WHERE userId = :userId AND timestamp >= :startTime AND timestamp <= :endTime
        ORDER BY timestamp DESC
    """)
    suspend fun getUsageInRange(userId: String, startTime: Long, endTime: Long): List<CreditUsageLogEntity>
    
    @Query("""
        SELECT modelName, SUM(creditCost) as totalCost, COUNT(*) as messageCount
        FROM credit_usage_logs 
        WHERE userId = :userId AND timestamp >= :startTime 
        GROUP BY modelName 
        ORDER BY totalCost DESC
    """)
    suspend fun getDailyUsageByModel(userId: String, startTime: Long): List<ModelUsageSummary>
    
    @Query("""
        SELECT AVG(creditCost) FROM credit_usage_logs 
        WHERE userId = :userId AND modelName = :modelName AND timestamp >= :startTime
    """)
    suspend fun getAverageCostForModel(userId: String, modelName: String, startTime: Long): Double?
    
    @Insert
    suspend fun insertUsageLog(log: CreditUsageLogEntity)
    
    @Query("DELETE FROM credit_usage_logs WHERE timestamp < :cutoffTime")
    suspend fun cleanupOldLogs(cutoffTime: Long)
}

@Dao
interface CreditTransactionDao {
    @Query("""
        SELECT * FROM credit_transactions 
        WHERE userId = :userId AND timestamp >= :startTime 
        ORDER BY timestamp DESC
    """)
    suspend fun getTransactionHistory(userId: String, startTime: Long): List<CreditTransactionEntity>
    
    @Query("""
        SELECT SUM(amount) FROM credit_transactions 
        WHERE userId = :userId AND transactionType = :type AND timestamp >= :startTime
    """)
    suspend fun getTotalByType(userId: String, type: String, startTime: Long): Double?
    
    @Insert
    suspend fun insertTransaction(transaction: CreditTransactionEntity)
    
    @Query("DELETE FROM credit_transactions WHERE timestamp < :cutoffTime")
    suspend fun cleanupOldTransactions(cutoffTime: Long)
}

/**
 * Data classes for query results
 */
data class ModelUsageSummary(
    val modelName: String,
    val totalCost: Double,
    val messageCount: Int
)

/**
 * Room Database
 */
@Database(
    entities = [
        UserCreditEntity::class,
        CreditUsageLogEntity::class,
        CreditTransactionEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(CreditConverters::class)
abstract class CreditDatabase : RoomDatabase() {
    abstract fun userCreditDao(): UserCreditDao
    abstract fun usageLogDao(): CreditUsageLogDao
    abstract fun transactionDao(): CreditTransactionDao
}

/**
 * Type converters for Room
 */
class CreditConverters {
    @TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }

    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }
}

/**
 * Migration from existing usage tracking system
 */
val MIGRATION_EXISTING_TO_CREDITS = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Create new credit tracking tables
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS user_credits (
                id TEXT PRIMARY KEY NOT NULL,
                userId TEXT NOT NULL,
                date TEXT NOT NULL,
                subscriptionTier TEXT NOT NULL,
                dailyAllowance REAL NOT NULL,
                creditsUsed REAL NOT NULL DEFAULT 0.0,
                rolloverCredits REAL NOT NULL DEFAULT 0.0,
                emergencyCreditsUsed REAL NOT NULL DEFAULT 0.0,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL
            )
        """)
        
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS credit_usage_logs (
                id TEXT PRIMARY KEY NOT NULL,
                userId TEXT NOT NULL,
                modelName TEXT NOT NULL,
                inputTokens INTEGER NOT NULL,
                outputTokens INTEGER NOT NULL,
                creditCost REAL NOT NULL,
                subscriptionTier TEXT NOT NULL,
                timestamp INTEGER NOT NULL,
                conversationId TEXT,
                messageId TEXT
            )
        """)
        
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS credit_transactions (
                id TEXT PRIMARY KEY NOT NULL,
                userId TEXT NOT NULL,
                transactionType TEXT NOT NULL,
                amount REAL NOT NULL,
                balanceBefore REAL NOT NULL,
                balanceAfter REAL NOT NULL,
                description TEXT NOT NULL,
                metadata TEXT,
                timestamp INTEGER NOT NULL
            )
        """)
        
        // Create indexes
        database.execSQL("CREATE INDEX IF NOT EXISTS index_credit_usage_logs_userId_timestamp ON credit_usage_logs(userId, timestamp)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_credit_usage_logs_modelName ON credit_usage_logs(modelName)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_credit_transactions_userId_timestamp ON credit_transactions(userId, timestamp)")
    }
}