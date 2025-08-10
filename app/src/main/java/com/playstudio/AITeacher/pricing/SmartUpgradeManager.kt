package com.playstudio.aiteacher.pricing

import android.content.Context
import android.content.SharedPreferences
import com.playstudio.aiteacher.billing.ProrationManager
import com.playstudio.aiteacher.billing.UpgradeCredit
import com.playstudio.aiteacher.billing.UpgradePrice
import kotlin.math.roundToInt

/**
 * Smart upgrade manager that calculates personalized upgrade recommendations
 * based on user's current usage patterns and subscription tier
 */
class SmartUpgradeManager(private val context: Context) {
    
    private val usageTracker = UsageTracker(context)
    private val prorationManager = ProrationManager(context)
    private val subscriptionPrefs = context.getSharedPreferences("subscription_prefs", Context.MODE_PRIVATE)
    
    /**
     * Get the next available upgrade tier
     */
    fun getNextUpgradeTier(currentTier: SubscriptionTier): SubscriptionTier? {
        return when (currentTier) {
            SubscriptionTier.FREE -> SubscriptionTier.BASIC
            SubscriptionTier.BASIC -> SubscriptionTier.PRO
            SubscriptionTier.PRO -> SubscriptionTier.PREMIUM
            SubscriptionTier.PREMIUM -> SubscriptionTier.ENTERPRISE
            SubscriptionTier.ENTERPRISE -> null // Already at top tier
        }
    }
    
    /**
     * Check if user has reached the maximum tier
     */
    fun isAtMaxTier(currentTier: SubscriptionTier): Boolean {
        return currentTier == SubscriptionTier.ENTERPRISE
    }
    
    /**
     * Calculate upgrade recommendation based on usage patterns
     */
    fun getUpgradeRecommendation(currentTier: SubscriptionTier): SmartUpgradeRecommendation? {
        val nextTier = getNextUpgradeTier(currentTier) ?: return null
        
        // Calculate usage statistics
        val usageStats = calculateUsageStats(currentTier)
        val nextTierBenefits = calculateTierBenefits(currentTier, nextTier)
        
        // Calculate discount based on usage pressure
        val discount = calculateUsageBasedDiscount(usageStats)
        
        // Determine urgency level
        val urgency = calculateUpgradeUrgency(usageStats)
        
        return SmartUpgradeRecommendation(
            currentTier = currentTier,
            recommendedTier = nextTier,
            usageStats = usageStats,
            benefits = nextTierBenefits,
            discount = discount,
            urgency = urgency
        )
    }
    
    /**
     * Get upgrade recommendation with detailed pricing information
     */
    fun getUpgradeRecommendationWithPricing(currentTier: SubscriptionTier): SmartUpgradeRecommendationWithPricing? {
        val basicRecommendation = getUpgradeRecommendation(currentTier) ?: return null
        
        // Get current subscription info
        val subscriptionStartTime = subscriptionPrefs.getLong("subscription_start_time", 0)
        val subscriptionEndTime = subscriptionPrefs.getLong("expiration_time", 0)
        
        // Calculate upgrade credit for current subscription
        val upgradeCredit = prorationManager.calculateUpgradeCredit(
            currentTier = currentTier,
            currentSubscriptionStartTime = subscriptionStartTime,
            currentSubscriptionEndTime = subscriptionEndTime
        )
        
        // Calculate upgrade price with credit and discount
        val upgradePrice = prorationManager.calculateUpgradePrice(
            currentTier = currentTier,
            targetTier = basicRecommendation.recommendedTier,
            upgradeCredit = upgradeCredit,
            usageBasedDiscountPercent = basicRecommendation.discount
        )
        
        return SmartUpgradeRecommendationWithPricing(
            basicRecommendation = basicRecommendation,
            upgradeCredit = upgradeCredit,
            upgradePrice = upgradePrice,
            pricingMessage = prorationManager.getUpgradeRecommendationMessage(
                currentTier = currentTier,
                targetTier = basicRecommendation.recommendedTier,
                upgradeCredit = upgradeCredit,
                upgradePrice = upgradePrice,
                usageBasedDiscountPercent = basicRecommendation.discount
            )
        )
    }
    
    /**
     * Calculate current usage statistics
     */
    private fun calculateUsageStats(currentTier: SubscriptionTier): UsageStats {
        val allModels = AIModel.values()
        var totalUsed = 0
        var totalAvailable = 0
        var modelsHitLimit = 0
        var modelsNearLimit = 0
        
        for (model in allModels) {
            val currentUsage = usageTracker.getCurrentUsage(model.modelId)
            val usageLimit = model.getUsageLimitForTier(currentTier)
            
            if (usageLimit > 0) {  // Skip unlimited models
                totalUsed += currentUsage
                totalAvailable += usageLimit
                
                if (currentUsage >= usageLimit) {
                    modelsHitLimit++
                } else if (currentUsage >= (usageLimit * 0.8)) {
                    modelsNearLimit++
                }
            }
        }
        
        val usagePercentage = if (totalAvailable > 0) {
            (totalUsed.toFloat() / totalAvailable.toFloat() * 100).roundToInt()
        } else 0
        
        return UsageStats(
            totalUsed = totalUsed,
            totalAvailable = totalAvailable,
            usagePercentage = usagePercentage,
            modelsHitLimit = modelsHitLimit,
            modelsNearLimit = modelsNearLimit
        )
    }
    
