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
            dailyCredits = 0.5,
            markupFactor = 3.0,
            rolloverDays = 1,
            maxRollover = 0.5
        ),
        SubscriptionTier.BASIC to TierConfig(
            dailyCredits = 2.0,
            markupFactor = 2.5,
            rolloverDays = 1,
            maxRollover = 1.0
        ),
        SubscriptionTier.PRO to TierConfig(
            dailyCredits = 5.0,
            markupFactor = 2.0,
            rolloverDays = 3,
            maxRollover = 7.5
        ),
        SubscriptionTier.PREMIUM to TierConfig(
            dailyCredits = 10.0,
            markupFactor = 1.75,
            rolloverDays = 5,
            maxRollover = 20.0
        ),
        SubscriptionTier.ULTRA_PREMIUM to TierConfig(
            dailyCredits = 15.0,
            markupFactor = 1.5,
            rolloverDays = 7,
            maxRollover = 45.0
        )
    )

    fun getConfig(tier: SubscriptionTier): TierConfig =
        tiers[tier] ?: tiers.getValue(SubscriptionTier.BASIC)
}
