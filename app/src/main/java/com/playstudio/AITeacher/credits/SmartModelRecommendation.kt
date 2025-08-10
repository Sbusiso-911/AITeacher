package com.playstudio.aiteacher.credits

import com.playstudio.aiteacher.pricing.AIModel
import com.playstudio.aiteacher.pricing.SubscriptionTier
import com.playstudio.aiteacher.pricing.ComplexityLevel

/**
 * Smart model recommendation system that considers cost, remaining credits, and task complexity
 */
class SmartModelRecommendation(
    private val creditManager: CreditManager
) {
    
    data class ModelRecommendation(
        val model: AIModel,
        val estimatedCost: Double,
        val messagesRemaining: Int,
        val reason: RecommendationReason,
        val priority: Int // Higher = better recommendation
    )
    
    enum class RecommendationReason {
        BEST_VALUE,           // Good balance of cost and capability
        MOST_AFFORDABLE,      // Cheapest option available
        HIGHEST_QUALITY,      // Best model user can afford
        EMERGENCY_ONLY,       // Very expensive, use sparingly
        INSUFFICIENT_CREDITS  // Cannot afford this model
    }
    
    /**
     * Get model recommendations based on user's remaining credits and task complexity
     */
    fun getRecommendations(
        userId: String,
        tier: SubscriptionTier,
        complexity: ComplexityLevel = ComplexityLevel.MEDIUM
    ): List<ModelRecommendation> {
        val remainingCredits = creditManager.getRemainingCredits(userId, tier)
        val recommendations = mutableListOf<ModelRecommendation>()
        
        AIModel.getAllModels().forEach { model ->
            val estimatedCost = creditManager.calculateEstimatedCost(model.modelId, tier)
            val messagesRemaining = if (estimatedCost > 0) {
                (remainingCredits / estimatedCost).toInt()
            } else 0
            
            val reason = determineRecommendationReason(
                model, estimatedCost, remainingCredits, complexity, tier
            )
            
            val priority = calculatePriority(model, estimatedCost, remainingCredits, complexity)
            
            recommendations.add(
                ModelRecommendation(
                    model = model,
                    estimatedCost = estimatedCost,
                    messagesRemaining = messagesRemaining,
                    reason = reason,
                    priority = priority
                )
            )
        }
        
        return recommendations.sortedByDescending { it.priority }
    }
    
    /**
     * Get the best model recommendation for a specific task complexity
     */
    fun getBestRecommendation(
        userId: String,
        tier: SubscriptionTier,
        complexity: ComplexityLevel = ComplexityLevel.MEDIUM
    ): ModelRecommendation? {
        return getRecommendations(userId, tier, complexity)
            .filter { it.reason != RecommendationReason.INSUFFICIENT_CREDITS }
            .firstOrNull()
    }
    
    /**
     * Check if user should be warned about low credits
     */
    fun shouldShowLowCreditWarning(userId: String, tier: SubscriptionTier): Boolean {
        val usagePercentage = creditManager.getCreditUsagePercentage(userId, tier)
        return usagePercentage > 0.8 // Warn when 80% used
    }
    
    /**
     * Get cost comparison between models
     */
    fun getCostComparison(
        userId: String,
        tier: SubscriptionTier,
        modelIds: List<String>
    ): Map<String, Double> {
        return modelIds.associateWith { modelId ->
            creditManager.calculateEstimatedCost(modelId, tier)
        }
    }
    
    private fun determineRecommendationReason(
        model: AIModel,
        estimatedCost: Double,
        remainingCredits: Double,
        complexity: ComplexityLevel,
        tier: SubscriptionTier
    ): RecommendationReason {
        if (remainingCredits < estimatedCost) {
            return RecommendationReason.INSUFFICIENT_CREDITS
        }
        
        val messagesRemaining = (remainingCredits / estimatedCost).toInt()
        val config = SubscriptionTiers.getConfig(tier)
        val dailyBudgetPercentage = estimatedCost / config.dailyCredits
        
        return when {
            // Very expensive models (>25% of daily budget per message)
            dailyBudgetPercentage > 0.25 -> RecommendationReason.EMERGENCY_ONLY
            
            // High capability models that are affordable
            model.capabilities >= 8 && messagesRemaining >= 5 -> RecommendationReason.HIGHEST_QUALITY
            
            // Best value models (good capability-to-cost ratio)
            model.capabilities >= 6 && dailyBudgetPercentage < 0.15 -> RecommendationReason.BEST_VALUE
            
            // Cheapest available models
            dailyBudgetPercentage < 0.05 -> RecommendationReason.MOST_AFFORDABLE
            
            else -> RecommendationReason.BEST_VALUE
        }
    }
    
    private fun calculatePriority(
        model: AIModel,
        estimatedCost: Double,
        remainingCredits: Double,
        complexity: ComplexityLevel
    ): Int {
        if (remainingCredits < estimatedCost) {
            return -1 // Cannot afford
        }
        
        val messagesRemaining = (remainingCredits / estimatedCost).toInt()
        var priority = 0
        
        // Capability bonus based on task complexity
        when (complexity) {
            ComplexityLevel.LOW -> {
                // For simple tasks, prefer cheaper models
                priority += (10 - model.capabilities) * 2
                priority += minOf(messagesRemaining, 20) // Prefer models with more remaining uses
            }
            ComplexityLevel.MEDIUM -> {
                // Balanced approach
                priority += model.capabilities
                priority += minOf(messagesRemaining / 2, 10)
            }
            ComplexityLevel.HIGH -> {
                // For complex tasks, prioritize capability
                priority += model.capabilities * 2
                priority += minOf(messagesRemaining / 4, 5)
            }
        }
        
        // Affordability bonus
        if (messagesRemaining >= 10) priority += 5
        if (messagesRemaining >= 20) priority += 5
        
        return priority
    }
    
    /**
     * Generate user-friendly cost explanation
     */
    fun generateCostExplanation(
        modelName: String,
        tier: SubscriptionTier,
        remainingCredits: Double
    ): String {
        val model = AIModel.fromModelId(modelName) ?: return "Unknown model"
        val estimatedCost = creditManager.calculateEstimatedCost(modelName, tier)
        val messagesRemaining = if (estimatedCost > 0) (remainingCredits / estimatedCost).toInt() else 0
        val config = SubscriptionTiers.getConfig(tier)
        val percentageOfBudget = (estimatedCost / config.dailyCredits * 100).toInt()
        
        return when {
            messagesRemaining == 0 -> "⚠️ Insufficient credits for this model"
            messagesRemaining == 1 -> "💡 This is your last message with ${model.displayName} today"
            messagesRemaining <= 3 -> "⚡ ${messagesRemaining} messages left with ${model.displayName}"
            percentageOfBudget >= 25 -> "💰 Premium model: Uses ${percentageOfBudget}% of daily budget per message"
            percentageOfBudget >= 15 -> "💳 ${model.displayName}: ~${messagesRemaining} messages available"
            else -> "✅ ${model.displayName}: Great value with ${messagesRemaining}+ messages available"
        }
    }
}