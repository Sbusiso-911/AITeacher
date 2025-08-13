package com.playstudio.aiteacher.profile

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import java.text.SimpleDateFormat
import java.util.*

/**
 * Manages daily token usage tracking and reset logic
 */
class DailyTokenTracker(private val context: Context) {
    
    companion object {
        private const val TAG = "DailyTokenTracker"
        private const val PREFS_NAME = "daily_token_tracker"
        private const val KEY_TOKENS_USED_TODAY = "tokens_used_today"
        private const val KEY_LAST_RESET_DATE = "last_reset_date"
        private const val DATE_FORMAT = "yyyy-MM-dd"
        
        // Daily limits based on subscription tier
        private const val FREE_TIER_DAILY_LIMIT = 1000
        private const val BASIC_TIER_DAILY_LIMIT = 5000
        private const val PRO_TIER_DAILY_LIMIT = 10000
        private const val PREMIUM_TIER_DAILY_LIMIT = 25000
        private const val ENTERPRISE_TIER_DAILY_LIMIT = 50000
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val dateFormat = SimpleDateFormat(DATE_FORMAT, Locale.getDefault())
    
    data class DailyUsage(
        val tokensUsedToday: Int,
        val dailyLimit: Int,
        val tokensRemaining: Int,
        val resetTimeHours: Int,
        val usagePercentage: Float
    ) {
        val isNearLimit: Boolean get() = usagePercentage >= 0.8f
        val isOverLimit: Boolean get() = tokensUsedToday >= dailyLimit
    }
    
    /**
     * Get current daily token usage for a subscription tier
     */
    fun getDailyUsage(subscriptionTier: String): DailyUsage {
        checkAndResetDaily()
        
        val tokensUsedToday = prefs.getInt(KEY_TOKENS_USED_TODAY, 0)
        val dailyLimit = getDailyLimit(subscriptionTier)
        val tokensRemaining = maxOf(0, dailyLimit - tokensUsedToday)
        val resetTimeHours = getHoursUntilReset()
        val usagePercentage = if (dailyLimit > 0) tokensUsedToday.toFloat() / dailyLimit else 0f
        
        return DailyUsage(
            tokensUsedToday = tokensUsedToday,
            dailyLimit = dailyLimit,
            tokensRemaining = tokensRemaining,
            resetTimeHours = resetTimeHours,
            usagePercentage = usagePercentage
        )
    }
    
    /**
     * Add tokens to today's usage
     */
    fun addTokenUsage(tokens: Int) {
        checkAndResetDaily()
        
        val currentUsage = prefs.getInt(KEY_TOKENS_USED_TODAY, 0)
        val newUsage = currentUsage + tokens
        
        prefs.edit().putInt(KEY_TOKENS_USED_TODAY, newUsage).apply()
        
        Log.d(TAG, "Added $tokens tokens to daily usage. Total: $newUsage")
    }
    
    /**
     * Check if user can use specified number of tokens
     */
    fun canUseTokens(tokens: Int, subscriptionTier: String): Boolean {
        val usage = getDailyUsage(subscriptionTier)
        return usage.tokensRemaining >= tokens
    }
    
    /**
     * Get daily token limit based on subscription tier
     */
    private fun getDailyLimit(subscriptionTier: String): Int {
        return when (subscriptionTier.lowercase()) {
            "free" -> FREE_TIER_DAILY_LIMIT
            "basic", "essential" -> BASIC_TIER_DAILY_LIMIT
            "pro", "professional" -> PRO_TIER_DAILY_LIMIT
            "premium" -> PREMIUM_TIER_DAILY_LIMIT
            "enterprise", "ultra_premium" -> ENTERPRISE_TIER_DAILY_LIMIT
            else -> FREE_TIER_DAILY_LIMIT
        }
    }
    
    /**
     * Check if we need to reset daily counters
     */
    private fun checkAndResetDaily() {
        val today = dateFormat.format(Date())
        val lastResetDate = prefs.getString(KEY_LAST_RESET_DATE, "")
        
        if (today != lastResetDate) {
            // Reset daily counters
            prefs.edit()
                .putInt(KEY_TOKENS_USED_TODAY, 0)
                .putString(KEY_LAST_RESET_DATE, today)
                .apply()
            
            Log.d(TAG, "Daily token usage reset for date: $today")
        }
    }
    
    /**
     * Get hours until daily reset (midnight)
     */
    private fun getHoursUntilReset(): Int {
        val calendar = Calendar.getInstance()
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
        return 24 - currentHour
    }
    
    /**
     * Format daily usage for display
     */
    fun formatDailyUsage(usage: DailyUsage): String {
        return "${formatNumber(usage.tokensRemaining)} / ${formatNumber(usage.dailyLimit)}"
    }
    
    /**
     * Format usage percentage for display
     */
    fun formatUsagePercentage(usage: DailyUsage): Int {
        return (usage.usagePercentage * 100).toInt()
    }
    
    /**
     * Get user-friendly reset time text
     */
    fun getResetTimeText(usage: DailyUsage): String {
        return when {
            usage.resetTimeHours <= 1 -> "Resets in less than 1 hour"
            usage.resetTimeHours < 24 -> "Resets in ${usage.resetTimeHours} hours"
            else -> "Resets at midnight"
        }
    }
    
    /**
     * Get subscription tier display name
     */
    fun getTierDisplayName(subscriptionTier: String): String {
        return when (subscriptionTier.lowercase()) {
            "free" -> "FREE"
            "basic", "essential" -> "BASIC"
            "pro", "professional" -> "PRO"
            "premium" -> "PREMIUM"
            "enterprise", "ultra_premium" -> "ENTERPRISE"
            else -> subscriptionTier.uppercase()
        }
    }
    
    /**
     * Format large numbers for display (e.g., 1000 -> 1K)
     */
    private fun formatNumber(number: Int): String {
        return when {
            number >= 1000000 -> String.format("%.1fM", number / 1000000.0)
            number >= 1000 -> String.format("%.1fK", number / 1000.0)
            else -> number.toString()
        }
    }
    
    /**
     * Get daily usage statistics for analytics
     */
    fun getDailyStats(): Map<String, Any> {
        checkAndResetDaily()
        
        return mapOf(
            "tokensUsedToday" to prefs.getInt(KEY_TOKENS_USED_TODAY, 0),
            "lastResetDate" to (prefs.getString(KEY_LAST_RESET_DATE, "") ?: ""),
            "resetTimeHours" to getHoursUntilReset()
        )
    }
    
    /**
     * Reset daily usage (for testing or manual reset)
     */
    fun resetDailyUsage() {
        val today = dateFormat.format(Date())
        prefs.edit()
            .putInt(KEY_TOKENS_USED_TODAY, 0)
            .putString(KEY_LAST_RESET_DATE, today)
            .apply()
        
        Log.d(TAG, "Manual daily token usage reset")
    }
}