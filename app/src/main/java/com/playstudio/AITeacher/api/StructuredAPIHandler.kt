package com.playstudio.aiteacher.api

import android.util.Log
import com.google.ads.interactivemedia.v3.api.UiElement
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.playstudio.aiteacher.BuildConfig
import com.playstudio.aiteacher.models.*
import okio.BufferedSource
import com.playstudio.aiteacher.api.StructuredOutputSchemas
import com.playstudio.aiteacher.security.FirestoreKeyManager
import com.playstudio.aiteacher.security.SmartApiCaller
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * Enhanced API handler for OpenAI with Structured Outputs support
 * Provides educational content with guaranteed schema adherence
 */
class StructuredAPIHandler(private val okHttpClient: OkHttpClient) {

    private val gson = Gson()
    private val keyManager = FirestoreKeyManager.getInstance()
    private val smartApiCaller = SmartApiCaller()
    
    // Helper function to get API key with fallback
    private fun getApiKey(model: String): String? {
        return when {
            model.startsWith("claude") -> keyManager.getApiKeyWithFallback("anthropic")
            model.startsWith("grok") -> keyManager.getApiKeyWithFallback("grok")
            else -> keyManager.getApiKeyWithFallback("openai")
        }
    }
    
    // Helper function to build request with current API key
    private fun buildRequestWithCurrentKey(model: String, requestBody: RequestBody): Request {
        val apiKey = getApiKey(model)
        if (apiKey == null) {
            Log.e(TAG, "No API key available for model: $model")
            throw Exception("API key not available")
        }
        
        val builder = Request.Builder()
            .post(requestBody)
            .addHeader("Content-Type", "application/json")
            
        when {
            model.startsWith("claude") -> {
                builder.url(ANTHROPIC_BASE_URL)
                    .addHeader("x-api-key", apiKey)
                    .addHeader("anthropic-version", "2023-06-01")
            }
            model.startsWith("grok") -> {
                builder.url(XAI_BASE_URL)
                    .addHeader("Authorization", "Bearer $apiKey")
            }
            else -> {
                builder.url(OPENAI_BASE_URL)
                    .addHeader("Authorization", "Bearer $apiKey")
            }
        }
        
        return builder.build()
    }
    
    // Smart API call wrapper - automatically handles key refresh and retry
    private suspend fun executeApiCallWithRetry(
        requestBodyJson: JSONObject,
        model: String
    ): Response {
        val requestBodyString = requestBodyJson.toString()
        val requestBody = requestBodyString.toRequestBody("application/json".toMediaTypeOrNull())
        
        return smartApiCaller.executeWithSmartRetry(
            okHttpClient = okHttpClient,
            requestBuilder = { buildRequestWithCurrentKey(model, requestBody) },
            onKeyRefresh = { buildRequestWithCurrentKey(model, requestBody) }
        )
    }

    companion object {
        private const val TAG = "StructuredAPIHandler"
        private const val OPENAI_BASE_URL = "https://api.openai.com/v1/chat/completions"
        private const val ANTHROPIC_BASE_URL = "https://api.anthropic.com/v1/messages"
        private const val XAI_BASE_URL = "https://api.x.ai/v1/chat/completions"

        // Supported models for structured outputs
        private val STRUCTURED_MODELS = setOf(
            "gpt-4o",
            "gpt-4o-2024-08-06",
            "gpt-4o-mini",
            "gpt-4o-mini-2024-07-18",
            "claude-sonnet-4-20250514",
            "claude-opus-4-20250514",
            "grok-4"
        )
    }

