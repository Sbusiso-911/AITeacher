package com.playstudio.aiteacher.credits

/**
 * Simple model pricing table based on per-million token costs.
 */
object ModelPricing {
    data class Pricing(val input: Double, val output: Double)

    private val pricingMap = mapOf(
        "gpt-4o" to Pricing(input = 2.50, output = 10.00),
        "claude-sonnet-4" to Pricing(input = 3.00, output = 15.00),
        "gpt-4o-mini" to Pricing(input = 0.15, output = 0.60),
        "grok-4" to Pricing(input = 3.00, output = 15.00)
    )

    fun getPricing(modelName: String): Pricing? = pricingMap[modelName]
}
