package com.playstudio.aiteacher.pricing

import android.util.Log

/**
 * Smart model selection based on cost optimization and user requirements
 * Ensures the best model is chosen within budget constraints
 */
class CostOptimizedModelSelector {
    
    companion object {
        private const val TAG = "ModelSelector"
        
        // Token estimation constants
        private const val CHARS_PER_TOKEN = 4.0
        private const val COMPLEXITY_MULTIPLIER_LOW = 1.0
        private const val COMPLEXITY_MULTIPLIER_MEDIUM = 1.3
        private const val COMPLEXITY_MULTIPLIER_HIGH = 1.8
    }
    
    /**
     * Select the optimal model for a chat request within budget
     */
    fun selectOptimalModel(
        request: ChatRequest,
        userTier: SubscriptionTier,
        remainingBudget: Double,
        preferredModel: AIModel? = null
    ): ModelSelectionResult {
        
        Log.d(TAG, "Selecting model for user tier: $userTier, budget: $remainingBudget, " +
                "complexity: ${request.complexity}, preferred: ${preferredModel?.displayName}")
        
        try {
            val availableModels = AIModel.getModelsForTier(userTier)
            if (availableModels.isEmpty()) {
                return ModelSelectionResult.NoModelsAvailable(userTier)
            }
            
            val estimatedTokens = estimateTokenUsage(request)
            Log.d(TAG, "Estimated tokens - input: ${estimatedTokens.input}, output: ${estimatedTokens.output}")
            
            // Check if preferred model is viable
            preferredModel?.let { preferred ->
                if (preferred in availableModels) {
                    val estimatedCost = preferred.calculateCost(estimatedTokens.input, estimatedTokens.output)
                    if (estimatedCost <= remainingBudget) {
                        Log.d(TAG, "Using preferred model: ${preferred.displayName}, cost: $estimatedCost")
                        return ModelSelectionResult.Selected(
                            model = preferred,
                            reason = "User preferred model within budget",
                            estimatedCost = estimatedCost
                        )
                    }
                }
            }
            
            // Filter models by budget
            val viableModels = availableModels.filter { model ->
                val estimatedCost = model.calculateCost(estimatedTokens.input, estimatedTokens.output)
                estimatedCost <= remainingBudget
            }
            
            if (viableModels.isEmpty()) {
                val cheapestModel = availableModels.minByOrNull { it.calculateMessageCost() }
                val requiredBudget = cheapestModel?.calculateCost(estimatedTokens.input, estimatedTokens.output) ?: 0.0
                
                return ModelSelectionResult.InsufficientBudget(
                    cheapestOption = cheapestModel,
                    requiredBudget = requiredBudget,
                    currentBudget = remainingBudget
                )
            }
            
            // Select best model based on complexity and cost efficiency
            val selectedModel = when (request.complexity) {
                ComplexityLevel.HIGH -> {
                    // For complex tasks, prioritize capability even if more expensive
                    val bestCapableModel = viableModels.maxByOrNull { it.capabilities }
                    bestCapableModel ?: viableModels.first()
                }
                
                ComplexityLevel.MEDIUM -> {
                    // Balance between capability and cost
                    viableModels.sortedWith(
                        compareByDescending<AIModel> { it.capabilities }
                            .thenBy { it.calculateCost(estimatedTokens.input, estimatedTokens.output) }
                    ).first()
                }
                
                ComplexityLevel.LOW -> {
                    // For simple tasks, prioritize cost efficiency
                    viableModels.minByOrNull { 
                        it.calculateCost(estimatedTokens.input, estimatedTokens.output) 
                    } ?: viableModels.first()
                }
            }
            
            val estimatedCost = selectedModel.calculateCost(estimatedTokens.input, estimatedTokens.output)
            val reason = getSelectionReason(request.complexity, selectedModel, viableModels)
            
            Log.d(TAG, "Selected model: ${selectedModel.displayName}, cost: $estimatedCost, reason: $reason")
            
            return ModelSelectionResult.Selected(
                model = selectedModel,
                reason = reason,
                estimatedCost = estimatedCost
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error selecting model", e)
            return ModelSelectionResult.Error("Failed to select model: ${e.message}")
        }
    }
    
    /**
     * Estimate token usage for a chat request
     */
    private fun estimateTokenUsage(request: ChatRequest): TokenEstimate {
        val baseInputTokens = (request.messageText.length / CHARS_PER_TOKEN).toInt()
        
        // Adjust for context and complexity
        val contextMultiplier = when {
            request.hasImages -> 1.5
            request.hasDocuments -> 1.3
            request.conversationHistory?.isNotEmpty() == true -> 1.2
            else -> 1.0
        }
        
        val complexityMultiplier = when (request.complexity) {
            ComplexityLevel.LOW -> COMPLEXITY_MULTIPLIER_LOW
            ComplexityLevel.MEDIUM -> COMPLEXITY_MULTIPLIER_MEDIUM
            ComplexityLevel.HIGH -> COMPLEXITY_MULTIPLIER_HIGH
        }
        
        val adjustedInputTokens = (baseInputTokens * contextMultiplier).toInt()
        
        // Estimate output tokens based on complexity and request type
        val baseOutputTokens = when (request.complexity) {
            ComplexityLevel.LOW -> adjustedInputTokens * 0.8  // Shorter responses for simple questions
            ComplexityLevel.MEDIUM -> adjustedInputTokens * 1.2
            ComplexityLevel.HIGH -> adjustedInputTokens * 2.0  // Detailed explanations for complex topics
        }
        
        val finalOutputTokens = (baseOutputTokens * complexityMultiplier).toInt()
        
        return TokenEstimate(
            input = adjustedInputTokens,
            output = finalOutputTokens
        )
    }
    
    /**
     * Generate human-readable reason for model selection
     */
    private fun getSelectionReason(
        complexity: ComplexityLevel,
        selectedModel: AIModel,
        availableModels: List<AIModel>
    ): String {
        return when (complexity) {
            ComplexityLevel.HIGH -> {
                if (selectedModel == availableModels.maxByOrNull { it.capabilities }) {
                    "Best available model for complex reasoning"
                } else {
                    "Most capable model within budget for complex task"
                }
            }
            ComplexityLevel.MEDIUM -> {
                "Balanced model for moderate complexity task"
            }
            ComplexityLevel.LOW -> {
                "Most cost-effective model for simple task"
            }
        }
    }
    
    /**
     * Determine complexity level from message content
     */
    fun analyzeComplexity(
        messageText: String,
        hasImages: Boolean = false,
        hasDocuments: Boolean = false,
        conversationHistory: List<String>? = null
    ): ComplexityLevel {
        
        val text = messageText.lowercase()
        var complexityScore = 0
        
        // Check for complexity indicators
        val highComplexityKeywords = listOf(
            "explain", "analyze", "compare", "evaluate", "discuss", "elaborate",
            "algorithm", "implementation", "optimization", "architecture", "design",
            "strategy", "methodology", "framework", "comprehensive", "detailed",
            "step by step", "walkthrough", "tutorial", "complex", "advanced"
        )
        
        val mediumComplexityKeywords = listOf(
            "how", "why", "what", "help", "code", "programming", "solve",
            "create", "build", "develop", "understand", "learn", "example"
        )
        
        // Score based on keywords
        complexityScore += highComplexityKeywords.count { text.contains(it) } * 3
        complexityScore += mediumComplexityKeywords.count { text.contains(it) } * 1
        
        // Additional complexity factors
        if (hasImages) complexityScore += 2
        if (hasDocuments) complexityScore += 2
        if (messageText.length > 500) complexityScore += 2
        if (messageText.contains("?") && messageText.count { it == '?' } > 1) complexityScore += 1
        if (conversationHistory?.size ?: 0 > 5) complexityScore += 1
        
        return when {
            complexityScore >= 8 -> ComplexityLevel.HIGH
            complexityScore >= 3 -> ComplexityLevel.MEDIUM
            else -> ComplexityLevel.LOW
        }
    }
    
    /**
     * Get cost comparison for available models
     */
    fun getModelCostComparison(
        userTier: SubscriptionTier,
        inputTokens: Int,
        outputTokens: Int
    ): List<ModelCostInfo> {
        return AIModel.getModelsForTier(userTier).map { model ->
            ModelCostInfo(
                model = model,
                estimatedCost = model.calculateCost(inputTokens, outputTokens),
                capabilities = model.capabilities,
                efficiency = calculateEfficiency(model, inputTokens, outputTokens)
            )
        }.sortedBy { it.estimatedCost }
    }
    
    /**
     * Calculate efficiency score (capabilities per cost)
     */
    private fun calculateEfficiency(model: AIModel, inputTokens: Int, outputTokens: Int): Double {
        val cost = model.calculateCost(inputTokens, outputTokens)
        return if (cost > 0) model.capabilities / cost else 0.0
    }
}

/**
 * Chat request information for model selection
 */
data class ChatRequest(
    val messageText: String,
    val complexity: ComplexityLevel,
    val hasImages: Boolean = false,
    val hasDocuments: Boolean = false,
    val conversationHistory: List<String>? = null,
    val userPreferences: UserPreferences? = null
)

/**
 * User preferences for model selection
 */
data class UserPreferences(
    val preferredModel: AIModel? = null,
    val prioritizeCost: Boolean = false,
    val prioritizeSpeed: Boolean = false,
    val prioritizeQuality: Boolean = false
)

/**
 * Token usage estimation
 */
data class TokenEstimate(
    val input: Int,
    val output: Int
) {
    val total: Int get() = input + output
}

/**
 * Model selection results
 */
sealed class ModelSelectionResult {
    data class Selected(
        val model: AIModel,
        val reason: String,
        val estimatedCost: Double
    ) : ModelSelectionResult()
    
    data class InsufficientBudget(
        val cheapestOption: AIModel?,
        val requiredBudget: Double,
        val currentBudget: Double
    ) : ModelSelectionResult()
    
    data class NoModelsAvailable(
        val userTier: SubscriptionTier
    ) : ModelSelectionResult()
    
    data class Error(
        val message: String
    ) : ModelSelectionResult()
}

/**
 * Model cost information for comparison
 */
data class ModelCostInfo(
    val model: AIModel,
    val estimatedCost: Double,
    val capabilities: Int,
    val efficiency: Double
)