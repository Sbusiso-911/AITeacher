package com.playstudio.aiteacher.pricing

/**
 * AI model definitions with usage limits per subscription tier
 * All models are available to all users, but with different daily usage limits
 */
enum class AIModel(
    val displayName: String,
    val modelId: String,
    val inputCostPer1M: Double,
    val outputCostPer1M: Double,
    val cachedInputCostPer1M: Double,
    val provider: String,
    val capabilities: Int = 5, // 1-10 scale for model selection
    val maxTokensPerMessage: Int = 16000,
    val averageInputTokens: Int = 200,
    val averageOutputTokens: Int = 300,
    // Usage limits per tier: FREE, BASIC, PRO, PREMIUM, ULTRA_PREMIUM
    val dailyUsageLimits: Map<SubscriptionTier, Int> = mapOf(),
    // Audio capabilities
    val supportsAudioInput: Boolean = false,
    val supportsAudioOutput: Boolean = false,
    val supportedVoices: List<String> = listOf(),
    val supportsRealtime: Boolean = false
) {
    // BASIC MODELS - Higher usage limits across all tiers
    GPT_35_TURBO("GPT-3.5 Turbo", "gpt-3.5-turbo", 0.50, 1.50, 0.25, "OpenAI", 3, 16000, 100, 150,
        mapOf(
            SubscriptionTier.FREE to 20,
            SubscriptionTier.BASIC to 100,
            SubscriptionTier.PRO to 300,
            SubscriptionTier.PREMIUM to 500,
            SubscriptionTier.ULTRA_PREMIUM to -1 // Unlimited
        )
    ),
    
    GEMINI("Gemini 2.5 Flash", "gemini-2.5-flash", 0.075, 0.30, 0.01875, "Google", 4, 8000, 150, 200,
        mapOf(
            SubscriptionTier.FREE to 25,
            SubscriptionTier.BASIC to 120,
            SubscriptionTier.PRO to 350,
            SubscriptionTier.PREMIUM to 600,
            SubscriptionTier.ULTRA_PREMIUM to -1
        )
    ),
    
    DEEPSEEK("DeepSeek", "deepseek-chat", 0.14, 0.28, 0.07, "DeepSeek", 3, 8000, 120, 180,
        mapOf(
            SubscriptionTier.FREE to 15,
            SubscriptionTier.BASIC to 80,
            SubscriptionTier.PRO to 250,
            SubscriptionTier.PREMIUM to 400,
            SubscriptionTier.ULTRA_PREMIUM to -1
        )
    ),
    
    // STANDARD MODELS - Medium usage limits
    
    GPT_41_MINI("GPT-4.1 Mini", "gpt-4.1-mini", 0.40, 1.60, 0.10, "OpenAI", 4, 8000, 150, 200,
        mapOf(
            SubscriptionTier.FREE to 8,
            SubscriptionTier.BASIC to 50,
            SubscriptionTier.PRO to 150,
            SubscriptionTier.PREMIUM to 300,
            SubscriptionTier.ULTRA_PREMIUM to -1
        )
    ),
    
    GEMINI_VOICE("Gemini Voice Chat", "gemini-2.5-flash", 0.125, 0.50, 0.03125, "Google", 5, 8000, 200, 300,
        mapOf(
            SubscriptionTier.FREE to 5,
            SubscriptionTier.BASIC to 30,
            SubscriptionTier.PRO to 100,
            SubscriptionTier.PREMIUM to 200,
            SubscriptionTier.ULTRA_PREMIUM to -1
        )
    ),
    
    // AUDIO-ENABLED MODELS - Support both text and audio input/output
    GPT_4O_AUDIO("GPT-4o Audio", "gpt-4o-audio-preview", 2.50, 10.00, 1.25, "OpenAI", 8, 32000, 250, 400,
        mapOf(
            SubscriptionTier.FREE to 2,
            SubscriptionTier.BASIC to 15,
            SubscriptionTier.PRO to 60,
            SubscriptionTier.PREMIUM to 150,
            SubscriptionTier.ULTRA_PREMIUM to 400
        ),
        supportsAudioInput = true,
        supportsAudioOutput = true,
        supportedVoices = listOf("alloy", "echo", "fable", "onyx", "nova", "shimmer"),
        supportsRealtime = false
    ),
    
    GPT_4O_MINI_AUDIO("GPT-4o Mini Audio", "gpt-4o-mini-audio-preview", 0.15, 0.60, 0.075, "OpenAI", 6, 16000, 200, 300,
        mapOf(
            SubscriptionTier.FREE to 5,
            SubscriptionTier.BASIC to 40,
            SubscriptionTier.PRO to 120,
            SubscriptionTier.PREMIUM to 250,
            SubscriptionTier.ULTRA_PREMIUM to -1
        ),
        supportsAudioInput = true,
        supportsAudioOutput = true,
        supportedVoices = listOf("alloy", "echo", "fable", "onyx", "nova", "shimmer"),
        supportsRealtime = false
    ),
    
    // ADVANCED MODELS - Lower usage limits
    GPT_4O("GPT-4o", "gpt-4o", 2.50, 10.00, 1.25, "OpenAI", 7, 32000, 250, 400,
        mapOf(
            SubscriptionTier.FREE to 3,
            SubscriptionTier.BASIC to 20,
            SubscriptionTier.PRO to 80,
            SubscriptionTier.PREMIUM to 200,
            SubscriptionTier.ULTRA_PREMIUM to 500
        )
    ),
    
    GPT_4_TURBO("GPT-4 Turbo", "gpt-4-turbo", 10.00, 30.00, 5.00, "OpenAI", 6, 32000, 300, 500,
        mapOf(
            SubscriptionTier.FREE to 2,
            SubscriptionTier.BASIC to 15,
            SubscriptionTier.PRO to 60,
            SubscriptionTier.PREMIUM to 150,
            SubscriptionTier.ULTRA_PREMIUM to 400
        )
    ),
    
    GPT_4O_SEARCH("GPT-4o Search", "gpt-4o-search-preview", 2.50, 10.00, 1.25, "OpenAI", 7, 32000, 250, 400,
        mapOf(
            SubscriptionTier.FREE to 2,
            SubscriptionTier.BASIC to 15,
            SubscriptionTier.PRO to 50,
            SubscriptionTier.PREMIUM to 120,
            SubscriptionTier.ULTRA_PREMIUM to 300
        )
    ),
    
    
    CLAUDE_SONNET_4("Claude Sonnet 4", "claude-sonnet-4-20250514", 3.00, 15.00, 0.30, "Anthropic", 8, 32000, 200, 300,
        mapOf(
            SubscriptionTier.FREE to 2,
            SubscriptionTier.BASIC to 12,
            SubscriptionTier.PRO to 50,
            SubscriptionTier.PREMIUM to 150,
            SubscriptionTier.ULTRA_PREMIUM to 400
        )
    ),
    
    // PREMIUM MODELS - Very low usage limits
    O1("O1", "o1", 15.00, 60.00, 7.50, "OpenAI", 9, 32000, 400, 600,
        mapOf(
            SubscriptionTier.FREE to 1,
            SubscriptionTier.BASIC to 3,
            SubscriptionTier.PRO to 10,
            SubscriptionTier.PREMIUM to 30,
            SubscriptionTier.ULTRA_PREMIUM to 100
        )
    ),
    
    O1_MINI("O1 Mini", "o1-mini", 3.00, 12.00, 1.50, "OpenAI", 8, 16000, 300, 450,
        mapOf(
            SubscriptionTier.FREE to 1,
            SubscriptionTier.BASIC to 5,
            SubscriptionTier.PRO to 15,
            SubscriptionTier.PREMIUM to 50,
            SubscriptionTier.ULTRA_PREMIUM to 150
        )
    ),
    
    O3("O3", "o3", 2.00, 8.00, 0.50, "OpenAI", 10, 200000, 500, 1000,
        mapOf(
            SubscriptionTier.FREE to 1,
            SubscriptionTier.BASIC to 2,
            SubscriptionTier.PRO to 5,
            SubscriptionTier.PREMIUM to 15,
            SubscriptionTier.ULTRA_PREMIUM to 50
        )
    ),
    
    O3_MINI("O3 Mini", "o3-mini", 1.10, 4.40, 0.275, "OpenAI", 7, 16000, 200, 350,
        mapOf(
            SubscriptionTier.FREE to 1,
            SubscriptionTier.BASIC to 5,
            SubscriptionTier.PRO to 20,
            SubscriptionTier.PREMIUM to 60,
            SubscriptionTier.ULTRA_PREMIUM to 200
        )
    ),
    
    GPT_4O_REALTIME("GPT-4o Realtime Preview", "gpt-4o-realtime-preview", 5.00, 20.00, 2.50, "OpenAI", 8, 32000, 200, 300,
        mapOf(
            SubscriptionTier.FREE to 1,
            SubscriptionTier.BASIC to 2,
            SubscriptionTier.PRO to 8,
            SubscriptionTier.PREMIUM to 25,
            SubscriptionTier.ULTRA_PREMIUM to 80
        ),
        supportsAudioInput = true,
        supportsAudioOutput = true,
        supportedVoices = listOf("alloy", "echo", "fable", "onyx", "nova", "shimmer"),
        supportsRealtime = true
    ),
    
    
    OPENAI_REALTIME_VOICE("OpenAI Realtime Voice", "openai-realtime-voice", 40.00, 80.00, 20.00, "OpenAI", 9, 32000, 250, 350,
        mapOf(
            SubscriptionTier.FREE to 1,
            SubscriptionTier.BASIC to 2,
            SubscriptionTier.PRO to 5,
            SubscriptionTier.PREMIUM to 15,
            SubscriptionTier.ULTRA_PREMIUM to 50
        ),
        supportsAudioInput = true,
        supportsAudioOutput = true,
        supportedVoices = listOf("alloy", "echo", "fable", "onyx", "nova", "shimmer"),
        supportsRealtime = true
    ),
    
    DALL_E_3("DALL-E 3", "dall-e-3", 40.00, 80.00, 20.00, "OpenAI", 8, 4000, 100, 50,
        mapOf(
            SubscriptionTier.FREE to 1,
            SubscriptionTier.BASIC to 3,
            SubscriptionTier.PRO to 10,
            SubscriptionTier.PREMIUM to 30,
            SubscriptionTier.ULTRA_PREMIUM to 100
        )
    ),
    
    
    // ULTRA PREMIUM MODELS - Extremely limited usage
    CLAUDE_OPUS_4("Claude Opus 4", "claude-opus-4-20250514", 15.00, 75.00, 1.50, "Anthropic", 10, 32000, 300, 500,
        mapOf(
            SubscriptionTier.FREE to 1,
            SubscriptionTier.BASIC to 1,
            SubscriptionTier.PRO to 3,
            SubscriptionTier.PREMIUM to 8,
            SubscriptionTier.ULTRA_PREMIUM to 25
        )
    );
    
    /**
     * Calculate cost per message using average token counts
     */
    fun calculateMessageCost(useCache: Boolean = false): Double {
        val inputCost = if (useCache) cachedInputCostPer1M else inputCostPer1M
        return ((averageInputTokens * inputCost) + (averageOutputTokens * outputCostPer1M)) / 1_000_000
    }
    
    /**
     * Calculate cost for specific token usage
     */
    fun calculateCost(inputTokens: Int, outputTokens: Int, useCache: Boolean = false): Double {
        val inputCost = if (useCache) cachedInputCostPer1M else inputCostPer1M
        return ((inputTokens * inputCost) + (outputTokens * outputCostPer1M)) / 1_000_000
    }
    
    /**
     * Get cost per input token (for real-time calculation)
     */
    fun getInputTokenCost(useCache: Boolean = false): Double {
        val inputCost = if (useCache) cachedInputCostPer1M else inputCostPer1M
        return inputCost / 1_000_000
    }
    
    /**
     * Get cost per output token (for real-time calculation)
     */
    fun getOutputTokenCost(): Double {
        return outputCostPer1M / 1_000_000
    }
    
    /**
     * Get usage limit for this model for a specific tier
     */
    fun getUsageLimitForTier(tier: SubscriptionTier): Int {
        return dailyUsageLimits[tier] ?: 0
    }
    
    /**
     * Check if model has usage remaining for a tier
     */
    fun hasUsageRemainingForTier(tier: SubscriptionTier): Boolean {
        val limit = getUsageLimitForTier(tier)
        return limit > 0 || limit == -1 // -1 means unlimited
    }
    
    /**
     * Check if this model supports audio input
     */
    fun supportsAudio(): Boolean {
        return supportsAudioInput || supportsAudioOutput
    }
    
    /**
     * Check if this model supports realtime voice interaction
     */
    fun supportsRealtimeVoice(): Boolean {
        return supportsRealtime && supportsAudioInput && supportsAudioOutput
    }
    
    /**
     * Get audio modalities supported by this model
     */
    fun getSupportedModalities(): List<String> {
        val modalities = mutableListOf("text")
        if (supportsAudioInput || supportsAudioOutput) {
            modalities.add("audio")
        }
        return modalities
    }
    
    companion object {
        /**
         * Find model by ID string
         */
        fun fromModelId(modelId: String): AIModel? {
            return values().find { it.modelId == modelId }
        }
        
        /**
         * Get all models (all models are available to all tiers, but with different usage limits)
         */
        fun getAllModels(): List<AIModel> {
            return values().toList()
        }
        
        /**
         * Get models available for subscription tier (all models, but filtered by usage limits)
         */
        fun getModelsForTier(tier: SubscriptionTier): List<AIModel> {
            return values().filter { model ->
                model.hasUsageRemainingForTier(tier)
            }
        }
        
        /**
         * Get models with their usage limits for a specific tier
         */
        fun getModelsWithUsageLimits(tier: SubscriptionTier): List<Pair<AIModel, Int>> {
            return values().map { model ->
                Pair(model, model.getUsageLimitForTier(tier))
            }.filter { (_, limit) -> limit > 0 || limit == -1 }
        }
        
        /**
         * Get cheapest model for tier
         */
        fun getCheapestModelForTier(tier: SubscriptionTier): AIModel? {
            return getModelsForTier(tier).minByOrNull { it.calculateMessageCost() }
        }
        
        /**
         * Get best model for tier (highest capabilities)
         */
        fun getBestModelForTier(tier: SubscriptionTier): AIModel? {
            return getModelsForTier(tier).maxByOrNull { it.capabilities }
        }
        
        /**
         * Get models sorted by usage limit for a tier (highest limits first)
         */
        fun getModelsSortedByUsageLimit(tier: SubscriptionTier): List<AIModel> {
            return getModelsForTier(tier).sortedByDescending { model ->
                val limit = model.getUsageLimitForTier(tier)
                if (limit == -1) Int.MAX_VALUE else limit
            }
        }
        
        /**
         * Get all models that support audio input/output
         */
        fun getAudioEnabledModels(): List<AIModel> {
            return values().filter { it.supportsAudio() }
        }
        
        /**
         * Get models with realtime voice capabilities
         */
        fun getRealtimeVoiceModels(): List<AIModel> {
            return values().filter { it.supportsRealtimeVoice() }
        }
        
        /**
         * Get audio-enabled models for specific tier
         */
        fun getAudioModelsForTier(tier: SubscriptionTier): List<AIModel> {
            return getModelsForTier(tier).filter { it.supportsAudio() }
        }
        
        /**
         * Get all supported voices across all models
         */
        fun getAllSupportedVoices(): List<String> {
            return values().flatMap { it.supportedVoices }.distinct().sorted()
        }
    }
}

