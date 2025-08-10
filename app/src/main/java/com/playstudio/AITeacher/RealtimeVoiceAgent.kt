package com.playstudio.aiteacher

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit
import com.playstudio.aiteacher.pricing.AIModel
import com.playstudio.aiteacher.security.FirestoreKeyManager
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Real-time Voice Agent using OpenAI's Realtime API
 * Supports speech-to-speech interactions with low latency
 * Based on OpenAI's voice agents architecture
 */
class RealtimeVoiceAgent(private val context: Context) {
    
    companion object {
        private const val TAG = "RealtimeVoiceAgent"
        private const val REALTIME_API_URL = "wss://api.openai.com/v1/realtime"
        private const val SESSION_API_URL = "https://api.openai.com/v1/realtime/sessions"
        
        // Voice agent personalities
        const val PERSONALITY_PROFESSIONAL = "professional"
        const val PERSONALITY_FRIENDLY = "friendly"
        const val PERSONALITY_ENTHUSIASTIC = "enthusiastic"
        const val PERSONALITY_CALM = "calm"
        
        // Conversation states
        const val STATE_IDLE = "idle"
        const val STATE_LISTENING = "listening"
        const val STATE_THINKING = "thinking"
        const val STATE_SPEAKING = "speaking"
        const val STATE_INTERRUPTED = "interrupted"
    }
    
    private val keyManager = FirestoreKeyManager.getInstance()
    private var webSocket: WebSocket? = null
    private var sessionToken: String? = null
    private var currentState = STATE_IDLE
    private var conversationHistory = mutableListOf<VoiceMessage>()
    private var currentAgent: VoiceAgentConfig? = null
    
    // Callback interfaces
    interface VoiceAgentCallback {
        fun onStateChanged(newState: String)
        fun onAudioReceived(audioData: ByteArray)
        fun onTranscriptReceived(transcript: String, isUser: Boolean)
        fun onError(error: String)
        fun onAgentHandoff(newAgent: String)
        fun onGuardrailTripped(reason: String)
    }
    
    private var callback: VoiceAgentCallback? = null
    
    /**
     * Voice Agent Configuration
     */
    data class VoiceAgentConfig(
        val name: String,
        val instructions: String,
        val personality: AgentPersonality = AgentPersonality(),
        val tools: List<VoiceAgentTool> = emptyList(),
        val handoffAgents: List<String> = emptyList(),
        val guardrails: List<Guardrail> = emptyList()
    )
    
    data class AgentPersonality(
        val identity: String = "You are a helpful AI assistant",
        val demeanor: String = "professional and friendly",
        val tone: String = "warm and conversational",
        val enthusiasm: String = "medium", // low, medium, high
        val formality: String = "professional", // casual, professional, formal
        val emotion: String = "empathetic", // neutral, empathetic, energetic
        val fillerWords: String = "occasionally", // none, occasionally, often, very_often
        val pacing: String = "natural", // slow, natural, fast
        val voiceModel: String = "alloy" // alloy, echo, fable, onyx, nova, shimmer
    )
    
    data class VoiceAgentTool(
        val name: String,
        val description: String,
        val parameters: Map<String, Any>,
        val needsApproval: Boolean = false,
        val handler: suspend (Map<String, Any>, VoiceContext) -> String
    )
    
    data class Guardrail(
        val name: String,
        val description: String,
        val handler: suspend (String) -> GuardrailResult
    )
    
    data class GuardrailResult(
        val tripwireTriggered: Boolean,
        val reason: String = "",
        val severity: String = "medium" // low, medium, high
    )
    
    data class VoiceMessage(
        val content: String,
        val isUser: Boolean,
        val timestamp: Long = System.currentTimeMillis(),
        val audioData: ByteArray? = null
    )
    
    data class VoiceContext(
        val history: List<VoiceMessage>,
        val currentAgent: VoiceAgentConfig?,
        val conversationState: String
    )
    
