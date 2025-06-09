package com.playstudio.aiteacher.viewmodel

import kotlinx.coroutines.withContext
import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.AudioAttributes
import android.media.MediaRecorder
import android.util.Base64
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.playstudio.aiteacher.BuildConfig // For your OpenAI API_KEY
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import com.playstudio.aiteacher.VoiceToolHandler
import java.util.UUID
import java.util.concurrent.TimeUnit

class OpenAILiveAudioViewModel : ViewModel() {

    private val _isSessionActive = MutableStateFlow(false)
    val isSessionActive: StateFlow<Boolean> = _isSessionActive.asStateFlow()

    private val _status = MutableStateFlow("Idle")
    val status: StateFlow<String> = _status.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _aiTextMessage = MutableStateFlow("")
    val aiTextMessage: StateFlow<String> = _aiTextMessage.asStateFlow()

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null

    private val recordingScope = CoroutineScope(Dispatchers.IO)
    private var recordingJob: Job? = null
    private var webSocketClient: WebSocket? = null
    private val okHttpClient = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS) // Keep WebSocket alive
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private val inputSampleRate = 16000
    private val inputChannelConfig = AudioFormat.CHANNEL_IN_MONO
    private val inputAudioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val inputAudioRecordBufferSize = AudioRecord.getMinBufferSize(inputSampleRate, inputChannelConfig, inputAudioFormat) * 2

    private val outputSampleRate = 24000 // Common for OpenAI TTS
    private val outputChannelConfig = AudioFormat.CHANNEL_OUT_MONO
    private val outputAudioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val outputAudioTrackBufferSize = AudioTrack.getMinBufferSize(outputSampleRate, outputChannelConfig, outputAudioFormat)

    // OpenAI Config
    // IMPORTANT: Replace "gpt-4o-realtime-preview" with the latest valid model ID from OpenAI docs
    // e.g., "gpt-4o-realtime-preview-YYYY-MM-DD"
    private val openAIModelId = "gpt-4o-realtime-preview" // Check for dated versions like "gpt-4o-realtime-preview-2024-12-17"
    private val openAIRealtimeUrl = "wss://api.openai.com/v1/realtime?model=$openAIModelId"

    private val audioOutChannel = Channel<ByteArray>(Channel.UNLIMITED)
    private var currentSessionId: String? = null // If server provides one in session.created

    private var sessionVoice: String = "alloy"
    private var sessionTools: JSONArray? = null

    var toolHandler: VoiceToolHandler? = null
        set(value) { field = value }

    fun setVoice(voice: String) {
        sessionVoice = voice
    }

    fun setTools(tools: JSONArray?) {
        sessionTools = tools?.let { convertToolsForRealtime(it) }
    }

    private fun convertToolsForRealtime(openAiTools: JSONArray): JSONArray {
        if (openAiTools.length() == 0) return openAiTools
        val firstObj = openAiTools.optJSONObject(0)
        // If already in realtime format (has name at top level), return as-is
        if (firstObj != null && firstObj.has("name") && !firstObj.has("function")) {
            return openAiTools
        }
        val realtimeTools = JSONArray()
        for (i in 0 until openAiTools.length()) {
            val tool = openAiTools.optJSONObject(i) ?: continue
            val functionObj = tool.optJSONObject("function") ?: continue
            val realTool = JSONObject()
            realTool.put("type", "function")
            realTool.put("name", functionObj.optString("name"))
            functionObj.optString("description")?.let { realTool.put("description", it) }
            functionObj.optJSONObject("parameters")?.let { realTool.put("parameters", it) }
            realtimeTools.put(realTool)
        }
        return realtimeTools
    }

    @SuppressLint("MissingPermission")
    fun toggleSession(context: Context) {
        if (_isSessionActive.value) {
            stopSession()
        } else {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED
            ) {
                _error.value = "Record audio permission not granted."
                return
            }
            startSession()
        }
    }

    @SuppressLint("MissingPermission")
    private fun startSession() {
        if (_isSessionActive.value) return
        _status.value = "Connecting to OpenAI Realtime..."
        _error.value = null
        _aiTextMessage.value = ""

        val request = Request.Builder()
            .url(openAIRealtimeUrl)
            .addHeader("Authorization", "Bearer ${BuildConfig.API_KEY}")
            .addHeader("OpenAI-Beta", "realtime=v1") // Required Beta header
            .build()

        Log.i("OpenAILiveAudioVM", "Attempting to connect to: $openAIRealtimeUrl")
        webSocketClient = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i("OpenAILiveAudioVM", "WebSocket Opened. Server: ${response.headers["server"]}")
                viewModelScope.launch {
                    _isSessionActive.value = true // Set this after successful session.created ideally
                    // Wait for session.created from server before considering fully active
                    // For now, let's assume open means we can send initial config
                    initializeSessionSettings(webSocket) // Send session.update
                    startAudioStreaming(webSocket)   // Start microphone
                    startAudioPlayback()             // Start audio output track
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d("OpenAILiveAudioVM", "WebSocket RX: $text")
                handleServerEvent(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                val errorMsg = "WebSocket Failure: ${t.message}. Response: ${response?.message}"
                Log.e("OpenAILiveAudioVM", errorMsg, t)
                viewModelScope.launch {
                    _error.value = "Connection Error: ${t.localizedMessage}"
                    cleanupSessionResources()
                }
            }
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.i("OpenAILiveAudioVM", "WebSocket Closing: $code / $reason")
                viewModelScope.launch { _status.value = "Session closing..." }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.i("OpenAILiveAudioVM", "WebSocket Closed: $code / $reason")
                viewModelScope.launch {
                    _status.value = if (code == 1000) "Session Ended." else "Session Closed: $reason"
                    cleanupSessionResources()
                }
            }
        })
    }

    // In OpenAILiveAudioViewModel.kt

    private fun initializeSessionSettings(webSocket: WebSocket) {
        val sessionConfig = JSONObject().apply {
            // Modalities the session should support for output
            put("modalities", JSONArray().put("audio").put("text")) // Request both audio and text

            put("instructions", "You are a friendly and helpful voice assistant. Respond naturally.") // Your custom instructions

            put("voice", sessionVoice)

            // Audio formats (already corrected to string)
            put("input_audio_format", "pcm16")
            put("output_audio_format", "pcm16") // Server will likely output 24kHz for pcm16

            // Optional: Specify transcription model
            put("input_audio_transcription", JSONObject().apply {
                put("model", "whisper-1") // Or a newer available Whisper variant if specified by OpenAI
            })

            // --- VAD / Turn Detection Configuration ---
            val turnDetectionConfig = JSONObject().apply {
                put("type", "server_vad")
                // put("threshold", 0.5) // Default is often fine, adjust if needed
                // put("prefix_padding_ms", 300) // Default from server log
                put("silence_duration_ms", 700) // **** INCREASED FROM EXAMPLE (500ms) - EXPERIMENT WITH THIS ****
                // Try 700ms, 800ms, 1000ms to reduce interruptions.
                put("create_response", true)    // Server automatically creates a response when VAD detects end of speech.
                put("interrupt_response", true) // Allow user to interrupt AI (barge-in) - this was in your session.created default
            }
            put("turn_detection", turnDetectionConfig)

            // Optional: Temperature for text generation
            put("temperature", 0.8)

            // Optional: Max response tokens (inf means no hard limit by tokens)
            // put("max_response_output_tokens", "inf") // Server log showed this as default

            sessionTools?.let { tools ->
                put("tools", tools)
            }
        }

        val sessionUpdateEvent = JSONObject().apply {
            put("type", "session.update")
            put("session", sessionConfig)
            put("event_id", UUID.randomUUID().toString())
        }
        Log.i("OpenAILiveAudioVM", "Sending session.update config: ${sessionUpdateEvent.toString()}")
        webSocket.send(sessionUpdateEvent.toString())
    }
    @SuppressLint("MissingPermission")
    private fun startAudioStreaming(webSocket: WebSocket) {
        if (recordingJob?.isActive == true) {
            Log.w("OpenAILiveAudioVM", "Audio streaming already active.")
            return
        }

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC, inputSampleRate, inputChannelConfig,
            inputAudioFormat, inputAudioRecordBufferSize
        )
        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            viewModelScope.launch { _error.value = "AudioRecord init failed." }
            stopSession()
            return
        }
        audioRecord?.startRecording()
        Log.i("OpenAILiveAudioVM", "AudioRecord started. Streaming input...")

        recordingJob = recordingScope.launch {
            // Buffer size for reading from AudioRecord, can be smaller than inputAudioRecordBufferSize
            val buffer = ByteArray(inputAudioRecordBufferSize / 4)
            try {
                while (isActive && _isSessionActive.value) { // Check isActive of the coroutine
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                    if (read > 0 && _isSessionActive.value) {
                        val audioChunk = buffer.copyOfRange(0, read)
                        val base64Audio = Base64.encodeToString(audioChunk, Base64.NO_WRAP)

                        val appendEvent = JSONObject().apply {
                            put("type", "input_audio_buffer.append")
                            put("audio", base64Audio)
                            put("event_id", UUID.randomUUID().toString())
                        }
                        webSocket.send(appendEvent.toString())
                    } else if (read < 0) {
                        Log.e("OpenAILiveAudioVM", "AudioRecord read error: $read")
                        viewModelScope.launch { _error.value = "Mic read error." }
                        break // Exit loop on error
                    }
                    delay(20) // Adjust send rate: 1024 bytes @ 16kHz 16bit = 32ms. Send slightly faster.
                }
            } catch (e: Exception) {
                Log.e("OpenAILiveAudioVM", "Audio streaming exception: ${e.message}", e)
                if (_isSessionActive.value) {
                    viewModelScope.launch { _error.value = "Streaming failed." }
                }
            } finally {
                Log.i("OpenAILiveAudioVM", "Audio streaming loop ended.")
                audioRecord?.stop()
                audioRecord?.release()
                audioRecord = null
            }
        }
    }
    private fun startAudioPlayback() {
        viewModelScope.launch(Dispatchers.IO) { // Keep on IO for AudioTrack operations
            Log.i("OpenAILiveAudioVM", "startAudioPlayback: Initializing AudioTrack...")
            try {
                // Ensure previous track is fully released if this function could be called multiple times
                // though current logic suggests it's only called once per session start.
                audioTrack?.release()
                audioTrack = null

                audioTrack = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(outputAudioFormat) // ENCODING_PCM_16BIT
                            .setSampleRate(outputSampleRate)   // 24000 Hz
                            .setChannelMask(outputChannelConfig).build() // CHANNEL_OUT_MONO
                    )
                    // Using the calculated min buffer size is good.
                    // You could experiment with a slightly larger buffer if underruns were an issue,
                    // but minBufferSize is usually the best starting point for low latency.
                    .setBufferSizeInBytes(outputAudioTrackBufferSize)
                    .setTransferMode(AudioTrack.MODE_STREAM).build()

                if (audioTrack?.state != AudioTrack.STATE_INITIALIZED) {
                    Log.e("OpenAILiveAudioVM", "startAudioPlayback: AudioTrack failed to initialize! State: ${audioTrack?.state}")
                    // Post error to UI thread
                    withContext(Dispatchers.Main) {
                        _error.value = "Audio output failed to initialize. State: ${audioTrack?.state}"
                    }
                    return@launch // Exit this coroutine
                }

                audioTrack?.play() // Start playing
                Log.i("OpenAILiveAudioVM", "startAudioPlayback: AudioTrack.play() called. PlayState: ${audioTrack?.playState}. Session Active: ${_isSessionActive.value}")

                // Check play state immediately after calling play()
                if (audioTrack?.playState != AudioTrack.PLAYSTATE_PLAYING) {
                    Log.e("OpenAILiveAudioVM", "startAudioPlayback: AudioTrack did not start playing! PlayState: ${audioTrack?.playState}")
                    withContext(Dispatchers.Main) {
                        _error.value = "Audio output track could not start playing."
                    }
                    // Clean up immediately if it didn't start
                    audioTrack?.stop()
                    audioTrack?.release()
                    audioTrack = null
                    return@launch
                }

                // Consume audio data from the channel
                audioOutChannel.consumeAsFlow().collect { audioData ->
                    // Log.d("OpenAILiveAudioVM", "startAudioPlayback: Received ${audioData.size} bytes from audioOutChannel.") // Can be very spammy

                    // Ensure the track is still valid and playing before writing
                    if (audioTrack != null && audioTrack?.playState == AudioTrack.PLAYSTATE_PLAYING && _isSessionActive.value) {
                        // Log.d("OpenAILiveAudioVM", "startAudioPlayback: Writing ${audioData.size} bytes to AudioTrack.") // Spammy
                        val bytesWritten = audioTrack?.write(audioData, 0, audioData.size, AudioTrack.WRITE_BLOCKING) // Use WRITE_BLOCKING for simplicity first

                        if (bytesWritten != null) {
                            if (bytesWritten < 0) {
                                // Error occurred during write
                                handleAudioTrackWriteError(bytesWritten) // Call helper to log specific error
                                // Potentially break or stop trying to write if error is persistent
                            } else if (bytesWritten < audioData.size) {
                                Log.w("OpenAILiveAudioVM", "startAudioPlayback: Partial write to AudioTrack. Wrote $bytesWritten of ${audioData.size} bytes.")
                                // This might indicate the buffer is full or an issue.
                                // For WRITE_BLOCKING, this shouldn't happen unless there's an error or track is stopped.
                            } else {
                                // Log.d("OpenAILiveAudioVM", "startAudioPlayback: Successfully wrote $bytesWritten bytes.") // Spammy
                            }
                        } else {
                            Log.w("OpenAILiveAudioVM", "startAudioPlayback: audioTrack?.write returned null. Track might have been released concurrently.")
                        }
                    } else {
                        Log.w("OpenAILiveAudioVM", "startAudioPlayback: AudioTrack not in PLAYING state or session inactive. Skipping write. PlayState: ${audioTrack?.playState}, SessionActive: ${_isSessionActive.value}")
                        // If the session is no longer active, we might want to break this collect loop.
                        // The audioOutChannel.close() in cleanupSessionResources should handle this.
                    }
                }
            } catch (e: IllegalStateException) {
                Log.e("OpenAILiveAudioVM", "startAudioPlayback: IllegalStateException (e.g., track not initialized or released): ${e.message}", e)
                withContext(Dispatchers.Main) { _error.value = "Audio playback state error." }
            } catch (e: Exception) { // Catches other exceptions like channel closed, etc.
                Log.e("OpenAILiveAudioVM", "startAudioPlayback: Playback loop exception: ${e.message}", e)
                // Don't set _error.value here if it's just the channel closing normally from stopSession()
                if (_isSessionActive.value) { // Only set error if session was supposed to be active
                    withContext(Dispatchers.Main) { _error.value = "Audio playback critical error." }
                }
            } finally {
                Log.i("OpenAILiveAudioVM", "startAudioPlayback: Playback loop finished or an error occurred. Releasing AudioTrack.")
                audioTrack?.stop()
                audioTrack?.release()
                audioTrack = null
                Log.i("OpenAILiveAudioVM", "startAudioPlayback: AudioTrack released.")
            }
        }
    }





    // Helper function (add this inside your ViewModel or as a private top-level function if preferred)
    private fun OpenAILiveAudioViewModel.handleAudioTrackWriteError(errorCode: Int) {
        val errorMsg = when (errorCode) {
            AudioTrack.ERROR_INVALID_OPERATION -> "Write failed: ERROR_INVALID_OPERATION (Track not properly initialized or in wrong state)"
            AudioTrack.ERROR_BAD_VALUE -> "Write failed: ERROR_BAD_VALUE (Invalid parameters to write)"
            AudioTrack.ERROR_DEAD_OBJECT -> "Write failed: ERROR_DEAD_OBJECT (AudioTrack died)"
            AudioTrack.ERROR -> "Write failed: ERROR (Generic AudioTrack error)"
            else -> "Write failed: Unknown AudioTrack error code: $errorCode"
        }
        Log.e("OpenAILiveAudioVM", errorMsg)
        // Update UI via StateFlow on the main thread
        viewModelScope.launch(Dispatchers.Main) {
            _error.value = errorMsg
        }
    }

    private var currentTextResponseId: String? = null
    private var currentAudioResponseId: String? = null

    private fun handleServerEvent(jsonEvent: String) {
        viewModelScope.launch {
            try {
                val event = JSONObject(jsonEvent)
                val type = event.optString("type")
                val eventId = event.optString("event_id", "N/A") // Useful for debugging

                // Log.d("OpenAILiveAudioVM", "Handling Event: $type, ID: $eventId")

                when (type) {
                    "session.created" -> {
                        currentSessionId = event.optJSONObject("session")?.optString("id")
                        _status.value = "Session active: ${currentSessionId?.take(8)}"
                        Log.i("OpenAILiveAudioVM", "Session created with ID: $currentSessionId")
                    }
                    "session.updated" -> {
                        Log.i("OpenAILiveAudioVM", "Session updated: ${event.optJSONObject("session")}")
                        _status.value = "Session configured."
                    }
                    "input_audio_buffer.speech_started" -> _status.value = "Hearing you..."
                    "input_audio_buffer.speech_stopped" -> _status.value = "Processing your speech..."
                    "response.created" -> {
                        val responseObj = event.getJSONObject("response")
                        val responseId = responseObj.getString("id")
                        Log.i("OpenAILiveAudioVM", "Response created: $responseId")
                        // Potentially track responseId if multiple responses can be in flight
                    }
                    // In handleServerEvent, within the "response.audio.delta" case:
                    "response.audio.delta" -> {
                        val base64AudioChunk = event.optString("delta")
                        if (!base64AudioChunk.isNullOrEmpty()) {
                            try {
                                // Log a small portion to verify it's not obviously wrong (optional, can be spammy)
                                // Log.d("OpenAILiveAudioVM", "RX Audio Delta (first 32 B64 chars): ${base64AudioChunk.take(32)}")
                                val audioBytes = Base64.decode(base64AudioChunk, Base64.NO_WRAP)
                                Log.d("OpenAILiveAudioVM", "Decoded ${audioBytes.size} audio bytes from delta. Sending to audioOutChannel.")
                                if (audioBytes.isNotEmpty()) { // Only send if there's actual data
                                    audioOutChannel.send(audioBytes)
                                } else {
                                    Log.w("OpenAILiveAudioVM", "Decoded audioBytes from delta is empty.")
                                }
                            } catch (e: IllegalArgumentException) {
                                Log.e("OpenAILiveAudioVM", "Base64 decoding failed for audio chunk.", e)
                                _error.value = "Corrupt audio data received (B64)."
                            } catch (e: Exception) {
                                Log.e("OpenAILiveAudioVM", "Error processing audio delta or sending to channel: ${e.message}", e)
                                _error.value = "Error handling received audio."
                            }
                        } else {
                            Log.w("OpenAILiveAudioVM", "Received response.audio.delta with empty/null delta string.")
                        }
                    }
                    "response.audio.delta" -> {
                        val base64AudioChunk = event.optString("delta")
                        if (!base64AudioChunk.isNullOrEmpty()) {
                            try {
                                Log.d("OpenAILiveAudioVM", "Decoding audio chunk, size: ${base64AudioChunk.length}")
                                val audioBytes = Base64.decode(base64AudioChunk, Base64.NO_WRAP)
                                Log.d("OpenAILiveAudioVM", "Decoded ${audioBytes.size} bytes. Sending to audioOutChannel.")
                                audioOutChannel.send(audioBytes) // This is suspend, ensure scope is correct
                                Log.d("OpenAILiveAudioVM", "Sent to audioOutChannel.")
                            } catch (e: IllegalArgumentException) {
                                Log.e("OpenAILiveAudioVM", "Base64 decoding failed for audio chunk.", e)
                            } catch (e: Exception) {
                                Log.e("OpenAILiveAudioVM", "Error sending to audioOutChannel: ${e.message}", e)
                            }
                        }
                    }
                    "response.done" -> {
                        _status.value = "AI finished."
                        _aiTextMessage.value = ""
                        val finalResponse = event.getJSONObject("response")
                        val outputArray = finalResponse.optJSONArray("output")
                        val firstItem = outputArray?.optJSONObject(0)
                        if (firstItem != null && firstItem.optString("type") == "function_call") {
                            val funcName = firstItem.optString("name")
                            val callId = firstItem.optString("call_id")
                            val arguments = firstItem.optString("arguments")
                            toolHandler?.let { handler ->
                                viewModelScope.launch {
                                    val result = handler.executeTool(funcName, arguments)
                                    val itemEvent = JSONObject().apply {
                                        put("type", "conversation.item.create")
                                        put("item", JSONObject().apply {
                                            put("type", "function_call_output")
                                            put("call_id", callId)
                                            put("output", result)
                                        })
                                        put("event_id", UUID.randomUUID().toString())
                                    }
                                    webSocketClient?.send(itemEvent.toString())
                                    val respCreate = JSONObject().apply {
                                        put("type", "response.create")
                                        put("event_id", UUID.randomUUID().toString())
                                    }
                                    webSocketClient?.send(respCreate.toString())
                                }
                            }
                        } else {
                            firstItem?.optString("text")?.let { finalText ->
                                if (finalText.isNotBlank()) _aiTextMessage.value = finalText
                            }
                        }
                        Log.i("OpenAILiveAudioVM", "Response done. Final text: ${_aiTextMessage.value}")
                        currentTextResponseId = null
                        currentAudioResponseId = null
                    }
                    "error", "invalid_request_error" -> {
                        val errorMessage = event.optString("message", "Unknown server error")
                        Log.e("OpenAILiveAudioVM", "Server Error (ID: $eventId, Type: $type): $errorMessage. Full: $event")
                        _error.value = "Server: $errorMessage"
                        // Potentially stop session on critical errors
                        // stopSession()
                    }
                    else -> Log.w("OpenAILiveAudioVM", "Unhandled server event (ID: $eventId): $type")
                }
            } catch (e: Exception) {
                Log.e("OpenAILiveAudioVM", "Error parsing server event JSON: $jsonEvent", e)
                _error.value = "Corrupt server message."
            }
        }
    }

    fun stopSession() {
        if (!_isSessionActive.value && webSocketClient == null) {
            Log.d("OpenAILiveAudioVM", "Session already stopped or not started.")
            return
        }
        Log.i("OpenAILiveAudioVM", "Stop session requested by client.")
        _status.value = "Stopping session..."
        cleanupSessionResources()
    }

    private fun cleanupSessionResources() {
        _isSessionActive.value = false

        recordingJob?.cancel() // Cancels the coroutine
        recordingJob = null
        // AudioRecord is released inside recordingJob's finally block

        webSocketClient?.close(1000, "Client session ended")
        webSocketClient = null

        audioOutChannel.close() // Signals end to playback loop
        // AudioTrack is released inside its loop's finally block

        Log.i("OpenAILiveAudioVM", "All session resources cleaned up.")
        // Update status only if no overriding error occurred
        if (_error.value == null || _status.value.startsWith("Stopping")) {
            _status.value = "Idle. Session ended."
        }
    }

    // Call this if VAD is disabled and user signals end of turn (e.g., releases button)
    fun signalUserTurnEnded() {
        if (!_isSessionActive.value || webSocketClient == null) return
        viewModelScope.launch {
            try {
                val commitEvent = JSONObject().apply {
                    put("type", "input_audio_buffer.commit")
                    put("event_id", UUID.randomUUID().toString())
                }
                webSocketClient?.send(commitEvent.toString())
                Log.i("OpenAILiveAudioVM", "Sent input_audio_buffer.commit")

                val responseCreateEvent = JSONObject().apply {
                    put("type", "response.create")
                    // Optionally specify modalities if not default:
                    // put("response", JSONObject().apply {
                    //     put("modalities", JSONArray().put("audio").put("text"))
                    // })
                    put("event_id", UUID.randomUUID().toString())
                }
                webSocketClient?.send(responseCreateEvent.toString())
                Log.i("OpenAILiveAudioVM", "Sent response.create")
                _status.value = "You: Done. Waiting for AI..."
            } catch (e: Exception) {
                Log.e("OpenAILiveAudioVM", "Error signaling user turn end: ${e.message}", e)
                _error.value = "Error sending turn end."
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        Log.i("OpenAILiveAudioVM", "ViewModel cleared, cleaning up resources.")
        cleanupSessionResources()
        // OkHttpClient's dispatcher should be shut down if it's shared across app,
        // but if it's only for this VM, this is a good place.
        // However, OkHttpClients are designed to be shared and long-lived.
        // okHttpClient.dispatcher.executorService.shutdown()
        // okHttpClient.connectionPool.evictAll()
    }
}