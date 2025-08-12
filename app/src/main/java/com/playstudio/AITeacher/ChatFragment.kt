package com.playstudio.aiteacher



import android.provider.AlarmClock
//import TooltipDialog
//import WhisperHelper
import android.Manifest
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Typeface
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.support.annotation.RequiresApi
import android.text.Html
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.text.style.UnderlineSpan
import android.util.Log
import android.view.*
import android.view.animation.AnimationUtils
import android.view.inputmethod.InputMethodManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.menu.MenuPopupHelper
import androidx.appcompat.widget.PopupMenu
import androidx.core.app.ActivityCompat
import androidx.core.content.ContentProviderCompat.requireContext
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.startActivity
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.GridLayoutManager
import com.airbnb.lottie.LottieAnimationView
import com.bumptech.glide.Glide
import com.canhub.cropper.CropImage
import com.canhub.cropper.CropImageView
import com.google.android.gms.ads.AdRequest
import com.playstudio.aiteacher.R
import com.playstudio.aiteacher.BuildConfig
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.rewarded.RewardItem
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.playstudio.aiteacher.databinding.FragmentChatBinding
import com.playstudio.aiteacher.utils.FileUtils
import com.playstudio.aiteacher.AudioControlsView
import com.playstudio.aiteacher.api.AIFunctionCallManager
import com.playstudio.aiteacher.api.EducationalFunctions
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import com.playstudio.aiteacher.firestore.FirestoreChatManager
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.TimeUnit
import android.util.Base64
import androidx.core.view.MenuProvider
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.google.gson.annotations.SerializedName
// DISABLED OLD IMPLEMENTATION - Using new RealtimeVoiceAgent instead
// import com.playstudio.aiteacher.viewmodel.OpenAILiveAudioViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import java.net.URLEncoder


import android.provider.CalendarContract
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import kotlinx.coroutines.CoroutineScope
//import com.playstudio.AITeacher.R
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.RequestBody.Companion.asRequestBody
import kotlin.coroutines.resume



class ChatFragment : Fragment() {
    // Add this data class inside your ChatFragment class
    data class Citation(
        val url: String,
        val title: String,
        val startIndex: Int,
        val endIndex: Int
    )
    // Add near your other data classes
    data class WebResult(
        val title: String,
        val url: String,
        val snippet: String?,
        val imageUrl: String?
    )

    data class GoogleSearchResponse(
        val items: List<SearchItem>?
    )

    data class SearchItem(
        val title: String,
        val link: String,
        val snippet: String?,
        val pagemap: PageMap?
    )

    data class PageMap(
        @SerializedName("cse_image") val images: List<SearchImage>?
    )

    data class SearchImage(
        val src: String
    )

    private fun showCitationDialog(citation: Citation) {
        AlertDialog.Builder(requireContext())
            .setTitle("Source: ${citation.title}")
            .setMessage(citation.url)
            .setPositiveButton("Visit") { _, _ ->
                // Load URL in WebView
                binding.webView.visibility = View.VISIBLE
                binding.webView.loadUrl(citation.url)
            }
            .setNegativeButton("Copy") { _, _ ->
                val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Source URL", citation.url)
                clipboard.setPrimaryClip(clip)
                showCustomToast("URL copied to clipboard")
            }
            .setNeutralButton("Close") { dialog, _ ->
                binding.webView.visibility = View.GONE
                dialog.dismiss()
            }
            .show()
    }

    // In your Activity or Fragment
    // Computer use button removed in new layout
    private lateinit var computerUseResponseTextView: TextView

    private var speechRecognizer: SpeechRecognizer? = null

    // Use the new ListAdapter
    internal lateinit var chatAdapter: com.playstudio.aiteacher.ChatAdapter
    private var consecutiveApiKeyErrors = 0
    private val MAX_API_KEY_ERRORS_BEFORE_UPDATE = 3
    private var outputFile: String = ""
    private var meetingTranscript = StringBuilder()

    // Web search related constants
    private val WEB_SEARCH_MODELS = listOf(
        "gpt-4o-search-preview",
        "gpt-4o-mini-search-preview"
    )
    private var isWebSearchEnabled = false





    // At the top of ChatFragment, with other viewModel declarations
    // DISABLED OLD IMPLEMENTATION - Using new RealtimeVoiceAgent instead
    // private val openAILiveAudioViewModel: OpenAILiveAudioViewModel by viewModels()


    // Manager for OpenAI computer-use API
    //private val computerUseManager by lazy { ComputerUseManager(requireActivity()) }
    private lateinit var chatTextView: TextView
    private var interstitialAd: InterstitialAd? = null
    private var isInterstitialAdLoaded = false
    private val PREFS_NAME = "app_prefs"
    private val FIRST_LAUNCH_KEY = "first_launch"
    private val INTERACTION_COUNT_KEY = "interaction_count"
    private val RATING_REMINDER_COUNT_KEY = "rating_reminder_count"
    private var isLoading = false
    private var _binding: FragmentChatBinding? = null
    internal val binding get() = _binding!!
    // private lateinit var chatAdapter: ChatAdapter
    internal val chatMessages = mutableListOf<ChatMessage>()
    private var rewardedAd: RewardedAd? = null
    private var canSendMessage = false
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)  // Increased for GPT-4o models
        .readTimeout(120, TimeUnit.SECONDS)    // Increased to 2 minutes for GPT-4o response time
        .writeTimeout(60, TimeUnit.SECONDS)    // Increased for large request payloads
        .build()
    private val keyManager = com.playstudio.aiteacher.security.FirestoreKeyManager.getInstance()
    
    // Get API key with Firestore fallback to BuildConfig
    private fun getApiKey(provider: String = "openai"): String? {
        val key = keyManager.getApiKeyWithFallback(provider)
        Log.d("ChatFragment", "🔑 Getting API key for $provider: ${key?.takeLast(4) ?: "null"}")
        return key
    }
    private var currentModel = "gpt-3.5-turbo"
    private var conversationId: String? = null
    private var isTtsEnabled = false
    // Holds conversation history across tool calls in a single turn
    private val currentConversationHistoryForToolCall = mutableListOf<JSONObject>()
    private val chatHistoryKey = "chat_history"
    private var isFollowUpEnabled = true

    // Realtime Voice Agent
    private var realtimeVoiceAgent: RealtimeVoiceAgent? = null
    private var isRealtimeMode = false
    
    // AI Function Call Manager for educational functions
    private lateinit var aiFunctionCallManager: AIFunctionCallManager
    private var currentVoiceAgent: RealtimeVoiceAgent.VoiceAgentConfig? = null
    private var voiceAgentCallback: RealtimeVoiceAgent.VoiceAgentCallback? = null
    private var voiceRecordingJob: kotlinx.coroutines.Job? = null
    private var pendingVoiceAgentType: String? = null // Store agent type during permission request

    // Voice conversation state management
    private var isAICurrentlySpeaking = false
    private var currentAudioTracks = mutableListOf<android.media.AudioTrack>()
    private val audioTrackLock = java.util.concurrent.locks.ReentrantLock()
    private var hasInterruptedCurrentResponse = false  // Prevent multiple interruptions
    private var lastInterruptTime = 0L  // Debounce interruption calls
    private var lastAiSpeakStartTime = 0L  // Track when AI started speaking to prevent immediate interruption

    // Enhanced voice activity detection - increased threshold to prevent interrupting AI
    private var audioLevelThreshold = 75 // Increased from 25 to prevent false speech detection during AI playback
    private var consecutiveQuietSamples = 0 // Counter for quiet periods
    private var consecutiveLoudSamples = 0 // Counter for speech periods
    
    // Audio mode management
    private var originalAudioMode = android.media.AudioManager.MODE_NORMAL
    private var userSpeechDetected = false // More accurate user speech detection
    private var lastAudioLevelCheck = 0L // Timing for audio level checks
    
    // Fallback VAD for triggering responses
    private var lastSpeechDetectedTime = 0L
    private var lastManualTriggerTime = 0L
    private var hasTriggeredResponse = false
    
    // Audio playback mode control - prevent dual playback systems
    private var isAudioTrackMode = true // true = AudioTrack, false = MediaPlayer only
    private var consecutiveAudioTrackFailures = 0
    private val maxAudioTrackFailures = 3
    
    // Sequential MediaPlayer fallback queue
    private var fallbackMediaPlayer: android.media.MediaPlayer? = null
    private val audioChunkQueue = mutableListOf<ByteArray>()
    private var isProcessingAudioQueue = false
    
    // Streaming AudioTrack for real-time audio playback
    private var streamingAudioTrack: android.media.AudioTrack? = null

    private lateinit var requestAudioPermissionLauncher: ActivityResultLauncher<String> // Assuming this is declared


    private var subscriptionClickListener: OnSubscriptionClickListener? = null
    private lateinit var requestMultiplePermissionsLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var pickImageLauncher: ActivityResultLauncher<String> // For "image/*"
    private lateinit var pickDocumentLauncher: ActivityResultLauncher<Array<String>> // For specific MIME types
    // In ChatFragment class
    private val okHttpClient = OkHttpClient.Builder() /* ... */ .build()



    companion object {

        private const val COMPUTER_USE_PERMISSION_REQUEST = 1001
        // Add with your other constants
        private const val REQUEST_RECORD_AUDIO_PERMISSION = 101

        private const val GOOGLE_API_KEY = "YOUR_NEW_API_KEY" // Replace with your new key
        private const val SEARCH_ENGINE_ID = "YOUR_SEARCH_ENGINE_ID"
        private const val WEB_SEARCH_ENABLED = true


        // SharedPreferences Keys
        private const val PREFS_NAME_APP = "app_prefs" // Main app prefs
        private const val PREFS_NAME_CHAT = "chat_prefs" // Specific to chat
        // ... other keys

        // Add these:
        private var isLoadingMoreMessages = false
        private val MESSAGES_PAGE_SIZE = 20 // Or your desired page size
        private const val REQUEST_CODE_MEETING_FILE = 1004


        // Daily Limits (ensure all needed are here)

        private const val DAILY_LIMIT_GPT4_MINI = 75
        private const val DAILY_LIMIT_GPT_DEFAULT = 100
        private const val DAILY_LIMIT_GEMINI_TEXT = 40 // For text-based Gemini
        private const val DAILY_LIMIT_O3_MINI = 50
        private const val DAILY_GENERAL_MESSAGE_LIMIT = 20 // Fallback general limit


        private const val REQUEST_IMAGE_CAPTURE = 1
        private const val CAMERA_REQUEST_CODE = 100
        private const val PERMISSION_REQUEST_CODE = 100
        private const val REQUEST_CODE_SPEECH_INPUT = 2
        private const val WRITE_EXTERNAL_STORAGE_PERMISSION_REQUEST_CODE = 105
        private const val PICK_DOCUMENT_REQUEST_CODE = 106
        private const val PICK_IMAGE_REQUEST_CODE = 107

        private const val PREFS_NAME = "prefs"
        private const val LAST_RESET_TIME_KEY = "last_reset_time"
        private const val MESSAGE_COUNT_KEY = "message_count"
        private const val DAILY_MESSAGE_LIMIT = 10

        // Define daily limits for each model
        private const val DAILY_LIMIT_GPT4 = 50
        private const val DAILY_LIMIT_DALLE = 20
        private const val DAILY_LIMIT_TTS = 30
        private const val DAILY_LIMIT_GEMINI = 40
        private const val DAILY_LIMIT_DEEPSEEK = 40
        //private const val REQUEST_RECORD_AUDIO_PERMISSION = 300
        private const val REQUEST_STORAGE_PERMISSION = 301
    }


    private lateinit var requestPermissionLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var captureImageLauncher: ActivityResultLauncher<Intent>
    private lateinit var cropImageLauncher: ActivityResultLauncher<Intent>

    private val subscriptionViewModel: SubscriptionViewModel by activityViewModels()

    // Subscription status now checked via SharedPreferences - see isUserCurrentlySubscribed()

    private var suggestedMessage: String? = null
    private var selectedModel: String? = null

    interface OnSubscriptionClickListener {
        fun onSubscriptionClick()
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is OnSubscriptionClickListener) {
            subscriptionClickListener = context
        } else {
            throw RuntimeException("$context must implement OnSubscriptionClickListener")
        }
    }

    // Convert OpenAI-style tool definitions to Anthropic's format
    private fun convertToolsForClaude(openAiTools: JSONArray): JSONArray {
        val claudeTools = JSONArray()
        for (i in 0 until openAiTools.length()) {
            val tool = openAiTools.getJSONObject(i)
            val functionObj = tool.optJSONObject("function") ?: continue
            val name = functionObj.optString("name")
            val description = functionObj.optString("description")
            val parameters = functionObj.optJSONObject("parameters")

            val claudeTool = JSONObject().apply {
                put("name", name)
                put("description", description)
                put("input_schema", parameters)
                put("type", "custom")
            }
            claudeTools.put(claudeTool)
        }
        return claudeTools
    }

    override fun onDetach() {
        super.onDetach()
        subscriptionClickListener = null
    }


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        _binding = FragmentChatBinding.inflate(inflater, container, false)
        setHasOptionsMenu(true) // Ensure the fragment can handle menu options
        return binding.root
    }


    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        menu.clear()
        inflater.inflate(R.menu.harmburger_menu, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onResume() {
        super.onResume()
        (requireActivity() as? AppCompatActivity)?.supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setHomeAsUpIndicator(R.drawable.ic_arrow_back)

        }

        // Initialize usage tracking and subscription management
        initializeUsageTracking()

        // Setup subscription UI manager for this fragment
        subscriptionUIManager.setupForFragment(this)

        // Update subscription status display and credit balance
        updateSubscriptionStatusDisplay()
        updateCreditBalanceDisplay()
        // Check if user should get model recommendations
        lifecycleScope.launch {
            delay(2000) // Small delay to let UI settle
            showModelRecommendationDialog()
        }

        //loadInterstitialAd() // Load the interstitial ad when the fragment resumes
    }



    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_new_conversation -> {
                startNewConversation()
                true
            }
            R.id.menu_meeting_record -> {
                showMeetingRecordingDialog()
                true
            }
            R.id.menu_live_voice_chat -> {
                try {
                    if (isRealtimeMode) {
                        stopRealtimeVoiceChat()
                    } else {
                        Log.d("ChatFragment", "Live Voice button clicked, showing agent selection")
                        showVoiceAgentSelectionDialog()
                    }
                } catch (e: Exception) {
                    Log.e("ChatFragment", "Error handling realtime voice button click", e)
                    showCustomToast("Error: ${e.message}")
                }
                true
            }
            R.id.menu_settings -> {
                // Navigate to settings activity
                val intent = Intent(requireContext(), SettingsActivity::class.java)
                startActivity(intent)
                true
            }
            R.id.menu_usage_dashboard -> {
                // Navigate to usage dashboard
                val intent = Intent(requireContext(), UsageDashboardActivity::class.java)
                startActivity(intent)
                true
            }
            R.id.menu_help -> {
                showHelpDialog()
                true
            }
            R.id.menu_report -> {
                showReportDialog()
                true
            }
            R.id.menu_profile -> {
                // Navigate to profile activity (if exists)
                try {
                    val intent = Intent(requireContext(), Class.forName("com.playstudio.AITeacher.profile.ProfileActivity"))
                    startActivity(intent)
                } catch (e: ClassNotFoundException) {
                    showCustomToast("Profile feature coming soon!")
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }





    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d("ChatFragment", "onViewCreated called")

        // Initialize AI Function Call Manager for educational features
        aiFunctionCallManager = AIFunctionCallManager(requireContext(), getApiKey("openai") ?: "")

        // Handle specialized AI modes from MainActivity
        handleSpecializedAIMode()

        // Load persisted preferences early so conversation ID and other
        // settings are initialized before any messages are processed
        loadSharedPrefs()

        // Initialize audio handler and features
        initializeAudioHandler()

        // Initialize realtime voice agent
        initializeRealtimeVoiceAgent()

        // Initialize structured outputs system
        initializeStructuredOutputs(client)
        Log.d("ChatFragment", "▶️ StructuredAPIHandler initialized with client: $client")

        selectedVoice = loadSelectedVoice()
        // Voice selection now handled within message input area

        // Initialize the views
        arguments?.getString("recognized_text")?.let { text ->
            binding.messageEditText.setText(text)
            binding.messageEditText.setSelection(text.length)
        }
        // Handle suggested message from arguments
        suggestedMessage = arguments?.getString("suggested_message")
        suggestedMessage?.let {
            binding.messageEditText.setText(it)
            binding.messageEditText.setSelection(it.length) // Move cursor to end
        }

        // Handle extracted text from arguments
        arguments?.getString("extracted_text")?.let { text ->
            setExtractedText(text)
        }


        // Check for email content in arguments
        arguments?.getString("email_content")?.let { emailContent ->
            binding.messageEditText.setText(emailContent)
        }

        // Handle auto show image picker from MainActivity shortcut
        arguments?.getBoolean("auto_show_image_picker", false)?.let { autoShow ->
            if (autoShow) {
                // Automatically show image picker dialog after a short delay
                Handler(Looper.getMainLooper()).postDelayed({
                    showImageOrDocumentPickerDialog()
                }, 800) // Small delay to ensure UI is fully loaded
            }
        }

        // Handle auto show document picker from Homework Helper shortcut
        arguments?.getBoolean("auto_show_document_picker", false)?.let { autoShow ->
            if (autoShow) {
                // Automatically show document/image picker dialog for homework assistance
                Handler(Looper.getMainLooper()).postDelayed({
                    showImageOrDocumentPickerDialog()
                }, 800) // Small delay to ensure UI is fully loaded
            }
        }

        // Handle auto model selection from AI Image Generator shortcut
        arguments?.getBoolean("auto_select_model", false)?.let { autoSelect ->
            if (autoSelect) {
                arguments?.getString("selected_model")?.let { model ->
                    // Automatically select the specified model (e.g., gpt-image-1)
                    Handler(Looper.getMainLooper()).postDelayed({
                        setSelectedModel(model)
                    }, 500) // Small delay to ensure UI is initialized
                }
            }
        }

        // Handle auto start live voice chat from Intent extra
        arguments?.getBoolean("auto_start_live_voice", false)?.let { autoStart ->
            Log.d("ChatFragment", "🚀 Voice Chat Auto-Start Check: autoStart=$autoStart")
            if (autoStart) {
                val agentType = arguments?.getString("voice_agent_type", "general_assistant")
                Log.d("ChatFragment", "🎙️ Voice Chat Button Clicked - Starting live voice chat with agent: $agentType")
                // Automatically start live voice chat after a short delay
                Handler(Looper.getMainLooper()).postDelayed({
                    try {
                        Log.d("ChatFragment", "🔥 STARTING LIVE VOICE CHAT (NOT TTS) with agent: $agentType")
                        startRealtimeVoiceChat(agentType ?: "general_assistant")
                    } catch (e: Exception) {
                        Log.e("ChatFragment", "💥 Error auto-starting live voice chat", e)
                        showCustomToast("Error starting live voice chat: ${e.message}")
                    }
                }, 1000) // Delay to ensure UI is fully initialized
            }
        }

        // Voice selection styling now handled within message input area
        // Initialize the chatAdapter with the lifecycleScope
        // Initialize the chatAdapter
        chatAdapter = com.playstudio.aiteacher.ChatAdapter(
            onCitationClicked = { citation ->
                showCitationDialog(citation)
            },
            onFollowUpQuestionClicked = { question ->
                binding.messageEditText.setText(question)
                binding.messageEditText.setSelection(question.length)
                // Optionally, you might want to also send the message or hide keyboard
            },
            onLoadMoreRequested = {
                // Check if already loading to prevent multiple requests
                if (!isLoadingMoreMessages) {
                    Log.d("ChatFragment", "onLoadMoreRequested triggered")
                    loadOlderMessages()
                }
            }
        )

        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext()).apply {
                stackFromEnd = true
            }
            adapter = chatAdapter
            setHasFixedSize(true)
            itemAnimator = null // Or DefaultItemAnimator()
            setItemViewCacheSize(20)
        }

        // Voice selection now accessed through long-press on voice button in message input



        arguments?.getString("recognized_text")?.let { text ->
            binding.messageEditText.setText(text)
            binding.messageEditText.setSelection(text.length)
        }


        // Set up the RecyclerView
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext()).apply {
                stackFromEnd = true // Ensure new messages appear at the bottom
            }
            adapter = chatAdapter
            setHasFixedSize(true) // Optimize for fixed-size RecyclerView
            itemAnimator = null // Disable item animations for better performance
            setItemViewCacheSize(20) // Increase cache size for smoother scrolling
        }

        updateActiveModelButton(getDisplayNameForModel(currentModel))
        switchUiForModel(currentModel)

        // TTS is now automatically handled by audio mode for audio-enabled models
        binding.activeModelButton.setOnClickListener {
            showChatGptOptionsDialog()
        }

        // Check if it's the first launch
        /*val isFirstLaunch = sharedPreferences.getBoolean(FIRST_LAUNCH_KEY, true)

        if (isFirstLaunch) {
            // Show the tooltip dialog
            val tooltipDialog = TooltipDialog()
            tooltipDialog.show(parentFragmentManager, "TooltipDialog")

            // Update the shared preferences to indicate that the dialog has been shown
            sharedPreferences.edit().putBoolean(FIRST_LAUNCH_KEY, false).apply()
        }*/

        suggestedMessage = arguments?.getString("suggested_message") ?: savedInstanceState?.getString("suggested_message")
        selectedModel = arguments?.getString("selected_model") ?: savedInstanceState?.getString("selected_model")

        if (suggestedMessage != null) {
            binding.messageEditText.setText(suggestedMessage)
            Log.d("ChatFragment", "Suggested message set: $suggestedMessage")
        }

        Log.d("ChatFragment", "Suggested message: $suggestedMessage")
        Log.d("ChatFragment", "Selected model: $selectedModel")

        val conversationId = arguments?.getString("conversation_id")
        initializeChat(selectedModel, conversationId)

        // Update subscription status display and show credit balance after initialization
        updateSubscriptionStatusDisplay()
        updateCreditBalanceDisplay()

        binding.historyButton.setOnClickListener {
            showChatHistoryDialog()
        }

        // Initialize with the selected model
        arguments?.getString("selected_model")?.let {
            currentModel = it
            updateUIForCurrentModel()
            switchUiForModel(currentModel)
        }
        captureImageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                // Image was saved to currentPhotoPath, now process it
                val file = File(currentPhotoPath)
                if (file.exists()) {
                    // Add the image to the gallery
                    galleryAddPic(currentPhotoPath)

                    // Process the saved image file
                    val bitmap = BitmapFactory.decodeFile(currentPhotoPath)
                    if (bitmap != null) {
                        showImageProcessingOptions(bitmap)
                    } else {
                        showCustomToast("Failed to process captured image")
                    }
                } else {
                    showCustomToast("Image file not found")
                }
            } else {
                showCustomToast("Image capture cancelled")
            }
        }
// In onViewCreated or onResume of ChatFragment

        cropImageLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    val resultUri: Uri? = CropImage.getActivityResult(result.data)?.uri
                    if (resultUri != null) {
                        try {
                            val bitmap = ImageDecoder.decodeBitmap(
                                ImageDecoder.createSource(requireContext().contentResolver, resultUri)
                            )
                            showImageProcessingOptions(bitmap)
                        } catch (e: Exception) {
                            Log.e("ChatFragment", "Failed to decode bitmap from URI: ${e.message}")
                            showCustomToast("Failed to process the cropped image.")
                        }
                    } else {
                        Log.e("ChatFragment", "Cropped image URI is null.")
                        showCustomToast("Failed to retrieve the cropped image.")
                    }
                } else {
                    Log.e("ChatFragment", "Image cropping failed.")
                    showCustomToast("Image cropping failed.")
                }
            }

        requestPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val isCameraPermissionGranted = permissions[Manifest.permission.CAMERA] ?: false
            if (isCameraPermissionGranted) {
                dispatchTakePictureIntent()
            } else {
                Log.e("ChatFragment", "Camera permission denied.")
                showCustomToast("Camera permission is required to use this feature")
            }
        }
        // Initialize your ActivityResultLauncher, e.g., for permissions
        requestAudioPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
                if (isGranted) {
                    showCustomToast("Audio permission granted.")
                    // Handle pending voice agent type for live voice chat
                    if (pendingVoiceAgentType != null) {
                        Log.d("ChatFragment", "Permission granted, starting live voice chat with agent: $pendingVoiceAgentType")
                        startRealtimeVoiceChat(pendingVoiceAgentType!!)
                        pendingVoiceAgentType = null
                    } else {
                        Log.d("ChatFragment", "Permission granted, showing voice agent selection")
                        showVoiceAgentSelectionDialog()
                    }
                } else {
                    showCustomToast("Audio permission denied. Cannot use voice features.")
                    pendingVoiceAgentType = null
                }
            }








        // In ChatFragment.kt - initializeActivityLaunchers() or class level
        requestAudioPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
                if (isGranted) {
                    showCustomToast("Audio permission granted. Please tap the button again.")
                } else {
                    showCustomToast("Audio permission denied. Cannot use voice features.")
                }
            }

        // --- OpenAI Live Audio ViewModel Integration ---
        // Make sure binding.openAISessionButton is a valid ID in your XML
        // and that _binding is initialized in onCreateView

        // Set the OnClickListener for openAISessionButton HERE, inside onViewCreated
        binding.openAISessionButton.setOnClickListener {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            } else {
                // DISABLED OLD IMPLEMENTATION - Using new RealtimeVoiceAgent instead
                // openAILiveAudioViewModel.toggleSession(requireContext())
            }
        }

        // Set up other listeners for OpenAI controls if any (e.g., openAISignalTurnEndButton)
        binding.openAISignalTurnEndButton.setOnClickListener {
            // DISABLED OLD IMPLEMENTATION - Using new RealtimeVoiceAgent instead
            // openAILiveAudioViewModel.signalUserTurnEnded()
        }


        // Your observers for openAILiveAudioViewModel states
        // DISABLED OLD IMPLEMENTATION - Using new RealtimeVoiceAgent instead
        // viewLifecycleOwner.lifecycleScope.launch {
        //     openAILiveAudioViewModel.isSessionActive.collect { isActive ->
        //         binding.openAISessionButton.text = if (isActive) "🛑 Stop OpenAI Session" else "🎙️ Start OpenAI Session"
        //         binding.openAISignalTurnEndButton.visibility = if (isActive) View.VISIBLE else View.GONE // Example visibility toggle
        //         if (isActive && currentModel == "openai-realtime-voice") { // Be specific about which mode hides it
        //             binding.messageInputLayout.visibility = View.GONE
        //         } else if (currentModel != "gemini-voice-chat") { // Don't show if Gemini voice is active
        //             binding.messageInputLayout.visibility = View.VISIBLE
        //         }
        //     }
        // }

        // DISABLED OLD IMPLEMENTATION - Using new RealtimeVoiceAgent instead
        // viewLifecycleOwner.lifecycleScope.launch {
        //     openAILiveAudioViewModel.status.collect { status ->
        //         binding.openAIStatusTextView.text = "OpenAI Live: $status"
        //     }
        // }

        // DISABLED OLD IMPLEMENTATION - Using new RealtimeVoiceAgent instead
        // viewLifecycleOwner.lifecycleScope.launch {
        //     openAILiveAudioViewModel.error.collect { error ->
        //         error?.let {
        //             binding.openAIStatusTextView.append("\nError: $it")
        //             showCustomToast("OpenAI Live Error: $it")
        //         }
        //     }
        // }

        // DISABLED OLD IMPLEMENTATION - Using new RealtimeVoiceAgent instead
        // viewLifecycleOwner.lifecycleScope.launch {
        //     openAILiveAudioViewModel.aiTextMessage.collect { text ->
        //         binding.openAIAiResponseTextView.text = text
        //     }
        // }
        // --- End OpenAI Live Audio ViewModel Integration ---
// ... (rest of your onViewCreated)


        // In your onViewCreated()
        binding.webView.apply {
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                    // Handle URL loading within WebView
                    view.loadUrl(url)
                    return true
                }
            }
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
        }

        (requireActivity() as? AppCompatActivity)?.supportActionBar?.title = "Chat with AITeacher"







        binding.scanTextButton.setOnClickListener { showImageOrDocumentPickerDialog() }

        // Initialize speech recognizer with listener
        initializeSpeechRecognizer()


        // Set up voice input button with enhanced functionality
        binding.voiceInputButton.setOnClickListener {
            if (checkAndRequestPermissions()) {
                startEnhancedVoiceRecording()
            }
        }

        // Long press to access voice options menu
        binding.voiceInputButton.setOnLongClickListener {
            showVoiceOptionsMenu()
            true
        }


        // setupMenuProvider() // Menu handled by onCreateOptionsMenu
        initializeActivityLaunchers()
        setupUIListeners()
        observeViewModels() // For OpenAI Live Audio, Gemini Live Audio, Subscription
        binding.shareButton.setOnClickListener { shareLastResponse() }

        // Removed programmatic background overrides to allow XML styling to work properly
        // binding.shareButton.background = ContextCompat.getDrawable(requireContext(), R.drawable.fading_background)
        // binding.historyButton.background = ContextCompat.getDrawable(requireContext(), R.drawable.fading_background)
        arguments?.getString("suggested_message")?.let { suggestedMessage ->
            binding.messageEditText.setText(suggestedMessage)
        }

        loadChatHistory()

        arguments?.getString("conversation_json")?.let { conversationJson ->
            loadConversationFromJson(conversationJson)
        }

        PDFBoxResourceLoader.init(requireContext())

        requestPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val isWritePermissionGranted = permissions[Manifest.permission.WRITE_EXTERNAL_STORAGE] ?: false
            val isReadPermissionGranted = permissions[Manifest.permission.READ_EXTERNAL_STORAGE] ?: false

            if (isWritePermissionGranted || isReadPermissionGranted) {
                // Permissions granted
            } else {
                showCustomToast("Storage permission is required to save the document")
            }
        }

        subscriptionViewModel.isAdFree.observe(viewLifecycleOwner, Observer { isAdFree ->
            updateSubscriptionStatus(isAdFree, subscriptionViewModel.expirationTime.value ?: 0L)
        })

        subscriptionViewModel.expirationTime.observe(viewLifecycleOwner, Observer { expirationTime ->
            updateSubscriptionStatus(subscriptionViewModel.isAdFree.value ?: false, expirationTime)
        })

        binding.messageEditText.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                hideKeyboard()
                binding.messageEditText.clearFocus()
            }
            false
        }
        binding.messageEditText.customSelectionActionModeCallback = object : ActionMode.Callback {
            override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                menu.clear()
                mode.menuInflater.inflate(R.menu.custom_selection_menu, menu)
                return true
            }

            override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
                return false
            }

            override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                return when (item.itemId) {
                    R.id.menu_copy -> {
                        copyHighlightedText()
                        mode.finish()
                        true
                    }
                    R.id.menu_delete -> {
                        deleteHighlightedText()
                        mode.finish()
                        true
                    }
                    else -> false
                }
            }

            override fun onDestroyActionMode(mode: ActionMode) {}
        }
        arguments?.getString("prefilled_question")?.let { question ->
            setQuestionText(question)
        }

        binding.messageEditText.customSelectionActionModeCallback = object : ActionMode.Callback {
            override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                menu.clear()
                mode.menuInflater.inflate(R.menu.custom_selection_menu, menu)
                return true
            }

            override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
                return false
            }

            override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                return when (item.itemId) {
                    R.id.menu_copy -> {
                        copyHighlightedText()
                        mode.finish()
                        true
                    }
                    else -> false
                }
            }

            override fun onDestroyActionMode(mode: ActionMode) {}
        }

        // Retrieve subscription status from arguments
        val isAdFree = arguments?.getBoolean("is_ad_free", false) ?: false
        val expirationTime = arguments?.getLong("expiration_time", 0) ?: 0
        updateSubscriptionStatus(isAdFree, expirationTime)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("suggested_message", suggestedMessage)
        outState.putString("selected_model", selectedModel)
    }



    private fun setupMenuProvider() {
        requireActivity().addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.harmburger_menu, menu)
            }
            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    R.id.menu_new_conversation -> { startNewConversation(); true }
                    R.id.menu_help -> { showHelpDialog(); true }
                    R.id.menu_report -> { showReportDialog(); true }
                    android.R.id.home -> { requireActivity().onBackPressedDispatcher.onBackPressed(); true } // Handle back arrow
                    else -> false
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
        (requireActivity() as? AppCompatActivity)?.supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setHomeAsUpIndicator(R.drawable.ic_arrow_back) // Ensure you have this drawable
            title = "AI Teacher" // Or dynamic title
        }
    }

    private fun initializeActivityLaunchers() {
        requestAudioPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
                if (isGranted) {
                    showCustomToast("Audio permission granted.")
                    // Re-trigger the action that needed permission
                    if (currentModel == "openai-realtime-voice" && binding.openaiLiveAudioControls.visibility == View.VISIBLE) {
                        // DISABLED OLD IMPLEMENTATION - Using new RealtimeVoiceAgent instead
                        // openAILiveAudioViewModel.toggleSession(requireContext())
                    }
                    // else if (currentModel == "gemini-voice-chat" && binding.geminiLiveAudioControls.visibility == View.VISIBLE) {
                    //    geminiLiveAudioViewModel.toggleRecording(requireContext())
                    // }
                    else if (binding.voiceInputButton.visibility == View.VISIBLE) { // For standard STT
                        startVoiceRecognition()
                    }
                } else {
                    showCustomToast("Audio permission denied.")
                }
            }

        requestMultiplePermissionsLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            if (permissions[Manifest.permission.CAMERA] == true) {
                dispatchTakePictureIntent()
            } else if (permissions[Manifest.permission.WRITE_EXTERNAL_STORAGE] == true ||
                permissions[Manifest.permission.READ_EXTERNAL_STORAGE] == true ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && permissions[Manifest.permission.READ_MEDIA_IMAGES] == true)
            ) {
                // Storage permission granted, action might depend on what triggered it
            } else {
                showCustomToast("Required permissions not granted.")
            }
        }
// Enhanced button click handler with permission checks


        // Initialize captureImageLauncher, cropImageLauncher, pickImageLauncher, pickDocumentLauncher
        // ... (your existing launcher initializations, ensure contexts are correct)
        //captureImageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { /* ... */ }
        //cropImageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { /* ... */ }

        pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let { processSelectedFile(it) }
        }
        pickDocumentLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            uri?.let { processSelectedFile(it) }
        }
    }




    private fun setupChatRecyclerView() {
        chatAdapter = com.playstudio.aiteacher.ChatAdapter(
            onCitationClicked = { showCitationDialog(it) },
            onFollowUpQuestionClicked = { question ->
                binding.messageEditText.setText(question)
                // Optionally send message or just prefill
            },
            onLoadMoreRequested = {
                // Implement if you have pagination
            }
        )
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext()).apply { stackFromEnd = true }
            adapter = chatAdapter
            // itemAnimator = null // Consider DefaultItemAnimator for better UX if no issues
        }
    }

    /**
     * Handle specialized AI modes passed from MainActivity
     */
    private fun handleSpecializedAIMode() {
        val chatMode = arguments?.getString("chat_mode")
        val featureName = arguments?.getString("feature_name")
        val aiSpecialty = arguments?.getString("ai_specialty")
        
        Log.d("ChatFragment", "Handling AI mode: $chatMode, feature: $featureName, specialty: $aiSpecialty")

        when (chatMode) {
            "voice" -> {
                setupVoiceChatMode()
            }
            "document" -> {
                setupDocumentIntelligenceMode()
            }
            "image_generation" -> {
                setupImageGenerationMode()
            }
            "image_generation_basic" -> {
                setupBasicImageGenerationMode()
            }
            "email" -> {
                setupEmailAssistantMode()
            }
            "math" -> {
                setupMathSolverMode()
            }
            "science" -> {
                setupScienceAssistantMode()
            }
            "creative_hub" -> {
                setupCreativeToolsHub()
            }
            "academic_hub" -> {
                setupAcademicToolsHub()
            }
            "productivity_hub" -> {
                setupProductivityToolsHub()
            }
        }

        // Set activity title based on feature name
        featureName?.let { name ->
            (requireActivity() as? AppCompatActivity)?.supportActionBar?.title = name
        }
    }

    private fun setupVoiceChatMode() {
        // Enable voice features
        arguments?.getBoolean("enable_voice", false)?.let { enableVoice ->
            if (enableVoice) {
                // Show voice input controls
                binding.voiceInputButton.visibility = View.VISIBLE
                // Enable TTS if specified
                arguments?.getBoolean("enable_tts", false)?.let { enableTts ->
                    if (enableTts) {
                        // Initialize text-to-speech
                        Log.d("ChatFragment", "Enabling TTS for voice chat mode")
                    }
                }
            }
        }
    }

    private fun setupDocumentIntelligenceMode() {
        // Show file upload capabilities
        arguments?.getBoolean("enable_file_upload", false)?.let { enableUpload ->
            if (enableUpload) {
                // Show document upload button
                Log.d("ChatFragment", "Enabling document upload for intelligence mode")
                showCustomToast("Ready for document analysis - tap to upload files")
            }
        }
    }

    private fun setupImageGenerationMode() {
        // Setup for premium image generation
        val selectedModel = arguments?.getString("selected_model", "dall-e-3")
        selectedModel?.let { model ->
            Log.d("ChatFragment", "Setting up image generation with model: $model")
            currentModel = model
            showCustomToast("AI Image Generator ready - describe your image")
        }
    }

    private fun setupBasicImageGenerationMode() {
        // Setup for basic image generation with limits
        val generationLimit = arguments?.getInt("generation_limit", 3)
        Log.d("ChatFragment", "Basic image generation mode - limit: $generationLimit")
        showCustomToast("Basic Image Generator (${generationLimit} generations)")
    }

    private fun setupEmailAssistantMode() {
        // Setup email-specific AI assistance
        arguments?.getBoolean("enable_templates", false)?.let { enableTemplates ->
            if (enableTemplates) {
                Log.d("ChatFragment", "Email assistant with templates enabled")
                showCustomToast("Email Assistant ready - I can help with professional emails")
            }
        }
    }

    private fun setupMathSolverMode() {
        // Setup math-specific features
        arguments?.getBoolean("enable_latex", false)?.let { enableLatex ->
            if (enableLatex) {
                Log.d("ChatFragment", "Math solver with LaTeX support enabled")
            }
        }
        arguments?.getBoolean("enable_step_by_step", false)?.let { enableSteps ->
            if (enableSteps) {
                Log.d("ChatFragment", "Step-by-step math solutions enabled")
                showCustomToast("Math Solver ready - I'll solve problems step by step")
            }
        }
    }

    private fun setupScienceAssistantMode() {
        // Setup science-specific features
        arguments?.getBoolean("enable_diagrams", false)?.let { enableDiagrams ->
            if (enableDiagrams) {
                Log.d("ChatFragment", "Science assistant with diagrams enabled")
            }
        }
        arguments?.getBoolean("enable_experiments", false)?.let { enableExperiments ->
            if (enableExperiments) {
                Log.d("ChatFragment", "Science experiments feature enabled")
                showCustomToast("Science Assistant ready - ask about any scientific concept")
            }
        }
    }

    private fun setupCreativeToolsHub() {
        val availableTools = arguments?.getStringArray("available_tools")
        availableTools?.let { tools ->
            Log.d("ChatFragment", "Creative tools available: ${tools.joinToString()}")
            showCustomToast("Creative Hub loaded - ${tools.size} tools available")
        }
    }

    private fun setupAcademicToolsHub() {
        val availableTools = arguments?.getStringArray("available_tools")
        availableTools?.let { tools ->
            Log.d("ChatFragment", "Academic tools available: ${tools.joinToString()}")
            showCustomToast("Academic Hub loaded - ${tools.size} tools available")
        }
    }

    private fun setupProductivityToolsHub() {
        val availableTools = arguments?.getStringArray("available_tools")
        availableTools?.let { tools ->
            Log.d("ChatFragment", "Productivity tools available: ${tools.joinToString()}")
            showCustomToast("Productivity Hub loaded - ${tools.size} tools available")
        }
    }

    private fun setupUIListeners() {
        binding.sendButton.setOnClickListener {
            val userMessage = binding.messageEditText.text.toString().trim()
            if (userMessage.isNotEmpty()) {
                Log.d("ChatFragment", "🚀 Send button clicked with message: '$userMessage'")
                // Check if we should use enhanced rendering for educational content
                if (shouldUseEnhancedRendering(userMessage)) {
                    Log.d("ChatFragment", "📚 Using ENHANCED structured path (enhancedSendMessage)")
                    enhancedSendMessage(userMessage)
                } else {
                    Log.d("ChatFragment", "💬 Using REGULAR chat path (processUserMessageSend)")
                    processUserMessageSend(userMessage)
                }
            }
        }

        // Enable/disable send button based on text input
        binding.messageEditText.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val hasText = s?.toString()?.trim()?.isNotEmpty() == true
                binding.sendButton.isEnabled = hasText
                binding.sendButton.alpha = if (hasText) 1.0f else 0.5f
            }
        })

        // Voice input functionality is now handled above in the main setup

        // Hamburger menu button listener
        binding.hamburgerMenuButton.setOnClickListener {
            // Show the options menu when hamburger menu is clicked with custom styling
            val contextThemeWrapper = androidx.appcompat.view.ContextThemeWrapper(requireContext(), R.style.PremiumPopupMenu)
            val popup = androidx.appcompat.widget.PopupMenu(contextThemeWrapper, binding.hamburgerMenuButton)
            popup.menuInflater.inflate(R.menu.harmburger_menu, popup.menu)
            popup.setOnMenuItemClickListener { menuItem ->
                onOptionsItemSelected(menuItem)
            }
            popup.show()
        }

        binding.scanTextButton.setOnClickListener { showImageOrDocumentPickerDialog() }
        binding.activeModelButton.setOnClickListener { showChatGptOptionsDialog() }
        binding.historyButton.setOnClickListener { showChatHistoryDialog() }
        binding.tierButton.setOnClickListener { openSubscriptionActivity() }
        binding.shareButton.setOnClickListener { shareLastResponse() }

        // Record Meeting and Live Chat buttons (now visible in secondary actions)
        binding.meetingRecordButton.setOnClickListener { toggleMeetingRecording() }
        binding.realtimeVoiceButton.setOnClickListener { toggleLiveChat() }

        // OpenAI Live Audio Controls
        binding.openAISessionButton.setOnClickListener {
            if (checkAndRequestAudioPermission(REQUEST_RECORD_AUDIO_PERMISSION)) {
                // DISABLED OLD IMPLEMENTATION - Using new RealtimeVoiceAgent instead
                // openAILiveAudioViewModel.toggleSession(requireContext())
            }
        }
        binding.openAISignalTurnEndButton.setOnClickListener {
            // DISABLED OLD IMPLEMENTATION - Using new RealtimeVoiceAgent instead
            // openAILiveAudioViewModel.signalUserTurnEnded()
        }


        // Gemini Live Audio Controls (if used)
        // binding.geminiRecordButton.setOnClickListener {
        //     if (checkAndRequestAudioPermission(REQUEST_RECORD_AUDIO_PERMISSION_GEMINI)) { // Different request code if needed
        //         geminiLiveAudioViewModel.toggleRecording(requireContext())
        //     }
        // }
        // binding.geminiResetButton.setOnClickListener { geminiLiveAudioViewModel.resetSession() }

        // Voice selection dialog now accessible via long-press on voice button
        // ... other listeners

    }







    // In ChatFragment.kt

    private fun observeViewModels() {
        // DISABLED OLD IMPLEMENTATION - Using new RealtimeVoiceAgent instead
        // // OpenAI Live Audio ViewModel Observers
        // viewLifecycleOwner.lifecycleScope.launch {
        //     repeatOnLifecycle(Lifecycle.State.STARTED) {
        //         openAILiveAudioViewModel.isSessionActive.collect { isActive ->
        //             binding.openAISessionButton.text = if (isActive) "🛑 Stop OpenAI" else "🎙️ OpenAI Voice"
        //             binding.openAISignalTurnEndButton.visibility = if (isActive) View.VISIBLE else View.GONE
        //             if (isActive && currentModel == "openai-realtime-voice") {
        //                 binding.messageInputLayout.visibility = View.GONE
        //             }
        //             // Don't manage messageInputLayout visibility for other models here, switchUiForModel handles it
        //         }
        //     }
        // }
        // DISABLED OLD IMPLEMENTATION - Using new RealtimeVoiceAgent instead
        // viewLifecycleOwner.lifecycleScope.launch {
        //     repeatOnLifecycle(Lifecycle.State.STARTED) {
        //         openAILiveAudioViewModel.status.collect { status -> binding.openAIStatusTextView.text = "OpenAI: $status" }
        //     }
        // }
        // DISABLED OLD IMPLEMENTATION - Using new RealtimeVoiceAgent instead
        // viewLifecycleOwner.lifecycleScope.launch {
        //     repeatOnLifecycle(Lifecycle.State.STARTED) {
        //         openAILiveAudioViewModel.error.collect { error -> error?.let { showCustomToast("OpenAI Error: $it") } }
        //     }
        // }
        // DISABLED OLD IMPLEMENTATION - Using new RealtimeVoiceAgent instead
        // viewLifecycleOwner.lifecycleScope.launch {
        //     repeatOnLifecycle(Lifecycle.State.STARTED) {
        //         openAILiveAudioViewModel.aiTextMessage.collect { text ->
        //             if (openAILiveAudioViewModel.isSessionActive.value) { // Only update if session is active
        //                 binding.openAIAiResponseTextView.text = text
        //                 // Consider adding this text as a ChatMessage if appropriate for your UI
        //             }
        //         }
        //     }
        // }

        // Gemini Live Audio ViewModel Observers (if using)
        // ...

        // Subscription ViewModel Observers
        subscriptionViewModel.isAdFree.observe(viewLifecycleOwner) { isAdFree ->
            updateSubscriptionStatus(isAdFree, subscriptionViewModel.expirationTime.value ?: 0L)
        }
        subscriptionViewModel.expirationTime.observe(viewLifecycleOwner) { expirationTime ->
            updateSubscriptionStatus(subscriptionViewModel.isAdFree.value ?: false, expirationTime)
        }
    }
    private fun loadSharedPrefs() {
        val appPrefs = requireContext().getSharedPreferences(PREFS_NAME_APP, Context.MODE_PRIVATE)
        selectedVoice = appPrefs.getString(SELECTED_VOICE_KEY, "alloy") ?: "alloy"
        // Voice selection now handled within message input area
        // Load conversationId for the last session or default to a new one
        conversationId = appPrefs.getString("last_conversation_id", null) ?: generateConversationId().also {
            appPrefs.edit().putString("last_conversation_id", it).apply()
        }
        isFollowUpEnabled = appPrefs.getBoolean("follow_up_enabled", true)
    }




    // Updated ChatFragment methods for computer use

    private var conversationHistory = JSONArray()

















    private fun switchUiForModel(model: String) {
        Log.d("ChatFragment", "Switching UI for model: $model")
        // Default to text chat UI
        binding.messageInputLayout.visibility = View.VISIBLE
        binding.scanTextButton.visibility = View.VISIBLE
        binding.voiceInputButton.visibility = View.VISIBLE // Enhanced STT with Whisper
        binding.sendButton.visibility = View.VISIBLE
        // Computer use button removed in new layout
        binding.followUpQuestionsContainer.visibility = if (isFollowUpEnabled) View.VISIBLE else View.GONE
        binding.generatedImageView.visibility = View.GONE
        binding.downloadButton.visibility = View.GONE
        binding.generatingText.visibility = View.GONE

        binding.openaiLiveAudioControls.visibility = View.GONE
        binding.openAIStatusTextView.visibility = View.GONE
        binding.openAIAiResponseTextView.visibility = View.GONE
        binding.imageContainer.visibility = View.GONE



        when (model) {

            "openai-realtime-voice" -> {
                binding.messageInputLayout.visibility = View.GONE
                binding.openaiLiveAudioControls.visibility = View.VISIBLE
                binding.openAIStatusTextView.visibility = View.VISIBLE
                binding.openAIAiResponseTextView.visibility = View.VISIBLE // Show where AI text will appear
                // openAILiveAudioViewModel.stopSession() // Ensure stopped, user will start it
            }
            "dall-e-3" -> {
                binding.messageEditText.hint = "Describe an image..."
                binding.generatedImageView.visibility = View.VISIBLE // Or visible after generation
                // Standard text input is still used for DALL-E prompt
                binding.followUpQuestionsContainer.visibility = View.GONE
            }
            "gpt-image-1" -> {
                binding.messageEditText.hint = "Describe an image (supports text + image input)..."
                binding.generatedImageView.visibility = View.VISIBLE
                // GPT Image 1 supports multimodal input
                binding.followUpQuestionsContainer.visibility = View.GONE
            }
            "veo-3.0-generate-preview", "veo-3.0-fast-generate-preview", "veo-2.0-generate-001" -> {
                binding.messageEditText.hint = "Describe a video scene with audio cues..."
                binding.generatedImageView.visibility = View.VISIBLE
                // Veo supports image-to-video input
                binding.followUpQuestionsContainer.visibility = View.GONE
            }
            "computer-use-preview" -> {
                binding.messageInputLayout.visibility = View.VISIBLE
                binding.sendButton.visibility = View.GONE
                binding.scanTextButton.visibility = View.GONE
                binding.voiceInputButton.visibility = View.GONE
                // Computer use button removed in new layout
                binding.followUpQuestionsContainer.visibility = View.GONE

                binding.openaiLiveAudioControls.visibility = View.GONE
                binding.openAIStatusTextView.visibility = View.GONE
                binding.openAIAiResponseTextView.visibility = View.GONE
            }
            // Add cases for other models if they have very specific UI needs
            else -> {
                // Standard text model
                binding.messageEditText.hint = "Type your message..."
            }
        }
    }














    private fun processUserMessageSend(userMessage: String) {
        Log.d("ChatFragment", "processUserMessageSend called with: $userMessage")

        // Central point for sending a message based on currentModel and limits
        hideKeyboard()
        addMessageToChat(userMessage, true)
        binding.messageEditText.text.clear()

        val isSubscribed = isUserCurrentlySubscribed()
        Log.d("ChatFragment", "User subscribed: $isSubscribed, canSendMessage: $canSendMessage")

        if (isSubscribed || canSendMessage) {
            Log.d("ChatFragment", "User can send message, calling handleMessage")
            handleMessage(userMessage)
            if (!isSubscribed) canSendMessage = false // Consume one "rewarded" message
            return
        }

        Log.d("ChatFragment", "User cannot send message, checking model-specific limits")

        // Check model-specific limits first
        val (limitKey, dailyMax) = when (currentModel) {
            "dall-e-3" -> "dall-e-3" to DAILY_LIMIT_DALLE
            "gpt-image-1" -> "gpt-image-1" to DAILY_LIMIT_DALLE // Image generation limit
            "gemini" -> "gemini_text" to DAILY_LIMIT_GEMINI_TEXT // Differentiate text Gemini
            "deepseek" -> "deepseek" to DAILY_LIMIT_DEEPSEEK
            "o3-mini" -> "o3-mini" to DAILY_LIMIT_O3_MINI
            "gpt-4o", "gpt-4-turbo" -> "gpt4_class" to DAILY_LIMIT_GPT4 // Group powerful GPTs
            "gpt-4.1-mini", "gpt-4o-mini" -> "gpt4mini_class" to DAILY_LIMIT_GPT4_MINI
            "gpt-3.5-turbo" -> "gpt35_class" to DAILY_LIMIT_GPT_DEFAULT
            // Add other specific model limits here
            else -> "general_chat" to DAILY_GENERAL_MESSAGE_LIMIT // Fallback general limit key
        }

        if (checkDailyLimit(limitKey, dailyMax)) {
            // Check credit balance before sending - must call suspend function in coroutine
            lifecycleScope.launch {
                val tier = subscriptionUIManager.getUserSubscriptionTier()
                val model = com.playstudio.aiteacher.pricing.AIModel.fromModelId(currentModel)
                if (model != null && currentModel != "gpt-image-1") {
                    val tokenPool = com.playstudio.aiteacher.credits.TokenPoolManager.getInstance(requireContext())
                    val poolTier = tier.toTokenPoolTier()
                    val estimatedCost = tokenPool.calculateTokenCost(
                        modelName = model.modelId,
                        responseType = com.playstudio.aiteacher.credits.TokenPoolManager.ResponseType.TEXT,
                        inputTokens = model.averageInputTokens,
                        outputTokens = model.averageOutputTokens,
                        responseLength = model.averageOutputTokens * 4,
                        userTier = poolTier
                    )
                    val remaining = tokenPool.getRemainingDailyTokens("default_user", poolTier)
                    if (remaining < estimatedCost) {
                        withContext(Dispatchers.Main) {
                            showCustomToast("Insufficient tokens to send message")
                        }
                        return@launch
                    }
                }

                // Usage will be tracked in trackMessageUsage() after successful response
                withContext(Dispatchers.Main) { handleMessage(userMessage) }
            }
        } else {
            showCustomToast("Daily limit for $currentModel reached.")
            showRewardedAd() // Offer ad to continue
        }
    }





    // MODIFIED handleChatCompletion with Tool Calling Logic
    private fun handleChatCompletion(
        userMessageContent: String
    ) {
        Log.d("ChatFragment", "handleChatCompletion called with message: $userMessageContent")

        // Check usage limits before processing
        lifecycleScope.launch {
            val currentAIModel = com.playstudio.aiteacher.pricing.AIModel.fromModelId(currentModel)
            Log.d("ChatFragment", "Current model: $currentModel, AIModel found: ${currentAIModel != null}")

            if (currentAIModel != null) {
                val canSend = checkUsageBeforeMessage(currentAIModel)
                if (!canSend) {
                    Log.d("ChatFragment", "Cannot send message due to usage limits")
                    withContext(Dispatchers.Main) {
                        removeTypingIndicator()
                    }
                    return@launch
                }
            } else {
                Log.w("ChatFragment", "Could not find AIModel for currentModel: $currentModel, proceeding anyway")
            }

            Log.d("ChatFragment", "Proceeding with message processing")
            // Proceed with actual message processing
            processChatCompletionInternal(userMessageContent)
        }
    }

    internal fun processChatCompletionInternal(userMessageContent: String) {
        val messagesToSend = JSONArray()

        if (currentConversationHistoryForToolCall.isEmpty()) {
            // Initial turn or non-tool-related turn
            // Add system/developer prompt if needed (NOT for o1/o3 models as they don't support system messages)
            if (!currentModel.startsWith("o1") && !currentModel.startsWith("o3")) {
                messagesToSend.put(JSONObject().apply {
                    put("role", "system")
                    put("content", "You are a specialized assistant. Use tools when necessary to fulfill the request.")
                })
            }

            // Add relevant chat history from adapter
            // Be mindful of token limits; you might only send the last N messages
            val historyLimit = 10 // Example: send last 10 messages
            chatMessages.filterNot { it.isTyping }
                .takeLast(historyLimit) // Take recent history
                .forEach { chatMsg ->
                    // Skip adding the current user message if it's already in userMessageContent for this turn
                    if (!(chatMsg.isUser && chatMsg.content == userMessageContent && currentConversationHistoryForToolCall.isEmpty())) {
                        messagesToSend.put(JSONObject().apply {
                            put("role", if (chatMsg.isUser) "user" else "assistant")
                            put("content", chatMsg.content)
                            // If chatMsg.toolCalls is not null, structure it as per API
                            // This part is complex if loading history with tool calls
                        })
                    }
                }
            // Add current user message for this turn
            messagesToSend.put(JSONObject().apply {
                put("role", "user")
                put("content", userMessageContent)
            })
        } else {
            // This is a follow-up call after a tool execution
            currentConversationHistoryForToolCall.forEach { messagesToSend.put(it) }
        }

        val requestBodyJson = JSONObject().apply {
            put("model", currentModel) // Ensure this is a model that supports tools (e.g., gpt-4o, gpt-3.5-turbo-0125+)
            
            if (currentModel.startsWith("claude")) {
                // Claude/Anthropic API format - extract system message and put in separate field
                val systemMessage = "You are a specialized assistant. Use tools when necessary to fulfill the request."
                val claudeMessages = JSONArray()
                
                // Only add user/assistant messages to messages array (skip system messages)
                for (i in 0 until messagesToSend.length()) {
                    val msg = messagesToSend.getJSONObject(i)
                    if (msg.getString("role") != "system") {
                        claudeMessages.put(msg)
                    }
                }
                
                if (!currentModel.startsWith("o1") && !currentModel.startsWith("o3")) {
                    put("system", systemMessage)
                }
                put("messages", claudeMessages)
                put("max_tokens", 2000) // Claude uses max_tokens, not max_completion_tokens
                
                // Add tools for Claude
                if (modelSupportsTools(currentModel) && !WEB_SEARCH_MODELS.contains(currentModel)) {
                    val tools = convertToolsForClaude(getAvailableTools())
                    put("tools", tools)
                }
            } else {
                // OpenAI API format - keep existing structure
                put("messages", messagesToSend)
                put("max_completion_tokens", 300)
                
                // Only include tools if the model supports them and this isn't a search-preview model
                if (modelSupportsTools(currentModel) && !WEB_SEARCH_MODELS.contains(currentModel)) {
                    val tools = getAvailableTools()
                    put("tools", tools)
                    // put("tool_choice", "auto") // "auto" is default
                }
                
                if (WEB_SEARCH_MODELS.contains(currentModel)) {
                    put("web_search_options", JSONObject())
                }
            }
            // Add other params like temperature if needed
        }

        val body = requestBodyJson.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())

        val requestBuilder = Request.Builder()
            .post(body)
            .addHeader("Content-Type", "application/json")

        if (currentModel.startsWith("claude")) {
            requestBuilder
                .url("https://api.anthropic.com/v1/messages")
                .addHeader("x-api-key", getApiKey("anthropic") ?: "")
                .addHeader("anthropic-version", "2023-06-01")
        } else if (currentModel.startsWith("grok")) {
            requestBuilder
                .url("https://api.x.ai/v1/chat/completions")
                .addHeader("Authorization", "Bearer ${BuildConfig.GROK_API_KEY}")
        } else {
            requestBuilder
                .url("https://api.openai.com/v1/chat/completions")
                .addHeader("Authorization", "Bearer ${getApiKey("openai") ?: ""}")
        }

        val request = requestBuilder.build()

        Log.d("ChatFragment", "Sending ChatCompletion (Tools/Text): ${requestBodyJson.toString(2)}")
        if (currentConversationHistoryForToolCall.isEmpty()) {
            showTypingIndicator() // Show typing only for the initial user query of a turn
        }

        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) { okHttpClient.newCall(request).execute() }
                val responseBodyString = response.body?.string()

                if (!response.isSuccessful) {
                    Log.e("ChatFragment", "ChatCompletion API Error ${response.code}: $responseBodyString")
                    withContext(Dispatchers.Main) {
                        removeTypingIndicator()
                        handleErrorResponse(response) // Your existing general error handler
                    }
                    return@launch
                }

                responseBodyString?.let { responseStr ->
                    val jsonResponse = JSONObject(responseStr)
                    Log.d("ChatFragment", "Received ChatCompletion Response: ${jsonResponse.toString(2)}")

                    var messageFromApi: JSONObject? = null

                    if (jsonResponse.has("choices")) {
                        val choice = jsonResponse.optJSONArray("choices")?.optJSONObject(0)
                        messageFromApi = choice?.optJSONObject("message")
                    } else if (jsonResponse.has("content")) {
                        // Handle Anthropic Messages API format
                        val contentArray = jsonResponse.getJSONArray("content")
                        val textBuilder = StringBuilder()
                        for (i in 0 until contentArray.length()) {
                            val block = contentArray.getJSONObject(i)
                            if (block.optString("type") == "text") {
                                textBuilder.append(block.optString("text"))
                            }
                        }
                        messageFromApi = JSONObject().apply {
                            put("role", jsonResponse.optString("role", "assistant"))
                            put("content", textBuilder.toString())
                        }
                    }

                    if (messageFromApi == null) {
                        withContext(Dispatchers.Main) {
                            removeTypingIndicator()
                            showCustomToast("No message content in API response.")
                        }
                        return@launch
                    }

                    // Prepare history for potential next call (if this was a tool call)
                    val ongoingHistoryForThisTurn = currentConversationHistoryForToolCall.ifEmpty {
                        // Create history from messagesToSend if it's the first part of the turn
                        MutableList(messagesToSend.length()) { i -> messagesToSend.getJSONObject(i) }
                    }
                    ongoingHistoryForThisTurn.add(messageFromApi) // Add AI's response (could be tool_calls or final message)

                    if (messageFromApi.has("tool_calls")) {
                        // AI wants to use a tool
                        withContext(Dispatchers.Main) { removeTypingIndicator() }

                        val toolCallsArray = messageFromApi.getJSONArray("tool_calls")
                        val toolResultsMessages = mutableListOf<JSONObject>()

                        for (i in 0 until toolCallsArray.length()) {
                            val toolCall = toolCallsArray.getJSONObject(i)
                            val functionCall = toolCall.getJSONObject("function")
                            val functionName = functionCall.getString("name")
                            val argumentsJsonStr = functionCall.getString("arguments")
                            val toolCallId = toolCall.getString("id")

                            // UI: Indicate tool usage
                            withContext(Dispatchers.Main) {
                                addMessageToChat(
                                    messageContent = "AI is using tool: $functionName...",
                                    isUser = false,
                                    containsRichContent = false
                                )
                            }

                            val functionResultStr = executeToolFunction(functionName, argumentsJsonStr)

                            toolResultsMessages.add(JSONObject().apply {
                                put("role", "tool")
                                put("tool_call_id", toolCallId)
                                put("content", functionResultStr) // Result of your native function
                            })
                        }
                        ongoingHistoryForThisTurn.addAll(toolResultsMessages) // Add tool results to history
                        // Save for next call and call API again with results
                        currentConversationHistoryForToolCall.clear()
                        currentConversationHistoryForToolCall.addAll(ongoingHistoryForThisTurn)
                        handleChatCompletion(userMessageContent)

                    } else {
                        // No tool_calls, this is a direct text response from the AI
                        // Your existing handleSuccessResponse should be called here.
                        // It expects the full response body string of THIS turn.
                        // We need to reconstruct a minimal response string for it.
                        val minimalResponseForHandler = JSONObject().apply {
                            put("choices", JSONArray().put(JSONObject().apply {
                                put("message", messageFromApi)
                                if (jsonResponse.has("usage")) {
                                    put("usage", jsonResponse.getJSONObject("usage"))
                                }
                            }))
                            if (jsonResponse.has("stop_reason")) {
                                put("stop_reason", jsonResponse.getString("stop_reason"))
                            }
                        }.toString()
                        handleSuccessResponse(minimalResponseForHandler)
                    }
                } ?: withContext(Dispatchers.Main) {
                    removeTypingIndicator()
                    showCustomToast("Received empty response body.")
                }
            } catch (e: Exception) { // Catch IOException and JSONException broadly
                Log.e("ChatFragment", "Error in handleChatCompletion: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    removeTypingIndicator()
                    showCustomToast("An error occurred: ${e.localizedMessage}")
                }
            }
        }
    }

    private fun modelSupportsTools(modelName: String): Boolean {
        // List models known to support function calling/tools
        if (WEB_SEARCH_MODELS.contains(modelName)) {
            // Search preview models do not currently support tool calling
            return false
        }

        return modelName.startsWith("gpt-4") ||
                modelName.startsWith("claude") ||
                modelName.contains("gpt-3.5-turbo-0125") ||
                modelName.contains("gpt-3.5-turbo-1106")
        // Add other models as OpenAI updates them.
    }

    // In ChatFragment.kt

    // --- Tool Execution Router ---
    private suspend fun executeToolFunction(functionName: String, argumentsJsonString: String): String {
        return try {
            val arguments = JSONObject(argumentsJsonString)
            Log.i("ChatFragmentTool", "Executing tool: $functionName with args: $arguments")

            // First check if this is an educational AI function
            if (aiFunctionCallManager.isEducationalFunction(functionName)) {
                Log.d("ChatFragmentTool", "Delegating to AIFunctionCallManager: $functionName")
                // Execute the educational function via the function caller
                return withContext(Dispatchers.IO) {
                    val result = when (functionName) {
                        "explain_concept" -> {
                            val concept = arguments.optString("concept")
                            val gradeLevel = arguments.optString("grade_level", "High School")
                            val chatMessage = aiFunctionCallManager.explainConcept(concept, gradeLevel)
                            chatMessage.content
                        }
                        "create_practice_quiz" -> {
                            val topic = arguments.optString("topic")
                            val difficulty = arguments.optString("difficulty", "medium")
                            val questionCount = arguments.optInt("num_questions", 5)
                            val chatMessage = aiFunctionCallManager.createQuiz(topic, difficulty, questionCount)
                            chatMessage.content
                        }
                        "get_homework_help" -> {
                            val question = arguments.optString("question")
                            val subject = arguments.optString("subject")
                            val chatMessage = aiFunctionCallManager.getHomeworkHelp(question, subject)
                            chatMessage.content
                        }
                        "search_educational_resources" -> {
                            val topic = arguments.optString("query")
                            val chatMessage = aiFunctionCallManager.searchResources(topic)
                            chatMessage.content
                        }
                        else -> JSONObject().apply { put("error", "Educational function '$functionName' not implemented.") }.toString()
                    }
                    result
                }
            }

            // Fall back to existing native tool functions
            when (functionName) {
                "get_weather" -> {
                    val location = arguments.optString("location")
                    val unit = arguments.optString("unit", "celsius") // Default from schema
                    if (location.isBlank()) {
                        JSONObject().apply { put("error", "Location is required to get weather.") }.toString()
                    } else {
                        executeGetWeather(location, unit)
                    }
                }
                "set_calendar_reminder" -> {
                    val title = arguments.optString("title")
                    val startTimeIso = arguments.optString("start_time_iso")
                    if (title.isBlank() || startTimeIso.isBlank()) {
                        JSONObject().apply { put("error", "Title and start time are required for calendar reminder.") }.toString()
                    } else {
                        val description = arguments.optString("description", null)
                        val endTimeIso = arguments.optString("end_time_iso", null)
                        val recurrence = arguments.optString("recurrence_rule", null)
                        executeSetCalendarReminder(title, description, startTimeIso, endTimeIso, recurrence)
                    }
                }
                "send_email_by_voice" -> {
                    val recipient = arguments.optString("recipient")
                    val subject = arguments.optString("subject")
                    val body = arguments.optString("body")
                    if (recipient.isBlank() || subject.isBlank() || body.isBlank()) {
                        JSONObject().apply { put("error", "Recipient, subject, and body are required to send an email.") }.toString()
                    } else {
                        executeSendEmail(recipient, subject, body)
                    }
                }
                "make_phone_call" -> {
                    val phoneNumber = arguments.optString("phone_number", null)
                    val contactName = arguments.optString("contact_name", null)
                    executeMakePhoneCall(phoneNumber, contactName) // Handles nulls internally
                }
                "set_alarm" -> {
                    if (!arguments.has("hour") || !arguments.has("minute")) {
                        JSONObject().apply { put("error", "Hour and minute are required to set an alarm.") }.toString()
                    } else {
                        val hour = arguments.getInt("hour")
                        val minute = arguments.getInt("minute")
                        val message = arguments.optString("message", null)
                        val daysArray = arguments.optJSONArray("days")
                        val daysList = mutableListOf<Int>()
                        if (daysArray != null) {
                            for (i in 0 until daysArray.length()) {
                                daysList.add(daysArray.getInt(i))
                            }
                        }
                        executeSetAlarm(hour, minute, message, if (daysList.isEmpty()) null else daysList)
                    }
                }
                "start_meeting_recording" -> {
                    val topic = arguments.optString("topic", "Meeting Recording")
                    executeStartMeetingRecording(topic)
                }
                // "stop_meeting_recording" -> executeStopMeetingRecording() // If you add this tool
                else -> {
                    Log.w("ChatFragmentTool", "Attempted to call unknown function: $functionName")
                    JSONObject().apply { put("error", "Function '$functionName' is not implemented.") }.toString()
                }
            }
        } catch (e: JSONException) {
            Log.e("ChatFragmentTool", "Error parsing arguments for $functionName: $argumentsJsonString", e)
            JSONObject().apply { put("error", "Invalid arguments provided for function $functionName.") }.toString()
        } catch (e: Exception) {
            Log.e("ChatFragmentTool", "Generic error executing function $functionName: ${e.message}", e)
            JSONObject().apply { put("error", "Execution of $functionName failed: ${e.localizedMessage}") }.toString()
        }
    }

// --- Native Tool Implementations (suspend functions) ---

    private suspend fun executeGetWeather(location: String, unit: String?): String = withContext(Dispatchers.IO) {
        Log.d("ChatFragmentTool", "Fetching weather for '$location', unit: $unit")
        val actualUnit = if (unit.equals("fahrenheit", ignoreCase = true)) "imperial" else "metric"
        val unitSymbol = if (actualUnit == "imperial") "°F" else "°C"

        // --- REPLACE WITH YOUR ACTUAL WEATHER API CALL ---
        // Example using OpenWeatherMap (requires an API key)
        // val weatherApiKey = BuildConfig.OPEN_WEATHER_API_KEY // Add this to your BuildConfig
        // val weatherUrl = "https://api.openweathermap.org/data/2.5/weather?q=${URLEncoder.encode(location, "UTF-8")}&appid=${weatherApiKey}&units=${actualUnit}"
        // try {
        //     val request = Request.Builder().url(weatherUrl).build()
        //     val response = okHttpClient.newCall(request).execute() // Use your app's OkHttpClient
        //     val responseBody = response.body?.string()
        //
        //     if (response.isSuccessful && responseBody != null) {
        //         val weatherJson = JSONObject(responseBody)
        //         if (weatherJson.optInt("cod") == 200) { // Check for successful API business logic
        //             val main = weatherJson.getJSONObject("main")
        //             val temp = main.getDouble("temp")
        //             val description = weatherJson.getJSONArray("weather").getJSONObject(0).getString("description")
        //             val feelsLike = main.optDouble("feels_like", temp) // Optional
        //             val humidity = main.optInt("humidity", -1)       // Optional
        //
        //             JSONObject().apply {
        //                 put("location", weatherJson.getString("name"))
        //                 put("temperature", "${String.format("%.1f", temp)}$unitSymbol")
        //                 put("feels_like", "${String.format("%.1f", feelsLike)}$unitSymbol")
        //                 put("condition", description.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() })
        //                 if (humidity != -1) put("humidity", "$humidity%")
        //             }.toString()
        //         } else {
        //             val errorMessage = weatherJson.optString("message", "Unknown error from weather API.")
        //             JSONObject().apply { put("error", "Could not fetch weather for $location: $errorMessage") }.toString()
        //         }
        //     } else {
        //         JSONObject().apply { put("error", "Weather API request failed for $location with code ${response.code}.") }.toString()
        //     }
        // } catch (e: Exception) {
        //     Log.e("ChatFragmentTool", "Exception fetching weather for $location: ${e.message}", e)
        //     JSONObject().apply { put("error", "Failed to connect to weather service for $location.") }.toString()
        // }
        // --- END OF REAL API EXAMPLE ---

        // Simulated response for testing:
        delay(1000)
        if (location.lowercase().contains("paris")) {
            JSONObject().apply { put("temperature", if(unit == "fahrenheit") "72°F" else "22°C"); put("condition", "Sunny") }.toString()
        } else if (location.lowercase().contains("london")) {
            JSONObject().apply { put("temperature", if(unit == "fahrenheit") "55°F" else "13°C"); put("condition", "Cloudy") }.toString()
        } else {
            JSONObject().apply { put("error", "Weather data currently unavailable for '$location'. Please try a major city.") }.toString()
        }
    }

    private suspend fun executeSetCalendarReminder(title: String, description: String?, startTimeIso: String, endTimeIso: String?, recurrenceRule: String?): String = withContext(Dispatchers.Main) {
        Log.d("ChatFragmentTool", "Attempting to set calendar reminder: '$title' at $startTimeIso")

        val startMillis: Long
        try {
            // More robust ISO parsing
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            // sdf.timeZone = TimeZone.getTimeZone("UTC") // If AI provides UTC, and you want to convert to local for Calendar
            val startDate = sdf.parse(startTimeIso)
            if (startDate == null) {
                return@withContext JSONObject().apply { put("status", "error"); put("message", "Invalid start time format. Please use YYYY-MM-DDTHH:MM:SS.") }.toString()
            }
            startMillis = startDate.time
            if (startMillis < System.currentTimeMillis()) {
                // return@withContext JSONObject().apply { put("status", "error"); put("message", "Cannot set reminder in the past. Start time: $startTimeIso" ) }.toString()
                // Allow past for testing, but in prod you might block this.
            }
        } catch (e: Exception) {
            Log.e("ChatFragmentTool", "Error parsing start_time_iso: $startTimeIso", e)
            return@withContext JSONObject().apply { put("status", "error"); put("message", "Invalid start time format. Please use YYYY-MM-DDTHH:MM:SS.") }.toString()
        }

        val endMillis: Long = endTimeIso?.let {
            try {
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).parse(it)?.time
            } catch (e: Exception) { null }
        } ?: (startMillis + 3600_000) // Default 1 hour duration if end time is invalid or not provided

        if (endMillis < startMillis) {
            return@withContext JSONObject().apply { put("status", "error"); put("message", "End time cannot be before start time.") }.toString()
        }

        val intent = Intent(Intent.ACTION_INSERT)
            .setData(CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.Events.TITLE, title)
            .putExtra(CalendarContract.Events.DESCRIPTION, description ?: "")
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startMillis)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endMillis)

        if (!recurrenceRule.isNullOrBlank()) {
            intent.putExtra(CalendarContract.Events.RRULE, recurrenceRule)
        }

        try {
            // User confirmation happens in the Calendar app
            startActivity(intent)
            JSONObject().apply { put("status", "success"); put("message", "Calendar app opened to set reminder for '$title'. User needs to confirm and save it there.") }.toString()
        } catch (e: ActivityNotFoundException) {
            JSONObject().apply { put("status", "error"); put("message", "No calendar app found to set reminder.") }.toString()
        }
    }

    private suspend fun executeSendEmail(recipient: String, subject: String, body: String): String = withContext(Dispatchers.Main) {
        Log.d("ChatFragmentTool", "Preparing email to $recipient, Subject: $subject")

        // User Confirmation before launching intent
        val confirmed = showConfirmationDialog(
            "Confirm Email",
            "Recipient: $recipient\nSubject: $subject\n\nBody:\n$body\n\nProceed to email app?"
        )

        if (!confirmed) {
            return@withContext JSONObject().apply { put("status", "cancelled"); put("message", "Email sending cancelled by user.") }.toString()
        }

        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:") // Ensures only email apps respond
            putExtra(Intent.EXTRA_EMAIL, arrayOf(recipient))
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
        }
        try {
            startActivity(Intent.createChooser(intent, "Send email via...")) // Offer chooser
            JSONObject().apply { put("status", "success"); put("message", "Email app opened with draft for '$recipient'. Please review and send.") }.toString()
        } catch (e: ActivityNotFoundException) {
            JSONObject().apply { put("status", "error"); put("message", "No email app found on the device.") }.toString()
        }
    }

    private suspend fun executeMakePhoneCall(phoneNumber: String?, contactName: String?): String = withContext(Dispatchers.Main) {
        var numberToDial = phoneNumber

        if (numberToDial.isNullOrBlank() && contactName.isNullOrBlank()) {
            return@withContext JSONObject().apply { put("status", "error"); put("message", "Either a phone number or a contact name is required to make a call.") }.toString()
        }

        if (numberToDial.isNullOrBlank() && !contactName.isNullOrBlank()) {
            Log.d("ChatFragmentTool", "Attempting to look up number for contact: $contactName")
            // --- Placeholder for actual contact lookup ---
            // This requires READ_CONTACTS permission, which should be requested before this tool is even offered/called by AI.
            // val foundNumber = findPhoneNumberForContact(contactName) // Implement this
            // if (foundNumber == null) {
            //     return@withContext JSONObject().apply { put("status", "error"); put("message", "Could not find a phone number for contact '$contactName'.") }.toString()
            // }
            // numberToDial = foundNumber
            // For now, simulate failure if only name is given, as lookup isn't implemented:
            return@withContext JSONObject().apply { put("status", "error"); put("message", "Contact lookup by name is not yet fully supported. Please provide a direct phone number.") }.toString()
        }

        Log.d("ChatFragmentTool", "Preparing to dial $numberToDial")
        val confirmed = showConfirmationDialog("Confirm Call", "Do you want to open the dialer for $numberToDial?")
        if (!confirmed) {
            return@withContext JSONObject().apply { put("status", "cancelled"); put("message", "Phone call cancelled by user.") }.toString()
        }

        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$numberToDial"))
        try {
            startActivity(intent)
            JSONObject().apply { put("status", "success"); put("message", "Dialer app opened for $numberToDial. Please initiate the call there.") }.toString()
        } catch (e: ActivityNotFoundException) {
            JSONObject().apply { put("status", "error"); put("message", "No phone app found on the device.") }.toString()
        }
    }
    @SuppressLint("MissingPermission")
    private suspend fun executeSetAlarm(hour: Int, minute: Int, message: String?, daysOfWeek: List<Int>?): String = withContext(Dispatchers.Main) {
        Log.d("ChatFragmentTool", "Attempting to set alarm for $hour:$minute, message: '$message', days: $daysOfWeek")

        if (hour !in 0..23 || minute !in 0..59) {
            return@withContext JSONObject().apply { put("status", "error"); put("message", "Invalid hour or minute provided for the alarm.") }.toString()
        }

        val alarmMessage = message ?: "AI Teacher Alarm" // Default message if AI provides null
        val confirmationDialogMessage = "Set an alarm for ${String.format("%02d:%02d", hour, minute)}" +
                (if (message.isNullOrBlank()) "" else " with message '$alarmMessage'") + "?"

        val confirmed = showConfirmationDialog("Confirm Alarm", confirmationDialogMessage)
        if (!confirmed) {
            return@withContext JSONObject().apply { put("status", "cancelled"); put("message", "Alarm setting cancelled by user.") }.toString()
        }

        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute) // Use the same import pattern
            putExtra(AlarmClock.EXTRA_MESSAGE, alarmMessage)
            putExtra(AlarmClock.EXTRA_SKIP_UI, false) // Let user see the clock app UI to confirm/save

            if (!daysOfWeek.isNullOrEmpty()) {
                val calendarDays = ArrayList(daysOfWeek) // Assumes daysOfWeek contains Calendar.MONDAY, etc.
                putExtra(AlarmClock.EXTRA_DAYS, calendarDays)
            }
        }

        try {
            startActivity(intent)
            JSONObject().apply { put("status", "success"); put("message", "Clock app opened to set alarm for ${String.format("%02d:%02d", hour, minute)}. Please confirm and save it there.") }.toString()
        } catch (e: ActivityNotFoundException) {
            JSONObject().apply { put("status", "error"); put("message", "No clock app found to set the alarm.") }.toString()
        }
    }

    // --- Meeting Recording ---
// Properties for meeting recording state
    private var mediaRecorder: MediaRecorder? = null
    private var currentMeetingAudioFile: File? = null
    private var isMeetingRecording = false // To track recording state

    private suspend fun executeStartMeetingRecording(topic: String?): String = withContext(Dispatchers.Main) {
        if (isMeetingRecording) {
            return@withContext JSONObject().apply { put("status", "error"); put("message", "A meeting recording is already in progress.") }.toString()
        }

        // Check RECORD_AUDIO permission (should ideally be done before AI calls tool)
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            // requestAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO) // Trigger permission request
            return@withContext JSONObject().apply { put("status", "error"); put("message", "Audio recording permission is required. Please grant it and try again.") }.toString()
        }
        // Also check storage permission if saving externally, or use app-specific directory
        // For simplicity, using cache directory which doesn't need explicit storage perms on newer Android.

        val finalTopic = topic ?: "Meeting ${SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.getDefault()).format(Date())}"
        val confirmed = showConfirmationDialog("Start Recording?", "Start recording a new meeting about '$finalTopic'?")

        if (!confirmed) {
            return@withContext JSONObject().apply { put("status", "cancelled"); put("message", "Meeting recording cancelled by user.") }.toString()
        }

        try {
            currentMeetingAudioFile = File(requireContext().cacheDir, "${finalTopic.replace(" ", "_")}_${System.currentTimeMillis()}.m4a")
            mediaRecorder = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(requireContext())
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }).apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4) // Common format, .m4a
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)    // Good quality encoder
                setAudioSamplingRate(44100) // Standard sampling rate
                setAudioChannels(1)         // Mono
                setAudioEncodingBitRate(96000) // Decent bitrate
                setOutputFile(currentMeetingAudioFile!!.absolutePath)
                prepare()
                start()
            }
            isMeetingRecording = true
            // Update UI to show "Recording... [Stop Button]" - This needs to be handled in Fragment's UI logic
            // For now, we just show a toast.
            showCustomToast("Meeting recording started: $finalTopic")
            Log.i("ChatFragmentTool", "Meeting recording started. File: ${currentMeetingAudioFile?.absolutePath}")

            // The AI's response to the user will be based on this string.
            // It cannot wait for the summary.
            JSONObject().apply {
                put("status", "success")
                put("message", "Meeting recording for '$finalTopic' has started. You can tell me to 'stop recording' when done, or stop it via the app UI.")
                put("recording_id", currentMeetingAudioFile?.nameWithoutExtension) // Optional ID
            }.toString()
        } catch (e: Exception) {
            Log.e("ChatFragmentTool", "Failed to start meeting recording: ${e.message}", e)
            isMeetingRecording = false
            currentMeetingAudioFile = null
            JSONObject().apply { put("status", "error"); put("message", "Failed to start meeting recording: ${e.localizedMessage}") }.toString()
        }
    }

    // This would be called by a UI button or potentially another AI tool ("stop_meeting_recording")
// For now, let's assume a UI button calls a public method in the fragment, which then calls this.
// This function is NOT directly called by the AI in the initial tool flow.
    suspend fun processAndSummarizeMeeting(): String? = withContext(Dispatchers.IO) {
        if (!isMeetingRecording && currentMeetingAudioFile == null) {
            Log.w("ChatFragmentTool", "No active recording or file to summarize.")
            return@withContext null // Or an error string
        }

        if (isMeetingRecording) { // Stop it first if still running
            try {
                mediaRecorder?.stop()
                mediaRecorder?.release()
            } catch (e: Exception) {
                Log.e("ChatFragmentTool", "Error stopping media recorder during summarize: ${e.message}")
            }
            isMeetingRecording = false
            mediaRecorder = null
        }

        val fileToSummarize = currentMeetingAudioFile
        currentMeetingAudioFile = null // Reset for next recording

        if (fileToSummarize == null || !fileToSummarize.exists() || fileToSummarize.length() == 0L) {
            Log.e("ChatFragmentTool", "Meeting audio file is invalid or empty for summarization.")
            return@withContext JSONObject().apply { put("error", "No valid meeting audio to summarize.") }.toString()
        }

        Log.i("ChatFragmentTool", "Processing meeting recording for summarization: ${fileToSummarize.absolutePath}")
        showCustomToast("Processing meeting summary...") // Show on UI thread

        // Step 1: Transcribe (using OpenAI Audio API - 'whisper-1' or similar)
        val transcript: String? = try {
            val requestFile = fileToSummarize.asRequestBody("audio/m4a".toMediaTypeOrNull())
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", fileToSummarize.name, requestFile)
                .addFormDataPart("model", "whisper-1") // Or your preferred transcription model
                // .addFormDataPart("response_format", "json") // 'json' gives structured output with text
                .build()

            val request = Request.Builder()
                .url("https://api.openai.com/v1/audio/transcriptions")
                .header("Authorization", "Bearer ${getApiKey("openai") ?: ""}") // Using Firestore key with BuildConfig fallback
                .post(requestBody)
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                responseBody?.let { JSONObject(it).getString("text") }
            } else {
                Log.e("ChatFragmentTool", "Transcription API error: ${response.code} - ${response.message}")
                null
            }
        } catch (e: Exception) {
            Log.e("ChatFragmentTool", "Exception during transcription: ${e.message}", e)
            null
        }

        if (transcript.isNullOrBlank()) {
            Log.e("ChatFragmentTool", "Transcription failed or produced empty text.")
            fileToSummarize.delete() // Clean up
            return@withContext JSONObject().apply { put("error", "Failed to transcribe the meeting audio.") }.toString()
        }

        Log.i("ChatFragmentTool", "Transcription successful. Length: ${transcript.length}")
        fileToSummarize.delete() // Clean up original audio file after successful transcription

        // Step 2: Summarize (using OpenAI Chat Completions API)
        val summaryPrompt = "Please provide a concise summary of the following meeting transcript:\n\nTranscript:\n\"\"\"\n$transcript\n\"\"\"\n\nSummary:"
        val messagesArray = JSONArray().put(JSONObject().apply {
            put("role", "user")
            put("content", summaryPrompt)
        })
        val summaryRequestBodyJson = JSONObject().apply {
            put("model", "gpt-3.5-turbo") // Or gpt-4o for better summaries
            put("messages", messagesArray)
            put("temperature", 0.5)
            put("max_completion_tokens", 300) // Adjust as needed
        }
        val summaryBody = summaryRequestBodyJson.toString().toRequestBody("application/json".toMediaTypeOrNull())
        val summaryRequest = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .post(summaryBody)
            .addHeader("Authorization", "Bearer ${getApiKey("openai") ?: ""}")
            .addHeader("Content-Type", "application/json")
            .build()

        try {
            val summaryResponse = okHttpClient.newCall(summaryRequest).execute()
            if (summaryResponse.isSuccessful) {
                val summaryResponseBody = summaryResponse.body?.string()
                summaryResponseBody?.let {
                    val summaryText = JSONObject(it).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content").trim()
                    Log.i("ChatFragmentTool", "Summarization successful.")
                    // Return the summary text itself. The calling UI logic will add it to chat.
                    return@withContext summaryText // Just the summary string
                }
            } else {
                Log.e("ChatFragmentTool", "Summarization API error: ${summaryResponse.code} - ${summaryResponse.message}")
            }
        } catch (e: Exception) {
            Log.e("ChatFragmentTool", "Exception during summarization: ${e.message}", e)
        }
        return@withContext JSONObject().apply { put("error", "Failed to summarize the meeting.") }.toString() // Fallback error
    }



    // In ChatFragment.kt

    private suspend fun showConfirmationDialog(title: String, message: String): Boolean = suspendCancellableCoroutine { continuation ->
        AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Yes") { _, _ -> continuation.resume(true) }
            .setNegativeButton("No") { _, _ -> continuation.resume(false) }
            .setOnCancelListener { continuation.resume(false) } // If user dismisses
            .show()
        continuation.invokeOnCancellation {
            // Dialog might still be showing, try to dismiss it if coroutine is cancelled
            // This is harder as you don't have a direct dialog instance here easily.
            // For simplicity, we'll rely on standard dialog dismissal.
        }
    }
    // In ChatFragment.kt

    private fun getAvailableTools(): JSONArray {
        val tools = JSONArray()
        
        // Add educational AI functions from EducationalFunctions
        val educationalFunctions = com.playstudio.aiteacher.api.EducationalFunctions.getAllFunctions()
        for (function in educationalFunctions) {
            tools.put(JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", function.name)
                    put("description", function.description)
                    put("parameters", function.parameters)
                })
            })
        }
        
        // Add existing native tools
        tools.apply {
            // --- Tool 1: Get Weather ---
            put(JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", "get_weather")
                    put(
                        "description",
                        "Fetches the current weather conditions for a specified city. " +
                                "If the user doesn't specify a city, you MUST ask them for one. " +
                                "If the city name is ambiguous (e.g., 'Paris'), you MUST ask for clarification " +
                                "(e.g., 'Paris, France or Paris, Texas?')."
                    )
                    put("parameters", JSONObject().apply {
                        put("type", "object")
                        put("properties", JSONObject().apply {
                            put("location", JSONObject().apply {
                                put("type", "string")
                                put(
                                    "description",
                                    "The city name for which to get the weather. " +
                                            "It should ideally include state or country if ambiguous (e.g., 'London, UK', 'Portland, Oregon')."
                                )
                            })
                            put("unit", JSONObject().apply {
                                put("type", "string")
                                put("enum", JSONArray().put("celsius").put("fahrenheit"))
                                put(
                                    "description",
                                    "Optional. The temperature unit to return the weather in. Defaults to Celsius if not specified by the user or if their preference is unknown."
                                )
                            })
                        })
                        put("required", JSONArray().put("location"))
                    })
                })
            })

            // --- Tool 2: Set Calendar Reminder/Event ---
            put(JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", "set_calendar_reminder")
                    put(
                        "description",
                        "Sets a reminder or event in the user's calendar. " +
                                "Requires a title for the event and a specific start date and time. " +
                                "Interpret relative times like 'tomorrow at 10 AM' or 'next Tuesday 2pm' into a precise ISO 8601 datetime format. " +
                                "If the year is not specified, assume the current year. If a time is vague (e.g., 'evening'), ask for a more specific time. " +
                                "Always confirm the parsed date and time with the user before proceeding if there was any ambiguity in their request."
                    )
                    put("parameters", JSONObject().apply {
                        put("type", "object")
                        put("properties", JSONObject().apply {
                            put("title", JSONObject().apply {
                                put("type", "string")
                                put("description", "The title or name of the calendar event or reminder.")
                            })
                            put("description", JSONObject().apply { // Made optional
                                put("type", "string")
                                put("description", "Optional. A longer description for the event/reminder.")
                            })
                            put("start_time_iso", JSONObject().apply {
                                put("type", "string")
                                put("format", "date-time")
                                put(
                                    "description",
                                    "The precise start date and time for the event in ISO 8601 format (e.g., 'YYYY-MM-DDTHH:MM:SS'). Must be in the future."
                                )
                            })
                            put("end_time_iso", JSONObject().apply { // Made optional
                                put("type", "string")
                                put("format", "date-time")
                                put(
                                    "description",
                                    "Optional. The precise end date and time for the event in ISO 8601 format. If not provided, a default duration (e.g., 1 hour) might be assumed by the calendar app."
                                )
                            })
                            put("recurrence_rule", JSONObject().apply { // Made optional
                                put("type", "string")
                                put(
                                    "description",
                                    "Optional. A recurrence rule (RRULE string as per iCalendar RFC 5545, e.g., 'FREQ=WEEKLY;BYDAY=MO;UNTIL=YYYYMMDDTHHMMSSZ'). Only use if the user explicitly asks for a recurring event."
                                )
                            })
                        })
                        put("required", JSONArray().put("title").put("start_time_iso"))
                    })
                })
            })

            // --- Tool 3: Send Email ---
            put(JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", "send_email_by_voice")
                    put(
                        "description",
                        "Composes an email to be sent. You MUST obtain the recipient's email address, a subject line, and the body of the email. " +
                                "If any of these are missing, ask the user for them. " +
                                "After composing, the system will show the draft to the user for final confirmation before it is actually sent through their email app."
                    )
                    put("parameters", JSONObject().apply {
                        put("type", "object")
                        put("properties", JSONObject().apply {
                            put("recipient", JSONObject().apply {
                                put("type", "string")
                                put("format", "email")
                                put("description", "The primary email address of the recipient (e.g., 'friend@example.com').")
                            })
                            put("subject", JSONObject().apply {
                                put("type", "string")
                                put("description", "The subject line for the email.")
                            })
                            put("body", JSONObject().apply {
                                put("type", "string")
                                put("description", "The main content or body of the email.")
                            })
                        })
                        put("required", JSONArray().put("recipient").put("subject").put("body"))
                    })
                })
            })

            // --- Tool 4: Make Phone Call ---
            put(JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", "make_phone_call")
                    put(
                        "description",
                        "Initiates a phone call. You can provide either a direct phone number or a contact name. " +
                                "If a contact name is given, the system will attempt to find their number from the user's contacts. " +
                                "If multiple numbers are found for a contact, or if the name is ambiguous, you should ask for clarification. " +
                                "The system will open the dialer app for the user to confirm and start the call."
                    )
                    put("parameters", JSONObject().apply {
                        put("type", "object")
                        put("properties", JSONObject().apply {
                            put("phone_number", JSONObject().apply {
                                put("type", "string")
                                put("description", "Optional. The direct phone number to call (e.g., '+15551234567').")
                            })
                            put("contact_name", JSONObject().apply {
                                put("type", "string")
                                put("description", "Optional. The name of the contact to call if the phone number is not known.")
                            })
                        })
                        // Not strictly requiring either, as the description guides the AI to ask if needed.
                        // Your executeToolFunction will need to handle if both are null.
                    })
                })
            })

            // --- Tool 5: Set Alarm ---
            put(JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", "set_alarm")
                    put(
                        "description",
                        "Sets an alarm on the user's device clock. You must determine a specific hour and minute for the alarm. " +
                                "Interpret relative times like 'in 30 minutes' or 'at 7 PM' into absolute hour and minute. " +
                                "An optional message for the alarm can be included. " +
                                "The system will open the clock app for the user to confirm and save the alarm."
                    )
                    put("parameters", JSONObject().apply {
                        put("type", "object")
                        put("properties", JSONObject().apply {
                            put("hour", JSONObject().apply {
                                put("type", "integer")
                                put("description", "The hour for the alarm in 24-hour format (0-23). For example, 7 AM is 7, 7 PM is 19.")
                            })
                            put("minute", JSONObject().apply {
                                put("type", "integer")
                                put("description", "The minute for the alarm (0-59).")
                            })
                            put("message", JSONObject().apply {
                                put("type", "string")
                                put("description", "Optional. A message or label for the alarm (e.g., 'Wake up for meeting')."
                                )
                            })
                            // Recurring alarms via ACTION_SET_ALARM intent are less reliable across devices.
                            // Keep it simple first, or add 'days' like in calendar if you want to try.
                            // "days": {
                            //    "type": "array",
                            //    "items": {"type": "integer", "description": "Calendar.MONDAY=2, Calendar.TUESDAY=3, etc."},
                            //    "description": "Optional. For recurring alarms, list of days (java.util.Calendar day constants)."
                            // }
                        })
                        put("required", JSONArray().put("hour").put("minute"))
                    })
                })
            })

            // --- Tool 6: Record Meeting and Summarize (Initiation Part) ---
            put(JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", "start_meeting_recording")
                    put(
                        "description",
                        "Starts an audio recording for a meeting or lecture. " +
                                "An optional topic can be provided for the recording. " +
                                "The user will be asked for confirmation before recording begins. " +
                                "The system will notify when recording has started. Summarization will occur after the recording is manually stopped by the user or if a duration was specified."
                    )
                    put("parameters", JSONObject().apply {
                        put("type", "object")
                        put("properties", JSONObject().apply {
                            put("topic", JSONObject().apply {
                                put("type", "string")
                                put("description", "Optional. A topic or title for the meeting recording. Helps in identifying the recording later.")
                            })
                            // Duration is complex for a single tool call that should return quickly.
                            // It's better if the user manually stops, or a separate "stop_recording" tool.
                            // For now, we'll omit duration from the AI's direct parameters.
                            // The app can offer a timer UI if needed.
                        })
                        // "required" can be empty if topic is truly optional
                    })
                })
            })

            // --- (Optional) Tool 7: Stop Meeting Recording ---
            // If you want the AI to be able to trigger a stop. Otherwise, user stops via UI.
            // put(JSONObject().apply {
            //     put("type", "function")
            //     put("function", JSONObject().apply {
            //         put("name", "stop_meeting_recording")
            //         put("description", "Stops the currently active meeting recording. The system will then attempt to summarize the recorded audio.")
            //         put("parameters", JSONObject().apply { put("type", "object"); put("properties", JSONObject()) }) // No parameters needed
            //     })
            // })

        }
        
        return tools
    }

    /**
     * Process message using AI Function Call Manager for enhanced educational responses
     */
    private suspend fun processMessageWithAIFunctions(userMessage: String, enableWebSearch: Boolean = true) {
        Log.d("ChatFragment", "Processing message with AI functions: $userMessage")
        
        try {
            // Use AIFunctionCallManager to process the message
            val response = aiFunctionCallManager.processMessageWithFunctions(
                message = userMessage,
                includeWebSearch = enableWebSearch
            )
            
            // Add the enhanced response to chat
            lifecycleScope.launch(Dispatchers.Main) {
                chatMessages.add(response)
                chatAdapter.submitList(chatMessages.toList()) {
                    binding.recyclerView.smoothScrollToPosition(chatMessages.size - 1)
                }
            }
            
        } catch (e: Exception) {
            Log.e("ChatFragment", "Error processing message with AI functions", e)
            
            // Fall back to regular chat completion
            lifecycleScope.launch(Dispatchers.Main) {
                showCustomToast("Enhanced AI features temporarily unavailable. Using standard mode.")
                handleChatCompletion(userMessage)
            }
        }
    }

    /**
     * Determine if a user message should use enhanced educational AI features
     */
    private fun shouldUseEducationalAI(message: String): Boolean {
        val lowerMessage = message.lowercase()
        
        // Educational keywords that should trigger enhanced AI
        val educationalKeywords = listOf(
            "teach", "learn", "explain", "lesson", "quiz", "test", "study", "homework", 
            "assignment", "practice", "example", "concept", "definition", "how to",
            "what is", "why does", "how does", "tutorial", "guide", "course", 
            "exercise", "problem", "solution", "formula", "theorem", "principle",
            "calculate", "solve", "prove", "demonstrate", "analyze", "evaluate",
            "math", "science", "history", "english", "literature", "physics", 
            "chemistry", "biology", "algebra", "geometry", "calculus", "statistics"
        )
        
        // Check if message contains educational keywords
        val hasEducationalKeywords = educationalKeywords.any { keyword ->
            lowerMessage.contains(keyword)
        }
        
        // Check if message is a question (likely educational)
        val isQuestion = lowerMessage.contains("?") || 
                        lowerMessage.startsWith("what") || 
                        lowerMessage.startsWith("how") || 
                        lowerMessage.startsWith("why") ||
                        lowerMessage.startsWith("when") ||
                        lowerMessage.startsWith("where") ||
                        lowerMessage.startsWith("who")
        
        // Check for educational sentence patterns
        val hasEducationalPatterns = lowerMessage.contains("i need help with") ||
                                   lowerMessage.contains("help me understand") ||
                                   lowerMessage.contains("can you explain") ||
                                   lowerMessage.contains("create a lesson") ||
                                   lowerMessage.contains("make a quiz") ||
                                   lowerMessage.contains("give me examples")
        
        return hasEducationalKeywords || (isQuestion && message.length > 10) || hasEducationalPatterns
    }














    private fun updateUIForCurrentModel() {
        when (currentModel) {
            "dall-e-3" -> {
                binding.messageEditText.hint = "Describe the image you want to generate..."
                binding.followUpQuestionsContainer.visibility = View.GONE
                binding.generatedImageView.visibility = View.VISIBLE
                updateActiveModelButton("DALL-E 3")
            }
            "gpt-image-1" -> {
                binding.messageEditText.hint = "Describe the image you want to generate (supports image input)..."
                binding.followUpQuestionsContainer.visibility = View.GONE
                binding.generatedImageView.visibility = View.VISIBLE
                updateActiveModelButton("GPT Image 1")
            }
            else -> {
                binding.messageEditText.hint = "Type your message here..."
                binding.followUpQuestionsContainer.visibility = View.VISIBLE
                binding.generatedImageView.visibility = View.GONE
                updateActiveModelButton(getDisplayNameForModel(currentModel))
            }
        }
    }



    private fun checkAndRequestPermissions(): Boolean {
        return if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            true
        } else {
            ActivityCompat.requestPermissions(
                requireActivity(),
                arrayOf(Manifest.permission.RECORD_AUDIO),
                REQUEST_RECORD_AUDIO_PERMISSION
            )
            false
        }

    }
















    private fun addMessageToList(chatMessage: ChatMessage, scrollToBottom: Boolean = true) {
        if (chatMessage.isTyping) {
            chatMessages.removeAll { it.isTyping }
        }
        chatMessages.add(chatMessage)
        chatAdapter.submitList(chatMessages.toList()) {
            if (scrollToBottom && chatMessages.isNotEmpty()) {
                // Post the scroll to ensure it occurs after RecyclerView layout
                binding.recyclerView.post {
                    binding.recyclerView.smoothScrollToPosition(chatMessages.size - 1)
                }
            }
            // Force redraw in case DiffUtil misses updates
            chatAdapter.notifyDataSetChanged()
        }
        if (!chatMessage.isTyping) {
            saveChatHistory()
        }
    }
    private fun addOlderMessagesToList(olderMessages: List<ChatMessage>) {
        if (olderMessages.isEmpty()) {
            isLoadingMoreMessages = false // Reset flag if no new messages
            return
        }
        if (olderMessages.isNotEmpty()) {
            chatMessages.addAll(0, olderMessages)
        }
        chatAdapter.submitList(chatMessages.toList()) {
            // Optional: maintain scroll position or scroll to a specific item
            // For chat, usually you don't scroll after loading older messages,
            // unless you want to keep the visual position of the current top item.
            // binding.recyclerView.scrollToPosition(olderMessages.size -1) // might be too abrupt
            isLoadingMoreMessages = false // Reset loading flag
        }
        saveChatHistory() // Save history including older messages
    }

    // In ChatFragment.kt

    // Helper function (you had this, make sure it's defined in ChatFragment)
    private fun determineIfRichContent(content: String): Boolean {
        // A simple check for HTML tags. You might need a more robust check.
        // For example, if your API explicitly tells you it's HTML.
        return content.contains("<") && content.contains(">") && (content.contains("</") || content.contains("/>"))
    }


    // In ChatFragment.kt

    private fun handleSuccessResponse(responseBody: String) {
        // This function is already launched on Dispatchers.Main by the calling function (handleChatCompletion)
        // So, UI updates here are safe. Suspend calls will need withContext(Dispatchers.IO).

        try {
            removeTypingIndicator() // Call this early, outside the async block if response parsing is quick

            val jsonResponse = JSONObject(responseBody)

            // Track usage for successful API responses
            val currentAIModel = com.playstudio.aiteacher.pricing.AIModel.fromModelId(currentModel)
            if (currentAIModel != null) {
                // Extract token usage from response if available
                val usage = jsonResponse.optJSONObject("usage")
                val inputTokens = usage?.optInt("prompt_tokens") ?: 200 // Default estimate
                val outputTokens = usage?.optInt("completion_tokens") ?: 300 // Default estimate

                // Track the usage asynchronously
                trackMessageUsage(currentAIModel, inputTokens, outputTokens)
            }
            val stopReason = jsonResponse.optString("stop_reason")
            if (stopReason == "refusal") {
                showCustomToast("Claude refused to answer the request.")
            }

            val choices = jsonResponse.optJSONArray("choices")
            // val usage = jsonResponse.optJSONObject("usage") // Keep if needed

            if (choices == null || choices.length() == 0) {
                showCustomToast("No valid response choices from API.")
                return
            }

            val choice = choices.getJSONObject(0)
            val messageFromApi = choice.getJSONObject("message") // The assistant's message object
            var originalReplyContent = messageFromApi.getString("content").trim()

            val openAIProvidedCitations = mutableListOf<com.playstudio.aiteacher.ChatFragment.Citation>()
            val openAIProvidedFollowUps = mutableListOf<String>()

            // --- Stage 1: Process OpenAI's built-in annotations (if any) ---
            var hasOpenAICitations = false
            if (messageFromApi.has("annotations")) {
                processBuiltInCitations(messageFromApi, openAIProvidedCitations, openAIProvidedFollowUps) // <<<< CALLING IT
                if (openAIProvidedCitations.isNotEmpty()) {
                    hasOpenAICitations = true
                }
            }

            // This will be the text ultimately displayed, potentially modified.
            var contentToDisplay = originalReplyContent
            var finalCitationsToShow = openAIProvidedCitations // Start with OpenAI's citations
            var finalFollowUpsToShow = openAIProvidedFollowUps.toMutableList() // Start with OpenAI's follow-ups
            var augmentedByCustomWebSearch = false

            // --- Stage 2: Perform custom Google Web Search if conditions are met ---
            // Only do custom search if:
            // 1. Web search is enabled in app settings.
            // 2. The reply suggests it (shouldAugmentWithWebSearch).
            // 3. The current model is NOT one that has its own built-in search (to avoid duplicate effort).
            // 4. (Optional) OpenAI didn't already provide citations.
            val performCustomSearch = isWebSearchEnabled &&
                    shouldAugmentWithWebSearch(originalReplyContent) && // <<<< CALLING IT
                    !WEB_SEARCH_MODELS.contains(currentModel) // WEB_SEARCH_MODELS are OpenAI's search models
            // && !hasOpenAICitations // Optional: only if OpenAI didn't provide any citations

            if (performCustomSearch) {
                Log.d("ChatFragment", "Custom web search triggered.")
                // Launch a new coroutine for the suspend function performGoogleSearch
                // This means the custom web search results might arrive *after* the initial AI reply is shown.
                // This is a common pattern for progressive enhancement.
                lifecycleScope.launch { // New coroutine for async web search
                    val searchQuery = extractSearchQuery(originalReplyContent) // <<<< CALLING IT
                    val customWebResults = withContext(Dispatchers.IO) {
                        performGoogleSearch(searchQuery) // <<<< CALLING IT
                    }

                    if (customWebResults.isNotEmpty()) {
                        val augmentedContent = enhanceResponseWithWebResults(originalReplyContent, customWebResults) // <<<< CALLING IT
                        augmentedByCustomWebSearch = true

                        // How to merge/display this?
                        // Option A: Update the existing message (complex with ListAdapter)
                        // Option B: Add a NEW message with the web search results
                        // Option C: Modify the 'contentToDisplay' IF the initial message hasn't been added yet.
                        // For simplicity with current structure, let's assume we might add a new message or
                        // this happens fast enough to be part of the initial message.
                        // If this block executes AFTER the initial AI message is added,
                        // you'd need to add a NEW ChatMessage for these web results.

                        // For now, let's assume we are building ONE ChatMessage.
                        // If OpenAI provided citations, decide how to merge.
                        // Here, we'll assume custom search results are primary if they exist.
                        contentToDisplay = augmentedContent
                        // If enhanceResponseWithWebResults provides its own "citation-like" info,
                        // you'd need to parse that and potentially create new Citation objects.
                        // finalCitationsToShow = parseCitationsFromAugmentedContent(augmentedContent) // Hypothetical
                        Log.d("ChatFragment", "Content augmented with custom web search.")

                        // Re-check rich content status after augmentation
                        val isRichNow = checkForRichContent(contentToDisplay) // <<<< CALLING IT
                        // Update the message IF IT WAS ALREADY ADDED (this is tricky part)
                        // OR ensure this whole block completes before any addMessageToChat call.

                        // To avoid complexity of updating an existing message, let's assume
                        // this whole 'handleSuccessResponse' aims to construct ONE ChatMessage object.
                        // This means performGoogleSearch should ideally complete before we call addMessageToChat.
                        // The launch for generateDynamicFollowUpQuestions also has this async nature.

                        // --- This structure implies we need to make `handleSuccessResponse` suspend or chain callbacks ---
                        // --- Let's simplify for now: the web search augmentation happens, THEN follow-ups, THEN add to chat ---
                    }

                    // This part will now execute *after* web search (if any) in its own coroutine.
                    // This needs to be rethought if we want one coherent message.
                    // For a single message, the structure needs to be sequential.
                    // Let's assume the below is the final step AFTER all content processing.
                }
                // If we launch the web search in a separate coroutine, the main flow continues.
                // This means the initial AI reply (without custom web search) would be added first.
                // This is a common source of conflict in complex async UI updates.

                // --- REVISED APPROACH: Make performGoogleSearch blocking within the main launch, then proceed ---
                // This means handleSuccessResponse should be prepared for a slight delay if web search happens.
            }

            // --- Let's re-structure for sequential processing within the initial launch block ---
            // The lifecycleScope.launch(Dispatchers.Main) from the start of this function continues here.

            lifecycleScope.launch { // New launch for the potentially suspending web search
                var processedContent = originalReplyContent // Start with the original
                var finalCitations = openAIProvidedCitations.toMutableList() // Start with OpenAI's

                if (performCustomSearch) {
                    val searchQuery = extractSearchQuery(originalReplyContent)
                    Log.d("ChatFragment", "Performing custom Google Search for: $searchQuery")
                    val customWebResults = withContext(Dispatchers.IO) {
                        performGoogleSearch(searchQuery)
                    }
                    if (customWebResults.isNotEmpty()) {
                        processedContent = enhanceResponseWithWebResults(originalReplyContent, customWebResults)
                        augmentedByCustomWebSearch = true
                        // If your enhanceResponseWithWebResults doesn't create Citation objects,
                        // and you want to represent them:
                        // customWebResults.forEach { webResult ->
                        //     finalCitations.add(Citation(webResult.url, webResult.title, -1, -1)) // -1 for indices if not in text
                        // }
                    }
                }

                // If OpenAI provided citations AND we didn't do a custom search OR want to combine:
                if (hasOpenAICitations && !augmentedByCustomWebSearch) {
                    // Prepend "Web Search Results" only if OpenAI citations exist and no custom search augmented it.
                    val prefix = "🔍 Official Search Results:\n\n"
                    processedContent = prefix + originalReplyContent // Use originalReplyContent for prefixing
                    // Adjust indices for openAIProvidedCitations if prefixing 'originalReplyContent'
                    finalCitations.forEach {
                        // This needs careful index math if citations refer to 'originalReplyContent'
                        // it.startIndex += prefix.length
                        // it.endIndex += prefix.length
                    }
                }
                // If augmentedByCustomWebSearch is true, processedContent already has the enhanced text.

                val containsRich = checkForRichContent(processedContent)

                // --- Final step: Generate follow-ups and add to chat ---
                if (finalFollowUpsToShow.isEmpty()) {
                    generateDynamicFollowUpQuestions(originalReplyContent) { generatedQuestions ->
                        finalFollowUpsToShow.addAll(generatedQuestions)
                        finalFollowUpsToShow = finalFollowUpsToShow.distinct().take(3).toMutableList()
                        addMessageToChat(
                            messageContent = processedContent,
                            isUser = false,
                            citations = finalCitations,
                            followUpQuestions = finalFollowUpsToShow,
                            containsRichContent = containsRich
                        )
                        addFollowUpQuestionsToChat(finalFollowUpsToShow)
                    }
                } else {
                    finalFollowUpsToShow = finalFollowUpsToShow.distinct().take(3).toMutableList()
                    addMessageToChat(
                        messageContent = processedContent,
                        isUser = false,
                        citations = finalCitations,
                        followUpQuestions = finalFollowUpsToShow,
                        containsRichContent = containsRich
                    )
                    addFollowUpQuestionsToChat(finalFollowUpsToShow)
                }

                // TTS is now automatically handled by addMessageToChat() when audio mode is enabled
                incrementInteractionCount()
                // Reset history after completing the tool-assisted turn
                currentConversationHistoryForToolCall.clear()
            }

        } catch (e: JSONException) {
            Log.e("ChatFragment", "Failed to parse success response JSON: $responseBody", e)
            // Ensure this runs on Main for UI update
            lifecycleScope.launch(Dispatchers.Main) {
                removeTypingIndicator()
                showCustomToast("Error processing API response.")
            }
        } catch (e: Exception) {
            Log.e("ChatFragment", "Unexpected error in handleSuccessResponse: ${e.message}", e)
            lifecycleScope.launch(Dispatchers.Main) {
                removeTypingIndicator()
                showCustomToast("An unexpected error occurred.")
            }
        }
    }






    private fun addFollowUpQuestionsToChat(questions: List<String>) {
        binding.followUpQuestionsContainer.removeAllViews()
        binding.followUpQuestionsContainer.visibility = View.GONE
    }


    private fun sendMessageToAPI(message: String) {
        when (currentModel) {
            "dall-e-3" -> handleImageGeneration(message)
            "gpt-image-1" -> handleGPTImageGeneration(message)
            else -> {
                handleChatCompletion(message)
            }
        }
    }


// In ChatFragment.kt

    private fun handleReasoningModelCompletion(message: String, model: String) {
        // For this specific API call, it seems you only send the current user message,
        // not the whole chat history. If you need to send history, use the pattern
        // from handleDeepSeekCompletion: iterate chatAdapter.currentList.
        val messagesArray = JSONArray().apply {
            put(JSONObject().apply {
                put("role", "user")
                put("content", message)
            })
        }

        val json = JSONObject().apply {
            put("model", model)
            put("messages", messagesArray)
            // Note: reasoning_effort is only for the new Responses API, not Chat Completions API
        }

        val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
        val request = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions") // Assuming OpenAI endpoint
            .post(body)
            .addHeader("Authorization", "Bearer ${getApiKey("openai") ?: ""}")
            .addHeader("Content-Type", "application/json")
            .build()

        Log.d("ChatFragment", "Sending request for reasoning model: $json")
        showTypingIndicator()

        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    client.newCall(request).execute()
                }
                val responseBody = response.body?.string()
                Log.d("ChatFragment", "Received response from reasoning model: $responseBody")

                if (!response.isSuccessful) {
                    withContext(Dispatchers.Main) {
                        when (response.code) {
                            400 -> showCustomToast("Bad Request (Reasoning): Check parameters")
                            401 -> showCustomToast("Unauthorized (Reasoning): Check API key")
                            // ... other error codes
                            else -> showCustomToast("API Error (Reasoning): ${response.code}")
                        }
                        removeTypingIndicator()
                    }
                    return@launch
                }

                responseBody?.let {
                    try {
                        val jsonResponse = JSONObject(it)
                        if (jsonResponse.has("choices")) {
                            val choices = jsonResponse.getJSONArray("choices")
                            if (choices.length() > 0) {
                                val reply = choices.getJSONObject(0).getJSONObject("message")
                                    .getString("content").trim()

                                // Extract token usage for tracking
                                var inputTokens = 0
                                var outputTokens = 0
                                if (jsonResponse.has("usage")) {
                                    val usage = jsonResponse.getJSONObject("usage")
                                    inputTokens = usage.optInt("prompt_tokens", 0)
                                    outputTokens = usage.optInt("completion_tokens", 0)
                                }

                                withContext(Dispatchers.Main) {
                                    removeTypingIndicator()
                                    addMessageToChat( // Your refactored method
                                        messageContent = reply,
                                        isUser = false,
                                        containsRichContent = determineIfRichContent(reply)
                                        // Parse and pass citations/followUps if this model provides them
                                    )

                                    // Track usage after successful response
                                    val currentAIModel = com.playstudio.aiteacher.pricing.AIModel.fromModelId(model)
                                    if (currentAIModel != null) {
                                        trackMessageUsage(currentAIModel, inputTokens, outputTokens)
                                    }

                                    // TTS is now automatically handled by addMessageToChat() when audio mode is enabled
                                    incrementInteractionCount()
                                }
                            } else { /* ... no choices handling ... */ }
                        } else { /* ... no 'choices' field handling ... */ }
                    } catch (e: JSONException) { /* ... JSON parsing error handling ... */ }
                } ?: withContext(Dispatchers.Main) { /* ... null response body handling ... */ }
            } catch (e: IOException) { /* ... IO error handling ... */ }
        }
    }

    // In ChatFragment.kt

    private fun handleErrorResponse(response: Response) {
        lifecycleScope.launch(Dispatchers.Main) {
            val errorMessage = when (response.code) {
                400 -> {
                    if (currentModel.startsWith("o")) {
                        "Insufficient context window for reasoning tokens"
                    } else {
                        "Bad request: Check parameters"
                    }
                }
                401 -> "Unauthorized: Check API key"
                403 -> "Forbidden: Access denied"
                429 -> "Rate limit exceeded"
                500 -> "Server error"
                503 -> "Service unavailable"
                else -> "Unexpected error: ${response.code}"
            }
            showCustomToast(errorMessage)
            removeTypingIndicator()
        }
    }

    // You need to implement this based on how you store chat history.
    // This is a placeholder.
    private suspend fun fetchOlderMessagesFromStorage(
        beforeMessageId: String?,
        limit: Int
    ): List<ChatMessage> {
        Log.d("ChatFragment", "Fetching older messages before: $beforeMessageId, limit: $limit")
        // This function should query your SharedPreferences (or database if you switch)
        // for 'limit' messages that are older than 'beforeMessageId'.
        // This is complex with SharedPreferences for pagination.
        // A database (SQLite with Room) would be much better for this.

        // --- Simplified SharedPreferences Example (Not truly paginated, loads all older than X) ---
        // This example is NOT efficient for large histories and won't truly paginate.
        // It's just to show the concept.
        val allMessages = mutableListOf<ChatMessage>()
        val sharedPreferences = requireContext().getSharedPreferences("prefs", Context.MODE_PRIVATE)
        val savedChatsJson = sharedPreferences.getString(chatHistoryKey, "[]")
        try {
            val allChatsArray = JSONArray(savedChatsJson)
            for (i in 0 until allChatsArray.length()) {
                val chatObject = allChatsArray.getJSONObject(i)
                if (chatObject.optString("id") == conversationId) {
                    val messagesArray = chatObject.getJSONArray("messages")
                    for (j in 0 until messagesArray.length()) {
                        val msg = parseChatMessageFromJson(messagesArray.getJSONObject(j))
                        allMessages.add(msg)
                    }
                    break
                }
            }
        } catch (e: Exception) { Log.e("ChatFragment", "Error fetching for pagination", e)}

        if (beforeMessageId == null) { // Initial load or no prior messages loaded
            return allMessages.takeLast(limit).reversed() // Get the latest 'limit' messages
        }

        val indexOfAnchor = allMessages.indexOfFirst { it.id == beforeMessageId }
        if (indexOfAnchor == -1 || indexOfAnchor == 0) return emptyList() // No messages before or anchor is the oldest

        val startIndex = (indexOfAnchor - limit).coerceAtLeast(0)
        return allMessages.subList(startIndex, indexOfAnchor).reversed() // Get 'limit' messages before the anchor
    }
    private fun startNewConversation() {
        val newId = generateConversationId()
        val appPrefs = requireContext().getSharedPreferences(PREFS_NAME_APP, Context.MODE_PRIVATE)
        appPrefs.edit().putString("last_conversation_id", newId).apply()
        conversationId = newId

        chatMessages.clear()
        chatAdapter.submitList(emptyList())

        val currentIntent = requireActivity().intent
        val intent = Intent(requireContext(), ChatActivity::class.java).apply {
            putExtra("selected_model", currentModel)
            putExtra("is_ad_free", currentIntent.getBooleanExtra("is_ad_free", false))
            putExtra("expiration_time", currentIntent.getLongExtra("expiration_time", 0))
        }
        startActivity(intent)
        activity?.finish()
    }

    // --- History and Pagination ---
    private fun loadOlderMessages() {
        if (isLoadingMoreMessages) return
        isLoadingMoreMessages = true

        val currentTopMessageId = chatMessages.firstOrNull { !it.isTyping }?.id

        lifecycleScope.launch {
            val olderMessages = withContext(Dispatchers.IO) {
                fetchOlderMessagesFromStorage(currentTopMessageId, MESSAGES_PAGE_SIZE)
            }
            withContext(Dispatchers.Main) {
                if (olderMessages.isNotEmpty()) {
                    addOlderMessagesToList(olderMessages)
                } else {
                    // No more older messages or an error occurred
                    isLoadingMoreMessages = false // Reset flag
                    // showCustomToast("No more messages to load.") // Optional
                }
            }
        }
    }

    // Helper functions
    private fun processBuiltInCitations(
        messageObj: JSONObject,
        citations: MutableList<Citation>,
        followUpQuestions: MutableList<String>
    ) {
        val annotations = messageObj.getJSONArray("annotations")
        for (i in 0 until annotations.length()) {
            val annotation = annotations.getJSONObject(i)
            when (annotation.getString("type")) {
                "url_citation" -> {
                    val citation = annotation.getJSONObject("url_citation")
                    citations.add(
                        Citation(
                            url = citation.getString("url"),
                            title = citation.optString("title", "Source"),
                            startIndex = citation.getInt("start_index"),
                            endIndex = citation.getInt("end_index")
                        )
                    )
                }
                "follow_up" -> {
                    val followUp = annotation.getJSONObject("follow_up")
                    followUpQuestions.add(followUp.getString("question"))
                }
            }
        }
    }


    private fun checkForRichContent(reply: String): Boolean {
        return reply.contains("```") ||
                reply.contains("|") ||
                reply.contains("- [ ]") ||
                reply.contains("\n- ") ||
                reply.contains("\n* ") ||
                reply.contains("\n1. ")
    }

    private suspend fun performGoogleSearch(query: String): List<WebResult> {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val url = "https://www.googleapis.com/customsearch/v1?" +
                "q=$encodedQuery&key=$GOOGLE_API_KEY&cx=$SEARCH_ENGINE_ID&num=3"

        return try {
            val response = client.newCall(Request.Builder().url(url).build()).execute()

            if (response.isSuccessful) {
                val json = response.body?.string() ?: return emptyList()
                val jsonResponse = JSONObject(json)
                val items = jsonResponse.optJSONArray("items") ?: return emptyList()

                (0 until items.length()).map { i ->
                    val item = items.getJSONObject(i)
                    WebResult(
                        title = item.getString("title"),
                        url = item.getString("link"),
                        snippet = item.optString("snippet", ""),
                        imageUrl = item.optJSONObject("pagemap")?.optJSONArray("cse_image")
                            ?.optJSONObject(0)?.optString("src", null)
                    )
                }
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun shouldAugmentWithWebSearch(reply: String): Boolean {
        return reply.contains("show me") ||
                reply.contains("what does look like") ||
                reply.contains("demonstrate") ||
                reply.contains("current") ||
                reply.contains("latest")
    }

    private fun extractSearchQuery(reply: String): String {
        return when {
            reply.contains("show me") -> reply.substringAfter("show me").trim()
            reply.contains("what does look like") ->
                reply.substringAfter("what does").substringBefore("look like").trim()
            else -> reply
        }
    }

    private fun enhanceResponseWithWebResults(reply: String, results: List<WebResult>): String {
        val builder = StringBuilder(reply)

        // Add top web result
        builder.append("\n\n🔍 From the web:")
        results.take(3).forEach { result ->
            builder.append("\n\n• ${result.title}")
            builder.append("\n${result.snippet}")
            builder.append("\n${result.url}")
        }

        // Add image if available
        results.firstOrNull { it.imageUrl != null }?.let {
            builder.append("\n\nVisual reference:")
            builder.append("\n${it.imageUrl}")
        }

        return builder.toString()
    }
    private fun addMessageToChat(
        messageContent: String,
        isUser: Boolean,
        citations: List<com.playstudio.aiteacher.ChatFragment.Citation> = emptyList(),
        followUpQuestions: List<String> = emptyList(),
        containsRichContent: Boolean = false // Pass this flag
    ) {
        val newChatMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            content = messageContent,
            isUser = isUser,
            citations = citations,
            followUpQuestions = followUpQuestions,
            containsRichContent = containsRichContent
        )
        addMessageToList(newChatMessage)

        // Auto-save meeting summaries when AI response is complete
        if (!isUser && pendingMeetingSummaryType != null && pendingMeetingTranscript != null) {
            saveMeetingSummary(messageContent, pendingMeetingSummaryType!!, pendingMeetingTranscript!!)
            // Clear pending state
            pendingMeetingSummaryType = null
            pendingMeetingTranscript = null
        }

        // Generate TTS for AI responses when audio mode is enabled BUT NOT in realtime mode
        // (RealtimeVoiceAgent handles its own audio output)
        if (!isUser && isAudioModeEnabled && messageContent.isNotBlank() && !isRealtimeMode) {
            Log.d("ChatFragment", "Generating TTS for AI response, audio mode enabled: $isAudioModeEnabled")
            lifecycleScope.launch {
                generateTextToSpeech(messageContent)
            }
        } else if (!isUser && messageContent.isNotBlank()) {
            Log.d("ChatFragment", "TTS skipped - audio mode enabled: $isAudioModeEnabled, isUser: $isUser, realtime mode: $isRealtimeMode")
        }
    }


    private fun generateDynamicFollowUpQuestions(reply: String, callback: (List<String>) -> Unit) {
        val prompt = """
        Based on the following AI response, generate 3 relevant follow-up questions 
        that would help continue the conversation. Return them as a JSON array of strings.
        
        Response: $reply
        
        Example format: ["Question 1", "Question 2", "Question 3"]
    """.trimIndent()

        val messagesArray = JSONArray().apply {
            put(JSONObject().apply {
                put("role", "user")
                put("content", prompt)
            })
        }

        val json = JSONObject().apply {
            put("model", "gpt-3.5-turbo") // Use a lightweight model for this
            put("messages", messagesArray)
            put("temperature", 0.7)
            put("max_completion_tokens", 150)
        }

        val request = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .post(json.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull()))
            .addHeader("Authorization", "Bearer ${getApiKey("openai") ?: ""}")
            .build()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    val jsonResponse = JSONObject(responseBody!!)
                    val content = jsonResponse.getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content")

                    // Parse the JSON array from the response
                    val questions = try {
                        JSONArray(content).let { array ->
                            List(array.length()) { array.getString(it) }
                        }
                    } catch (e: Exception) {
                        // Fallback to parsing as plain text if JSON parsing fails
                        content.split("\n")
                            .map { it.trim() }
                            .filter { it.isNotBlank() && it.startsWith("\"") && it.endsWith("\"") }
                            .map { it.removeSurrounding("\"") }
                    }

                    withContext(Dispatchers.Main) {
                        callback(questions.take(3)) // Ensure we only return 3 questions
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        callback(emptyList()) // Fallback to no questions if API fails
                    }
                }
            } catch (e: Exception) {
                Log.e("ChatFragment", "Error generating follow-up questions", e)
                withContext(Dispatchers.Main) {
                    callback(emptyList()) // Fallback to no questions on error
                }
            }
        }
    }
    private fun handleNetworkError(e: IOException) {
        lifecycleScope.launch(Dispatchers.Main) {
            showCustomToast("Network error: ${e.message}")
            removeTypingIndicator()
        }
    }

    private fun playAudioFromFile(file: File) {
        val mediaPlayer = MediaPlayer().apply {
            setDataSource(file.absolutePath)
            prepare()
            start()
        }
    }

    private fun handleImageGeneration(prompt: String, retryCount: Int = 3) {
        if (!isNetworkAvailable()) {
            showCustomToast("No internet connection. Please check your network settings.")
            return
        }

        // Show "Generating..." text and hide other elements
        binding.generatingText.visibility = View.VISIBLE
        binding.generatedImageView.visibility = View.GONE
        binding.downloadButton.visibility = View.GONE

        // Start the "Generating..." animation
        startGeneratingAnimation()

        val json = JSONObject().apply {
            put("model", "dall-e-3")
            put("prompt", prompt)
            put("n", 1)
            put("size", "1024x1024")
            put("quality", "standard")
            put("style", "vivid")
        }

        val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
        val request = Request.Builder()
            .url("https://api.openai.com/v1/images/generations")
            .post(body)
            .addHeader("Authorization", "Bearer ${getApiKey("openai") ?: ""}")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (retryCount > 0) {
                    Log.d("ChatFragment", "Retrying image generation... Attempts left: $retryCount")
                    handleImageGeneration(prompt, retryCount - 1)
                } else {
                    Log.e("ChatFragment", "Failed to get image generation response", e)
                    requireActivity().runOnUiThread {
                        showCustomToast("Failed to generate image. Please check your internet connection.")
                        binding.generatingText.visibility = View.GONE
                        binding.downloadButton.visibility = View.GONE

                        // Stop the "Generating..." animation on failure
                        stopGeneratingAnimation()
                    }
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful || response.body == null) {
                    Log.e("ChatFragment", "Unexpected code $response")
                    requireActivity().runOnUiThread {
                        showCustomToast("Unexpected response from image generation API")
                        binding.generatingText.visibility = View.GONE
                        binding.downloadButton.visibility = View.GONE

                        // Stop the "Generating..." animation on error
                        stopGeneratingAnimation()
                    }
                    return
                }

                val responseBody = response.body?.string()
                Log.d("ChatFragment", "Received response: $responseBody")

                try {
                    val jsonResponse = JSONObject(responseBody)
                    val data = jsonResponse.getJSONArray("data")
                    if (data.length() > 0) {
                        val imageUrl = data.getJSONObject(0).getString("url")
                        val revisedPrompt = data.getJSONObject(0).optString("revised_prompt", prompt)

                        requireActivity().runOnUiThread {
                            // Display the generated image
                            Glide.with(this@ChatFragment)
                                .load(imageUrl)
                                .into(binding.generatedImageView)
                            binding.generatedImageView.visibility = View.VISIBLE

                            // Show the download button and change its color to green
                            binding.downloadButton.visibility = View.VISIBLE
                            binding.downloadButton.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.green)
                            binding.downloadButton.setOnClickListener {
                                downloadImage(imageUrl)
                            }

                            // Track usage after successful image generation
                            val currentAIModel = com.playstudio.aiteacher.pricing.AIModel.fromModelId("dall-e-3")
                            if (currentAIModel != null) {
                                // For image generation, we don't have token usage, so use nominal values
                                trackMessageUsage(currentAIModel, 0, 0)
                                // Deduct from the shared token pool as an image response (1 image)
                                lifecycleScope.launch {
                                    com.playstudio.aiteacher.credits.TokenPoolIntegration.getInstance(requireContext())
                                        .processImageResponse(
                                            modelName = currentAIModel.modelId,
                                            inputTokens = 0,
                                            outputTokens = 0,
                                            imageCount = 1,
                                            agentName = "image-generator",
                                            userId = "default_user",
                                            userTier = subscriptionUIManager.getUserSubscriptionTier().toTokenPoolTier()
                                        )
                                }
                            }

                            // Hide the "Generating..." text and stop the animation
                            binding.generatingText.visibility = View.GONE
                            stopGeneratingAnimation()
                        }
                    } else {
                        requireActivity().runOnUiThread {
                            showCustomToast("No image generated")
                            binding.generatingText.visibility = View.GONE
                            binding.downloadButton.visibility = View.GONE

                            // Stop the "Generating..." animation if no image is generated
                            stopGeneratingAnimation()
                        }
                    }
                } catch (e: JSONException) {
                    Log.e("ChatFragment", "Failed to parse image generation response", e)
                    requireActivity().runOnUiThread {
                        showCustomToast("Failed to parse image generation response")
                        binding.generatingText.visibility = View.GONE
                        binding.downloadButton.visibility = View.GONE

                        // Stop the "Generating..." animation on parsing error
                        stopGeneratingAnimation()
                    }
                }
            }
        })
    }

    /**
     * Enhanced image generation using GPT Image 1 with multimodal support
     * Supports both text-only and text+image inputs
     */
    private fun handleGPTImageGeneration(prompt: String, inputImageBase64: String? = null, quality: String = "medium", size: String = "1024x1024", retryCount: Int = 3) {
        if (!isNetworkAvailable()) {
            showCustomToast("No internet connection. Please check your network settings.")
            return
        }

        // Validate quality and size parameters
        val validQualities = listOf("low", "medium", "high")
        val validSizes = listOf("1024x1024", "1024x1536", "1536x1024")
        val finalQuality = if (quality in validQualities) quality else "medium"
        val finalSize = if (size in validSizes) size else "1024x1024"

        // Show "Generating..." text and hide other elements
        binding.imageContainer.visibility = View.VISIBLE
        binding.generatingText.visibility = View.VISIBLE
        binding.generatedImageView.visibility = View.GONE
        binding.downloadButton.visibility = View.GONE

        // Start the "Generating..." animation
        startGeneratingAnimation()
        
        Log.d("ChatFragment", "🎨 GPT Image 1 generation started with prompt: '$prompt'")

        // Build request for GPT Image 1 using images/generations endpoint
        // This should work similar to DALL-E 3 but with GPT Image 1 model
        val requestJson = JSONObject().apply {
            put("model", "gpt-image-1")
            put("prompt", prompt)
            put("n", 1)
            put("size", finalSize)
            put("quality", finalQuality)
            
            // Add input image if provided (multimodal capability)
            inputImageBase64?.let { imageData ->
                put("input_image", "data:image/jpeg;base64,$imageData")
            }
        }

        val body = requestJson.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
        val request = Request.Builder()
            .url("https://api.openai.com/v1/images/generations")
            .post(body)
            .addHeader("Authorization", "Bearer ${getApiKey("openai") ?: ""}")
            .addHeader("Content-Type", "application/json")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (retryCount > 0) {
                    Log.d("ChatFragment", "Retrying GPT Image 1 generation... Attempts left: $retryCount")
                    handleGPTImageGeneration(prompt, inputImageBase64, finalQuality, finalSize, retryCount - 1)
                } else {
                    Log.e("ChatFragment", "Failed to get GPT Image 1 response", e)
                    requireActivity().runOnUiThread {
                        showCustomToast("Failed to generate image with GPT Image 1. Please try again.")
                        binding.imageContainer.visibility = View.GONE
                        binding.generatingText.visibility = View.GONE
                        binding.downloadButton.visibility = View.GONE
                        stopGeneratingAnimation()
                    }
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful || response.body == null) {
                    Log.e("ChatFragment", "GPT Image 1 request failed: ${response.code} - ${response.message}")
                    requireActivity().runOnUiThread {
                        showCustomToast("GPT Image 1 request failed: ${response.message}")
                        binding.imageContainer.visibility = View.GONE
                        binding.generatingText.visibility = View.GONE
                        binding.downloadButton.visibility = View.GONE
                        stopGeneratingAnimation()
                    }
                    return
                }

                try {
                    val responseData = JSONObject(response.body!!.string())
                    Log.d("ChatFragment", "GPT Image 1 response: $responseData")

                    // Parse response using DALL-E format (GPT Image 1 may return b64_json or url)
                    val data = responseData.optJSONArray("data")
                    if (data != null && data.length() > 0) {
                        val firstImage = data.getJSONObject(0)
                        val revisedPrompt = firstImage.optString("revised_prompt", prompt)
                        
                        // GPT Image 1 may return base64 data instead of URL
                        var imageUrl: String? = null
                        
                        if (firstImage.has("url")) {
                            // Standard URL format (like DALL-E 3)
                            imageUrl = firstImage.getString("url")
                        } else if (firstImage.has("b64_json")) {
                            // Base64 format (GPT Image 1 format)
                            val base64Data = firstImage.getString("b64_json")
                            imageUrl = "data:image/png;base64,$base64Data"
                            Log.d("ChatFragment", "GPT Image 1 returned base64 data, converted to data URL")
                        }
                        
                        if (imageUrl != null) {
                            requireActivity().runOnUiThread {
                                Log.d("ChatFragment", "🎨 Loading GPT Image 1 result, URL length: ${imageUrl.length}")
                                
                                // Display the generated image using Glide (same as DALL-E 3)
                                Glide.with(this@ChatFragment)
                                    .load(imageUrl)
                                    .listener(object : com.bumptech.glide.request.RequestListener<android.graphics.drawable.Drawable> {
                                        override fun onLoadFailed(
                                            e: com.bumptech.glide.load.engine.GlideException?,
                                            model: Any?,
                                            target: com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable>?,
                                            isFirstResource: Boolean
                                        ): Boolean {
                                            Log.e("ChatFragment", "🎨 Glide failed to load GPT Image 1", e)
                                            showCustomToast("Failed to display generated image")
                                            return false
                                        }
                                        
                                        override fun onResourceReady(
                                            resource: android.graphics.drawable.Drawable?,
                                            model: Any?,
                                            target: com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable>?,
                                            dataSource: com.bumptech.glide.load.DataSource?,
                                            isFirstResource: Boolean
                                        ): Boolean {
                                            Log.d("ChatFragment", "🎨 GPT Image 1 successfully loaded and displayed")
                                            return false
                                        }
                                    })
                                    .into(binding.generatedImageView)
                                binding.imageContainer.visibility = View.VISIBLE
                                binding.generatedImageView.visibility = View.VISIBLE

                                // Show the download button and change its color to green
                                binding.downloadButton.visibility = View.VISIBLE
                                binding.downloadButton.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.green)
                                binding.downloadButton.setOnClickListener {
                                    downloadImage(imageUrl)
                                }
                                
                                // Calculate and track usage with proper pricing
                                val currentAIModel = com.playstudio.aiteacher.pricing.AIModel.fromModelId("gpt-image-1")
                                if (currentAIModel != null) {
                                    // Calculate cost based on quality and size
                                    val imageCost = currentAIModel.imageOutputCostPerImage[finalQuality]?.get(finalSize) ?: 0.0
                                    
                                    // Convert cost to token equivalent for tracking (approximation)
                                    val equivalentOutputTokens = (imageCost / currentAIModel.outputCostPer1M * 1000000).toInt()
                                    
                                    trackMessageUsage(currentAIModel, 100, equivalentOutputTokens) // 100 tokens for prompt
                                    // Also deduct from unified token pool as an image response (1 image)
                                    lifecycleScope.launch {
                                        com.playstudio.aiteacher.credits.TokenPoolIntegration.getInstance(requireContext())
                                            .processImageResponse(
                                                modelName = currentAIModel.modelId,
                                                inputTokens = 100,
                                                outputTokens = equivalentOutputTokens,
                                                imageCount = 1,
                                                agentName = "gpt-image-1",
                                                userId = "default_user",
                                                userTier = subscriptionUIManager.getUserSubscriptionTier().toTokenPoolTier()
                                            )
                                    }
                                }

                                binding.generatingText.visibility = View.GONE
                                stopGeneratingAnimation()
                            }
                        } else {
                            requireActivity().runOnUiThread {
                                showCustomToast("No image URL found in GPT Image 1 response")
                                binding.generatingText.visibility = View.GONE
                                binding.downloadButton.visibility = View.GONE
                                stopGeneratingAnimation()
                            }
                        }
                    } else {
                        requireActivity().runOnUiThread {
                            showCustomToast("No image data in GPT Image 1 response")
                            binding.generatingText.visibility = View.GONE
                            binding.downloadButton.visibility = View.GONE
                            stopGeneratingAnimation()
                        }
                    }
                } catch (e: JSONException) {
                    Log.e("ChatFragment", "Failed to parse GPT Image 1 response", e)
                    requireActivity().runOnUiThread {
                        showCustomToast("Failed to parse GPT Image 1 response")
                        binding.generatingText.visibility = View.GONE
                        binding.downloadButton.visibility = View.GONE
                        stopGeneratingAnimation()
                    }
                }
            }
        })
    }



    // Old subscription prompt methods removed - replaced with new subscription system

    private fun isUserCurrentlySubscribed(): Boolean {
        return try {
            // Use the new authentication and Firestore-based subscription system
            val subscriptionUIManager = SubscriptionUIManager(requireContext())
            lifecycleScope.launch {
                val tier = subscriptionUIManager.getUserSubscriptionTier()
                // Cache the result for synchronous use
                val sharedPreferences = requireContext().getSharedPreferences("prefs", Context.MODE_PRIVATE)
                sharedPreferences.edit().putBoolean("current_subscription_active", tier != com.playstudio.aiteacher.pricing.SubscriptionTier.FREE).apply()
            }

            // For immediate synchronous response, check cached value or fallback to old method
            val sharedPreferences = requireContext().getSharedPreferences("prefs", Context.MODE_PRIVATE)
            val cachedSubscriptionActive = sharedPreferences.getBoolean("current_subscription_active", false)
            val expirationTime = sharedPreferences.getLong("expiration_time", 0)

            // Return true if either new system shows active subscription or old system shows valid expiration
            cachedSubscriptionActive || (System.currentTimeMillis() < expirationTime)
        } catch (e: Exception) {
            // Fallback to old SharedPreferences method if error
            val sharedPreferences = requireContext().getSharedPreferences("prefs", Context.MODE_PRIVATE)
            val expirationTime = sharedPreferences.getLong("expiration_time", 0)
            System.currentTimeMillis() < expirationTime
        }
    }

    /**
     * Asynchronous version that properly checks Firestore authentication and subscription
     */
    private suspend fun isUserCurrentlySubscribedAsync(): Boolean {
        return try {
            val subscriptionUIManager = SubscriptionUIManager(requireContext())
            val tier = subscriptionUIManager.getUserSubscriptionTier()
            tier != com.playstudio.aiteacher.pricing.SubscriptionTier.FREE
        } catch (e: Exception) {
            Log.e("ChatFragment", "Error checking subscription status", e)
            false
        }
    }


    private fun startGeneratingAnimation() {
        val blinkAnimation = AnimationUtils.loadAnimation(requireContext(), R.anim.blink)
        binding.generatingText.startAnimation(blinkAnimation)
    }

    private fun stopGeneratingAnimation() {
        binding.generatingText.clearAnimation()
    }

    private fun downloadImage(imageUrl: String) {
        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    client.newCall(Request.Builder().url(imageUrl).build()).execute()
                }

                if (!response.isSuccessful || response.body == null) {
                    Log.e("ChatFragment", "Failed to download image")
                    withContext(Dispatchers.Main) {
                        showCustomToast("Failed to download image")
                    }
                    return@launch
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    saveImageUsingMediaStore(response.body!!.byteStream())
                } else {
                    saveImageUsingFileSystem(response.body!!.byteStream())
                }
            } catch (e: IOException) {
                Log.e("ChatFragment", "Failed to download image", e)
                withContext(Dispatchers.Main) {
                    showCustomToast("Failed to download image")
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private suspend fun saveImageUsingMediaStore(inputStream: InputStream) {
        withContext(Dispatchers.IO) {
            val fileName = "generated_image_${System.currentTimeMillis()}.png"

            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
            }

            val resolver = requireContext().contentResolver
            val imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

            if (imageUri != null) {
                resolver.openOutputStream(imageUri)?.use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
                withContext(Dispatchers.Main) {
                    showCustomToast("Image saved to Pictures directory")
                }
            } else {
                withContext(Dispatchers.Main) {
                    showCustomToast("Failed to save image")
                }
            }
        }
    }

    private suspend fun saveImageUsingFileSystem(inputStream: InputStream) {
        withContext(Dispatchers.IO) {
            val fileName = "generated_image_${System.currentTimeMillis()}.png"
            val file = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                fileName
            )

            FileOutputStream(file).use { outputStream ->
                inputStream.copyTo(outputStream)
            }

            withContext(Dispatchers.Main) {
                showCustomToast("Image saved to ${file.absolutePath}")
            }
        }
    }

    private fun handleMessage(message: String) {
        val response = generateResponse(message)
        when (currentModel) {
            "dall-e-3" -> {
                if (checkDailyLimit("dall-e-3", DAILY_LIMIT_DALLE)) {
                    handleImageGeneration(message)
                    binding.messageEditText.text.clear()
                    // Usage will be tracked in trackMessageUsage() after successful generation
                } else {
                    showCustomToast("Daily limit for DALL-E 3 reached.")
                }
            }
            "gpt-image-1" -> {
                // Direct access to GPT Image 1 for all users
                Log.d("ChatFragment", "GPT Image 1 generation - direct access for all users")
                handleGPTImageGeneration(message)
                binding.messageEditText.text.clear()
            }
            "gemini", "gemini-2.5-flash" -> {
                if (checkDailyLimit("gemini", DAILY_LIMIT_GEMINI)) {
                    handleGeminiCompletion(message)
                    binding.messageEditText.text.clear()
                    // Usage will be tracked in trackMessageUsage() after successful response
                } else {
                    showCustomToast("Daily limit for Gemini reached.")
                }
            }
            "deepseek" -> {
                if (checkDailyLimit("deepseek", DAILY_LIMIT_DEEPSEEK)) {
                    handleDeepSeekCompletion(message)
                    binding.messageEditText.text.clear()
                    // Usage will be tracked in trackMessageUsage() after successful response
                } else {
                    showCustomToast("Daily limit for DeepSeek reached.")
                }
            }
            "veo-3.0-generate-preview", "veo-3.0-fast-generate-preview", "veo-2.0-generate-001" -> {
                // Use new subscription-based limit system for Veo models
                val currentAIModel = com.playstudio.aiteacher.pricing.AIModel.fromModelId(currentModel)
                if (currentAIModel != null) {
                    lifecycleScope.launch {
                        val canSend = checkUsageBeforeMessage(currentAIModel)
                        if (canSend) {
                            Log.d("ChatFragment", "Veo usage check passed, generating video")
                            handleVeoVideoGeneration(message)
                            binding.messageEditText.text.clear()
                        } else {
                            Log.d("ChatFragment", "Veo usage limit reached")
                            showCustomToast("Veo video generation limit reached for your subscription tier.")
                        }
                    }
                } else {
                    Log.e("ChatFragment", "Veo model not found in AIModel enum")
                    showCustomToast("Veo model configuration error.")
                }
            }
            "o1", "o1-mini", "o3", "o3-mini" -> {
                if (checkDailyLimit(currentModel, DAILY_LIMIT_GPT4)) {
                    handleReasoningModelCompletion(message, currentModel)
                    binding.messageEditText.text.clear()
                    // Usage will be tracked in trackMessageUsage() after successful response
                } else {
                    showCustomToast("Daily limit for $currentModel reached.")
                }
            }
            else -> {
                if (isUserCurrentlySubscribed() || canSendMessage) {
                    // Check if this is an educational query that should use enhanced AI functions
                    if (shouldUseEducationalAI(message)) {
                        Log.d("ChatFragment", "Using enhanced educational AI for query: $message")
                        lifecycleScope.launch {
                            processMessageWithAIFunctions(message, enableWebSearch = true)
                        }
                    } else {
                        sendMessageToAPI(response)
                    }
                    binding.messageEditText.text.clear()
                    if (!isUserCurrentlySubscribed()) {
                        canSendMessage = false
                    }
                } else if (checkDailyMessageLimit()) {
                    incrementMessageCount()
                    sendMessageToAPI(response)
                    binding.messageEditText.text.clear()
                } else {
                    //showInterstitialAd() // Show interstitial ad if daily message limit is reached
                }
                // Old subscription prompt removed
            }
        }
    }

    private fun handleDalle3Request(message: String) {
        handleImageGeneration(message)
        binding.messageEditText.text.clear()
        // Usage will be tracked in trackMessageUsage() after successful generation
    }


    // In ChatFragment.kt - THIS IS THE VERSION TO KEEP AND USE
    private fun addMessageToChat(
        messageContent: String,
        isUser: Boolean,
        citations: List<com.playstudio.aiteacher.ChatFragment.Citation> = emptyList(),
        followUpQuestions: List<String> = emptyList(),
        containsRichContent: Boolean = false,
        isWebSearchResult: Boolean = false // If you adopted this from my suggestion
    ) {
        val newChatMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            content = messageContent,
            isUser = isUser,
            citations = citations,
            followUpQuestions = followUpQuestions,
            containsRichContent = containsRichContent,
            isWebSearchResult = isWebSearchResult // Add this if using
            // timestamp will be set by default in ChatMessage constructor
        )
        addMessageToList(newChatMessage) // Your helper that calls submitList

        // Generate TTS for AI responses when audio mode is enabled BUT NOT in realtime mode
        // (RealtimeVoiceAgent handles its own audio output)
        if (!isUser && isAudioModeEnabled && messageContent.isNotBlank() && !isRealtimeMode) {
            Log.d("ChatFragment", "Generating TTS for AI response, audio mode enabled: $isAudioModeEnabled")
            lifecycleScope.launch {
                generateTextToSpeech(messageContent)
            }
        } else if (!isUser && messageContent.isNotBlank()) {
            Log.d("ChatFragment", "TTS skipped - audio mode enabled: $isAudioModeEnabled, isUser: $isUser, realtime mode: $isRealtimeMode")
        }
    }
    private fun generateResponse(userQuery: String): String {
        val baseResponse = "Here is the explanation for your query: $userQuery"
        val needsDiagramKeywords = listOf("diagram", "sketch", "draw", "looks like", "visualize", "illustrate", "chart", "graph")

        val needsDiagram = needsDiagramKeywords.any { keyword ->
            userQuery.contains(keyword, ignoreCase = true)
        }

        return if (needsDiagram) {
            val searchQuery = userQuery.replace(" ", "+")
            val searchUrl = "https://www.google.com/search?q=$searchQuery+diagram"
            "$baseResponse<br>As an AI, I am text-based and cannot provide clickable links or visual content directly. However, you can find relevant diagrams and sketches by visiting: <a href=\"$searchUrl\">this link</a>."
        } else {
            baseResponse
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetworkInfo = connectivityManager.activeNetworkInfo
        return activeNetworkInfo != null && activeNetworkInfo.isConnected
    }

    private fun generateFollowUpQuestions(response: String) {
        val prompt = "Based on the following response, generate 3 follow-up questions that the user can send to AI for an answer: $response"

        // Use hardcoded OpenAI model for follow-up questions to avoid routing issues
        val messagesArray = JSONArray().apply {
            put(JSONObject().apply {
                put("role", "user")
                put("content", prompt)
            })
        }

        val json = JSONObject().apply {
            put("model", "gpt-3.5-turbo") // Hardcoded lightweight model
            put("messages", messagesArray)
            put("temperature", 0.7)
            put("max_completion_tokens", 150)
        }

        val request = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .post(json.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull()))
            .addHeader("Authorization", "Bearer ${getApiKey("openai") ?: ""}")
            .addHeader("Content-Type", "application/json")
            .build()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val apiResponse = client.newCall(request).execute()
                if (apiResponse.isSuccessful) {
                    val responseBody = apiResponse.body?.string()
                    val jsonResponse = JSONObject(responseBody!!)
                    val followUpResponse = jsonResponse.getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content")

                    val questions = followUpResponse.split("\n").filter { it.isNotBlank() }
                    requireActivity().runOnUiThread {
                        addFollowUpQuestionsToChat(questions)
                    }
                }
            } catch (e: Exception) {
                Log.e("ChatFragment", "Error generating follow-up questions", e)
            }
        }
    }

    private fun loadRewardedAd() {
        val adRequest = AdRequest.Builder().build()
        RewardedAd.load(
            requireContext(),
            "ca-app-pub-9180832030816304/2247664120",
            adRequest,
            object : RewardedAdLoadCallback() {
                override fun onAdFailedToLoad(adError: LoadAdError) {
                    Log.d("ChatFragment", adError.message)
                    rewardedAd = null
                }

                override fun onAdLoaded(ad: RewardedAd) {
                    Log.d("ChatFragment", "Ad was loaded.")
                    rewardedAd = ad
                }
            }
        )
    }

    private fun showRewardedAd() {
        if (!isUserCurrentlySubscribed()) {
            rewardedAd?.let { ad ->
                ad.show(requireActivity()) { rewardItem: RewardItem ->
                    Log.d("ChatFragment", "User earned the reward.")
                    canSendMessage = true
                    showCustomToast("You can now send a message.")
                    loadRewardedAd()
                }
            } ?: run {
                Log.d("ChatFragment", "The rewarded ad wasn't ready yet.")
                showCustomToast("Please try again in a moment or consider upgrading for unlimited access.")
                loadRewardedAd()
            }
        } else {
            showExtraToast("The ad is not ready yet. Please try again later.")
        }
    }

    private fun showExtraToast(message: String) {
        val inflater = layoutInflater
        val layout: View = inflater.inflate(R.layout.extra_toast, null)

        val text: TextView = layout.findViewById(R.id.toast_text)
        text.text = message

        val toast = Toast(requireContext())
        toast.duration = Toast.LENGTH_LONG
        toast.view = layout
        toast.show()
    }

    private fun showCustomToast(message: String) {
        if (isAdded) {
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        } else {
            Log.w("ChatFragment", "Cannot show toast: Fragment not attached to context.")
        }
    }


    private fun sendMessageToChatGPT(prompt: String, callback: (String) -> Unit) {
        val messagesArray = JSONArray().apply {
            put(JSONObject().apply {
                put("role", "user")
                put("content", prompt)
            })
        }

        val json = JSONObject().apply {
            put("model", currentModel)
            put("messages", messagesArray)
            if (currentModel == "o1" || currentModel == "o3-mini") {
                put("reasoning_effort", "medium")
            }
        }

        val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
        val request = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .post(body)
            .addHeader("Authorization", "Bearer ${getApiKey("openai") ?: ""}")
            .addHeader("Content-Type", "application/json")
            .build()

        Log.d("ChatFragment", "Sending request: $json")

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()
                Log.d("ChatFragment", "Received response: $responseBody")

                when {
                    response.code == 401 -> { // Unauthorized - likely API key issue
                        consecutiveApiKeyErrors++
                        withContext(Dispatchers.Main) {
                            if (consecutiveApiKeyErrors >= MAX_API_KEY_ERRORS_BEFORE_UPDATE) {
                                showApiKeyUpdateRequiredDialog()
                            } else {
                                showCustomToast(getString(R.string.api_key_error))
                            }
                        }
                        return@launch
                    }
                    !response.isSuccessful -> {
                        withContext(Dispatchers.Main) {
                            showCustomToast("Unexpected response from server")
                        }
                        return@launch
                    }
                    else -> {
                        consecutiveApiKeyErrors = 0 // Reset counter on successful response
                        responseBody?.let {
                            try {
                                val jsonResponse = JSONObject(it)
                                val choices = jsonResponse.optJSONArray("choices")

                                if (choices != null && choices.length() > 0) {
                                    val reply = choices.getJSONObject(0).getJSONObject("message").getString("content").trim()
                                    withContext(Dispatchers.Main) {
                                        callback(reply)
                                    }
                                } else {
                                    withContext(Dispatchers.Main) {
                                        showCustomToast("No choices found in the response")
                                    }
                                }
                            } catch (e: JSONException) {
                                Log.e("ChatFragment", "Failed to parse response", e)
                                withContext(Dispatchers.Main) {
                                    showCustomToast("Failed to parse response")
                                }
                            }
                        }
                    }
                }
            } catch (e: IOException) {
                Log.e("ChatFragment", "Failed to get response", e)
                withContext(Dispatchers.Main) {
                    showCustomToast("Failed to get response")
                }
            }
        }
    }

    private fun showApiKeyUpdateRequiredDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.api_key_update_required))
            .setMessage(getString(R.string.api_key_update_message))
            .setPositiveButton(getString(R.string.update_button)) { _, _ ->
                openPlayStore()
            }
            .setNegativeButton(getString(R.string.later_button), null)
            .setCancelable(false)
            .show()
    }

    private fun openPlayStore() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW,
                Uri.parse("market://details?id=${requireContext().packageName}")))
        } catch (e: ActivityNotFoundException) {
            startActivity(Intent(Intent.ACTION_VIEW,
                Uri.parse("https://play.google.com/store/apps/details?id=${requireContext().packageName}")))
        }
    }

    fun updateSubscriptionStatus(isAdFree: Boolean, expirationTime: Long) {
        // Update SharedPreferences instead of old variables
        val sharedPreferences = requireContext().getSharedPreferences("prefs", Context.MODE_PRIVATE)
        sharedPreferences.edit()
            .putLong("expiration_time", expirationTime)
            .apply()

        if (isAdFree) {
            canSendMessage = true
        } else {
            loadRewardedAd()
        }

        if (expirationTime <= System.currentTimeMillis()) {
            canSendMessage = false
        }

        // Update the subscription status display and credit balance
        updateSubscriptionStatusDisplay()
        updateCreditBalanceDisplay()
    }

    private fun openDocumentPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                "application/pdf",
                "application/msword",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            ))
        }
        startActivityForResult(intent, PICK_DOCUMENT_REQUEST_CODE)
    }

    private fun checkDailyMessageLimit(): Boolean {
        val sharedPreferences = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastResetTime = sharedPreferences.getLong(LAST_RESET_TIME_KEY, 0)
        val currentTime = System.currentTimeMillis()

        if (currentTime - lastResetTime > TimeUnit.HOURS.toMillis(24)) {
            sharedPreferences.edit().putLong(LAST_RESET_TIME_KEY, currentTime)
                .putInt(MESSAGE_COUNT_KEY, 0).apply()
        }

        val messageCount = sharedPreferences.getInt(MESSAGE_COUNT_KEY, 0)
        return messageCount < DAILY_MESSAGE_LIMIT
    }

    private fun incrementMessageCount() {
        val sharedPreferences = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val messageCount = sharedPreferences.getInt(MESSAGE_COUNT_KEY, 0)
        sharedPreferences.edit().putInt(MESSAGE_COUNT_KEY, messageCount + 1).apply()
    }

    // In ChatFragment.kt

    private fun loadChatHistoryById(chatId: String) {
        val sharedPreferences = requireContext().getSharedPreferences("prefs", Context.MODE_PRIVATE)
        val savedChatsJson = sharedPreferences.getString(chatHistoryKey, "[]")
        val messagesToLoad = mutableListOf<ChatMessage>() // Local temporary list

        try {
            val savedChatsArray = JSONArray(savedChatsJson)
            var foundConversation = false
            for (i in 0 until savedChatsArray.length()) {
                val chatObject = savedChatsArray.getJSONObject(i)
                if (chatObject.getString("id") == chatId) {
                    this.conversationId = chatId // Update current conversation ID
                    val messagesArray = chatObject.getJSONArray("messages")
                    for (j in 0 until messagesArray.length()) {
                        // Use your parseChatMessageFromJson helper
                        messagesToLoad.add(parseChatMessageFromJson(messagesArray.getJSONObject(j)))
                    }
                    foundConversation = true
                    break
                }
            }
            if (!foundConversation) {
                showCustomToast("Chat not found.") // Or handle appropriately
            }
        } catch (e: JSONException) {
            Log.e("ChatFragment", "Error loading chat by ID", e)
            showCustomToast("Error loading chat.")
            // Optionally clear the adapter if loading fails critically
            // chatAdapter.submitList(emptyList())
            return // Exit if parsing fails
        }

        chatMessages.clear()
        chatMessages.addAll(messagesToLoad)
        chatAdapter.submitList(chatMessages.toList()) {
            if (chatMessages.isNotEmpty()) {
                binding.recyclerView.smoothScrollToPosition(chatMessages.size - 1)
            }
        }
        // Note: saveChatHistory() might be called if this implies the chat is now "active"
        // and further messages will be added to this loaded history.
    }
    private fun showChatOptionsDialog(chatId: String, chatTitle: String) {
        val options = arrayOf("View Chat", "Delete Chat")
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle(chatTitle)
        builder.setItems(options) { dialog, which ->
            when (which) {
                0 -> loadChatHistoryById(chatId)
                1 -> showDeleteConfirmationDialog(chatId)
            }
        }
        builder.setNegativeButton("Cancel", null)
        builder.show()
    }

    private fun showDeleteConfirmationDialog(chatId: String) {
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("Delete Chat")
        builder.setMessage("Are you sure you want to delete this chat?")
        builder.setPositiveButton("Yes") { dialog, which ->
            deleteChatHistoryById(chatId)
        }
        builder.setNegativeButton("No", null)
        builder.show()
    }

    private fun showDeleteAllConfirmationDialog() {
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("Delete All Chats")
        builder.setMessage("Are you sure you want to delete all chat history?")
        builder.setPositiveButton("Yes") { dialog, which ->
            deleteAllChatHistory()
        }
        builder.setNegativeButton("No", null)
        builder.show()
    }

    private fun deleteChatHistoryById(chatId: String) {
        val sharedPreferences = requireContext().getSharedPreferences("prefs", Context.MODE_PRIVATE)
        val savedChatsArray = JSONArray(sharedPreferences.getString(chatHistoryKey, "[]"))
        val updatedChatsArray = JSONArray()

        for (i in 0 until savedChatsArray.length()) {
            val chatObject = savedChatsArray.getJSONObject(i)
            if (chatObject.getString("id") != chatId) {
                updatedChatsArray.put(chatObject)
            }
        }

        sharedPreferences.edit().putString(chatHistoryKey, updatedChatsArray.toString()).apply()
        showCustomToast("Chat deleted successfully")
    }

    private fun deleteAllChatHistory() {
        val sharedPreferences = requireContext().getSharedPreferences("prefs", Context.MODE_PRIVATE)
        sharedPreferences.edit().putString(chatHistoryKey, "[]").apply()
        showCustomToast("All chat history deleted successfully")
    }

    private fun showChatGptOptionsDialog() {
        val subscriptionUIManager = SubscriptionUIManager(requireContext())
        showSubscriptionAwareModelDialog(subscriptionUIManager) { selectedModel ->
            handleModelSelection(selectedModel)
        }
    }

    private fun handleModelSelection(selectedModel: com.playstudio.aiteacher.pricing.AIModel) {
        // Update current model ID
        currentModel = selectedModel.modelId

        // Handle special model UI configurations
        when (selectedModel.modelId) {
            "gemini-2.5-flash" -> {
                if (selectedModel == com.playstudio.aiteacher.pricing.AIModel.GEMINI_VOICE) {
                    // This is Gemini Voice Chat
                    binding.openaiLiveAudioControls.visibility = View.GONE
                    hideStandardChatUI()
                    updateActiveModelButton("Gemini Voice")
                    showCustomToast("Switched to Gemini Voice Chat")
                } else {
                    // Regular Gemini text model
                    showStandardChatUI()
                    updateActiveModelButton(selectedModel.displayName)
                    showCustomToast("Switched to ${selectedModel.displayName}")
                }
            }
            "openai-realtime-voice" -> {
                binding.openaiLiveAudioControls.visibility = View.VISIBLE
                hideStandardChatUI()
                // DISABLED OLD IMPLEMENTATION - Using new RealtimeVoiceAgent instead
                // openAILiveAudioViewModel.stopSession()
                updateActiveModelButton("OpenAI Voice")
                showCustomToast("Switched to OpenAI Realtime Voice")
            }
            "gpt-4o-audio-preview" -> {
                // GPT-4o Audio model
                showStandardChatUI()
                updateActiveModelButton(selectedModel)  // Use the overloaded method that shows [AUDIO]
                showCustomToast("Switched to ${selectedModel.displayName} - Audio features enabled!")
                updateUIForCurrentModel()
                switchUiForModel(currentModel)
                // Enable audio mode when audio model is selected
                isAudioModeEnabled = true
            }
            "gpt-4o-mini-audio-preview" -> {
                // GPT-4o Mini Audio model
                showStandardChatUI()
                updateActiveModelButton(selectedModel)  // Use the overloaded method that shows [AUDIO]
                showCustomToast("Switched to ${selectedModel.displayName} - Audio features enabled!")
                updateUIForCurrentModel()
                switchUiForModel(currentModel)
                // Enable audio mode when audio model is selected
                isAudioModeEnabled = true
            }
            "dall-e-3" -> {
                showStandardChatUI()
                updateUIForCurrentModel()
                updateActiveModelButton(selectedModel.displayName)
                showCustomToast("Switched to ${selectedModel.displayName}")
            }
            else -> {
                showStandardChatUI()
                updateActiveModelButton(selectedModel.displayName)
                showCustomToast("Switched to ${selectedModel.displayName}")
                updateUIForCurrentModel()
                switchUiForModel(currentModel)
            }
        }
    }

    /**
     * Automatically selects the specified model by model ID
     * Used by auto-selection from shortcuts (e.g., AI Image Generator)
     */
    private fun setSelectedModel(modelId: String) {
        Log.d("ChatFragment", "Auto-selecting model: $modelId")
        
        // Find the corresponding AIModel enum from the model ID
        val aiModel = com.playstudio.aiteacher.pricing.AIModel.values().find { 
            it.modelId == modelId 
        }
        
        if (aiModel != null) {
            Log.d("ChatFragment", "Found AIModel: ${aiModel.displayName}")
            // Use the existing model selection logic
            handleModelSelection(aiModel)
            showCustomToast("Auto-selected ${aiModel.displayName} for image generation")
        } else {
            Log.w("ChatFragment", "Could not find AIModel for ID: $modelId")
            // Fallback to setting currentModel directly
            currentModel = modelId
            updateUIForCurrentModel()
            switchUiForModel(currentModel)
            showCustomToast("Selected model: $modelId")
        }
    }

    private fun hideStandardChatUI() {
        binding.messageInputLayout.visibility = View.GONE
        binding.scanTextButton.visibility = View.GONE
        binding.voiceInputButton.visibility = View.GONE
        binding.sendButton.visibility = View.GONE
        binding.followUpQuestionsContainer.visibility = View.GONE
        binding.generatedImageView.visibility = View.GONE
        binding.downloadButton.visibility = View.GONE
        binding.generatingText.visibility = View.GONE
    }

    private fun showStandardChatUI() {
        binding.openaiLiveAudioControls.visibility = View.GONE
        binding.messageInputLayout.visibility = View.VISIBLE
        binding.scanTextButton.visibility = View.VISIBLE
        binding.voiceInputButton.visibility = View.VISIBLE
        binding.sendButton.visibility = View.VISIBLE
    }

    // Usage tracking and cost management functionality
    private lateinit var usageTracker: com.playstudio.aiteacher.pricing.UsageTracker
        private lateinit var subscriptionUIManager: SubscriptionUIManager

    private fun initializeUsageTracking() {
        usageTracker = com.playstudio.aiteacher.pricing.UsageTracker(requireContext())
        subscriptionUIManager = SubscriptionUIManager(requireContext())
    }

    private fun updateSubscriptionStatusDisplay() {
        lifecycleScope.launch {
            try {
                // Use the new authentication and Firestore-based subscription system
                val subscriptionUIManager = SubscriptionUIManager(requireContext())
                val firebaseAuthService = com.playstudio.aiteacher.profile.FirebaseAuthenticationService(requireContext())

                val statusText: String
                val isSubscribed: Boolean

                if (!firebaseAuthService.isSignedIn()) {
                    // User not authenticated - show free plan with sign-in prompt
                    isSubscribed = false
                    statusText = "Free Plan - Sign in to upgrade"
                } else {
                    // Get subscription status from Firestore
                    val tier = subscriptionUIManager.getUserSubscriptionTier()
                    isSubscribed = tier != com.playstudio.aiteacher.pricing.SubscriptionTier.FREE

                    statusText = when (tier) {
                        com.playstudio.aiteacher.pricing.SubscriptionTier.BASIC -> "Essential Plan"
                        com.playstudio.aiteacher.pricing.SubscriptionTier.PRO -> "Professional Plan"
                        com.playstudio.aiteacher.pricing.SubscriptionTier.PREMIUM -> "Premium Plan"
                        com.playstudio.aiteacher.pricing.SubscriptionTier.ENTERPRISE -> "Enterprise Max"
                        else -> "Free Plan"
                    }
                }

                // Update the cached SharedPreferences for backward compatibility
                val sharedPreferences = requireContext().getSharedPreferences("prefs", Context.MODE_PRIVATE)
                sharedPreferences.edit().putBoolean("current_subscription_active", isSubscribed).apply()

                binding.subscriptionStatusText.text = statusText

                // Update color based on subscription status
                val textColor = if (isSubscribed) {
                    android.R.color.holo_green_light
                } else {
                    android.R.color.holo_red_light
                }
                binding.subscriptionStatusText.setTextColor(ContextCompat.getColor(requireContext(), textColor))

                // Set up click listener based on authentication status
                binding.subscriptionStatusText.setOnClickListener {
                    if (!firebaseAuthService.isSignedIn()) {
                        // Redirect to authentication if not signed in
                        val intent = Intent(requireContext(), com.playstudio.aiteacher.profile.ProfileActivity::class.java)
                        intent.putExtra("show_login", true)
                        startActivity(intent)
                    } else {
                        // Open subscription activity if already authenticated
                        openSubscriptionActivity()
                    }
                }

            } catch (e: Exception) {
                Log.e("ChatFragment", "Error updating subscription status display", e)

                // Fallback to SharedPreferences method if Firestore fails
                try {
                    val sharedPreferences = requireContext().getSharedPreferences("prefs", Context.MODE_PRIVATE)
                    val subscriptionType = sharedPreferences.getString("subscription_type", null)
                    val expirationTime = sharedPreferences.getLong("expiration_time", 0)
                    val currentTime = System.currentTimeMillis()

                    val statusText = if (currentTime < expirationTime) {
                        when (subscriptionType) {
                            "basic" -> "Essential Plan"
                            "pro" -> "Professional Plan"
                            "premium" -> "Premium Plan"
                            "ultra_premium" -> "Enterprise Max"
                            else -> "Free Plan"
                        }
                    } else {
                        "Free Plan"
                    }

                    binding.subscriptionStatusText.text = statusText
                } catch (fallbackError: Exception) {
                    Log.e("ChatFragment", "Fallback subscription check also failed", fallbackError)
                    binding.subscriptionStatusText.text = "Free Plan"
                }
            }
        }
    }

    private fun trackMessageUsage(model: com.playstudio.aiteacher.pricing.AIModel, inputTokens: Int, outputTokens: Int) {
        lifecycleScope.launch {
            try {
                // Increment usage count
                usageTracker.incrementUsage(model.modelId)

                // Update UI with remaining usage and token balance
                updateUsageDisplay(model)
                updateCreditBalanceDisplay()

                // Also deduct from the unified token pool so all models share one pool
                val tier = subscriptionUIManager.getUserSubscriptionTier()
                val tokenPoolIntegration = com.playstudio.aiteacher.credits.TokenPoolIntegration.getInstance(requireContext())
                val poolTier = tier.toTokenPoolTier()
                val responseType = when {
                    model.modelId.contains("dall-e", ignoreCase = true) ||
                    model.modelId.contains("gpt-image-1", ignoreCase = true) ->
                        com.playstudio.aiteacher.credits.TokenPoolManager.ResponseType.IMAGE
                    model.modelId.contains("realtime", ignoreCase = true) ||
                    model.modelId.contains("openai-realtime-voice", ignoreCase = true) ->
                        com.playstudio.aiteacher.credits.TokenPoolManager.ResponseType.REALTIME
                    model.modelId.contains("audio", ignoreCase = true) ->
                        com.playstudio.aiteacher.credits.TokenPoolManager.ResponseType.AUDIO
                    else -> com.playstudio.aiteacher.credits.TokenPoolManager.ResponseType.TEXT
                }
                val responseLengthEstimate = if (responseType == com.playstudio.aiteacher.credits.TokenPoolManager.ResponseType.TEXT) {
                    (outputTokens * 4).coerceAtLeast(0)
                } else 0
                tokenPoolIntegration.processAIResponseWithLength(
                    modelName = model.modelId,
                    responseType = responseType,
                    inputTokens = inputTokens,
                    outputTokens = outputTokens,
                    responseLength = responseLengthEstimate,
                    agentName = "chat-fragment",
                    userId = "default_user",
                    userTier = poolTier
                )



            } catch (e: Exception) {
                Log.e("ChatFragment", "Error tracking usage", e)
            }
        }
    }

    private fun updateUsageDisplay(model: com.playstudio.aiteacher.pricing.AIModel) {
        lifecycleScope.launch {
            try {
                val userTier = subscriptionUIManager.getUserSubscriptionTier()
                val remainingUsage = usageTracker.getRemainingUsage(model.modelId, userTier)
                val usageLimit = model.getUsageLimitForTier(userTier)

                val usageText = if (usageLimit == -1) {
                    "Unlimited"
                } else {
                    "$remainingUsage/$usageLimit remaining today"
                }

                // Update any UI elements that show usage info
                // This could be a toast, status bar, or dedicated usage indicator
                if (remainingUsage <= 3 && usageLimit != -1) {
                    showCustomToast("⚠️ Low usage: $usageText")
                }

            } catch (e: Exception) {
                Log.e("ChatFragment", "Error updating usage display", e)
            }
        }
    }

    private fun updateCreditBalanceDisplay() {
        lifecycleScope.launch {
            try {
                val tier = subscriptionUIManager.getUserSubscriptionTier()
                val poolTier = tier.toTokenPoolTier()
                val tokenPool = com.playstudio.aiteacher.credits.TokenPoolManager.getInstance(requireContext())
                val remainingTokens = tokenPool.getRemainingDailyTokens("default_user", poolTier).toInt()
                val dailyAllocation = poolTier.tokenAllocation.toInt()
                binding.tvCreditBalance.text = "Tokens: $remainingTokens / $dailyAllocation"
            } catch (e: Exception) {
                Log.e("ChatFragment", "Error updating credit balance", e)
            }
        }
    }

    private suspend fun checkUsageBeforeMessage(model: com.playstudio.aiteacher.pricing.AIModel): Boolean {
        return try {
            Log.d("ChatFragment", "Checking usage for model: ${model.modelId}")

            // Use the same subscription checking logic as the rest of the app
            val isSubscribed = isUserCurrentlySubscribed()
            Log.d("ChatFragment", "User subscribed: $isSubscribed")

            // If user is subscribed, allow unlimited usage
            if (isSubscribed) {
                Log.d("ChatFragment", "User is subscribed, allowing unlimited usage")
                return true
            }

            // For free users, check token pool daily tokens instead of per-model usage limits
            val userTier = com.playstudio.aiteacher.pricing.SubscriptionTier.FREE
            val poolTier = userTier.toTokenPoolTier()
            val tokenPool = com.playstudio.aiteacher.credits.TokenPoolManager.getInstance(requireContext())
            val remainingTokens = tokenPool.getRemainingDailyTokens("default_user", poolTier)
            if (remainingTokens <= 0.0) {
                withContext(Dispatchers.Main) { showUsageLimitDialog(model) }
                return false
            }

            Log.d("ChatFragment", "Usage check passed, allowing message")
            true
        } catch (e: Exception) {
            Log.e("ChatFragment", "Error checking usage", e)
            // On error, show user-friendly message and allow them to try
            withContext(Dispatchers.Main) {
                showCustomToast("Unable to check usage limits. Please try again.")
            }
            false
        }
    }

    private fun showUsageLimitDialog(model: com.playstudio.aiteacher.pricing.AIModel) {
        AlertDialog.Builder(requireContext())
            .setTitle("Usage Limit Reached")
            .setMessage("You've reached your daily limit for ${model.displayName}. Upgrade your plan for more usage.")
            .setPositiveButton("Upgrade Plan") { _, _ ->
                openSubscriptionActivity()
            }
            .setNegativeButton("Select Different Model") { _, _ ->
                showChatGptOptionsDialog()
            }
            .setNeutralButton("View Usage") { _, _ ->
                openUsageDashboard()
            }
            .show()
    }

    private fun openSubscriptionActivity() {
        try {
            val intent = Intent(requireContext(), com.playstudio.aiteacher.profile.SubscriptionActivity::class.java)
            startActivity(intent)
        } catch (e: Exception) {
            Log.e("ChatFragment", "Error opening subscription activity", e)
            showCustomToast("Subscription settings not available")
        }
    }

    private fun openUsageDashboard() {
        try {
            val intent = Intent(requireContext(), UsageDashboardActivity::class.java)
            startActivity(intent)
        } catch (e: Exception) {
            Log.e("ChatFragment", "Error opening usage dashboard", e)
            showCustomToast("Usage dashboard not available")
        }
    }

    // Smart model selection based on usage and cost
    private suspend fun suggestOptimalModel(): com.playstudio.aiteacher.pricing.AIModel? {
        return try {
            val userTier = subscriptionUIManager.getUserSubscriptionTier()
            val smartUpgradeManager = com.playstudio.aiteacher.pricing.SmartUpgradeManager(requireContext())

            // Get recommendation based on usage patterns
            val recommendation = smartUpgradeManager.getUpgradeRecommendation(userTier)

            // Find the best available model for current tier
            com.playstudio.aiteacher.pricing.AIModel.getBestModelForTier(userTier)

        } catch (e: Exception) {
            Log.e("ChatFragment", "Error getting optimal model", e)
            null
        }
    }

    private fun showModelRecommendationDialog() {
        lifecycleScope.launch {
            try {
                val optimalModel = suggestOptimalModel()
                if (optimalModel != null && optimalModel.modelId != currentModel) {
                    withContext(Dispatchers.Main) {
                        AlertDialog.Builder(requireContext())
                            .setTitle("Model Recommendation")
                            .setMessage("Based on your usage, we recommend switching to ${optimalModel.displayName} for better value.")
                            .setPositiveButton("Switch") { _, _ ->
                                handleModelSelection(optimalModel)
                            }
                            .setNegativeButton("Keep Current", null)
                            .show()
                    }
                }
            } catch (e: Exception) {
                Log.e("ChatFragment", "Error showing model recommendation", e)
            }
        }
    }

    // Advanced UI enhancements and user experience improvements
    private fun setupAdvancedUIFeatures() {
        // Model-specific UI optimizations
        setupModelSpecificFeatures()

        // Cost-aware UI elements
        setupCostAwareUIElements()

        // Usage progress indicators
        setupUsageProgressIndicators()

        // Smart suggestions
        setupSmartSuggestions()
    }

    private fun setupModelSpecificFeatures() {
        lifecycleScope.launch {
            try {
                val currentAIModel = com.playstudio.aiteacher.pricing.AIModel.fromModelId(currentModel)
                if (currentAIModel != null) {
                    // Configure UI based on model capabilities
                    when (currentAIModel.provider) {
                        "OpenAI" -> setupOpenAISpecificFeatures(currentAIModel)
                        "Anthropic" -> setupAnthropicSpecificFeatures(currentAIModel)
                        "Google" -> setupGoogleSpecificFeatures(currentAIModel)
                        "DeepSeek" -> setupDeepSeekSpecificFeatures(currentAIModel)
                    }

                    // Update UI elements based on model costs
                    updateCostIndicators(currentAIModel)
                }
            } catch (e: Exception) {
                Log.e("ChatFragment", "Error setting up model-specific features", e)
            }
        }
    }

    private fun setupOpenAISpecificFeatures(model: com.playstudio.aiteacher.pricing.AIModel) {
        // Enable specific OpenAI features
        when (model.modelId) {
            "gpt-4o" -> {
                // Enable advanced reasoning UI hints
                showModelCapabilityHints("🧠 Advanced reasoning mode active")
            }
            "gpt-4o-search-preview" -> {
                // Enable web search indicators
                showModelCapabilityHints("🔍 Web search capabilities enabled")
            }
            "dall-e-3" -> {
                // Enable image generation UI
                showModelCapabilityHints("🎨 Image generation mode active")
            }
            "o1", "o1-mini", "o3", "o3-mini" -> {
                // Enable reasoning model UI
                showModelCapabilityHints("🔬 Reasoning model - longer processing time")
            }
        }
    }

    private fun setupAnthropicSpecificFeatures(model: com.playstudio.aiteacher.pricing.AIModel) {
        when {
            model.modelId.contains("claude-sonnet") -> {
                showModelCapabilityHints("📝 Balanced reasoning and speed")
            }
            model.modelId.contains("claude-opus") -> {
                showModelCapabilityHints("🎭 Maximum capability mode")
            }
        }
    }

    private fun setupGoogleSpecificFeatures(model: com.playstudio.aiteacher.pricing.AIModel) {
        when {
            model.modelId.contains("gemini") && model.displayName.contains("Voice") -> {
                showModelCapabilityHints("🎙️ Voice conversation mode")
            }
            model.modelId.contains("gemini-2.5-flash") -> {
                showModelCapabilityHints("High-speed responses")
            }
        }
    }

    private fun setupDeepSeekSpecificFeatures(model: com.playstudio.aiteacher.pricing.AIModel) {
        showModelCapabilityHints("🧮 Optimized for coding and math")
    }

    private fun showModelCapabilityHints(hint: String) {
        // Show capability hints in a subtle way
        binding.root.post {
            try {
                showCustomToast(hint)
            } catch (e: Exception) {
                Log.e("ChatFragment", "Error showing capability hint", e)
            }
        }
    }

    private fun setupCostAwareUIElements() {
        lifecycleScope.launch {
            try {
                val currentAIModel = com.playstudio.aiteacher.pricing.AIModel.fromModelId(currentModel)
                if (currentAIModel != null) {
                    val messageCost = currentAIModel.calculateMessageCost()

                    // Show cost indicators for expensive models
                    if (messageCost > 0.01) { // More than 1 cent per message
                        showCostWarning(currentAIModel, messageCost)
                    }

                    // Update UI with cost-efficient suggestions
                    suggestCostOptimizations(currentAIModel)
                }
            } catch (e: Exception) {
                Log.e("ChatFragment", "Error setting up cost-aware UI", e)
            }
        }
    }

    private fun showCostWarning(model: com.playstudio.aiteacher.pricing.AIModel, cost: Double) {
        val costFormatted = String.format("%.3f", cost)
        val warningMessage = "💰 ${model.displayName} costs ~$${costFormatted} per message"

        // Show cost warning for expensive models
        if (cost > 0.05) { // More than 5 cents
            AlertDialog.Builder(requireContext())
                .setTitle("High Cost Model")
                .setMessage("$warningMessage\n\nConsider using a more cost-effective model for general conversations.")
                .setPositiveButton("Continue") { _, _ -> }
                .setNegativeButton("Switch Model") { _, _ ->
                    showChatGptOptionsDialog()
                }
                .setNeutralButton("View Cheaper Options") { _, _ ->
                    showCostOptimizedModels()
                }
                .show()
        }
    }

    private fun showCostOptimizedModels() {
        lifecycleScope.launch {
            try {
                val userTier = subscriptionUIManager.getUserSubscriptionTier()
                val cheapestModel = com.playstudio.aiteacher.pricing.AIModel.getCheapestModelForTier(userTier)

                if (cheapestModel != null) {
                    val cheapestCost = cheapestModel.calculateMessageCost()
                    val currentAIModel = com.playstudio.aiteacher.pricing.AIModel.fromModelId(currentModel)
                    val currentCost = currentAIModel?.calculateMessageCost() ?: 0.0

                    val savings = ((currentCost - cheapestCost) / currentCost * 100).toInt()

                    withContext(Dispatchers.Main) {
                        AlertDialog.Builder(requireContext())
                            .setTitle("Cost Optimization")
                            .setMessage("Switch to ${cheapestModel.displayName} to save up to $savings% on costs while maintaining great quality.")
                            .setPositiveButton("Switch") { _, _ ->
                                handleModelSelection(cheapestModel)
                            }
                            .setNegativeButton("Keep Current", null)
                            .show()
                    }
                }
            } catch (e: Exception) {
                Log.e("ChatFragment", "Error showing cost-optimized models", e)
            }
        }
    }

    private fun suggestCostOptimizations(model: com.playstudio.aiteacher.pricing.AIModel) {
        // Suggest optimizations based on usage patterns
        lifecycleScope.launch {
            try {
                val userTier = subscriptionUIManager.getUserSubscriptionTier()
                val usageSummary = usageTracker.getUsageSummary(userTier)
                val dailyUsage = usageSummary.values.sumOf { it.currentUsage }

                if (dailyUsage > 10 && model.calculateMessageCost() > 0.02) {
                    // High usage with expensive model - suggest optimization
                    val cheaperAlternatives = com.playstudio.aiteacher.pricing.AIModel.getAllModels()
                        .filter { it.getUsageLimitForTier(userTier) > 0 || it.getUsageLimitForTier(userTier) == -1 }
                        .filter { it.calculateMessageCost() < model.calculateMessageCost() }
                        .sortedBy { it.calculateMessageCost() }
                        .take(3)

                    if (cheaperAlternatives.isNotEmpty()) {
                        withContext(Dispatchers.Main) {
                            showCostOptimizationSuggestion(model, cheaperAlternatives)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("ChatFragment", "Error suggesting cost optimizations", e)
            }
        }
    }

    private fun showCostOptimizationSuggestion(
        currentModel: com.playstudio.aiteacher.pricing.AIModel,
        alternatives: List<com.playstudio.aiteacher.pricing.AIModel>
    ) {
        val alternativeNames = alternatives.joinToString(", ") { it.displayName }

        AlertDialog.Builder(requireContext())
            .setTitle("💡 Cost Optimization")
            .setMessage("You're using ${currentModel.displayName} frequently. Consider these cost-effective alternatives: $alternativeNames")
            .setPositiveButton("Show Options") { _, _ ->
                showChatGptOptionsDialog()
            }
            .setNegativeButton("Keep Current", null)
            .setNeutralButton("Don't Show Again") { _, _ ->
                // Save preference to not show cost suggestions
                saveCostSuggestionPreference(false)
            }
            .show()
    }

    private fun saveCostSuggestionPreference(showSuggestions: Boolean) {
        val prefs = requireContext().getSharedPreferences("ai_cost_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("show_cost_suggestions", showSuggestions).apply()
    }

    private fun shouldShowCostSuggestions(): Boolean {
        val prefs = requireContext().getSharedPreferences("ai_cost_prefs", Context.MODE_PRIVATE)
        return prefs.getBoolean("show_cost_suggestions", true)
    }

    private fun setupUsageProgressIndicators() {
        lifecycleScope.launch {
            try {
                val currentAIModel = com.playstudio.aiteacher.pricing.AIModel.fromModelId(currentModel)
                if (currentAIModel != null) {
                    val userTier = subscriptionUIManager.getUserSubscriptionTier()
                    val usageLimit = currentAIModel.getUsageLimitForTier(userTier)
                    val currentUsage = usageTracker.getCurrentUsage(currentAIModel.modelId)

                    if (usageLimit > 0) {
                        val usagePercentage = (currentUsage.toFloat() / usageLimit * 100).toInt()

                        withContext(Dispatchers.Main) {
                            updateUsageProgressUI(usagePercentage, currentUsage, usageLimit, currentAIModel.displayName)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("ChatFragment", "Error setting up usage progress indicators", e)
            }
        }
    }

    private fun updateUsageProgressUI(percentage: Int, current: Int, limit: Int, modelName: String) {
        // Update any progress indicators in the UI
        val usageText = "$current/$limit messages used today"

        when {
            percentage >= 90 -> {
                showCustomToast("⚠️ $modelName: $usageText (${100-percentage}% remaining)")
            }
            percentage >= 75 -> {
                // Could update a progress bar or indicator here
                Log.d("ChatFragment", "Usage at $percentage% for $modelName")
            }
        }
    }

    private fun setupSmartSuggestions() {
        // Set up intelligent suggestions based on user behavior
        lifecycleScope.launch {
            try {
                val smartUpgradeManager = com.playstudio.aiteacher.pricing.SmartUpgradeManager(requireContext())
                val userTier = subscriptionUIManager.getUserSubscriptionTier()

                // Get smart recommendations
                val recommendation = smartUpgradeManager.getUpgradeRecommendation(userTier)

                // Show smart suggestions if appropriate
                if (shouldShowSmartSuggestions()) {
                    withContext(Dispatchers.Main) {
                        showSmartSuggestionIfRelevant(recommendation)
                    }
                }
            } catch (e: Exception) {
                Log.e("ChatFragment", "Error setting up smart suggestions", e)
            }
        }
    }

    private fun shouldShowSmartSuggestions(): Boolean {
        val prefs = requireContext().getSharedPreferences("ai_suggestions_prefs", Context.MODE_PRIVATE)
        val lastShown = prefs.getLong("last_suggestion_time", 0)
        val now = System.currentTimeMillis()

        // Show suggestions at most once per day
        return (now - lastShown) > 24 * 60 * 60 * 1000
    }

    private fun showSmartSuggestionIfRelevant(recommendation: com.playstudio.aiteacher.pricing.SmartUpgradeRecommendation?) {
        if (recommendation != null && recommendation.urgency != com.playstudio.aiteacher.pricing.SmartUpgradeUrgency.OPTIONAL) {
            val smartUpgradeManager = com.playstudio.aiteacher.pricing.SmartUpgradeManager(requireContext())
            val message = smartUpgradeManager.getUpgradeMessage(recommendation)

            AlertDialog.Builder(requireContext())
                .setTitle("💡 Smart Suggestion")
                .setMessage(message)
                .setPositiveButton("Learn More") { _, _ ->
                    openSubscriptionActivity()
                }
                .setNegativeButton("Maybe Later", null)
                .setNeutralButton("Don't Show Again") { _, _ ->
                    disableSmartSuggestions()
                }
                .show()

            // Update last shown time
            val prefs = requireContext().getSharedPreferences("ai_suggestions_prefs", Context.MODE_PRIVATE)
            prefs.edit().putLong("last_suggestion_time", System.currentTimeMillis()).apply()
        }
    }

    private fun disableSmartSuggestions() {
        val prefs = requireContext().getSharedPreferences("ai_suggestions_prefs", Context.MODE_PRIVATE)
        prefs.edit().putLong("last_suggestion_time", Long.MAX_VALUE).apply()
    }

    private fun updateCostIndicators(model: com.playstudio.aiteacher.pricing.AIModel) {
        // Update any cost indicators in the UI
        val costPerMessage = model.calculateMessageCost()
        val costText = if (costPerMessage < 0.001) {
            "~Free"
        } else {
            "~$${String.format("%.3f", costPerMessage)}/msg"
        }

        // This could update a status indicator or tooltip
        Log.d("ChatFragment", "Cost indicator for ${model.displayName}: $costText")
    }







    private fun handleGeminiCompletion(message: String) {
        val geminiApiKey = getApiKey("google")
        if (geminiApiKey.isNullOrEmpty()) {
            Log.e("ChatFragment", "❌ No Google API key available for Gemini")
            requireActivity().runOnUiThread {
                showCustomToast("Google API key not available")
                removeTypingIndicator()
            }
            return
        }
        val geminiUrl = "https://generativelanguage.googleapis.com/v1beta/models/${currentModel}:generateContent"

        // 1. Define 'contentsArray' (for message history)
        val contentsArray = JSONArray().apply {
            chatMessages.filterNot { it.isTyping }.forEach { chatMsg ->
                put(JSONObject().apply {
                    put("role", if (chatMsg.isUser) "user" else "model")
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", chatMsg.content)
                        })
                    })
                })
            }
            put(JSONObject().apply {
                put("role", "user")
                put("parts", JSONArray().apply {
                    put(JSONObject().apply {
                        put("text", message)
                    })
                })
            })
        }

        // 2. Define 'json' (the main request body JSON object)
        val json = JSONObject().apply { // <<<< DEFINITION OF 'json' WAS MISSING IN PREVIOUS SNIPPET
            put("contents", contentsArray)
            // You can add generationConfig here if needed by Gemini API
            // put("generationConfig", JSONObject().apply {
            //     put("temperature", 0.7)
            //     put("maxOutputTokens", 2048)
            // })
        }

        // 3. Define 'body' and 'request'
        val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
        val request = Request.Builder() // <<<< DEFINITION OF 'request' WAS MISSING IN PREVIOUS SNIPPET
            .url("$geminiUrl?key=$geminiApiKey")
            .post(body)
            .addHeader("Content-Type", "application/json")
            .build()

        Log.d("ChatFragment", "Sending request to Gemini: $json") // Now 'json' is defined
        showTypingIndicator()

        lifecycleScope.launch { // This is the coroutine body
            try {
                // 4. Make the network call and define 'response'
                val response: Response = withContext(Dispatchers.IO) { // <<<< 'response' DEFINITION
                    client.newCall(request).execute() // 'request' is now defined
                }

                // 5. Get 'responseBody' (this can be nullable)
                val responseBody = response.body?.string() // This can be done outside withContext if preferred
                Log.d("ChatFragment", "Received response from Gemini: $responseBody")

                if (!response.isSuccessful) { // Now 'response' is defined
                    withContext(Dispatchers.Main) { // Switch to Main for UI
                        when (response.code) {
                            400 -> showCustomToast("Bad Request: Check your request parameters")
                            401 -> showCustomToast("Unauthorized: Check your API key")
                            403 -> showCustomToast("Forbidden: You don't have permission to access this resource")
                            500 -> showCustomToast("Server Error: Try again later")
                            else -> showCustomToast("Unexpected response from Gemini API: ${response.code}")
                        }
                        removeTypingIndicator()
                    }
                    return@launch
                }

                // 6. Process 'responseBody'
                // The 'it' in responseBody?.let { it -> ... } refers to the non-null responseBody string.
                responseBody?.let { rb -> // Explicitly naming 'it' to 'rb' (response body string)
                    try {
                        val jsonResponse = JSONObject(rb) // Use 'rb' here
                        Log.d("ChatFragment", "Parsed JSON response from Gemini: $jsonResponse")

                        if (jsonResponse.has("candidates")) {
                            val candidates = jsonResponse.getJSONArray("candidates")
                            if (candidates.length() > 0) {
                                val reply = candidates.getJSONObject(0)
                                    .getJSONObject("content")
                                    .getJSONArray("parts")
                                    .getJSONObject(0)
                                    .getString("text")
                                    .trim()

                                // Extract token usage for tracking (Gemini response format)
                                var inputTokens = 0
                                var outputTokens = 0
                                if (jsonResponse.has("usageMetadata")) {
                                    val usage = jsonResponse.getJSONObject("usageMetadata")
                                    inputTokens = usage.optInt("promptTokenCount", 0)
                                    outputTokens = usage.optInt("candidatesTokenCount", 0)
                                }

                                withContext(Dispatchers.Main) { // Switch to Main for UI updates
                                    removeTypingIndicator()
                                    addMessageToChat(
                                        messageContent = reply,
                                        isUser = false,
                                        containsRichContent = determineIfRichContent(reply)
                                    )

                                    // Track usage after successful response
                                    val currentAIModel = com.playstudio.aiteacher.pricing.AIModel.fromModelId("gemini")
                                    if (currentAIModel != null) {
                                        trackMessageUsage(currentAIModel, inputTokens, outputTokens)
                                    }

                                    // If generateFollowUpQuestions is a suspend function, it needs to be called
                                    // from a coroutine scope or be launched in its own.
                                    // For now, assuming it's not a suspend function or handles its own scope.
                                    generateFollowUpQuestions(reply)
                                    // TTS is now automatically handled by addMessageToChat() when audio mode is enabled
                                    incrementInteractionCount()
                                }
                            } else {
                                withContext(Dispatchers.Main) {
                                    showCustomToast("No candidates found in Gemini response")
                                    removeTypingIndicator()
                                }
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                showCustomToast("No 'candidates' field in Gemini response")
                                removeTypingIndicator()
                            }
                        }
                    } catch (e: JSONException) {
                        Log.e("ChatFragment", "Failed to parse Gemini response", e)
                        withContext(Dispatchers.Main) {
                            showCustomToast("Failed to parse Gemini response")
                            removeTypingIndicator()
                        }
                    }
                } ?: withContext(Dispatchers.Main) { // If responseBody was null
                    Log.w("ChatFragment", "Gemini response body was null")
                    removeTypingIndicator()
                    showCustomToast("Received empty response from Gemini")
                }
            } catch (e: IOException) {
                Log.e("ChatFragment", "Failed to get Gemini response", e)
                withContext(Dispatchers.Main) {
                    showCustomToast("Network error with Gemini: ${e.message}")
                    removeTypingIndicator()
                }
            }
        }
    }

    /**
     * Handle Veo video generation using Google's video generation API
     * Supports async operation polling for video generation completion
     * 
     * STATUS: Veo 3 API is not yet publicly available (404 errors)
     * Currently shows a preview demo. Will switch to real API when available.
     * 
     * TODO: Monitor Google's Veo API availability and switch to real implementation
     */
    private fun handleVeoVideoGeneration(prompt: String, inputImageBase64: String? = null, aspectRatio: String = "16:9") {
        if (!isNetworkAvailable()) {
            showCustomToast("No internet connection. Please check your network settings.")
            return
        }

        // Show generating status
        binding.imageContainer.visibility = View.VISIBLE
        binding.generatingText.visibility = View.VISIBLE
        binding.generatingText.text = "Generating video..."
        binding.generatedImageView.visibility = View.GONE
        binding.downloadButton.visibility = View.GONE

        // Start generating animation
        startGeneratingAnimation()
        
        Log.d("ChatFragment", "🎬 Veo video generation started with prompt: '$prompt'")
        
        // TEMPORARY: Show demo for now since Veo API isn't publicly available yet
        if (prompt.length > 10) { // Only show demo for substantial prompts
            showVeoPreviewDemo(prompt)
            return
        }

        // Build request for Veo using Google's API format
        val requestJson = JSONObject().apply {
            put("prompt", prompt)
            
            // Add optional parameters
            if (aspectRatio != "16:9") {
                put("config", JSONObject().apply {
                    put("aspectRatio", aspectRatio)
                    put("personGeneration", "allow_all") // Allow people in videos
                })
            } else {
                put("config", JSONObject().apply {
                    put("personGeneration", "allow_all") // Allow people in videos
                })
            }
            
            // Add input image if provided (image-to-video capability)
            inputImageBase64?.let { imageData ->
                put("image", JSONObject().apply {
                    put("data", imageData)
                    put("mimeType", "image/jpeg")
                })
            }
        }

        val googleApiKey = getApiKey("google")
        if (googleApiKey.isNullOrEmpty()) {
            Log.e("ChatFragment", "❌ No Google API key available for Veo video generation")
            requireActivity().runOnUiThread {
                showCustomToast("Google API key not available")
                removeTypingIndicator()
            }
            return
        }
        val url = "https://generativelanguage.googleapis.com/v1beta/models/${currentModel}:generateVideos?key=$googleApiKey"
        Log.d("ChatFragment", "🎬 Veo API URL: $url")
        Log.d("ChatFragment", "🎬 Veo request body: ${requestJson.toString(2)}")
        
        val body = requestJson.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
        val request = Request.Builder()
            .url(url)
            .post(body)
            .addHeader("Content-Type", "application/json")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("ChatFragment", "Failed to start Veo video generation", e)
                requireActivity().runOnUiThread {
                    showCustomToast("Failed to start video generation. Please try again.")
                    binding.imageContainer.visibility = View.GONE
                    stopGeneratingAnimation()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                
                if (!response.isSuccessful) {
                    Log.e("ChatFragment", "Veo request failed: ${response.code} - ${response.message}")
                    Log.e("ChatFragment", "Veo error body: $responseBody")
                    Log.e("ChatFragment", "Veo request URL was: $url")
                    
                    requireActivity().runOnUiThread {
                        val errorMessage = when (response.code) {
                            404 -> "🎬 Veo video generation is not yet available. This feature is in preview and may require special access from Google. Please try again later or use image generation instead."
                            403 -> "Access denied. Veo may require special API access or billing enabled in Google Cloud Console."
                            429 -> "Rate limit exceeded. Please try again later."
                            400 -> "Invalid request format. Please check the prompt."
                            else -> "Video generation failed: ${response.code} ${response.message}"
                        }
                        showCustomToast(errorMessage)
                        binding.imageContainer.visibility = View.GONE
                        stopGeneratingAnimation()
                    }
                    return
                }
                
                if (responseBody == null) {
                    Log.e("ChatFragment", "Veo response body is null")
                    requireActivity().runOnUiThread {
                        showCustomToast("Video generation failed: empty response")
                        binding.imageContainer.visibility = View.GONE
                        stopGeneratingAnimation()
                    }
                    return
                }

                try {
                    val responseData = JSONObject(responseBody)
                    Log.d("ChatFragment", "Veo operation started: $responseData")

                    // Extract operation name for polling
                    val operationName = responseData.optString("name")
                    if (operationName.isNotEmpty()) {
                        // Start polling for completion
                        pollVeoOperation(operationName)
                    } else {
                        requireActivity().runOnUiThread {
                            showCustomToast("Failed to start video generation operation")
                            binding.imageContainer.visibility = View.GONE
                            stopGeneratingAnimation()
                        }
                    }
                } catch (e: Exception) {
                    Log.e("ChatFragment", "Error parsing Veo response", e)
                    requireActivity().runOnUiThread {
                        showCustomToast("Error processing video generation response")
                        binding.imageContainer.visibility = View.GONE
                        stopGeneratingAnimation()
                    }
                }
            }
        })
    }

    /**
     * Poll Veo operation status until video generation is complete
     */
    private fun pollVeoOperation(operationName: String, maxAttempts: Int = 60) {
        var attempts = 0
        
        val pollHandler = Handler(Looper.getMainLooper())
        
        fun pollOnce() {
            attempts++
            
            if (attempts > maxAttempts) {
                Log.w("ChatFragment", "Veo polling timeout after $maxAttempts attempts")
                showCustomToast("Video generation timeout. Please try again.")
                binding.imageContainer.visibility = View.GONE
                stopGeneratingAnimation()
                return
            }

            // Update status text with attempt count
            binding.generatingText.text = "Generating video... (${attempts * 10}s)"

            val googleApiKey = getApiKey("google")
            if (googleApiKey.isNullOrEmpty()) {
                Log.e("ChatFragment", "❌ No Google API key available for Veo polling")
                requireActivity().runOnUiThread {
                    showCustomToast("Google API key not available")
                    stopGeneratingAnimation()
                }
                return
            }
            
            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/$operationName?key=$googleApiKey")
                .get()
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.e("ChatFragment", "Failed to poll Veo operation", e)
                    pollHandler.postDelayed({ pollOnce() }, 10000) // Retry in 10 seconds
                }

                override fun onResponse(call: Call, response: Response) {
                    if (!response.isSuccessful) {
                        Log.e("ChatFragment", "Veo polling failed: ${response.code}")
                        pollHandler.postDelayed({ pollOnce() }, 10000)
                        return
                    }

                    try {
                        val operationData = JSONObject(response.body!!.string())
                        val done = operationData.optBoolean("done", false)

                        if (done) {
                            // Video generation completed
                            val responseObj = operationData.optJSONObject("response")
                            val generatedVideos = responseObj?.optJSONArray("generatedVideos")
                            
                            if (generatedVideos != null && generatedVideos.length() > 0) {
                                val firstVideo = generatedVideos.getJSONObject(0)
                                val videoFile = firstVideo.optJSONObject("video")
                                val videoUri = videoFile?.optString("uri")
                                
                                if (videoUri != null) {
                                    requireActivity().runOnUiThread {
                                        displayGeneratedVideo(videoUri)
                                    }
                                } else {
                                    requireActivity().runOnUiThread {
                                        showCustomToast("Video generation completed but no video found")
                                        binding.imageContainer.visibility = View.GONE
                                        stopGeneratingAnimation()
                                    }
                                }
                            } else {
                                requireActivity().runOnUiThread {
                                    showCustomToast("Video generation completed but no videos returned")
                                    binding.imageContainer.visibility = View.GONE
                                    stopGeneratingAnimation()
                                }
                            }
                        } else {
                            // Continue polling
                            pollHandler.postDelayed({ pollOnce() }, 10000) // Poll every 10 seconds
                        }
                    } catch (e: Exception) {
                        Log.e("ChatFragment", "Error parsing Veo operation response", e)
                        pollHandler.postDelayed({ pollOnce() }, 10000)
                    }
                }
            })
        }
        
        // Start polling
        pollHandler.postDelayed({ pollOnce() }, 10000) // First poll after 10 seconds
    }

    /**
     * Display generated video and setup download functionality
     */
    private fun displayGeneratedVideo(videoUri: String) {
        Log.d("ChatFragment", "🎬 Displaying generated video: $videoUri")
        
        binding.imageContainer.visibility = View.VISIBLE
        binding.generatingText.visibility = View.GONE
        stopGeneratingAnimation()
        
        // For now, show the video URI as text (we'll need VideoView for actual playback)
        // TODO: Implement proper video display with VideoView or WebView
        binding.generatedImageView.visibility = View.VISIBLE
        
        // Load video thumbnail or show placeholder
        // TODO: Extract video thumbnail and display with Glide
        
        // Show download button
        binding.downloadButton.visibility = View.VISIBLE
        binding.downloadButton.text = "Download Video"
        binding.downloadButton.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.green)
        binding.downloadButton.setOnClickListener {
            downloadVideo(videoUri)
        }
        
        // Track usage
        val currentAIModel = com.playstudio.aiteacher.pricing.AIModel.fromModelId(currentModel)
        if (currentAIModel != null) {
            // Calculate cost for video generation
            val videoCost = currentAIModel.imageOutputCostPerImage["720p"]?.get("16:9") ?: 0.0
            
            // Convert cost to token equivalent for tracking
            val equivalentOutputTokens = (videoCost / currentAIModel.outputCostPer1M * 1000000).toInt()
            
            trackMessageUsage(currentAIModel, 100, equivalentOutputTokens) // 100 tokens for prompt
        }
        
        showCustomToast("Video generated successfully!")
    }

    /**
     * Download generated video file
     */
    private fun downloadVideo(videoUri: String) {
        Log.d("ChatFragment", "Downloading video: $videoUri")
        showCustomToast("Video download started...")
        
        // TODO: Implement video download functionality
        // This will involve downloading the video file from Google's servers
        // and saving it to the device's storage
    }

    /**
     * Show preview demo for Veo since the API isn't publicly available yet
     */
    private fun showVeoPreviewDemo(prompt: String) {
        // Simulate video generation delay
        Handler(Looper.getMainLooper()).postDelayed({
            requireActivity().runOnUiThread {
                binding.generatingText.text = "Analyzing prompt..."
            }
        }, 2000)
        
        Handler(Looper.getMainLooper()).postDelayed({
            requireActivity().runOnUiThread {
                binding.generatingText.text = "Creating video frames..."
            }
        }, 4000)
        
        Handler(Looper.getMainLooper()).postDelayed({
            requireActivity().runOnUiThread {
                binding.generatingText.text = "Adding audio track..."
            }
        }, 6000)
        
        Handler(Looper.getMainLooper()).postDelayed({
            requireActivity().runOnUiThread {
                stopGeneratingAnimation()
                binding.generatingText.visibility = View.GONE
                
                // Show a preview message instead of actual video
                binding.generatedImageView.visibility = View.VISIBLE
                
                // Set a placeholder or preview text
                val previewMessage = """
                    🎬 VIDEO PREVIEW 🎬
                    
                    Prompt: "$prompt"
                    
                    This would generate an 8-second 720p video with audio featuring:
                    • Cinematic quality visuals
                    • Synchronized audio track
                    • Professional video effects
                    
                    📹 Veo 3 is coming soon! 
                    Video generation will be available when Google releases the public API.
                    
                    For now, try our image generation with GPT Image 1 or DALL-E 3!
                """.trimIndent()
                
                // Display preview message
                addMessageToChat(previewMessage, false)
                
                // Show download button (for demo)
                binding.downloadButton.visibility = View.VISIBLE
                binding.downloadButton.text = "Preview Feature"
                binding.downloadButton.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.blue)
                binding.downloadButton.setOnClickListener {
                    showCustomToast("🎬 Veo video generation will be available soon! Stay tuned for updates.")
                }
                
                // Hide the image container after showing the message
                Handler(Looper.getMainLooper()).postDelayed({
                    binding.imageContainer.visibility = View.GONE
                }, 1000)
            }
        }, 8000) // Total: 8 seconds simulation
    }

    // In ChatFragment.kt

    private fun handleDeepSeekCompletion(message: String) {
        val deepSeekApiKey = "sk-365290c51f54434e983914c5fae190a8" // Consider secure storage
        val deepSeekUrl = "https://api.deepseek.com/v1/chat/completions"

        val messagesArray = JSONArray().apply {
            chatMessages.filterNot { it.isTyping }.forEach { chatMsg ->
                put(JSONObject().apply {
                    put("role", if (chatMsg.isUser) "user" else "assistant")
                    put("content", chatMsg.content)
                })
            }
            put(JSONObject().apply {
                put("role", "user")
                put("content", message)
            })
        }

        val json = JSONObject().apply {
            put("model", "deepseek-chat")
            put("messages", messagesArray)
            put("stream", false)
        }

        val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
        val request = Request.Builder()
            .url(deepSeekUrl)
            .post(body)
            .addHeader("Authorization", "Bearer $deepSeekApiKey")
            .addHeader("Content-Type", "application/json")
            .build()

        Log.d("ChatFragment", "Sending request to DeepSeek: $json")
        showTypingIndicator()

        val clientWithTimeout = client.newBuilder()
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .connectTimeout(60, TimeUnit.SECONDS)
            .build()

        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    clientWithTimeout.newCall(request).execute()
                }
                val responseBody = response.body?.string()
                Log.d("ChatFragment", "Received response from DeepSeek: $responseBody")

                if (!response.isSuccessful) {
                    withContext(Dispatchers.Main) {
                        when (response.code) {
                            400 -> showCustomToast("Bad Request: Check your request parameters")
                            401 -> showCustomToast("Unauthorized: Check your API key")
                            403 -> showCustomToast("Forbidden: You don't have permission to access this resource")
                            404 -> showCustomToast("Not Found: Check the API URL")
                            500 -> showCustomToast("Server Error: Try again later")
                            else -> showCustomToast("Unexpected response from DeepSeek API: ${response.code}")
                        }
                        removeTypingIndicator()
                    }
                    return@launch
                }

                responseBody?.let {
                    try {
                        val jsonResponse = JSONObject(it)
                        Log.d("ChatFragment", "Parsed JSON response from DeepSeek: $jsonResponse")

                        if (jsonResponse.has("choices")) {
                            val choices = jsonResponse.getJSONArray("choices")
                            if (choices.length() > 0) {
                                val reply = choices.getJSONObject(0).getJSONObject("message")
                                    .getString("content").trim()

                                // Extract token usage for tracking
                                var inputTokens = 0
                                var outputTokens = 0
                                if (jsonResponse.has("usage")) {
                                    val usage = jsonResponse.getJSONObject("usage")
                                    inputTokens = usage.optInt("prompt_tokens", 0)
                                    outputTokens = usage.optInt("completion_tokens", 0)
                                }

                                withContext(Dispatchers.Main) {
                                    removeTypingIndicator()
                                    addMessageToChat( // Your refactored method
                                        messageContent = reply,
                                        isUser = false,
                                        containsRichContent = determineIfRichContent(reply)
                                        // Citations and followUpQuestions can be added here if DeepSeek provides them
                                    )

                                    // Track usage after successful response
                                    val currentAIModel = com.playstudio.aiteacher.pricing.AIModel.fromModelId("deepseek")
                                    if (currentAIModel != null) {
                                        trackMessageUsage(currentAIModel, inputTokens, outputTokens)
                                    }

                                    // The scrolling is now handled inside addMessageToChat (via addMessageToList)

                                    // This call might add follow-up questions to a separate UI element at the bottom
                                    // or it might be intended to add more ChatMessage items.
                                    // If it adds more ChatMessage items, it should also use addMessageToChat.
                                    generateFollowUpQuestions(reply)
                                    // TTS is now automatically handled by addMessageToChat() when audio mode is enabled
                                    incrementInteractionCount()
                                }
                            } else {
                                withContext(Dispatchers.Main) {
                                    showCustomToast("No choices found in DeepSeek response")
                                    removeTypingIndicator()
                                }
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                showCustomToast("No 'choices' field in DeepSeek response")
                                removeTypingIndicator()
                            }
                        }
                    } catch (e: JSONException) {
                        Log.e("ChatFragment", "Failed to parse DeepSeek response", e)
                        withContext(Dispatchers.Main) {
                            showCustomToast("Failed to parse DeepSeek response")
                            removeTypingIndicator()
                        }
                    }
                } ?: withContext(Dispatchers.Main) {
                    Log.w("ChatFragment", "DeepSeek response body was null")
                    removeTypingIndicator()
                    showCustomToast("Received empty response from DeepSeek")
                }
            } catch (e: IOException) {
                Log.e("ChatFragment", "Failed to get DeepSeek response", e)
                withContext(Dispatchers.Main) {
                    showCustomToast("Network error with DeepSeek: ${e.message}")
                    removeTypingIndicator()
                }
            }
        }
    }

    private fun showOverlay() {
        Log.d("ChatFragment", "Showing overlay")
        binding.subscriptionOverlay.visibility = View.VISIBLE
    }

    private fun hideOverlay() {
        Log.d("ChatFragment", "Hiding overlay")
        binding.subscriptionOverlay.visibility = View.GONE
    }

    private fun updateActiveModelButton(modelName: String) {
        binding.activeModelButton.text = modelName
    }

    private fun getDisplayNameForModel(modelId: String): String {
        return when (modelId) {
            "gpt-3.5-turbo" -> "GPT-3.5 Turbo"
            "gpt-4o" -> "GPT-4o"
            "gpt-4o-mini" -> "GPT-4o Mini"
            "gpt-4o-search-preview" -> "GPT-4o Search"
            "gpt-4o-mini-search-preview" -> "GPT-4o Mini Search"
            "gpt-4-turbo" -> "GPT-4 Turbo"
            "dall-e-3" -> "DALL-E 3"
            "gpt-image-1" -> "GPT Image 1"
            "claude-sonnet-4-20250514" -> "Claude Sonnet 4"
            "claude-opus-4-20250514" -> "Claude Opus 4"
            "o1" -> "O1"
            "o1-mini" -> "O1 Mini"
            "o3-mini" -> "O3 Mini"
            else -> modelId
        }
    }


    // --------------------------
    // Voice and Speech Functions
    // --------------------------

    private fun checkAndRequestAudioPermission(requestCode: Int): Boolean {
        return if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) {
            true
        } else {
            requestAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            false
        }
    }

    private fun initializeSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(requireContext())) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(requireContext())
            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    showCustomToast("Listening...")
                }

                override fun onBeginningOfSpeech() {
                    binding.voiceInputButton.text = "STOP"
                }

                override fun onRmsChanged(rmsdB: Float) {}

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    binding.voiceInputButton.text = "MIC"
                }

                override fun onError(error: Int) {
                    showCustomToast("Error: $error")
                    binding.voiceInputButton.text = "MIC"
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        binding.messageEditText.setText(matches[0])
                        binding.messageEditText.setSelection(matches[0].length)
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {}

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        } else {
            showCustomToast("Speech recognition is not available on this device.")
        }
    }

    private fun startEnhancedVoiceRecording() {
        // Show voice recording dialog with hold-to-record functionality
        showVoiceRecordingDialog()
    }

    private fun showVoiceRecordingDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_voice_recording, null)
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(true)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        // Initialize the audio controls view
        val audioControlsView = dialogView.findViewById<AudioControlsView>(R.id.audioControlsView)

        // Set up audio recording callback
        audioControlsView.onAudioRecorded = { audioFile ->
            // Process the recorded audio file with Whisper transcription
            lifecycleScope.launch {
                try {
                    showCustomToast("Transcribing audio...")
                    val audioHandler = audioApiHandler ?: return@launch

                    val transcription = audioHandler.transcribeAudio(audioFile)
                    val transcribedText = transcription.text

                    withContext(Dispatchers.Main) {
                        if (transcribedText.isNotBlank()) {
                            // Handle any existing text in the input field
                            val currentText = binding.messageEditText.text.toString()
                            val finalMessage = if (currentText.isBlank()) {
                                transcribedText
                            } else {
                                "$currentText $transcribedText"
                            }

                            // Clear the input field and auto-send the voice message
                            binding.messageEditText.setText("")
                            showCustomToast("Voice message sent")
                            dialog.dismiss()

                            // Enable audio mode temporarily for voice-initiated conversations
                            val wasAudioModeEnabled = isAudioModeEnabled
                            isAudioModeEnabled = true
                            Log.d("ChatFragment", "Audio mode enabled for voice message: $isAudioModeEnabled")

                            // Automatically send the transcribed message
                            processUserMessageSend(finalMessage)

                            // Keep audio mode enabled for this conversation flow
                            // (User can disable manually if desired)
                        } else {
                            showCustomToast("Could not transcribe audio. Please try again.")
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Log.e("ChatFragment", "Voice transcription failed", e)
                        showCustomToast("Transcription failed: ${e.message}")
                    }
                }
            }
        }

        // Set up cancel button
        val cancelButton = dialogView.findViewById<Button>(R.id.cancel_button)
        cancelButton.setOnClickListener {
            audioControlsView.cleanup() // Stop any ongoing recording
            dialog.dismiss()
        }

        dialog.show()
    }

    @Deprecated("Replaced with startEnhancedVoiceRecording() using Whisper API")
    private fun startVoiceRecognition() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak now...")
        }
        try {
            startActivityForResult(intent, REQUEST_CODE_SPEECH_INPUT)
        } catch (e: ActivityNotFoundException) {
            showCustomToast("Speech recognition not supported on this device")
        }
    }

    @Deprecated("Replaced with generateTextToSpeech() using AudioApiHandler")
    private fun handleTextToSpeech(text: String) {
        if (isAudioModeEnabled) {
            val json = JSONObject().apply {
                put("model", "tts-1")
                put("input", text)
                put("voice", selectedVoice)
            }

            val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
            val request = Request.Builder()
                .url("https://api.openai.com/v1/audio/speech")
                .post(body)
                .addHeader("Authorization", "Bearer ${getApiKey("openai") ?: ""}")
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.e("ChatFragment", "Failed to get TTS response", e)
                    requireActivity().runOnUiThread {
                        showCustomToast("Failed to get TTS response")
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    if (!response.isSuccessful || response.body == null) {
                        Log.e("ChatFragment", "Unexpected code $response")
                        requireActivity().runOnUiThread {
                            showCustomToast("Unexpected response from TTS API")
                        }
                        return
                    }

                    val audioBytes = response.body?.bytes()
                    if (audioBytes != null) {
                        val tempFile = File.createTempFile("tts_audio", ".mp3", requireContext().cacheDir)
                        tempFile.writeBytes(audioBytes)
                        requireActivity().runOnUiThread {
                            playAudioFromFile(tempFile)
                        }
                    } else {
                        requireActivity().runOnUiThread {
                            showCustomToast("Failed to get audio data")
                        }
                    }
                }
            })
        }
    }


    /*private fun updateSelectedVoice(voice: String) {
        selectedVoice = voice
        saveSelectedVoice(voice)
        // Voice selection now integrated in message input area
    }*/


    private fun showVoiceOptionsMenu() {
        val options = arrayOf(
            "Quick Voice Message",
            "Voice Settings",
            "Record Meeting",
            "Meeting Summary (from file)"
        )

        AlertDialog.Builder(requireContext())
            .setTitle("Voice & Meeting Options")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> startEnhancedVoiceRecording() // Quick voice message
                    1 -> showVoiceSelectionDialogInternal() // Voice settings
                    2 -> startMeetingRecording() // Meeting recording
                    3 -> selectMeetingFileForSummary() // Process existing recording
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showVoiceSelectionDialog() {
        showVoiceSelectionDialogInternal()
    }

    private fun showVoiceSelectionDialogInternal() {
        // Check microphone permission before showing audio features
        if (!checkAndRequestPermissions()) {
            // Permission request was initiated, dialog will be shown in onRequestPermissionsResult
            return
        }

        val audioEnabledModels = com.playstudio.aiteacher.pricing.AIModel.getAudioModelsForTier(getCurrentUserTier())

        if (audioEnabledModels.isEmpty()) {
            // Fallback to simple voice selection for non-audio models
            showSimpleVoiceSelection()
            return
        }

        val dialogBuilder = AlertDialog.Builder(requireContext())
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_voice_features, null)

        val audioControlsView = dialogView.findViewById<AudioControlsView>(R.id.audio_controls_view)

        // Setup audio controls
        audioControlsView.setAvailableModels(audioEnabledModels)
        audioControlsView.setSelectedVoice(selectedVoice)
        audioControlsView.setAudioModeEnabled(isAudioModeEnabled)

        // Set callbacks
        audioControlsView.onVoiceSelected = { voice ->
            updateSelectedVoice(voice)
        }

        audioControlsView.onAudioModelSelected = { model ->
            if (model.supportsAudio()) {
                selectedModel = model.modelId
                updateActiveModelButton(model)
                showCustomToast("Selected: ${model.displayName}")
            }
        }

        audioControlsView.onAudioModeToggled = { enabled ->
            isAudioModeEnabled = enabled
            if (enabled) {
                showCustomToast("Audio mode enabled - You can now use voice input")
            } else {
                showCustomToast("Audio mode disabled")
            }
        }

        audioControlsView.onAudioRecorded = { audioFile ->
            // Process the recorded audio
            processAudioInput(audioFile)
        }

        val dialog = dialogBuilder.setView(dialogView)
            .setTitle("Audio & Voice Settings")
            .setPositiveButton("Done", null)
            .setNegativeButton("Help") { _, _ ->
                showAudioHelpDialog()
            }
            .create()

        dialog.show()
    }

    private fun showSimpleVoiceSelection() {
        val voices = arrayOf("Alloy", "Echo", "Fable", "Onyx", "Nova", "Shimmer")
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("Select TTS Voice")
        builder.setItems(voices) { _, which ->
            val selectedVoice = voices[which].lowercase(Locale.ROOT)
            updateSelectedVoice(selectedVoice)
            showCustomToast("Selected voice: ${voices[which]}")
        }
        builder.setNegativeButton("Cancel", null)
        builder.show()
    }

    private var selectedVoice = "alloy"
    private val SELECTED_VOICE_KEY = "selected_voice"

    // Audio-related properties
    private var audioApiHandler: AudioApiHandler? = null
    private var currentAudioPlayer: MediaPlayer? = null
    private var isAudioModeEnabled = false

    // Meeting recording properties
    private var meetingRecorder: MediaRecorder? = null
    private var meetingStartTime: Long = 0
    private var currentMeetingFile: File? = null
    private var meetingTimerHandler: Handler? = null
    private var meetingTimerRunnable: Runnable? = null
    private var currentMeetingDialog: AlertDialog? = null
    private var pendingMeetingSummaryType: String? = null
    private var pendingMeetingTranscript: String? = null


    private fun saveSelectedVoice(voice: String) {
        val sharedPreferences = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sharedPreferences.edit().putString(SELECTED_VOICE_KEY, voice).apply()
    }

    private fun loadSelectedVoice(): String {
        val sharedPreferences = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return sharedPreferences.getString(SELECTED_VOICE_KEY, "alloy") ?: "alloy"
    }




    private fun sendImageToOpenAI(bitmap: Bitmap) {
        val base64Image = encodeImageToBase64(bitmap)
        analyzeImage(base64Image)
    }

    private fun resizeBitmap(bitmap: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        val aspectRatio = width.toFloat() / height.toFloat()
        var newWidth = maxWidth
        var newHeight = maxHeight

        if (width > height) {
            newHeight = (newWidth / aspectRatio).toInt()
        } else {
            newWidth = (newHeight * aspectRatio).toInt()
        }

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    private fun encodeImageToBase64(bitmap: Bitmap): String {
        val resizedBitmap = resizeBitmap(bitmap, 1024, 1024) // Resize the bitmap to a maximum of 1024x1024
        val byteArrayOutputStream = ByteArrayOutputStream()
        resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 100, byteArrayOutputStream)
        val byteArray = byteArrayOutputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.DEFAULT)
    }

    private fun showImageProcessingOptions(bitmap: Bitmap) {
        val options = arrayOf("Extract Text", "Analyze Image")
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("Choose an option")
        builder.setItems(options) { dialog, which ->
            when (which) {
                0 -> detectTextFromImage(bitmap)
                1 -> sendImageToOpenAI(bitmap)
            }
        }
        builder.show()
    }
    private fun analyzeImage(base64Image: String) {
        val json = JSONObject().apply {
            put("model", "gpt-4o") // Updated to use GPT-4o
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", JSONArray().apply {
                        put(JSONObject().apply {
                            put("type", "text")
                            put("text", "What is in this image?")
                        })
                        put(JSONObject().apply {
                            put("type", "image_url")
                            put("image_url", JSONObject().apply {
                                put("url", "data:image/jpeg;base64,$base64Image")
                                // For GPT-4o, detail can be "low", "high", or "auto"
                                put("detail", "auto")
                            })
                        })
                    })
                })
            })
            put("max_completion_tokens", 300)
        }

        val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
        val request = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .post(body)
            .addHeader("Authorization", "Bearer ${getApiKey("openai") ?: ""}")
            .addHeader("Content-Type", "application/json")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("ChatFragment", "API call failed", e)
                requireActivity().runOnUiThread {
                    showCustomToast("Network error: ${e.message}")
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: "No error details"
                    Log.e("ChatFragment", "API Error ${response.code}: $errorBody")
                    requireActivity().runOnUiThread {
                        showCustomToast("API Error: ${parseErrorMessage(errorBody)}")
                    }
                    return
                }

                response.body?.use { responseBody ->
                    try {
                        val jsonResponse = JSONObject(responseBody.string())
                        val reply = jsonResponse.getJSONArray("choices")
                            .getJSONObject(0)
                            .getJSONObject("message")
                            .getString("content")
                            .trim()

                        requireActivity().runOnUiThread {
                            addMessageToChat(reply, false)
                        }
                    } catch (e: Exception) {
                        Log.e("ChatFragment", "Response parsing failed", e)
                        requireActivity().runOnUiThread {
                            showCustomToast("Failed to parse response")
                        }
                    }
                }
            }
        })
    }

    private fun parseErrorMessage(errorBody: String): String {
        return try {
            JSONObject(errorBody).getJSONObject("error").getString("message")
        } catch (e: Exception) {
            "Unknown error (code: ${errorBody.take(200)})"
        }
    }
    private fun checkDailyLimit(model: String, limit: Int): Boolean {
        val sharedPreferences = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastResetTimeKey = "${model}_last_reset_time"
        val usageCountKey = "${model}_usage_count"
        val lastResetTime = sharedPreferences.getLong(lastResetTimeKey, 0)
        val currentTime = System.currentTimeMillis()

        if (currentTime - lastResetTime > TimeUnit.HOURS.toMillis(24)) {
            sharedPreferences.edit().putLong(lastResetTimeKey, currentTime)
                .putInt(usageCountKey, 0).apply()
        }

        val usageCount = sharedPreferences.getInt(usageCountKey, 0)
        return usageCount < limit
    }

    private fun incrementModelUsage(model: String) {
        val sharedPreferences = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val usageCountKey = "${model}_usage_count"
        val usageCount = sharedPreferences.getInt(usageCountKey, 0)
        sharedPreferences.edit().putInt(usageCountKey, usageCount + 1).apply()
    }


    private fun copyHighlightedText() {
        val start = binding.messageEditText.selectionStart
        val end = binding.messageEditText.selectionEnd
        val selectedText = binding.messageEditText.text.substring(start, end)

        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Copied Text", selectedText)
        clipboard.setPrimaryClip(clip)

        showCustomToast("Text copied to clipboard")
    }

    private fun deleteHighlightedText() {
        val start = binding.messageEditText.selectionStart
        val end = binding.messageEditText.selectionEnd
        binding.messageEditText.text.delete(start, end)
        showCustomToast("Text deleted")
    }





    private fun startImageCrop(uri: Uri) {
        val cropIntent = CropImage.activity(uri)
            .setGuidelines(CropImageView.Guidelines.ON)
            .getIntent(requireContext())
        cropImageLauncher.launch(cropIntent)
    }

    /*override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            WRITE_EXTERNAL_STORAGE_PERMISSION_REQUEST_CODE -> {
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    val imageUrl = binding.imageContainer.getTag(R.id.image_url) as? String
                    if (imageUrl != null) {
                        downloadImage(imageUrl)
                    }
                } else {
                    showCustomToast("Storage permission is required to download the image")
                }
            }
        }
    }*/

    /*override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, requestCode, data)
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == Activity.RESULT_OK) {
            val imageBitmap = data?.extras?.get("data") as Bitmap
            detectTextFromImage(imageBitmap)
        } else if (requestCode == REQUEST_CODE_SPEECH_INPUT && resultCode == Activity.RESULT_OK) {
            val result = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            result?.let {
                binding.messageEditText.setText(it[0])
            }
        } else if (requestCode == PICK_DOCUMENT_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            data?.data?.let { uri ->
                handleDocUpload(uri)
            }
        }
    }*/
    /*private fun loadInterstitialAd() {
        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(
            requireContext(),
            "ca-app-pub-9180832030816304/7454777206",
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdFailedToLoad(adError: LoadAdError) {
                    Log.d("ChatFragment", "Interstitial ad failed to load: ${adError.message}")
                    interstitialAd = null
                    isInterstitialAdLoaded = false
                }

                override fun onAdLoaded(ad: InterstitialAd) {
                    Log.d("ChatFragment", "Interstitial ad loaded.")
                    interstitialAd = ad
                    isInterstitialAdLoaded = true
                }
            }
        )
    }

    private fun showInterstitialAd() {
        if (isUserCurrentlySubscribed()) {
            Log.d("ChatFragment", "User is subscribed, not showing interstitial ad.")
            return
        }

        if (isInterstitialAdLoaded && interstitialAd != null) {
            interstitialAd?.show(requireActivity())
        } else {
            Log.d("ChatFragment", "Interstitial ad is not ready yet.")
            loadInterstitialAd()
        }
    }*/

    /*private fun stopVoiceRecognition() {
        speechRecognizer?.stopListening()
        showRecordingStatus(false)
        binding.voiceInputButton.text = "MIC"
    }*/

    private fun showReportDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_report, null)
        val reportDescription = dialogView.findViewById<EditText>(R.id.reportDescription)

        AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setTitle("Report Content")
            .setPositiveButton("Submit") { dialog, which ->
                val description = reportDescription.text.toString()
                submitReport(description)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun submitReport(description: String) {
        val emailIntent = Intent(Intent.ACTION_SEND).apply {
            type = "message/rfc822"
            putExtra(Intent.EXTRA_EMAIL, arrayOf("nsbusiso542@gmail.com")) // Replace with your support email address
            putExtra(Intent.EXTRA_SUBJECT, "User Report")
            putExtra(Intent.EXTRA_TEXT, description)
        }

        try {
            startActivity(Intent.createChooser(emailIntent, "Send report via email..."))
        } catch (ex: android.content.ActivityNotFoundException) {
            Toast.makeText(requireContext(), "No email clients installed.", Toast.LENGTH_SHORT).show()
        }
    }





    private fun showHelpDialog() {
        // Inflate the custom layout
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_help, null)

        // Set the title and message
        val title = dialogView.findViewById<TextView>(R.id.title)
        val message = dialogView.findViewById<TextView>(R.id.message)

        // Add emojis and format the message
        val helpMessage = """
        **How to Use the Chat** 📚
        
        - **Send Button** ✉️: Send your message to the AI.
        - **Voice Input** 🎤: Use your voice to input text.
        - **Scan Text**: Scan text from an image or document.
        - **Share Button** 📤: Share the last response from the AI.
        - **Follow-Up Questions** 🔄: Get suggested follow-up questions based on the AI's response.
        
        **GPT Models** 🤖
        
        - **GPT-3.5 Turbo**: Fast and efficient for most tasks.
        - **GPT-4o** 🧠: Advanced model for more complex queries.
        - **DALL-E 3** 🎨: Generate images from text prompts.
        - **GPT Image 1** 🎨: Next-gen multimodal image generation (text + image input).
        - **TTS-1** 🔊: Convert text to speech.
    """.trimIndent()

        // Set the message text
        message.text = helpMessage
        AlertDialog.Builder(requireContext(), R.style.CustomDialogTheme)
            .setView(dialogView)
            .setPositiveButton("Got it! 👍", null)
            .show()
    }

    fun setQuestionText(question: String) {
        binding.messageEditText.apply {
            setText(question)
            setSelection(question.length) // Move cursor to end
            requestFocus()

            // Show keyboard
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
        }
    }


    private fun hideKeyboard() {
        val imm = requireActivity().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val currentFocus = requireActivity().currentFocus
        if (currentFocus is EditText) {
            imm.hideSoftInputFromWindow(currentFocus.windowToken, 0)
            currentFocus.clearFocus()
        } else {
            imm.hideSoftInputFromWindow(view?.windowToken, 0)
        }
    }


    override fun onDestroyView() {
        super.onDestroyView()

        // Clean up voice agent resources
        if (isRealtimeMode) {
            stopRealtimeVoiceChat()
        }
        realtimeVoiceAgent?.disconnect()
        realtimeVoiceAgent = null
        voiceAgentCallback = null

        // Clean up audio resources
        currentAudioPlayer?.release()
        currentAudioPlayer = null

        // Clean up voice recording
        stopVoiceRecording()
        stopAllAIAudio()

        // Save audio mode preference
        try {
            val appPrefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            appPrefs.edit().putBoolean("audio_mode_enabled", isAudioModeEnabled).apply()
        } catch (e: Exception) {
            Log.w("ChatFragment", "Could not save audio mode preference", e)
        }

        _binding = null
    }

    private fun initializeChat(model: String?, conversationId: String?) {
        if (model != null && conversationId != null) {
            // Initialize the chat with the provided model and conversation ID
        }
    }


    fun setEmailContent(emailContent: String) {
        // Set the email content to the message input box
        binding.messageEditText.setText(emailContent)

        // Optionally move cursor to end
        binding.messageEditText.setSelection(emailContent.length)
    }

    fun setExtractedText(text: String) {
        binding.messageEditText.setText(text)
        binding.messageEditText.setSelection(text.length) // Ensure cursor is at end
        binding.messageEditText.requestFocus() // Optional: bring focus to the input field
    }
    fun setRecognizedText(text: String) {
        view?.findViewById<EditText>(R.id.messageEditText)?.apply {
            setText(text)
            setSelection(text.length) // Move cursor to end
        }
    }

    private fun detectTextFromImage(bitmap: Bitmap) {
        // Show loading indicator
        binding.progressBar.visibility = View.VISIBLE

        // Process image directly using ML Kit
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val image = InputImage.fromBitmap(bitmap, 0)

        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                // Process the recognized text
                val extractedText = processVisionText(visionText)

                // Populate directly into input box
                requireActivity().runOnUiThread {
                    binding.messageEditText.setText(extractedText)
                    binding.messageEditText.setSelection(extractedText.length)
                    binding.progressBar.visibility = View.GONE
                    showCustomToast("Text extracted successfully!")
                }
            }
            .addOnFailureListener { e ->
                requireActivity().runOnUiThread {
                    binding.progressBar.visibility = View.GONE
                    showCustomToast("Text recognition failed: ${e.message}")
                    Log.e("ChatFragment", "Text recognition error", e)
                }
            }
    }

    private fun processVisionText(visionText: Text): String {
        val stringBuilder = StringBuilder()

        // Process all text blocks
        for (block in visionText.textBlocks) {
            stringBuilder.append(block.text)
            stringBuilder.append("\n") // Add newline between blocks
        }

        return stringBuilder.toString().trim()
    }
    private fun processTextBlock(result: Text) {
        val resultText = result.text
        Log.d("ChatFragment", "Detected Text: $resultText")

        if (resultText.isNotEmpty()) {
            requireActivity().runOnUiThread {
                binding.messageEditText.setText(resultText)
            }
        } else {
            requireActivity().runOnUiThread {
                showCustomToast("No text detected.")
            }
        }

        for (block in result.textBlocks) {
            val blockText = block.text
            val blockCornerPoints = block.cornerPoints
            val blockFrame = block.boundingBox
            Log.d("ChatFragment", "Block Text: $blockText")
            Log.d("ChatFragment", "Block BoundingBox: $blockFrame")
            Log.d("ChatFragment", "Block Corner Points: ${blockCornerPoints?.joinToString()}")

            for (line in block.lines) {
                val lineText = line.text
                val lineCornerPoints = line.cornerPoints
                val lineFrame = line.boundingBox
                Log.d("ChatFragment", "Line Text: $lineText")
                Log.d("ChatFragment", "Line BoundingBox: $lineFrame")
                Log.d("ChatFragment", "Line Corner Points: ${lineCornerPoints?.joinToString()}")

                for (element in line.elements) {
                    val elementText = element.text
                    val elementCornerPoints = element.cornerPoints
                    val elementFrame = element.boundingBox
                    Log.d("ChatFragment", "Element Text: $elementText")
                    Log.d("ChatFragment", "Element BoundingBox: $elementFrame")
                    Log.d("ChatFragment", "Element Corner Points: ${elementCornerPoints?.joinToString()}")
                }
            }
        }
    }

    private fun processSelectedFile(uri: Uri) {
        val mimeType = requireContext().contentResolver.getType(uri)

        val callback = object : FileUtils.TextExtractionCallback {
            override fun onTextExtracted(extractedText: String) {
                requireActivity().runOnUiThread {
                    binding.messageEditText.setText(extractedText)
                    binding.messageEditText.setSelection(extractedText.length)
                    showCustomToast("Text extracted successfully!")
                }
            }

            override fun onError(errorMessage: String) {
                requireActivity().runOnUiThread {
                    showCustomToast(errorMessage)
                }
            }
        }

        when {
            mimeType?.startsWith("image/") == true -> {
                FileUtils.extractTextFromImage(requireContext(), uri, callback)
            }
            mimeType in setOf(
                "application/pdf",
                "application/msword",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "application/vnd.ms-excel",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            ) -> {
                FileUtils.extractTextFromDocument(requireContext(), uri, callback)
            }
            else -> {
                showCustomToast("Unsupported file type")
            }
        }
    }
    private fun showImageOrDocumentPickerDialog() {
        val options = arrayOf("Capture Image", "Pick Image", "Pick Document")
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("Choose an option")
        builder.setItems(options) { dialog, which ->
            when (which) {
                0 -> checkCameraPermission()
                1 -> openImagePicker()
                2 -> openDocumentPicker()
            }
        }
        builder.show()
    }


    private fun openImagePicker() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        startActivityForResult(intent, PICK_IMAGE_REQUEST_CODE)
    }


    private fun handleDocUpload(uri: Uri) {
        FileUtils.extractTextFromDocument(requireContext(), uri, object : FileUtils.TextExtractionCallback {
            override fun onTextExtracted(extractedText: String) {
                requireActivity().runOnUiThread {
                    binding.messageEditText.setText(extractedText)
                    binding.messageEditText.setSelection(extractedText.length)
                    showCustomToast("Text extracted successfully!")
                }
            }

            override fun onError(errorMessage: String) {
                requireActivity().runOnUiThread {
                    showCustomToast(errorMessage)
                }
            }
        })
    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            PICK_IMAGE_REQUEST_CODE -> {
                if (resultCode == Activity.RESULT_OK) {
                    data?.data?.let { uri ->
                        processSelectedFile(uri)
                    }
                }
            }
            PICK_DOCUMENT_REQUEST_CODE -> {
                if (resultCode == Activity.RESULT_OK) {
                    data?.data?.let { uri ->
                        processSelectedFile(uri)
                    }
                }
            }
            REQUEST_CODE_SPEECH_INPUT -> {
                if (resultCode == Activity.RESULT_OK && data != null) {
                    val result = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                    result?.let {
                        binding.messageEditText.setText(it[0])
                        binding.messageEditText.setSelection(it[0].length)
                    }
                }
            }


            // Other request codes...


        }
    }
    private fun incrementInteractionCount() {
        val sharedPreferences = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val interactionCount = sharedPreferences.getInt(INTERACTION_COUNT_KEY, 0) + 1
        sharedPreferences.edit().putInt(INTERACTION_COUNT_KEY, interactionCount).apply()

        val ratingReminderCount = sharedPreferences.getInt(RATING_REMINDER_COUNT_KEY, 0)

        if (interactionCount == 2 || interactionCount == 10) {
            showRatingDialog()
            sharedPreferences.edit().putInt(RATING_REMINDER_COUNT_KEY, ratingReminderCount + 1).apply()
        }
    }


    private fun showRatingDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_rate_app, null)
        val title = dialogView.findViewById<TextView>(R.id.title)
        val message = dialogView.findViewById<TextView>(R.id.message)

        title.text = "Rate AITeacher! ⭐"
        message.text = "We hope you are enjoying AITeacher. Please take a moment to rate the app on the Google Play Store. 🙏"

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setPositiveButton("Rate") { dialog, which ->
                // Direct the user to the Google Play Store to leave a review
                val appPackageName = requireContext().packageName
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$appPackageName")))
                } catch (e: android.content.ActivityNotFoundException) {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$appPackageName")))
                }
            }
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            val negativeButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)

            // Customize the buttons
            positiveButton.setTextColor(Color.BLACK)
            positiveButton.setBackgroundColor(Color.WHITE)
            negativeButton.setTextColor(Color.BLACK)
            negativeButton.setBackgroundColor(Color.WHITE)

            // Set the layout parameters for the buttons
            val layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            layoutParams.setMargins(8, 0, 8, 0)
            positiveButton.layoutParams = layoutParams
            negativeButton.layoutParams = layoutParams

            // Set the background color of the button bar
            val parent = positiveButton.parent as View
            parent.setBackgroundColor(Color.WHITE)
        }

        dialog.show()
    }


    // In ChatFragment.kt

    private fun loadConversationFromJson(conversationJson: String) {
        val messagesToLoad = mutableListOf<ChatMessage>() // Create a local temporary list
        try {
            val messagesArray = JSONArray(conversationJson)
            for (i in 0 until messagesArray.length()) {
                val messageObject = messagesArray.getJSONObject(i)
                // Use your parseChatMessageFromJson helper for consistency and to include all fields
                messagesToLoad.add(parseChatMessageFromJson(messageObject))
            }
        } catch (e: JSONException) {
            Log.e("ChatFragment", "Failed to parse conversation JSON", e)
            showCustomToast("Failed to load conversation")
            // Optionally, submit an empty list if parsing fails completely for a fresh state
            // chatAdapter.submitList(emptyList())
            return // Exit if parsing fails
        }

        chatMessages.clear()
        chatMessages.addAll(messagesToLoad)
        chatAdapter.submitList(chatMessages.toList()) {
            if (chatMessages.isNotEmpty()) {
                binding.recyclerView.smoothScrollToPosition(chatMessages.size - 1)
            }
        }
        // Optionally, save this loaded conversation as the current one
        // if this function also implies switching to this conversation.
        // If so, update conversationId and call saveChatHistory() if needed.
    }
    private fun generateConversationId(): String {
        val timestamp = System.currentTimeMillis()
        val date = Date(timestamp)
        val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd_HH:mm:ss", Locale.getDefault())
        return "conversation_${dateFormat.format(date)}"
    }
    private fun jsonArrayToStringList(jsonArray: JSONArray?): List<String> {
        if (jsonArray == null) return emptyList()
        val list = mutableListOf<String>()
        for (i in 0 until jsonArray.length()) {
            list.add(jsonArray.getString(i))
        }
        return list
    }

    private fun loadChatHistory() {
        val currentConversationId = conversationId ?: return // Don't load if no ID
        
        // Try loading from Firestore first for cross-device sync
        CoroutineScope(Dispatchers.IO).launch {
            val firestoreMessages = loadMessagesFromFirestore(currentConversationId)
            
            withContext(Dispatchers.Main) {
                if (firestoreMessages.isNotEmpty()) {
                    // Use Firestore data if available
                    Log.d("ChatFragment", "Loading ${firestoreMessages.size} messages from Firestore")
                    chatMessages.clear()
                    chatMessages.addAll(firestoreMessages)
                    chatAdapter.submitList(chatMessages.toList()) {
                        if (chatMessages.isNotEmpty()) {
                            binding.recyclerView.smoothScrollToPosition(chatMessages.size - 1)
                        }
                    }
                } else {
                    // Fall back to SharedPreferences if Firestore is empty
                    Log.d("ChatFragment", "No Firestore data found, loading from SharedPreferences")
                    loadChatHistoryFromSharedPrefs(currentConversationId)
                }
            }
        }
    }
    
    /**
     * Load chat messages from Firestore for cross-device sync
     */
    private suspend fun loadMessagesFromFirestore(conversationId: String): List<ChatMessage> = withContext(Dispatchers.IO) {
        try {
            val firebaseAuth = com.google.firebase.auth.FirebaseAuth.getInstance()
            val currentUser = firebaseAuth.currentUser
            
            if (currentUser == null) {
                Log.w("ChatFragment", "Cannot load from Firestore - user not authenticated")
                return@withContext emptyList<ChatMessage>()
            }
            
            val firestoreManager = FirestoreChatManager.getInstance()
            val firestoreMessages = firestoreManager.getChatMessages(conversationId)
            
            // Convert Firestore messages to ChatMessage format
            return@withContext firestoreMessages.map { firestoreMsg ->
                ChatMessage(
                    id = firestoreMsg.messageId,
                    content = firestoreMsg.content,
                    isUser = firestoreMsg.senderType == "user",
                    timestamp = firestoreMsg.timestamp.time,
                    isTyping = false,
                    followUpQuestions = emptyList(),
                    citations = emptyList(),
                    containsRichContent = false,
                    structuredContentJson = null
                )
            }.sortedBy { it.timestamp }
            
        } catch (e: Exception) {
            Log.e("ChatFragment", "Error loading from Firestore", e)
            return@withContext emptyList<ChatMessage>()
        }
    }
    
    /**
     * Fallback method to load from SharedPreferences (original implementation)
     */
    private fun loadChatHistoryFromSharedPrefs(currentConversationId: String) {
        val sharedPreferences = requireContext().getSharedPreferences("prefs", Context.MODE_PRIVATE)
        val savedChatsJson = sharedPreferences.getString(chatHistoryKey, "[]")
        val loadedMessages = mutableListOf<ChatMessage>()

        try {
            val savedChatsArray = JSONArray(savedChatsJson)
            for (i in 0 until savedChatsArray.length()) {
                val chatObject = savedChatsArray.getJSONObject(i)
                if (chatObject.optString("id") == currentConversationId) {
                    val messagesArray = chatObject.getJSONArray("messages")
                    for (j in 0 until messagesArray.length()) {
                        loadedMessages.add(parseChatMessageFromJson(messagesArray.getJSONObject(j)))
                    }
                    break
                }
            }
        } catch (e: JSONException) {
            Log.e("ChatFragment", "Error loading chat history from SharedPreferences", e)
        }

        chatMessages.clear()
        chatMessages.addAll(loadedMessages)
        chatAdapter.submitList(chatMessages.toList()) {
            if (chatMessages.isNotEmpty()) {
                binding.recyclerView.smoothScrollToPosition(chatMessages.size - 1)
            }
        }
    }

    private fun saveChatHistory() {
        val sharedPreferences = requireContext().getSharedPreferences("prefs", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()

        val currentMessagesToSave = chatMessages.filterNot { it.isTyping }
        if (currentMessagesToSave.isEmpty() && conversationId == null) return // Don't save empty new chats

        val messagesJsonArray = JSONArray()
        currentMessagesToSave.forEach { chatMsg ->
            messagesJsonArray.put(JSONObject().apply {
                put("id", chatMsg.id)
                put("content", chatMsg.content)
                put("isUser", chatMsg.isUser)
                put("isTyping", chatMsg.isTyping)
                put("followUpQuestions", JSONArray(chatMsg.followUpQuestions))
                val citationsArray = JSONArray()
                chatMsg.citations.forEach { c ->
                    citationsArray.put(JSONObject().apply {
                        put("url", c.url); put("title", c.title);
                        put("startIndex", c.startIndex); put("endIndex", c.endIndex)
                    })
                }
                put("citations", citationsArray)
                put("timestamp", chatMsg.timestamp)
                put("containsRichContent", chatMsg.containsRichContent)
                put("structuredContentJson", chatMsg.structuredContentJson)
            })
        }

        val currentConvId = conversationId ?: generateConversationId().also { conversationId = it }
        val chatTitle = "Chat on ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())}"

        val chatObjectToSave = JSONObject().apply {
            put("id", currentConvId)
            put("title", chatTitle)
            put("messages", messagesJsonArray)
        }

        val allChatsJson = sharedPreferences.getString(chatHistoryKey, "[]")
        val allChatsArray = try { JSONArray(allChatsJson) } catch (e: JSONException) { JSONArray() }
        val updatedChatsArray = JSONArray()
        var foundAndReplaced = false
        for (i in 0 until allChatsArray.length()) {
            val existingChat = allChatsArray.getJSONObject(i)
            if (existingChat.optString("id") == currentConvId) {
                updatedChatsArray.put(chatObjectToSave) // Replace
                foundAndReplaced = true
            } else {
                updatedChatsArray.put(existingChat)
            }
        }
        if (!foundAndReplaced) {
            updatedChatsArray.put(chatObjectToSave) // Add new
        }

        editor.putString(chatHistoryKey, updatedChatsArray.toString())
        editor.apply()
        
        // Sync to Firestore for cross-device access
        syncChatHistoryToFirestore(currentMessagesToSave, currentConvId)
    }
    
    /**
     * Sync chat messages to Firestore for cross-device access
     */
    private fun syncChatHistoryToFirestore(messages: List<ChatMessage>, conversationId: String) {
        // Run sync in background to avoid blocking the UI
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val firebaseAuth = com.google.firebase.auth.FirebaseAuth.getInstance()
                val currentUser = firebaseAuth.currentUser
                
                if (currentUser == null) {
                    Log.w("ChatFragment", "Cannot sync to Firestore - user not authenticated")
                    return@launch
                }
                
                Log.d("ChatFragment", "Syncing ${messages.size} messages to Firestore for conversation: $conversationId")
                
                val firestoreManager = FirestoreChatManager.getInstance()
                
                // Convert ChatMessage objects to Firestore format and sync each message
                messages.forEach { chatMessage ->
                    val firestoreMessage = FirestoreChatManager.FirestoreChatMessage(
                        messageId = chatMessage.id,
                        sessionId = conversationId,
                        content = chatMessage.content,
                        senderType = if (chatMessage.isUser) "user" else "ai",
                        timestamp = java.util.Date(chatMessage.timestamp),
                        aiModel = "gpt-3.5-turbo", // Default model - could be enhanced to track actual model
                        provider = "openai"
                    )
                    
                    val success = firestoreManager.saveChatMessage(firestoreMessage)
                    if (!success) {
                        Log.w("ChatFragment", "Failed to sync message ${chatMessage.id} to Firestore")
                    }
                }
                
                // Also create/update the chat session in Firestore
                val sessionTitle = if (messages.isNotEmpty()) {
                    // Use first user message as title, truncated to 50 characters
                    val firstUserMessage = messages.find { it.isUser }?.content?.take(50) ?: "Chat Session"
                    firstUserMessage
                } else {
                    "Chat Session"
                }
                
                val chatSession = FirestoreChatManager.FirestoreChatSession(
                    sessionId = conversationId,
                    title = sessionTitle,
                    aiModelUsed = "gpt-3.5-turbo",
                    category = "general",
                    createdAt = java.util.Date(messages.minByOrNull { it.timestamp }?.timestamp ?: System.currentTimeMillis()),
                    updatedAt = java.util.Date(messages.maxByOrNull { it.timestamp }?.timestamp ?: System.currentTimeMillis()),
                    isFavorite = false,
                    isArchived = false,
                    messageCount = messages.size,
                    lastMessagePreview = messages.lastOrNull()?.content?.take(100) ?: ""
                )
                
                val sessionSuccess = firestoreManager.saveChatSession(chatSession)
                if (sessionSuccess) {
                    Log.d("ChatFragment", "Successfully synced chat session to Firestore")
                } else {
                    Log.w("ChatFragment", "Failed to sync chat session to Firestore")
                }
                
            } catch (e: Exception) {
                Log.e("ChatFragment", "Error syncing to Firestore", e)
            }
        }
    }


    // loadChatHistoryById, loadConversationFromJson would be similar to loadChatHistory,
    // ensuring they use parseChatMessageFromJson and submitList.

    // ... (Rest of your ChatFragment: dialogs, API calls, permissions, etc.)

    private fun parseChatMessageFromJson(messageObject: JSONObject): ChatMessage {
        // Ensure ChatFragment.Citation is correctly Parcelable or handle parsing manually
        val citationsList = mutableListOf<com.playstudio.aiteacher.ChatFragment.Citation>()
        messageObject.optJSONArray("citations")?.let { cArray ->
            for (k in 0 until cArray.length()) {
                cArray.getJSONObject(k)?.let { cObj ->
                    citationsList.add(com.playstudio.aiteacher.ChatFragment.Citation(
                        url = cObj.getString("url"),
                        title = cObj.getString("title"),
                        startIndex = cObj.getInt("startIndex"),
                        endIndex = cObj.getInt("endIndex")
                    ))
                }
            }
        }
        return ChatMessage(
            id = messageObject.optString("id", UUID.randomUUID().toString()),
            content = messageObject.getString("content"),
            isUser = messageObject.getBoolean("isUser"),
            isTyping = messageObject.optBoolean("isTyping", false),
            followUpQuestions = jsonArrayToStringList(messageObject.optJSONArray("followUpQuestions")),
            citations = citationsList,
            timestamp = messageObject.optLong("timestamp", System.currentTimeMillis()),
            containsRichContent = messageObject.optBoolean("containsRichContent", false),
            isWebSearchResult = messageObject.optBoolean("isWebSearchResult", false),
            structuredContentJson = messageObject.optString("structuredContentJson", null)
        )
    }

    private fun showChatHistoryDialog() {
        val sharedPreferences = requireContext().getSharedPreferences("prefs", Context.MODE_PRIVATE)
        val savedChatsArray = JSONArray(sharedPreferences.getString(chatHistoryKey, "[]"))

        val chatTitles = mutableListOf<String>()
        val chatIds = mutableListOf<String>()

        for (i in 0 until savedChatsArray.length()) {
            val chatObject = savedChatsArray.getJSONObject(i)
            chatTitles.add(chatObject.getString("title"))
            chatIds.add(chatObject.getString("id"))
        }

        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("Chat History")
        builder.setItems(chatTitles.toTypedArray()) { dialog, which ->
            showChatOptionsDialog(chatIds[which], chatTitles[which])
        }
        builder.setNegativeButton("Cancel", null)
        builder.setNeutralButton("Delete All") { dialog, which ->
            showDeleteAllConfirmationDialog()
        }
        builder.show()
    }

    private fun checkCameraPermission() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.CAMERA, Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            arrayOf(Manifest.permission.CAMERA, Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        if (permissions.any {
                ContextCompat.checkSelfPermission(requireContext(), it) != PackageManager.PERMISSION_GRANTED
            }) {
            requestPermissions(permissions, CAMERA_REQUEST_CODE)
        } else {
            dispatchTakePictureIntent()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            CAMERA_REQUEST_CODE -> {
                if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    dispatchTakePictureIntent()
                } else {
                    showCustomToast("Camera permission required")
                }
            }

            REQUEST_STORAGE_PERMISSION -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Handle storage permission granted
                } else {
                    showCustomToast("Storage permission required")
                }
            }
            PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    // Permissions granted, proceed with voice recognition
                    startVoiceRecognition()
                } else {
                    showCustomToast("Permissions required for voice recognition")
                }
            }

            REQUEST_RECORD_AUDIO_PERMISSION -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Audio permission granted, continue with pending voice chat
                    if (pendingVoiceAgentType != null) {
                        // Continue with the specific agent type that was requested
                        startRealtimeVoiceChat(pendingVoiceAgentType!!)
                        pendingVoiceAgentType = null // Clear the pending state
                    } else {
                        // Fallback to agent selection dialog if no specific agent was pending
                        showVoiceAgentSelectionDialog()
                    }
                } else {
                    showCustomToast("Microphone permission is required for audio features")
                    pendingVoiceAgentType = null // Clear pending state on permission denial
                }
            }

            // Add other permission request codes here as needed
        }
    }
    private fun checkAndRequestPermissions(permissions: Array<String>, requestCode: Int) {
        val permissionsToRequest = permissions.filter {
            ContextCompat.checkSelfPermission(requireContext(), it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissions(permissionsToRequest, requestCode)
        } else {
            onPermissionsGranted(requestCode)
        }
    }

    private fun onPermissionsGranted(requestCode: Int) {
        when (requestCode) {
            CAMERA_REQUEST_CODE -> dispatchTakePictureIntent()
            WRITE_EXTERNAL_STORAGE_PERMISSION_REQUEST_CODE -> {
                val imageUrl = binding.imageContainer.getTag(R.id.image_url) as? String
                if (imageUrl != null) {
                    downloadImage(imageUrl)
                }
            }
            PICK_DOCUMENT_REQUEST_CODE -> openDocumentPicker()
        }
    }

    private fun dispatchTakePictureIntent() {
        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)

        // Create a file to save the image
        val photoFile = createImageFile()
        photoFile?.let { file ->
            val photoURI = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                file
            )
            takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)

            // Save the file path to use it later
            currentPhotoPath = file.absolutePath

            captureImageLauncher.launch(takePictureIntent)
        } ?: run {
            showCustomToast("Error creating image file")
        }
    }

    private var currentPhotoPath: String = ""

    @Throws(IOException::class)
    private fun createImageFile(): File? {
        // Create an image file name
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir = requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile(
            "JPEG_${timeStamp}_", /* prefix */
            ".jpg", /* suffix */
            storageDir /* directory */
        ).apply {
            // Save a file: path for use with ACTION_VIEW intents
            currentPhotoPath = absolutePath
        }
    }

    private fun galleryAddPic(imagePath: String) {
        val mediaScanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
        val f = File(imagePath)
        val contentUri = Uri.fromFile(f)
        mediaScanIntent.data = contentUri
        requireContext().sendBroadcast(mediaScanIntent)
        showCustomToast("Image saved to gallery")
    }


    private fun showTypingIndicator() {
        Log.d("ChatFragment", "Showing typing indicator")
        val typingMessage = ChatMessage(
            id = "typing_${System.currentTimeMillis()}",
            content = "...", // Content for typing can be minimal
            isUser = false,
            isTyping = true
        )
        addMessageToList(typingMessage)
    }

    private fun removeTypingIndicator() {
        Log.d("ChatFragment", "Removing typing indicator")
        val listChanged = chatMessages.removeAll { it.isTyping }
        Log.d("ChatFragment", "Typing indicator removed, list changed: $listChanged")
        if (listChanged) {
            chatAdapter.submitList(chatMessages.toList())
        }
    }

    /*private fun stopVoiceRecognition() {
        speechRecognizer?.stopListening()
        showRecordingStatus(false)
        binding.voiceInputButton.text = "MIC"
    }*/

    // In ChatFragment.kt

    private fun shareLastResponse() {
        // Get the current list from the adapter
        val currentChatList = chatMessages
        if (currentChatList.isNotEmpty()) {
            // Find the last message that is NOT from the user and NOT a typing indicator
            val lastMessageToShare = currentChatList.lastOrNull { message -> !message.isUser && !message.isTyping }

            if (lastMessageToShare != null && lastMessageToShare.content.isNotBlank()) {
                val shareIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, lastMessageToShare.content) // Share the content
                }

                if (shareIntent.resolveActivity(requireContext().packageManager) != null) {
                    startActivity(Intent.createChooser(shareIntent, "Share the response via"))
                } else {
                    showCustomToast("No app available to share the response")
                }
            } else {
                showCustomToast("No response available to share")
            }
        } else {
            showCustomToast("No response available to share")
        }
    }

    // ===========================================
    // AUDIO PROCESSING METHODS
    // ===========================================

    private fun initializeAudioHandler() {
        audioApiHandler = AudioApiHandler(requireContext())

        // Load audio mode preference
        val appPrefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        isAudioModeEnabled = appPrefs.getBoolean("audio_mode_enabled", false)
    }

    private fun processAudioInput(audioFile: File) {
        if (!isAudioModeEnabled) {
            showCustomToast("Audio mode is disabled")
            return
        }

        lifecycleScope.launch {
            try {
                binding.progressBar.visibility = View.VISIBLE

                // Get current model from selectedModel or sharedPrefs or use default
                val currentModelName = selectedModel ?: run {
                    val sharedPrefs = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                    sharedPrefs.getString("selected_model", "gpt-4o-audio-preview") ?: "gpt-4o-audio-preview"
                }

                // For now, always use transcription approach due to OpenAI format restrictions
                // Direct audio input requires very specific formats (wav/mp3) that MediaRecorder doesn't produce reliably
                processAudioTranscriptionThenChat(audioFile)

            } catch (e: Exception) {
                Log.e("ChatFragment", "Error processing audio input", e)
                showCustomToast("Audio processing failed: ${e.message}")
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private suspend fun processAudioChatCompletion(audioFile: File, model: com.playstudio.aiteacher.pricing.AIModel) {
        val audioHandler = audioApiHandler ?: return

        try {
            // Add user audio message to chat
            addMessageToChat("🎤 Audio message", isUser = true)

            // Send to API with audio support
            val response = audioHandler.chatCompletionWithAudio(
                model = model,
                messages = chatMessages.filter { !it.isTyping },
                audioInput = audioFile,
                voice = "alloy", // Default voice
                audioFormat = AudioApiHandler.FORMAT_WAV
            )

            // Add AI response message
            addMessageToChat(response.textContent, isUser = false)

            // Play audio response if available
            if (response.audioData != null) {
                playAudioResponse(response.audioData)
            }

        } catch (e: Exception) {
            Log.e("ChatFragment", "Audio chat completion failed", e)
            showCustomToast("Audio chat failed: ${e.message}")
        }
    }

    private suspend fun processAudioTranscriptionThenChat(audioFile: File) {
        val audioHandler = audioApiHandler ?: return

        try {
            // Show transcription status
            showCustomToast("Transcribing audio...")

            // First transcribe the audio
            val transcription = audioHandler.transcribeAudio(
                audioFile = audioFile,
                model = "whisper-1",
                responseFormat = "json"
            )

            if (transcription.text.isNotBlank()) {
                // Add transcribed text as user message
                addMessageToChat("🎤 ${transcription.text}", isUser = true)

                // Process as regular text message
                sendMessageToAPI(transcription.text)
            } else {
                showCustomToast("Could not transcribe audio. Please try again.")
            }

        } catch (e: Exception) {
            Log.e("ChatFragment", "Audio transcription failed", e)
            showCustomToast("Transcription failed: ${e.message}")
        }
    }

    private suspend fun playAudioResponse(audioData: String) {
        try {
            val audioHandler = audioApiHandler ?: return
            val audioFile = audioHandler.saveBase64Audio(audioData, AudioApiHandler.FORMAT_WAV)

            // Stop any currently playing audio
            currentAudioPlayer?.release()

            // Play the new audio
            currentAudioPlayer = audioHandler.playAudioFile(audioFile)

            currentAudioPlayer?.setOnCompletionListener { player ->
                player.release()
                currentAudioPlayer = null
            }

        } catch (e: Exception) {
            Log.e("ChatFragment", "Error playing audio response", e)
            showCustomToast("Audio playback failed")
        }
    }

    private suspend fun generateTextToSpeech(text: String) {
        if (text.isBlank()) return

        Log.d("ChatFragment", "Starting TTS generation for text: ${text.take(50)}... with voice: $selectedVoice")

        try {
            val audioHandler = audioApiHandler ?: return

            val audioFile = audioHandler.textToSpeech(
                text = text,
                voice = selectedVoice
            )

            Log.d("ChatFragment", "TTS audio file generated successfully, now playing...")

            // Play the generated speech
            currentAudioPlayer?.release()
            currentAudioPlayer = audioHandler.playAudioFile(audioFile)

            Log.d("ChatFragment", "TTS audio playback started")

        } catch (e: Exception) {
            Log.e("ChatFragment", "TTS generation failed", e)
        }
    }

    /*private fun showVoiceSelectionDialog() {
        val voiceOptions = AudioApiHandler.SUPPORTED_VOICES.toTypedArray()

        AlertDialog.Builder(requireContext())
            .setTitle("Select Voice")
            .setItems(voiceOptions) { _, which ->
                val selectedVoice = voiceOptions[which]
                saveSelectedVoice(selectedVoice)
                showCustomToast("Voice changed to: $selectedVoice")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }*/

    private fun showAudioHelpDialog() {
        val helpText = """
            🎙️ Audio Features:
            
            • Voice Input: Hold record button to capture audio
            • Voice Output: AI responses can be spoken aloud
            • Model Selection: Choose audio-enabled models
            • Voice Selection: Pick from 6 different voices
            
            📱 How to Use:
            1. Enable audio mode toggle
            2. Select an audio-capable model
            3. Choose your preferred voice
            4. Hold record button to speak
            5. Release to send your message
            
            Realtime Models:
            Some models support low-latency voice conversation for natural dialogue.
            
            🔧 Requirements:
            • Microphone permission
            • Compatible subscription tier
            • Audio-enabled AI model
        """.trimIndent()

        AlertDialog.Builder(requireContext())
            .setTitle("Audio Features Help")
            .setMessage(helpText)
            .setPositiveButton("Got it!", null)
            .show()
    }

    private fun getCurrentUserTier(): com.playstudio.aiteacher.pricing.SubscriptionTier {
        return try {
            val subscriptionUIManager = SubscriptionUIManager(requireContext())
            // This would need to be a blocking call or we'd need to restructure
            // For now, let's use the SharedPreferences approach
            val sharedPreferences = requireContext().getSharedPreferences("prefs", Context.MODE_PRIVATE)
            val subscriptionType = sharedPreferences.getString("subscription_type", null)
            val expirationTime = sharedPreferences.getLong("expiration_time", 0)
            val currentTime = System.currentTimeMillis()

            if (currentTime < expirationTime && subscriptionType != null) {
                when (subscriptionType.lowercase()) {
                    "essential", "basic" -> com.playstudio.aiteacher.pricing.SubscriptionTier.BASIC
                    "pro", "professional" -> com.playstudio.aiteacher.pricing.SubscriptionTier.PRO
                    "premium" -> com.playstudio.aiteacher.pricing.SubscriptionTier.PREMIUM
                    "ultra_premium", "enterprise" -> com.playstudio.aiteacher.pricing.SubscriptionTier.ENTERPRISE
                    else -> com.playstudio.aiteacher.pricing.SubscriptionTier.FREE
                }
            } else {
                com.playstudio.aiteacher.pricing.SubscriptionTier.FREE
            }
        } catch (e: Exception) {
            com.playstudio.aiteacher.pricing.SubscriptionTier.FREE
        }
    }

    private fun updateActiveModelButton(model: com.playstudio.aiteacher.pricing.AIModel) {
        binding.activeModelButton.text = if (model.supportsAudio()) {
            "[AUDIO] ${model.displayName}"
        } else {
            model.displayName
        }
    }

    private fun updateSelectedVoice(voice: String) {
        selectedVoice = voice
        saveSelectedVoice(voice)
        // Voice selection now integrated within message input area
        showCustomToast("Voice changed to: ${voice.replaceFirstChar { it.uppercase() }}")
    }

    // =================== MEETING RECORDING FEATURES ===================

    private fun startMeetingRecording() {
        if (isMeetingRecording) {
            stopMeetingRecording()
            return
        }

        if (!checkAndRequestPermissions()) {
            showCustomToast("Microphone permission required for meeting recording")
            return
        }

        showMeetingRecordingDialog()
    }

    private fun showMeetingRecordingDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_meeting_recording, null)
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(false)
            .create()

        currentMeetingDialog = dialog
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val startStopButton = dialogView.findViewById<Button>(R.id.startStopRecordingButton)
        val statusText = dialogView.findViewById<TextView>(R.id.statusText)
        val timerText = dialogView.findViewById<TextView>(R.id.timerText)
        val doneButton = dialogView.findViewById<Button>(R.id.doneButton)
        val pauseResumeButton = dialogView.findViewById<Button>(R.id.pauseResumeButton)

        // Update UI based on current state
        updateMeetingRecordingUI(startStopButton, statusText, timerText, doneButton, pauseResumeButton)

        startStopButton.setOnClickListener {
            if (isMeetingRecording) {
                stopMeetingRecordingInternal()
            } else {
                startMeetingRecordingInternal()
            }
            updateMeetingRecordingUI(startStopButton, statusText, timerText, doneButton, pauseResumeButton)
        }

        pauseResumeButton.setOnClickListener {
            if (isMeetingRecording) {
                // Pause recording
                stopMeetingRecordingInternal()
                updateMeetingRecordingUI(startStopButton, statusText, timerText, doneButton, pauseResumeButton)
            }
        }

        doneButton.setOnClickListener {
            stopMeetingTimer()
            if (isMeetingRecording) {
                stopMeetingRecordingInternal()
            }
            currentMeetingFile?.let { file ->
                processMeetingRecording(file)
            }
            dialog.dismiss()
            currentMeetingDialog = null
        }

        dialog.setOnDismissListener {
            stopMeetingTimer()
            currentMeetingDialog = null
        }

        dialog.show()
    }

    private fun startMeetingRecordingInternal() {
        try {
            currentMeetingFile = File(context?.cacheDir, "meeting_${System.currentTimeMillis()}.m4a")

            meetingRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(requireContext())
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(currentMeetingFile?.absolutePath)
                setMaxDuration(3600000) // 1 hour max
                prepare()
                start()
            }

            isMeetingRecording = true
            meetingStartTime = System.currentTimeMillis()
            startMeetingTimer()
            showCustomToast("Meeting recording started")

        } catch (e: Exception) {
            Log.e("ChatFragment", "Failed to start meeting recording", e)
            showCustomToast("Failed to start recording: ${e.message}")
        }
    }

    private fun stopMeetingRecordingInternal() {
        try {
            meetingRecorder?.apply {
                stop()
                release()
            }
            meetingRecorder = null
            isMeetingRecording = false
            stopMeetingTimer()
            showCustomToast("Meeting recording stopped")

        } catch (e: Exception) {
            Log.e("ChatFragment", "Error stopping meeting recording", e)
            showCustomToast("Error stopping recording")
        }
    }

    private fun stopMeetingRecording() {
        if (isMeetingRecording) {
            stopMeetingRecordingInternal()
        }
    }

    private fun updateMeetingRecordingUI(
        startStopButton: Button,
        statusText: TextView,
        timerText: TextView,
        doneButton: Button,
        pauseResumeButton: Button? = null
    ) {
        if (isMeetingRecording) {
            startStopButton.text = "Stop Recording"
            statusText.text = "Recording in progress..."
            doneButton.text = "Stop & Process Meeting"
            doneButton.visibility = View.VISIBLE
            pauseResumeButton?.visibility = View.VISIBLE
            pauseResumeButton?.text = "Pause"
        } else {
            startStopButton.text = if (currentMeetingFile?.exists() == true) "Resume Recording" else "Start Recording"
            statusText.text = if (currentMeetingFile?.exists() == true) "Recording paused" else "Ready to record"
            doneButton.text = if (currentMeetingFile?.exists() == true) "Process Meeting" else "Cancel"
            doneButton.visibility = if (currentMeetingFile?.exists() == true) View.VISIBLE else View.GONE
            pauseResumeButton?.visibility = View.GONE
        }

        // Update timer display
        updateTimerDisplay(timerText)
    }

    private fun startMeetingTimer() {
        meetingTimerHandler = Handler(Looper.getMainLooper())
        meetingTimerRunnable = object : Runnable {
            override fun run() {
                currentMeetingDialog?.let { dialog ->
                    val timerText = dialog.findViewById<TextView>(R.id.timerText)
                    updateTimerDisplay(timerText)
                }
                meetingTimerHandler?.postDelayed(this, 1000) // Update every second
            }
        }
        meetingTimerHandler?.post(meetingTimerRunnable!!)
    }

    private fun stopMeetingTimer() {
        meetingTimerRunnable?.let { runnable ->
            meetingTimerHandler?.removeCallbacks(runnable)
        }
        meetingTimerHandler = null
        meetingTimerRunnable = null
    }

    private fun updateTimerDisplay(timerText: TextView?) {
        if (meetingStartTime > 0) {
            val elapsedTime = System.currentTimeMillis() - meetingStartTime
            val seconds = (elapsedTime / 1000) % 60
            val minutes = (elapsedTime / (1000 * 60)) % 60
            val hours = (elapsedTime / (1000 * 60 * 60))

            val timeString = String.format("%02d:%02d:%02d", hours, minutes, seconds)
            timerText?.text = timeString
        } else {
            timerText?.text = "00:00:00"
        }
    }

    private fun processMeetingRecording(meetingFile: File) {
        val fileSizeMB = meetingFile.length() / (1024 * 1024)
        val durationMinutes = estimateAudioDurationMinutes(meetingFile)

        Log.d("ChatFragment", "Processing meeting: ${fileSizeMB}MB, estimated ${durationMinutes} minutes")

        // Show processing strategy dialog for large files
        if (fileSizeMB > 25 || durationMinutes > 30) {
            showLongRecordingStrategyDialog(meetingFile, fileSizeMB, durationMinutes)
        } else {
            // Standard processing for smaller files
            processStandardMeeting(meetingFile, fileSizeMB)
        }
    }

    private fun estimateAudioDurationMinutes(audioFile: File): Int {
        // Rough estimation: M4A typically ~1MB per minute at standard quality
        val fileSizeMB = audioFile.length() / (1024 * 1024)
        return (fileSizeMB * 1.2).toInt() // Add 20% buffer for estimation
    }

    private fun showLongRecordingStrategyDialog(meetingFile: File, fileSizeMB: Long, durationMinutes: Int) {
        val message = buildString {
            appendLine("Large Meeting Detected")
            appendLine("Size: ${fileSizeMB}MB")
            appendLine("Estimated Duration: ${durationMinutes} minutes")
            appendLine()
            appendLine("Choose processing method:")
        }

        val options = arrayOf(
            "Split & Process (Recommended)",
            "Try Full File (May timeout)",
            "Save Audio Only"
        )

        AlertDialog.Builder(requireContext())
            .setTitle("Long Recording Processing")
            .setMessage(message)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> processLongMeetingInChunks(meetingFile, fileSizeMB, durationMinutes)
                    1 -> processFullLongMeeting(meetingFile, fileSizeMB, durationMinutes)
                    2 -> saveAudioFileOnly(meetingFile)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun processStandardMeeting(meetingFile: File, fileSizeMB: Long) {
        showCustomToast("Processing meeting recording... (${fileSizeMB}MB)")
        showLoadingOverlay(true)

        lifecycleScope.launch {
            try {
                val audioHandler = audioApiHandler
                if (audioHandler == null) {
                    withContext(Dispatchers.Main) {
                        showLoadingOverlay(false)
                        showCustomToast("Audio handler not available")
                    }
                    return@launch
                }

                val transcription = audioHandler.transcribeAudio(
                    audioFile = meetingFile,
                    model = "whisper-1",
                    responseFormat = "json"
                )

                withContext(Dispatchers.Main) {
                    showLoadingOverlay(false)
                    showMeetingSummaryDialog(transcription.text, meetingFile)
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showLoadingOverlay(false)
                    handleMeetingProcessingError(e, meetingFile, fileSizeMB)
                }
            }
        }
    }

    private fun processLongMeetingInChunks(meetingFile: File, fileSizeMB: Long, durationMinutes: Int) {
        showCustomToast("Preparing to split large meeting into chunks...")
        showLoadingOverlay(true)

        lifecycleScope.launch {
            try {
                withContext(Dispatchers.Main) {
                    showCustomToast("Splitting ${durationMinutes}-minute recording into manageable chunks...")
                }

                // For now, we'll simulate chunking and recommend the user manually split
                // In a full implementation, you'd use FFmpeg or similar to split audio
                withContext(Dispatchers.Main) {
                    showLoadingOverlay(false)
                    showChunkingRecommendationDialog(meetingFile, fileSizeMB, durationMinutes)
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showLoadingOverlay(false)
                    Log.e("ChatFragment", "Chunking failed", e)
                    showCustomToast("Chunking failed: ${e.message}")
                }
            }
        }
    }

    private fun processFullLongMeeting(meetingFile: File, fileSizeMB: Long, durationMinutes: Int) {
        showCustomToast("Processing full ${durationMinutes}-minute recording... This may take 10-15 minutes.")
        showLoadingOverlay(true)

        lifecycleScope.launch {
            try {
                val audioHandler = audioApiHandler
                if (audioHandler == null) {
                    withContext(Dispatchers.Main) {
                        showLoadingOverlay(false)
                        showCustomToast("Audio handler not available")
                    }
                    return@launch
                }

                // Show progress updates for long processing
                var progressCounter = 0
                val progressTimer = Timer()
                progressTimer.scheduleAtFixedRate(object : TimerTask() {
                    override fun run() {
                        progressCounter++
                        lifecycleScope.launch {
                            withContext(Dispatchers.Main) {
                                val minutes = progressCounter / 2
                                showCustomToast("Still processing... ${minutes} minutes elapsed")
                            }
                        }
                    }
                }, 30000, 30000) // Update every 30 seconds

                try {
                    val transcription = audioHandler.transcribeAudio(
                        audioFile = meetingFile,
                        model = "whisper-1",
                        responseFormat = "json"
                    )

                    progressTimer.cancel()

                    withContext(Dispatchers.Main) {
                        showLoadingOverlay(false)
                        showCustomToast("Long recording processed successfully!")
                        showMeetingSummaryDialog(transcription.text, meetingFile)
                    }
                } catch (e: Exception) {
                    progressTimer.cancel()
                    throw e
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showLoadingOverlay(false)
                    handleMeetingProcessingError(e, meetingFile, fileSizeMB)
                }
            }
        }
    }

    private fun saveAudioFileOnly(meetingFile: File) {
        lifecycleScope.launch {
            try {
                val timestamp = System.currentTimeMillis()
                val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
                val formattedDate = dateFormat.format(Date(timestamp))

                // Copy audio file to Documents/Meeting Transcripts folder
                val fileName = "Meeting_Audio_${formattedDate}.m4a"
                val success = copyAudioFileToDocuments(meetingFile, fileName)

                withContext(Dispatchers.Main) {
                    if (success) {
                        showCustomToast("Audio file saved to Documents/Meeting Transcripts/")
                        showMeetingFileSavedDialog(fileName, "audio file")
                    } else {
                        showCustomToast("Failed to save audio file")
                    }
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Log.e("ChatFragment", "Failed to save audio file", e)
                    showCustomToast("Failed to save audio file: ${e.message}")
                }
            }
        }
    }

    private fun copyAudioFileToDocuments(sourceFile: File, fileName: String): Boolean {
        return try {
            val audioContent = sourceFile.readBytes()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = requireContext().contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "audio/m4a")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOCUMENTS}/Meeting Transcripts")
                }

                val uri = resolver.insert(MediaStore.Files.getContentUri("external"), contentValues)
                uri?.let { fileUri ->
                    resolver.openOutputStream(fileUri)?.use { outputStream ->
                        outputStream.write(audioContent)
                    }
                    true
                } ?: false

            } else {
                val documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
                val meetingDir = File(documentsDir, "Meeting Transcripts")

                if (!meetingDir.exists()) {
                    meetingDir.mkdirs()
                }

                val file = File(meetingDir, fileName)
                file.writeBytes(audioContent)

                // Notify media scanner
                val intent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
                intent.data = Uri.fromFile(file)
                requireContext().sendBroadcast(intent)

                true
            }
        } catch (e: Exception) {
            Log.e("ChatFragment", "Failed to copy audio file", e)
            false
        }
    }

    private fun showChunkingRecommendationDialog(meetingFile: File, fileSizeMB: Long, durationMinutes: Int) {
        val message = buildString {
            appendLine("Large Meeting Detected")
            appendLine("Size: ${fileSizeMB}MB (${durationMinutes} minutes)")
            appendLine()
            appendLine("For best results with long recordings, consider:")
            appendLine("• Recording in 20-30 minute segments")
            appendLine("• Using an external audio editor to split the file")
            appendLine("• Processing each segment separately")
            appendLine()
            appendLine("The audio file has been saved to Documents for manual processing.")
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Long Recording Recommendations")
            .setMessage(message)
            .setPositiveButton("Save Audio File") { _, _ ->
                saveAudioFileOnly(meetingFile)
            }
            .setNeutralButton("Try Anyway") { _, _ ->
                processFullLongMeeting(meetingFile, fileSizeMB, durationMinutes)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun handleMeetingProcessingError(e: Exception, meetingFile: File, fileSizeMB: Long) {
        Log.e("ChatFragment", "Meeting processing error", e)

        when (e) {
            is java.net.SocketTimeoutException -> {
                showMeetingTimeoutDialog(meetingFile, fileSizeMB)
            }
            is IOException -> {
                AlertDialog.Builder(requireContext())
                    .setTitle("Network Error")
                    .setMessage("Network error during processing. This could be due to:\n\n• Slow internet connection\n• Large file size (${fileSizeMB}MB)\n• Server load\n\nWould you like to save the audio file for later processing?")
                    .setPositiveButton("Save Audio") { _, _ ->
                        saveAudioFileOnly(meetingFile)
                    }
                    .setNeutralButton("Try Again") { _, _ ->
                        processStandardMeeting(meetingFile, fileSizeMB)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            else -> {
                showCustomToast("Processing failed: ${e.message}")
            }
        }
    }

    private fun showLoadingOverlay(show: Boolean) {
        try {
            binding.loadingOverlay.visibility = if (show) View.VISIBLE else View.GONE
            binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
        } catch (e: Exception) {
            Log.e("ChatFragment", "Error toggling loading overlay", e)
        }
    }

    private fun showMeetingTimeoutDialog(meetingFile: File, fileSizeMB: Long) {
        AlertDialog.Builder(requireContext())
            .setTitle("Processing Timeout")
            .setMessage("The meeting recording (${fileSizeMB}MB) is taking longer than expected to process.\n\nThis might be due to:\n• Large file size\n• Network connection\n• Server load\n\nWould you like to try again?")
            .setPositiveButton("Try Again") { _, _ ->
                processMeetingRecording(meetingFile)
            }
            .setNeutralButton("Try Smaller Chunks") { _, _ ->
                showCustomToast("Consider splitting large meetings into smaller segments")
            }
            .setNegativeButton("Cancel") { _, _ ->
                showCustomToast("Meeting processing cancelled")
            }
            .show()
    }

    private fun showMeetingSummaryDialog(transcript: String, meetingFile: File) {
        val summaryOptions = arrayOf(
            "Generate Meeting Summary",
            "Extract Action Items",
            "Identify Key Decisions",
            "Create Meeting Minutes",
            "Analyze Speakers & Contributions",
            "Full Transcript Only"
        )

        AlertDialog.Builder(requireContext())
            .setTitle("Meeting Processing Options")
            .setMessage("Meeting transcribed successfully! Choose how to process:\\n\\n✓ All summaries will be automatically saved to Documents/Meeting Transcripts/")
            .setItems(summaryOptions) { _, which ->
                when (which) {
                    0 -> generateMeetingSummary(transcript, "summary")
                    1 -> generateMeetingSummary(transcript, "action_items")
                    2 -> generateMeetingSummary(transcript, "key_decisions")
                    3 -> generateMeetingSummary(transcript, "meeting_minutes")
                    4 -> generateMeetingSummary(transcript, "speaker_analysis")
                    5 -> {
                        showFullTranscript(transcript)
                        saveMeetingTranscript(transcript, meetingFile)
                    }
                }
            }
            .setNeutralButton("Save Transcript Only") { _, _ ->
                saveMeetingTranscript(transcript, meetingFile)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun generateMeetingSummary(transcript: String, summaryType: String) {
        val prompt = when (summaryType) {
            "summary" -> """
                Create a comprehensive meeting summary from this transcript:
                
                $transcript
                
                **EXECUTIVE SUMMARY**
                - Meeting purpose and context (2-3 sentences)
                - Key outcomes and decisions reached
                
                **PARTICIPANTS & ENGAGEMENT**
                - Identify speakers and their roles (if determinable)
                - Note level of participation and contribution patterns
                
                **MAIN DISCUSSION TOPICS**
                - Primary agenda items covered
                - Secondary topics that emerged
                - Time allocation for major discussion points
                
                **OUTCOMES & NEXT STEPS**
                - Specific decisions made
                - Action items identified
                - Follow-up requirements
                
                **MEETING EFFECTIVENESS**
                - Overall productivity assessment
                - Areas where more discussion may be needed
            """.trimIndent()

            "action_items" -> """
                Extract and analyze all action items from this meeting transcript:
                
                $transcript
                
                **IMMEDIATE ACTION ITEMS** (This week)
                - [Task description] 
                  - Owner: [Person/Team identified]
                  - Due date: [If mentioned] 
                  - Priority: [High/Medium/Low based on context]
                  - Dependencies: [Other tasks this depends on]
                
                **SHORT-TERM ACTION ITEMS** (Next 2-4 weeks)
                - [Task description]
                  - Owner: [Person/Team identified]
                  - Timeline: [If mentioned]
                  - Success criteria: [How to measure completion]
                
                **LONG-TERM ACTION ITEMS** (Beyond 1 month)
                - [Strategic items or major projects discussed]
                
                **FOLLOW-UP MEETINGS REQUIRED**
                - [Meeting type] with [participants] by [date]
                
                **UNCLEAR OR UNASSIGNED ITEMS**
                - [Items that need clarification or assignment]
                
                **ACTION ITEM SUMMARY**
                - Total items identified: [Count]
                - Items with clear owners: [Count]
                - Items with deadlines: [Count]
                - Items requiring follow-up: [Count]
            """.trimIndent()

            "key_decisions" -> """
                Analyze all decisions made in this meeting transcript:
                
                $transcript
                
                **FINAL DECISIONS REACHED**
                - [Decision description]
                  - Decision maker(s): [Who made/approved the decision]
                  - Rationale: [Why this decision was made]
                  - Impact: [Who/what this affects]
                  - Implementation timeline: [When to execute]
                  - Success metrics: [How to measure success]
                
                **TENTATIVE DECISIONS** (Requiring further approval/discussion)
                - [Decision pending final approval]
                  - Conditions for finalization: [What needs to happen]
                  - Decision deadline: [When final decision needed]
                
                **DEFERRED DECISIONS**
                - [Decision postponed]
                  - Reason for deferral: [Why postponed]
                  - Review date: [When to revisit]
                  - Information needed: [What's required before deciding]
                
                **DECISION-MAKING PROCESS OBSERVATIONS**
                - Consensus level: [Strong agreement/Split opinion/No clear consensus]
                - Alternative options considered: [Other possibilities discussed]
                - Dissenting views: [Concerns or objections raised]
                
                **DECISIONS REQUIRING COMMUNICATION**
                - [Decisions that need to be communicated to others]
                  - Stakeholders to inform: [Who needs to know]
                  - Communication method: [How to inform them]
                  - Timeline for communication: [When to communicate]
            """.trimIndent()

            "meeting_minutes" -> """
                Create formal meeting minutes from this transcript with speaker identification:
                
                $transcript
                
                Include:
                - Meeting date and attendees (if mentioned)
                - Speaker identification based on speech patterns and context
                - Agenda items discussed with speaker contributions
                - Decisions made and who advocated for them
                - Action items with specific owners identified
                - Next steps and responsible parties
            """.trimIndent()

            "speaker_analysis" -> """
                Analyze speakers in this meeting transcript:
                
                $transcript
                
                Provide:
                - Speaker identification (Speaker A, B, C, etc. or names if mentioned)
                - Each speaker's main contributions and viewpoints
                - Speaking time estimation for each participant
                - Key statements and decisions attributed to each speaker
                - Speaking patterns and engagement levels
            """.trimIndent()

            else -> "Please summarize this meeting transcript: $transcript"
        }

        // Store the summary type for saving when AI responds
        pendingMeetingSummaryType = summaryType
        pendingMeetingTranscript = transcript

        // Send the prompt as a regular message to get AI processing
        processUserMessageSend(prompt)
        showCustomToast("Generating ${summaryType.replace("_", " ")}... (Will auto-save when complete)")
    }

    private fun saveMeetingSummary(summaryContent: String, summaryType: String, originalTranscript: String) {
        lifecycleScope.launch {
            try {
                val timestamp = System.currentTimeMillis()
                val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
                val formattedDate = dateFormat.format(Date(timestamp))

                val typeDisplay = summaryType.replace("_", " ").split(" ").joinToString(" ") {
                    it.replaceFirstChar { char -> if (char.isLowerCase()) char.titlecase(Locale.getDefault()) else char.toString() }
                }

                val fullContent = buildString {
                    appendLine("=".repeat(60))
                    appendLine("MEETING $typeDisplay".uppercase())
                    appendLine("Generated: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}")
                    appendLine("=".repeat(60))
                    appendLine()
                    appendLine(summaryContent)
                    appendLine()
                    appendLine("=".repeat(60))
                    appendLine("ORIGINAL TRANSCRIPT")
                    appendLine("=".repeat(60))
                    appendLine()
                    appendLine(originalTranscript)
                    appendLine()
                    appendLine("=".repeat(60))
                    appendLine("Generated by AI Chat Teacher")
                }

                val fileName = "Meeting_${typeDisplay.replace(" ", "_")}_${formattedDate}.txt"
                val success = saveMeetingFileToDocuments(fullContent, fileName)

                withContext(Dispatchers.Main) {
                    if (success) {
                        showCustomToast("$typeDisplay saved to Documents/Meeting Transcripts/")
                        showMeetingFileSavedDialog(fileName, typeDisplay.lowercase())
                    } else {
                        showCustomToast("Failed to save $typeDisplay")
                    }
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Log.e("ChatFragment", "Failed to save meeting summary", e)
                    showCustomToast("Failed to save summary: ${e.message}")
                }
            }
        }
    }

    private fun showFullTranscript(transcript: String) {
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("Meeting Transcript")
        builder.setMessage(transcript)
        builder.setPositiveButton("Copy to Chat") { _, _ ->
            binding.messageEditText.setText(transcript)
            showCustomToast("Transcript copied to message input")
        }
        builder.setNegativeButton("Close", null)
        builder.show()
    }

    private fun saveMeetingTranscript(transcript: String, meetingFile: File) {
        lifecycleScope.launch {
            try {
                val timestamp = System.currentTimeMillis()
                val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
                val formattedDate = dateFormat.format(Date(timestamp))

                // Save transcript to Documents/Meeting Transcripts folder
                val transcriptContent = buildString {
                    appendLine("=".repeat(50))
                    appendLine("MEETING TRANSCRIPT")
                    appendLine("Generated: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}")
                    appendLine("=".repeat(50))
                    appendLine()
                    appendLine(transcript)
                    appendLine()
                    appendLine("=".repeat(50))
                    appendLine("End of Transcript")
                    appendLine("Generated by AI Chat Teacher")
                }

                val fileName = "Meeting_Transcript_${formattedDate}.txt"
                val success = saveMeetingFileToDocuments(transcriptContent, fileName)

                withContext(Dispatchers.Main) {
                    if (success) {
                        showCustomToast("Transcript saved to Documents/Meeting Transcripts/")
                        showMeetingFileSavedDialog(fileName, "transcript")
                    } else {
                        showCustomToast("Failed to save transcript")
                    }
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Log.e("ChatFragment", "Failed to save meeting transcript", e)
                    showCustomToast("Failed to save transcript: ${e.message}")
                }
            }
        }
    }

    private fun saveMeetingFileToDocuments(content: String, fileName: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Use MediaStore for Android 10+
                val resolver = requireContext().contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOCUMENTS}/Meeting Transcripts")
                }

                val uri = resolver.insert(MediaStore.Files.getContentUri("external"), contentValues)
                uri?.let { fileUri ->
                    resolver.openOutputStream(fileUri)?.use { outputStream ->
                        outputStream.write(content.toByteArray())
                    }
                    true
                } ?: false

            } else {
                // For older Android versions
                val documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
                val meetingDir = File(documentsDir, "Meeting Transcripts")

                if (!meetingDir.exists()) {
                    meetingDir.mkdirs()
                }

                val file = File(meetingDir, fileName)
                file.writeText(content)

                // Notify media scanner
                val intent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
                intent.data = Uri.fromFile(file)
                requireContext().sendBroadcast(intent)

                true
            }
        } catch (e: Exception) {
            Log.e("ChatFragment", "Failed to save meeting file", e)
            false
        }
    }

    private fun showMeetingFileSavedDialog(fileName: String, fileType: String) {
        AlertDialog.Builder(requireContext())
            .setTitle("File Saved Successfully")
            .setMessage("Your meeting $fileType has been saved as:\n\n$fileName\n\nLocation: Documents/Meeting Transcripts/")
            .setPositiveButton("Share File") { _, _ ->
                shareMeetingFile(fileName, fileType)
            }
            .setNeutralButton("Export Options") { _, _ ->
                showMeetingExportDialog(fileName, fileType)
            }
            .setNegativeButton("Open Folder") { _, _ ->
                openMeetingFolder()
            }
            .show()
    }

    private fun shareMeetingFile(fileName: String, fileType: String) {
        try {
            // Get the file path
            val filePath = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // For Android 10+, need to get the content URI
                val resolver = requireContext().contentResolver
                val collection = MediaStore.Files.getContentUri("external")
                val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ?"
                val selectionArgs = arrayOf(fileName)

                var fileUri: Uri? = null
                resolver.query(collection, null, selection, selectionArgs, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                        val id = cursor.getLong(idColumn)
                        fileUri = ContentUris.withAppendedId(collection, id)
                    }
                }
                fileUri
            } else {
                // For older versions
                val documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
                val meetingDir = File(documentsDir, "Meeting Transcripts")
                val file = File(meetingDir, fileName)
                FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.fileprovider", file)
            }

            filePath?.let { uri ->
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "Meeting ${fileType.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }}: ${fileName.substringBeforeLast(".")}")
                    putExtra(Intent.EXTRA_TEXT, "Sharing meeting $fileType generated by AI Chat Teacher.\n\nFile: $fileName")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                startActivity(Intent.createChooser(shareIntent, "Share Meeting $fileType"))
            } ?: run {
                showCustomToast("Could not find file to share")
            }

        } catch (e: Exception) {
            Log.e("ChatFragment", "Error sharing meeting file", e)
            showCustomToast("Failed to share file: ${e.message}")
        }
    }

    private fun showMeetingExportDialog(fileName: String, fileType: String) {
        val exportOptions = arrayOf(
            "Export as PDF",
            "Export as Word Document",
            "Email as Attachment",
            "Copy to Clipboard",
            "Create Summary Report"
        )

        AlertDialog.Builder(requireContext())
            .setTitle("Export Meeting $fileType")
            .setMessage("Choose export format for $fileName:")
            .setItems(exportOptions) { _, which ->
                when (which) {
                    0 -> exportAsPDF(fileName, fileType)
                    1 -> exportAsWord(fileName, fileType)
                    2 -> emailAsAttachment(fileName, fileType)
                    3 -> copyToClipboard(fileName, fileType)
                    4 -> createSummaryReport(fileName, fileType)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun exportAsPDF(fileName: String, fileType: String) {
        showCustomToast("PDF export feature coming soon!")
        // TODO: Implement PDF generation using library like iText
    }

    private fun exportAsWord(fileName: String, fileType: String) {
        showCustomToast("Word export feature coming soon!")
        // TODO: Implement Word document generation
    }

    private fun emailAsAttachment(fileName: String, fileType: String) {
        try {
            val filePath = getMeetingFilePath(fileName)
            filePath?.let { uri ->
                val emailIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "message/rfc822"
                    putExtra(Intent.EXTRA_EMAIL, arrayOf(""))
                    putExtra(Intent.EXTRA_SUBJECT, "Meeting ${fileType.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }}: ${fileName.substringBeforeLast(".")}")
                    putExtra(Intent.EXTRA_TEXT, "Please find attached the meeting $fileType.\n\nGenerated by AI Chat Teacher")
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                startActivity(Intent.createChooser(emailIntent, "Send Email"))
            }
        } catch (e: Exception) {
            Log.e("ChatFragment", "Error emailing meeting file", e)
            showCustomToast("Failed to create email: ${e.message}")
        }
    }

    private fun copyToClipboard(fileName: String, fileType: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val content = readMeetingFileContent(fileName)
                withContext(Dispatchers.Main) {
                    val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("Meeting $fileType", content)
                    clipboard.setPrimaryClip(clip)
                    showCustomToast("Meeting $fileType copied to clipboard!")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Log.e("ChatFragment", "Error copying to clipboard", e)
                    showCustomToast("Failed to copy content: ${e.message}")
                }
            }
        }
    }

    private fun createSummaryReport(fileName: String, fileType: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val content = readMeetingFileContent(fileName)
                val summaryPrompt = """
                Create an executive summary report for this meeting content:
                
                $content
                
                Include:
                - Executive Summary (3-4 sentences)
                - Key Participants (if identifiable)
                - Main Topics Discussed
                - Decisions Made
                - Action Items
                - Next Steps
                
                Format as a professional report suitable for distribution.
                """

                withContext(Dispatchers.Main) {
                    processUserMessageSend(summaryPrompt)
                    showCustomToast("Generating executive summary report...")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Log.e("ChatFragment", "Error creating summary report", e)
                    showCustomToast("Failed to create summary: ${e.message}")
                }
            }
        }
    }

    private fun getMeetingFilePath(fileName: String): Uri? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = requireContext().contentResolver
                val collection = MediaStore.Files.getContentUri("external")
                val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ?"
                val selectionArgs = arrayOf(fileName)

                resolver.query(collection, null, selection, selectionArgs, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                        val id = cursor.getLong(idColumn)
                        return ContentUris.withAppendedId(collection, id)
                    }
                }
                null
            } else {
                val documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
                val meetingDir = File(documentsDir, "Meeting Transcripts")
                val file = File(meetingDir, fileName)
                if (file.exists()) {
                    FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.fileprovider", file)
                } else null
            }
        } catch (e: Exception) {
            Log.e("ChatFragment", "Error getting file path", e)
            null
        }
    }

    private fun readMeetingFileContent(fileName: String): String {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = requireContext().contentResolver
                val collection = MediaStore.Files.getContentUri("external")
                val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ?"
                val selectionArgs = arrayOf(fileName)

                resolver.query(collection, null, selection, selectionArgs, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                        val id = cursor.getLong(idColumn)
                        val uri = ContentUris.withAppendedId(collection, id)

                        resolver.openInputStream(uri)?.use { inputStream ->
                            return inputStream.bufferedReader().use { it.readText() }
                        }
                    }
                }
                throw Exception("File not found")
            } else {
                val documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
                val meetingDir = File(documentsDir, "Meeting Transcripts")
                val file = File(meetingDir, fileName)
                file.readText()
            }
        } catch (e: Exception) {
            throw Exception("Could not read file content: ${e.message}")
        }
    }

    private fun openMeetingFolder() {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                type = "resource/folder"
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    data = Uri.parse("content://com.android.externalstorage.documents/document/primary%3ADocuments%2FMeeting%20Transcripts")
                } else {
                    data = Uri.fromFile(File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "Meeting Transcripts"))
                }
            }
            startActivity(Intent.createChooser(intent, "Open Folder"))
        } catch (e: Exception) {
            // Fallback to general file manager
            try {
                val intent = Intent(Intent.ACTION_VIEW)
                intent.data = Uri.parse("content://com.android.externalstorage.documents/document/primary%3ADocuments")
                startActivity(intent)
            } catch (ex: Exception) {
                showCustomToast("Please check your Documents folder manually")
            }
        }
    }

    private fun selectMeetingFileForSummary() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "audio/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }

        try {
            startActivityForResult(Intent.createChooser(intent, "Select Meeting Recording"), REQUEST_CODE_MEETING_FILE)
        } catch (e: ActivityNotFoundException) {
            showCustomToast("No file manager available")
        }
    }

    /**
     * Initialize Realtime Voice Agent for speech-to-speech interactions
     */
    private fun initializeRealtimeVoiceAgent() {
        try {
            realtimeVoiceAgent = RealtimeVoiceAgent(requireContext())

            // Create voice agent callback
            voiceAgentCallback = object : RealtimeVoiceAgent.VoiceAgentCallback {
                override fun onStateChanged(newState: String) {
                    lifecycleScope.launch(Dispatchers.Main) {
                        updateVoiceAgentUI(newState)

                        // Handle AI speaking state changes with improved conflict prevention
                        when (newState) {
                            RealtimeVoiceAgent.STATE_SPEAKING -> {
                                // Only set AI as speaking if user is not currently speaking
                                if (!isUserCurrentlySpeaking()) {
                                    isAICurrentlySpeaking = true
                                    hasInterruptedCurrentResponse = false  // Reset interruption flag for new response
                                    hasTriggeredResponse = false  // Reset trigger flag for next user turn
                                    lastAiSpeakStartTime = System.currentTimeMillis()  // Track when AI started speaking
                                    resumeStreamingAudio() // Resume if it was paused
                                    Log.d("ChatFragment", "AI started speaking - ready for interruption (protection window: 1s)")
                                } else {
                                    // User is speaking, gently interrupt the AI response
                                    Log.d("ChatFragment", "AI tried to speak while user is speaking - gentle interrupt")
                                    realtimeVoiceAgent?.interrupt()
                                    pauseStreamingAudio() // Pause instead of destroying
                                    isAICurrentlySpeaking = false
                                    hasInterruptedCurrentResponse = true
                                    lastInterruptTime = System.currentTimeMillis()
                                }
                            }
                            RealtimeVoiceAgent.STATE_LISTENING -> {
                                isAICurrentlySpeaking = false
                                hasInterruptedCurrentResponse = false  // Reset for next conversation turn
                                Log.d("ChatFragment", "AI finished speaking - microphone input resumed")
                            }
                            RealtimeVoiceAgent.STATE_THINKING -> {
                                // AI is processing - not speaking yet, but might start soon
                                Log.d("ChatFragment", "AI is thinking...")
                            }
                            RealtimeVoiceAgent.STATE_INTERRUPTED -> {
                                // User interrupted AI - pause audio instead of destroying it
                                pauseStreamingAudio()
                                isAICurrentlySpeaking = false
                                hasInterruptedCurrentResponse = true  // Mark as already interrupted
                                Log.d("ChatFragment", "User interrupted AI - paused audio")
                            }
                        }
                    }
                }

                override fun onAudioReceived(audioData: ByteArray) {
                    val userSpeaking = isUserCurrentlySpeaking()
                    val shouldPlayAudio = !userSpeaking && !hasInterruptedCurrentResponse

                    if (shouldPlayAudio) {
                        playRealtimeAudio(audioData)
                        Log.d("ChatFragment", "Playing AI audio (${audioData.size} bytes)")
                    } else {
                        Log.d("ChatFragment", "Blocking AI audio - User speaking: $userSpeaking, Already interrupted: $hasInterruptedCurrentResponse")

                        // If user is speaking and we haven't interrupted yet, trigger interruption
                        if (userSpeaking && !hasInterruptedCurrentResponse && isAICurrentlySpeaking) {
                            Log.d("ChatFragment", "Triggering interruption due to user speech")
                            realtimeVoiceAgent?.interrupt()
                            pauseStreamingAudio() // Gentle pause instead of destroying
                            hasInterruptedCurrentResponse = true
                            lastInterruptTime = System.currentTimeMillis()
                            isAICurrentlySpeaking = false
                        }
                    }
                }

                override fun onTranscriptReceived(transcript: String, isUser: Boolean) {
                    lifecycleScope.launch(Dispatchers.Main) {
                        displayRealtimeTranscript(transcript, isUser)
                    }
                }

                override fun onError(error: String) {
                    lifecycleScope.launch(Dispatchers.Main) {
                        // Filter out expected cancellation errors to reduce noise
                        if (!error.contains("Cancellation failed: no active response found")) {
                            // Check if fragment is still attached before showing toast
                            if (isAdded && context != null) {
                                showCustomToast("Voice Agent Error: $error")
                            }
                            Log.e("ChatFragment", "Realtime Voice Agent Error: $error")
                        } else {
                            // Just log cancellation errors without showing toasts
                            Log.d("ChatFragment", "Expected cancellation: $error")
                        }
                    }
                }

                override fun onAgentHandoff(newAgent: String) {
                    lifecycleScope.launch(Dispatchers.Main) {
                        if (isAdded && context != null) {
                            showCustomToast("Transferring to $newAgent...")
                        }
                        handleAgentHandoff(newAgent)
                    }
                }

                override fun onGuardrailTripped(reason: String) {
                    lifecycleScope.launch(Dispatchers.Main) {
                        if (isAdded && context != null) {
                            showCustomToast("Response filtered: $reason")
                        }
                        Log.w("ChatFragment", "Guardrail tripped: $reason")
                    }
                }
            }

            Log.d("ChatFragment", "Realtime Voice Agent initialized successfully")
        } catch (e: Exception) {
            Log.e("ChatFragment", "Failed to initialize Realtime Voice Agent", e)
            showCustomToast("Voice agent initialization failed")
        }
    }

    /**
     * Create default voice agent configurations
     */
    private fun createDefaultVoiceAgents(): Map<String, RealtimeVoiceAgent.VoiceAgentConfig> {
        return try {
            Log.d("ChatFragment", "Creating default voice agents...")

            val agents = mapOf(
                "general_assistant" to RealtimeVoiceAgent.VoiceAgentConfig(
                    name = "🤖 AI Chat Assistant",
                    instructions = "You are a helpful AI assistant. Answer questions accurately and conversationally.",
                    personality = RealtimeVoiceAgent.AgentPersonality(
                        identity = "You are a knowledgeable and friendly AI assistant",
                        demeanor = "helpful and engaging",
                        tone = "warm and conversational",
                        enthusiasm = "medium",
                        formality = "professional",
                        emotion = "empathetic",
                        fillerWords = "occasionally",
                        pacing = "natural",
                        voiceModel = "alloy"
                    )
                ),

                "meeting_specialist" to RealtimeVoiceAgent.VoiceAgentConfig(
                    name = "📋 Meeting Specialist",
                    instructions = "You specialize in meeting analysis, note-taking, and action item extraction. Help users record, transcribe, and summarize meetings effectively.",
                    personality = RealtimeVoiceAgent.AgentPersonality(
                        identity = "You are a professional meeting assistant with expertise in business communication",
                        demeanor = "organized and professional",
                        tone = "business-appropriate and clear",
                        enthusiasm = "medium",
                        formality = "professional",
                        emotion = "neutral",
                        fillerWords = "none",
                        pacing = "natural",
                        voiceModel = "nova"
                    ),
                    tools = createMeetingToolsSafely()
                ),

                "educational_tutor" to RealtimeVoiceAgent.VoiceAgentConfig(
                    name = "🎓 Educational Tutor",
                    instructions = "You are an expert tutor helping students learn. Break down complex concepts, provide examples, and encourage learning.",
                    personality = RealtimeVoiceAgent.AgentPersonality(
                        identity = "You are a patient and knowledgeable tutor",
                        demeanor = "encouraging and supportive",
                        tone = "friendly and educational",
                        enthusiasm = "high",
                        formality = "casual",
                        emotion = "encouraging",
                        fillerWords = "occasionally",
                        pacing = "slow",
                        voiceModel = "shimmer"
                    ),
                    handoffAgents = listOf("general_assistant", "meeting_specialist")
                )
            )

            Log.d("ChatFragment", "Successfully created ${agents.size} voice agents")
            agents

        } catch (e: Exception) {
            Log.e("ChatFragment", "Error creating voice agents", e)
            // Return minimal fallback agents
            mapOf(
                "general_assistant" to RealtimeVoiceAgent.VoiceAgentConfig(
                    name = "🤖 AI Assistant",
                    instructions = "You are a helpful AI assistant."
                )
            )
        }
    }

    /**
     * Create meeting tools safely without throwing exceptions
     */
    private fun createMeetingToolsSafely(): List<RealtimeVoiceAgent.VoiceAgentTool> {
        return try {
            listOf(
                createMeetingTranscriptionTool(),
                createActionItemExtractionTool(),
                createMeetingSummaryTool()
            )
        } catch (e: Exception) {
            Log.e("ChatFragment", "Error creating meeting tools, using empty list", e)
            emptyList()
        }
    }

    /**
     * Create meeting transcription tool
     */
    private fun createMeetingTranscriptionTool(): RealtimeVoiceAgent.VoiceAgentTool {
        return RealtimeVoiceAgent.VoiceAgentTool(
            name = "transcribe_meeting",
            description = "Transcribe and analyze meeting audio with speaker identification",
            parameters = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "meeting_type" to mapOf(
                        "type" to "string",
                        "description" to "Type of meeting (team, client, interview, etc.)"
                    )
                )
            )
        ) { args, context ->
            val meetingType = args["meeting_type"] as? String ?: "general"
            "Meeting transcription started for $meetingType meeting. I will provide real-time transcription with speaker identification and key point extraction."
        }
    }

    /**
     * Create action item extraction tool
     */
    private fun createActionItemExtractionTool(): RealtimeVoiceAgent.VoiceAgentTool {
        return RealtimeVoiceAgent.VoiceAgentTool(
            name = "extract_action_items",
            description = "Extract action items from meeting conversation",
            parameters = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "priority" to mapOf(
                        "type" to "string",
                        "enum" to listOf("high", "medium", "low"),
                        "description" to "Priority filter for action items"
                    )
                )
            )
        ) { args, context ->
            val priority = args["priority"] as? String ?: "all"
            val history = context.history

            // Analyze conversation history for action items
            val actionItems = extractActionItemsFromHistory(history, priority)
            "Found ${actionItems.size} action items with $priority priority: $actionItems"
        }
    }

    /**
     * Create meeting summary tool
     */
    private fun createMeetingSummaryTool(): RealtimeVoiceAgent.VoiceAgentTool {
        return RealtimeVoiceAgent.VoiceAgentTool(
            name = "generate_meeting_summary",
            description = "Generate comprehensive meeting summary with key insights",
            parameters = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "format" to mapOf(
                        "type" to "string",
                        "enum" to listOf("executive", "detailed", "action_focused"),
                        "description" to "Summary format type"
                    )
                )
            )
        ) { args, context ->
            val format = args["format"] as? String ?: "executive"
            val history = context.history

            generateMeetingSummaryFromHistory(history, format)
        }
    }

    /**
     * Extract action items from conversation history
     */
    private fun extractActionItemsFromHistory(history: List<RealtimeVoiceAgent.VoiceMessage>, priority: String): List<String> {
        val actionItems = mutableListOf<String>()


        history.forEach { message ->
            val content = message.content.lowercase()
            if (content.contains("action item") || content.contains("todo") || content.contains("follow up")) {
                actionItems.add(message.content)
            }
        }

        return actionItems
    }

    /**
     * Generate meeting summary from history
     */
    private fun generateMeetingSummaryFromHistory(history: List<RealtimeVoiceAgent.VoiceMessage>, format: String): String {
        val userMessages = history.filter { it.isUser }
        val aiMessages = history.filter { !it.isUser }

        return when (format) {
            "executive" -> "Executive Summary: Meeting covered ${userMessages.size} main topics with ${aiMessages.size} responses. Key decisions and action items identified."
            "detailed" -> "Detailed Summary: Comprehensive discussion with full transcript and analysis available."
            "action_focused" -> "Action Items: ${extractActionItemsFromHistory(history, "all").size} items identified for follow-up."
            else -> "Meeting summary generated successfully."
        }
    }

    /**
     * Start realtime voice conversation
     */
    private fun startRealtimeVoiceChat(agentType: String = "general_assistant") {
        Log.d("ChatFragment", "🎯 startRealtimeVoiceChat called with agentType: $agentType")
        // Check microphone permission first
        if (!checkAndRequestAudioPermission(REQUEST_RECORD_AUDIO_PERMISSION)) {
            Log.w("ChatFragment", "🔒 Audio permission not granted, storing agent type: $agentType")
            // Permission not granted, store agent type for later and request permission
            pendingVoiceAgentType = agentType
            return
        }
        Log.d("ChatFragment", "✅ Audio permission already granted, proceeding with live voice chat")

        lifecycleScope.launch {
            try {
                val agents = createDefaultVoiceAgents()
                val selectedAgent = agents[agentType] ?: agents["general_assistant"]!!

                currentVoiceAgent = selectedAgent
                realtimeVoiceAgent?.initializeAgent(selectedAgent, voiceAgentCallback!!)

                showLoadingOverlay(true)
                if (isAdded && context != null) {
                    showCustomToast("Connecting to voice agent...")
                }

                // Try connecting with retry logic
                var connected = false
                for (attempt in 0..2) {
                    connected = realtimeVoiceAgent?.connectToRealtime(retryCount = attempt) == true
                    if (connected) break

                    if (attempt < 2) {
                        Log.d("ChatFragment", "Connection attempt ${attempt + 1} failed, retrying...")
                        kotlinx.coroutines.delay(1000L * (attempt + 1)) // Progressive delay
                    }
                }

                withContext(Dispatchers.Main) {
                    showLoadingOverlay(false)
                    if (connected) {
                        Log.d("ChatFragment", "🎉 LIVE VOICE CHAT CONNECTED! Setting isRealtimeMode = true")
                        isRealtimeMode = true
                        if (isAdded && context != null) {
                            showCustomToast("Voice agent ready! Start talking...")
                        }
                        updateRealtimeUI(true)

                        // Configure audio mode for voice communication
                        setupVoiceCommunicationMode()

                        // Start microphone recording for voice input
                        startVoiceRecording()
                    } else {
                        Log.e("ChatFragment", "❌ LIVE VOICE CHAT CONNECTION FAILED! Falling back to TTS mode")
                        if (isAdded && context != null) {
                            showCustomToast("Failed to connect to voice agent after multiple attempts")
                        }
                        Log.e("ChatFragment", "Failed to connect to voice agent after 3 attempts - isRealtimeMode remains false")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showLoadingOverlay(false)
                    if (isAdded && context != null) {
                        showCustomToast("Error starting voice chat: ${e.message}")
                    }
                    Log.e("ChatFragment", "Error starting realtime voice chat", e)
                }
            }
        }
    }

    /**
     * Setup voice communication mode for echo cancellation
     */
    private fun setupVoiceCommunicationMode() {
        try {
            val audioManager = requireContext().getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
            
            // Store original mode to restore later
            originalAudioMode = audioManager.mode
            
            // Set mode to voice communication for better echo cancellation
            audioManager.mode = android.media.AudioManager.MODE_IN_COMMUNICATION
            
            // Enable speakerphone for hands-free operation
            audioManager.isSpeakerphoneOn = true
            
            // Disable microphone mute
            audioManager.isMicrophoneMute = false
            
            Log.d("ChatFragment", "Audio mode set to voice communication with speakerphone enabled")
        } catch (e: Exception) {
            Log.w("ChatFragment", "Failed to setup voice communication mode: ${e.message}")
            // Continue without special audio mode - not critical
        }
    }

    /**
     * Start voice recording for real-time voice agents
     */
    private fun startVoiceRecording() {
        try {
            Log.d("ChatFragment", "Starting voice recording for realtime agent")

            // Check microphone permission
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
                Log.e("ChatFragment", "Microphone permission not granted")
                showCustomToast("Microphone permission required for voice chat")
                return
            }

            // Initialize AudioRecord for capturing user's voice
            val sampleRate = 24000 // Match OpenAI Realtime API format
            val channelConfig = android.media.AudioFormat.CHANNEL_IN_MONO
            val audioFormat = android.media.AudioFormat.ENCODING_PCM_16BIT

            val bufferSize = android.media.AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            if (bufferSize == android.media.AudioRecord.ERROR_BAD_VALUE ||
                bufferSize == android.media.AudioRecord.ERROR) {
                Log.e("ChatFragment", "Invalid buffer size for AudioRecord")
                showCustomToast("Audio recording setup failed")
                return
            }

            voiceRecordingJob = lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val audioRecord = android.media.AudioRecord(
                        android.media.MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                        sampleRate,
                        channelConfig,
                        audioFormat,
                        bufferSize
                    )

                    if (audioRecord.state != android.media.AudioRecord.STATE_INITIALIZED) {
                        withContext(Dispatchers.Main) {
                            Log.e("ChatFragment", "AudioRecord initialization failed")
                            showCustomToast("Microphone initialization failed")
                        }
                        return@launch
                    }

                    // Enable echo cancellation if available
                    try {
                        if (android.media.audiofx.AcousticEchoCanceler.isAvailable()) {
                            val aec = android.media.audiofx.AcousticEchoCanceler.create(audioRecord.audioSessionId)
                            aec?.let { echoCanceler ->
                                echoCanceler.enabled = true
                                Log.d("ChatFragment", "Acoustic Echo Cancellation enabled")
                            }
                        } else {
                            Log.w("ChatFragment", "Acoustic Echo Cancellation not available on this device")
                        }

                        // Enable noise suppression if available
                        if (android.media.audiofx.NoiseSuppressor.isAvailable()) {
                            val noiseSuppressor = android.media.audiofx.NoiseSuppressor.create(audioRecord.audioSessionId)
                            noiseSuppressor?.let { suppressor ->
                                suppressor.enabled = true
                                Log.d("ChatFragment", "Noise Suppression enabled")
                            }
                        }

                        // Enable automatic gain control if available
                        if (android.media.audiofx.AutomaticGainControl.isAvailable()) {
                            val agc = android.media.audiofx.AutomaticGainControl.create(audioRecord.audioSessionId)
                            agc?.let { gainControl ->
                                gainControl.enabled = true
                                Log.d("ChatFragment", "Automatic Gain Control enabled")
                            }
                        }
                    } catch (e: Exception) {
                        Log.w("ChatFragment", "Error setting up audio effects: ${e.message}")
                        // Continue without effects - not critical
                    }

                    audioRecord.startRecording()
                    Log.d("ChatFragment", "AudioRecord started with echo cancellation, streaming audio to voice agent")

                    val buffer = ByteArray(bufferSize / 4) // Use smaller chunks for real-time streaming

                    while (isRealtimeMode && voiceRecordingJob?.isActive == true) {
                        val bytesRead = audioRecord.read(buffer, 0, buffer.size)

                        if (bytesRead > 0) {
                            val audioData = buffer.copyOfRange(0, bytesRead)

                            // Enhanced voice activity detection based on audio levels
                            val currentTime = System.currentTimeMillis()
                            val audioLevel = calculateAudioLevel(audioData)
                            updateVoiceActivityDetection(audioLevel, currentTime)

                            // EXTREMELY conservative interruption detection to prevent AI self-interruption
                            if (isAICurrentlySpeaking && !hasInterruptedCurrentResponse && userSpeechDetected) {
                                // Much longer debounce: 5 seconds since last interrupt to let AI complete responses
                                if (currentTime - lastInterruptTime > 5000) {
                                    // Additional check: Only interrupt if the AI has been speaking for at least 1 second
                                    // This prevents interrupting at the very start of AI responses
                                    if (currentTime - lastAiSpeakStartTime > 1000) {
                                        // FINAL CHECK: Only interrupt on EXTREMELY loud user speech (triple the threshold)
                                        if (audioLevel > audioLevelThreshold * 3.0) {
                                            Log.d("ChatFragment", "STRONG user speech detected (level: $audioLevel > ${audioLevelThreshold * 3.0}) - interrupting AI (last interrupt: ${(currentTime - lastInterruptTime)}ms ago, AI speaking for: ${(currentTime - lastAiSpeakStartTime)}ms)")
                                            // Gentle interruption - pause AudioTrack instead of destroying it
                                            pauseStreamingAudio()
                                            realtimeVoiceAgent?.interrupt()
                                            hasInterruptedCurrentResponse = true
                                            lastInterruptTime = currentTime
                                            isAICurrentlySpeaking = false
                                        } else {
                                            Log.d("ChatFragment", "Weak speech signal (level: $audioLevel <= ${audioLevelThreshold * 3.0}) - ignoring to prevent false interruption")
                                        }
                                    } else {
                                        Log.d("ChatFragment", "Ignoring potential interruption - AI just started speaking (${(currentTime - lastAiSpeakStartTime)}ms ago)")
                                    }
                                } else {
                                    Log.d("ChatFragment", "Debouncing interruption - too soon after last interrupt (${(currentTime - lastInterruptTime)}ms ago)")
                                }
                            }

                            // Send audio data to RealtimeVoiceAgent
                            realtimeVoiceAgent?.sendAudio(audioData)

                            // Log periodically to avoid spam but include useful info (every ~1 second)
                            if (currentTime % 1000 < 50) {
                                val thresholdInfo = if (audioLevel > audioLevelThreshold) "ABOVE" else "below"
                                Log.d("ChatFragment", "Sent $bytesRead bytes to voice agent (audio level: $audioLevel $thresholdInfo threshold $audioLevelThreshold, user speaking: $userSpeechDetected, loud samples: $consecutiveLoudSamples)")
                            }
                        } else if (bytesRead < 0) {
                            Log.w("ChatFragment", "AudioRecord read error: $bytesRead")
                            break
                        }

                        // Small delay to prevent excessive CPU usage
                        kotlinx.coroutines.delay(10L)
                    }

                    // Cleanup
                    audioRecord.stop()
                    audioRecord.release()
                    Log.d("ChatFragment", "Voice recording stopped and AudioRecord released")

                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Log.e("ChatFragment", "Error in voice recording", e)
                        showCustomToast("Voice recording error: ${e.message}")
                    }
                }
            }

        } catch (e: Exception) {
            Log.e("ChatFragment", "Error starting voice recording", e)
            showCustomToast("Failed to start voice recording")
        }
    }

    /**
     * Stop voice recording
     */
    private fun stopVoiceRecording() {
        try {
            Log.d("ChatFragment", "Stopping voice recording")
            voiceRecordingJob?.cancel()
            voiceRecordingJob = null
        } catch (e: Exception) {
            Log.e("ChatFragment", "Error stopping voice recording", e)
        }
    }

    /**
     * Calculate audio level from PCM16 audio data
     */
    private fun calculateAudioLevel(audioData: ByteArray): Int {
        if (audioData.size < 2) return 0

        var sum = 0L
        var sampleCount = 0

        // Process PCM16 data (2 bytes per sample)
        for (i in 0 until audioData.size - 1 step 2) {
            val sample = (audioData[i].toInt() and 0xFF) or ((audioData[i + 1].toInt() and 0xFF) shl 8)
            val signedSample = if (sample > 32767) sample - 65536 else sample
            sum += kotlin.math.abs(signedSample)
            sampleCount++
        }

        return if (sampleCount > 0) (sum / sampleCount).toInt() else 0
    }

    /**
     * Update voice activity detection based on audio levels
     */
    private fun updateVoiceActivityDetection(audioLevel: Int, currentTime: Long) {
        // Update timing
        lastAudioLevelCheck = currentTime

        if (audioLevel > audioLevelThreshold) {
            consecutiveLoudSamples++
            consecutiveQuietSamples = 0

            // Require fewer consecutive samples for faster speech detection
            if (consecutiveLoudSamples >= 6) { // Reduced from 12 to 6 samples for better responsiveness
                if (!userSpeechDetected) {
                    // First detection of user speech
                    lastSpeechDetectedTime = currentTime
                    hasTriggeredResponse = false
                    Log.d("ChatFragment", "Speech detected! Audio level: $audioLevel > $audioLevelThreshold, loud samples: $consecutiveLoudSamples")
                }
                userSpeechDetected = true
            }
        } else {
            consecutiveQuietSamples++
            consecutiveLoudSamples = 0

            // Require fewer consecutive quiet samples for faster response triggering
            if (consecutiveQuietSamples >= 20) { // Increased from 15 to 20 samples (~200ms of quiet) for more stable detection
                // User stopped speaking - trigger response if OpenAI's VAD didn't
                if (userSpeechDetected && !hasTriggeredResponse && !isAICurrentlySpeaking) {
                    val speechDuration = currentTime - lastSpeechDetectedTime
                    // Ensure robust minimum speech duration and cooldown - OpenAI needs substantial audio buffer
                    if (speechDuration > 2000 && currentTime - lastManualTriggerTime > 8000) { // Further increased requirements for reliable buffer
                        Log.w("ChatFragment", "Fallback VAD: User spoke for ${speechDuration}ms but OpenAI VAD didn't trigger - preparing manual response trigger")
                        // Add substantial delay to ensure OpenAI's buffer has sufficient audio data
                        lifecycleScope.launch {
                            // Wait longer to ensure the audio buffer has enough data (OpenAI requires at least 100ms, we ensure much more)
                            kotlinx.coroutines.delay(750) // Increased from 500ms to 750ms for even better buffer accumulation
                            
                            // Additional check: ensure user is still not speaking before committing
                            if (!userSpeechDetected && !isAICurrentlySpeaking) {
                                Log.w("ChatFragment", "🔄 FALLBACK VAD: Committing audio buffer (speech duration: ${speechDuration}ms, buffer wait: 750ms)")
                                realtimeVoiceAgent?.commitAudioAndTriggerResponse()
                            } else {
                                Log.d("ChatFragment", "Cancelled fallback trigger - user resumed speaking or AI started responding")
                            }
                        }
                        hasTriggeredResponse = true
                        lastManualTriggerTime = currentTime
                    } else {
                        Log.d("ChatFragment", "User stopped speaking but not triggering: duration=${speechDuration}ms (need >2000ms), lastTrigger=${currentTime - lastManualTriggerTime}ms ago (need >8000ms)")
                    }
                }
                userSpeechDetected = false
            }
        }
    }

    /**
     * Check if user is currently speaking (voice activity detection)
     */
    private fun isUserCurrentlySpeaking(): Boolean {
        // Enhanced voice activity detection with more robust checks
        val currentTime = System.currentTimeMillis()
        val recentlyInterrupted = (currentTime - lastInterruptTime) < 2000 // Extended to 2 seconds

        // Primary check: voice recording must be active and in realtime mode
        val basicConditions = voiceRecordingJob?.isActive == true && isRealtimeMode

        // Enhanced logic: Use the more accurate userSpeechDetected flag
        val isCurrentlySpeaking = basicConditions && userSpeechDetected && !recentlyInterrupted

        // Add logging for debugging
        if (isCurrentlySpeaking && isAICurrentlySpeaking) {
            Log.d("ChatFragment", "Concurrent speech detected - User: $isCurrentlySpeaking (speech detected: $userSpeechDetected), AI: $isAICurrentlySpeaking")
        }

        return isCurrentlySpeaking
    }

    /**
     * Stop all currently playing AI audio to prevent overlap
     */
    private fun stopAllAIAudio() {
        audioTrackLock.lock()
        try {
            Log.d("ChatFragment", "Stopping ${currentAudioTracks.size} active audio tracks")
            currentAudioTracks.forEach { audioTrack ->
                try {
                    // Check state before stopping to avoid IllegalStateException
                    if (audioTrack.state == android.media.AudioTrack.STATE_INITIALIZED &&
                        audioTrack.playState == android.media.AudioTrack.PLAYSTATE_PLAYING) {
                        audioTrack.stop()
                    }
                    // Always try to release, but catch any exceptions
                    if (audioTrack.state != android.media.AudioTrack.STATE_UNINITIALIZED) {
                        audioTrack.release()
                    }
                } catch (e: Exception) {
                    Log.w("ChatFragment", "Error stopping audio track: ${e.message}")
                }
            }
            currentAudioTracks.clear()

            // Also stop streaming audio track
            val track = streamingAudioTrack
            if (track != null) {
                try {
                    if (track.state == android.media.AudioTrack.STATE_INITIALIZED &&
                        track.playState == android.media.AudioTrack.PLAYSTATE_PLAYING) {
                        track.stop()
                    }
                    track.release()
                    streamingAudioTrack = null
                    Log.d("ChatFragment", "Stopped and released streaming AudioTrack")
                } catch (e: Exception) {
                    Log.e("ChatFragment", "Error stopping streaming AudioTrack", e)
                }
            }
        } finally {
            audioTrackLock.unlock()
        }
    }

    /**
     * Pause streaming audio without destroying the AudioTrack
     */
    private fun pauseStreamingAudio() {
        audioTrackLock.lock()
        try {
            val track = streamingAudioTrack
            if (track != null) {
                try {
                    if (track.state == android.media.AudioTrack.STATE_INITIALIZED &&
                        track.playState == android.media.AudioTrack.PLAYSTATE_PLAYING) {
                        track.flush() // Clear any pending data to prevent buffer issues
                        track.pause()
                        Log.d("ChatFragment", "Paused and flushed streaming AudioTrack")
                    } else {
                        Log.d("ChatFragment", "AudioTrack not in playing state, cannot pause (state: ${track.state}, playState: ${track.playState})")
                    }
                } catch (e: Exception) {
                    Log.e("ChatFragment", "Error pausing streaming AudioTrack", e)
                    // If pause fails, stop and recreate to prevent crashes
                    try {
                        track.stop()
                        track.release()
                        streamingAudioTrack = null
                        Log.w("ChatFragment", "Released AudioTrack due to pause error")
                    } catch (releaseError: Exception) {
                        Log.e("ChatFragment", "Error releasing AudioTrack", releaseError)
                    }
                }
            }
        } finally {
            audioTrackLock.unlock()
        }
    }

    /**
     * Resume streaming audio
     */
    private fun resumeStreamingAudio() {
        audioTrackLock.lock()
        try {
            val track = streamingAudioTrack
            if (track != null) {
                try {
                    if (track.state == android.media.AudioTrack.STATE_INITIALIZED) {
                        when (track.playState) {
                            android.media.AudioTrack.PLAYSTATE_PAUSED -> {
                                track.play()
                                Log.d("ChatFragment", "Resumed streaming AudioTrack from paused state")
                            }
                            android.media.AudioTrack.PLAYSTATE_STOPPED -> {
                                track.play()
                                Log.d("ChatFragment", "Started streaming AudioTrack from stopped state")
                            }
                            android.media.AudioTrack.PLAYSTATE_PLAYING -> {
                                Log.d("ChatFragment", "AudioTrack already playing")
                            }
                            else -> {
                                Log.d("ChatFragment", "AudioTrack in unknown state: ${track.playState}")
                            }
                        }
                    } else {
                        Log.d("ChatFragment", "AudioTrack not initialized, cannot resume (state: ${track.state})")
                    }
                } catch (e: Exception) {
                    Log.e("ChatFragment", "Error resuming streaming AudioTrack", e)
                    // If resume fails, release and recreate will happen automatically
                    try {
                        track.stop()
                        track.release()
                        streamingAudioTrack = null
                        Log.w("ChatFragment", "Released AudioTrack due to resume error")
                    } catch (releaseError: Exception) {
                        Log.e("ChatFragment", "Error releasing AudioTrack", releaseError)
                    }
                }
            }
        } finally {
            audioTrackLock.unlock()
        }
    }

    /**
     * Stop realtime voice conversation
     */
    private fun stopRealtimeVoiceChat() {
        stopVoiceRecording()
        stopAllAIAudio()  // Stop any playing AI audio
        
        // Clear audio fallback queue
        synchronized(audioChunkQueue) {
            audioChunkQueue.clear()
            isProcessingAudioQueue = false
            Log.d("ChatFragment", "Cleared audio fallback queue")
        }
        
        // Reset audio mode flags
        isAudioTrackMode = true
        consecutiveAudioTrackFailures = 0
        
        realtimeVoiceAgent?.disconnect()
        isRealtimeMode = false
        isAICurrentlySpeaking = false
        hasInterruptedCurrentResponse = false  // Reset interruption state

        // Reset voice activity detection variables
        userSpeechDetected = false
        consecutiveQuietSamples = 0
        consecutiveLoudSamples = 0
        lastAudioLevelCheck = 0L

        // Restore original audio mode
        restoreOriginalAudioMode()

        updateRealtimeUI(false)
        showCustomToast("Voice chat stopped")
    }

    /**
     * Restore original audio mode after voice chat
     */
    private fun restoreOriginalAudioMode() {
        try {
            val audioManager = requireContext().getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
            
            // Restore original audio mode
            audioManager.mode = originalAudioMode
            
            // Restore speakerphone setting (turn off for normal mode)
            audioManager.isSpeakerphoneOn = false
            
            Log.d("ChatFragment", "Audio mode restored to original settings")
        } catch (e: Exception) {
            Log.w("ChatFragment", "Failed to restore original audio mode: ${e.message}")
            // Not critical - system will reset on app restart
        }
    }

    /**
     * Handle agent handoff
     */
    private fun handleAgentHandoff(newAgentType: String) {
        lifecycleScope.launch {
            try {
                val agents = createDefaultVoiceAgents()
                val newAgent = agents[newAgentType]

                if (newAgent != null) {
                    realtimeVoiceAgent?.updateAgent(newAgent)
                    currentVoiceAgent = newAgent
                    showCustomToast("Switched to ${newAgent.name}")
                } else {
                    showCustomToast("Agent $newAgentType not available")
                }
            } catch (e: Exception) {
                Log.e("ChatFragment", "Error in agent handoff", e)
                showCustomToast("Failed to switch agents")
            }
        }
    }

    /**
     * Update UI for realtime voice mode
     */
    private fun updateRealtimeUI(enabled: Boolean) {
        binding.apply {
            if (enabled) {
                messageEditText.hint = "Voice mode active - speak naturally"
                sendButton.text = "Stop Voice"
                sendButton.setOnClickListener { stopRealtimeVoiceChat() }
            } else {
                messageEditText.hint = "Type or use MIC button for voice..."
                sendButton.text = "Send"
                sendButton.setOnClickListener {
                    val userMessage = binding.messageEditText.text.toString().trim()
                    if (userMessage.isNotEmpty()) {
                        processUserMessageSend(userMessage)
                    }
                }
            }
        }
    }

    /**
     * Update UI based on voice agent state
     */
    private fun updateVoiceAgentUI(state: String) {
        val statusText = when (state) {
            RealtimeVoiceAgent.STATE_IDLE -> "Ready"
            RealtimeVoiceAgent.STATE_LISTENING -> "Listening..."
            RealtimeVoiceAgent.STATE_THINKING -> "Thinking..."
            RealtimeVoiceAgent.STATE_SPEAKING -> "Speaking..."
            RealtimeVoiceAgent.STATE_INTERRUPTED -> "Interrupted"
            else -> state
        }

        // Update status in UI if needed
        // Could add a status indicator to the top bar
        Log.d("ChatFragment", "Voice Agent State: $statusText")
    }

    /**
     * Display realtime transcript
     */
    private fun displayRealtimeTranscript(transcript: String, isUser: Boolean) {
        val message = ChatMessage(
            id = "realtime_${System.currentTimeMillis()}",
            content = transcript,
            isUser = isUser,
            isTyping = false,
            timestamp = System.currentTimeMillis()
        )

        chatMessages.add(message)
        chatAdapter.notifyItemInserted(chatMessages.size - 1)
        binding.recyclerView.smoothScrollToPosition(chatMessages.size - 1)
    }

    // Single streaming AudioTrack for realtime audio (declared above)

    /**
     * Play realtime audio from AI using thread-safe streaming AudioTrack
     * Uses proper synchronization to prevent buffer corruption and crashes
     */
    private fun playRealtimeAudio(audioData: ByteArray) {
        try {
            Log.d("ChatFragment", "Received audio data: ${audioData.size} bytes")

            // Check if we should use MediaPlayer-only mode
            if (!isAudioTrackMode) {
                Log.d("ChatFragment", "Using MediaPlayer-only mode for audio playback")
                playRealtimeAudioFallback(audioData)
                return
            }
            
            // CRITICAL FIX: If MediaPlayer is currently processing, queue this audio there instead
            if (isProcessingAudioQueue || audioChunkQueue.isNotEmpty()) {
                Log.d("ChatFragment", "MediaPlayer queue active - routing audio to fallback to prevent dual playback")
                playRealtimeAudioFallback(audioData)
                return
            }

            // OpenAI Realtime API sends PCM16 audio at 24kHz mono
            val sampleRate = 24000
            val channelConfig = android.media.AudioFormat.CHANNEL_OUT_MONO
            val audioFormat = android.media.AudioFormat.ENCODING_PCM_16BIT

            // Use synchronized block to prevent concurrent access
            audioTrackLock.lock()
            try {
                // Validate and create AudioTrack if needed
                if (streamingAudioTrack == null || 
                    streamingAudioTrack?.state == android.media.AudioTrack.STATE_UNINITIALIZED) {
                    
                    // Clean up any existing track first
                    streamingAudioTrack?.release()
                    streamingAudioTrack = null
                    
                    val bufferSize = android.media.AudioTrack.getMinBufferSize(
                        sampleRate, channelConfig, audioFormat
                    )

                    if (bufferSize <= 0) {
                        Log.e("ChatFragment", "Invalid buffer size: $bufferSize")
                        return
                    }

                    streamingAudioTrack = android.media.AudioTrack.Builder()
                        .setAudioAttributes(
                            android.media.AudioAttributes.Builder()
                                .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                                .setFlags(android.media.AudioAttributes.FLAG_LOW_LATENCY)
                                .build()
                        )
                        .setAudioFormat(
                            android.media.AudioFormat.Builder()
                                .setEncoding(audioFormat)
                                .setSampleRate(sampleRate)
                                .setChannelMask(channelConfig)
                                .build()
                        )
                        .setBufferSizeInBytes(bufferSize * 32) // Much larger buffer to prevent partial writes and audio cutting
                        .setTransferMode(android.media.AudioTrack.MODE_STREAM)
                        .build()

                    // Validate creation and start playback
                    val track = streamingAudioTrack
                    if (track != null) {
                        // Set maximum volume for better audibility
                        track.setVolume(1.0f)
                        if (track.state == android.media.AudioTrack.STATE_INITIALIZED) {
                            track.play()
                            Log.d("ChatFragment", "Created and started streaming AudioTrack (buffer: ${bufferSize * 32} bytes)")
                        } else {
                            Log.e("ChatFragment", "AudioTrack failed to initialize, state: ${track.state}")
                            track.release()
                            streamingAudioTrack = null
                            return
                        }
                    }
                }

                // Write audio data with proper validation
                val track = streamingAudioTrack
                if (track != null) {
                    if (track.state == android.media.AudioTrack.STATE_INITIALIZED) {
                        when (track.playState) {
                            android.media.AudioTrack.PLAYSTATE_PLAYING -> {
                                // Retry mechanism for smooth audio playback
                                var totalBytesWritten = 0
                                var remainingData = audioData
                                var retryCount = 0
                                val maxRetries = 3
                                
                                while (totalBytesWritten < audioData.size && retryCount < maxRetries) {
                                    val bytesWritten = track.write(remainingData, 0, remainingData.size, android.media.AudioTrack.WRITE_BLOCKING)
                                    
                                    if (bytesWritten > 0) {
                                        totalBytesWritten += bytesWritten
                                        if (bytesWritten < remainingData.size) {
                                            // Partial write - prepare remaining data for retry
                                            remainingData = remainingData.sliceArray(bytesWritten until remainingData.size)
                                            retryCount++
                                            Log.d("ChatFragment", "Partial AudioTrack write: $bytesWritten bytes, $totalBytesWritten/${audioData.size} total, retry $retryCount")
                                            
                                            // Small delay to allow buffer to drain
                                            Thread.sleep(10)
                                        } else {
                                            // Complete write
                                            break
                                        }
                                    } else {
                                        retryCount++
                                        Thread.sleep(20) // Wait for buffer space
                                    }
                                }
                                
                                if (totalBytesWritten >= audioData.size) {
                                    // Complete success
                                    consecutiveAudioTrackFailures = 0
                                    Log.d("ChatFragment", "Successfully wrote all $totalBytesWritten bytes to AudioTrack")
                                } else {
                                    // Failed after retries - use MediaPlayer fallback
                                    consecutiveAudioTrackFailures++
                                    Log.w("ChatFragment", "AudioTrack write failed after $retryCount retries ($totalBytesWritten/${audioData.size} bytes) - using MediaPlayer fallback")
                                    playRealtimeAudioFallback(remainingData)
                                }
                            }
                            android.media.AudioTrack.PLAYSTATE_STOPPED,
                            android.media.AudioTrack.PLAYSTATE_PAUSED -> {
                                // Try to restart
                                track.play()
                                Log.d("ChatFragment", "Restarted AudioTrack from stopped/paused state")
                            }
                            else -> {
                                Log.w("ChatFragment", "AudioTrack in unexpected state: ${track.playState}")
                            }
                        }
                    } else {
                        Log.e("ChatFragment", "AudioTrack not initialized: ${track.state}")
                        track.release()
                        streamingAudioTrack = null
                    }
                }

            } finally {
                audioTrackLock.unlock()
            }

        } catch (e: Exception) {
            Log.e("ChatFragment", "Error in audio playback", e)
            // Clean up and use fallback
            audioTrackLock.lock()
            try {
                val track = streamingAudioTrack
                track?.release()
                streamingAudioTrack = null
            } finally {
                audioTrackLock.unlock()
            }
            playRealtimeAudioFallback(audioData)
        }
    }

    /**
     * Stop and cleanup the streaming AudioTrack with thread safety
     */
    private fun stopStreamingAudio() {
        audioTrackLock.lock()
        try {
            val track = streamingAudioTrack
            if (track != null) {
                try {
                    if (track.state == android.media.AudioTrack.STATE_INITIALIZED) {
                        if (track.playState == android.media.AudioTrack.PLAYSTATE_PLAYING) {
                            track.stop()
                        }
                        track.release()
                        Log.d("ChatFragment", "Stopped and released streaming AudioTrack")
                    } else {
                        // Track not initialized, but still try to release
                        track.release()
                        Log.d("ChatFragment", "Released uninitialized streaming AudioTrack")
                    }
                } catch (e: Exception) {
                    Log.e("ChatFragment", "Error stopping streaming AudioTrack", e)
                    // Force release even on error
                    try {
                        track.release()
                    } catch (releaseError: Exception) {
                        Log.e("ChatFragment", "Error releasing AudioTrack", releaseError)
                    }
                } finally {
                    streamingAudioTrack = null
                }
            }
        } finally {
            audioTrackLock.unlock()
        }
    }

    /**
     * Fallback method for audio playback using sequential MediaPlayer
     * Prevents multiple concurrent MediaPlayers from overlapping
     */
    private fun playRealtimeAudioFallback(audioData: ByteArray) {
        synchronized(audioChunkQueue) {
            // Add audio chunk to queue
            audioChunkQueue.add(audioData)
            Log.d("ChatFragment", "Added ${audioData.size} bytes to audio queue (queue size: ${audioChunkQueue.size})")
            
            // Start processing queue if not already processing
            if (!isProcessingAudioQueue) {
                processAudioQueue()
            }
        }
    }
    
    /**
     * Process audio chunks sequentially to prevent overlapping playback
     */
    private fun processAudioQueue() {
        if (isProcessingAudioQueue) return
        
        lifecycleScope.launch(Dispatchers.IO) {
            isProcessingAudioQueue = true
            
            try {
                while (audioChunkQueue.isNotEmpty()) {
                    val audioData = synchronized(audioChunkQueue) {
                        if (audioChunkQueue.isNotEmpty()) audioChunkQueue.removeAt(0) else null
                    } ?: break
                    
                    // Create and play single audio chunk
                    playAudioChunkSequentially(audioData)
                }
            } catch (e: Exception) {
                Log.e("ChatFragment", "Error processing audio queue", e)
            } finally {
                isProcessingAudioQueue = false
                
                // CRITICAL FIX: Resume AudioTrack when MediaPlayer queue is empty
                if (isAudioTrackMode && audioChunkQueue.isEmpty()) {
                    try {
                        resumeStreamingAudio()
                        Log.d("ChatFragment", "Resumed AudioTrack after MediaPlayer queue completed")
                    } catch (e: Exception) {
                        Log.e("ChatFragment", "Error resuming AudioTrack after MediaPlayer completion", e)
                    }
                }
            }
        }
    }
    
    /**
     * Play a single audio chunk and wait for completion
     */
    private suspend fun playAudioChunkSequentially(audioData: ByteArray) = withContext(Dispatchers.IO) {
        try {
            // Create temporary WAV file with optimized caching
            val tempFile = File(requireContext().cacheDir, "temp_voice_${System.currentTimeMillis()}.wav")
            val wavHeader = createWavHeader(audioData.size, 24000, 1, 16)
            val wavData = wavHeader + audioData
            tempFile.writeBytes(wavData)
            
            Log.d("ChatFragment", "Playing audio chunk: ${audioData.size} bytes")
            
            // Create and configure MediaPlayer with optimized settings
            val mediaPlayer = android.media.MediaPlayer().apply {
                setDataSource(tempFile.absolutePath)
                setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                // Set maximum volume for better audibility
                setVolume(1.0f, 1.0f)
                prepare()
            }
            
            // Play and wait for completion
            suspendCancellableCoroutine<Unit> { continuation ->
                mediaPlayer.setOnCompletionListener {
                    it.release()
                    if (tempFile.exists()) {
                        tempFile.delete()
                        Log.d("ChatFragment", "Completed playing audio chunk")
                    }
                    continuation.resume(Unit)
                }
                
                mediaPlayer.setOnErrorListener { mp, what, extra ->
                    Log.e("ChatFragment", "MediaPlayer error: what=$what, extra=$extra")
                    mp.release()
                    if (tempFile.exists()) tempFile.delete()
                    continuation.resume(Unit)
                    true
                }
                
                mediaPlayer.start()
            }
            
        } catch (e: Exception) {
            Log.e("ChatFragment", "Error playing audio chunk", e)
        }
    }

    /**
     * Create WAV file header for PCM audio data
     */
    private fun createWavHeader(dataSize: Int, sampleRate: Int, channels: Int, bitsPerSample: Int): ByteArray {
        val header = ByteArray(44)
        val totalDataLen = dataSize + 36
        val byteRate = sampleRate * channels * bitsPerSample / 8

        // RIFF header
        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()

        // File size
        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = (totalDataLen shr 8 and 0xff).toByte()
        header[6] = (totalDataLen shr 16 and 0xff).toByte()
        header[7] = (totalDataLen shr 24 and 0xff).toByte()

        // WAVE header
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()

        // fmt subchunk
        header[12] = 'f'.code.toByte()
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()

        // Subchunk1Size (16 for PCM)
        header[16] = 16
        header[17] = 0
        header[18] = 0
        header[19] = 0

        // AudioFormat (1 for PCM)
        header[20] = 1
        header[21] = 0

        // NumChannels
        header[22] = channels.toByte()
        header[23] = 0

        // SampleRate
        header[24] = (sampleRate and 0xff).toByte()
        header[25] = (sampleRate shr 8 and 0xff).toByte()
        header[26] = (sampleRate shr 16 and 0xff).toByte()
        header[27] = (sampleRate shr 24 and 0xff).toByte()

        // ByteRate
        header[28] = (byteRate and 0xff).toByte()
        header[29] = (byteRate shr 8 and 0xff).toByte()
        header[30] = (byteRate shr 16 and 0xff).toByte()
        header[31] = (byteRate shr 24 and 0xff).toByte()

        // BlockAlign
        val blockAlign = channels * bitsPerSample / 8
        header[32] = blockAlign.toByte()
        header[33] = 0

        // BitsPerSample
        header[34] = bitsPerSample.toByte()
        header[35] = 0

        // data subchunk
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()

        // Subchunk2Size
        header[40] = (dataSize and 0xff).toByte()
        header[41] = (dataSize shr 8 and 0xff).toByte()
        header[42] = (dataSize shr 16 and 0xff).toByte()
        header[43] = (dataSize shr 24 and 0xff).toByte()

        return header
    }

    /**
     * Check if in realtime voice mode
     */
    fun isInRealtimeMode(): Boolean = isRealtimeMode

    /**
     * Get current voice agent
     */
    fun getCurrentVoiceAgent(): RealtimeVoiceAgent.VoiceAgentConfig? = currentVoiceAgent

    /**
     * Show voice agent selection dialog with fallback options
     */
    private fun showVoiceAgentSelectionDialog() {
        try {
            Log.d("ChatFragment", "Showing voice agent selection dialog")

            // Simple hardcoded options for testing
            val agentOptions = arrayOf(
                "🤖 AI Chat Assistant",
                "📋 Meeting Specialist",
                "🎓 Educational Tutor"
            )

            val agentKeys = arrayOf(
                "general_assistant",
                "meeting_specialist",
                "educational_tutor"
            )

            Log.d("ChatFragment", "Agent options: ${agentOptions.contentToString()}")
            Log.d("ChatFragment", "Creating AlertDialog with ${agentOptions.size} items")

            // Try using setSingleChoiceItems for better visibility
            val builder = AlertDialog.Builder(requireContext(), android.R.style.Theme_DeviceDefault_Light_Dialog_Alert)
            builder.setTitle("Choose Voice Agent")

            var selectedIndex = -1
            builder.setSingleChoiceItems(agentOptions, -1) { _, which ->
                selectedIndex = which
                Log.d("ChatFragment", "Agent selected at index: $which")
            }

            builder.setPositiveButton("Start Chat") { dialog, _ ->
                if (selectedIndex >= 0) {
                    val selectedAgentKey = agentKeys[selectedIndex]
                    val selectedAgentName = agentOptions[selectedIndex]
                    Log.d("ChatFragment", "Starting chat with: $selectedAgentKey ($selectedAgentName)")
                    showCustomToast("Starting conversation with $selectedAgentName...")
                    startRealtimeVoiceChat(selectedAgentKey)
                }
                dialog.dismiss()
            }

            builder.setNegativeButton("Cancel") { dialog, _ ->
                Log.d("ChatFragment", "Voice agent selection cancelled")
                dialog.dismiss()
            }

            // Fallback: if setSingleChoiceItems doesn't work, use simple buttons
            builder.setNeutralButton("Quick Start") { dialog, _ ->
                Log.d("ChatFragment", "Quick start with default agent")
                showCustomToast("Starting conversation with AI Chat Assistant...")
                startRealtimeVoiceChat("general_assistant")
                dialog.dismiss()
            }

            // Create and show dialog
            val dialog = builder.create()
            Log.d("ChatFragment", "About to show dialog")
            dialog.show()
            Log.d("ChatFragment", "Dialog shown successfully")

        } catch (e: Exception) {
            Log.e("ChatFragment", "Error showing voice agent selection dialog", e)
            showCustomToast("Error loading voice agents: ${e.message}")
            // Ultimate fallback - use button-based dialog
            Log.d("ChatFragment", "Using button-based fallback dialog")
            showSimpleVoiceAgentDialog()
        }
    }

    /**
     * Fallback dialog using simple buttons - guaranteed to work
     */
    private fun showSimpleVoiceAgentDialog() {
        try {
            Log.d("ChatFragment", "Showing simple button-based voice agent dialog")

            val builder = AlertDialog.Builder(requireContext())
            builder.setTitle("Choose Voice Agent")
            builder.setMessage("Select your AI assistant type:")

            // Use three separate buttons in positive/neutral/negative positions
            builder.setPositiveButton("🤖 AI Assistant") { _, _ ->
                Log.d("ChatFragment", "Selected: AI Assistant")
                showCustomToast("Starting conversation with AI Chat Assistant...")
                startRealtimeVoiceChat("general_assistant")
            }

            builder.setNeutralButton("📋 Meeting") { _, _ ->
                Log.d("ChatFragment", "Selected: Meeting Specialist")
                showCustomToast("Starting conversation with Meeting Specialist...")
                startRealtimeVoiceChat("meeting_specialist")
            }

            builder.setNegativeButton("🎓 Tutor") { _, _ ->
                Log.d("ChatFragment", "Selected: Educational Tutor")
                showCustomToast("Starting conversation with Educational Tutor...")
                startRealtimeVoiceChat("educational_tutor")
            }

            val dialog = builder.create()
            dialog.show()
            Log.d("ChatFragment", "Simple dialog shown successfully")

        } catch (e: Exception) {
            Log.e("ChatFragment", "Error in simple dialog", e)
            // Final fallback - just start with default
            Log.d("ChatFragment", "Final fallback - starting default agent")
            showCustomToast("Starting voice chat...")
            startRealtimeVoiceChat("general_assistant")
        }
    }

    private fun toggleMeetingRecording() {
        try {
            // Toggle meeting recording state
            val isCurrentlyRecording = binding.meetingRecordButton.text.contains("Stop")

            if (isCurrentlyRecording) {
                // Stop recording
                binding.meetingRecordButton.text = "🔴 Record Meeting"
                showCustomToast("Meeting recording stopped")
                // TODO: Stop actual recording functionality
            } else {
                // Start recording
                binding.meetingRecordButton.text = "⏹️ Stop Recording"
                showCustomToast("Meeting recording started")
                // TODO: Start actual recording functionality
            }
        } catch (e: Exception) {
            Log.e("ChatFragment", "Error toggling meeting recording", e)
            showCustomToast("Error toggling recording")
        }
    }

    private fun toggleLiveChat() {
        try {
            // Toggle live chat state
            val isCurrentlyActive = binding.realtimeVoiceButton.text.contains("Stop")

            if (isCurrentlyActive) {
                // Stop live chat
                binding.realtimeVoiceButton.text = "🟢 Live Chat"
                showCustomToast("Live chat stopped")
                stopRealtimeVoiceChat()
            } else {
                // Start live chat
                binding.realtimeVoiceButton.text = "⏹️ Stop Live Chat"
                showCustomToast("Live chat started")
                showVoiceAgentSelectionDialog()
            }
        } catch (e: Exception) {
            Log.e("ChatFragment", "Error toggling live chat", e)
            showCustomToast("Error toggling live chat")
        }
    }

    // Extension support methods for structured outputs
    internal fun getCurrentModel(): String {
        return currentModel
    }

    internal fun saveConversation() {
        saveChatHistory()
    }


}
// --- Request Data Classes ---
data class GeminiTtsRequest(
    val contents: List<ContentPart>,
    val generationConfig: GenerationConfig? = null // Renamed from 'config' for clarity
)

data class ContentPart(
    val parts: List<Part>
)

data class Part(
    val text: String
)

data class GenerationConfig(
    val responseMimeType: String? = null, // e.g., "audio/wav" or inferred
    val responseModalities: List<String>? = null, // ["AUDIO"]
    val speechConfig: SpeechConfig? = null
)

data class SpeechConfig(
    val multiSpeakerVoiceConfig: MultiSpeakerVoiceConfig? = null
)

data class MultiSpeakerVoiceConfig(
    val speakerVoiceConfigs: List<SpeakerVoiceConfigItem>
)

data class SpeakerVoiceConfigItem(
    val speaker: String, // e.g., "Joe"
    val voiceConfig: VoiceConfig
)

data class VoiceConfig(
    val prebuiltVoiceConfig: PrebuiltVoiceConfig? = null
)

data class PrebuiltVoiceConfig(
    val voiceName: String // e.g., "Kore", "Puck"
)




data class Candidate(
    val content: ResponseContent?
)

data class ResponseContent(
    val parts: List<ResponsePart>?,
    val role: String?
)

data class ResponsePart(
    val inlineData: InlineData?
    // May also have `fileData` if audio is large and returned as a URI
)

data class InlineData(
    val mimeType: String, // e.g., "audio/wav" or "audio/mp3"
    val data: String      // Base64 encoded audio data
)