    /**
     * Create ephemeral session token for secure client connection
     */
    suspend fun createEphemeralToken(model: String = "gpt-4o-realtime-preview"): String = withContext(Dispatchers.IO) {
        // Get OpenAI API key from Firestore
        val apiKey = keyManager.getApiKeyWithFallback("openai")
        if (apiKey.isNullOrBlank()) {
            throw Exception("OpenAI API key not found in Firestore or BuildConfig")
        }
        
        val client = OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)  // Increased for GPT-4o realtime API
            .readTimeout(90, TimeUnit.SECONDS)     // Increased for voice processing
            .build()
        
        val requestBody = JSONObject().apply {
            put("model", model)
            put("modalities", JSONArray().apply {
                put("text")
                put("audio")
            })
            put("instructions", currentAgent?.let { buildAgentPrompt(it) } ?: "You are a helpful AI assistant.")
            put("voice", currentAgent?.personality?.voiceModel ?: "alloy")
            put("input_audio_format", "pcm16")
            put("output_audio_format", "pcm16")
            put("input_audio_transcription", JSONObject().apply {
                put("model", "whisper-1")
            })
            put("turn_detection", JSONObject().apply {
                put("type", "server_vad")
                put("threshold", 0.4) // Slightly higher threshold for more reliable speech detection
                put("prefix_padding_ms", 400) // Increased padding to capture more speech start
                put("silence_duration_ms", 1000) // Longer silence duration for better audio buffer accumulation
                put("create_response", true)
                put("interrupt_response", true)
            })
            put("temperature", 0.8)
            put("max_response_output_tokens", 4096)
            put("tool_choice", "auto")
            put("speed", 1.0)
            currentAgent?.tools?.let { tools ->
                if (tools.isNotEmpty()) {
                    put("tools", buildToolsArray(tools))
                }
            }
        }
        
