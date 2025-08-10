package com.playstudio.aiteacher.credits

/**
 * Simple model pricing table based on per-million token costs.
 */
import com.playstudio.aiteacher.pricing.AIModel

object ModelPricing {
    data class Pricing(val input: Double, val output: Double)

    /**
     * Retrieve pricing based on AIModel definition so all models are covered.
     */
    fun getPricing(modelName: String): Pricing? {
        val model = AIModel.fromModelId(modelName) ?: return null
        return Pricing(model.inputCostPer1M, model.outputCostPer1M)
    }
}