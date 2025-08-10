package com.playstudio.aiteacher.pricing

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.HashMap

/**
 * Real-time API cost tracking system
 * Prevents cost overruns and ensures subscription tier profitability
 */
class RealTimeCostTracker(private val context: Context) {
    
    private val costStorage = CostStorage(context)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    
    companion object {
        private const val TAG = "CostTracker"
        
        // Emergency brake - if any user exceeds this in a day, immediate shutdown
        private const val ABSOLUTE_DAILY_LIMIT = 500.0
        
        // Warning thresholds
        private const val WARNING_THRESHOLD_PERCENTAGE = 80.0
        private const val CRITICAL_THRESHOLD_PERCENTAGE = 95.0
    }
    
    /**
     * Track an API call and check if it's within budget
     */
    suspend fun trackAPICall(
        userId: String,
        model: AIModel,
        inputTokens: Int,
        outputTokens: Int,
        additionalFeatures: List<APIFeature> = emptyList(),
        useCache: Boolean = false
    ): CostTrackingResult = withContext(Dispatchers.IO) {
        
        try {
            val baseCost = model.calculateCost(inputTokens, outputTokens, useCache)
            val additionalCosts = additionalFeatures.sumOf { it.cost }
            val totalCost = baseCost + additionalCosts
            
            Log.d(TAG, "API call cost calculation: model=${model.displayName}, " +
                    "inputTokens=$inputTokens, outputTokens=$outputTokens, " +
                    "baseCost=$baseCost, additionalCosts=$additionalCosts, totalCost=$totalCost")
            
            // Emergency brake check
            if (totalCost > ABSOLUTE_DAILY_LIMIT) {
                Log.e(TAG, "EMERGENCY: Single API call cost exceeds absolute limit! " +
                        "Cost: $totalCost, User: $userId, Model: ${model.displayName}")
                return@withContext CostTrackingResult.EmergencyStop(
                    reason = "Single API call exceeds safety limit",
                    cost = totalCost,
                    limit = ABSOLUTE_DAILY_LIMIT
                )
            }
            
            // Get current usage and user plan
            val dailyUsage = getDailyUsage(userId)
            val userPlan = getUserPlan(userId)
            val projectedTotalCost = dailyUsage.totalCost + totalCost
            
            // Check if user can afford this call
            if (projectedTotalCost > userPlan.maxDailyCostLimit) {
                Log.w(TAG, "Cost limit exceeded for user $userId: " +
                        "current=${dailyUsage.totalCost}, requested=$totalCost, " +
                        "projected=$projectedTotalCost, limit=${userPlan.maxDailyCostLimit}")
                        
                return@withContext CostTrackingResult.Denied(
                    reason = "Daily cost limit exceeded",
                    currentCost = dailyUsage.totalCost,
                    requestCost = totalCost,
                    limit = userPlan.maxDailyCostLimit,
                    suggestedUpgrade = getSuggestedUpgrade(projectedTotalCost)
                )
            }
            
            // Check usage limits (messages, tokens, etc.)
            val usageLimitCheck = checkUsageLimits(userId, userPlan, model)
            if (!usageLimitCheck.allowed) {
                return@withContext CostTrackingResult.UsageLimitExceeded(
                    limitType = usageLimitCheck.limitType,
                    current = usageLimitCheck.current,
                    limit = usageLimitCheck.limit,
                    suggestedUpgrade = getSuggestedUpgrade(projectedTotalCost)
                )
            }
            
            // Record the cost and usage
            recordCost(userId, totalCost, model, inputTokens, outputTokens, additionalFeatures)
            
            // Calculate remaining budget and check warning thresholds
            val remainingBudget = userPlan.maxDailyCostLimit - projectedTotalCost
            val usagePercentage = (projectedTotalCost / userPlan.maxDailyCostLimit) * 100
            
            val warningLevel = when {
                usagePercentage >= CRITICAL_THRESHOLD_PERCENTAGE -> WarningLevel.CRITICAL
                usagePercentage >= WARNING_THRESHOLD_PERCENTAGE -> WarningLevel.WARNING
                else -> WarningLevel.NONE
            }
            
            Log.d(TAG, "API call approved for user $userId: cost=$totalCost, " +
                    "totalCost=$projectedTotalCost, remaining=$remainingBudget, " +
                    "usage=${usagePercentage}%")
            
            return@withContext CostTrackingResult.Approved(
                cost = totalCost,
                totalDailyCost = projectedTotalCost,
                remainingBudget = remainingBudget,
                warningLevel = warningLevel,
                usagePercentage = usagePercentage
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error tracking API call for user $userId", e)
            return@withContext CostTrackingResult.Error(
                message = "Failed to track API cost: ${e.message}"
            )
        }
    }
    
    /**
     * Get detailed daily cost analysis for a user
     */
    suspend fun getDailyCostAnalysis(userId: String): DailyCostAnalysis = withContext(Dispatchers.IO) {
        val usage = getDailyUsage(userId)
        val plan = getUserPlan(userId)
        
        DailyCostAnalysis(
            userId = userId,
            date = getCurrentDate(),
            totalCost = usage.totalCost,
            costByModel = usage.costByModel,
            costByFeature = usage.costByFeature,
            messageCount = usage.messageCount,
            tokenCount = usage.tokenCount,
            remainingBudget = plan.maxDailyCostLimit - usage.totalCost,
            dailyLimit = plan.maxDailyCostLimit,
            usagePercentage = (usage.totalCost / plan.maxDailyCostLimit) * 100,
            projectedMonthlyCost = usage.totalCost * 30,
            subscriptionTier = plan.tier,
            isProfiTable = calculateUserProfitability(userId, usage, plan)
        )
    }
    
    /**
     * Check if user has exceeded usage limits (beyond cost) including model-specific limits
     */
    private suspend fun checkUsageLimits(userId: String, plan: CostAwareSubscriptionPlan, requestedModel: AIModel): UsageLimitResult {
        val usage = getDailyUsage(userId)
        
        // Check general limits first
        when {
            usage.messageCount >= plan.dailyLimits.messages -> {
                return UsageLimitResult(false, "messages", usage.messageCount, plan.dailyLimits.messages)
            }
            usage.tokenCount >= plan.dailyLimits.tokens -> {
                return UsageLimitResult(false, "tokens", usage.tokenCount, plan.dailyLimits.tokens)
            }
            usage.imageCount >= plan.dailyLimits.images -> {
                return UsageLimitResult(false, "images", usage.imageCount, plan.dailyLimits.images)
            }
            usage.webSearchCount >= plan.dailyLimits.webSearches -> {
                return UsageLimitResult(false, "web_searches", usage.webSearchCount, plan.dailyLimits.webSearches)
            }
        }
        
        // Check model-specific daily limits (CRITICAL for expensive models)
        val userTier = plan.tier
        val modelUsageLimit = requestedModel.getUsageLimitForTier(userTier)
        if (modelUsageLimit > 0) {
            val modelUsageToday = getDailyModelUsage(userId, requestedModel)
            if (modelUsageToday >= modelUsageLimit) {
                return UsageLimitResult(
                    false, 
                    "model_${requestedModel.displayName.replace(" ", "_").lowercase()}", 
                    modelUsageToday, 
                    modelUsageLimit
                )
            }
        }
        
        return UsageLimitResult(true, "", 0, 0)
    }
    
    /**
     * Get daily usage count for a specific model (CRITICAL for expensive models)
     */
    private suspend fun getDailyModelUsage(userId: String, model: AIModel): Int {
        val today = getCurrentDate()
        val dailyUsage = costStorage.getDailyUsage(userId, today)
        return dailyUsage.modelUsageCount[model] ?: 0
    }
    
    /**
     * Record cost and usage to persistent storage
     */
    private suspend fun recordCost(
        userId: String,
        cost: Double,
        model: AIModel,
        inputTokens: Int,
        outputTokens: Int,
        features: List<APIFeature>
    ) {
        val today = getCurrentDate()
        costStorage.recordAPICall(
            APICallRecord(
                userId = userId,
                date = today,
                timestamp = System.currentTimeMillis(),
                model = model,
                inputTokens = inputTokens,
                outputTokens = outputTokens,
                cost = cost,
                features = features
            )
        )
    }
    
    /**
     * Get current daily usage for user
     */
    private suspend fun getDailyUsage(userId: String): DailyUsage {
        val today = getCurrentDate()
        return costStorage.getDailyUsage(userId, today)
    }
    
    /**
     * Get user's current subscription plan
     */
    private fun getUserPlan(userId: String): CostAwareSubscriptionPlan {
        // TODO: Integrate with subscription system
        // For now, return FREE plan as default
        val userTier = costStorage.getUserSubscriptionTier(userId) ?: SubscriptionTier.FREE
        return SubscriptionPlans.getPlanForTier(userTier)
    }
    
    /**
     * Suggest upgrade based on usage patterns
     */
    private fun getSuggestedUpgrade(projectedCost: Double): SubscriptionTier? {
        return when {
            projectedCost <= SubscriptionPlans.BASIC_PLAN.maxDailyCostLimit -> SubscriptionTier.BASIC
            projectedCost <= SubscriptionPlans.PRO_PLAN.maxDailyCostLimit -> SubscriptionTier.PRO
            projectedCost <= SubscriptionPlans.PREMIUM_PLAN.maxDailyCostLimit -> SubscriptionTier.PREMIUM
            else -> SubscriptionTier.ENTERPRISE
        }
    }
    
    /**
     * Calculate if user is profitable
     */
    private fun calculateUserProfitability(
        userId: String,
        usage: DailyUsage,
        plan: CostAwareSubscriptionPlan
    ): Boolean {
        return plan.dailyRevenue > usage.totalCost
    }
    
    private fun getCurrentDate(): String {
        return dateFormat.format(Date())
    }
    
    /**
     * Get cost breakdown by time period for analytics
     */
    suspend fun getCostAnalytics(
        userId: String,
        startDate: String,
        endDate: String
    ): CostAnalytics = withContext(Dispatchers.IO) {
        costStorage.getCostAnalytics(userId, startDate, endDate)
    }
    
    /**
     * Reset daily usage (called by daily cleanup job)
     */
    suspend fun resetDailyUsage() = withContext(Dispatchers.IO) {
        costStorage.cleanupOldRecords()
    }
}

/**
 * Results from cost tracking
 */
sealed class CostTrackingResult {
    data class Approved(
        val cost: Double,
        val totalDailyCost: Double,
        val remainingBudget: Double,
        val warningLevel: WarningLevel,
        val usagePercentage: Double
    ) : CostTrackingResult()
    
