package com.playstudio.aiteacher.api

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.playstudio.aiteacher.BuildConfig
import com.playstudio.aiteacher.models.*
import com.playstudio.aiteacher.api.StructuredOutputSchemas
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    
    companion object {
        private const val TAG = "StructuredAPIHandler"
        private const val OPENAI_BASE_URL = "https://api.openai.com/v1/chat/completions"
        
        // Supported models for structured outputs
        private val STRUCTURED_MODELS = setOf(
            "gpt-4o",
            "gpt-4o-2024-08-06",
            "gpt-4o-mini",
            "gpt-4o-mini-2024-07-18"
        )
    }

    /**
     * Send a user message and get a structured educational response
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

        // Build messages array
        val messagesArray = JSONArray()
        
        // Add system prompt
        messagesArray.put(JSONObject().apply {
            put("role", "system")
            put("content", StructuredOutputSchemas.getEducationalSystemPrompt())
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
        val responseFormatSchema = StructuredOutputSchemas.getEducationalResponseSchema()
        Log.d(TAG, "Response format schema: $responseFormatSchema")
        
        // Convert Gson JsonObject to org.json JSONObject
        val responseFormatJson = JSONObject(responseFormatSchema.toString())
        
        val requestBodyJson = JSONObject().apply {
            put("model", model)
            put("messages", messagesArray)
            put("response_format", responseFormatJson)
            put("temperature", 0.7)
            put("max_completion_tokens", 4000)
        }

        val requestBodyString = requestBodyJson.toString()
        Log.d(TAG, "Request body: $requestBodyString")
        val requestBody = requestBodyString.toRequestBody("application/json".toMediaTypeOrNull())
        
        return Request.Builder()
            .url(OPENAI_BASE_URL)
            .post(requestBody)
            .addHeader("Authorization", "Bearer ${BuildConfig.API_KEY}")
            .addHeader("Content-Type", "application/json")
            .build()
    }

    private fun buildQuickExplanationRequest(userMessage: String, model: String): Request {
        val messagesArray = JSONArray()
        
        messagesArray.put(JSONObject().apply {
            put("role", "system")
            put("content", "You are AI Teacher. Provide clear, concise explanations. Use the structured format for consistency.")
        })
        
        messagesArray.put(JSONObject().apply {
            put("role", "user")
            put("content", userMessage)
        })

        val requestBodyJson = JSONObject().apply {
            put("model", model)
            put("messages", messagesArray)
            put("response_format", JSONObject(StructuredOutputSchemas.getQuickExplanationSchema().toString()))
            put("temperature", 0.5)
            put("max_completion_tokens", 1000)
        }

        val requestBody = requestBodyJson.toString().toRequestBody("application/json".toMediaTypeOrNull())
        
        return Request.Builder()
            .url(OPENAI_BASE_URL)
            .post(requestBody)
            .addHeader("Authorization", "Bearer ${BuildConfig.API_KEY}")
            .addHeader("Content-Type", "application/json")
            .build()
    }

    private fun buildMathSolutionRequest(problem: String, model: String): Request {
        val messagesArray = JSONArray()
        
        messagesArray.put(JSONObject().apply {
            put("role", "system")
            put("content", "You are AI Teacher specialized in mathematics. Provide step-by-step solutions with clear reasoning for each step.")
        })
        
        messagesArray.put(JSONObject().apply {
            put("role", "user")
            put("content", "Solve this step by step: $problem")
        })

        val requestBodyJson = JSONObject().apply {
            put("model", model)
            put("messages", messagesArray)
            put("response_format", JSONObject(StructuredOutputSchemas.getMathSolutionSchema().toString()))
            put("temperature", 0.3)
            put("max_completion_tokens", 2000)
        }

        val requestBody = requestBodyJson.toString().toRequestBody("application/json".toMediaTypeOrNull())
        
        return Request.Builder()
            .url(OPENAI_BASE_URL)
            .post(requestBody)
            .addHeader("Authorization", "Bearer ${BuildConfig.API_KEY}")
            .addHeader("Content-Type", "application/json")
            .build()
    }

    private fun parseStructuredResponse(responseBody: String): Result<EducationalResponse> {
        return try {
            val jsonResponse = JSONObject(responseBody)
            val choices = jsonResponse.getJSONArray("choices")
            
            if (choices.length() == 0) {
                return Result.failure(Exception("No choices in response"))
            }

            val message = choices.getJSONObject(0).getJSONObject("message")
            
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
            val choices = jsonResponse.getJSONArray("choices")
            val message = choices.getJSONObject(0).getJSONObject("message")
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
            val choices = jsonResponse.getJSONArray("choices")
            val message = choices.getJSONObject(0).getJSONObject("message")
            val content = message.getString("content")
            
            val mathResponse = gson.fromJson(content, MathSolutionResponse::class.java)
            Result.success(mathResponse)
        } catch (e: Exception) {
            Result.failure(e)
        }
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