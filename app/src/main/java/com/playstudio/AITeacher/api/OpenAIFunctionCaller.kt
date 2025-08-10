package com.playstudio.aiteacher.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import android.util.Log

/**
 * OpenAI Function Calling API Handler
 * Implements OpenAI's function calling capabilities for enhanced AI interactions
 */
class OpenAIFunctionCaller(private val apiKey: String) {
    
    companion object {
        private const val TAG = "OpenAIFunctionCaller"
        private const val BASE_URL = "https://api.openai.com/v1"
        private const val RESPONSES_ENDPOINT = "$BASE_URL/responses"
        private const val TIMEOUT_SECONDS = 30L
    }
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()
    
    /**
     * Data class for function definitions
     */
    data class FunctionDefinition(
        val name: String,
        val description: String,
        val parameters: JSONObject,
        val strict: Boolean = true
    )
    
    /**
     * Data class for function call results
     */
    data class FunctionCallResult(
        val callId: String,
        val name: String,
        val arguments: String,
        val result: String? = null
    )
    
    /**
     * Data class for API response
     */
    data class OpenAIResponse(
        val outputText: String? = null,
        val functionCalls: List<FunctionCallResult> = emptyList(),
        val requiresFunctionExecution: Boolean = false
    )
    
    /**
     * Call OpenAI API with function calling capabilities
     */
    suspend fun callWithFunctions(
        input: String,
        functions: List<FunctionDefinition> = emptyList(),
        model: String = "gpt-4.1",
        includeWebSearch: Boolean = false,
        toolChoice: String = "auto"
    ): OpenAIResponse = withContext(Dispatchers.IO) {
        try {
            val requestBody = buildRequestBody(input, functions, model, includeWebSearch, toolChoice)
            val request = Request.Builder()
                .url(RESPONSES_ENDPOINT)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(requestBody)
                .build()
            
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
            
            if (!response.isSuccessful) {
                Log.e(TAG, "API call failed: ${response.code} - $responseBody")
                return@withContext OpenAIResponse(
                    outputText = "Error: Failed to get AI response (${response.code})"
                )
            }
            
            responseBody?.let { parseResponse(it) } 
                ?: OpenAIResponse(outputText = "Error: Empty response from API")
                
        } catch (e: IOException) {
            Log.e(TAG, "Network error during API call", e)
            OpenAIResponse(outputText = "Error: Network connectivity issue - ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during API call", e)
            OpenAIResponse(outputText = "Error: Unexpected issue occurred - ${e.message}")
        }
    }
    
    /**
     * Execute function calls and get final response
     */
    suspend fun executeAndContinue(
        originalInput: String,
        functionCalls: List<FunctionCallResult>,
        functions: List<FunctionDefinition>,
        model: String = "gpt-4.1"
    ): OpenAIResponse = withContext(Dispatchers.IO) {
        try {
            val messages = JSONArray().apply {
                // Add original user message
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", originalInput)
                })
                
                // Add function call results
                functionCalls.forEach { call ->
                    put(JSONObject().apply {
                        put("type", "function_call_output")
                        put("call_id", call.callId)
                        put("output", call.result ?: "Function executed successfully")
                    })
                }
            }
            
            val requestBody = JSONObject().apply {
                put("model", model)
                put("input", messages)
                put("tools", buildToolsArray(functions))
            }.toString().toRequestBody("application/json".toMediaType())
            
            val request = Request.Builder()
                .url(RESPONSES_ENDPOINT)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(requestBody)
                .build()
            
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
            
            responseBody?.let { parseResponse(it) } 
                ?: OpenAIResponse(outputText = "Error: Empty response from API")
                
        } catch (e: Exception) {
            Log.e(TAG, "Error in executeAndContinue", e)
            OpenAIResponse(outputText = "Error: Failed to process function results - ${e.message}")
        }
    }
    
    /**
     * Build request body for API call
     */
    private fun buildRequestBody(
        input: String,
        functions: List<FunctionDefinition>,
        model: String,
        includeWebSearch: Boolean,
        toolChoice: String
    ): RequestBody {
        val requestJson = JSONObject().apply {
            put("model", model)
            put("input", input)
            
            // Add tools
            val tools = buildToolsArray(functions, includeWebSearch)
            if (tools.length() > 0) {
                put("tools", tools)
                put("tool_choice", toolChoice)
            }
        }
        
        Log.d(TAG, "Request: $requestJson")
        return requestJson.toString().toRequestBody("application/json".toMediaType())
    }
    
    /**
     * Build tools array for API request
     */
    private fun buildToolsArray(
        functions: List<FunctionDefinition>,
        includeWebSearch: Boolean = false
    ): JSONArray {
        return JSONArray().apply {
            // Add web search if requested
            if (includeWebSearch) {
                put(JSONObject().apply {
                    put("type", "web_search_preview")
                })
            }
            
            // Add custom functions
            functions.forEach { function ->
                put(JSONObject().apply {
                    put("type", "function")
                    put("name", function.name)
                    put("description", function.description)
                    put("parameters", function.parameters)
                    put("strict", function.strict)
                })
            }
        }
    }
    
    /**
     * Parse API response
     */
    private fun parseResponse(responseBody: String): OpenAIResponse {
        try {
            val jsonResponse = JSONObject(responseBody)
            Log.d(TAG, "Response: $jsonResponse")
            
            // Check for output_text (direct text response)
            val outputText = jsonResponse.optString("output_text")
            if (outputText.isNotEmpty()) {
                return OpenAIResponse(outputText = outputText)
            }
            
            // Check for output array (function calls or mixed response)
            val outputArray = jsonResponse.optJSONArray("output")
            if (outputArray != null) {
                val functionCalls = mutableListOf<FunctionCallResult>()
                val textParts = mutableListOf<String>()
                
                for (i in 0 until outputArray.length()) {
                    val item = outputArray.getJSONObject(i)
                    val type = item.optString("type")
                    
                    when (type) {
                        "function_call" -> {
                            functionCalls.add(FunctionCallResult(
                                callId = item.optString("call_id"),
                                name = item.optString("name"),
                                arguments = item.optString("arguments")
                            ))
                        }
                        "text" -> {
                            textParts.add(item.optString("content", ""))
                        }
                    }
                }
                
                return OpenAIResponse(
                    outputText = if (textParts.isNotEmpty()) textParts.joinToString(" ") else null,
                    functionCalls = functionCalls,
                    requiresFunctionExecution = functionCalls.isNotEmpty()
                )
            }
            
            return OpenAIResponse(outputText = "Error: Unexpected response format")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing response", e)
            return OpenAIResponse(outputText = "Error: Failed to parse AI response - ${e.message}")
        }
    }
}