    /**
     * Calculate benefits of upgrading to next tier
     */
    private fun calculateTierBenefits(currentTier: SubscriptionTier, nextTier: SubscriptionTier): TierBenefits {
        val allModels = AIModel.values()
        var additionalUsage = 0
        var newModelsUnlocked = 0
        var unlimitedModels = 0
        
        for (model in allModels) {
            val currentLimit = model.getUsageLimitForTier(currentTier)
            val nextLimit = model.getUsageLimitForTier(nextTier)
            
            if (currentLimit == 0 && nextLimit > 0) {
                newModelsUnlocked++
            }
            
            if (nextLimit == -1) {
                unlimitedModels++
            } else if (currentLimit > 0 && nextLimit > currentLimit) {
                additionalUsage += (nextLimit - currentLimit)
            }
        }
        
        return TierBenefits(
            additionalDailyUsage = additionalUsage,
            newModelsUnlocked = newModelsUnlocked,
            unlimitedModels = unlimitedModels
        )
    }
    
    /**
     * Calculate usage-based discount percentage
     */
    private fun calculateUsageBasedDiscount(usageStats: UsageStats): Int {
        return when {
            usageStats.modelsHitLimit >= 5 -> 25 // Heavy user - 25% discount
            usageStats.modelsHitLimit >= 3 -> 20 // Medium-heavy user - 20% discount
            usageStats.modelsNearLimit >= 3 -> 15 // Near limits - 15% discount
            usageStats.usagePercentage >= 80 -> 10 // High usage - 10% discount
            usageStats.usagePercentage >= 60 -> 5  // Medium usage - 5% discount
            else -> 0 // Low usage - no discount
        }
    }
    
    /**
     * Calculate upgrade urgency level
     */
    private fun calculateUpgradeUrgency(usageStats: UsageStats): SmartUpgradeUrgency {
        return when {
            usageStats.modelsHitLimit >= 3 -> SmartUpgradeUrgency.URGENT
            usageStats.modelsHitLimit >= 1 || usageStats.modelsNearLimit >= 3 -> SmartUpgradeUrgency.RECOMMENDED
            usageStats.usagePercentage >= 70 -> SmartUpgradeUrgency.SUGGESTED
            else -> SmartUpgradeUrgency.OPTIONAL
        }
    }
    
    /**
     * Get upgrade button text based on recommendation
     */
    fun getUpgradeButtonText(recommendation: SmartUpgradeRecommendation): String {
        return when (recommendation.urgency) {
            SmartUpgradeUrgency.URGENT -> "🚨 Upgrade Now - ${recommendation.discount}% OFF!"
            SmartUpgradeUrgency.RECOMMENDED -> "Recommended Upgrade - ${recommendation.discount}% OFF"
            SmartUpgradeUrgency.SUGGESTED -> "Upgrade to ${recommendation.recommendedTier.displayName}"
            SmartUpgradeUrgency.OPTIONAL -> "🔓 Unlock ${recommendation.recommendedTier.displayName}"
        }
    }
    
    /**
     * Get upgrade message based on recommendation
     */
    fun getUpgradeMessage(recommendation: SmartUpgradeRecommendation): String {
        val benefits = recommendation.benefits
        val stats = recommendation.usageStats
        
        return buildString {
            append("📊 Your Usage Summary:\n")
            append("• Used ${stats.totalUsed} of ${stats.totalAvailable} daily messages (${stats.usagePercentage}%)\n")
            append("• ${stats.modelsHitLimit} models at daily limit\n")
            append("• ${stats.modelsNearLimit} models near limit\n\n")
            
            append("🎯 ${recommendation.recommendedTier.displayName} Plan Benefits:\n")
            append("• +${benefits.additionalDailyUsage} additional daily messages\n")
            append("• ${benefits.newModelsUnlocked} new AI models unlocked\n")
            if (benefits.unlimitedModels > 0) {
                append("• ${benefits.unlimitedModels} models with unlimited usage\n")
            }
            
            if (recommendation.discount > 0) {
                append("\n🎉 Limited Time: ${recommendation.discount}% OFF!")
            }
        }
    }
}

/**
 * Data classes for upgrade recommendations
 */
data class SmartUpgradeRecommendation(
    val currentTier: SubscriptionTier,
    val recommendedTier: SubscriptionTier,
    val usageStats: UsageStats,
    val benefits: TierBenefits,
    val discount: Int,
    val urgency: SmartUpgradeUrgency
)

data class UsageStats(
    val totalUsed: Int,
    val totalAvailable: Int,
    val usagePercentage: Int,
    val modelsHitLimit: Int,
    val modelsNearLimit: Int
)

data class TierBenefits(
    val additionalDailyUsage: Int,
    val newModelsUnlocked: Int,
    val unlimitedModels: Int
)

enum class SmartUpgradeUrgency {
    URGENT,       // User has hit multiple limits
    RECOMMENDED,  // User is close to limits
    SUGGESTED,    // User has medium usage
    OPTIONAL      // User has low usage
}

/**
 * Extended upgrade recommendation with detailed pricing
 */
data class SmartUpgradeRecommendationWithPricing(
    val basicRecommendation: SmartUpgradeRecommendation,
    val upgradeCredit: UpgradeCredit,
    val upgradePrice: UpgradePrice,
    val pricingMessage: String
)