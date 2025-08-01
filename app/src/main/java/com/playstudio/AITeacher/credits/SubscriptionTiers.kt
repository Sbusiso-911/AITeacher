package com.playstudio.aiteacher.credits

/**
 * Basic subscription tier configuration for the credit system.
 */
object SubscriptionTiers {
    data class TierConfig(
        val dailyCredits: Double,
        val markupFactor: Double,
        val rolloverDays: Int,
        val maxRollover: Double
    )

    val tiers = mapOf(
        "basic" to TierConfig(dailyCredits = 2.0, markupFactor = 2.5, rolloverDays = 1, maxRollover = 1.0),
        "professional" to TierConfig(dailyCredits = 5.0, markupFactor = 2.0, rolloverDays = 3, maxRollover = 7.5),
        "enterprise" to TierConfig(dailyCredits = 15.0, markupFactor = 1.5, rolloverDays = 7, maxRollover = 45.0)
    )

    fun getConfig(tier: String): TierConfig = tiers[tier] ?: tiers.getValue("basic")
}
