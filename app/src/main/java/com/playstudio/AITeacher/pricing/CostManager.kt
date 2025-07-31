package com.playstudio.aiteacher.pricing

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Central cost management system that integrates with the chat functionality
 * Provides easy-to-use interface for the ChatFragment
 */
class CostManager private constructor(private val context: Context) {
    
    private val costTracker = RealTimeCostTracker(context)
    private val modelSelector = CostOptimizedModelSelector()
    private val costStorage = CostStorage(context)
    
    companion object {
        private const val TAG = "CostManager"
        
        @Volatile
        private var INSTANCE: CostManager? = null
        
        fun getInstance(context: Context): CostManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: CostManager(context.applicationContext).also { INSTANCE = it }
            }
        }
        
        // Default user ID for cases where user authentication isn't implemented yet
        private const val DEFAULT_USER_ID = "default_user"
    }
    
    /**
     * Check if a chat request can be processed and get the optimal model
     */
    suspend fun checkAndSelectModel(
        userId: String = DEFAULT_USER_ID,
        messageText: String,
        hasImages: Boolean = false,
        hasDocuments: Boolean = false,
        preferredModelId: String? = null
    ): ChatRequestResult {
        
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Processing chat request for user: $userId, message length: ${messageText.length}")
                
                // Analyze message complexity
                val complexity = modelSelector.analyzeComplexity(
                    messageText = messageText,
                    hasImages = hasImages,
                    hasDocuments = hasDocuments
                )
                
                // Get user's subscription tier and remaining budget
                val userTier = costStorage.getUserSubscriptionTier(userId) ?: SubscriptionTier.FREE
                val userPlan = SubscriptionPlans.getPlanForTier(userTier)
                val dailyUsage = costTracker.getDailyCostAnalysis(userId)
                val remainingBudget = dailyUsage.remainingBudget
                
                Log.d(TAG, "User tier: $userTier, remaining budget: $remainingBudget, complexity: $complexity")
                
                // Create chat request
                val preferredModel = preferredModelId?.let { AIModel.fromModelId(it) }
                val chatRequest = ChatRequest(
                    messageText = messageText,
                    complexity = complexity,
                    hasImages = hasImages,
                    hasDocuments = hasDocuments,
                    userPreferences = UserPreferences(preferredModel = preferredModel)
                )
                
                // Select optimal model
                val modelSelection = modelSelector.selectOptimalModel(
                    request = chatRequest,
                    userTier = userTier,
                    remainingBudget = remainingBudget,
                    preferredModel = preferredModel
                )
                
                when (modelSelection) {
                    is ModelSelectionResult.Selected -> {
                        ChatRequestResult.Approved(
                            selectedModel = modelSelection.model,
                            estimatedCost = modelSelection.estimatedCost,
                            remainingBudget = remainingBudget - modelSelection.estimatedCost,
                            reason = modelSelection.reason,
                            complexity = complexity
                        )
                    }
                    
                    is ModelSelectionResult.InsufficientBudget -> {
                        ChatRequestResult.BudgetExceeded(
                            requiredBudget = modelSelection.requiredBudget,
                            currentBudget = modelSelection.currentBudget,
                            suggestedUpgrade = getSuggestedUpgrade(userTier),
                            cheapestModel = modelSelection.cheapestOption
                        )
                    }
                    
                    is ModelSelectionResult.NoModelsAvailable -> {
                        ChatRequestResult.NoAccess(
                            reason = "No models available for your subscription tier",
                            suggestedUpgrade = SubscriptionTier.BASIC
                        )
                    }
                    
                    is ModelSelectionResult.Error -> {
                        ChatRequestResult.Error(modelSelection.message)
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error processing chat request", e)
                ChatRequestResult.Error("Failed to process request: ${e.message}")
            }
        }
    }
    
    /**
     * Record the actual cost after API call completion
     */
    suspend fun recordActualCost(
        userId: String = DEFAULT_USER_ID,
        model: AIModel,
        inputTokens: Int,
        outputTokens: Int,
        additionalFeatures: List<APIFeature> = emptyList()
    ): CostTrackingResult {
        
        return costTracker.trackAPICall(
            userId = userId,
            model = model,
            inputTokens = inputTokens,
            outputTokens = outputTokens,
            additionalFeatures = additionalFeatures
        )
    }
    
    /**
     * Get user's current cost status
     */
    suspend fun getUserCostStatus(userId: String = DEFAULT_USER_ID): UserCostStatus {
        return withContext(Dispatchers.IO) {
            try {
                val analysis = costTracker.getDailyCostAnalysis(userId)
                val userTier = costStorage.getUserSubscriptionTier(userId) ?: SubscriptionTier.FREE
                val plan = SubscriptionPlans.getPlanForTier(userTier)
                
                UserCostStatus(
                    tier = userTier,
                    dailyCost = analysis.totalCost,
                    dailyLimit = analysis.dailyLimit,
                    remainingBudget = analysis.remainingBudget,
                    usagePercentage = analysis.usagePercentage,
                    messageCount = analysis.messageCount,
                    messageLimit = plan.dailyLimits.messages,
                    isProfiTable = analysis.isProfiTable,
                    warningLevel = when {
                        analysis.usagePercentage >= 95 -> WarningLevel.CRITICAL
                        analysis.usagePercentage >= 80 -> WarningLevel.WARNING
                        else -> WarningLevel.NONE
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error getting user cost status", e)
                UserCostStatus.getDefault()
            }
        }
    }
    
    /**
     * Set user's subscription tier
     */
    fun setUserSubscriptionTier(userId: String = DEFAULT_USER_ID, tier: SubscriptionTier) {
        costStorage.setUserSubscriptionTier(userId, tier)
        Log.d(TAG, "Updated subscription tier for user $userId to $tier")
    }
    
    /**
     * Get available models for user
     */
    fun getAvailableModels(userId: String = DEFAULT_USER_ID): List<AIModel> {
        val userTier = costStorage.getUserSubscriptionTier(userId) ?: SubscriptionTier.FREE
        return AIModel.getModelsForTier(userTier)
    }
    
    /**
     * Get cost analytics for user
     */
    suspend fun getCostAnalytics(
        userId: String = DEFAULT_USER_ID,
        days: Int = 7
    ): CostAnalytics {
        val endDate = getCurrentDate()
        val startDate = getDateDaysAgo(days)
        return costTracker.getCostAnalytics(userId, startDate, endDate)
    }
    
    /**
     * Check if user needs to upgrade based on usage patterns
     */
    suspend fun checkUpgradeRecommendation(userId: String = DEFAULT_USER_ID): UpgradeRecommendation? {
        return withContext(Dispatchers.IO) {
            try {
                val analytics = getCostAnalytics(userId, 7)
                val currentTier = costStorage.getUserSubscriptionTier(userId) ?: SubscriptionTier.FREE
                val currentPlan = SubscriptionPlans.getPlanForTier(currentTier)
                
                // Check if user consistently hits limits
                val averageDailyCost = analytics.averageDailyCost
                val utilizationRate = averageDailyCost / currentPlan.maxDailyCostLimit
                
                when {
                    utilizationRate >= 0.9 -> {
                        val suggestedTier = getSuggestedUpgrade(currentTier)
                        if (suggestedTier != null) {
                            UpgradeRecommendation(
                                currentTier = currentTier,
                                suggestedTier = suggestedTier,
                                reason = "You're using ${(utilizationRate * 100).toInt()}% of your daily budget",
                                potentialSavings = calculatePotentialSavings(currentTier, suggestedTier),
                                urgency = UpgradeUrgency.HIGH
                            )
                        } else null
                    }
                    
                    utilizationRate >= 0.7 -> {
                        val suggestedTier = getSuggestedUpgrade(currentTier)
                        if (suggestedTier != null) {
                            UpgradeRecommendation(
                                currentTier = currentTier,
                                suggestedTier = suggestedTier,
                                reason = "Upgrade for more features and higher limits",
                                potentialSavings = calculatePotentialSavings(currentTier, suggestedTier),
                                urgency = UpgradeUrgency.MEDIUM
                            )
                        } else null
                    }
                    
                    else -> null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking upgrade recommendation", e)
                null
            }
        }
    }
    
    /**
     * Get suggested upgrade tier
     */
    private fun getSuggestedUpgrade(currentTier: SubscriptionTier): SubscriptionTier? {
        return when (currentTier) {
            SubscriptionTier.FREE -> SubscriptionTier.BASIC
            SubscriptionTier.BASIC -> SubscriptionTier.PRO
            SubscriptionTier.PRO -> SubscriptionTier.PREMIUM
            SubscriptionTier.PREMIUM -> SubscriptionTier.ULTRA_PREMIUM
            SubscriptionTier.ULTRA_PREMIUM -> null
        }
    }
    
    /**
     * Calculate potential savings from upgrade
     */
    private fun calculatePotentialSavings(currentTier: SubscriptionTier, suggestedTier: SubscriptionTier): Double {
        val currentPlan = SubscriptionPlans.getPlanForTier(currentTier)
        val suggestedPlan = SubscriptionPlans.getPlanForTier(suggestedTier)
        
        // Calculate value of additional features
        val additionalValue = (suggestedPlan.dailyLimits.messages - currentPlan.dailyLimits.messages) * 0.01
        return additionalValue
    }
    
    /**
     * Utility functions
     */
    private fun getCurrentDate(): String {
        return java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
    }
    
    private fun getDateDaysAgo(days: Int): String {
        val calendar = java.util.Calendar.getInstance()
        calendar.add(java.util.Calendar.DAY_OF_MONTH, -days)
        return java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(calendar.time)
    }
}

/**
 * Result of processing a chat request
 */
sealed class ChatRequestResult {
    data class Approved(
        val selectedModel: AIModel,
        val estimatedCost: Double,
        val remainingBudget: Double,
        val reason: String,
        val complexity: ComplexityLevel
    ) : ChatRequestResult()
    
    data class BudgetExceeded(
        val requiredBudget: Double,
        val currentBudget: Double,
        val suggestedUpgrade: SubscriptionTier?,
        val cheapestModel: AIModel?
    ) : ChatRequestResult()
    
    data class NoAccess(
        val reason: String,
        val suggestedUpgrade: SubscriptionTier
    ) : ChatRequestResult()
    
    data class Error(
        val message: String
    ) : ChatRequestResult()
}

/**
 * User's current cost status
 */
data class UserCostStatus(
    val tier: SubscriptionTier,
    val dailyCost: Double,
    val dailyLimit: Double,
    val remainingBudget: Double,
    val usagePercentage: Double,
    val messageCount: Int,
    val messageLimit: Int,
    val isProfiTable: Boolean,
    val warningLevel: WarningLevel
) {
    companion object {
        fun getDefault() = UserCostStatus(
            tier = SubscriptionTier.FREE,
            dailyCost = 0.0,
            dailyLimit = 0.1,
            remainingBudget = 0.1,
            usagePercentage = 0.0,
            messageCount = 0,
            messageLimit = 10,
            isProfiTable = false,
            warningLevel = WarningLevel.NONE
        )
    }
}

/**
 * Upgrade recommendation
 */
data class UpgradeRecommendation(
    val currentTier: SubscriptionTier,
    val suggestedTier: SubscriptionTier,
    val reason: String,
    val potentialSavings: Double,
    val urgency: UpgradeUrgency
)

enum class UpgradeUrgency {
    LOW, MEDIUM, HIGH
}