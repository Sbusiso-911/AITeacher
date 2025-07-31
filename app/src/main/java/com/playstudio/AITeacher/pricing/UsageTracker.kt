package com.playstudio.aiteacher.pricing

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.*

/**
 * Tracks daily usage of AI models per user
 */
class UsageTracker(private val context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences("model_usage_prefs", Context.MODE_PRIVATE)
    
    companion object {
        private const val USAGE_PREFIX = "usage_"
        private const val LAST_RESET_KEY = "last_reset_date"
        private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    }
    
    /**
     * Get current usage count for a model
     */
    fun getCurrentUsage(modelId: String): Int {
        checkAndResetDailyUsage()
        val usage = prefs.getInt(getUsageKey(modelId), 0)
        android.util.Log.d("UsageTracker", "getCurrentUsage: modelId=$modelId, usage=$usage, key=${getUsageKey(modelId)}")
        return usage
    }
    
    /**
     * Increment usage count for a model
     */
    fun incrementUsage(modelId: String) {
        checkAndResetDailyUsage()
        val currentUsage = getCurrentUsage(modelId)
        prefs.edit().putInt(getUsageKey(modelId), currentUsage + 1).apply()
    }
    
    /**
     * Check if user can use a model (has remaining usage)
     */
    fun canUseModel(modelId: String, userTier: SubscriptionTier): Boolean {
        val model = AIModel.fromModelId(modelId) ?: return false
        val usageLimit = model.getUsageLimitForTier(userTier)
        
        // -1 means unlimited
        if (usageLimit == -1) return true
        
        val currentUsage = getCurrentUsage(modelId)
        val canUse = currentUsage < usageLimit
        
        android.util.Log.d("UsageTracker", "canUseModel: modelId=$modelId, userTier=$userTier")
        android.util.Log.d("UsageTracker", "canUseModel: usageLimit=$usageLimit, currentUsage=$currentUsage, canUse=$canUse")
        
        return canUse
    }
    
    /**
     * Get remaining usage for a model
     */
    fun getRemainingUsage(modelId: String, userTier: SubscriptionTier): Int {
        val model = AIModel.fromModelId(modelId) ?: return 0
        val usageLimit = model.getUsageLimitForTier(userTier)
        
        // -1 means unlimited
        if (usageLimit == -1) return Int.MAX_VALUE
        
        val currentUsage = getCurrentUsage(modelId)
        return maxOf(0, usageLimit - currentUsage)
    }
    
    /**
     * Get usage summary for all models
     */
    fun getUsageSummary(userTier: SubscriptionTier): Map<String, UsageInfo> {
        checkAndResetDailyUsage()
        val summary = mutableMapOf<String, UsageInfo>()
        
        AIModel.getAllModels().forEach { model ->
            val currentUsage = getCurrentUsage(model.modelId)
            val usageLimit = model.getUsageLimitForTier(userTier)
            val remainingUsage = if (usageLimit == -1) Int.MAX_VALUE else maxOf(0, usageLimit - currentUsage)
            
            summary[model.modelId] = UsageInfo(
                modelName = model.displayName,
                currentUsage = currentUsage,
                usageLimit = usageLimit,
                remainingUsage = remainingUsage,
                canUse = canUseModel(model.modelId, userTier)
            )
        }
        
        return summary
    }
    
    /**
     * Reset usage for a specific model (admin function)
     */
    fun resetModelUsage(modelId: String) {
        prefs.edit().remove(getUsageKey(modelId)).apply()
    }
    
    /**
     * Reset all usage (admin function)
     */
    fun resetAllUsage() {
        val editor = prefs.edit()
        AIModel.getAllModels().forEach { model ->
            editor.remove(getUsageKey(model.modelId))
        }
        editor.putString(LAST_RESET_KEY, getCurrentDate())
        editor.apply()
    }
    
    /**
     * Check if we need to reset daily usage (call this before any usage check)
     */
    private fun checkAndResetDailyUsage() {
        val currentDate = getCurrentDate()
        val lastResetDate = prefs.getString(LAST_RESET_KEY, "")
        
        if (currentDate != lastResetDate) {
            // New day - reset all usage
            resetAllUsage()
        }
    }
    
    private fun getUsageKey(modelId: String): String {
        return USAGE_PREFIX + modelId
    }
    
    private fun getCurrentDate(): String {
        return dateFormat.format(Date())
    }
    
    /**
     * Data class for usage information
     */
    data class UsageInfo(
        val modelName: String,
        val currentUsage: Int,
        val usageLimit: Int, // -1 means unlimited
        val remainingUsage: Int, // Int.MAX_VALUE means unlimited
        val canUse: Boolean
    )
}