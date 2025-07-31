package com.playstudio.aiteacher

import android.content.Context
import android.media.MediaPlayer
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import com.playstudio.aiteacher.pricing.AIModel

/**
 * Comprehensive Audio API Handler for OpenAI audio features
 * Supports:
 * - Chat Completions with audio input/output (gpt-4o-audio-preview)
 * - Speech-to-Text transcription (whisper models)
 * - Text-to-Speech generation (TTS models)
 * - Audio streaming and playback
 */
class AudioApiHandler(private val context: Context) {
    
    companion object {
        private const val TAG = "AudioApiHandler"
        private const val OPENAI_BASE_URL = "https://api.openai.com/v1/"
        
        // Supported audio formats
        const val FORMAT_WAV = "wav"
        const val FORMAT_MP3 = "mp3"
        const val FORMAT_M4A = "m4a"
        const val FORMAT_FLAC = "flac"
        
        // Supported voices for TTS
        val SUPPORTED_VOICES = listOf("alloy", "echo", "fable", "onyx", "nova", "shimmer")
    }
    
    private val apiKey = BuildConfig.API_KEY
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)     // 1 minute connection timeout
        .readTimeout(1800, TimeUnit.SECONDS)      // 30 minutes for very large audio files
        .writeTimeout(1800, TimeUnit.SECONDS)     // 30 minutes for 1-hour recording uploads
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("Authorization", "Bearer $apiKey")
                .build()
            chain.proceed(request)
        }
        .build()
    
    /**
     * Send chat completion with audio input/output support
     * Supports models like gpt-4o-audio-preview
     */
    suspend fun chatCompletionWithAudio(
        model: com.playstudio.aiteacher.pricing.AIModel,
        messages: List<ChatMessage>,
        audioInput: File? = null,
        voice: String = "alloy",
        audioFormat: String = FORMAT_WAV,
        temperature: Double = 0.7,
        maxTokens: Int? = null
    ): AudioChatResponse = withContext(Dispatchers.IO) {
        
        if (!model.supportsAudio()) {
            throw IllegalArgumentException("Model ${model.displayName} does not support audio")
        }
        
        val requestBody = buildAudioChatRequest(
            model = model,
            messages = messages,
            audioInput = audioInput,
            voice = voice,
            audioFormat = audioFormat,
            temperature = temperature,
            maxTokens = maxTokens
        )
        
        val request = Request.Builder()
            .url("${OPENAI_BASE_URL}chat/completions")
            .post(requestBody.toRequestBody("application/json".toMediaTypeOrNull()))
            .build()
        
        Log.d(TAG, "Sending audio chat completion request")
        
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errorBody = response.body?.string()
                Log.e(TAG, "Audio chat completion failed: $errorBody")
                throw IOException("Audio chat completion failed: ${response.code} - $errorBody")
            }
            
            val responseBody = response.body?.string()
                ?: throw IOException("Empty response body")
            
            parseAudioChatResponse(responseBody)
        }
    }
    
    /**
     * Transcribe audio file to text using Whisper models
     */
    suspend fun transcribeAudio(
        audioFile: File,
        model: String = "whisper-1",
        language: String? = null,
        prompt: String? = null,
        responseFormat: String = "json",
        temperature: Double = 0.0
    ): TranscriptionResponse = withContext(Dispatchers.IO) {
        
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", audioFile.name, audioFile.asRequestBody("audio/*".toMediaTypeOrNull()))
            .addFormDataPart("model", model)
            .apply {
                language?.let { addFormDataPart("language", it) }
                prompt?.let { addFormDataPart("prompt", it) }
                addFormDataPart("response_format", responseFormat)
                addFormDataPart("temperature", temperature.toString())
            }
            .build()
        
        val request = Request.Builder()
            .url("${OPENAI_BASE_URL}audio/transcriptions")
            .post(requestBody)
            .build()
        
        Log.d(TAG, "Transcribing audio file: ${audioFile.name}")
        
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errorBody = response.body?.string()
                Log.e(TAG, "Audio transcription failed: $errorBody")
                throw IOException("Transcription failed: ${response.code} - $errorBody")
            }
            
            val responseBody = response.body?.string()
                ?: throw IOException("Empty transcription response")
            
            parseTranscriptionResponse(responseBody, responseFormat)
        }
    }
    
    /**
     * Convert text to speech using OpenAI TTS models
     */
    suspend fun textToSpeech(
        text: String,
        model: String = "tts-1",
        voice: String = "alloy",
        responseFormat: String = FORMAT_MP3,
        speed: Double = 1.0
    ): File = withContext(Dispatchers.IO) {
        
        if (!SUPPORTED_VOICES.contains(voice)) {
            throw IllegalArgumentException("Unsupported voice: $voice")
        }
        
        val requestBody = JSONObject().apply {
            put("model", model)
            put("input", text)
            put("voice", voice)
            put("response_format", responseFormat)
            put("speed", speed)
        }
        
        val request = Request.Builder()
            .url("${OPENAI_BASE_URL}audio/speech")
            .post(requestBody.toString().toRequestBody("application/json".toMediaTypeOrNull()))
            .build()
        
        Log.d(TAG, "Generating speech for text: ${text.take(50)}...")
        
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errorBody = response.body?.string()
                Log.e(TAG, "Text-to-speech failed: $errorBody")
                throw IOException("TTS failed: ${response.code} - $errorBody")
            }
            
            val audioBytes = response.body?.bytes()
                ?: throw IOException("Empty audio response")
            
            // Save audio to temporary file
            val outputFile = File(context.cacheDir, "tts_${System.currentTimeMillis()}.$responseFormat")
            FileOutputStream(outputFile).use { it.write(audioBytes) }
            
            Log.d(TAG, "Speech generated and saved to: ${outputFile.absolutePath}")
            outputFile
        }
    }
    
    /**
     * Play audio file using MediaPlayer
     */
    suspend fun playAudioFile(audioFile: File): MediaPlayer = withContext(Dispatchers.Main) {
        val mediaPlayer = MediaPlayer().apply {
            setDataSource(audioFile.absolutePath)
            prepare()
            start()
        }
        
        Log.d(TAG, "Playing audio file: ${audioFile.name}")
        mediaPlayer
    }
    
    /**
     * Convert base64 audio data to file
     */
    suspend fun saveBase64Audio(
        base64Data: String,
        format: String = FORMAT_WAV,
        filename: String? = null
    ): File = withContext(Dispatchers.IO) {
        
        val audioBytes = Base64.decode(base64Data, Base64.NO_WRAP)
        val outputFile = File(context.cacheDir, filename ?: "audio_${System.currentTimeMillis()}.$format")
        
        FileOutputStream(outputFile).use { it.write(audioBytes) }
        
        Log.d(TAG, "Saved base64 audio to: ${outputFile.absolutePath}")
        outputFile
    }
    
    private fun buildAudioChatRequest(
        model: com.playstudio.aiteacher.pricing.AIModel,
        messages: List<ChatMessage>,
        audioInput: File?,
        voice: String,
        audioFormat: String,
        temperature: Double,
        maxTokens: Int?
    ): String {
        
        val requestJson = JSONObject().apply {
            put("model", model.modelId)
            put("modalities", JSONArray(model.getSupportedModalities()))
            
            if (model.supportsAudioOutput) {
                put("audio", JSONObject().apply {
                    put("voice", voice)
                    put("format", audioFormat)
                })
            }
            
            put("temperature", temperature)
            maxTokens?.let { put("max_completion_tokens", it) }
            
            // Convert messages to API format
            val messagesArray = JSONArray()
            messages.forEach { message ->
                val messageJson = JSONObject().apply {
                    put("role", if (message.isUser) "user" else "assistant")
                    
                    // For now, just send text content - audio files will be handled separately
                    put("content", message.content)
                }
                messagesArray.put(messageJson)
            }
            
            // Add audio input if provided separately
            if (audioInput != null && model.supportsAudioInput) {
                val audioBase64 = encodeAudioFileToBase64(audioInput)
                val audioMessage = JSONObject().apply {
                    put("role", "user")
                    put("content", JSONArray().apply {
                        put(JSONObject().apply {
                            put("type", "input_audio")
                            put("input_audio", JSONObject().apply {
                                put("data", audioBase64)
                                put("format", getAudioFormat(audioInput))
                            })
                        })
                    })
                }
                messagesArray.put(audioMessage)
            }
            
            put("messages", messagesArray)
        }
        
        return requestJson.toString()
    }
    
    private fun parseAudioChatResponse(responseBody: String): AudioChatResponse {
        try {
            val json = JSONObject(responseBody)
            val choices = json.getJSONArray("choices")
            val firstChoice = choices.getJSONObject(0)
            val message = firstChoice.getJSONObject("message")
            
            val content = message.optString("content", "")
            val audioData = message.optJSONObject("audio")?.optString("data")
            
            val usage = json.optJSONObject("usage")
            val inputTokens = usage?.optInt("prompt_tokens") ?: 0
            val outputTokens = usage?.optInt("completion_tokens") ?: 0
            
            return AudioChatResponse(
                textContent = content,
                audioData = audioData,
                inputTokens = inputTokens,
                outputTokens = outputTokens
            )
        } catch (e: JSONException) {
            Log.e(TAG, "Error parsing audio chat response", e)
            throw IOException("Failed to parse audio chat response: ${e.message}")
        }
    }
    
    private fun parseTranscriptionResponse(responseBody: String, format: String): TranscriptionResponse {
        return try {
            when (format) {
                "json" -> {
                    val json = JSONObject(responseBody)
                    TranscriptionResponse(
                        text = json.getString("text"),
                        language = json.optString("language"),
                        duration = json.optDouble("duration"),
                        segments = parseSegments(json.optJSONArray("segments"))
                    )
                }
                "text" -> TranscriptionResponse(text = responseBody)
                else -> TranscriptionResponse(text = responseBody)
            }
        } catch (e: JSONException) {
            Log.e(TAG, "Error parsing transcription response", e)
            TranscriptionResponse(text = responseBody) // Fallback to raw text
        }
    }
    
    private fun parseSegments(segmentsArray: JSONArray?): List<TranscriptionSegment> {
        if (segmentsArray == null) return emptyList()
        
        val segments = mutableListOf<TranscriptionSegment>()
        for (i in 0 until segmentsArray.length()) {
            val segment = segmentsArray.getJSONObject(i)
            segments.add(TranscriptionSegment(
                id = segment.optInt("id"),
                start = segment.optDouble("start"),
                end = segment.optDouble("end"),
                text = segment.optString("text"),
                confidence = segment.optDouble("avg_logprob")
            ))
        }
        return segments
    }
    
    private fun encodeAudioFileToBase64(audioFile: File): String {
        return Base64.encodeToString(audioFile.readBytes(), Base64.NO_WRAP)
    }
    
    private fun getAudioFormat(audioFile: File): String {
        return when (audioFile.extension.lowercase()) {
            "wav" -> FORMAT_WAV
            "mp3" -> FORMAT_MP3
            "m4a" -> FORMAT_M4A  // M4A is supported by Whisper API
            "3gp" -> FORMAT_MP3  // Treat 3GP as MP3 for OpenAI compatibility
            "aac" -> FORMAT_MP3  // Treat AAC as MP3 for OpenAI compatibility
            "flac" -> FORMAT_FLAC // FLAC is supported by Whisper
            else -> FORMAT_MP3   // Default to MP3
        }
    }
}

/**
 * Data classes for audio API responses
 */
data class AudioChatResponse(
    val textContent: String,
    val audioData: String? = null, // Base64 encoded audio
    val inputTokens: Int = 0,
    val outputTokens: Int = 0
)

data class TranscriptionResponse(
    val text: String,
    val language: String? = null,
    val duration: Double? = null,
    val segments: List<TranscriptionSegment> = emptyList()
)

data class TranscriptionSegment(
    val id: Int,
    val start: Double,
    val end: Double,
    val text: String,
    val confidence: Double
)

/**
 * Audio-enhanced extension properties for existing ChatMessage
 */
// Extension properties for audio support - these will be added to the existing ChatMessage class
data class AudioChatMessage(
    val baseMessage: ChatMessage,
    val audioFile: File? = null,
    val audioData: String? = null // Base64 encoded audio for responses
)