package com.playstudio.aiteacher.credits

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Centralized token pool manager that tracks usage across all AI models and response types.
 * Manages token deduction based on model used, response type, token pricing, and response length.
 */
class TokenPoolManager private constructor(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    private val scope = CoroutineScope(Dispatchers.IO)

    companion object {
        private const val TAG = "TokenPoolManager"
        private const val PREFS_NAME = "token_pool"
        private const val KEY_TOTAL_TOKENS = "total_tokens"
        private const val KEY_USED_TOKENS = "used_tokens"
        private const val KEY_LAST_RESET = "last_reset"
        private const val TOKEN_POOL_FILE = "token_pool_log.json"
        
        // Default token pool size (can be configured)
        private const val DEFAULT_TOTAL_TOKENS = 1000000.0 // 1M tokens
        
        @Volatile
        private var INSTANCE: TokenPoolManager? = null

        fun getInstance(context: Context): TokenPoolManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: TokenPoolManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    // Response type multipliers for different types of responses
    enum class ResponseType(val multiplier: Double) {
        TEXT(1.0),
        IMAGE(2.5),
        LIVE_VOICE(3.0),
        AUDIO(2.0),
        REALTIME(4.0),
        SEARCH(1.5),
        CODE_INTERPRETER(1.8)
    }

    data class TokenUsage(
        val modelName: String,
        val responseType: ResponseType,
        val inputTokens: Int,
        val outputTokens: Int,
        val totalCost: Double,
        val timestamp: String,
        val agentName: String? = null,
        val responseLength: Int = 0
    )

    /**
     * Initialize the token pool with default values if not exists
     */
    init {
        if (!prefs.contains(KEY_TOTAL_TOKENS)) {
            prefs.edit()
                .putFloat(KEY_TOTAL_TOKENS, DEFAULT_TOTAL_TOKENS.toFloat())
                .putFloat(KEY_USED_TOKENS, 0f)
                .putString(KEY_LAST_RESET, currentTimestamp())
                .apply()
        }
    }

    /**
     * Get total tokens in pool
     */
    fun getTotalTokens(): Double = prefs.getFloat(KEY_TOTAL_TOKENS, DEFAULT_TOTAL_TOKENS.toFloat()).toDouble()

    /**
     * Get used tokens
     */
    fun getUsedTokens(): Double = prefs.getFloat(KEY_USED_TOKENS, 0f).toDouble()

    /**
     * Get remaining tokens
     */
    fun getRemainingTokens(): Double = getTotalTokens() - getUsedTokens()

    /**
     * Get usage percentage (0.0 to 1.0)
     */
    fun getUsagePercentage(): Double {
        val total = getTotalTokens()
        val used = getUsedTokens()
        return if (total > 0) used / total else 0.0
    }

    /**
     * Check if there are sufficient tokens for a request
     */
    fun canAffordTokens(estimatedTokens: Double): Boolean {
        return getRemainingTokens() >= estimatedTokens
    }

    /**
     * All models available to all tiers - restriction is through token deduction cost
     */
    fun canUseModel(modelName: String, userTier: SubscriptionTier): Boolean {
        return true // All models available, cost varies by model strength and user tier markup
    }

    /**
     * Get user's daily token usage from preferences
     */
    private fun getDailyUsage(userId: String): Pair<Double, String> {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val lastUsageDate = prefs.getString("daily_usage_date_$userId", "")
        val dailyUsage = if (lastUsageDate == today) {
            prefs.getFloat("daily_usage_$userId", 0f).toDouble()
        } else {
            0.0 // Reset for new day
        }
        return Pair(dailyUsage, today)
    }

    /**
     * Update user's daily usage
     */
    private fun updateDailyUsage(userId: String, additionalUsage: Double, today: String) {
        val (currentUsage, _) = getDailyUsage(userId)
        val newUsage = currentUsage + additionalUsage
        
        prefs.edit()
            .putFloat("daily_usage_$userId", newUsage.toFloat())
            .putString("daily_usage_date_$userId", today)
            .apply()
    }

    /**
     * Check if user has exceeded daily limit
     */
    fun hasExceededDailyLimit(userId: String, userTier: SubscriptionTier, additionalUsage: Double = 0.0): Boolean {
        val (dailyUsage, _) = getDailyUsage(userId)
        return (dailyUsage + additionalUsage) > userTier.tokenAllocation
    }

    /**
     * Get remaining daily tokens for user
     */
    fun getRemainingDailyTokens(userId: String, userTier: SubscriptionTier): Double {
        val (dailyUsage, _) = getDailyUsage(userId)
        return maxOf(0.0, userTier.tokenAllocation - dailyUsage)
    }

    /**
     * Main method to deduct tokens from the pool with daily limits and tier restrictions
     */
    suspend fun deductTokens(
        modelName: String,
        responseType: ResponseType,
        inputTokens: Int,
        outputTokens: Int,
        responseLength: Int = 0,
        agentName: String? = null,
        userId: String = "default_user",
        userTier: SubscriptionTier = SubscriptionTier.FREE
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            // Check if user can use this model based on their tier
            if (!canUseModel(modelName, userTier)) {
                Log.w(TAG, "Model $modelName not available for tier ${userTier.name}")
                return@withContext false
            }

            val totalCost = calculateTokenCost(
                modelName = modelName,
                responseType = responseType,
                inputTokens = inputTokens,
                outputTokens = outputTokens,
                responseLength = responseLength,
                userTier = userTier
            )

            // Check daily limit first
            if (hasExceededDailyLimit(userId, userTier, totalCost)) {
                Log.w(TAG, "Daily limit exceeded for user $userId. Required: $totalCost, Remaining: ${getRemainingDailyTokens(userId, userTier)}")
                return@withContext false
            }

            // Check global pool
            if (!canAffordTokens(totalCost)) {
                Log.w(TAG, "Insufficient tokens in global pool. Required: $totalCost, Available: ${getRemainingTokens()}")
                return@withContext false
            }

            // Deduct from global pool
            val currentUsed = getUsedTokens()
            val newUsed = currentUsed + totalCost

            prefs.edit()
                .putFloat(KEY_USED_TOKENS, newUsed.toFloat())
                .apply()

            // Update user's daily usage
            val (_, today) = getDailyUsage(userId)
            updateDailyUsage(userId, totalCost, today)

            // Log usage
            val usage = TokenUsage(
                modelName = modelName,
                responseType = responseType,
                inputTokens = inputTokens,
                outputTokens = outputTokens,
                totalCost = totalCost,
                timestamp = currentTimestamp(),
                agentName = agentName,
                responseLength = responseLength
            )

            logUsage(usage)
            checkQuotaAlerts()

            Log.d(TAG, "Tokens deducted: $totalCost. Remaining: ${getRemainingTokens()}")
            return@withContext true

        } catch (e: Exception) {
            Log.e(TAG, "Error deducting tokens", e)
            return@withContext false
        }
    }

    /**
     * Calculate token cost with business markup and operational costs
     */
    fun calculateTokenCost(
        modelName: String,
        responseType: ResponseType,
        inputTokens: Int,
        outputTokens: Int,
        responseLength: Int = 0,
        userTier: SubscriptionTier = SubscriptionTier.FREE
    ): Double {
        // Get base cost from provider's pricing
        val providerCost = calculateBaseCost(modelName, inputTokens, outputTokens)
        
        // Apply response type multiplier
        val responseMultiplier = responseType.multiplier
        
        // Apply response length factor
        val lengthMultiplier = calculateLengthMultiplier(responseLength)
        
        // Apply business markup (includes profit, Google billing, developer costs, services)
        val businessMarkup = userTier.businessMarkup
        
        return providerCost * responseMultiplier * lengthMultiplier * businessMarkup
    }

    /**
     * Calculate base cost using OpenAI pricing table
     */
    private fun calculateBaseCost(modelName: String, inputTokens: Int, outputTokens: Int): Double {
        val pricing = getModelPricing(modelName)
        val inputCost = (inputTokens / 1_000_000.0) * pricing.input
        val outputCost = (outputTokens / 1_000_000.0) * pricing.output
        return inputCost + outputCost
    }

    // Simple tier-based token allocation system
    enum class SubscriptionTier(
        val tierLevel: Int,
        val tokenAllocation: Double, // Base tokens allocated per day
        val businessMarkup: Double, // Markup for operational costs
        val description: String
    ) {
        FREE(1, 1000.0, 3.0, "Tier 1 - 1K tokens/day"),
        BASIC(2, 2000.0, 2.5, "Tier 2 - 2K tokens/day"),
        PREMIUM(4, 4000.0, 2.0, "Tier 3 - 4K tokens/day"),
        PRO(8, 8000.0, 1.8, "Tier 4 - 8K tokens/day"),
        ENTERPRISE(16, 16000.0, 1.5, "Tier 5 - 16K tokens/day")
    }

    // Model capability tiers based on power and accuracy (provider's actual strength)
    enum class ModelTier(val strengthMultiplier: Double, val description: String) {
        NANO(0.1, "Basic models - simple tasks (cheapest)"),
        MINI(0.3, "Lightweight models - general tasks"),
        STANDARD(1.0, "Standard models - balanced performance"),
        ADVANCED(3.0, "Advanced models - complex reasoning"),
        PRO(8.0, "Pro models - expert-level tasks"),
        RESEARCH(15.0, "Research models - deep analysis"),
        FLAGSHIP(25.0, "Flagship models - maximum capability (most expensive)")
    }

    /**
     * Get model tier based on capabilities and performance
     */
    private fun getModelTier(modelName: String): ModelTier {
        return when {
            // Flagship/Most Powerful Models - Highest accuracy and reasoning
            modelName.contains("gpt-4.5-preview") -> ModelTier.FLAGSHIP
            modelName.contains("o1-pro") -> ModelTier.FLAGSHIP
            
            // Research/Deep Analysis Models
            modelName.contains("o3-deep-research") -> ModelTier.RESEARCH
            modelName.contains("o4-mini-deep-research") -> ModelTier.RESEARCH
            
            // Pro Models - Expert level reasoning
            modelName.contains("o3-pro") -> ModelTier.PRO
            modelName.contains("claude-opus-4.1") -> ModelTier.PRO
            modelName.contains("claude-opus-4") -> ModelTier.PRO
            
            // Advanced Models - Complex reasoning and high accuracy
            modelName.contains("o1-2024-12-17") -> ModelTier.ADVANCED
            modelName.contains("o3-2025-04-16") -> ModelTier.ADVANCED
            modelName.contains("gpt-4-turbo") -> ModelTier.ADVANCED
            modelName.contains("gpt-4") && !modelName.contains("gpt-4o") -> ModelTier.ADVANCED
            modelName.contains("computer-use-preview") -> ModelTier.ADVANCED
            
            // Standard Models - Good balance of performance
            modelName.contains("gpt-4.1-2025-04-14") -> ModelTier.STANDARD
            modelName.contains("gpt-4o-2024-08-06") -> ModelTier.STANDARD
            modelName.contains("gpt-4o-audio-preview") -> ModelTier.STANDARD
            modelName.contains("gpt-4o-realtime-preview") -> ModelTier.STANDARD
            modelName.contains("gpt-4o-search-preview") -> ModelTier.STANDARD
            modelName.contains("claude-sonnet-4") -> ModelTier.STANDARD
            modelName.contains("grok-4-0709") -> ModelTier.STANDARD
            modelName.contains("codex-mini-latest") -> ModelTier.STANDARD
            modelName.contains("gpt-image-1") -> ModelTier.STANDARD
            
            // Mini Models - Lightweight but capable
            modelName.contains("gpt-4.1-mini") -> ModelTier.MINI
            modelName.contains("gpt-4o-mini") -> ModelTier.MINI
            modelName.contains("o1-mini") -> ModelTier.MINI
            modelName.contains("o3-mini") -> ModelTier.MINI
            modelName.contains("o4-mini") && !modelName.contains("deep-research") -> ModelTier.MINI
            modelName.contains("gpt-3.5-turbo") -> ModelTier.MINI
            
            // Nano Models - Most basic
            modelName.contains("gpt-4.1-nano") -> ModelTier.NANO
            
            // Default to standard for unknown models
            else -> ModelTier.STANDARD
        }
    }

    /**
     * Get model pricing - Updated with actual OpenAI pricing reflecting model power and accuracy
     */
    private fun getModelPricing(modelName: String): ModelPricing.Pricing {
        // Prefer shared pricing table from ModelPricing/AIModel if available
        ModelPricing.getPricing(modelName)?.let { pricing ->
            val tier = getModelTier(modelName)
            return ModelPricing.Pricing(
                input = pricing.input * tier.strengthMultiplier,
                output = pricing.output * tier.strengthMultiplier
            )
        }

        // Fallback: map from known model prefixes (kept for robustness if model isn't in AIModel)
        val basePricing = when {
            // GPT-4.1 series
            modelName.contains("gpt-4.1-2025-04-14") -> ModelPricing.Pricing(2.00, 8.00)
            modelName.contains("gpt-4.1-mini-2025-04-14") -> ModelPricing.Pricing(0.40, 1.60)
            modelName.contains("gpt-4.1-nano-2025-04-14") -> ModelPricing.Pricing(0.10, 0.40)
            
            // GPT-4.5 series - Most expensive due to superior capabilities
            modelName.contains("gpt-4.5-preview-2025-02-27") -> ModelPricing.Pricing(75.00, 150.00)
            
            // GPT-4o series
            modelName.contains("gpt-4o-2024-08-06") -> ModelPricing.Pricing(2.50, 10.00)
            modelName.contains("gpt-4o-audio-preview") -> ModelPricing.Pricing(2.50, 10.00)
            modelName.contains("gpt-4o-realtime-preview") -> ModelPricing.Pricing(5.00, 20.00)
            modelName.contains("gpt-4o-mini-2024-07-18") -> ModelPricing.Pricing(0.15, 0.60)
            modelName.contains("gpt-4o-mini-audio-preview") -> ModelPricing.Pricing(0.15, 0.60)
            modelName.contains("gpt-4o-mini-realtime-preview") -> ModelPricing.Pricing(0.60, 2.40)
            modelName.contains("gpt-4o-search-preview") -> ModelPricing.Pricing(2.50, 10.00)
            modelName.contains("gpt-4o-mini-search-preview") -> ModelPricing.Pricing(0.15, 0.60)
            
            // O-series models - Reasoning models with higher accuracy
            modelName.contains("o1-2024-12-17") -> ModelPricing.Pricing(15.00, 60.00) // Advanced reasoning
            modelName.contains("o1-pro-2025-03-19") -> ModelPricing.Pricing(150.00, 600.00) // Top-tier reasoning
            modelName.contains("o1-mini-2024-09-12") -> ModelPricing.Pricing(1.10, 4.40)
            modelName.contains("o3-pro-2025-06-10") -> ModelPricing.Pricing(20.00, 80.00) // Pro-level reasoning
            modelName.contains("o3-2025-04-16") -> ModelPricing.Pricing(2.00, 8.00)
            modelName.contains("o3-deep-research-2025-06-26") -> ModelPricing.Pricing(10.00, 40.00) // Deep research capabilities
            modelName.contains("o3-mini-2025-01-31") -> ModelPricing.Pricing(1.10, 4.40)
            modelName.contains("o4-mini-2025-04-16") -> ModelPricing.Pricing(1.10, 4.40)
            modelName.contains("o4-mini-deep-research-2025-06-26") -> ModelPricing.Pricing(2.00, 8.00)
            
            // Special capability models
            modelName.contains("codex-mini-latest") -> ModelPricing.Pricing(1.50, 6.00) // Code generation
            modelName.contains("computer-use-preview") -> ModelPricing.Pricing(3.00, 12.00) // Computer interaction
            modelName.contains("gpt-image-1") -> ModelPricing.Pricing(5.00, 0.0) // Image generation
            
            // Claude models - High-quality reasoning
            modelName.contains("claude-opus-4.1") -> ModelPricing.Pricing(15.00, 75.00) // Premium Claude
            modelName.contains("claude-opus-4") -> ModelPricing.Pricing(15.00, 75.00) // Premium Claude
            modelName.contains("claude-sonnet-4") -> ModelPricing.Pricing(3.00, 15.00) // Balanced Claude
            
            // Grok models
            modelName.contains("grok-4-0709") -> ModelPricing.Pricing(3.00, 15.00)
            
            // Legacy models - Lower capability but still valuable
            modelName.contains("gpt-4-turbo") -> ModelPricing.Pricing(10.00, 30.00) // Previous flagship
            modelName.contains("gpt-4") && !modelName.contains("gpt-4o") -> ModelPricing.Pricing(30.00, 60.00) // Original GPT-4
            modelName.contains("gpt-3.5-turbo") -> ModelPricing.Pricing(0.50, 1.50) // Legacy but efficient
            
            // Default fallback
            else -> ModelPricing.Pricing(1.00, 2.00)
        }

        // Apply model strength multiplier to reflect provider's actual model capabilities
        val tier = getModelTier(modelName)
        return ModelPricing.Pricing(
            input = basePricing.input * tier.strengthMultiplier,
            output = basePricing.output * tier.strengthMultiplier
        )
    }

    /**
     * Calculate length multiplier based on response length
     */
    private fun calculateLengthMultiplier(responseLength: Int): Double {
        return when {
            responseLength == 0 -> 1.0
            responseLength < 100 -> 1.0
            responseLength < 500 -> 1.1
            responseLength < 1000 -> 1.2
            responseLength < 2000 -> 1.3
            responseLength < 5000 -> 1.4
            else -> 1.5
        }
    }

    /**
     * Log token usage to persistent storage
     */
    private suspend fun logUsage(usage: TokenUsage) {
        try {
            val logFile = File(context.filesDir, TOKEN_POOL_FILE)
            val logData = if (logFile.exists()) {
                JSONObject(logFile.readText())
            } else {
                JSONObject()
            }

            // Add usage entry
            val usageJson = JSONObject().apply {
                put("modelName", usage.modelName)
                put("responseType", usage.responseType.name)
                put("inputTokens", usage.inputTokens)
                put("outputTokens", usage.outputTokens)
                put("totalCost", usage.totalCost)
                put("timestamp", usage.timestamp)
                put("agentName", usage.agentName ?: "unknown")
                put("responseLength", usage.responseLength)
            }

            val usageArray = logData.optJSONArray("usage") ?: org.json.JSONArray()
            usageArray.put(usageJson)
            logData.put("usage", usageArray)

            // Keep only last 1000 entries
            if (usageArray.length() > 1000) {
                val newArray = org.json.JSONArray()
                for (i in (usageArray.length() - 1000) until usageArray.length()) {
                    newArray.put(usageArray.get(i))
                }
                logData.put("usage", newArray)
            }

            logFile.writeText(logData.toString(2))
        } catch (e: Exception) {
            Log.e(TAG, "Error logging usage", e)
        }
    }

    /**
     * Check for quota alerts and log warnings
     */
    private fun checkQuotaAlerts() {
        val usagePercentage = getUsagePercentage()
        val remaining = getRemainingTokens()

        when {
            usagePercentage >= 0.95 -> {
                Log.w(TAG, "CRITICAL: Token pool 95% depleted. Remaining: $remaining tokens")
            }
            usagePercentage >= 0.85 -> {
                Log.w(TAG, "WARNING: Token pool 85% depleted. Remaining: $remaining tokens")
            }
            usagePercentage >= 0.75 -> {
                Log.i(TAG, "NOTICE: Token pool 75% depleted. Remaining: $remaining tokens")
            }
        }
    }

    /**
     * Get top consuming agents/models
     */
    suspend fun getTopConsumers(limit: Int = 3): List<Pair<String, Double>> = withContext(Dispatchers.IO) {
        try {
            val logFile = File(context.filesDir, TOKEN_POOL_FILE)
            if (!logFile.exists()) return@withContext emptyList()

            val logData = JSONObject(logFile.readText())
            val usageArray = logData.optJSONArray("usage") ?: return@withContext emptyList()

            val consumerMap = mutableMapOf<String, Double>()

            for (i in 0 until usageArray.length()) {
                val usage = usageArray.getJSONObject(i)
                val consumer = usage.optString("agentName", "unknown") + " (${usage.optString("modelName")})"
                val cost = usage.optDouble("totalCost", 0.0)
                consumerMap[consumer] = (consumerMap[consumer] ?: 0.0) + cost
            }

            consumerMap.toList()
                .sortedByDescending { it.second }
                .take(limit)

        } catch (e: Exception) {
            Log.e(TAG, "Error getting top consumers", e)
            emptyList()
        }
    }

    /**
     * Reset the token pool (admin function)
     */
    fun resetTokenPool(newTotalTokens: Double = DEFAULT_TOTAL_TOKENS) {
        prefs.edit()
            .putFloat(KEY_TOTAL_TOKENS, newTotalTokens.toFloat())
            .putFloat(KEY_USED_TOKENS, 0f)
            .putString(KEY_LAST_RESET, currentTimestamp())
            .apply()
        
        Log.i(TAG, "Token pool reset to $newTotalTokens tokens")
    }

    /**
     * Add tokens to the pool
     */
    fun addTokens(additionalTokens: Double) {
        val current = getTotalTokens()
        val newTotal = current + additionalTokens
        prefs.edit()
            .putFloat(KEY_TOTAL_TOKENS, newTotal.toFloat())
            .apply()
        
        Log.i(TAG, "Added $additionalTokens tokens. New total: $newTotal")
    }

    /**
     * Get current status summary
     */
    suspend fun getStatusSummary(): String = withContext(Dispatchers.IO) {
        val total = getTotalTokens()
        val used = getUsedTokens()
        val remaining = getRemainingTokens()
        val percentage = (getUsagePercentage() * 100).toInt()
        val topConsumers = getTopConsumers(3)

        buildString {
            appendLine("=== TOKEN POOL STATUS ===")
            appendLine("Total tokens: ${total.toInt()}")
            appendLine("Used tokens: ${used.toInt()}")
            appendLine("Remaining tokens: ${remaining.toInt()}")
            appendLine("Usage: $percentage%")
            appendLine()
            appendLine("Top 3 consumers:")
            topConsumers.forEachIndexed { index, (consumer, cost) ->
                appendLine("${index + 1}. $consumer: ${cost.toInt()} tokens")
            }
            appendLine()
            appendLine("Recommended action:")
            when {
                percentage >= 95 -> appendLine("URGENT: Add more tokens immediately!")
                percentage >= 85 -> appendLine("WARNING: Consider adding tokens soon.")
                percentage >= 75 -> appendLine("NOTICE: Monitor usage closely.")
                else -> appendLine("Token pool healthy.")
            }
        }
    }

    private fun currentTimestamp(): String = dateFormat.format(Date())
}