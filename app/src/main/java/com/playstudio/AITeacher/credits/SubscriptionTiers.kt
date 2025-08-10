package com.playstudio.aiteacher.credits

/**
 * Basic subscription tier configuration for the credit system.
 */
import com.playstudio.aiteacher.pricing.SubscriptionTier

object SubscriptionTiers {
    data class TierConfig(
        val dailyCredits: Double,
        val markupFactor: Double,
        val rolloverDays: Int,
        val maxRollover: Double
    )

    private val tiers: Map<SubscriptionTier, TierConfig> = mapOf(
        SubscriptionTier.FREE to TierConfig(
            dailyCredits = 1000.0, // Updated to match TokenPoolManager
            markupFactor = 3.0,
            rolloverDays = 1,
            maxRollover = 500.0
        ),
        SubscriptionTier.BASIC to TierConfig(
            dailyCredits = 5000.0, // Updated to match TokenPoolManager
            markupFactor = 2.5,
            rolloverDays = 1,
            maxRollover = 2500.0
        ),
        SubscriptionTier.PREMIUM to TierConfig(
            dailyCredits = 20000.0, // Updated to match TokenPoolManager
            markupFactor = 2.0,
            rolloverDays = 3,
            maxRollover = 10000.0
        ),
        SubscriptionTier.PRO to TierConfig(
            dailyCredits = 100000.0, // Updated to match TokenPoolManager
            markupFactor = 1.8,
            rolloverDays = 5,
            maxRollover = 50000.0
        ),
        SubscriptionTier.ENTERPRISE to TierConfig(
            dailyCredits = 500000.0, // Updated to match TokenPoolManager
            markupFactor = 1.5,
            rolloverDays = 7,
            maxRollover = 250000.0
        )
    )

    fun getConfig(tier: SubscriptionTier): TierConfig =
        tiers[tier] ?: tiers.getValue(SubscriptionTier.BASIC)
}