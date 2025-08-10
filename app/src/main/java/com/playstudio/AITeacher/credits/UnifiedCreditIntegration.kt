package com.playstudio.aiteacher.credits

import android.content.Context
import android.util.Log
import androidx.lifecycle.lifecycleScope
import com.playstudio.aiteacher.pricing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Integration layer that bridges the existing cost tracking system with the new unified credit system
 * This ensures backward compatibility while providing the new credit-based functionality
 */
class UnifiedCreditIntegration private constructor(
    private val context: Context
) {
    
    private val creditRepository = CreditRepository.getInstance(context)
    private val smartRecommendation = SmartModelRecommendation(CreditManager.getInstance(context))
    private val existingCostTracker = RealTimeCostTracker(context)
    
    companion object {
        private const val TAG = "UnifiedCreditIntegration"
        
        @Volatile
        private var INSTANCE: UnifiedCreditIntegration? = null
        
        fun getInstance(context: Context): UnifiedCreditIntegration {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: UnifiedCreditIntegration(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    /**
     * Check if user can send a message with the selected model
     * This replaces the old usage-based system with credit-based system
     */
    suspend fun canSendMessage(
        userId: String,
        modelId: String,
        tier: SubscriptionTier,
        estimatedInputTokens: Int = 0,
        estimatedOutputTokens: Int = 0
    ): MessagePermissionResult = withContext(Dispatchers.IO) {
        
        try {
            val creditManager = CreditManager.getInstance(context)
            
            // If we have actual token estimates, use them; otherwise use model averages
            val actualCost = if (estimatedInputTokens > 0 || estimatedOutputTokens > 0) {
                creditManager.calculateMessageCost(estimatedInputTokens, estimatedOutputTokens, modelId, tier)
            } else {
                creditManager.calculateEstimatedCost(modelId, tier)
            }
            
            val remainingCredits = creditManager.getRemainingCredits(userId, tier)
            val canAfford = creditManager.canAffordMessage(userId, modelId, tier)
            
            if (!canAfford) {
                // Check if emergency credits can help
                val config = SubscriptionTiers.getConfig(tier)
                val emergencyNeeded = actualCost - remainingCredits
                
                if (emergencyNeeded <= 0.50) { // $0.50 emergency limit
                    return@withContext MessagePermissionResult.AllowedWithEmergency(
                        cost = actualCost,
                        emergencyAmount = emergencyNeeded,
                        remainingCredits = remainingCredits
                    )
                }
                
                // Suggest model alternatives
                val alternatives = getAffordableAlternatives(userId, tier, actualCost)
                
                return@withContext MessagePermissionResult.Denied(
                    reason = "Insufficient credits",
                    cost = actualCost,
                    remainingCredits = remainingCredits,
                    suggestedAlternatives = alternatives,
                    suggestedUpgrade = getSuggestedUpgrade(tier, actualCost)
                )
            }
            
            // Additional safety check using existing cost tracker
            val model = AIModel.fromModelId(modelId)
            if (model != null) {
                val legacyCheck = existingCostTracker.trackAPICall(
                    userId = userId,
                    model = model,
                    inputTokens = estimatedInputTokens.takeIf { it > 0 } ?: model.averageInputTokens,
                    outputTokens = estimatedOutputTokens.takeIf { it > 0 } ?: model.averageOutputTokens
                )
                
                when (legacyCheck) {
                    is CostTrackingResult.Denied -> {
                        return@withContext MessagePermissionResult.Denied(
                            reason = legacyCheck.reason,
                            cost = actualCost,
                            remainingCredits = remainingCredits,
                            suggestedAlternatives = getAffordableAlternatives(userId, tier, actualCost),
                            suggestedUpgrade = legacyCheck.suggestedUpgrade
                        )
                    }
                    is CostTrackingResult.UsageLimitExceeded -> {
                        return@withContext MessagePermissionResult.Denied(
                            reason = "Model usage limit exceeded: ${legacyCheck.limitType}",
                            cost = actualCost,
                            remainingCredits = remainingCredits,
                            suggestedAlternatives = getAffordableAlternatives(userId, tier, actualCost),
                            suggestedUpgrade = legacyCheck.suggestedUpgrade
                        )
                    }
                    is CostTrackingResult.EmergencyStop -> {
                        return@withContext MessagePermissionResult.EmergencyStop(
                            reason = legacyCheck.reason,
                            cost = legacyCheck.cost
                        )
                    }
                    is CostTrackingResult.Error -> {
                        Log.w(TAG, "Legacy cost tracker error: ${legacyCheck.message}")
                        // Continue with credit-based approval
                    }
                    is CostTrackingResult.Approved -> {
                        // Legacy system approves, continue with credit-based system
                    }
                }
            }
            
            // Generate smart recommendations for the user
            val usagePercentage = creditManager.getCreditUsagePercentage(userId, tier)
            val recommendations = if (usagePercentage > 0.7) {
                smartRecommendation.getRecommendations(userId, tier).take(3)
            } else {
                emptyList()
            }
            
            MessagePermissionResult.Allowed(
                cost = actualCost,
                remainingCredits = remainingCredits,
                usagePercentage = usagePercentage,
                recommendations = recommendations
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error checking message permission for user $userId", e)
            MessagePermissionResult.Error("Permission check failed: ${e.message}")
        }
    }
    
    /**
     * Process a completed message and deduct credits
     */
    suspend fun processCompletedMessage(
        userId: String,
        modelId: String,
        inputTokens: Int,
        outputTokens: Int,
        tier: SubscriptionTier,
        conversationId: String? = null,
        messageId: String? = null,
        useEmergencyCredits: Boolean = false
    ): MessageProcessingResult = withContext(Dispatchers.IO) {
        
        try {
            if (useEmergencyCredits) {
                creditRepository.processEmergencyMessage(
                    userId, modelId, inputTokens, outputTokens, tier
                )
            } else {
                creditRepository.processMessage(
                    userId, modelId, inputTokens, outputTokens, tier, conversationId, messageId
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing message for user $userId", e)
            MessageProcessingResult(
                success = false,
                creditCost = 0.0,
                balanceBefore = 0.0,
                balanceAfter = 0.0,
                reason = "Processing failed: ${e.message}"
            )
        }
    }
    
    /**
     * Get real-time cost preview and model recommendations
     */
    suspend fun getCostPreviewForChat(
        userId: String,
        tier: SubscriptionTier,
        complexity: ComplexityLevel = ComplexityLevel.MEDIUM
    ): ChatCostPreview = withContext(Dispatchers.IO) {
        
        val costPreview = creditRepository.getCostPreview(userId, tier)
        val recommendations = smartRecommendation.getRecommendations(userId, tier, complexity)
        val analytics = creditRepository.getUsageAnalytics(userId, tier, 1) // Today only
        
        ChatCostPreview(
            remainingCredits = costPreview.remainingCredits,
            dailyAllowance = SubscriptionTiers.getConfig(tier).dailyCredits,
            usagePercentage = CreditManager.getInstance(context).getCreditUsagePercentage(userId, tier),
            modelRecommendations = recommendations.take(5),
            todayStats = ChatUsageStats(
                messagesCount = analytics.totalMessages,
                creditsSpent = analytics.totalCreditsSpent,
                averageCostPerMessage = analytics.averageCostPerMessage
            ),
            lowCreditWarning = costPreview.lowCreditWarning,
            emergencyCreditsAvailable = calculateEmergencyCreditsAvailable(userId)
        )
    }
    
    /**
     * Get affordable model alternatives when user can't afford selected model
     */
    private suspend fun getAffordableAlternatives(
        userId: String,
        tier: SubscriptionTier,
        maxCost: Double
    ): List<ModelAlternative> {
        val remainingCredits = CreditManager.getInstance(context).getRemainingCredits(userId, tier)
        val recommendations = smartRecommendation.getRecommendations(userId, tier)
        
        return recommendations
            .filter { it.estimatedCost <= remainingCredits && it.estimatedCost < maxCost }
            .take(3)
            .map { recommendation ->
                ModelAlternative(
                    modelId = recommendation.model.modelId,
                    displayName = recommendation.model.displayName,
                    estimatedCost = recommendation.estimatedCost,
                    messagesRemaining = recommendation.messagesRemaining,
                    reason = recommendation.reason.toString()
                )
            }
    }
    
    /**
     * Suggest subscription tier upgrade based on usage patterns
     */
    private fun getSuggestedUpgrade(currentTier: SubscriptionTier, desiredCost: Double): SubscriptionTier? {
        val currentConfig = SubscriptionTiers.getConfig(currentTier)
        
        return when {
            desiredCost <= currentConfig.dailyCredits -> null // Current tier is sufficient
            currentTier == SubscriptionTier.FREE -> SubscriptionTier.BASIC
            currentTier == SubscriptionTier.BASIC -> SubscriptionTier.PRO
            currentTier == SubscriptionTier.PRO -> SubscriptionTier.PREMIUM
            currentTier == SubscriptionTier.PREMIUM -> SubscriptionTier.ENTERPRISE
            else -> null // Already on highest tier
        }
    }
    
    /**
     * Calculate available emergency credits
     */
    private suspend fun calculateEmergencyCreditsAvailable(userId: String): Double {
        val todayDate = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
        val todayRecord = creditRepository.database.userCreditDao().getCreditsByUserAndDate(userId, todayDate)
        val emergencyUsedToday = todayRecord?.emergencyCreditsUsed ?: 0.0
        return maxOf(0.0, 0.50 - emergencyUsedToday) // $0.50 max emergency credits
    }
    
    /**
     * Generate user-friendly cost explanation for UI
     */
    fun generateCostExplanation(
        modelId: String,
        tier: SubscriptionTier,
        remainingCredits: Double
    ): String {
        return smartRecommendation.generateCostExplanation(modelId, tier, remainingCredits)
    }
}

/**
 * Result of permission check for sending a message
 */
sealed class MessagePermissionResult {
    data class Allowed(
        val cost: Double,
        val remainingCredits: Double,
        val usagePercentage: Double,
        val recommendations: List<SmartModelRecommendation.ModelRecommendation>
    ) : MessagePermissionResult()
    
    data class AllowedWithEmergency(
        val cost: Double,
        val emergencyAmount: Double,
        val remainingCredits: Double
    ) : MessagePermissionResult()
    
    data class Denied(
        val reason: String,
        val cost: Double,
        val remainingCredits: Double,
        val suggestedAlternatives: List<ModelAlternative>,
        val suggestedUpgrade: SubscriptionTier?
    ) : MessagePermissionResult()
    
    data class EmergencyStop(
        val reason: String,
        val cost: Double
    ) : MessagePermissionResult()
    
    data class Error(
        val message: String
    ) : MessagePermissionResult()
}

/**
 * Cost preview specifically for chat interface
 */
data class ChatCostPreview(
    val remainingCredits: Double,
    val dailyAllowance: Double,
    val usagePercentage: Double,
    val modelRecommendations: List<SmartModelRecommendation.ModelRecommendation>,
    val todayStats: ChatUsageStats,
    val lowCreditWarning: Boolean,
    val emergencyCreditsAvailable: Double
)

/**
 * Today's usage statistics for chat
 */
data class ChatUsageStats(
    val messagesCount: Int,
    val creditsSpent: Double,
    val averageCostPerMessage: Double
)

/**
 * Model alternative suggestion
 */
data class ModelAlternative(
    val modelId: String,
    val displayName: String,
    val estimatedCost: Double,
    val messagesRemaining: Int,
    val reason: String
)