    data class Denied(
        val reason: String,
        val currentCost: Double,
        val requestCost: Double,
        val limit: Double,
        val suggestedUpgrade: SubscriptionTier?
    ) : CostTrackingResult()
    
    data class UsageLimitExceeded(
        val limitType: String,
        val current: Int,
        val limit: Int,
        val suggestedUpgrade: SubscriptionTier?
    ) : CostTrackingResult()
    
    data class EmergencyStop(
        val reason: String,
        val cost: Double,
        val limit: Double
    ) : CostTrackingResult()
    
    data class Error(
        val message: String
    ) : CostTrackingResult()
}

/**
 * Warning levels for cost usage
 */
enum class WarningLevel {
    NONE,
    WARNING,    // 80%+ of budget used
    CRITICAL    // 95%+ of budget used
}

/**
 * Usage limit check result
 */
data class UsageLimitResult(
    val allowed: Boolean,
    val limitType: String,
    val current: Int,
    val limit: Int
)

/**
 * Daily cost analysis for a user
 */
data class DailyCostAnalysis(
    val userId: String,
    val date: String,
    val totalCost: Double,
    val costByModel: Map<AIModel, Double>,
    val costByFeature: Map<APIFeature, Double>,
    val messageCount: Int,
    val tokenCount: Int,
    val remainingBudget: Double,
    val dailyLimit: Double,
    val usagePercentage: Double,
    val projectedMonthlyCost: Double,
    val subscriptionTier: SubscriptionTier,
    val isProfiTable: Boolean
)

/**
 * Daily usage aggregation
 */
data class DailyUsage(
    val userId: String,
    val date: String,
    val totalCost: Double,
    val costByModel: Map<AIModel, Double>,
    val costByFeature: Map<APIFeature, Double>,
    val messageCount: Int,
    val tokenCount: Int,
    val imageCount: Int,
    val webSearchCount: Int,
    val modelUsageCount: Map<AIModel, Int> = emptyMap() // CRITICAL: Track usage per model
)

/**
 * Cost analytics over time period
 */
data class CostAnalytics(
    val userId: String,
    val startDate: String,
    val endDate: String,
    val totalCost: Double,
    val averageDailyCost: Double,
    val peakDailyCost: Double,
    val costTrend: List<Pair<String, Double>>, // Date to cost mapping
    val modelUsage: Map<AIModel, Int>,
    val featureUsage: Map<APIFeature, Int>
)