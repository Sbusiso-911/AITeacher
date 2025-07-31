package com.playstudio.aiteacher.pricing

/**
 * Cost-optimized subscription plans based on real API costs
 * Ensures profitability while providing maximum user value
 */
data class CostAwareSubscriptionPlan(
    val tier: SubscriptionTier,
    val name: String,
    val price: Double,
    val billingCycle: BillingCycle,
    val dailyLimits: UsageLimits,
    val allowedModels: List<AIModel>,
    val estimatedDailyCost: Double,
    val profitMargin: Double,
    val maxDailyCostLimit: Double, // Critical safety net
    val features: List<String> = emptyList()
) {
    val dailyRevenue: Double
        get() = price / billingCycle.days
        
    val dailyProfit: Double
        get() = dailyRevenue - estimatedDailyCost
        
    val profitMarginPercentage: Double
        get() = if (dailyRevenue > 0) (dailyProfit / dailyRevenue) * 100 else 0.0
        
    val isProfiTable: Boolean
        get() = dailyProfit > 0
}

/**
 * Pre-configured cost-optimized subscription plans
 * Updated with real 2025 API pricing and Claude Opus 4 considerations
 */
object SubscriptionPlans {
    
    val FREE_PLAN = CostAwareSubscriptionPlan(
        tier = SubscriptionTier.FREE,
        name = "Free Starter",
        price = 0.0,
        billingCycle = BillingCycle.MONTHLY,
        dailyLimits = UsageLimits(
            messages = 20, // Limited but enough to try
            tokens = 5000,
            images = 0,
            webSearches = 0,
            ttsMinutes = 0,
            sttMinutes = 0,
            visionAnalyses = 2
        ),
        allowedModels = listOf(AIModel.GPT_35_TURBO, AIModel.GEMINI, AIModel.DEEPSEEK),
        estimatedDailyCost = 0.06, // 20 messages × $0.003 average cost
        profitMargin = -100.0, // Loss leader for user acquisition
        maxDailyCostLimit = 0.10, // STRICT limit
        features = listOf(
            "20 messages per day",
            "GPT-3.5 Turbo, Gemini, DeepSeek",
            "Basic text responses",
            "Community support"
        )
    )
    
    val BASIC_PLAN = CostAwareSubscriptionPlan(
        tier = SubscriptionTier.BASIC,
        name = "Essential Plan",
        price = 9.99,
        billingCycle = BillingCycle.MONTHLY,
        dailyLimits = UsageLimits(
            messages = 100,
            tokens = 20000,
            images = 2,
            webSearches = 3,
            ttsMinutes = 5,
            sttMinutes = 5,
            visionAnalyses = 5
        ),
        allowedModels = listOf(
            AIModel.GPT_35_TURBO, AIModel.GEMINI, AIModel.DEEPSEEK,
            AIModel.GPT_41_MINI, AIModel.GEMINI_VOICE,
            AIModel.GPT_4O_MINI_AUDIO
        ),
        estimatedDailyCost = 0.30, // 100 messages × $0.003 average cost
        profitMargin = 25.0, // $0.33/day revenue - $0.30/day cost = healthy profit
        maxDailyCostLimit = 0.60, // STRICT
        features = listOf(
            "100 messages per day",
            "GPT-4o mini & GPT-4.1 mini access",
            "Gemini Voice Chat",
            "2 image generations daily",
            "3 web searches",
            "5 minutes text-to-speech",
            "Email support"
        )
    )
    
    val PRO_PLAN = CostAwareSubscriptionPlan(
        tier = SubscriptionTier.PRO,
        name = "Professional Plan", 
        price = 39.99,
        billingCycle = BillingCycle.MONTHLY,
        dailyLimits = UsageLimits(
            messages = 300,
            tokens = 80000,
            images = 8,
            webSearches = 15,
            ttsMinutes = 30,
            sttMinutes = 15,
            visionAnalyses = 25
        ),
        allowedModels = listOf(
            AIModel.GPT_35_TURBO, AIModel.GEMINI, AIModel.DEEPSEEK,
            AIModel.GPT_41_MINI, AIModel.GEMINI_VOICE,
            AIModel.GPT_4O, AIModel.GPT_4_TURBO, AIModel.GPT_4O_SEARCH,
            AIModel.CLAUDE_SONNET_4,
            AIModel.GPT_4O_AUDIO, AIModel.GPT_4O_MINI_AUDIO
        ),
        estimatedDailyCost = 1.20, // 300 messages × $0.004 average cost
        profitMargin = 23.0, // $1.33/day revenue - $1.20/day cost = healthy
        maxDailyCostLimit = 2.00, // STRICT safety net
        features = listOf(
            "300 messages per day",
            "GPT-4o & Claude Sonnet 4 access",
            "Search-enabled models",
            "8 image generations daily",
            "15 web searches",
            "30 minutes text-to-speech",
            "Advanced vision analysis",
            "Priority support"
        )
    )
    
