package com.playstudio.aiteacher



import android.provider.AlarmClock
import TooltipDialog
import WhisperHelper
import android.Manifest
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
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
import android.provider.MediaStore
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
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
import com.airbnb.lottie.LottieAnimationView
import com.bumptech.glide.Glide
import com.canhub.cropper.CropImage
import com.canhub.cropper.CropImageView
import com.google.android.gms.ads.AdRequest
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
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
import java.util.concurrent.TimeUnit
import android.util.Base64
import androidx.core.view.MenuProvider
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.google.gson.annotations.SerializedName
import com.playstudio.aiteacher.viewmodel.OpenAILiveAudioViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.Job
import java.net.URLEncoder
import com.playstudio.aiteacher.ComputerUseManager

import android.provider.CalendarContract
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.RequestBody.Companion.asRequestBody
import kotlin.coroutines.resume



class ChatFragment : Fragment(), TextToSpeech.OnInitListener {
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
    private lateinit var startComputerUseButton: Button
    private lateinit var computerUseResponseTextView: TextView

    private var speechRecognizer: SpeechRecognizer? = null

    // Use the new ListAdapter
    private lateinit var chatAdapter: com.playstudio.aiteacher.ChatAdapter
    private var consecutiveApiKeyErrors = 0
    private val MAX_API_KEY_ERRORS_BEFORE_UPDATE = 3
    private var outputFile: String = ""
    private var meetingTranscript = StringBuilder()
    private var conversationHistory = JSONArray()

    // Web search related constants
    private val WEB_SEARCH_MODELS = listOf(
        "gpt-4o-search-preview",
        "gpt-4o-mini-search-preview"
    )
    private var isWebSearchEnabled = false





    // At the top of ChatFragment, with other viewModel declarations
    private val openAILiveAudioViewModel: OpenAILiveAudioViewModel by viewModels()


    // Manager for OpenAI computer-use API
    //private val computerUseManager by lazy { ComputerUseManager(requireActivity()) }
    private lateinit var chatTextView: TextView
    private var interstitialAd: InterstitialAd? = null
    private var isInterstitialAdLoaded = false
    private val CHAT_COUNT_KEY = "chat_count"
    private val SUBSCRIPTION_PROMPT_THRESHOLD = 3
    private val PREFS_NAME = "app_prefs"
    private val FIRST_LAUNCH_KEY = "first_launch"
    private val GREETING_SENT_KEY = "greeting_sent"
    private val INTERACTION_COUNT_KEY = "interaction_count"
    private val RATING_REMINDER_COUNT_KEY = "rating_reminder_count"
    private var isLoading = false
    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!
    // private lateinit var chatAdapter: ChatAdapter
    // private val chatMessages = mutableListOf<ChatMessage>()
    private var isGreetingSent = false
    private val greetings = listOf(
        "Hello! How can I assist you today? 😊",
        "Hi there! What can I do for you? 😄",
        "Hey! Ready to help you out! 😃",
        "Good to see you! How can I assist? 😁",
        "Welcome back! What’s on your mind? 😊",
        "Hi! Let’s get started—what do you need help with? 😄",
        "Hello! How can I make your day better? 😊",
        "Hey! What’s up? How can I assist you? 😃",
        "Hi! Let’s tackle your questions together! 😁",
        "Hello! Ready to help you with anything! 😊"
    )
    private var rewardedAd: RewardedAd? = null
    private var canSendMessage = false
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    private val apiKey =  BuildConfig.API_KEY
    private var currentModel = "gpt-3.5-turbo"
    private var conversationId: String? = null
    private var tts: TextToSpeech? = null
    private var isTtsEnabled = false
    private val chatHistoryKey = "chat_history"
    private var isFollowUpEnabled = true

    // Track ongoing API call so we can cancel when starting a new conversation
    private var currentApiJob: Job? = null

    private lateinit var requestAudioPermissionLauncher: ActivityResultLauncher<String> // Assuming this is declared

    private lateinit var expandFollowUpQuestionsButton: Button
    private lateinit var followUpQuestionsScrollView: HorizontalScrollView
    private var isFollowUpQuestionsExpanded = false

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
        private const val KEY_GREETING_SENT = "greeting_sent_for_conv_" // Append convId
        // ... other keys

