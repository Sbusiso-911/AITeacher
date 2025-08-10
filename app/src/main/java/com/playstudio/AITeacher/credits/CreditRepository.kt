package com.playstudio.aiteacher.credits

import android.content.Context
import androidx.room.Room
import com.playstudio.aiteacher.pricing.SubscriptionTier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Repository that combines CreditManager with database persistence for analytics and history
 */
class CreditRepository private constructor(
    private val context: Context,
    private val creditManager: CreditManager,
    internal val database: CreditDatabase
) {
    
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    
    companion object {
        @Volatile
        private var INSTANCE: CreditRepository? = null
        
        fun getInstance(context: Context): CreditRepository {
            return INSTANCE ?: synchronized(this) {
                val creditManager = CreditManager.getInstance(context)
                val database = Room.databaseBuilder(
                    context.applicationContext,
                    CreditDatabase::class.java,
                    "credit_database"
                ).build()
                
                INSTANCE ?: CreditRepository(context, creditManager, database).also { INSTANCE = it }
            }
        }
    }
    
    /**
     * Process a message and deduct credits with full logging
     */
    suspend fun processMessage(
        userId: String,
        modelName: String,
        inputTokens: Int,
        outputTokens: Int,
        tier: SubscriptionTier,
        conversationId: String? = null,
        messageId: String? = null
    ): MessageProcessingResult = withContext(Dispatchers.IO) {
        
        // Calculate actual cost
        val creditCost = creditManager.calculateMessageCost(inputTokens, outputTokens, modelName, tier)
        val balanceBefore = creditManager.getRemainingCredits(userId, tier)
        
        // Attempt to deduct credits
        val success = creditManager.updateUserCredits(userId, tier, creditCost)
        val balanceAfter = creditManager.getRemainingCredits(userId, tier)
        
        if (success) {
            // Log the usage
            database.usageLogDao().insertUsageLog(
                CreditUsageLogEntity(
                    userId = userId,
                    modelName = modelName,
                    inputTokens = inputTokens,
                    outputTokens = outputTokens,
                    creditCost = creditCost,
                    subscriptionTier = tier.name,
                    conversationId = conversationId,
                    messageId = messageId
                )
            )
            
            // Log the transaction
            database.transactionDao().insertTransaction(
                CreditTransactionEntity(
                    userId = userId,
                    transactionType = "MESSAGE_COST",
                    amount = -creditCost,
                    balanceBefore = balanceBefore,
                    balanceAfter = balanceAfter,
                    description = "Message sent using $modelName"
                )
            )
            
            // Update daily record
            updateDailyRecord(userId, tier, creditCost)
        }
        
        MessageProcessingResult(
            success = success,
            creditCost = creditCost,
            balanceBefore = balanceBefore,
            balanceAfter = balanceAfter,
            reason = if (success) "Success" else "Insufficient credits"
        )
    }
    
    /**
     * Get comprehensive usage analytics for a user
     */
    suspend fun getUsageAnalytics(
        userId: String,
        tier: SubscriptionTier,
        days: Int = 7
    ): UsageAnalytics = withContext(Dispatchers.IO) {
        
        val endTime = System.currentTimeMillis()
        val startTime = endTime - TimeUnit.DAYS.toMillis(days.toLong())
        
        val usageLogs = database.usageLogDao().getUsageInRange(userId, startTime, endTime)
        val modelUsage = database.usageLogDao().getDailyUsageByModel(userId, startTime)
        val transactions = database.transactionDao().getTransactionHistory(userId, startTime)
        
        val totalSpent = usageLogs.sumOf { it.creditCost }
        val totalMessages = usageLogs.size
        val averageCostPerMessage = if (totalMessages > 0) totalSpent / totalMessages else 0.0
        
        val config = SubscriptionTiers.getConfig(tier)
        val currentBalance = creditManager.getRemainingCredits(userId, tier)
        val usagePercentage = creditManager.getCreditUsagePercentage(userId, tier)
        
        UsageAnalytics(
            totalCreditsSpent = totalSpent,
            totalMessages = totalMessages,
            averageCostPerMessage = averageCostPerMessage,
            modelUsageBreakdown = modelUsage,
            currentBalance = currentBalance,
            dailyAllowance = config.dailyCredits,
            usagePercentage = usagePercentage,
            daysAnalyzed = days,
            recommendations = generateRecommendations(userId, tier, modelUsage, usagePercentage)
        )
    }
    
    /**
     * Get real-time cost preview for model selection
     */
    suspend fun getCostPreview(
        userId: String,
        tier: SubscriptionTier
    ): CostPreview = withContext(Dispatchers.IO) {
        
        val remainingCredits = creditManager.getRemainingCredits(userId, tier)
        val smartRecommendation = SmartModelRecommendation(creditManager)
        val recommendations = smartRecommendation.getRecommendations(userId, tier)
        
        CostPreview(
            remainingCredits = remainingCredits,
            modelCosts = recommendations.associate { it.model.modelId to it.estimatedCost },
            messagesRemaining = recommendations.associate { it.model.modelId to it.messagesRemaining },
            recommendations = recommendations.take(5), // Top 5 recommendations
            lowCreditWarning = smartRecommendation.shouldShowLowCreditWarning(userId, tier)
        )
    }
    
    /**
     * Handle daily credit reset with rollover logic
     */
    suspend fun processDailyReset(userId: String, tier: SubscriptionTier) = withContext(Dispatchers.IO) {
        val balanceBefore = creditManager.getRemainingCredits(userId, tier)
        
        // Process rollover (this is handled internally by CreditManager)
        creditManager.processRollover(userId, tier)
        
        val balanceAfter = creditManager.getRemainingCredits(userId, tier)
        val config = SubscriptionTiers.getConfig(tier)
        
        // Log the daily reset transaction
        database.transactionDao().insertTransaction(
            CreditTransactionEntity(
                userId = userId,
                transactionType = "DAILY_RESET",
                amount = balanceAfter - balanceBefore,
                balanceBefore = balanceBefore,
                balanceAfter = balanceAfter,
                description = "Daily credit reset with rollover"
            )
        )
        
        // Create new daily record
        database.userCreditDao().insertOrUpdate(
            UserCreditEntity(
                userId = userId,
                date = dateFormat.format(Date()),
                subscriptionTier = tier.name,
                dailyAllowance = config.dailyCredits,
                rolloverCredits = balanceAfter - config.dailyCredits
            )
        )
    }
    
    /**
     * Emergency credit system - allow small overdraft for important messages
     */
    suspend fun processEmergencyMessage(
        userId: String,
        modelName: String,
        inputTokens: Int,
        outputTokens: Int,
        tier: SubscriptionTier,
        maxEmergencyCredits: Double = 0.50 // $0.50 overdraft limit
    ): MessageProcessingResult = withContext(Dispatchers.IO) {
        
        val creditCost = creditManager.calculateMessageCost(inputTokens, outputTokens, modelName, tier)
        val currentBalance = creditManager.getRemainingCredits(userId, tier)
        
        // Check if emergency credits are needed and allowed
        if (currentBalance >= creditCost) {
            // Regular processing
            return@withContext processMessage(userId, modelName, inputTokens, outputTokens, tier)
        }
        
        val emergencyNeeded = creditCost - currentBalance
        val todayDate = dateFormat.format(Date())
        val todayRecord = database.userCreditDao().getCreditsByUserAndDate(userId, todayDate)
        val emergencyUsedToday = todayRecord?.emergencyCreditsUsed ?: 0.0
        
        if (emergencyUsedToday + emergencyNeeded > maxEmergencyCredits) {
            return@withContext MessageProcessingResult(
                success = false,
                creditCost = creditCost,
                balanceBefore = currentBalance,
                balanceAfter = currentBalance,
                reason = "Emergency credit limit exceeded"
            )
        }
        
        // Process with emergency credits
        val balanceBefore = currentBalance
        val balanceAfter = 0.0 // Will be negative but we'll track it
        
        // Log usage and emergency transaction
        database.usageLogDao().insertUsageLog(
            CreditUsageLogEntity(
                userId = userId,
                modelName = modelName,
                inputTokens = inputTokens,
                outputTokens = outputTokens,
                creditCost = creditCost,
                subscriptionTier = tier.name
            )
        )
        
        database.transactionDao().insertTransaction(
            CreditTransactionEntity(
                userId = userId,
                transactionType = "EMERGENCY",
                amount = -creditCost,
                balanceBefore = balanceBefore,
                balanceAfter = balanceAfter,
                description = "Emergency message using $modelName (overdraft: $${String.format("%.3f", emergencyNeeded)})"
            )
        )
        
        // Update daily record with emergency usage
        val updatedRecord = (todayRecord ?: UserCreditEntity(
            userId = userId,
            date = todayDate,
            subscriptionTier = tier.name,
            dailyAllowance = SubscriptionTiers.getConfig(tier).dailyCredits
        )).copy(
            creditsUsed = (todayRecord?.creditsUsed ?: 0.0) + creditCost,
            emergencyCreditsUsed = emergencyUsedToday + emergencyNeeded,
            updatedAt = System.currentTimeMillis()
        )
        
        database.userCreditDao().insertOrUpdate(updatedRecord)
        
        MessageProcessingResult(
            success = true,
            creditCost = creditCost,
            balanceBefore = balanceBefore,
            balanceAfter = balanceAfter,
            reason = "Processed with emergency credits"
        )
    }
    
    private suspend fun updateDailyRecord(userId: String, tier: SubscriptionTier, creditCost: Double) {
        val todayDate = dateFormat.format(Date())
        val existing = database.userCreditDao().getCreditsByUserAndDate(userId, todayDate)
        
        val updated = (existing ?: UserCreditEntity(
            userId = userId,
            date = todayDate,
            subscriptionTier = tier.name,
            dailyAllowance = SubscriptionTiers.getConfig(tier).dailyCredits
        )).copy(
            creditsUsed = (existing?.creditsUsed ?: 0.0) + creditCost,
            updatedAt = System.currentTimeMillis()
        )
        
        database.userCreditDao().insertOrUpdate(updated)
    }
    
    private fun generateRecommendations(
        userId: String,
        tier: SubscriptionTier,
        modelUsage: List<ModelUsageSummary>,
        usagePercentage: Double
    ): List<String> {
        val recommendations = mutableListOf<String>()
        
        if (usagePercentage > 0.9) {
            recommendations.add("⚠️ You've used 90%+ of your daily credits. Consider upgrading your plan.")
        }
        
        if (usagePercentage > 0.7) {
            recommendations.add("💡 Switch to cheaper models like GPT-3.5 for simple tasks to conserve credits.")
        }
        
        val expensiveModels = modelUsage.filter { it.totalCost > 1.0 }
        if (expensiveModels.isNotEmpty()) {
            recommendations.add("🎯 Most expensive: ${expensiveModels.first().modelName} (${String.format("%.2f", expensiveModels.first().totalCost)})")
        }
        
        if (modelUsage.size > 3) {
            recommendations.add("📊 You're using ${modelUsage.size} different models. Focus on 2-3 for better cost efficiency.")
        }
        
        return recommendations
    }
}

/**
 * Data classes for repository results
 */
data class MessageProcessingResult(
    val success: Boolean,
    val creditCost: Double,
    val balanceBefore: Double,
    val balanceAfter: Double,
    val reason: String
)

data class UsageAnalytics(
    val totalCreditsSpent: Double,
    val totalMessages: Int,
    val averageCostPerMessage: Double,
    val modelUsageBreakdown: List<ModelUsageSummary>,
    val currentBalance: Double,
    val dailyAllowance: Double,
    val usagePercentage: Double,
    val daysAnalyzed: Int,
    val recommendations: List<String>
)

data class CostPreview(
    val remainingCredits: Double,
    val modelCosts: Map<String, Double>,
    val messagesRemaining: Map<String, Int>,
    val recommendations: List<SmartModelRecommendation.ModelRecommendation>,
    val lowCreditWarning: Boolean
)