    val PREMIUM_PLAN = CostAwareSubscriptionPlan(
        tier = SubscriptionTier.PREMIUM,
        name = "Premium Plan",
        price = 79.99,
        billingCycle = BillingCycle.MONTHLY,
        dailyLimits = UsageLimits(
            messages = 500,
            tokens = 200000,
            images = 20,
            webSearches = 40,
            ttsMinutes = 60,
            sttMinutes = 30,
            visionAnalyses = 50
        ),
        allowedModels = listOf(
            AIModel.GPT_35_TURBO, AIModel.GEMINI, AIModel.DEEPSEEK,
            AIModel. GPT_41_MINI, AIModel.GEMINI_VOICE,
            AIModel.GPT_4O, AIModel.GPT_4_TURBO, AIModel.GPT_4O_SEARCH,
            AIModel.CLAUDE_SONNET_4,
            AIModel.O1, AIModel.O1_MINI, AIModel.O3_MINI,
            AIModel.GPT_4O_REALTIME,
            AIModel.OPENAI_REALTIME_VOICE, AIModel.DALL_E_3,
            AIModel.GPT_4O_AUDIO, AIModel.GPT_4O_MINI_AUDIO
        ),
        estimatedDailyCost = 2.50, // 500 messages × $0.005 average cost
        profitMargin = 25.0, // $2.67/day revenue - $2.50/day cost = good profit
        maxDailyCostLimit = 4.00, // STRICT safety net
        features = listOf(
            "500 messages per day",

            "Access to O1, O1-Mini, O3-Mini",
            "GPT-4o Realtime & Audio features",
            "DALL-E 3 image generation",
            "20 image generations daily", 
            "40 web searches",
            "60 minutes text-to-speech",
            "Realtime voice conversations",
            "Advanced AI features",
            "Premium support"
        )
    )
    
    val ULTRA_PREMIUM_PLAN = CostAwareSubscriptionPlan(
        tier = SubscriptionTier.ULTRA_PREMIUM,
        name = "Enterprise Max",
        price = 299.99,
        billingCycle = BillingCycle.MONTHLY,
        dailyLimits = UsageLimits(
            messages = 1000,
            tokens = 500000,
            images = 50,
            webSearches = 100,
            ttsMinutes = 200,
            sttMinutes = 100,
            visionAnalyses = 200
        ),
        allowedModels = AIModel.values().toList(), // All models including Claude Opus 4
        estimatedDailyCost = 8.00, // MUCH more realistic with limits
        profitMargin = 20.0, // $10.00/day revenue - $8.00/day cost = healthy profit
        maxDailyCostLimit = 15.00, // STRICT safety net
        features = listOf(
            "1000 messages per day",
            "ACCESS TO ALL MODELS",
            "Claude Opus 4: 3 uses/day",
            "All OpenAI models including O1",
            "Realtime Audio features", 
            "50 image generations daily",
            "100 web searches",
            "All premium features",
            "Dedicated support",
            "Enterprise integrations"
        )
    )
    
    val ALL_PLANS = listOf(
        FREE_PLAN,
        BASIC_PLAN, 
        PRO_PLAN,
        PREMIUM_PLAN,
        ULTRA_PREMIUM_PLAN
    )
    
    /**
     * Get plan by subscription tier
     */
    fun getPlanForTier(tier: SubscriptionTier): CostAwareSubscriptionPlan {
        return ALL_PLANS.first { it.tier == tier }
    }
    
    /**
     * Get plan by price point (useful for payment processing)
     */
    fun getPlanByPrice(price: Double, billingCycle: BillingCycle): CostAwareSubscriptionPlan? {
        return ALL_PLANS.find { it.price == price && it.billingCycle == billingCycle }
    }
    
    /**
     * Get upgrade options for current tier
     */
    fun getUpgradeOptions(currentTier: SubscriptionTier): List<CostAwareSubscriptionPlan> {
        return ALL_PLANS.filter { it.tier.level > currentTier.level }
    }
    
    /**
     * Calculate potential savings for yearly billing
     */
    fun calculateYearlySavings(monthlyPlan: CostAwareSubscriptionPlan): Double {
        val yearlyPrice = monthlyPlan.price * 10 // 2 months free
        val monthlyCost = monthlyPlan.price * 12
        return monthlyCost - yearlyPrice
    }
    
    /**
     * Get recommended plan based on usage patterns
     */
    fun getRecommendedPlan(
        averageDailyMessages: Int,
        averageDailyImages: Int,
        usesAdvancedFeatures: Boolean
    ): CostAwareSubscriptionPlan {
        return when {
            averageDailyMessages <= 10 -> FREE_PLAN
            averageDailyMessages <= 50 && !usesAdvancedFeatures -> BASIC_PLAN
            averageDailyMessages <= 200 -> PRO_PLAN
            averageDailyMessages <= 500 -> PREMIUM_PLAN
            else -> ULTRA_PREMIUM_PLAN
        }
    }
}