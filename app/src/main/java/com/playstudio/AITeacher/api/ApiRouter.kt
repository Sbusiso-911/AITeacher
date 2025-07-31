package com.playstudio.aiteacher.api

import com.playstudio.aiteacher.pricing.AIModel

/**
 * API Router for handling different AI model providers and their endpoints
 */
object ApiRouter {
    
    /**
     * API configuration for different providers
     */
    data class ApiConfig(
        val baseUrl: String,
        val authHeaderName: String = "Authorization",
        val authHeaderPrefix: String = "Bearer",
        val additionalHeaders: Map<String, String> = emptyMap()
    )
    
    /**
     * Get API configuration for a specific model
     */
    fun getApiConfig(model: AIModel): ApiConfig {
        return when (model.provider) {
            "OpenAI" -> ApiConfig(
                baseUrl = "https://api.openai.com/v1",
                authHeaderName = "Authorization",
                authHeaderPrefix = "Bearer"
            )
            "DeepSeek" -> ApiConfig(
                baseUrl = "https://api.deepseek.com/v1",
                authHeaderName = "Authorization",
                authHeaderPrefix = "Bearer"
            )
            "Anthropic" -> ApiConfig(
                baseUrl = "https://api.anthropic.com",
                authHeaderName = "x-api-key",
                authHeaderPrefix = "",
                additionalHeaders = mapOf(
                    "anthropic-version" to "2023-06-01"
                )
            )
            "Google" -> ApiConfig(
                baseUrl = "https://generativelanguage.googleapis.com/v1beta",
                authHeaderName = "x-goog-api-key",
                authHeaderPrefix = ""
            )
            else -> ApiConfig(
                baseUrl = "https://api.openai.com/v1", // Default to OpenAI
                authHeaderName = "Authorization",
                authHeaderPrefix = "Bearer"
            )
        }
    }
    
    /**
     * Get chat completions endpoint for a model
     */
    fun getChatCompletionsUrl(model: AIModel): String {
        val config = getApiConfig(model)
        return when (model.provider) {
            "Google" -> "${config.baseUrl}/models/${model.modelId}:generateContent"
            else -> "${config.baseUrl}/chat/completions"
        }
    }
    
    /**
     * Get image generation endpoint for a model
     */
    fun getImageGenerationUrl(model: AIModel): String {
        val config = getApiConfig(model)
        return when (model.provider) {
            "OpenAI" -> "${config.baseUrl}/images/generations"
            else -> "${config.baseUrl}/images/generations"
        }
    }
    
    /**
     * Get audio transcription endpoint for a model
     */
    fun getAudioTranscriptionUrl(model: AIModel): String {
        val config = getApiConfig(model)
        return "${config.baseUrl}/audio/transcriptions"
    }
    
    /**
     * Get text-to-speech endpoint for a model
     */
    fun getTtsUrl(model: AIModel): String {
        val config = getApiConfig(model)
        return "${config.baseUrl}/audio/speech"
    }
    
    /**
     * Get authorization header for a model
     */
    fun getAuthHeader(model: AIModel, apiKey: String): Pair<String, String> {
        val config = getApiConfig(model)
        val headerValue = if (config.authHeaderPrefix.isNotEmpty()) {
            "${config.authHeaderPrefix} $apiKey"
        } else {
            apiKey
        }
        return Pair(config.authHeaderName, headerValue)
    }
    
    /**
     * Get additional headers for a model
     */
    fun getAdditionalHeaders(model: AIModel): Map<String, String> {
        val config = getApiConfig(model)
        return config.additionalHeaders
    }
    
    /**
     * Check if model supports a specific feature
     */
    fun supportsFeature(model: AIModel, feature: ApiFeature): Boolean {
        return when (feature) {
            ApiFeature.CHAT_COMPLETIONS -> true // All models support chat
            ApiFeature.IMAGE_GENERATION -> model.modelId.contains("dall-e", ignoreCase = true)
            ApiFeature.AUDIO_TRANSCRIPTION -> model.provider == "OpenAI"
            ApiFeature.TEXT_TO_SPEECH -> model.provider == "OpenAI"
            ApiFeature.STREAMING -> when (model.provider) {
                "OpenAI", "DeepSeek", "Anthropic" -> true
                else -> false
            }
        }
    }
}

/**
 * API features supported by different models
 */
enum class ApiFeature {
    CHAT_COMPLETIONS,
    IMAGE_GENERATION,
    AUDIO_TRANSCRIPTION,
    TEXT_TO_SPEECH,
    STREAMING
}