        // Add these:
        private var isLoadingMoreMessages = false
        private val MESSAGES_PAGE_SIZE = 20 // Or your desired page size


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
        private const val DAILY_LIMIT_CLAUDE_SONNET4 = 40
        private const val DAILY_LIMIT_CLAUDE_OPUS4 = 25
        //private const val REQUEST_RECORD_AUDIO_PERMISSION = 300
        private const val REQUEST_STORAGE_PERMISSION = 301
    }


    private lateinit var requestPermissionLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var captureImageLauncher: ActivityResultLauncher<Intent>
    private lateinit var cropImageLauncher: ActivityResultLauncher<Intent>

    private val subscriptionViewModel: SubscriptionViewModel by activityViewModels()

    private var isUserSubscribed: Boolean = false
    private var subscriptionExpirationTime: Long = 0L

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
        inflater.inflate(R.menu.harmburger_menu, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onResume() {
        super.onResume()
        (requireActivity() as? AppCompatActivity)?.supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setHomeAsUpIndicator(R.drawable.ic_arrow_back)
            computerUseManager.accessibilityService = MyAccessibilityService.instance
        }
        //loadInterstitialAd() // Load the interstitial ad when the fragment resumes
    }



    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_new_conversation -> {
                startNewConversation()
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
            else -> super.onOptionsItemSelected(item)
        }
    }





    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d("ChatFragment", "onViewCreated called")

        loadSharedPrefs()
        binding.voiceSelectionButton.text = "Voice: ${selectedVoice.replaceFirstChar { it.uppercase() }}"

        // Initialize the views
        expandFollowUpQuestionsButton = view.findViewById(R.id.expandFollowUpQuestionsButton)
        followUpQuestionsScrollView = view.findViewById(R.id.followUpQuestionsScrollView)

        // Set the click listener for the expand/collapse button
        expandFollowUpQuestionsButton.setOnClickListener {
            toggleFollowUpQuestions()
        }
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

        binding.voiceSelectionButton.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.white))
        setupChatRecyclerView()

        binding.voiceSelectionButton.setOnClickListener {
            showVoiceSelectionDialog()
        }



        arguments?.getString("recognized_text")?.let { text ->
            binding.messageEditText.setText(text)
            binding.messageEditText.setSelection(text.length)
        }


        // Load the isGreetingSent flag from SharedPreferences
        val sharedPreferences = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        isGreetingSent = sharedPreferences.getBoolean(GREETING_SENT_KEY, false)

        // Now it's safe to send the greeting message
        if (!isGreetingSent) {
            sendGreetingMessage()
            isGreetingSent = true
            // Save the isGreetingSent flag to SharedPreferences
            sharedPreferences.edit().putBoolean(GREETING_SENT_KEY, true).apply()
        }

        updateActiveModelButton("GPT-3.5 Turbo")

        // Initialize TTS button state
        updateTtsButtonState()

        // Set up TTS toggle button click listener
        binding.ttsToggleButton.setOnClickListener {
            isTtsEnabled = !isTtsEnabled // Toggle the state
            updateTtsButtonState() // Update the button's appearance
            showCustomToast(if (isTtsEnabled) "TTS enabled" else "TTS disabled")
        }
        binding.activeModelButton.setOnClickListener {
            showChatGptOptionsDialog()
        }

        // Check if it's the first launch
        val isFirstLaunch = sharedPreferences.getBoolean(FIRST_LAUNCH_KEY, true)

        if (isFirstLaunch) {
            // Show the tooltip dialog
            val tooltipDialog = TooltipDialog()
            tooltipDialog.show(parentFragmentManager, "TooltipDialog")

            // Update the shared preferences to indicate that the dialog has been shown
            sharedPreferences.edit().putBoolean(FIRST_LAUNCH_KEY, false).apply()
        }

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

        binding.historyButton.setOnClickListener {
            showChatHistoryDialog()
        }

        // Initialize with the selected model
        arguments?.getString("selected_model")?.let {
            currentModel = it
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
        computerUseManager.accessibilityService = MyAccessibilityService.instance
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
                    // Decide if you want to auto-trigger the action or let the user tap again
                    // For example, if the user was trying to start the OpenAI session:
                    if (binding.openaiLiveAudioControls.visibility == View.VISIBLE) { // Check if this mode is active
                        openAILiveAudioViewModel.toggleSession(requireContext())
                    }
                } else {
                    showCustomToast("Audio permission denied. Cannot use voice features.")
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
                openAILiveAudioViewModel.toggleSession(requireContext())
            }
        }

        // Set up other listeners for OpenAI controls if any (e.g., openAISignalTurnEndButton)
        binding.openAISignalTurnEndButton.setOnClickListener {
            openAILiveAudioViewModel.signalUserTurnEnded()
        }


        // Your observers for openAILiveAudioViewModel states
        viewLifecycleOwner.lifecycleScope.launch {
            openAILiveAudioViewModel.isSessionActive.collect { isActive ->
                binding.openAISessionButton.text = if (isActive) "🛑 Stop OpenAI Session" else "🎙️ Start OpenAI Session"
                binding.openAISignalTurnEndButton.visibility = if (isActive) View.VISIBLE else View.GONE // Example visibility toggle
                if (isActive && currentModel == "openai-realtime-voice") { // Be specific about which mode hides it
                    binding.messageInputLayout.visibility = View.GONE
                } else if (currentModel != "gemini-voice-chat") { // Don't show if Gemini voice is active
                    binding.messageInputLayout.visibility = View.VISIBLE
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            openAILiveAudioViewModel.status.collect { status ->
                binding.openAIStatusTextView.text = "OpenAI Live: $status"
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            openAILiveAudioViewModel.error.collect { error ->
                error?.let {
                    binding.openAIStatusTextView.append("\nError: $it")
                    showCustomToast("OpenAI Live Error: $it")
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            openAILiveAudioViewModel.aiTextMessage.collect { text ->
                binding.openAIAiResponseTextView.text = text
            }
        }
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

        tts = TextToSpeech(requireContext(), this)






        binding.sendButton.setOnClickListener {
            val userMessage = binding.messageEditText.text.toString()
            Log.d("ChatFragment", "Send button clicked with message: $userMessage")
            if (userMessage.isNotEmpty()) {
                if (isUserSubscribed && subscriptionExpirationTime > System.currentTimeMillis() || canSendMessage) {
                    Log.d("ChatFragment", "User is subscribed or can send message")
                    handleMessage(userMessage)
                } else {
                    Log.d("ChatFragment", "User is not subscribed and cannot send message")
                    showRewardedAd()
                }
            } else {
                Log.d("ChatFragment", "User message is empty")
            }
        }

        binding.scanTextButton.setOnClickListener { showImageOrDocumentPickerDialog() }

        // Initialize speech recognizer with listener
        initializeSpeechRecognizer()


        binding.voiceInputButton.setOnClickListener {
            if (checkAndRequestPermissions()) {
                startVoiceRecognition()
            }
        }


        setupMenuProvider() // For new OptionsMenu handling
        initializeActivityLaunchers()
        setupUIListeners()
        observeViewModels() // For OpenAI Live Audio, Gemini Live Audio, Subscription
        setupUIListeners()
        observeViewModels() // For OpenAI Live Audio, Gemini Live Audio, Subscription
        binding.shareButton.setOnClickListener { shareLastResponse() }

        binding.shareButton.background = ContextCompat.getDrawable(requireContext(), R.drawable.fading_background)
        binding.historyButton.background = ContextCompat.getDrawable(requireContext(), R.drawable.fading_background)
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


        binding.sendButton.setOnClickListener {
            val userMessage = binding.messageEditText.text.toString()
            if (userMessage.isNotEmpty()) {
                hideKeyboard()
                if (isUserSubscribed && subscriptionExpirationTime > System.currentTimeMillis() || canSendMessage) {
                    handleMessage(userMessage)
                } else if (checkDailyMessageLimit()) {
                    incrementMessageCount()
                    handleMessage(userMessage)
                } else {
                    showRewardedAd()
                }
            }
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
                        openAILiveAudioViewModel.toggleSession(requireContext())
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

        // Example button click in ChatFragment
        binding.startComputerUseButton.setOnClickListener {
            val prompt = binding.messageEditText.text.toString().trim()
            if (prompt.isNotBlank()) {
                addMessageToChat(prompt, true, containsRichContent = false) // Show user's prompt
                binding.messageEditText.text.clear()
                showTypingIndicator() // Show general "AI working"

                lifecycleScope.launch {
                    val sessionSummary = computerUseManager.startComputerUseSession(prompt)
                    // Typing indicator will be removed by the first message from onUpdate,
                    // or you can explicitly remove it here if no messages were sent via onUpdate.
                    // If ComputerUseManager sends messages via onUpdate, they will call addMessageToChat,
                    // which should ideally handle removing the typing indicator.
                    // removeTypingIndicator() // Might be redundant if onUpdate calls addMessageToChat
                    Log.d("ChatFragment", "ComputerUseManager final session summary: $sessionSummary")
                    // Optionally, display the finalSummary if it contains info not sent via onUpdate
                    // addMessageToChat("Session summary: $sessionSummary", false, containsRichContent = false)
                }
            } else {
                showCustomToast("Please enter a prompt for computer use.")
            }
        }


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
            binding.messageEditText.setSelection(question.length)
        },
        onLoadMoreRequested = {
            if (!isLoadingMoreMessages) {
                loadOlderMessages()
            }
        }
    )

    binding.recyclerView.apply {
        layoutManager = LinearLayoutManager(requireContext()).apply { stackFromEnd = true }
        adapter = chatAdapter
        setHasFixedSize(true)
        itemAnimator = null
        setItemViewCacheSize(20)
    }
}





    private fun setupUIListeners() {
        binding.sendButton.setOnClickListener {
            val userMessage = binding.messageEditText.text.toString().trim()
            if (userMessage.isNotEmpty()) {
                processUserMessageSend(userMessage)
            }
        }

        binding.voiceInputButton.setOnClickListener { // Standard STT
            if (checkAndRequestAudioPermission(REQUEST_RECORD_AUDIO_PERMISSION)) { // Use a specific request code
                startVoiceRecognition()
            }
        }

        binding.scanTextButton.setOnClickListener { showImageOrDocumentPickerDialog() }
        binding.ttsToggleButton.setOnClickListener {
            isTtsEnabled = !isTtsEnabled
            updateTtsButtonState()
            showCustomToast(if (isTtsEnabled) "TTS Enabled" else "TTS Disabled")
        }
        binding.activeModelButton.setOnClickListener { showChatGptOptionsDialog() }
        binding.historyButton.setOnClickListener { showChatHistoryDialog() }
        binding.shareButton.setOnClickListener { shareLastResponse() }

        // OpenAI Live Audio Controls
        binding.openAISessionButton.setOnClickListener {
            if (checkAndRequestAudioPermission(REQUEST_RECORD_AUDIO_PERMISSION)) {
                openAILiveAudioViewModel.toggleSession(requireContext())
            }
        }
        binding.openAISignalTurnEndButton.setOnClickListener {
            openAILiveAudioViewModel.signalUserTurnEnded()
        }


        // Gemini Live Audio Controls (if used)
        // binding.geminiRecordButton.setOnClickListener {
        //     if (checkAndRequestAudioPermission(REQUEST_RECORD_AUDIO_PERMISSION_GEMINI)) { // Different request code if needed
        //         geminiLiveAudioViewModel.toggleRecording(requireContext())
        //     }
        // }
        // binding.geminiResetButton.setOnClickListener { geminiLiveAudioViewModel.resetSession() }

        binding.voiceSelectionButton.setOnClickListener { showVoiceSelectionDialog() }
        // ... other listeners

    }







    // In ChatFragment.kt

    private fun observeViewModels() {
        // OpenAI Live Audio ViewModel Observers
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                openAILiveAudioViewModel.isSessionActive.collect { isActive ->
                    binding.openAISessionButton.text = if (isActive) "🛑 Stop OpenAI" else "🎙️ OpenAI Voice"
                    binding.openAISignalTurnEndButton.visibility = if (isActive) View.VISIBLE else View.GONE
                    if (isActive && currentModel == "openai-realtime-voice") {
                        binding.messageInputLayout.visibility = View.GONE
                    }
                    // Don't manage messageInputLayout visibility for other models here, switchUiForModel handles it
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                openAILiveAudioViewModel.status.collect { status -> binding.openAIStatusTextView.text = "OpenAI: $status" }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                openAILiveAudioViewModel.error.collect { error -> error?.let { showCustomToast("OpenAI Error: $it") } }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                openAILiveAudioViewModel.aiTextMessage.collect { text ->
                    if (openAILiveAudioViewModel.isSessionActive.value) { // Only update if session is active
                        binding.openAIAiResponseTextView.text = text
                        // Consider adding this text as a ChatMessage if appropriate for your UI
                    }
                }
            }
        }

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
    private fun isGreetingSentForCurrentConversation(): Boolean {
        val chatPrefs = requireContext().getSharedPreferences(PREFS_NAME_CHAT, Context.MODE_PRIVATE)
        return chatPrefs.getBoolean(KEY_GREETING_SENT + conversationId, false)
    }

    private fun markGreetingSentForCurrentConversation() {
        val chatPrefs = requireContext().getSharedPreferences(PREFS_NAME_CHAT, Context.MODE_PRIVATE)
        chatPrefs.edit().putBoolean(KEY_GREETING_SENT + conversationId, true).apply()
    }

    private fun loadSharedPrefs() {
        val appPrefs = requireContext().getSharedPreferences(PREFS_NAME_APP, Context.MODE_PRIVATE)
        selectedVoice = appPrefs.getString(SELECTED_VOICE_KEY, "alloy") ?: "alloy"
        conversationId = appPrefs.getString("last_conversation_id", null) ?: generateConversationId().also {
            appPrefs.edit().putString("last_conversation_id", it).apply()
        }
        isFollowUpEnabled = appPrefs.getBoolean("follow_up_enabled", true)
    }





















    private fun switchUiForModel(model: String) {
        Log.d("ChatFragment", "Switching UI for model: $model")
        // Default to text chat UI
        binding.messageInputLayout.visibility = View.VISIBLE
        binding.scanTextButton.visibility = View.VISIBLE
        binding.voiceInputButton.visibility = View.VISIBLE // Standard STT
        binding.sendButton.visibility = View.VISIBLE
        binding.ttsToggleButton.visibility = View.VISIBLE
        binding.followUpQuestionsContainer.visibility = if (isFollowUpEnabled) View.VISIBLE else View.GONE
        binding.generatedImageView.visibility = View.GONE
        binding.downloadButton.visibility = View.GONE
        binding.generatingText.visibility = View.GONE

        binding.openaiLiveAudioControls.visibility = View.GONE
        binding.openAIStatusTextView.visibility = View.GONE
        binding.openAIAiResponseTextView.visibility = View.GONE
        binding.computerUseControls.visibility = View.GONE



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
            "computer-use-preview" -> {
                binding.messageInputLayout.visibility = View.VISIBLE
                binding.sendButton.visibility = View.GONE
                binding.scanTextButton.visibility = View.GONE
                binding.voiceInputButton.visibility = View.GONE
                binding.ttsToggleButton.visibility = View.GONE
                binding.followUpQuestionsContainer.visibility = View.GONE
                binding.computerUseControls.visibility = View.VISIBLE
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
        // Central point for sending a message based on currentModel and limits
        hideKeyboard()
        binding.messageEditText.text.clear()

        if (isUserSubscribed || canSendMessage) {
            handleMessage(userMessage)
            if (!isUserSubscribed) canSendMessage = false // Consume one "rewarded" message
            return
        }

        // Check model-specific limits first
        val (limitKey, dailyMax) = when (currentModel) {
            "dall-e-3" -> "dall-e-3" to DAILY_LIMIT_DALLE
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
            incrementModelUsage(limitKey)
            handleMessage(userMessage)
        } else {
            showCustomToast("Daily limit for $currentModel reached.")
            showRewardedAd() // Offer ad to continue
        }
    }





    private val computerUseManager by lazy {
        ComputerUseManager(requireActivity()) { messageFromManager ->
            // This is the onUpdate callback
            addMessageToChat(
                messageContent = messageFromManager,
                isUser = false,
                containsRichContent = false // Adjust as needed
            )
        }
    }
    // MODIFIED handleChatCompletion with Tool Calling Logic
    private fun handleChatCompletion(
        userMessageContent: String,
        currentConversationHistoryForToolCall: MutableList<JSONObject> = mutableListOf() // For multi-turn tool use
    ) {
        // Cancel any previous request so outdated responses don't appear
        currentApiJob?.cancel()
        val requestConversationId = conversationId
        val messagesToSend = JSONArray()

        if (currentConversationHistoryForToolCall.isEmpty()) {
            // Initial turn or non-tool-related turn
            // Add system/developer prompt if needed
            if (currentModel.startsWith("o1") || currentModel.startsWith("o3")) { // Example for 'o' models
                messagesToSend.put(JSONObject().apply {
                    put("role", "system")
                    put("content", "You are a specialized assistant. Use tools when necessary to fulfill the request.")
                })
            }

            // Add relevant chat history from adapter
            // Be mindful of token limits; you might only send the last N messages
            val historyLimit = 10 // Example: send last 10 messages
            chatAdapter.currentList.filterNot { it.isTyping }
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
            put("model", currentModel)
            put("messages", messagesToSend)

            if (modelSupportsTools(currentModel)) {
                put("tools", getAvailableTools())
            }

            if (WEB_SEARCH_MODELS.contains(currentModel)) {
                put("web_search_options", JSONObject())
            }
        }

        val body = requestBodyJson.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
        val request = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .post(body)
            .addHeader("Authorization", "Bearer ${BuildConfig.API_KEY}") // Use OpenAI key
            .addHeader("Content-Type", "application/json")
            .build()

        Log.d("ChatFragment", "Sending ChatCompletion (Tools/Text): ${requestBodyJson.toString(2)}")
        if (currentConversationHistoryForToolCall.isEmpty()) {
            showTypingIndicator() // Show typing only for the initial user query of a turn
        }

        currentApiJob = lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) { okHttpClient.newCall(request).execute() }
                val responseBodyString = response.body?.string()

                if (requestConversationId != conversationId) {
                    withContext(Dispatchers.Main) { removeTypingIndicator() }
                    return@launch
                }

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

                    val choice = jsonResponse.optJSONArray("choices")?.optJSONObject(0)
                    val messageFromApi = choice?.optJSONObject("message")

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
                        // Call API again with results
                        handleChatCompletion(userMessageContent, ongoingHistoryForThisTurn)

                    } else {
                        // No tool_calls, this is a direct text response from the AI
                        // Your existing handleSuccessResponse should be called here.
                        // It expects the full response body string of THIS turn.
                        // We need to reconstruct a minimal response string for it.
                        val minimalResponseForHandler = JSONObject().apply {
                            put("choices", JSONArray().put(JSONObject().apply {
                                put("message", messageFromApi)
                                // Add usage if your handleSuccessResponse expects it
                                if (jsonResponse.has("usage")) {
                                    put("usage", jsonResponse.getJSONObject("usage"))
                                }
                            }))
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
        // Exclude preview models that do not allow tool usage
        if (modelName.contains("search-preview") ||
            modelName.contains("realtime-preview") ||
            modelName.contains("audio-preview") ||
            modelName.contains("computer-use-preview")
        ) {
            return false
        }

        // List models known to support function calling/tools
        return modelName.startsWith("gpt-4") ||
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

    suspend fun processAndSummarizeMeeting(): String? = withContext(Dispatchers.IO) {
        if (!isMeetingRecording && currentMeetingAudioFile == null) {
            Log.w("ChatFragmentTool", "No active recording or file to summarize.")
            return@withContext null
        }

        if (isMeetingRecording) {
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
        currentMeetingAudioFile = null

        if (fileToSummarize == null || !fileToSummarize.exists() || fileToSummarize.length() == 0L) {
            Log.e("ChatFragmentTool", "Meeting audio file is invalid or empty for summarization.")
            return@withContext JSONObject().apply { put("error", "No valid meeting audio to summarize.") }.toString()
        }

        Log.i("ChatFragmentTool", "Processing meeting recording for summarization: ${fileToSummarize.absolutePath}")
        showCustomToast("Processing meeting summary...")

        val transcript: String? = try {
            val requestFile = fileToSummarize.asRequestBody("audio/m4a".toMediaTypeOrNull())
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", fileToSummarize.name, requestFile)
                .addFormDataPart("model", "whisper-1")
                .build()

            val request = Request.Builder()
                .url("https://api.openai.com/v1/audio/transcriptions")
                .header("Authorization", "Bearer ${BuildConfig.API_KEY}")
                .post(requestBody)
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string()
                body?.let { JSONObject(it).getString("text") }
            } else {
                Log.e("ChatFragmentTool", "Transcription API error: ${response.code} - ${response.message}")
                null
            }
        } catch (e: Exception) {
            Log.e("ChatFragmentTool", "Exception during transcription: ${e.message}", e)
            null
        }

        if (transcript.isNullOrBlank()) {
            fileToSummarize.delete()
            return@withContext JSONObject().apply { put("error", "Failed to transcribe the meeting audio.") }.toString()
        }

        fileToSummarize.delete()

        val summaryPrompt = "Please provide a concise summary of the following meeting transcript:\n\nTranscript:\n\"\"\"\n$transcript\n\"\"\"\n\nSummary:"
        val messagesArray = JSONArray().put(JSONObject().apply {
            put("role", "user")
            put("content", summaryPrompt)
        })
        val summaryRequestBodyJson = JSONObject().apply {
            put("model", "gpt-3.5-turbo")
            put("messages", messagesArray)
            put("temperature", 0.5)
            put("max_tokens", 300)
        }
        val summaryBody = summaryRequestBodyJson.toString().toRequestBody("application/json".toMediaTypeOrNull())
        val summaryRequest = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .post(summaryBody)
            .addHeader("Authorization", "Bearer ${BuildConfig.API_KEY}")
            .addHeader("Content-Type", "application/json")
            .build()

        try {
            val summaryResponse = okHttpClient.newCall(summaryRequest).execute()
            if (summaryResponse.isSuccessful) {
                val summaryResponseBody = summaryResponse.body?.string()
                summaryResponseBody?.let {
                    val summaryText = JSONObject(it).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content").trim()
                    return@withContext summaryText
                }
            } else {
                Log.e("ChatFragmentTool", "Summarization API error: ${summaryResponse.code} - ${summaryResponse.message}")
            }
        } catch (e: Exception) {
            Log.e("ChatFragmentTool", "Exception during summarization: ${e.message}", e)
        }
        JSONObject().apply { put("error", "Failed to summarize the meeting.") }.toString()
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
        return JSONArray().apply {
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
    }














    private fun updateUIForCurrentModel() {
        when (currentModel) {
            "dall-e-3" -> {
                binding.messageEditText.hint = "Describe the image you want to generate..."
                binding.followUpQuestionsContainer.visibility = View.GONE
                binding.generatedImageView.visibility = View.VISIBLE
                updateActiveModelButton("DALL-E 3")
            }
            else -> {
                binding.messageEditText.hint = "Type your message here..."
                binding.followUpQuestionsContainer.visibility = View.VISIBLE
                binding.generatedImageView.visibility = View.GONE
                updateActiveModelButton(when(currentModel) {
                    "gpt-4" -> "GPT-4"
                    else -> "GPT-3.5 Turbo"
                })
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
        val currentList = chatAdapter.currentList.toMutableList()
        if (chatMessage.isTyping) {
            currentList.removeAll { it.isTyping } // Ensure only one typing indicator
        }
        currentList.add(chatMessage)
        chatAdapter.submitList(currentList.toList()) {
            if (scrollToBottom && currentList.isNotEmpty()) {
                binding.recyclerView.smoothScrollToPosition(currentList.size - 1)
            }
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
        val currentList = chatAdapter.currentList.toMutableList()
        currentList.addAll(0, olderMessages) // Add to the beginning of the list
        chatAdapter.submitList(currentList.toList()) {
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
                    addMessageToChat(
                        messageContent = processedContent,
                        isUser = false,
                        citations = finalCitations,
                        followUpQuestions = finalFollowUpsToShow,
                        containsRichContent = containsRich
                    )
                    addFollowUpQuestionsToChat(finalFollowUpsToShow)
                }

                if (isTtsEnabled) {
                    handleTextToSpeech(originalReplyContent) // Speak original, un-prefixed/un-augmented content
                }
                incrementInteractionCount()
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

        if (!isFollowUpEnabled || questions.isEmpty()) {
            binding.followUpQuestionsContainer.visibility = View.GONE
            return
        }

        // Create the toggle header button
        val toggleButton = Button(requireContext()).apply {
            //text = "Suggested Follow-ups ▼"
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
            setBackgroundColor(Color.TRANSPARENT)
            setTextColor(ContextCompat.getColor(requireContext(), R.color.primary_color))

            val params = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, (8 * resources.displayMetrics.density).toInt())
            }
            layoutParams = params
        }

        // Create container for questions (initially hidden)
        val questionsContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }

        // Add questions to the container
        questions.forEach { question ->
            Button(requireContext()).apply {
                text = question
                textSize = 12f
                setTypeface(typeface, Typeface.NORMAL)
                setBackgroundColor(Color.TRANSPARENT)
                setTextColor(ContextCompat.getColor(requireContext(), R.color.primary_color))

                val params = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, 0, (4 * resources.displayMetrics.density).toInt())
                }
                layoutParams = params

                setOnClickListener {
                    binding.messageEditText.setText(question)
                    binding.messageEditText.setSelection(question.length)
                }
            }.also { questionsContainer.addView(it) }
        }

        // Toggle functionality
        var isExpanded = false
        toggleButton.setOnClickListener {
            isExpanded = !isExpanded
            if (isExpanded) {
                questionsContainer.visibility = View.VISIBLE
                toggleButton.text = "Suggested Follow-ups ▲"
                questionsContainer.animate()
                    .alpha(1f)
                    .setDuration(200)
                    .start()
            } else {
                questionsContainer.animate()
                    .alpha(0f)
                    .setDuration(200)
                    .withEndAction {
                        questionsContainer.visibility = View.GONE
                    }
                    .start()
                toggleButton.text = "Suggested Follow-ups ▼"
            }
        }

        // Add views to container
        binding.followUpQuestionsContainer.addView(toggleButton)
        binding.followUpQuestionsContainer.addView(questionsContainer)
        binding.followUpQuestionsContainer.visibility = View.VISIBLE
    }


    private fun sendMessageToAPI(message: String) {
        when (currentModel) {
            "dall-e-3" -> handleImageGeneration(message)
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
            put("reasoning_effort", "medium")
            put("messages", messagesArray)
        }

        val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
        val request = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions") // Assuming OpenAI endpoint
            .post(body)
            .addHeader("Authorization", "Bearer $apiKey")
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

                                withContext(Dispatchers.Main) {
                                    removeTypingIndicator()
                                    addMessageToChat( // Your refactored method
                                        messageContent = reply,
                                        isUser = false,
                                        containsRichContent = determineIfRichContent(reply)
                                        // Parse and pass citations/followUps if this model provides them
                                    )
                                    // //generateFollowUpQuestions(reply) // If needed
                                    if (isTtsEnabled) {
                                        handleTextToSpeech(reply)
                                    }
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
        // Cancel any pending API call and clear typing indicator
        currentApiJob?.cancel()
        removeTypingIndicator()
        chatAdapter.submitList(emptyList()) // Clear the adapter
        conversationId = generateConversationId()
        isGreetingSent = false // Allow greeting for the new conversation
        isWebSearchEnabled = false
        sendGreetingMessage()
        showCustomToast("New conversation started")
    }

    // --- History and Pagination ---
    private fun loadOlderMessages() {
        if (isLoadingMoreMessages) return
        isLoadingMoreMessages = true
        showCustomToast("Loading older messages...") // Optional UI feedback

        val currentTopMessageId = chatAdapter.currentList.firstOrNull { !it.isTyping }?.id

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
            builder.append("\n\n📷 Visual reference:")
            builder.append("\n${it.imageUrl}")
        }

        return builder.toString()
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
            put("max_tokens", 150)
        }

        val request = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .post(json.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull()))
            .addHeader("Authorization", "Bearer $apiKey")
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
    private fun playAudioFromFile(file: File) {
        MediaPlayer().apply {
            setDataSource(file.absolutePath)
            prepare()
            start()
        }
    }

    private fun handleNetworkError(e: IOException) {
        lifecycleScope.launch(Dispatchers.Main) {
            showCustomToast("Network error: ${e.message}")
            removeTypingIndicator()
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
            .addHeader("Authorization", "Bearer $apiKey")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (retryCount > 0) {
                    Log.d("ChatFragment", "Retrying image generation... Attempts left: $retryCount")
                    handleImageGeneration(prompt, retryCount - 1)
                } else {
                    handleNetworkError(e)
                    requireActivity().runOnUiThread {
                        binding.generatingText.visibility = View.GONE
                        binding.downloadButton.visibility = View.GONE
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

    private fun incrementChatCount() {
        val sharedPreferences = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val chatCount = sharedPreferences.getInt(CHAT_COUNT_KEY, 0)
        sharedPreferences.edit().putInt(CHAT_COUNT_KEY, chatCount + 1).apply()
    }

    private fun getChatCount(): Int {
        val sharedPreferences = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return sharedPreferences.getInt(CHAT_COUNT_KEY, 0)
    }

    private fun checkAndShowSubscriptionPrompt() {
        if (!isUserSubscribed && getChatCount() >= SUBSCRIPTION_PROMPT_THRESHOLD) {
            showSubscriptionPrompt()
        }
    }

    private fun showSubscriptionPrompt() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_subscription_app, null)
        val title: TextView = dialogView.findViewById(R.id.title)
        val message: TextView = dialogView.findViewById(R.id.message)
        val lottieAnimationView: LottieAnimationView = dialogView.findViewById(R.id.lottieAnimationView)

        title.text = "Buy Now! 🎉"
        message.text = Html.fromHtml(
            "<font color='#008000'><b>Enjoy unlimited chat</b></font>.<br><br>" +
                    "Unlock all features and enjoy an ad-free experience. 🛒<br><br>" +
                    "Create your own EBook, your own APP with personalized features, generate stunning pictures, wallpapers, icons from simple text. ✨<br><br>" +
                    "<b>Learn anything</b> with our powerful models, from <b>maths</b> and <b>science</b> to <b>coding</b>. 📚💡<br><br>" +
                    "Buy now and get access to all these premium features and more! 🚀"
        )

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setPositiveButton("Buy") { dialog, which ->
                subscriptionClickListener?.onSubscriptionClick()
            }
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            val negativeButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)

            // Customize the buttons
            positiveButton.setTextColor(Color.WHITE)
            positiveButton.setBackgroundColor(Color.parseColor("#FF9800")) // Solid orange color
            negativeButton.setTextColor(Color.BLACK)
            negativeButton.setBackgroundColor(Color.LTGRAY)

            // Set layout parameters with weight
            val layoutParams = LinearLayout.LayoutParams(
                0, // Width set to 0dp to use weight
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                weight = 1f // Equal weight distribution
                setMargins(16, 8, 16, 8)
            }

            positiveButton.layoutParams = layoutParams
            negativeButton.layoutParams = layoutParams

            // Set parent background
            val parent = positiveButton.parent as View
            parent.setBackgroundColor(Color.WHITE)

            // Animation (unchanged)
            val animator = ObjectAnimator.ofFloat(positiveButton, "translationY", 0f, 10f, 0f)
            animator.duration = 1000
            animator.repeatCount = ObjectAnimator.INFINITE
            animator.start()
        }

        dialog.show()
    }

    private fun showSubscriptionRequiredDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Subscription Required")
            .setMessage("This feature requires an active subscription")
            .setPositiveButton("Subscribe") { _, _ ->
                subscriptionClickListener?.onSubscriptionClick()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun handleDalle3Request(message: String) {
        addMessageToChat(message, true)
        handleImageGeneration(message)
        binding.messageEditText.text.clear()
        incrementModelUsage("dall-e-3")
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
                    handleDalle3Request(message)
                } else {
                    showCustomToast("Daily limit for DALL-E 3 reached.")
                }
            }
            "gemini" -> {
                if (checkDailyLimit("gemini", DAILY_LIMIT_GEMINI)) {
                    addMessageToChat(message, true)
                    handleGeminiCompletion(message)
                    binding.messageEditText.text.clear()
                    incrementModelUsage("gemini")
                } else {
                    showCustomToast("Daily limit for Gemini reached.")
                }
            }
            "deepseek" -> {
                if (checkDailyLimit("deepseek", DAILY_LIMIT_DEEPSEEK)) {
                    addMessageToChat(message, true)
                    handleDeepSeekCompletion(message)
                    binding.messageEditText.text.clear()
                    incrementModelUsage("deepseek")
                } else {
                    showCustomToast("Daily limit for DeepSeek reached.")
                }
            }
            "claude-sonnet-4" -> {
                if (checkDailyLimit("claude-sonnet-4", DAILY_LIMIT_CLAUDE_SONNET4)) {
                    addMessageToChat(message, true)
                    handleClaudeCompletion(message, "claude-sonnet-4-20250514")
                    binding.messageEditText.text.clear()
                    incrementModelUsage("claude-sonnet-4")
                } else {
                    showCustomToast("Daily limit for Claude Sonnet 4 reached.")
                }
            }
            "claude-opus-4" -> {
                if (checkDailyLimit("claude-opus-4", DAILY_LIMIT_CLAUDE_OPUS4)) {
                    addMessageToChat(message, true)
                    handleClaudeCompletion(message, "claude-opus-4-20250514")
                    binding.messageEditText.text.clear()
                    incrementModelUsage("claude-opus-4")
                } else {
                    showCustomToast("Daily limit for Claude Opus 4 reached.")
                }
            }
            "o3-mini" -> {
                if (checkDailyLimit("o3-mini", DAILY_LIMIT_GPT4)) {
                    addMessageToChat(message, true)
                    handleReasoningModelCompletion(message, "o3-mini")
                    binding.messageEditText.text.clear()
                    incrementModelUsage("o3-mini")
                } else {
                    showCustomToast("Daily limit for O3 Mini reached.")
                }
            }
            else -> {
                addMessageToChat(message, true)
                if (isUserSubscribed && subscriptionExpirationTime > System.currentTimeMillis() || canSendMessage) {
                    sendMessageToAPI(response)
                    binding.messageEditText.text.clear()
                    if (!isUserSubscribed) {
                        canSendMessage = false
                    }
                } else if (checkDailyMessageLimit()) {
                    incrementMessageCount()
                    sendMessageToAPI(response)
                    binding.messageEditText.text.clear()
                } else {
                    //showInterstitialAd() // Show interstitial ad if daily message limit is reached
                }
                incrementChatCount()
                checkAndShowSubscriptionPrompt()
            }
        }
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
            id = System.currentTimeMillis().toString(),
            content = messageContent,
            isUser = isUser,
            citations = citations,
            followUpQuestions = followUpQuestions,
            containsRichContent = containsRichContent,
            isWebSearchResult = isWebSearchResult // Add this if using
            // timestamp will be set by default in ChatMessage constructor
        )
        addMessageToList(newChatMessage) // Your helper that calls submitList

        conversationHistory.put(JSONObject().apply {
            put("role", if (isUser) "user" else "assistant")
            put("content", messageContent)
        })

        if (!isUser && isTtsEnabled) {
            speakOut(messageContent)
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
        val prompt = "Based on the following response, generate 3 follow-up questions that the user can send to openAI for an answer: $response"

        sendMessageToChatGPT(prompt) { followUpResponse ->
            val questions = followUpResponse.split("\n").filter { it.isNotBlank() }
            requireActivity().runOnUiThread {
                addFollowUpQuestionsToChat(questions)
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
        if (!isUserSubscribed) {
            rewardedAd?.let { ad ->
                ad.show(requireActivity()) { rewardItem: RewardItem ->
                    Log.d("ChatFragment", "User earned the reward.")
                    canSendMessage = true
                    showCustomToast("You can now send a message.")
                    loadRewardedAd()
                }
            } ?: run {
                Log.d("ChatFragment", "The rewarded ad wasn't ready yet.")
                showCustomToast("Free Daily usage is finished.Buy now!& Enjoy unlimited Chat!!")
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
            .addHeader("Authorization", "Bearer $apiKey")
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
        isUserSubscribed = isAdFree
        subscriptionExpirationTime = expirationTime

        if (isAdFree) {
            canSendMessage = true
        } else {
            loadRewardedAd()
        }

        if (expirationTime <= System.currentTimeMillis()) {
            canSendMessage = false
        }
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

        // Submit the new list to the adapter
        chatAdapter.submitList(messagesToLoad.toList()) {
            if (messagesToLoad.isNotEmpty()) {
                binding.recyclerView.smoothScrollToPosition(messagesToLoad.size - 1)
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
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_with_overlay, null)
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(true)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val options = arrayOf(
            "GPT-3.5 Turbo 🚀 - Fast and cost-effective\nExample: Learn Python basics, write essays, or get quick answers.",
            "GPT-4o 🌟 - Advanced reasoning and creativity\nExample: Solve complex math problems, write poetry, or brainstorm ideas.",
            "GPT-4o Mini 🧩 - Lightweight version of GPT-4o\nExample: Quick summaries, simple Q&A, or lightweight tasks.",
            "GPT-4o Search 🔍 - Web-connected AI\nExample: Get latest news, real-time information, and cited sources.",
            "GPT-4o Mini Search 🔍 - Lightweight web-connected AI\nExample: Quick web searches with cited results.",
            "O1 🛠️ - Optimized for specific tasks\nExample: Code debugging, data analysis, or technical documentation.",
            "O1 Mini 🧰 - Lightweight version of O1\nExample: Simple coding help, quick fixes, or small-scale tasks.",
            "O3 Mini 🧠 - Reasoning model for complex problem solving\nExample: Advanced coding, scientific reasoning, or multi-step planning.",
            "GPT-4o Realtime Preview ⏱️ - Real-time updates and previews\nExample: Live coding assistance, real-time editing, or interactive learning.",
            "GPT-4o Audio Preview 🎧 - Audio-based interactions\nExample: Learn languages, practice pronunciation, or listen to stories.", // This is an OpenAI model
            "GPT-4 Turbo 🚄 - High-speed and high-capacity\nExample: Long-form content creation, deep research, or multitasking.",
            "DALL-E 3 🎨 - Generate stunning images\nExample: Create art, design logos, or visualize concepts.",
            "TTS-1 🔊 - Text-to-speech with natural voices\nExample: Listen to articles, practice speeches, or create audiobooks.", // OpenAI TTS
            "Gemini 🔷✨ - Google's AI model (Text)\nExample: Advanced research, creative writing, or coding assistance.",
            "DeepSeek 🐬 - Fast and efficient AI for chat\nExample: Quick answers, challenging math problems, debug complex code, or brainstorm innovative solutions.",
            "GPT-4.1 Mini 🖼️ - Image analysis and understanding\nExample: Analyze images, extract information, or generate descriptions.",
            "Gemini Voice Chat 🎙️ - Google real-time voice\nExample: Engage in spoken dialogue with Gemini.", // ADDED Gemini Voice Chat
            "OpenAI Realtime Voice 🔊 - OpenAI low-latency voice\nExample: Conversational AI with OpenAI.",
            "Claude Sonnet 4 🤖 - Balanced speed and intelligence\nExample: Detailed explanations with moderate latency.",
            "Claude Opus 4 🧠 - Highest reasoning ability\nExample: Complex problem solving and analysis.",
            "Computer Use 🖥️ - Automate tasks via browser screenshots"

        )

        val listView = dialogView.findViewById<ListView>(R.id.optionsListView)
        // Assuming CustomAdapter is correctly defined elsewhere and works with this options array
        val adapter = CustomAdapter(requireContext(), android.R.layout.simple_list_item_1, options)
        listView.adapter = adapter

        listView.setOnItemClickListener { _, _, position, _ ->
            if (isUserSubscribed && subscriptionExpirationTime > System.currentTimeMillis() || canSendMessage) { // Added canSendMessage check
                val selectedModelIdentifier = when (position) {
                    0 -> "gpt-3.5-turbo"
                    1 -> "gpt-4o"
                    2 -> "gpt-4o-mini"
                    3 -> "gpt-4o-search-preview"
                    4 -> "gpt-4o-mini-search-preview"
                    5 -> "o1"
                    6 -> "o1-mini"
                    7 -> "o3-mini"
                    8 -> "gpt-4o-realtime-preview"
                    9 -> "gpt-4o-audio-preview" // This is likely an OpenAI model needing its own handling if different from general text
                    10 -> "gpt-4-turbo"
                    11 -> "dall-e-3"
                    12 -> "tts-1" // This is for OpenAI TTS output, not a conversational model usually
                    13 -> "gemini" // Text-based Gemini
                    14 -> "deepseek"
                    15 -> "gpt-4.1-mini"
                    16 -> "gemini-voice-chat"     // Identifier for Gemini Voice Chat
                    17 -> "openai-realtime-voice"// Identifier for OpenAI Realtime Voice
                    18 -> "claude-sonnet-4"
                    19 -> "claude-opus-4"
                    20 -> "computer-use-preview"
                    else -> "gpt-3.5-turbo"       // Default fallback
                }

                isWebSearchEnabled = selectedModelIdentifier in listOf(
                    "gpt-4o-search-preview",
                    "gpt-4o-mini-search-preview"
                )
                currentModel = selectedModelIdentifier
                
                switchUiForModel(currentModel)
                val displayName = if (position < options.size) options[position].substringBefore(" -") else "Chat"
                updateActiveModelButton(displayName)
                showCustomToast("Switched to $displayName")

                // binding.webSearchToggle.isEnabled = WEB_SEARCH_MODELS.contains(currentModel)

            } else {
                showSubscriptionRequiredDialog()
            }
            dialog.dismiss()
        }

        dialog.setOnDismissListener {
            hideOverlay()
        }

        showOverlay()
        dialog.show()
    }






    private fun handleGeminiCompletion(message: String) {
        val geminiApiKey = "YOUR_GEMINI_API_KEY" // Replace with actual key, ideally from secure storage
        val geminiUrl = "https://generativelanguage.googleapis.com/v1/models/gemini-pro:generateContent"

        // 1. Define 'contentsArray' (for message history)
        val contentsArray = JSONArray().apply {
            chatAdapter.currentList.filterNot { it.isTyping }.forEach { chatMsg ->
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

                                withContext(Dispatchers.Main) { // Switch to Main for UI updates
                                    removeTypingIndicator()
                                    addMessageToChat(
                                        messageContent = reply,
                                        isUser = false,
                                        containsRichContent = determineIfRichContent(reply)
                                    )
                                    // If generateFollowUpQuestions is a suspend function, it needs to be called
                                    // from a coroutine scope or be launched in its own.
                                    // For now, assuming it's not a suspend function or handles its own scope.
                                    generateFollowUpQuestions(reply)
                                    if (isTtsEnabled) {
                                        handleTextToSpeech(reply)
                                    }
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
    // In ChatFragment.kt

    private fun handleDeepSeekCompletion(message: String) {
        val deepSeekApiKey = "sk-365290c51f54434e983914c5fae190a8" // Consider secure storage
        val deepSeekUrl = "https://api.deepseek.com/v1/chat/completions"

        val messagesArray = JSONArray().apply {
            chatAdapter.currentList.filterNot { it.isTyping }.forEach { chatMsg ->
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

                                withContext(Dispatchers.Main) {
                                    removeTypingIndicator()
                                    addMessageToChat( // Your refactored method
                                        messageContent = reply,
                                        isUser = false,
                                        containsRichContent = determineIfRichContent(reply)
                                        // Citations and followUpQuestions can be added here if DeepSeek provides them
                                    )
                                    // The scrolling is now handled inside addMessageToChat (via addMessageToList)

                                    // This call might add follow-up questions to a separate UI element at the bottom
                                    // or it might be intended to add more ChatMessage items.
                                    // If it adds more ChatMessage items, it should also use addMessageToChat.
                                    generateFollowUpQuestions(reply)

                                    if (isTtsEnabled) {
                                        handleTextToSpeech(reply)
                                    }
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

    private fun handleClaudeCompletion(message: String, model: String) {
        val claudeApiKey = "YOUR_CLAUDE_API_KEY" // Replace with your key
        val claudeUrl = "https://api.anthropic.com/v1/messages"

        val messagesArray = JSONArray().apply {
            chatAdapter.currentList.filterNot { it.isTyping }.forEach { chatMsg ->
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
            put("model", model)
            put("messages", messagesArray)
            put("max_tokens", 1024)
        }

        val body = json.toString().toRequestBody("application/json".toMediaTypeOrNull())
        val request = Request.Builder()
            .url(claudeUrl)
            .post(body)
            .addHeader("x-api-key", claudeApiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("Content-Type", "application/json")
            .build()

        Log.d("ChatFragment", "Sending request to Claude: $json")
        showTypingIndicator()

        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) { client.newCall(request).execute() }
                val responseBody = response.body?.string()
                Log.d("ChatFragment", "Claude response: $responseBody")

                if (!response.isSuccessful) {
                    withContext(Dispatchers.Main) {
                        showCustomToast("Claude API error: ${'$'}{response.code}")
                        removeTypingIndicator()
                    }
                    return@launch
                }

                responseBody?.let {
                    try {
                        val jsonResponse = JSONObject(it)
                        val stopReason = jsonResponse.optString("stop_reason")
                        val contentArray = jsonResponse.optJSONArray("content")
                        var reply = ""
                        if (contentArray != null && contentArray.length() > 0) {
                            reply = contentArray.getJSONObject(0).optString("text").trim()
                        }

                        if (stopReason == "refusal") {
                            reply = "Claude refused to answer this request."
                        }

                        withContext(Dispatchers.Main) {
                            removeTypingIndicator()
                            addMessageToChat(
                                messageContent = reply,
                                isUser = false,
                                containsRichContent = determineIfRichContent(reply)
                            )
                            if (isTtsEnabled) {
                                handleTextToSpeech(reply)
                            }
                            incrementInteractionCount()
                        }
                    } catch (e: JSONException) {
                        Log.e("ChatFragment", "Error parsing Claude response", e)
                        withContext(Dispatchers.Main) {
                            removeTypingIndicator()
                            showCustomToast("Failed to parse Claude response")
                        }
                    }
                } ?: withContext(Dispatchers.Main) {
                    removeTypingIndicator()
                    showCustomToast("Empty response from Claude")
                }
            } catch (e: IOException) {
                Log.e("ChatFragment", "Claude request failed", e)
                withContext(Dispatchers.Main) {
                    removeTypingIndicator()
                    showCustomToast("Network error with Claude: ${'$'}{e.message}")
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
                    binding.voiceInputButton.text = "🛑"
                }

                override fun onRmsChanged(rmsdB: Float) {}

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    binding.voiceInputButton.text = "🎤"
                }

                override fun onError(error: Int) {
                    showCustomToast("Error: $error")
                    binding.voiceInputButton.text = "🎤"
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

    private fun handleTextToSpeech(text: String) {
        if (isTtsEnabled) {
            val json = JSONObject().apply {
                put("model", "tts-1")
                put("input", text)
                put("voice", selectedVoice)
            }

            val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
            val request = Request.Builder()
                .url("https://api.openai.com/v1/audio/speech")
                .post(body)
                .addHeader("Authorization", "Bearer $apiKey")
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

    private fun speakOut(text: String) {
        if (isTtsEnabled) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "")
        }
    }

    private fun updateSelectedVoice(voice: String) {
        selectedVoice = voice
        saveSelectedVoice(voice)
        binding.voiceSelectionButton.text = "🎙️ ${voice.replaceFirstChar { it.uppercase() }}"
    }

    private fun updateTtsButtonState() {
        binding.ttsToggleButton.apply {
            backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(
                    context,
                    if (isTtsEnabled) R.color.greenn else R.color.card_surface
                )
            )
            isChecked = isTtsEnabled
        }
    }

    private fun showVoiceSelectionDialog() {
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

    private fun saveSelectedVoice(voice: String) {
        val sharedPreferences = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sharedPreferences.edit().putString(SELECTED_VOICE_KEY, voice).apply()
    }

    private fun loadSelectedVoice(): String {
        val sharedPreferences = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return sharedPreferences.getString(SELECTED_VOICE_KEY, "alloy") ?: "alloy"
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.US)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("ChatFragment", "Language not supported")
                showCustomToast("TTS language not supported")
            } else {
                Log.d("ChatFragment", "TTS initialized successfully")
            }
        } else {
            Log.e("ChatFragment", "TTS initialization failed")
            showCustomToast("TTS initialization failed")
        }
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
            put("max_tokens", 300)
        }

        val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
        val request = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .post(body)
            .addHeader("Authorization", "Bearer $apiKey")
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
        if (isUserSubscribed) {
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
        binding.voiceInputButton.text = "🎤"
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

    private fun toggleFollowUpQuestions() {
        if (isFollowUpQuestionsExpanded) {
            followUpQuestionsScrollView.visibility = View.GONE
            expandFollowUpQuestionsButton.text = "▼"
        } else {
            followUpQuestionsScrollView.visibility = View.VISIBLE
            expandFollowUpQuestionsButton.text = "▲"
        }
        isFollowUpQuestionsExpanded = !isFollowUpQuestionsExpanded
    }

    private fun sendGreetingMessage() {
        val randomGreeting = greetings.random()
        addMessageToChat(randomGreeting, false, containsRichContent = false) // Ensure rich content flag
        // Update isGreetingSent for current conversation if needed,
        // or manage it globally as before.
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
        - **Scan Text** 📷: Scan text from an image or document.
        - **Share Button** 📤: Share the last response from the AI.
        - **Follow-Up Questions** 🔄: Get suggested follow-up questions based on the AI's response.
        
        **GPT Models** 🤖
        
        - **GPT-3.5 Turbo** ⚡: Fast and efficient for most tasks.
        - **GPT-4o** 🧠: Advanced model for more complex queries.
        - **DALL-E 3** 🎨: Generate images from text prompts.
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
        if (isMeetingRecording || currentMeetingAudioFile != null) {
            lifecycleScope.launch {
                processAndSummarizeMeeting()?.let { summary ->
                    addMessageToChat(summary, false)
                }
            }
        }
        _binding = null
        tts?.stop()
        tts?.shutdown()
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

    private fun showSubscriptionDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_subscription_app, null)
        val title: TextView = dialogView.findViewById(R.id.title)
        val message: TextView = dialogView.findViewById(R.id.message)
        val lottieAnimationView: LottieAnimationView = dialogView.findViewById(R.id.lottieAnimationView)

        title.text = "Buy Now! 🎉"
        message.text = Html.fromHtml(
            "<font color='#008000'><b>Enjoy unlimited chat</b></font>.<br><br>" +
                    "Unlock all features and enjoy an ad-free experience. 🛒<br><br>" +
                    "Create your own EBook, your own APP with personalized features, generate stunning pictures, wallpapers, icons from simple text. ✨<br><br>" +
                    "<b>Learn anything</b> with our powerful models, from <b>maths</b> and <b>science</b> to <b>coding</b>. 📚💡<br><br>" +
                    "Buy now and get access to all these premium features and more! 🚀"
        )

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setPositiveButton("Buy") { dialog, which ->
                subscriptionClickListener?.onSubscriptionClick()
            }
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            val negativeButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)

            // Customize the buttons
            positiveButton.setTextColor(Color.WHITE)
            positiveButton.setBackgroundColor(Color.parseColor("#FF9800")) // Solid orange color
            negativeButton.setTextColor(Color.BLACK)
            negativeButton.setBackgroundColor(Color.LTGRAY)

            // Set layout parameters with weight
            val layoutParams = LinearLayout.LayoutParams(
                0, // Width set to 0dp to use weight
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                weight = 1f // Equal weight distribution
                setMargins(16, 8, 16, 8)
            }

            positiveButton.layoutParams = layoutParams
            negativeButton.layoutParams = layoutParams

            // Set parent background
            val parent = positiveButton.parent as View
            parent.setBackgroundColor(Color.WHITE)

            // Animation (unchanged)
            val animator = ObjectAnimator.ofFloat(positiveButton, "translationY", 0f, 10f, 0f)
            animator.duration = 1000
            animator.repeatCount = ObjectAnimator.INFINITE
            animator.start()
        }

        dialog.show()
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

        // Submit the new list to the adapter
        chatAdapter.submitList(messagesToLoad.toList()) {
            if (messagesToLoad.isNotEmpty()) {
                binding.recyclerView.smoothScrollToPosition(messagesToLoad.size - 1)
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
        val sharedPreferences = requireContext().getSharedPreferences("prefs", Context.MODE_PRIVATE)
        val savedChatsJson = sharedPreferences.getString(chatHistoryKey, "[]")
        val loadedMessages = mutableListOf<ChatMessage>()

        val currentConversationId = conversationId ?: return // Don't load if no ID

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
            Log.e("ChatFragment", "Error loading chat history", e)
        }

        chatAdapter.submitList(loadedMessages.toList()) {
            if (loadedMessages.isNotEmpty()) {
                binding.recyclerView.smoothScrollToPosition(loadedMessages.size - 1)
            }
        }
    }

    private fun saveChatHistory() {
        val sharedPreferences = requireContext().getSharedPreferences("prefs", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()

        val currentMessagesToSave = chatAdapter.currentList.filterNot { it.isTyping }
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
            id = messageObject.optString("id", System.currentTimeMillis().toString()),
            content = messageObject.getString("content"),
            isUser = messageObject.getBoolean("isUser"),
            isTyping = messageObject.optBoolean("isTyping", false),
            followUpQuestions = jsonArrayToStringList(messageObject.optJSONArray("followUpQuestions")),
            citations = citationsList,
            timestamp = messageObject.optLong("timestamp", System.currentTimeMillis()),
            containsRichContent = messageObject.optBoolean("containsRichContent", false)
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


            // Add other permission request codes here as needed
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
        val typingMessage = ChatMessage(
            id = "typing_${System.currentTimeMillis()}",
            content = "...", // Content for typing can be minimal
            isUser = false,
            isTyping = true
        )
        addMessageToList(typingMessage)
    }

    private fun removeTypingIndicator() {
        val currentList = chatAdapter.currentList.toMutableList()
        val listChanged = currentList.removeAll { it.isTyping }
        if (listChanged) {
            chatAdapter.submitList(currentList.toList())
        }
    }

    /*private fun stopVoiceRecognition() {
        speechRecognizer?.stopListening()
        showRecordingStatus(false)
        binding.voiceInputButton.text = "🎤"
    }*/

    // In ChatFragment.kt

    private fun shareLastResponse() {
        // Get the current list from the adapter
        val currentChatList = chatAdapter.currentList
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


// --- Response Data Classes ---
data class GeminiTtsResponse(
    val candidates: List<Candidate>?
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