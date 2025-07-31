package com.playstudio.aiteacher.pricing

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.HashMap

/**
 * Persistent storage for API cost tracking
 * Uses SharedPreferences for immediate deployment, can be upgraded to Room database later
 */
class CostStorage(private val context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    
    companion object {
        private const val PREFS_NAME = "cost_tracking"
        private const val TAG = "CostStorage"
        
        // Keys
        private const val KEY_DAILY_USAGE = "daily_usage_"
        private const val KEY_API_CALLS = "api_calls_"
        private const val KEY_USER_TIER = "user_tier_"
        private const val KEY_MONTHLY_COSTS = "monthly_costs_"
        private const val KEY_LAST_CLEANUP = "last_cleanup"
        
        // Cleanup intervals
        private const val CLEANUP_INTERVAL_DAYS = 7
        private const val KEEP_RECORDS_DAYS = 30
    }
    
    /**
     * Record an API call with detailed cost information
     */
    suspend fun recordAPICall(record: APICallRecord) = withContext(Dispatchers.IO) {
        try {
            // Update daily usage summary
            updateDailyUsage(record)
            
            // Store individual API call record
            storeAPICallRecord(record)
            
            Log.d(TAG, "Recorded API call: user=${record.userId}, cost=${record.cost}, model=${record.model.displayName}")
        } catch (e: Exception) {
            Log.e(TAG, "Error recording API call", e)
        }
    }
    
    /**
     * Get daily usage summary for a user
     */
    suspend fun getDailyUsage(userId: String, date: String): DailyUsage = withContext(Dispatchers.IO) {
        try {
            val key = KEY_DAILY_USAGE + userId + "_" + date
            val json = prefs.getString(key, null)
            
            return@withContext if (json != null) {
                val usage = gson.fromJson(json, DailyUsage::class.java)
                // Ensure modelUsageCount exists (for backward compatibility)
                if (usage.modelUsageCount.isEmpty() && usage.costByModel.isNotEmpty()) {
                    // Migrate old data by creating usage count from cost data
                    val modelUsageCount = usage.costByModel.keys.associateWith { 1 }
                    usage.copy(modelUsageCount = modelUsageCount)
                } else {
                    usage
                }
            } else {
                // Return empty usage
                DailyUsage(
                    userId = userId,
                    date = date,
                    totalCost = 0.0,
                    costByModel = emptyMap(),
                    costByFeature = emptyMap(),
                    messageCount = 0,
                    tokenCount = 0,
                    imageCount = 0,
                    webSearchCount = 0,
                    modelUsageCount = emptyMap()
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting daily usage for user $userId", e)
            return@withContext DailyUsage(userId, date, 0.0, emptyMap(), emptyMap(), 0, 0, 0, 0, emptyMap())
        }
    }
    
    /**
     * Update daily usage with new API call
     */
    private fun updateDailyUsage(record: APICallRecord) {
        val key = KEY_DAILY_USAGE + record.userId + "_" + record.date
        val existingJson = prefs.getString(key, null)
        
        val currentUsage = if (existingJson != null) {
            val usage = gson.fromJson(existingJson, DailyUsage::class.java)
            // Ensure modelUsageCount exists (for backward compatibility)
            if (usage.modelUsageCount.isEmpty() && usage.costByModel.isNotEmpty()) {
                // Migrate old data by creating usage count from cost data
                val modelUsageCount = usage.costByModel.keys.associateWith { 1 }
                usage.copy(modelUsageCount = modelUsageCount)
            } else {
                usage
            }
        } else {
            DailyUsage(
                userId = record.userId,
                date = record.date,
                totalCost = 0.0,
                costByModel = emptyMap(),
                costByFeature = emptyMap(),
                messageCount = 0,
                tokenCount = 0,
                imageCount = 0,
                webSearchCount = 0,
                modelUsageCount = emptyMap()
            )
        }
        
        // Update totals
        val updatedCostByModel = currentUsage.costByModel.toMutableMap()
        updatedCostByModel[record.model] = (updatedCostByModel[record.model] ?: 0.0) + record.cost
        
        val updatedCostByFeature = currentUsage.costByFeature.toMutableMap()
        record.features.forEach { feature ->
            updatedCostByFeature[feature] = (updatedCostByFeature[feature] ?: 0.0) + feature.cost
        }
        
        // CRITICAL: Update model usage count for daily limits
        val updatedModelUsageCount = currentUsage.modelUsageCount.toMutableMap()
        updatedModelUsageCount[record.model] = (updatedModelUsageCount[record.model] ?: 0) + 1
        
        val updatedUsage = currentUsage.copy(
            totalCost = currentUsage.totalCost + record.cost,
            costByModel = updatedCostByModel,
            costByFeature = updatedCostByFeature,
            messageCount = currentUsage.messageCount + 1,
            tokenCount = currentUsage.tokenCount + record.inputTokens + record.outputTokens,
            imageCount = currentUsage.imageCount + record.features.count { 
                it in listOf(APIFeature.IMAGE_GEN_LOW, APIFeature.IMAGE_GEN_MEDIUM, APIFeature.IMAGE_GEN_HIGH) 
            },
            webSearchCount = currentUsage.webSearchCount + record.features.count { 
                it in listOf(APIFeature.WEB_SEARCH_1K, APIFeature.WEB_SEARCH_1K_REASONING) 
            },
            modelUsageCount = updatedModelUsageCount // CRITICAL: Include model usage count
        )
        
        // Save updated usage
        prefs.edit().putString(key, gson.toJson(updatedUsage)).apply()
    }
    
    /**
     * Store individual API call record for detailed analytics
     */
    private fun storeAPICallRecord(record: APICallRecord) {
        val key = KEY_API_CALLS + record.userId + "_" + record.date
        val existingJson = prefs.getString(key, "[]")
        
        val type = object : TypeToken<MutableList<APICallRecord>>() {}.type
        val records: MutableList<APICallRecord> = gson.fromJson(existingJson, type) ?: mutableListOf()
        
        records.add(record)
        
        // Keep only last 100 records per day to prevent storage bloat
        if (records.size > 100) {
            records.removeAt(0)
        }
        
        prefs.edit().putString(key, gson.toJson(records)).apply()
    }
    
    /**
     * Get user's subscription tier
     */
    fun getUserSubscriptionTier(userId: String): SubscriptionTier? {
        val tierName = prefs.getString(KEY_USER_TIER + userId, null)
        return tierName?.let { SubscriptionTier.valueOf(it) }
    }
    
    /**
     * Set user's subscription tier
     */
    fun setUserSubscriptionTier(userId: String, tier: SubscriptionTier) {
        prefs.edit().putString(KEY_USER_TIER + userId, tier.name).apply()
        Log.d(TAG, "Updated subscription tier for user $userId to $tier")
    }
    
    /**
     * Get cost analytics for date range
     */
    suspend fun getCostAnalytics(userId: String, startDate: String, endDate: String): CostAnalytics = withContext(Dispatchers.IO) {
        try {
            val dailyCosts = mutableListOf<Pair<String, Double>>()
            val modelUsage = mutableMapOf<AIModel, Int>()
            val featureUsage = mutableMapOf<APIFeature, Int>()
            var totalCost = 0.0
            
            val calendar = Calendar.getInstance()
            val start = dateFormat.parse(startDate)
            val end = dateFormat.parse(endDate)
            
            calendar.time = start
            while (calendar.time <= end) {
                val currentDate = dateFormat.format(calendar.time)
                val dailyUsage = getDailyUsage(userId, currentDate)
                
                dailyCosts.add(Pair(currentDate, dailyUsage.totalCost))
                totalCost += dailyUsage.totalCost
                
                // Aggregate model usage
                dailyUsage.costByModel.forEach { (model, cost) ->
                    modelUsage[model] = (modelUsage[model] ?: 0) + 1
                }
                
                // Aggregate feature usage
                dailyUsage.costByFeature.forEach { (feature, cost) ->
                    featureUsage[feature] = (featureUsage[feature] ?: 0) + 1
                }
                
                calendar.add(Calendar.DAY_OF_MONTH, 1)
            }
            
            val dayCount = dailyCosts.size
            val averageDailyCost = if (dayCount > 0) totalCost / dayCount else 0.0
            val peakDailyCost = dailyCosts.maxOfOrNull { it.second } ?: 0.0
            
            return@withContext CostAnalytics(
                userId = userId,
                startDate = startDate,
                endDate = endDate,
                totalCost = totalCost,
                averageDailyCost = averageDailyCost,
                peakDailyCost = peakDailyCost,
                costTrend = dailyCosts,
                modelUsage = modelUsage,
                featureUsage = featureUsage
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error getting cost analytics for user $userId", e)
            return@withContext CostAnalytics(userId, startDate, endDate, 0.0, 0.0, 0.0, emptyList(), emptyMap(), emptyMap())
        }
    }
    
    /**
     * Cleanup old records to prevent storage bloat
     */
    suspend fun cleanupOldRecords() = withContext(Dispatchers.IO) {
        try {
            val lastCleanup = prefs.getLong(KEY_LAST_CLEANUP, 0)
            val now = System.currentTimeMillis()
            val cleanupInterval = CLEANUP_INTERVAL_DAYS * 24 * 60 * 60 * 1000L
            
            if (now - lastCleanup < cleanupInterval) {
                return@withContext // Too soon for cleanup
            }
            
            val cutoffDate = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_MONTH, -KEEP_RECORDS_DAYS)
            }.time
            
            val cutoffDateStr = dateFormat.format(cutoffDate)
            val editor = prefs.edit()
            
            // Find and remove old keys
            val allKeys = prefs.all.keys
            var removedCount = 0
            
            allKeys.forEach { key ->
                when {
                    key.startsWith(KEY_DAILY_USAGE) -> {
                        val dateStr = extractDateFromKey(key)
                        if (dateStr != null && dateStr < cutoffDateStr) {
                            editor.remove(key)
                            removedCount++
                        }
                    }
                    key.startsWith(KEY_API_CALLS) -> {
                        val dateStr = extractDateFromKey(key)
                        if (dateStr != null && dateStr < cutoffDateStr) {
                            editor.remove(key)
                            removedCount++
                        }
                    }
                }
            }
            
            editor.putLong(KEY_LAST_CLEANUP, now)
            editor.apply()
            
            Log.d(TAG, "Cleanup completed: removed $removedCount old records")
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup", e)
        }
    }
    
    /**
     * Extract date from storage key
     */
    private fun extractDateFromKey(key: String): String? {
        return try {
            // Keys are in format: "prefix_userId_yyyy-MM-dd"
            val parts = key.split("_")
            if (parts.size >= 3) {
                parts.last()
            } else null
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Get all users with cost data (for admin analytics)
     */
    suspend fun getAllUsersWithCostData(): List<String> = withContext(Dispatchers.IO) {
        val users = mutableSetOf<String>()
        val allKeys = prefs.all.keys
        
        allKeys.forEach { key ->
            when {
                key.startsWith(KEY_DAILY_USAGE) -> {
                    val parts = key.removePrefix(KEY_DAILY_USAGE).split("_")
                    if (parts.size >= 2) {
                        users.add(parts[0])
                    }
                }
            }
        }
        
        return@withContext users.toList()
    }
    
    /**
     * Export cost data for a user (for GDPR compliance or data portability)
     */
    suspend fun exportUserData(userId: String): Map<String, Any> = withContext(Dispatchers.IO) {
        val exportData = mutableMapOf<String, Any>()
        
        try {
            // Get all keys for this user
            val allKeys = prefs.all.keys
            val userKeys = allKeys.filter { key ->
                key.contains("_$userId" + "_") || key.endsWith("_$userId")
            }
            
            userKeys.forEach { key ->
                val value = prefs.all[key]
                if (value != null) {
                    exportData[key] = value
                }
            }
            
            Log.d(TAG, "Exported ${exportData.size} data entries for user $userId")
        } catch (e: Exception) {
            Log.e(TAG, "Error exporting data for user $userId", e)
        }
        
        return@withContext exportData
    }
    
    /**
     * Delete all data for a user (for account deletion)
     */
    suspend fun deleteUserData(userId: String) = withContext(Dispatchers.IO) {
        try {
            val editor = prefs.edit()
            val allKeys = prefs.all.keys
            var deletedCount = 0
            
            // Find and remove all keys containing this user ID
            allKeys.forEach { key ->
                if (key.contains("_$userId" + "_") || key.endsWith("_$userId")) {
                    editor.remove(key)
                    deletedCount++
                }
            }
            
            editor.apply()
            Log.d(TAG, "Deleted $deletedCount data entries for user $userId")
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting data for user $userId", e)
        }
    }
}

/**
 * Record of a single API call with cost information
 */
data class APICallRecord(
    val userId: String,
    val date: String,
    val timestamp: Long,
    val model: AIModel,
    val inputTokens: Int,
    val outputTokens: Int,
    val cost: Double,
    val features: List<APIFeature>
)