        val request = Request.Builder()
            .url(SESSION_API_URL)
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(requestBody.toString().toRequestBody("application/json".toMediaTypeOrNull()))
            .build()
        
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("Failed to create session: ${response.code}")
            }
            
            val responseBody = response.body?.string()
                ?: throw Exception("Empty response from session creation")
            
            val json = JSONObject(responseBody)
            sessionToken = json.getJSONObject("client_secret").getString("value")
            Log.d(TAG, "Created ephemeral session token")
            sessionToken!!
        }
    }
    
    /**
     * Initialize voice agent with configuration and ensure API keys are loaded
     */
    suspend fun initializeAgent(config: VoiceAgentConfig, callback: VoiceAgentCallback) {
        this.currentAgent = config
        this.callback = callback
        
        // Ensure API keys are loaded from Firestore
        if (!keyManager.hasValidCache()) {
            Log.d(TAG, "Loading API keys from Firestore...")
            val success = keyManager.fetchAndCacheKeys()
            if (!success) {
                callback.onError("Failed to load API keys from Firestore")
                return
            }
        }
        
        Log.d(TAG, "Initialized voice agent: ${config.name}")
    }
    
    /**
     * Connect to realtime voice session with retry logic
     */
    suspend fun connectToRealtime(ephemeralToken: String? = null, retryCount: Int = 0): Boolean = withContext(Dispatchers.Main) {
        try {
            val token = ephemeralToken ?: sessionToken ?: createEphemeralToken()
            
            val client = OkHttpClient.Builder()
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .build()
            
            val request = Request.Builder()
                .url("$REALTIME_API_URL?model=gpt-4o-realtime-preview")
                .header("Authorization", "Bearer $token")
                .header("OpenAI-Beta", "realtime=v1")
                .build()
            
            return@withContext suspendCoroutine { continuation ->
                var isResumed = false
                
                webSocket = client.newWebSocket(request, object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        Log.d(TAG, "WebSocket connected to Realtime API")
                        synchronized(this@RealtimeVoiceAgent) {
                            if (!isResumed) {
                                isResumed = true
                                initializeSession()
                                continuation.resume(true)
                            }
                        }
                    }
                    
                    override fun onMessage(webSocket: WebSocket, text: String) {
                        handleRealtimeMessage(text)
                    }
                    
                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        Log.e(TAG, "WebSocket connection failed (attempt ${retryCount + 1})", t)
                        synchronized(this@RealtimeVoiceAgent) {
                            if (!isResumed) {
                                isResumed = true
                                
                                // Check if this is a network connectivity issue that we might retry
                                val isRetryableError = t is java.net.SocketException || 
                                                     t is java.net.ConnectException ||
                                                     t is java.net.SocketTimeoutException
                                
                                if (isRetryableError && retryCount < 2) {
                                    // Will be retried by caller
                                    callback?.onError("Connection failed, retrying... (${t.message})")
                                } else {
                                    // Final failure
                                    callback?.onError("Connection failed: ${t.message}")
                                }
                                
                                continuation.resume(false)
                            }
                        }
                    }
                    
                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        Log.d(TAG, "WebSocket closed: $reason")
                        updateState(STATE_IDLE)
                    }
                })
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect to Realtime API", e)
            callback?.onError("Failed to connect: ${e.message}")
            false
        }
    }
    
    /**
     * Initialize the realtime session with agent configuration
     */
    private fun initializeSession() {
        val agent = currentAgent ?: return
        
        val sessionConfig = JSONObject().apply {
            put("type", "session.update")
            put("session", JSONObject().apply {
                put("modalities", JSONArray().apply {
                    put("text")
                    put("audio")
                })
                put("instructions", buildAgentPrompt(agent))
                put("voice", agent.personality.voiceModel)
                put("input_audio_format", "pcm16")
                put("output_audio_format", "pcm16")
                put("input_audio_transcription", JSONObject().apply {
                    put("model", "whisper-1")
                })
                put("turn_detection", JSONObject().apply {
                    put("type", "server_vad")
                    put("threshold", 0.4) // Match the session creation threshold
                    put("prefix_padding_ms", 400) // Increased padding for better speech capture
                    put("silence_duration_ms", 1000) // Longer silence for better buffer accumulation
                    put("create_response", true)
                    put("interrupt_response", true)
                })
                put("tools", buildToolsArray(agent.tools))
                put("temperature", 0.8)
                put("max_response_output_tokens", 4096)
                put("tool_choice", "auto")
                put("speed", 1.0)
            })
        }
        
        webSocket?.send(sessionConfig.toString())
        updateState(STATE_LISTENING)
        Log.d(TAG, "Session initialized with agent: ${agent.name}")
    }
    
    /**
     * Build comprehensive agent prompt based on OpenAI voice agent guidelines
     */
    private fun buildAgentPrompt(agent: VoiceAgentConfig): String {
        return buildString {
            appendLine("# Personality and Tone")
            appendLine("## Identity")
            appendLine(agent.personality.identity)
            appendLine()
            appendLine("## Task")
            appendLine(agent.instructions)
            appendLine()
            appendLine("## Demeanor")
            appendLine(agent.personality.demeanor)
            appendLine()
            appendLine("## Tone")
            appendLine(agent.personality.tone)
            appendLine()
            appendLine("## Level of Enthusiasm")
            appendLine(agent.personality.enthusiasm)
            appendLine()
            appendLine("## Level of Formality")
            appendLine(agent.personality.formality)
            appendLine()
            appendLine("## Level of Emotion")
            appendLine(agent.personality.emotion)
            appendLine()
            appendLine("## Filler Words")
            appendLine("Use filler words ${agent.personality.fillerWords} to make conversation natural")
            appendLine()
            appendLine("## Pacing")
            appendLine("Speak at a ${agent.personality.pacing} pace")
            appendLine()
            appendLine("# Instructions")
            appendLine("- Always be helpful and accurate in your responses")
            appendLine("- If you need to spell something out, do so letter by letter for clarity")
            appendLine("- If the user corrects any detail, acknowledge the correction naturally")
            appendLine("- Use your voice expressively to convey emotion and engagement")
            
            if (agent.handoffAgents.isNotEmpty()) {
                appendLine("- You can transfer conversations to specialized agents when appropriate:")
                agent.handoffAgents.forEach { agentName ->
                    appendLine("  - $agentName")
                }
            }
        }
    }
    
    /**
     * Build tools array for session configuration
     */
    private fun buildToolsArray(tools: List<VoiceAgentTool>): JSONArray {
        val toolsArray = JSONArray()
        
        tools.forEach { tool ->
            val toolConfig = JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", tool.name)
                    put("description", tool.description)
                    put("parameters", JSONObject(tool.parameters))
                })
            }
            toolsArray.put(toolConfig)
        }
        
        // Add handoff tool if agents are configured
        currentAgent?.handoffAgents?.let { handoffAgents ->
            if (handoffAgents.isNotEmpty()) {
                val handoffTool = JSONObject().apply {
                    put("type", "function")
                    put("function", JSONObject().apply {
                        put("name", "transfer_agent")
                        put("description", "Transfer the conversation to a specialized agent")
                        put("parameters", JSONObject().apply {
                            put("type", "object")
                            put("properties", JSONObject().apply {
                                put("agent_name", JSONObject().apply {
                                    put("type", "string")
                                    put("enum", JSONArray().apply {
                                        handoffAgents.forEach { put(it) }
                                    })
                                    put("description", "The specialized agent to transfer to")
                                })
                                put("reason", JSONObject().apply {
                                    put("type", "string")
                                    put("description", "Reason for the transfer")
                                })
                                put("context", JSONObject().apply {
                                    put("type", "string")
                                    put("description", "Relevant context for the new agent")
                                })
                            })
                            put("required", JSONArray().apply {
                                put("agent_name")
                                put("reason")
                            })
                        })
                    })
                }
                toolsArray.put(handoffTool)
            }
        }
        
        return toolsArray
    }
    
    /**
     * Handle incoming realtime messages
     */
    private fun handleRealtimeMessage(message: String) {
        try {
            val json = JSONObject(message)
            val type = json.getString("type")
            
            when (type) {
                "session.created" -> {
                    Log.d(TAG, "Realtime session created successfully")
                }
                "session.updated" -> {
                    Log.d(TAG, "Session configuration updated")
                }
                "input_audio_buffer.speech_started" -> {
                    updateState(STATE_LISTENING)
                    Log.i(TAG, "🎙️ OPENAI VAD: User started speaking - server detected speech!")
                }
                "input_audio_buffer.speech_stopped" -> {
                    updateState(STATE_THINKING)
                    Log.i(TAG, "🛑 OPENAI VAD: User stopped speaking - processing response...")
                }
                "input_audio_buffer.committed" -> {
                    Log.d(TAG, "Audio buffer committed successfully")
                }
                "response.created" -> {
                    updateState(STATE_THINKING)
                    Log.d(TAG, "Response created by OpenAI")
                }
                "response.output_item.added" -> {
                    handleOutputItem(json.getJSONObject("item"))
                }
                "response.audio_transcript.delta" -> {
                    val delta = json.getString("delta")
                    callback?.onTranscriptReceived(delta, false)
                }
                "response.audio.delta" -> {
                    val audioBase64 = json.getString("delta")
                    val audioData = android.util.Base64.decode(audioBase64, android.util.Base64.DEFAULT)
                    callback?.onAudioReceived(audioData)
                    updateState(STATE_SPEAKING)
                }
                "response.done" -> {
                    updateState(STATE_LISTENING)
                    Log.d(TAG, "Response completed")
                }
                "conversation.item.input_audio_transcription.completed" -> {
                    val transcript = json.getString("transcript")
                    callback?.onTranscriptReceived(transcript, true)
                    conversationHistory.add(VoiceMessage(transcript, true))
                }
                "response.function_call_arguments.done" -> {
                    handleFunctionCall(json)
                }
                "error" -> {
                    val error = json.getJSONObject("error")
                    val errorMessage = error.getString("message")
                    val errorType = error.optString("type", "unknown")
                    
                    // Special handling for buffer size errors
                    if (errorMessage.contains("buffer too small") || errorMessage.contains("Expected at least")) {
                        Log.e(TAG, "Audio buffer size error - this indicates insufficient audio data was sent to OpenAI")
                        Log.e(TAG, "Error details: type=$errorType, message=$errorMessage")
                        callback?.onError("Audio buffer error: Not enough speech detected. Please speak longer and clearer.")
                    } else {
                        Log.e(TAG, "OpenAI API Error: type=$errorType, message=$errorMessage")
                        callback?.onError("API Error: $errorMessage")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling realtime message", e)
            callback?.onError("Message processing error: ${e.message}")
        }
    }
    
    /**
     * Handle function call execution
     */
    private fun handleFunctionCall(json: JSONObject) {
        try {
            val callId = json.getString("call_id")
            val name = json.getString("name")
            val arguments = json.getString("arguments")
            
            if (name == "transfer_agent") {
                handleAgentHandoff(arguments, callId)
            } else {
                executeTool(name, arguments, callId)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling function call", e)
        }
    }
    
    /**
     * Handle agent handoff
     */
    private fun handleAgentHandoff(arguments: String, callId: String) {
        try {
            val args = JSONObject(arguments)
            val agentName = args.getString("agent_name")
            val reason = args.getString("reason")
            
            Log.d(TAG, "Agent handoff requested: $agentName (Reason: $reason)")
            callback?.onAgentHandoff(agentName)
            
            // Send function call result
            val result = JSONObject().apply {
                put("type", "conversation.item.create")
                put("item", JSONObject().apply {
                    put("type", "function_call_output")
                    put("call_id", callId)
                    put("output", "Successfully transferred to $agentName agent")
                })
            }
            webSocket?.send(result.toString())
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in agent handoff", e)
        }
    }
    
    /**
     * Execute custom tool
     */
    private fun executeTool(toolName: String, arguments: String, callId: String) {
        val tool = currentAgent?.tools?.find { it.name == toolName }
        if (tool == null) {
            Log.e(TAG, "Unknown tool: $toolName")
            return
        }
        
        // Execute tool in background
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val args = JSONObject(arguments).let { json ->
                    mutableMapOf<String, Any>().apply {
                        json.keys().forEach { key ->
                            put(key, json.get(key))
                        }
                    }
                }
                
                val context = VoiceContext(
                    history = conversationHistory.toList(),
                    currentAgent = currentAgent,
                    conversationState = currentState
                )
                
                val result = tool.handler(args, context)
                
                withContext(Dispatchers.Main) {
                    // Send function call result back to the API
                    val response = JSONObject().apply {
                        put("type", "conversation.item.create")
                        put("item", JSONObject().apply {
                            put("type", "function_call_output")
                            put("call_id", callId)
                            put("output", result)
                        })
                    }
                    webSocket?.send(response.toString())
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error executing tool $toolName", e)
                withContext(Dispatchers.Main) {
                    val errorResponse = JSONObject().apply {
                        put("type", "conversation.item.create")
                        put("item", JSONObject().apply {
                            put("type", "function_call_output")
                            put("call_id", callId)
                            put("output", "Error executing tool: ${e.message}")
                        })
                    }
                    webSocket?.send(errorResponse.toString())
                }
            }
        }
    }
    
    /**
     * Handle output items (text/audio)
     */
    private fun handleOutputItem(item: JSONObject) {
        val type = item.getString("type")
        when (type) {
            "message" -> {
                val role = item.getString("role")
                val content = item.getJSONArray("content")
                if (content.length() > 0) {
                    val firstContent = content.getJSONObject(0)
                    if (firstContent.has("text")) {
                        val text = firstContent.getString("text")
                        conversationHistory.add(VoiceMessage(text, false))
                    }
                }
            }
        }
    }
    
    /**
     * Send audio data to the realtime API
     */
    fun sendAudio(audioData: ByteArray) {
        val base64Audio = android.util.Base64.encodeToString(audioData, android.util.Base64.NO_WRAP)
        val message = JSONObject().apply {
            put("type", "input_audio_buffer.append")
            put("audio", base64Audio)
        }
        webSocket?.send(message.toString())
    }
    
    /**
     * Send text message to the agent
     */
    fun sendTextMessage(text: String) {
        val message = JSONObject().apply {
            put("type", "conversation.item.create")
            put("item", JSONObject().apply {
                put("type", "message")
                put("role", "user")
                put("content", JSONArray().apply {
                    put(JSONObject().apply {
                        put("type", "input_text")
                        put("text", text)
                    })
                })
            })
        }
        webSocket?.send(message.toString())
        
        // Trigger response
        val responseMessage = JSONObject().apply {
            put("type", "response.create")
        }
        webSocket?.send(responseMessage.toString())
    }

    /**
     * Manually commit audio buffer and trigger response (for testing/fallback)
     */
    fun commitAudioAndTriggerResponse() {
        if (webSocket == null) {
            Log.e(TAG, "Cannot commit audio buffer - WebSocket is null")
            return
        }
        
        try {
            // Commit the audio buffer first
            val commitMessage = JSONObject().apply {
                put("type", "input_audio_buffer.commit")
                put("event_id", java.util.UUID.randomUUID().toString()) // Add event ID for tracking
            }
            webSocket?.send(commitMessage.toString())
            Log.w(TAG, "🔄 FALLBACK: Manually committed audio buffer - event: ${commitMessage.optString("event_id")}")
            
            // Small delay to ensure commit is processed before response creation
            Thread.sleep(50)
            
            // Trigger response creation
            val responseMessage = JSONObject().apply {
                put("type", "response.create")
                put("event_id", java.util.UUID.randomUUID().toString()) // Add event ID for tracking
                // Specify output modalities to ensure we get audio response
                put("response", JSONObject().apply {
                    put("modalities", org.json.JSONArray().apply {
                        put("text")
                        put("audio")
                    })
                })
            }
            webSocket?.send(responseMessage.toString())
            Log.w(TAG, "🚀 FALLBACK: Manually triggered response creation - event: ${responseMessage.optString("event_id")}")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in fallback audio commit and response trigger", e)
            callback?.onError("Failed to trigger fallback response: ${e.message}")
        }
    }
    
    /**
     * Interrupt current response
     */
    fun interrupt() {
        // Only attempt to cancel if we're in a state where cancellation makes sense
        if (currentState == STATE_SPEAKING || currentState == STATE_THINKING) {
            val message = JSONObject().apply {
                put("type", "response.cancel")
            }
            webSocket?.send(message.toString())
            updateState(STATE_INTERRUPTED)
            Log.d(TAG, "Response interrupted by user")
        } else {
            Log.d(TAG, "Interrupt ignored - no active response (current state: $currentState)")
        }
    }
    
    /**
     * Update conversation state
     */
    private fun updateState(newState: String) {
        if (currentState != newState) {
            currentState = newState
            callback?.onStateChanged(newState)
            Log.d(TAG, "State changed to: $newState")
        }
    }
    
    /**
     * Get current conversation history
     */
    fun getConversationHistory(): List<VoiceMessage> = conversationHistory.toList()
    
    /**
     * Clear conversation history
     */
    fun clearHistory() {
        conversationHistory.clear()
        Log.d(TAG, "Conversation history cleared")
    }
    
    /**
     * Disconnect from realtime session
     */
    fun disconnect() {
        webSocket?.close(1000, "User disconnected")
        webSocket = null
        sessionToken = null
        updateState(STATE_IDLE)
        Log.d(TAG, "Disconnected from realtime session")
    }
    
    /**
     * Check if currently connected
     */
    fun isConnected(): Boolean = webSocket != null && currentState != STATE_IDLE
    
    /**
     * Get current agent configuration
     */
    fun getCurrentAgent(): VoiceAgentConfig? = currentAgent
    
    /**
     * Update agent configuration (will restart session)
     */
    suspend fun updateAgent(newConfig: VoiceAgentConfig) {
        currentAgent = newConfig
        if (isConnected()) {
            initializeSession()
            Log.d(TAG, "Agent configuration updated: ${newConfig.name}")
        }
    }
}