/**
 * Additional API costs for features beyond basic chat
 */
data class AdditionalAPICosts(
    val codeInterpreter: Double = 0.03,
    val fileSearchPerGB: Double = 0.10,
    val webSearchPer1K: Double = 25.00, // For GPT-4o models
    val webSearchPer1KReasoning: Double = 10.00, // For o3/o4-mini
    val imageGenerationLow: Double = 0.01,
    val imageGenerationMedium: Double = 0.04,
    val imageGenerationHigh: Double = 0.17,
    val textToSpeechPer1KChars: Double = 15.00,
    val speechToTextPerMinute: Double = 0.006,
    val visionAnalysisPerImage: Double = 0.00765
)

/**
 * API features that have additional costs
 */
enum class APIFeature(val cost: Double) {
    CODE_INTERPRETER(0.03),
    FILE_SEARCH_1GB(0.10),
    WEB_SEARCH_1K(25.00),
    WEB_SEARCH_1K_REASONING(10.00),
    IMAGE_GEN_LOW(0.01),
    IMAGE_GEN_MEDIUM(0.04),
    IMAGE_GEN_HIGH(0.17),
    TTS_1K_CHARS(0.015), // $15/1M chars
    STT_1_MINUTE(0.006),
    VISION_ANALYSIS(0.00765)
}

/**
 * Subscription tiers with hierarchical access
 */
enum class SubscriptionTier(val displayName: String, val level: Int) {
    FREE("Free", 0),
    BASIC("Basic", 1),
    PRO("Pro", 2),
    PREMIUM("Premium", 3),
    ULTRA_PREMIUM("Ultra Premium", 4);
    
    fun canAccess(requiredTier: SubscriptionTier): Boolean {
        return this.level >= requiredTier.level
    }
}

/**
 * Billing cycles for subscription plans
 */
enum class BillingCycle(val days: Int, val displayName: String) {
    WEEKLY(7, "Weekly"),
    MONTHLY(30, "Monthly"),
    YEARLY(365, "Yearly")
}

/**
 * Usage limits per billing period
 */
data class UsageLimits(
    val messages: Int,
    val tokens: Int,
    val images: Int,
    val webSearches: Int,
    val ttsMinutes: Int = 0,
    val sttMinutes: Int = 0,
    val visionAnalyses: Int = 0
)

/**
 * Complexity levels for smart model selection
 */
enum class ComplexityLevel {
    LOW,    // Simple questions, basic chat
    MEDIUM, // Code help, detailed explanations
    HIGH    // Complex reasoning, multi-step problems
}