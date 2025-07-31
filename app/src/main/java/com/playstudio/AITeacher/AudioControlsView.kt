package com.playstudio.aiteacher

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.*
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.playstudio.aiteacher.pricing.AIModel
import kotlinx.coroutines.*
import java.io.File
import java.io.IOException

/**
 * Comprehensive audio controls view for voice interaction
 * Features:
 * - Voice recording with visual feedback
 * - Model selection with audio capabilities
 * - Voice selection (alloy, echo, fable, etc.)
 * - Audio playback controls
 * - Real-time audio level visualization
 */
class AudioControlsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

    companion object {
        private const val TAG = "AudioControlsView"
        private const val RECORDING_ANIMATION_DURATION = 1000L
    }

    // UI Components
    private lateinit var recordButton: ImageButton
    private lateinit var playbackButton: ImageButton
    private lateinit var voiceSpinner: Spinner
    private lateinit var audioModelSpinner: Spinner
    private lateinit var recordingIndicator: View
    private lateinit var audioLevelView: ProgressBar
    private lateinit var statusText: TextView
    private lateinit var audioModeToggle: ToggleButton

    // Audio components
    private var mediaRecorder: MediaRecorder? = null
    private var mediaPlayer: MediaPlayer? = null
    private var audioApiHandler: AudioApiHandler? = null
    private var currentRecordingFile: File? = null
    private var isRecording = false
    private var isPlaying = false

    // Animation
    private var recordingAnimator: ObjectAnimator? = null
    private var audioLevelAnimator: ValueAnimator? = null

    // Callbacks
    var onAudioRecorded: ((File) -> Unit)? = null
    var onAudioModelSelected: ((AIModel) -> Unit)? = null
    var onVoiceSelected: ((String) -> Unit)? = null
    var onAudioModeToggled: ((Boolean) -> Unit)? = null

    // Data
    private var availableAudioModels = listOf<AIModel>()
    private var selectedModel: AIModel? = null
    private var selectedVoice = "alloy"
    private var isAudioModeEnabled = false

    init {
        setupView()
    }

    private fun setupView() {
        // Inflate the layout
        LayoutInflater.from(context).inflate(R.layout.view_audio_controls, this, true)
        
        initializeViews()
        setupVoiceSpinner()
        setupAudioModelSpinner()
        setupRecordButton()
        setupPlaybackButton()
        setupAudioModeToggle()
        setupAnimations()
        
        // Initialize audio handler
        audioApiHandler = AudioApiHandler(context)
    }

    private fun initializeViews() {
        recordButton = findViewById(R.id.btn_record)
        playbackButton = findViewById(R.id.btn_playback)
        voiceSpinner = findViewById(R.id.spinner_voice)
        audioModelSpinner = findViewById(R.id.spinner_audio_model)
        recordingIndicator = findViewById(R.id.recording_indicator)
        audioLevelView = findViewById(R.id.audio_level)
        statusText = findViewById(R.id.status_text)
        audioModeToggle = findViewById(R.id.toggle_audio_mode)
    }

    private fun setupVoiceSpinner() {
        val voices = AudioApiHandler.SUPPORTED_VOICES
        val voiceAdapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, voices)
        voiceAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        voiceSpinner.adapter = voiceAdapter

        voiceSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedVoice = voices[position]
                onVoiceSelected?.invoke(selectedVoice)
                updateStatusText("Voice: ${selectedVoice.capitalize()}")
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupAudioModelSpinner() {
        val adapter = object : ArrayAdapter<AIModel>(context, android.R.layout.simple_spinner_item, availableAudioModels) {
            override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                val view = super.getView(position, convertView, parent) as TextView
                view.text = getItem(position)?.displayName
                return view
            }

            override fun getDropDownView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                val view = super.getDropDownView(position, convertView, parent) as TextView
                val model = getItem(position)
                view.text = "${model?.displayName} ${if (model?.supportsRealtime == true) "[RT]" else "[AUDIO]"}"
                return view
            }
        }
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        audioModelSpinner.adapter = adapter

        audioModelSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedModel = availableAudioModels.getOrNull(position)
                selectedModel?.let { model ->
                    onAudioModelSelected?.invoke(model)
                    updateStatusText("Model: ${model.displayName}")
                    updateUIForModel(model)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupRecordButton() {
        recordButton.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startRecording()
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    stopRecording()
                    true
                }
                else -> false
            }
        }
    }

    private fun setupPlaybackButton() {
        playbackButton.setOnClickListener {
            currentRecordingFile?.let { file ->
                if (isPlaying) {
                    stopPlayback()
                } else {
                    startPlayback(file)
                }
            }
        }
        playbackButton.isEnabled = false
    }

    private fun setupAudioModeToggle() {
        audioModeToggle.setOnCheckedChangeListener { _, isChecked ->
            isAudioModeEnabled = isChecked
            onAudioModeToggled?.invoke(isChecked)
            updateUIForAudioMode(isChecked)
        }
    }

    private fun setupAnimations() {
        // Recording animation - pulsing red circle
        recordingAnimator = ObjectAnimator.ofFloat(recordingIndicator, "alpha", 0.3f, 1.0f).apply {
            duration = RECORDING_ANIMATION_DURATION
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.REVERSE
        }

        // Audio level animation
        audioLevelAnimator = ValueAnimator.ofInt(0, 100).apply {
            duration = 200
            addUpdateListener { animator ->
                audioLevelView.progress = animator.animatedValue as Int
            }
        }
    }

    private fun startRecording() {
        if (isRecording) return

        // Check for microphone permission
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) 
            != PackageManager.PERMISSION_GRANTED) {
            updateStatusText("Microphone permission required")
            Log.w(TAG, "Microphone permission not granted")
            return
        }

        try {
            // Create recording file in M4A format (supported by Whisper API)
            currentRecordingFile = File(context.cacheDir, "voice_${System.currentTimeMillis()}.m4a")

            // Setup MediaRecorder with proper error handling
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                try {
                    setAudioSource(MediaRecorder.AudioSource.MIC)
                    setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    setOutputFile(currentRecordingFile?.absolutePath)
                    prepare()
                    start()
                } catch (e: RuntimeException) {
                    Log.e(TAG, "MediaRecorder setup failed", e)
                    release()
                    throw e
                }
            }

            isRecording = true
            updateRecordingState()
            startRecordingAnimation()
            updateStatusText("Recording... Hold to continue")

        } catch (e: IOException) {
            Log.e(TAG, "Recording failed - IOException", e)
            updateStatusText("Recording failed: ${e.message}")
            stopRecording()
        } catch (e: RuntimeException) {
            Log.e(TAG, "Recording failed - RuntimeException", e)
            updateStatusText("Recording failed: Microphone in use or unavailable")
            stopRecording()
        } catch (e: Exception) {
            Log.e(TAG, "Recording failed - General exception", e)
            updateStatusText("Recording failed: ${e.message}")
            stopRecording()
        }
    }

    private fun stopRecording() {
        if (!isRecording) return

        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            isRecording = false

            stopRecordingAnimation()
            updateRecordingState()

            currentRecordingFile?.let { file ->
                if (file.exists() && file.length() > 0) {
                    playbackButton.isEnabled = true
                    updateStatusText("Audio recorded (${file.length() / 1024}KB)")
                    onAudioRecorded?.invoke(file)
                } else {
                    updateStatusText("Recording too short")
                }
            }

        } catch (e: Exception) {
            updateStatusText("Error stopping recording: ${e.message}")
        }
    }

    private fun startPlayback(audioFile: File) {
        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(audioFile.absolutePath)
                setOnCompletionListener {
                    stopPlayback()
                }
                setOnErrorListener { _, what, extra ->
                    updateStatusText("Playback error: $what-$extra")
                    stopPlayback()
                    true
                }
                prepare()
                start()
            }

            isPlaying = true
            updatePlaybackState()
            updateStatusText("Playing audio...")

        } catch (e: IOException) {
            updateStatusText("Playback failed: ${e.message}")
        }
    }

    private fun stopPlayback() {
        mediaPlayer?.apply {
            if (isPlaying) {
                stop()
            }
            release()
        }
        mediaPlayer = null
        isPlaying = false
        updatePlaybackState()
        updateStatusText("Playback stopped")
    }

    private fun startRecordingAnimation() {
        recordingIndicator.visibility = View.VISIBLE
        recordingAnimator?.start()
        
        // Simulate audio level updates
        GlobalScope.launch {
            while (isRecording) {
                val level = (Math.random() * 100).toInt()
                withContext(Dispatchers.Main) {
                    audioLevelView.progress = level
                }
                delay(100)
            }
        }
    }

    private fun stopRecordingAnimation() {
        recordingAnimator?.cancel()
        recordingIndicator.visibility = View.GONE
        audioLevelView.progress = 0
    }

    private fun updateRecordingState() {
        recordButton.setImageResource(
            if (isRecording) R.drawable.ic_stop else R.drawable.ic_mic
        )
        
        val background = recordButton.background as? GradientDrawable
        background?.setColor(
            ContextCompat.getColor(
                context,
                if (isRecording) R.color.red else R.color.trust_blue
            )
        )
    }

    private fun updatePlaybackState() {
        playbackButton.setImageResource(
            if (isPlaying) R.drawable.ic_stop else R.drawable.ic_send
        )
    }

    private fun updateUIForModel(model: AIModel) {
        // Update voice spinner availability
        voiceSpinner.isEnabled = model.supportsAudioOutput && model.supportedVoices.isNotEmpty()
        
        // Update controls based on model capabilities
        val supportsInput = model.supportsAudioInput
        val supportsOutput = model.supportsAudioOutput
        
        recordButton.isEnabled = supportsInput
        recordButton.alpha = if (supportsInput) 1.0f else 0.5f
        
        // Show/hide realtime features
        if (model.supportsRealtime) {
            updateStatusText("Realtime model - Low latency voice chat")
        }
    }

    private fun updateUIForAudioMode(enabled: Boolean) {
        val alpha = if (enabled) 1.0f else 0.7f
        recordButton.alpha = alpha
        playbackButton.alpha = alpha
        voiceSpinner.alpha = alpha
        audioModelSpinner.alpha = alpha
        
        if (enabled) {
            updateStatusText("Audio mode enabled")
        } else {
            updateStatusText("Audio mode disabled")
            if (isRecording) stopRecording()
            if (isPlaying) stopPlayback()
        }
    }

    private fun updateStatusText(text: String) {
        statusText.text = text
    }

    // Public methods for external control
    fun setAvailableModels(models: List<AIModel>) {
        availableAudioModels = models.filter { it.supportsAudio() }
        (audioModelSpinner.adapter as? ArrayAdapter<*>)?.notifyDataSetChanged()
        
        if (availableAudioModels.isNotEmpty() && selectedModel == null) {
            audioModelSpinner.setSelection(0)
        }
    }

    fun setSelectedVoice(voice: String) {
        val index = AudioApiHandler.SUPPORTED_VOICES.indexOf(voice)
        if (index >= 0) {
            voiceSpinner.setSelection(index)
        }
    }

    fun setAudioModeEnabled(enabled: Boolean) {
        audioModeToggle.isChecked = enabled
    }
    
    /**
     * Check if the app has microphone permission
     */
    fun hasMicrophonePermission(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * Check if audio recording is available (permissions + hardware)
     */
    fun isAudioRecordingAvailable(): Boolean {
        return hasMicrophonePermission() && context.packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE)
    }

    fun getSelectedModel(): AIModel? = selectedModel
    fun getSelectedVoice(): String = selectedVoice
    fun isAudioModeEnabled(): Boolean = isAudioModeEnabled
    fun getCurrentRecording(): File? = currentRecordingFile

    fun cleanup() {
        if (isRecording) stopRecording()
        if (isPlaying) stopPlayback()
        recordingAnimator?.cancel()
        audioLevelAnimator?.cancel()
    }
}