    /**
     * NEW: Structured streaming response following OpenAI guidelines
     * Streams content with proper structured output at completion
     */
    fun getStructuredStreamingResponse(
        userMessage: String,
        chatHistory: List<Pair<String, String>> = emptyList(),
        model: String = "gpt-4o-2024-08-06" // Use model that supports structured outputs
    ): Flow<SimpleStreamingUpdate> = flow {
        try {
            // Start with progress indication
            emit(SimpleStreamingUpdate.Progress("Thinking..."))
            
            // Check if this should use structured output (math problems)
            val isMathProblem = isMathRelated(userMessage)
            Log.d(TAG, "🔧 Model for request: '$model', isMathProblem: $isMathProblem")
            
            val request = if (isMathProblem) {
                Log.d(TAG, "🧮 Using structured math tutor request with model: $model")
                // Use structured output for math problems following OpenAI docs
                buildMathTutorRequest(userMessage, chatHistory, model)
            } else {
                Log.d(TAG, "💬 Using simple streaming request with model: $model")
                // Use streaming for other content
                buildSimpleStreamingRequest(userMessage, chatHistory, model)
            }
            
            emit(SimpleStreamingUpdate.Progress("Getting response..."))
            
            // Execute the call
            val call = okHttpClient.newCall(request)
            val response = call.execute()
            
            if (response.isSuccessful) {
                val responseBody = response.body
                responseBody?.let { body ->
                    if (isMathProblem) {
                        // Handle structured math response
                        val responseContent = body.string()
                        val structuredContent = parseMathTutorResponse(responseContent)
                        emit(SimpleStreamingUpdate.Complete(structuredContent))
                    } else {
                        // Handle streaming response - Support both Claude and OpenAI formats
                        val source = body.source()
                        var accumulatedContent = ""
                        val isClaudeModel = isMathProblem == false && model.startsWith("claude")
                        
                        emit(SimpleStreamingUpdate.Progress("Receiving content..."))
                        
                        while (!source.exhausted()) {
                            val line = source.readUtf8Line()
                            
                            if (isClaudeModel) {
                                // Handle Claude 4 SSE format
                                if (line != null && line.startsWith("data: ")) {
                                    val jsonData = line.substring(6).trim()
                                    
                                    if (jsonData.isNotEmpty()) {
                                        try {
                                            val jsonObject = JSONObject(jsonData)
                                            val eventType = jsonObject.optString("type", "")
                                            
                                            when (eventType) {
                                                "content_block_delta" -> {
                                                    val delta = jsonObject.optJSONObject("delta")
                                                    if (delta != null && delta.optString("type") == "text_delta") {
                                                        val content = delta.optString("text", "")
                                                        if (content.isNotEmpty()) {
                                                            accumulatedContent += content
                                                            
                                                            // Emit content updates for better UX
                                                            if (accumulatedContent.length % 200 == 0) {
                                                                emit(SimpleStreamingUpdate.ContentChunk(accumulatedContent))
                                                            }
                                                        }
                                                    }
                                                }
                                                "message_delta" -> {
                                                    val delta = jsonObject.optJSONObject("delta")
                                                    if (delta != null) {
                                                        val stopReason = delta.optString("stop_reason", "")
                                                        if (stopReason == "end_turn" || stopReason == "refusal") {
                                                            Log.d(TAG, "🏁 Claude streaming completed with stop_reason: $stopReason")
                                                            if (stopReason == "refusal") {
                                                                emit(SimpleStreamingUpdate.Error("AI declined to respond due to safety guidelines"))
                                                                return@flow
                                                            }
                                                            break
                                                        }
                                                    }
                                                }
                                                "message_stop" -> {
                                                    Log.d(TAG, "🏁 Claude streaming completed with message_stop")
                                                    break
                                                }
                                            }
                                        } catch (e: Exception) {
                                            Log.w(TAG, "Error parsing Claude streaming chunk: $jsonData", e)
                                        }
                                    }
                                } else if (line != null && line.startsWith("event: ")) {
                                    // Handle Claude event lines (informational only)
                                    val eventName = line.substring(7).trim()
                                    Log.d(TAG, "Claude SSE event: $eventName")
                                }
                            } else {
                                // Handle OpenAI SSE format
                                if (line != null && line.startsWith("data: ")) {
                                    val jsonData = line.substring(6).trim()
                                    
                                    if (jsonData == "[DONE]") {
                                        Log.d(TAG, "🏁 OpenAI streaming completed with [DONE] marker")
                                        break
                                    }
                                    
                                    if (jsonData.isNotEmpty()) {
                                        try {
                                            val jsonObject = JSONObject(jsonData)
                                            val choices = jsonObject.getJSONArray("choices")
                                            if (choices.length() > 0) {
                                                val choice = choices.getJSONObject(0)
                                                val delta = choice.getJSONObject("delta")
                                                if (delta.has("content")) {
                                                    val content = delta.getString("content")
                                                    accumulatedContent += content
                                                    
                                                    // Emit content updates more frequently for better UX
                                                    if (accumulatedContent.length % 200 == 0) {
                                                        emit(SimpleStreamingUpdate.ContentChunk(accumulatedContent))
                                                    }
                                                }
                                                
                                                // Check for completion
                                                if (choice.has("finish_reason")) {
                                                    val finishReason = choice.optString("finish_reason", "")
                                                    if (finishReason == "stop") {
                                                        Log.d(TAG, "🏁 OpenAI streaming completed with finish_reason: stop")
                                                        break
                                                    }
                                                }
                                            }
                                        } catch (e: Exception) {
                                            Log.w(TAG, "Error parsing OpenAI streaming chunk: $jsonData", e)
                                        }
                                    }
                                }
                            }
                        }
                        
                        // Emit final content
                        Log.d(TAG, "✅ Streaming complete. Content length: ${accumulatedContent.length}")
                        
                        if (accumulatedContent.isBlank()) {
                            Log.e(TAG, "❌ No content received from streaming.")
                            emit(SimpleStreamingUpdate.Error("No content received from API"))
                        } else {
                            Log.d(TAG, "✅ Emitting SimpleStreamingUpdate.Complete")
                            emit(SimpleStreamingUpdate.Complete(accumulatedContent))
                        }
                    }
                } ?: emit(SimpleStreamingUpdate.Error("Empty response body"))
            } else {
                val errorBody = response.body?.string() ?: "Unknown error"
                Log.e(TAG, "Streaming API Error: ${response.code} - $errorBody")
                emit(SimpleStreamingUpdate.Error("API Error: ${response.code} - ${response.message}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception in simple streaming", e)
            emit(SimpleStreamingUpdate.Error("Network error: ${e.message}"))
        }
    }.flowOn(Dispatchers.IO)
    
    /**
     * FALLBACK: Non-streaming version for when structured output is specifically needed
     */
    suspend fun getLearningContent(
        userMessage: String,
        chatHistory: List<Pair<String, String>> = emptyList(),
        model: String = "gpt-4o-2024-08-06"
    ): Result<LearningContent> = withContext(Dispatchers.IO) {

        return@withContext try {
            val request = buildLearningContentRequest(
                userMessage = userMessage,
                chatHistory = chatHistory,
                model = model
            )

            val response = okHttpClient.newCall(request).execute()

            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                responseBody?.let { body ->
                    parseLearningContentResponse(body)
                } ?: Result.failure(Exception("Empty response body"))
            } else {
                val errorBody = response.body?.string() ?: "Unknown error"
                Log.e(TAG, "Learning Content API Error: ${response.code} - $errorBody")
                Result.failure(Exception("API Error: ${response.code} - ${response.message}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception in learning content API call", e)
            Result.failure(e)
        }
    }

    /**
     * NEW: Get interactive session for dedicated Q&A mode
     * Separate from educational content for clean knowledge testing
     */
    suspend fun getInteractiveSession(
        topicTitle: String,
        sessionType: SessionType = SessionType.KNOWLEDGE_CHECK,
        chatHistory: List<Pair<String, String>> = emptyList(),
        model: String = "gpt-4o-2024-08-06"
    ): Result<InteractiveSession> = withContext(Dispatchers.IO) {

        return@withContext try {
            val request = buildInteractiveSessionRequest(
                topicTitle = topicTitle,
                sessionType = sessionType,
                chatHistory = chatHistory,
                model = model
            )

            val response = okHttpClient.newCall(request).execute()

            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                responseBody?.let { body ->
                    parseInteractiveSessionResponse(body)
                } ?: Result.failure(Exception("Empty response body"))
            } else {
                val errorBody = response.body?.string() ?: "Unknown error"
                Log.e(TAG, "Interactive Session API Error: ${response.code} - $errorBody")
                Result.failure(Exception("API Error: ${response.code} - ${response.message}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception in interactive session API call", e)
            Result.failure(e)
        }
    }

    /**
     * LEGACY: Send a user message and get a structured educational response
     * Keep for backward compatibility during transition
     */
    suspend fun getStructuredEducationalResponse(
        userMessage: String,
        chatHistory: List<Pair<String, String>> = emptyList(),
        model: String = "gpt-4o-2024-08-06",
        requestQuiz: Boolean = false,
        requestStepByStep: Boolean = false
    ): Result<EducationalResponse> = withContext(Dispatchers.IO) {

        return@withContext try {
            // Determine response type based on request
            val responseType = when {
                requestQuiz -> ResponseType.QUIZ
                requestStepByStep -> ResponseType.STEP_BY_STEP
                isCodeRelated(userMessage) -> ResponseType.CODE_TUTORIAL
                isMathRelated(userMessage) -> ResponseType.STEP_BY_STEP
                else -> ResponseType.EXPLANATION
            }

            // Build the request
            val request = buildStructuredRequest(
                userMessage = userMessage,
                chatHistory = chatHistory,
                model = model,
                responseType = responseType
            )

            // Execute the request
            val response = okHttpClient.newCall(request).execute()

            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                responseBody?.let { body ->
                    parseStructuredResponse(body)
                } ?: Result.failure(Exception("Empty response body"))
            } else {
                val errorBody = response.body?.string() ?: "Unknown error"
                Log.e(TAG, "API Error: ${response.code} - $errorBody")
                Result.failure(Exception("API Error: ${response.code} - ${response.message}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception in structured API call", e)
            Result.failure(e)
        }
    }

    /**
     * NEW: Get simplified security analysis with clear problem-solution format
     */
    suspend fun getSecurityAnalysis(
        userMessage: String,
        chatHistory: List<Pair<String, String>> = emptyList(),
        model: String = "gpt-4o-2024-08-06",
        analysisType: SecurityAnalysisType = SecurityAnalysisType.ACCOUNT_SECURITY
    ): Result<SecurityAnalysisResponse> = withContext(Dispatchers.IO) {

        return@withContext try {
            // Build the security analysis request
            val request = buildSecurityAnalysisRequest(
                userMessage = userMessage,
                chatHistory = chatHistory,
                model = model,
                analysisType = analysisType
            )

            // Execute the request
            val response = okHttpClient.newCall(request).execute()

            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                responseBody?.let { body ->
                    parseSecurityAnalysisResponse(body)
                } ?: Result.failure(Exception("Empty response body"))
            } else {
                val errorBody = response.body?.string() ?: "Unknown error"
                Log.e(TAG, "Security Analysis API Error: ${response.code} - $errorBody")
                Result.failure(Exception("API Error: ${response.code} - ${response.message}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception in security analysis API call", e)
            Result.failure(e)
        }
    }

    /**
     * Get a quick explanation for simple questions
     */
    suspend fun getQuickExplanation(
        userMessage: String,
        model: String = "gpt-4o-mini"
    ): Result<QuickExplanationResponse> = withContext(Dispatchers.IO) {

        return@withContext try {
            val request = buildQuickExplanationRequest(userMessage, model)
            val response = okHttpClient.newCall(request).execute()

            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                responseBody?.let { body ->
                    parseQuickExplanationResponse(body)
                } ?: Result.failure(Exception("Empty response body"))
            } else {
                Result.failure(Exception("API Error: ${response.code} - ${response.message}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get structured math solution
     */
    suspend fun getMathSolution(
        problem: String,
        model: String = "gpt-4o"
    ): Result<MathSolutionResponse> = withContext(Dispatchers.IO) {

        return@withContext try {
            val request = buildMathSolutionRequest(problem, model)
            val response = okHttpClient.newCall(request).execute()

            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                responseBody?.let { body ->
                    parseMathSolutionResponse(body)
                } ?: Result.failure(Exception("Empty response body"))
            } else {
                Result.failure(Exception("API Error: ${response.code} - ${response.message}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Generate UI structure from a user description
     */
    suspend fun generateUi(
        description: String,
        model: String = "gpt-4o-2024-08-06"
    ): Result<UiResponse> = withContext(Dispatchers.IO) {
        return@withContext try {
            val request = buildUiRequest(description, model)
            val response = okHttpClient.newCall(request).execute()

            if (response.isSuccessful) {
                val body = response.body?.string()
                body?.let { parseUiResponse(it) } ?: Result.failure(Exception("Empty response body"))
            } else {
                Result.failure(Exception("API Error: ${response.code} - ${response.message}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun buildStructuredRequest(
        userMessage: String,
        chatHistory: List<Pair<String, String>>,
        model: String,
        responseType: ResponseType
    ): Request {

        // Verify model supports structured outputs
        if (!STRUCTURED_MODELS.contains(model)) {
            throw IllegalArgumentException("Model $model does not support structured outputs")
        }

        // Build request body with proper Claude/OpenAI format
        val requestBodyJson = JSONObject()

        if (model.startsWith("claude")) {
            // Claude/Anthropic API format
            val claudeMessages = JSONArray()
            
            // Add chat history (user/assistant only)
            chatHistory.forEach { (role, content) ->
                claudeMessages.put(JSONObject().apply {
                    put("role", if (role == "user") "user" else "assistant")
                    put("content", content)
                })
            }

            // Add current user message with enhanced prompt
            val enhancedPrompt = enhanceUserPrompt(userMessage, responseType)
            claudeMessages.put(JSONObject().apply {
                put("role", "user")
                put("content", enhancedPrompt)
            })

            requestBodyJson.apply {
                put("model", model)
                put("system", StructuredOutputSchemas.getLearningContentSystemPrompt())
                put("messages", claudeMessages)
                put("max_tokens", 4000)
                put("temperature", 0.7)
            }
        } else {
            // OpenAI API format - keep existing structure
            val messagesArray = JSONArray()

            // Add system prompt
            messagesArray.put(JSONObject().apply {
                put("role", "system")
                put("content", StructuredOutputSchemas.getLearningContentSystemPrompt())
            })

            // Add chat history
            chatHistory.forEach { (role, content) ->
                messagesArray.put(JSONObject().apply {
                    put("role", if (role == "user") "user" else "assistant")
                    put("content", content)
                })
            }

            // Add current user message with enhanced prompt
            val enhancedPrompt = enhanceUserPrompt(userMessage, responseType)
            messagesArray.put(JSONObject().apply {
                put("role", "user")
                put("content", enhancedPrompt)
            })

            // Build request body with structured output schema
            val responseFormatSchema = StructuredOutputSchemas.getLearningContentSchema()
            Log.d(TAG, "Response format schema: $responseFormatSchema")

            // Convert Gson JsonObject to org.json JSONObject
            val responseFormatJson = JSONObject(responseFormatSchema.toString())

            requestBodyJson.apply {
                put("model", model)
                put("messages", messagesArray)
                put("response_format", responseFormatJson)
                put("temperature", 0.7)
                put("max_completion_tokens", 4000)
            }
        }

        val requestBodyString = requestBodyJson.toString()
        Log.d(TAG, "Request body: $requestBodyString")
        val requestBody = requestBodyString.toRequestBody("application/json".toMediaTypeOrNull())

        val builder = Request.Builder()
            .post(requestBody)
            .addHeader("Content-Type", "application/json")

        val apiKey = getApiKey(model)
        if (apiKey == null) {
            Log.e(TAG, "No API key available for model: $model")
            throw Exception("API key not available")
        }
        
        if (model.startsWith("claude")) {
            builder.url(ANTHROPIC_BASE_URL)
                .addHeader("x-api-key", apiKey)
                .addHeader("anthropic-version", "2023-06-01")
        } else if (model.startsWith("grok")) {
            builder.url(XAI_BASE_URL)
                .addHeader("Authorization", "Bearer $apiKey")
        } else {
            builder.url(OPENAI_BASE_URL)
                .addHeader("Authorization", "Bearer $apiKey")
        }

        return builder.build()
    }

    private fun buildQuickExplanationRequest(userMessage: String, model: String): Request {
        val requestBodyJson = JSONObject()

        if (model.startsWith("claude")) {
            // Claude/Anthropic API format
            val claudeMessages = JSONArray()
            
            claudeMessages.put(JSONObject().apply {
                put("role", "user")
                put("content", userMessage)
            })

            requestBodyJson.apply {
                put("model", model)
                put("system", "You are AI Teacher. Provide clear, concise explanations. Use the structured format for consistency.")
                put("messages", claudeMessages)
                put("temperature", 0.5)
                put("max_tokens", 1000)
            }
        } else {
            // OpenAI API format
            val messagesArray = JSONArray()

            messagesArray.put(JSONObject().apply {
                put("role", "system")
                put("content", "You are AI Teacher. Provide clear, concise explanations. Use the structured format for consistency.")
            })

            messagesArray.put(JSONObject().apply {
                put("role", "user")
                put("content", userMessage)
            })

            requestBodyJson.apply {
                put("model", model)
                put("messages", messagesArray)
                put("response_format", JSONObject(StructuredOutputSchemas.getQuickExplanationSchema().toString()))
                put("temperature", 0.5)
                put("max_completion_tokens", 1000)
            }
        }

        val requestBody = requestBodyJson.toString().toRequestBody("application/json".toMediaTypeOrNull())

        val builder = Request.Builder()
            .post(requestBody)
            .addHeader("Content-Type", "application/json")

        if (model.startsWith("claude")) {
            builder.url(ANTHROPIC_BASE_URL)
                .addHeader("x-api-key", BuildConfig.ANTHROPIC_API_KEY)
                .addHeader("anthropic-version", "2023-06-01")
        } else if (model.startsWith("grok")) {
            builder.url(XAI_BASE_URL)
                .addHeader("Authorization", "Bearer ${BuildConfig.GROK_API_KEY}")
        } else {
            builder.url(OPENAI_BASE_URL)
                .addHeader("Authorization", "Bearer ${getApiKey("openai")}")
        }

        return builder.build()
    }

    private fun buildMathSolutionRequest(problem: String, model: String): Request {
        val requestBodyJson = JSONObject()

        if (model.startsWith("claude")) {
            // Claude/Anthropic API format
            val claudeMessages = JSONArray()
            
            claudeMessages.put(JSONObject().apply {
                put("role", "user")
                put("content", "Solve this step by step: $problem")
            })

            requestBodyJson.apply {
                put("model", model)
                put("system", "You are AI Teacher specialized in mathematics. Provide step-by-step solutions with clear reasoning for each step.")
                put("messages", claudeMessages)
                put("temperature", 0.3)
                put("max_tokens", 2000)
            }
        } else {
            // OpenAI API format
            val messagesArray = JSONArray()

            messagesArray.put(JSONObject().apply {
                put("role", "system")
                put("content", "You are AI Teacher specialized in mathematics. Provide step-by-step solutions with clear reasoning for each step.")
            })

            messagesArray.put(JSONObject().apply {
                put("role", "user")
                put("content", "Solve this step by step: $problem")
            })

            requestBodyJson.apply {
                put("model", model)
                put("messages", messagesArray)
                put("response_format", JSONObject(StructuredOutputSchemas.getMathSolutionSchema().toString()))
                put("temperature", 0.3)
                put("max_completion_tokens", 2000)
            }
        }

        val requestBody = requestBodyJson.toString().toRequestBody("application/json".toMediaTypeOrNull())

        val builder = Request.Builder()
            .post(requestBody)
            .addHeader("Content-Type", "application/json")

        if (model.startsWith("claude")) {
            builder.url(ANTHROPIC_BASE_URL)
                .addHeader("x-api-key", BuildConfig.ANTHROPIC_API_KEY)
                .addHeader("anthropic-version", "2023-06-01")
        } else if (model.startsWith("grok")) {
            builder.url(XAI_BASE_URL)
                .addHeader("Authorization", "Bearer ${BuildConfig.GROK_API_KEY}")
        } else {
            builder.url(OPENAI_BASE_URL)
                .addHeader("Authorization", "Bearer ${getApiKey("openai")}")
        }

        return builder.build()
    }

    private fun buildUiRequest(description: String, model: String): Request {
        val requestBodyJson = JSONObject()

        if (model.startsWith("claude")) {
            // Claude/Anthropic API format
            val claudeMessages = JSONArray()
            
            claudeMessages.put(JSONObject().apply {
                put("role", "user")
                put("content", description)
            })

            requestBodyJson.apply {
                put("model", model)
                put("system", "You are a UI generator AI. Convert the user input into a UI.")
                put("messages", claudeMessages)
                put("temperature", 0.3)
                put("max_tokens", 1000)
            }
        } else {
            // OpenAI API format
            val messagesArray = JSONArray()

            messagesArray.put(JSONObject().apply {
                put("role", "system")
                put("content", "You are a UI generator AI. Convert the user input into a UI.")
            })

            messagesArray.put(JSONObject().apply {
                put("role", "user")
                put("content", description)
            })

            requestBodyJson.apply {
                put("model", model)
                put("messages", messagesArray)
                put("response_format", JSONObject(StructuredOutputSchemas.getUiSchema().toString()))
                put("temperature", 0.3)
                put("max_completion_tokens", 1000)
            }
        }

        val requestBody = requestBodyJson.toString().toRequestBody("application/json".toMediaTypeOrNull())

        val builder = Request.Builder()
            .post(requestBody)
            .addHeader("Content-Type", "application/json")

        if (model.startsWith("claude")) {
            builder.url(ANTHROPIC_BASE_URL)
                .addHeader("x-api-key", BuildConfig.ANTHROPIC_API_KEY)
                .addHeader("anthropic-version", "2023-06-01")
        } else if (model.startsWith("grok")) {
            builder.url(XAI_BASE_URL)
                .addHeader("Authorization", "Bearer ${BuildConfig.GROK_API_KEY}")
        } else {
            builder.url(OPENAI_BASE_URL)
                .addHeader("Authorization", "Bearer ${getApiKey("openai")}")
        }

        return builder.build()
    }

    private fun parseStructuredResponse(responseBody: String): Result<EducationalResponse> {
        return try {
            val jsonResponse = JSONObject(responseBody)
            val message = if (jsonResponse.has("choices")) {
                val choices = jsonResponse.getJSONArray("choices")
                if (choices.length() == 0) {
                    return Result.failure(Exception("No choices in response"))
                }
                choices.getJSONObject(0).getJSONObject("message")
            } else {
                // Anthropic format
                val textBuilder = StringBuilder()
                val contentArray = jsonResponse.getJSONArray("content")
                for (i in 0 until contentArray.length()) {
                    val block = contentArray.getJSONObject(i)
                    if (block.optString("type") == "text") {
                        textBuilder.append(block.optString("text"))
                    }
                }
                JSONObject().apply {
                    put("content", textBuilder.toString())
                    put("role", jsonResponse.optString("role", "assistant"))
                    if (jsonResponse.has("refusal")) {
                        put("refusal", jsonResponse.getString("refusal"))
                    }
                }
            }

            // Check for refusal
            if (message.has("refusal") && !message.isNull("refusal")) {
                val refusal = message.getString("refusal")
                return Result.failure(Exception("AI refused to respond: $refusal"))
            }

            val content = message.getString("content")
            val educationalResponse = gson.fromJson(content, EducationalResponse::class.java)

            Result.success(educationalResponse)
        } catch (e: JsonSyntaxException) {
            Log.e(TAG, "Failed to parse structured response", e)
            Result.failure(Exception("Invalid response format: ${e.message}"))
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing response", e)
            Result.failure(e)
        }
    }

    private fun parseQuickExplanationResponse(responseBody: String): Result<QuickExplanationResponse> {
        return try {
            val jsonResponse = JSONObject(responseBody)
            val message = if (jsonResponse.has("choices")) {
                jsonResponse.getJSONArray("choices").getJSONObject(0).getJSONObject("message")
            } else {
                val textBuilder = StringBuilder()
                val contentArray = jsonResponse.getJSONArray("content")
                for (i in 0 until contentArray.length()) {
                    val block = contentArray.getJSONObject(i)
                    if (block.optString("type") == "text") textBuilder.append(block.optString("text"))
                }
                JSONObject().apply { put("content", textBuilder.toString()) }
            }

            if (message.has("refusal") && !message.isNull("refusal")) {
                val refusal = message.getString("refusal")
                return Result.failure(Exception("AI refused to respond: $refusal"))
            }

            val content = message.getString("content")
            val quickResponse = gson.fromJson(content, QuickExplanationResponse::class.java)
            Result.success(quickResponse)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseMathSolutionResponse(responseBody: String): Result<MathSolutionResponse> {
        return try {
            val jsonResponse = JSONObject(responseBody)
            val message = if (jsonResponse.has("choices")) {
                jsonResponse.getJSONArray("choices").getJSONObject(0).getJSONObject("message")
            } else {
                val textBuilder = StringBuilder()
                val contentArray = jsonResponse.getJSONArray("content")
                for (i in 0 until contentArray.length()) {
                    val block = contentArray.getJSONObject(i)
                    if (block.optString("type") == "text") textBuilder.append(block.optString("text"))
                }
                JSONObject().apply { put("content", textBuilder.toString()) }
            }

            if (message.has("refusal") && !message.isNull("refusal")) {
                val refusal = message.getString("refusal")
                return Result.failure(Exception("AI refused to respond: $refusal"))
            }

            val content = message.getString("content")
            val mathResponse = gson.fromJson(content, MathSolutionResponse::class.java)
            Result.success(mathResponse)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseUiResponse(responseBody: String): Result<UiResponse> {
        return try {
            val json = JSONObject(responseBody)
            val message = if (json.has("choices")) {
                json.getJSONArray("choices").getJSONObject(0).getJSONObject("message")
            } else {
                val textBuilder = StringBuilder()
                val contentArray = json.getJSONArray("content")
                for (i in 0 until contentArray.length()) {
                    val block = contentArray.getJSONObject(i)
                    if (block.optString("type") == "text") textBuilder.append(block.optString("text"))
                }
                JSONObject().apply { put("content", textBuilder.toString()) }
            }

            if (message.has("refusal") && !message.isNull("refusal")) {
                val refusal = message.getString("refusal")
                return Result.failure(Exception("AI refused to respond: $refusal"))
            }

            val content = message.getString("content")
            val result = gson.fromJson(content, UiResponse::class.java)
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * NEW: Build security analysis request with simplified schema
     */
    private fun buildSecurityAnalysisRequest(
        userMessage: String,
        chatHistory: List<Pair<String, String>>,
        model: String,
        analysisType: SecurityAnalysisType
    ): Request {

        // Verify model supports structured outputs
        if (!STRUCTURED_MODELS.contains(model)) {
            throw IllegalArgumentException("Model $model does not support structured outputs")
        }

        // Build request body with proper Claude/OpenAI format
        val requestBodyJson = JSONObject()

        if (model.startsWith("claude")) {
            // Claude/Anthropic API format
            val claudeMessages = JSONArray()
            
            // Add chat history
            chatHistory.forEach { (role, content) ->
                claudeMessages.put(JSONObject().apply {
                    put("role", if (role == "user") "user" else "assistant")
                    put("content", content)
                })
            }

            // Add current user message with security analysis enhancement
            val enhancedPrompt = enhanceSecurityAnalysisPrompt(userMessage, analysisType)
            claudeMessages.put(JSONObject().apply {
                put("role", "user")
                put("content", enhancedPrompt)
            })

            requestBodyJson.apply {
                put("model", model)
                put("system", StructuredOutputSchemas.getSecurityAnalysisSystemPrompt())
                put("messages", claudeMessages)
                put("temperature", 0.3) // Lower temperature for more focused security analysis
                put("max_tokens", 2000)
            }
        } else {
            // OpenAI API format
            val messagesArray = JSONArray()

            // Add security analysis system prompt
            messagesArray.put(JSONObject().apply {
                put("role", "system")
                put("content", StructuredOutputSchemas.getSecurityAnalysisSystemPrompt())
            })

            // Add chat history
            chatHistory.forEach { (role, content) ->
                messagesArray.put(JSONObject().apply {
                    put("role", if (role == "user") "user" else "assistant")
                    put("content", content)
                })
            }

            // Add current user message with security analysis enhancement
            val enhancedPrompt = enhanceSecurityAnalysisPrompt(userMessage, analysisType)
            messagesArray.put(JSONObject().apply {
                put("role", "user")
                put("content", enhancedPrompt)
            })

            // Build request body with security analysis schema
            val responseFormatSchema = StructuredOutputSchemas.getSecurityAnalysisSchema()
            Log.d(TAG, "Security analysis schema: $responseFormatSchema")

            // Convert Gson JsonObject to org.json JSONObject
            val responseFormatJson = JSONObject(responseFormatSchema.toString())

            requestBodyJson.apply {
                put("model", model)
                put("messages", messagesArray)
                put("response_format", responseFormatJson)
                put("temperature", 0.3) // Lower temperature for more focused security analysis
                put("max_tokens", 2000)
            }
        }

        val requestBody = requestBodyJson.toString()
            .toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())

        val builder = Request.Builder()
            .post(requestBody)
            .addHeader("Content-Type", "application/json")
            
        if (model.startsWith("claude")) {
            builder.url(ANTHROPIC_BASE_URL)
                .addHeader("x-api-key", BuildConfig.ANTHROPIC_API_KEY)
                .addHeader("anthropic-version", "2023-06-01")
        } else {
            builder.url(OPENAI_BASE_URL)
                .addHeader("Authorization", "Bearer ${getApiKey("openai")}")
        }
        
        return builder.build()
    }

    /**
     * NEW: Parse security analysis response
     */
    private fun parseSecurityAnalysisResponse(responseBody: String): Result<SecurityAnalysisResponse> {
        return try {
            val jsonResponse = JSONObject(responseBody)
            val message = if (jsonResponse.has("choices")) {
                val choices = jsonResponse.getJSONArray("choices")
                if (choices.length() == 0) {
                    return Result.failure(Exception("No choices in response"))
                }
                choices.getJSONObject(0).getJSONObject("message")
            } else {
                // Anthropic format
                val textBuilder = StringBuilder()
                val contentArray = jsonResponse.getJSONArray("content")
                for (i in 0 until contentArray.length()) {
                    val block = contentArray.getJSONObject(i)
                    if (block.optString("type") == "text") {
                        textBuilder.append(block.optString("text"))
                    }
                }
                JSONObject().apply {
                    put("content", textBuilder.toString())
                    put("role", jsonResponse.optString("role", "assistant"))
                    if (jsonResponse.has("refusal")) {
                        put("refusal", jsonResponse.getString("refusal"))
                    }
                }
            }

            // Check for refusal
            if (message.has("refusal") && !message.isNull("refusal")) {
                val refusal = message.getString("refusal")
                return Result.failure(Exception("AI refused to respond: $refusal"))
            }

            val content = message.getString("content")
            val securityAnalysisResponse = gson.fromJson(content, SecurityAnalysisResponse::class.java)

            Result.success(securityAnalysisResponse)
        } catch (e: JsonSyntaxException) {
            Log.e(TAG, "Failed to parse security analysis response", e)
            Result.failure(Exception("Invalid security analysis format: ${e.message}"))
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing security analysis response", e)
            Result.failure(e)
        }
    }

    /**
     * NEW: Enhance user prompt for security analysis
     */
    private fun enhanceSecurityAnalysisPrompt(userMessage: String, analysisType: SecurityAnalysisType): String {
        val analysisContext = when (analysisType) {
            SecurityAnalysisType.ACCOUNT_SECURITY -> "focusing on account security, authentication, and access controls"
            SecurityAnalysisType.DATA_PROTECTION -> "focusing on data protection, encryption, and privacy measures"
            SecurityAnalysisType.NETWORK_SECURITY -> "focusing on network security, firewalls, and communication protection"
            SecurityAnalysisType.SYSTEM_VULNERABILITY -> "focusing on system vulnerabilities, patches, and hardening"
        }

        return """
        $userMessage

        Please provide a security analysis $analysisContext.

        Use this exact format:
        1. THE PROBLEM: What was broken or vulnerable?
        2. THE SOLUTION: What did we fix or implement?
        3. THE RESULT: What works now (before vs after)?
        4. RECOMMENDED ACTION: One clear action (if needed)

        Make it so simple that anyone can understand "Oh, I get it!" - no technical jargon.
        """.trimIndent()
    }

    /**
     * NEW: Build learning content request with comprehensive education schema
     */
    private fun buildLearningContentRequest(
        userMessage: String,
        chatHistory: List<Pair<String, String>>,
        model: String
    ): Request {
        
        // Verify model supports structured outputs
        if (!STRUCTURED_MODELS.contains(model)) {
            throw IllegalArgumentException("Model $model does not support structured outputs")
        }

        val requestBodyJson = JSONObject()

        if (model.startsWith("claude")) {
            // Claude/Anthropic API format
            val claudeMessages = JSONArray()

            // Add chat history
            chatHistory.forEach { (role, content) ->
                claudeMessages.put(JSONObject().apply {
                    put("role", if (role == "user") "user" else "assistant")
                    put("content", content)
                })
            }

            // Add current user message with learning enhancement
            val enhancedPrompt = enhanceLearningPrompt(userMessage)
            claudeMessages.put(JSONObject().apply {
                put("role", "user")
                put("content", enhancedPrompt)
            })

            requestBodyJson.apply {
                put("model", model)
                put("system", StructuredOutputSchemas.getLearningContentSystemPrompt())
                put("messages", claudeMessages)
                put("temperature", 0.4) // Balanced temperature for engaging yet accurate content
                put("max_tokens", 4000) // More tokens for comprehensive content
            }
        } else {
            // OpenAI API format
            val messagesArray = JSONArray()

            // Add learning content system prompt
            messagesArray.put(JSONObject().apply {
                put("role", "system")
                put("content", StructuredOutputSchemas.getLearningContentSystemPrompt())
            })

            // Add chat history
            chatHistory.forEach { (role, content) ->
                messagesArray.put(JSONObject().apply {
                    put("role", if (role == "user") "user" else "assistant")
                    put("content", content)
                })
            }

            // Add current user message with learning enhancement
            val enhancedPrompt = enhanceLearningPrompt(userMessage)
            messagesArray.put(JSONObject().apply {
                put("role", "user")
                put("content", enhancedPrompt)
            })

            // Build request body with learning content schema
            val responseFormatSchema = StructuredOutputSchemas.getLearningContentSchema()
            Log.d(TAG, "Learning content schema: $responseFormatSchema")

            val responseFormatJson = JSONObject(responseFormatSchema.toString())

            requestBodyJson.apply {
                put("model", model)
                put("messages", messagesArray)
                put("response_format", responseFormatJson)
                put("temperature", 0.4) // Balanced temperature for engaging yet accurate content
                put("max_tokens", 4000) // More tokens for comprehensive content
            }
        }

        val requestBody = requestBodyJson.toString()
            .toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())

        val builder = Request.Builder()
            .post(requestBody)
            .addHeader("Content-Type", "application/json")
            
        if (model.startsWith("claude")) {
            builder.url(ANTHROPIC_BASE_URL)
                .addHeader("x-api-key", BuildConfig.ANTHROPIC_API_KEY)
                .addHeader("anthropic-version", "2023-06-01")
        } else {
            builder.url(OPENAI_BASE_URL)
                .addHeader("Authorization", "Bearer ${getApiKey("openai")}")
        }
        
        return builder.build()
    }

    /**
     * NEW: Build interactive session request for dedicated Q&A mode
     */
    private fun buildInteractiveSessionRequest(
        topicTitle: String,
        sessionType: SessionType,
        chatHistory: List<Pair<String, String>>,
        model: String
    ): Request {
        
        // Verify model supports structured outputs
        if (!STRUCTURED_MODELS.contains(model)) {
            throw IllegalArgumentException("Model $model does not support structured outputs")
        }

        // Build request body with proper Claude/OpenAI format
        val requestBodyJson = JSONObject()

        if (model.startsWith("claude")) {
            // Claude/Anthropic API format
            val claudeMessages = JSONArray()
            
            // Add chat history (for context)
            chatHistory.forEach { (role, content) ->
                claudeMessages.put(JSONObject().apply {
                    put("role", if (role == "user") "user" else "assistant")
                    put("content", content)
                })
            }

            // Add session request
            val sessionPrompt = buildSessionPrompt(topicTitle, sessionType)
            claudeMessages.put(JSONObject().apply {
                put("role", "user")
                put("content", sessionPrompt)
            })

            requestBodyJson.apply {
                put("model", model)
                put("system", getInteractiveSessionSystemPrompt())
                put("messages", claudeMessages)
                put("temperature", 0.3) // Lower temperature for consistent question format
                put("max_tokens", 2500)
            }
        } else {
            // OpenAI API format
            val messagesArray = JSONArray()

            // Add interactive session system prompt
            messagesArray.put(JSONObject().apply {
                put("role", "system")
                put("content", getInteractiveSessionSystemPrompt())
            })

            // Add chat history (for context)
            chatHistory.forEach { (role, content) ->
                messagesArray.put(JSONObject().apply {
                    put("role", if (role == "user") "user" else "assistant")
                    put("content", content)
                })
            }

            // Add session request
            val sessionPrompt = buildSessionPrompt(topicTitle, sessionType)
            messagesArray.put(JSONObject().apply {
                put("role", "user")
                put("content", sessionPrompt)
            })

            // Build request body with interactive session schema
            val responseFormatSchema = StructuredOutputSchemas.getInteractiveSessionSchema()
            Log.d(TAG, "Interactive session schema: $responseFormatSchema")

            val responseFormatJson = JSONObject(responseFormatSchema.toString())

            requestBodyJson.apply {
                put("model", model)
                put("messages", messagesArray)
                put("response_format", responseFormatJson)
                put("temperature", 0.3) // Lower temperature for consistent question format
                put("max_tokens", 2500)
            }
        }

        val requestBody = requestBodyJson.toString()
            .toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())

        val builder = Request.Builder()
            .post(requestBody)
            .addHeader("Content-Type", "application/json")
            
        if (model.startsWith("claude")) {
            builder.url(ANTHROPIC_BASE_URL)
                .addHeader("x-api-key", BuildConfig.ANTHROPIC_API_KEY)
                .addHeader("anthropic-version", "2023-06-01")
        } else {
            builder.url(OPENAI_BASE_URL)
                .addHeader("Authorization", "Bearer ${getApiKey("openai")}")
        }
        
        return builder.build()
    }

    /**
     * NEW: Parse learning content response
     */
    private fun parseLearningContentResponse(responseBody: String): Result<LearningContent> {
        return try {
            val jsonResponse = JSONObject(responseBody)
            val message = jsonResponse.getJSONArray("choices").getJSONObject(0).getJSONObject("message")

            // Check for refusal
            if (message.has("refusal") && !message.isNull("refusal")) {
                val refusal = message.getString("refusal")
                return Result.failure(Exception("AI refused to respond: $refusal"))
            }

            val content = message.getString("content")
            val learningContent = gson.fromJson(content, LearningContent::class.java)

            Result.success(learningContent)
        } catch (e: JsonSyntaxException) {
            Log.e(TAG, "Failed to parse learning content response", e)
            Result.failure(Exception("Invalid learning content format: ${e.message}"))
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing learning content response", e)
            Result.failure(e)
        }
    }

    /**
     * NEW: Parse interactive session response
     */
    private fun parseInteractiveSessionResponse(responseBody: String): Result<InteractiveSession> {
        return try {
            val jsonResponse = JSONObject(responseBody)
            val message = jsonResponse.getJSONArray("choices").getJSONObject(0).getJSONObject("message")

            // Check for refusal
            if (message.has("refusal") && !message.isNull("refusal")) {
                val refusal = message.getString("refusal")
                return Result.failure(Exception("AI refused to respond: $refusal"))
            }

            val content = message.getString("content")
            val interactiveSession = gson.fromJson(content, InteractiveSession::class.java)

            Result.success(interactiveSession)
        } catch (e: JsonSyntaxException) {
            Log.e(TAG, "Failed to parse interactive session response", e)
            Result.failure(Exception("Invalid interactive session format: ${e.message}"))
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing interactive session response", e)
            Result.failure(e)
        }
    }

    /**
     * NEW: Enhance user prompt for comprehensive learning content
     */
    private fun enhanceLearningPrompt(userMessage: String): String {
        return """
        Please provide comprehensive educational content about: $userMessage

        Create content like an engaging textbook chapter or experienced teacher would provide.
        
        REQUIREMENTS:
        - Cover the topic thoroughly and comprehensively
        - Start with an engaging hook that captures interest
        - Explain fundamental concepts building to more advanced ideas
        - Include real-world examples showing practical applications
        - Show how this knowledge is actually used in life and work
        - Write in an engaging, accessible style appropriate for learning
        - Focus on understanding and practical relevance, not just mechanics

        Avoid step-by-step problem solving unless specifically requested.
        """.trimIndent()
    }

    /**
     * NEW: Get system prompt for interactive sessions
     */
    private fun getInteractiveSessionSystemPrompt(): String {
        return """
        You are creating an interactive Q&A session to test knowledge about a specific topic.

        IMPORTANT: This is SEPARATE from educational content. This is purely for testing understanding.

        RULES:
        1. Focus ONLY on creating good questions to test comprehension
        2. Questions should be engaging and thoughtful
        3. Include different question types (multiple choice, open-ended, scenarios)
        4. Provide clear explanations for correct answers
        5. Be encouraging and supportive
        6. Questions should test real understanding, not just memorization

        This session helps learners check their knowledge after studying the topic.
        """.trimIndent()
    }

    /**
     * NEW: Build session prompt based on topic and session type
     */
    private fun buildSessionPrompt(topicTitle: String, sessionType: SessionType): String {
        val sessionDescription = when (sessionType) {
            SessionType.KNOWLEDGE_CHECK -> "quick knowledge check with 3-5 questions"
            SessionType.PRACTICE_QUIZ -> "practice quiz with 5-8 questions of varying difficulty"
            SessionType.DISCUSSION_QUESTIONS -> "thought-provoking discussion questions"
            SessionType.APPLICATION_SCENARIOS -> "practical scenarios to apply the knowledge"
        }

        return """
        Create an interactive $sessionDescription for the topic: "$topicTitle"

        Make sure the questions test real understanding and application of the concepts.
        Include encouraging messages and clear explanations for each answer.
        """.trimIndent()
    }

    private fun enhanceUserPrompt(userMessage: String, responseType: ResponseType): String {
        val basePrompt = userMessage

        return when (responseType) {
            ResponseType.STEP_BY_STEP -> "$basePrompt\n\nPlease provide a detailed step-by-step solution with clear explanations for each step."
            ResponseType.QUIZ -> "$basePrompt\n\nPlease include practice questions to test understanding of this topic."
            ResponseType.CODE_TUTORIAL -> "$basePrompt\n\nPlease include complete, runnable code examples with thorough explanations. If you mention diagrams or graphs, provide a simple ASCII or textual representation so the user can visualize it."
            ResponseType.LESSON -> "$basePrompt\n\nPlease provide a comprehensive lesson with detailed examples, practice questions, and clear learning objectives. Avoid referencing any example, graph, or diagram unless you actually include it."
            else -> basePrompt
        }
    }

    private fun isCodeRelated(message: String): Boolean {
        val codeKeywords = setOf(
            "code", "programming", "function", "variable", "class", "method",
            "algorithm", "javascript", "python", "java", "kotlin", "swift",
            "html", "css", "sql", "database", "api", "framework"
        )
        return codeKeywords.any { message.lowercase().contains(it) }
    }

    private fun isMathRelated(message: String): Boolean {
        val mathKeywords = setOf(
            "solve", "equation", "calculate", "math", "algebra", "geometry",
            "calculus", "derivative", "integral", "formula", "theorem",
            "proof", "graph", "function", "variable", "coefficient"
        )
        return mathKeywords.any { message.lowercase().contains(it) } ||
                message.contains(Regex("[+\\-*/=<>^√∫∑π]"))
    }

    /**
     * Build simple streaming request (no forced schemas or rigid structure)
     * Updated to support Claude 4 models with proper Anthropic API format
     */
    private fun buildSimpleStreamingRequest(
        userMessage: String,
        chatHistory: List<Pair<String, String>>,
        model: String
    ): Request {
        val builder = Request.Builder()
            .addHeader("Content-Type", "application/json")
        
        if (model.startsWith("claude")) {
            // Claude/Anthropic format
            val messages = mutableListOf<Map<String, String>>()
            
            // Add chat history
            chatHistory.forEach { (role, content) ->
                messages.add(mapOf("role" to role, "content" to content))
            }
            
            // Add current user message
            messages.add(mapOf("role" to "user", "content" to userMessage))
            
            val requestBody = mapOf(
                "model" to model,
                "max_tokens" to 2000,
                "messages" to messages,
                "temperature" to 0.7,
                "stream" to true
            )
            
            val json = gson.toJson(requestBody)
            val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
            val body = json.toRequestBody(mediaType)
            
            builder.url(ANTHROPIC_BASE_URL)
                .addHeader("x-api-key", BuildConfig.ANTHROPIC_API_KEY)
                .addHeader("anthropic-version", "2023-06-01")
                .post(body)
        } else {
            // OpenAI format
            val messages = mutableListOf<Map<String, String>>()
            
            // Add system message for natural, adaptive responses
            messages.add(mapOf(
                "role" to "system",
                "content" to """You are an AI assistant that provides clear, helpful responses. Format your responses naturally using:

• **Headings** for main topics (use ## for sections)
• **Bold text** for important points
• **Bullet points** for lists (use - or •)
• **Numbered lists** for steps (1. 2. 3.)
• **Code blocks** for code (use ```)
• **Quotes** for important definitions (use >)

Adapt your response style to the question:
- Quick answers for simple questions
- Step-by-step for tutorials 
- Examples for complex topics
- Direct solutions for problems

Let the content drive the structure, not the other way around."""
            ))
            
            // Add chat history
            chatHistory.forEach { (role, content) ->
                messages.add(mapOf("role" to role, "content" to content))
            }
            
            // Add current user message
            messages.add(mapOf("role" to "user", "content" to userMessage))
            
            val requestBody = mapOf(
                "model" to model,
                "messages" to messages,
                "temperature" to 0.7,
                "max_tokens" to 2000,
                "stream" to true
            )
            
            val json = gson.toJson(requestBody)
            val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
            val body = json.toRequestBody(mediaType)
            
            builder.url(OPENAI_BASE_URL)
                .addHeader("Authorization", "Bearer ${getApiKey("openai")}")
                .post(body)
        }
        
        return builder.build()
    }

    /**
     * Convert accumulated text response to structured learning content
     */
    private fun convertTextToLearningContent(text: String, userMessage: String): LearningContent {
        val topic = extractTopicFromQuestion(userMessage)
        val subject = inferSubjectArea(userMessage)
        val keyPrinciples = extractKeyPrinciples(text)
        val examples = extractExamples(text)
        val readingTime = calculateReadingTime(text)
        
        return LearningContent(
            topicTitle = topic,
            subjectArea = subject,
            contentType = ContentType.COMPREHENSIVE_EXPLANATION,
            introduction = Introduction(
                hook = "Let's explore $topic and understand its significance.",
                overview = text.take(200) + "...",
                realWorldRelevance = "This topic has practical applications in many areas of study and daily life."
            ),
            coreContent = CoreContent(
                fundamentalConcepts = text.take(500),
                detailedExplanation = text,
                keyPrinciples = keyPrinciples,
                advancedConcepts = if (text.length > 1000) text.takeLast(300) else null
            ),
            practicalExamples = examples,
            applications = Applications(
                commonUses = listOf("Educational purposes", "Practical problem solving"),
                professionalApplications = listOf("Academic research", "Professional development"),
                everydayRelevance = "Understanding this topic helps in daily decision making and problem solving."
            ),
            readingTimeMinutes = readingTime
        )
    }

    private fun extractTopicFromQuestion(question: String): String {
        // Simple topic extraction - could be enhanced with NLP
        val words = question.split(" ").filter { it.length > 3 }
        return words.take(3).joinToString(" ").replaceFirstChar { it.uppercase() }
    }

    private fun inferSubjectArea(question: String): String {
        val subjects = mapOf(
            "math" to setOf("calculate", "solve", "equation", "algebra", "geometry", "calculus"),
            "science" to setOf("physics", "chemistry", "biology", "experiment", "theory"),
            "programming" to setOf("code", "function", "algorithm", "programming", "software"),
            "history" to setOf("history", "historical", "past", "century", "war", "civilization"),
            "language" to setOf("grammar", "language", "writing", "literature", "essay")
        )
        
        val questionLower = question.lowercase()
        return subjects.entries.find { (_, keywords) ->
            keywords.any { questionLower.contains(it) }
        }?.key ?: "General Knowledge"
    }

    private fun extractKeyPrinciples(text: String): List<KeyPrinciple> {
        // Simple principle extraction - could be enhanced
        val sentences = text.split(". ").filter { it.length > 50 }
        return sentences.take(3).mapIndexed { index, sentence ->
            KeyPrinciple(
                principle = "Key Principle ${index + 1}",
                explanation = sentence,
                importance = "This principle is fundamental to understanding the topic."
            )
        }
    }

    private fun extractExamples(text: String): List<PracticalExample> {
        // Simple example extraction - could be enhanced
        return listOf(
            PracticalExample(
                exampleTitle = "Practical Application",
                context = "Real-world scenario",
                application = text.take(200),
                outcome = "Better understanding of the concept"
            )
        )
    }

    private fun calculateReadingTime(text: String): Int {
        // Average reading speed: 200 words per minute
        val wordCount = text.split("\\s+".toRegex()).size
        return maxOf(1, (wordCount / 200))
    }

    /**
     * Build math tutor request following OpenAI structured output guidelines
     */
    private fun buildMathTutorRequest(
        userMessage: String,
        chatHistory: List<Pair<String, String>>,
        model: String
    ): Request {
        val messages = mutableListOf<Map<String, String>>()
        
        // Add system prompt following OpenAI math tutor example
        messages.add(mapOf(
            "role" to "system",
            "content" to """You are a helpful math tutor. You will be provided with a math problem,
and your goal will be to output a step by step solution, along with a final answer.
For each step, just provide the output as an equation use the explanation field to detail the reasoning."""
        ))
        
        // Add chat history
        chatHistory.forEach { (role, content) ->
            messages.add(mapOf("role" to role, "content" to content))
        }
        
        // Add current user message
        messages.add(mapOf("role" to "user", "content" to userMessage))
        
        // Build request with structured output schema following OpenAI docs
        val requestBody = mapOf(
            "model" to model,
            "messages" to messages,
            "response_format" to mapOf(
                "type" to "json_schema",
                "json_schema" to mapOf(
                    "name" to "math_reasoning",
                    "strict" to true,
                    "schema" to mapOf(
                        "type" to "object",
                        "properties" to mapOf(
                            "steps" to mapOf(
                                "type" to "array",
                                "items" to mapOf(
                                    "type" to "object",
                                    "properties" to mapOf(
                                        "explanation" to mapOf("type" to "string"),
                                        "output" to mapOf("type" to "string")
                                    ),
                                    "required" to listOf("explanation", "output"),
                                    "additionalProperties" to false
                                )
                            ),
                            "final_answer" to mapOf("type" to "string")
                        ),
                        "required" to listOf("steps", "final_answer"),
                        "additionalProperties" to false
                    )
                )
            ),
            "temperature" to 0.7
        )
        
        val json = gson.toJson(requestBody)
        val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
        val body = json.toRequestBody(mediaType)
        
        return Request.Builder()
            .url(OPENAI_BASE_URL)
            .addHeader("Authorization", "Bearer ${getApiKey("openai")}")
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build()
    }

    /**
     * Parse math tutor structured response and format for display
     */
    private fun parseMathTutorResponse(responseBody: String): String {
        return try {
            val jsonResponse = JSONObject(responseBody)
            val message = jsonResponse.getJSONArray("choices").getJSONObject(0).getJSONObject("message")
            
            // Check for refusal as per OpenAI docs
            if (message.has("refusal") && !message.isNull("refusal")) {
                val refusal = message.getString("refusal")
                Log.w(TAG, "AI refused to respond: $refusal")
                return "I'm unable to help with that math problem. Please try rephrasing your question."
            }
            
            val content = message.getString("content")
            val mathData = JSONObject(content)
            
            // Format structured math response for display
            val steps = mathData.getJSONArray("steps")
            val finalAnswer = mathData.getString("final_answer")
            
            val formatted = StringBuilder()
            formatted.append("## Step-by-Step Solution\n\n")
            
            for (i in 0 until steps.length()) {
                val step = steps.getJSONObject(i)
                val explanation = step.getString("explanation")
                val output = step.getString("output")
                
                formatted.append("**Step ${i + 1}:** $explanation\n\n")
                formatted.append("```\n$output\n```\n\n")
            }
            
            formatted.append("## Final Answer\n\n")
            formatted.append("**$finalAnswer**")
            
            formatted.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing math tutor response", e)
            "I encountered an error processing the math solution. Please try again."
        }
    }
}

// Additional response models for specific use cases
data class QuickExplanationResponse(
    val explanation: String,
    val keyPoints: List<String>,
    val example: String?,
    val followUpQuestions: List<String>
)

data class MathSolutionResponse(
    val problemType: String,
    val steps: List<MathStep>,
    val finalAnswer: String,
    val verification: String?
)

data class MathStep(
    val stepNumber: Int,
    val explanation: String,
    val mathematicalExpression: String,
    val reasoning: String
)

data class UiResponse(
    val ui: UiElement
)
