package com.playstudio.aiteacher.credits

import android.content.Context
import android.content.SharedPreferences
import com.playstudio.aiteacher.pricing.SubscriptionTier
import java.text.SimpleDateFormat
import java.util.*

/**
 * Manages user credit balances and rollover logic.
 */
class CreditManager private constructor(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    companion object {
        private const val PREFS = "credit_system"
        private const val KEY_PREFIX = "credits_"
        private const val KEY_LAST_UPDATE = "last_update_"
        private const val KEY_ROLLOVER = "rollover_"

        @Volatile
        private var INSTANCE: CreditManager? = null

        fun getInstance(context: Context): CreditManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: CreditManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    /** Get remaining credits for the user, processing rollover if needed */
    fun getRemainingCredits(userId: String, tier: SubscriptionTier): Double {
        processRollover(userId, tier)
        return prefs.getFloat(
            KEY_PREFIX + userId,
            SubscriptionTiers.getConfig(tier).dailyCredits.toFloat()
        ).toDouble()
    }

    /** Update user credits after a message */
    fun updateUserCredits(userId: String, tier: SubscriptionTier, creditsCost: Double) {
        processRollover(userId, tier)
        val current = getRemainingCredits(userId, tier)
        val newBalance = (current - creditsCost).coerceAtLeast(0.0)
        prefs.edit().putFloat(KEY_PREFIX + userId, newBalance.toFloat()).apply()
    }

    /** Calculate message cost using pricing table and tier markup */
    fun calculateMessageCost(
        inputTokens: Int,
        outputTokens: Int,
        modelName: String,
        tier: SubscriptionTier
    ): Double {
        val pricing = ModelPricing.getPricing(modelName) ?: return 0.0
        val config = SubscriptionTiers.getConfig(tier)
        val inputCost = (inputTokens / 1_000_000.0) * pricing.input
        val outputCost = (outputTokens / 1_000_000.0) * pricing.output
        return (inputCost + outputCost) * config.markupFactor
    }

    /**
     * Handle rollover credits at start of new day. This is automatically
     * invoked by other methods but can also be called manually.
     */
    fun processRollover(userId: String, tier: SubscriptionTier) {
        val today = currentDate()
        val lastUpdate = prefs.getString(KEY_LAST_UPDATE + userId, null)
        if (today == lastUpdate) return

        val config = SubscriptionTiers.getConfig(tier)
        val remaining = prefs.getFloat(KEY_PREFIX + userId, config.dailyCredits.toFloat())
        var rollover = prefs.getFloat(KEY_ROLLOVER + userId, 0f)

        // If last update was within rollover period, carry over unused credits
        if (lastUpdate != null) {
            rollover = (rollover + remaining).coerceAtMost(config.maxRollover.toFloat())
        }

        // Reset daily credits plus rollover (expire old rollover if days exceeded)
        prefs.edit().apply {
            putString(KEY_LAST_UPDATE + userId, today)
            putFloat(KEY_PREFIX + userId, config.dailyCredits.toFloat() + rollover)
            // Rollover expires after defined days
            putFloat(KEY_ROLLOVER + userId, rollover)
        }.apply()
    }

    private fun currentDate(): String = dateFormat.format(Date())
}
