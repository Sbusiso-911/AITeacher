package com.playstudio.aiteacher.credits

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*

/**
 * Integration layer that connects TokenPoolManager with existing AI model adapters.
 * Provides easy-to-use methods for deducting tokens across different AI models and response types.
 */
class TokenPoolIntegration private constructor(private val context: Context) {

    private val tokenPoolManager = TokenPoolManager.getInstance(context)
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    companion object {
        private const val TAG = "TokenPoolIntegration"

        @Volatile
        private var INSTANCE: TokenPoolIntegration? = null

        fun getInstance(context: Context): TokenPoolIntegration {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: TokenPoolIntegration(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    /**
     * Process token deduction for a completed AI response with user tier and daily limits
     */
    suspend fun processAIResponse(
        modelName: String,
        responseType: TokenPoolManager.ResponseType,
        inputTokens: Int,
        outputTokens: Int,
        responseText: String = "",
        agentName: String? = null,
        userId: String = "default_user",
        userTier: TokenPoolManager.SubscriptionTier = TokenPoolManager.SubscriptionTier.FREE
    ): Boolean {
        return tokenPoolManager.deductTokens(
            modelName = modelName,
            responseType = responseType,
            inputTokens = inputTokens,
            outputTokens = outputTokens,
            responseLength = responseText.length,
            agentName = agentName,
            userId = userId,
            userTier = userTier
        )
    }
    /**
     * Process token deduction when only the response length is known/estimated
     * Useful when we have token counts but not the actual text string.
     */
    suspend fun processAIResponseWithLength(
        modelName: String,
        responseType: TokenPoolManager.ResponseType,
        inputTokens: Int,
        outputTokens: Int,
        responseLength: Int,
        agentName: String? = null,
        userId: String = "default_user",
        userTier: TokenPoolManager.SubscriptionTier = TokenPoolManager.SubscriptionTier.FREE
    ): Boolean {
        return tokenPoolManager.deductTokens(
            modelName = modelName,
            responseType = responseType,
            inputTokens = inputTokens,
            outputTokens = outputTokens,
            responseLength = responseLength,
            agentName = agentName,
            userId = userId,
            userTier = userTier
        )
    }

    /**
     * Process text response from OpenAI models
     */
    suspend fun processOpenAITextResponse(
        modelName: String,
        inputTokens: Int,
        outputTokens: Int,
        responseText: String,
        agentName: String? = "openai-adapter",
        userId: String = "default_user",
        userTier: TokenPoolManager.SubscriptionTier = TokenPoolManager.SubscriptionTier.FREE
    ): Boolean {
        return processAIResponse(
            modelName = modelName,
            responseType = TokenPoolManager.ResponseType.TEXT,
            inputTokens = inputTokens,
            outputTokens = outputTokens,
            responseText = responseText,
            agentName = agentName,
            userId = userId,
            userTier = userTier
        )
    }

    /**
     * Process image generation response
     */
    suspend fun processImageResponse(
        modelName: String,
        inputTokens: Int,
        outputTokens: Int,
        imageCount: Int = 1,
        agentName: String? = "image-generator",
        userId: String = "default_user",
        userTier: TokenPoolManager.SubscriptionTier = TokenPoolManager.SubscriptionTier.FREE
    ): Boolean {
        // Image responses have higher cost multiplier
        return processAIResponse(
            modelName = modelName,
            responseType = TokenPoolManager.ResponseType.IMAGE,
            inputTokens = inputTokens,
            outputTokens = outputTokens,
            responseText = (imageCount * 1000).toString(), // Approximate image "length" for costing
            agentName = agentName,
            userId = userId,
            userTier = userTier
        )
    }

    /**
     * Process voice/audio response
     */
    suspend fun processVoiceResponse(
        modelName: String,
        inputTokens: Int,
        outputTokens: Int,
        audioDurationSeconds: Int,
        isRealtime: Boolean = false,
        agentName: String? = "voice-adapter",
        userId: String = "default_user",
        userTier: TokenPoolManager.SubscriptionTier = TokenPoolManager.SubscriptionTier.FREE
    ): Boolean {
        val responseType = if (isRealtime) {
            TokenPoolManager.ResponseType.REALTIME
        } else {
            TokenPoolManager.ResponseType.AUDIO
        }

        return processAIResponse(
            modelName = modelName,
            responseType = responseType,
            inputTokens = inputTokens,
            outputTokens = outputTokens,
            responseText = (audioDurationSeconds * 100).toString(), // Duration-based length calculation
            agentName = agentName,
            userId = userId,
            userTier = userTier
        )
    }

    /**
     * Process live voice interaction
     */
    suspend fun processLiveVoiceResponse(
        modelName: String,
        inputTokens: Int,
        outputTokens: Int,
        sessionDurationSeconds: Int,
        agentName: String? = "live-voice-adapter",
        userId: String = "default_user",
        userTier: TokenPoolManager.SubscriptionTier = TokenPoolManager.SubscriptionTier.FREE
    ): Boolean {
        return processAIResponse(
            modelName = modelName,
            responseType = TokenPoolManager.ResponseType.LIVE_VOICE,
            inputTokens = inputTokens,
            outputTokens = outputTokens,
            responseText = (sessionDurationSeconds * 150).toString(), // Live session cost factor
            agentName = agentName,
            userId = userId,
            userTier = userTier
        )
    }

    /**
     * Process search-enhanced response
     */
    suspend fun processSearchResponse(
        modelName: String,
        inputTokens: Int,
        outputTokens: Int,
        responseText: String,
        searchQueries: Int = 1,
        agentName: String? = "search-adapter",
        userId: String = "default_user",
        userTier: TokenPoolManager.SubscriptionTier = TokenPoolManager.SubscriptionTier.FREE
    ): Boolean {
        return processAIResponse(
            modelName = modelName,
            responseType = TokenPoolManager.ResponseType.SEARCH,
            inputTokens = inputTokens,
            outputTokens = outputTokens,
            responseText = responseText + "_search_overhead_${searchQueries * 200}", // Add search overhead
            agentName = agentName,
            userId = userId,
            userTier = userTier
        )
    }

    /**
     * Process code interpreter response
     */
    suspend fun processCodeResponse(
        modelName: String,
        inputTokens: Int,
        outputTokens: Int,
        responseText: String,
        codeExecutions: Int = 1,
        agentName: String? = "code-interpreter",
        userId: String = "default_user",
        userTier: TokenPoolManager.SubscriptionTier = TokenPoolManager.SubscriptionTier.FREE
    ): Boolean {
        return processAIResponse(
            modelName = modelName,
            responseType = TokenPoolManager.ResponseType.CODE_INTERPRETER,
            inputTokens = inputTokens,
            outputTokens = outputTokens,
            responseText = responseText + "_code_overhead_${codeExecutions * 300}", // Add code execution overhead
            agentName = agentName,
            userId = userId,
            userTier = userTier
        )
    }

    /**
     * Check if user can afford a specific request before making it
     */
    fun canAffordRequest(
        modelName: String,
        responseType: TokenPoolManager.ResponseType,
        estimatedInputTokens: Int = 1000,
        estimatedOutputTokens: Int = 1000,
        estimatedResponseLength: Int = 0,
        userTier: TokenPoolManager.SubscriptionTier = TokenPoolManager.SubscriptionTier.FREE
    ): Boolean {
        val estimatedCost = tokenPoolManager.calculateTokenCost(
            modelName = modelName,
            responseType = responseType,
            inputTokens = estimatedInputTokens,
            outputTokens = estimatedOutputTokens,
            responseLength = estimatedResponseLength,
            userTier = userTier
        )
        return tokenPoolManager.canAffordTokens(estimatedCost)
    }

    /**
     * Get cost preview for a request
     */
    fun getCostPreview(
        modelName: String,
        responseType: TokenPoolManager.ResponseType,
        estimatedInputTokens: Int = 1000,
        estimatedOutputTokens: Int = 1000,
        estimatedResponseLength: Int = 0,
        userTier: TokenPoolManager.SubscriptionTier = TokenPoolManager.SubscriptionTier.FREE
    ): Double {
        return tokenPoolManager.calculateTokenCost(
            modelName = modelName,
            responseType = responseType,
            inputTokens = estimatedInputTokens,
            outputTokens = estimatedOutputTokens,
            responseLength = estimatedResponseLength,
            userTier = userTier
        )
    }

    /**
     * Get current pool status
     */
    fun getPoolStatus(): PoolStatus {
        return PoolStatus(
            totalTokens = tokenPoolManager.getTotalTokens(),
            usedTokens = tokenPoolManager.getUsedTokens(),
            remainingTokens = tokenPoolManager.getRemainingTokens(),
            usagePercentage = tokenPoolManager.getUsagePercentage()
        )
    }

    /**
     * Get comprehensive status report
     */
    suspend fun getStatusReport(): String {
        return tokenPoolManager.getStatusSummary()
    }

    /**
     * Process token deduction with automatic error handling and retries
     */
    fun processTokenDeductionAsync(
        modelName: String,
        responseType: TokenPoolManager.ResponseType,
        inputTokens: Int,
        outputTokens: Int,
        responseText: String = "",
        agentName: String? = null,
        userId: String = "default_user",
        userTier: TokenPoolManager.SubscriptionTier = TokenPoolManager.SubscriptionTier.FREE,
        onSuccess: () -> Unit = {},
        onFailure: (String) -> Unit = {}
    ) {
        scope.launch {
            try {
                val success = processAIResponse(
                    modelName = modelName,
                    responseType = responseType,
                    inputTokens = inputTokens,
                    outputTokens = outputTokens,
                    responseText = responseText,
                    agentName = agentName,
                    userId = userId,
                    userTier = userTier
                )

                withContext(Dispatchers.Main) {
                    if (success) {
                        onSuccess()
                    } else {
                        onFailure("Insufficient tokens in pool or daily limit exceeded")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing token deduction", e)
                withContext(Dispatchers.Main) {
                    onFailure("Error processing token deduction: ${e.message}")
                }
            }
        }
    }

    /**
     * Helper method for ChatFragment integration
     */
    fun processChatMessage(
        modelName: String,
        inputTokens: Int,
        outputTokens: Int,
        messageText: String,
        isVoice: Boolean = false,
        isImage: Boolean = false,
        userId: String = "default_user",
        userTier: TokenPoolManager.SubscriptionTier = TokenPoolManager.SubscriptionTier.FREE,
        onComplete: (Boolean, String) -> Unit
    ) {
        val responseType = when {
            isImage -> TokenPoolManager.ResponseType.IMAGE
            isVoice -> TokenPoolManager.ResponseType.AUDIO
            else -> TokenPoolManager.ResponseType.TEXT
        }

        processTokenDeductionAsync(
            modelName = modelName,
            responseType = responseType,
            inputTokens = inputTokens,
            outputTokens = outputTokens,
            responseText = messageText,
            agentName = "chat-fragment",
            userId = userId,
            userTier = userTier,
            onSuccess = { 
                onComplete(true, "Tokens processed successfully")
            },
            onFailure = { error ->
                onComplete(false, error)
            }
        )
    }

    /**
     * Get recommended action based on current token usage
     */
    fun getRecommendedAction(): String {
        val usagePercentage = tokenPoolManager.getUsagePercentage()
        val remaining = tokenPoolManager.getRemainingTokens()

        return when {
            usagePercentage >= 0.95 -> "URGENT: Token pool critically low (${remaining.toInt()} remaining). Add tokens immediately!"
            usagePercentage >= 0.85 -> "WARNING: Token pool running low (${remaining.toInt()} remaining). Consider adding tokens."
            usagePercentage >= 0.75 -> "NOTICE: Token usage at ${(usagePercentage * 100).toInt()}%. Monitor usage closely."
            else -> "Token pool healthy (${remaining.toInt()} tokens remaining)."
        }
    }

    /**
     * Data class for pool status
     */
    data class PoolStatus(
        val totalTokens: Double,
        val usedTokens: Double,
        val remainingTokens: Double,
        val usagePercentage: Double
    ) {
        val isHealthy: Boolean get() = usagePercentage < 0.75
        val needsAttention: Boolean get() = usagePercentage >= 0.85
        val isCritical: Boolean get() = usagePercentage >= 0.95
    }

    /**
     * Clean up resources
     */
    fun cleanup() {
        scope.cancel()
    }
}
