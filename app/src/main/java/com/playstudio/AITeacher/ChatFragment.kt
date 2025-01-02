package com.playstudio.aiteacher

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.text.InputType
import android.util.Log
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.canhub.cropper.CropImage
import com.canhub.cropper.CropImageView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardItem
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.gson.Gson
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.playstudio.aiteacher.databinding.FragmentChatBinding
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.apache.poi.hwpf.HWPFDocument
import org.apache.poi.hwpf.extractor.WordExtractor
import org.apache.poi.openxml4j.opc.OPCPackage
import org.apache.poi.xwpf.extractor.XWPFWordExtractor
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.ooxml.POIXMLDocument
import org.apache.poi.poifs.filesystem.POIFSFileSystem
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.*
import java.util.concurrent.TimeUnit
import java.io.File

class ChatFragment : Fragment(), TextToSpeech.OnInitListener {

    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!
    private lateinit var chatAdapter: ChatAdapter
    private val chatMessages = mutableListOf<ChatMessage>()
    private var rewardedAd: RewardedAd? = null
    private var canSendMessage = false
    private val client = OkHttpClient()
    private val gson = Gson()
    private val apiKey = BuildConfig.API_KEY
    private var currentModel = "gpt-3.5-turbo"
    private var conversationId: String? = null
    private var tts: TextToSpeech? = null
    private var isTtsEnabled = false
    private val chatHistoryKey = "chat_history"
    private var isFollowUpEnabled = true // Default state for follow-up questions
    private var subscriptionClickListener: OnSubscriptionClickListener? = null

    companion object {
        private const val REQUEST_IMAGE_CAPTURE = 1
        private const val CAMERA_REQUEST_CODE = 100
        private const val REQUEST_CODE_SPEECH_INPUT = 2
        private const val RC_SIGN_IN = 9001
        private const val WRITE_EXTERNAL_STORAGE_PERMISSION_REQUEST_CODE = 105
        private const val PICK_DOCUMENT_REQUEST_CODE = 106

        private const val PREFS_NAME = "prefs"
        private const val LAST_SUBSCRIPTION_TOAST_KEY = "last_subscription_toast"
        private const val LAST_AD_TOAST_KEY = "last_ad_toast"
        private const val TOAST_INTERVAL = 24 * 60 * 60 * 1000 // 24 hours in milliseconds

        private const val DAILY_MESSAGE_LIMIT = 10
        private const val LAST_RESET_TIME_KEY = "last_reset_time"
        private const val MESSAGE_COUNT_KEY = "message_count"
        private const val DAILY_TOKEN_LIMIT = 1000 // Define your daily token limit here
    }

    private lateinit var requestPermissionLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var captureImageLauncher: ActivityResultLauncher<Intent>
    private lateinit var cropImageLauncher: ActivityResultLauncher<Intent>

    // Use the SubscriptionViewModel to observe changes
    private val subscriptionViewModel: SubscriptionViewModel by activityViewModels()

    // Caching subscription status and expiration time locally
    private var isUserSubscribed: Boolean = false
    private var subscriptionExpirationTime: Long = 0L

    // Token count and limit for each model
    private val modelTokenCounts = mutableMapOf(
        "gpt-4o" to 0,
        "gpt-4o-mini" to 0,
        "o1" to 0,
        "o1-mini" to 0,
        "gpt-4o-realtime-preview" to 0,
        "gpt-4o-audio-preview" to 0,
        "gpt-4-turbo" to 0,
        "dall-e-3" to 0,
        "tts-1" to 0
    )
    private val modelTokenLimits = mutableMapOf(
        "gpt-4o" to 2000000,
        "gpt-4o-mini" to 2000000,
        "o1" to 2000000,
        "o1-mini" to 2000000,
        "gpt-4o-realtime-preview" to 2000000,
        "gpt-4o-audio-preview" to 2000000,
        "gpt-4-turbo" to 2000000,
        "dall-e-3" to 2000000,
        "tts-1" to 2000000
    )
    private val modelLastResetKeys = mutableMapOf(
        "gpt-4o" to "gpt-4o_last_reset",
        "gpt-4o-mini" to "gpt-4o-mini_last_reset",
        "o1" to "o1_last_reset",
        "o1-mini" to "o1-mini_last_reset",
        "gpt-4o-realtime-preview" to "gpt-4o-realtime-preview_last_reset",
        "gpt-4o-audio-preview" to "gpt-4o-audio-preview_last_reset",
        "gpt-4-turbo" to "gpt-4-turbo_last_reset",
        "dall-e-3" to "dall-e-3_last_reset",
        "tts-1" to "tts-1_last_reset"
    )
    private val modelTokenCountKeys = mutableMapOf(
        "gpt-4o" to "gpt-4o_token_count",
        "gpt-4o-mini" to "gpt-4o-mini_token_count",
        "o1" to "o1_token_count",
        "o1-mini" to "o1-mini_token_count",
        "gpt-4o-realtime-preview" to "gpt-4o-realtime-preview_token_count",
        "gpt-4o-audio-preview" to "gpt-4o-audio-preview_token_count",
        "gpt-4-turbo" to "gpt-4-turbo_token_count",
        "dall-e-3" to "dall-e-3_token_count",
        "tts-1" to "tts-1_token_count"
    )

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
        setHasOptionsMenu(true) // Enable options menu in the fragment
        return binding.root
    }

    override fun onResume() {
        super.onResume()
        // Set up the action bar
        (requireActivity() as? AppCompatActivity)?.supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setHomeAsUpIndicator(R.drawable.ic_back_arrow) // Set your close icon here
        }
    }

    override fun onPause() {
        super.onPause()
        // Hide the back button when the fragment is not visible
        (requireActivity() as? AppCompatActivity)?.supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(false)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                parentFragmentManager.popBackStack()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d("ChatFragment", "onViewCreated called")

        // Retrieve data from arguments
        val suggestedMessage = arguments?.getString("suggested_message")
        val selectedModel = arguments?.getString("selected_model")
        val conversationId = arguments?.getString("conversation_id")

// Use the data to initialize the fragment's content
        if (suggestedMessage != null) {
            binding.messageEditText.setText(suggestedMessage)
        }
        // Initialize the chat with the selected model and conversation ID
        initializeChat(selectedModel, conversationId)

        // Initialize the ChatGPT options button
        binding.chatGptOptionsButton.setOnClickListener {
            showChatGptOptionsDialog()
        }

        // Initialize the history button
        binding.historyButton.setOnClickListener {
            showChatHistoryDialog()
        }

        // Initialize the activity result launchers
        captureImageLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    val imageUri = result.data?.data
                    if (imageUri == null) {
                        // Handle the case where the imageUri is null (e.g., when the image is captured from the camera)
                        val bitmap = result.data?.extras?.get("data") as Bitmap
                        val tempUri = saveImage(bitmap)
                        startImageCrop(tempUri)
                    } else {
                        startImageCrop(imageUri)
                    }
                } else {
                    Log.e("ChatFragment", "Image capture failed.")
                }
            }  // Call the function to check POI class paths
        checkPOIClassPaths()

        cropImageLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    val resultUri: Uri? = CropImage.getActivityResult(result.data)?.uri
                    resultUri?.let {
                        val bitmap = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                            ImageDecoder.decodeBitmap(ImageDecoder.createSource(requireContext().contentResolver, it))
                        } else {
                            MediaStore.Images.Media.getBitmap(requireContext().contentResolver, it)
                        }
                        detectTextFromImage(bitmap)
                    }
                } else {
                    Log.e("ChatFragment", "Image cropping failed.")
                }
            }

        requestPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val isCameraPermissionGranted = permissions[Manifest.permission.CAMERA] ?: false
            if (isCameraPermissionGranted) {
                dispatchTakePictureIntent()
            } else {
                showCustomToast("Camera permission is required to use this feature")
            }
        }

        // Initialize follow-up toggle button
        binding.followUpToggle.setOnCheckedChangeListener { _, isChecked ->
            isFollowUpEnabled = isChecked
        }

        // Initialize chatAdapter
        chatAdapter = ChatAdapter(chatMessages, binding)
        Log.d("ChatFragment", "ChatAdapter initialized")

        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = chatAdapter
            setHasFixedSize(true) // Use this if the size of the RecyclerView does not change
            itemAnimator = null // Disable default item animator if not needed
            setItemViewCacheSize(20)
            isDrawingCacheEnabled = true
            drawingCacheQuality = View.DRAWING_CACHE_QUALITY_HIGH
        }
        Log.d("ChatFragment", "RecyclerView initialized")

        // Log the size of chatMessages list
        Log.d("ChatFragment", "chatMessages size: ${chatMessages.size}")
        // Set the title to "Chat"
        (requireActivity() as? AppCompatActivity)?.supportActionBar?.title = "Chat with AITeacher"

        // Initialize Text-to-Speech
        tts = TextToSpeech(requireContext(), this)

        // Set up button clicks
        binding.sendButton.setOnClickListener {
            val userMessage = binding.messageEditText.text.toString()
            Log.d("ChatFragment", "Send button clicked with message: $userMessage")
            if (userMessage.isNotEmpty()) {
                if (isUserSubscribed && subscriptionExpirationTime > System.currentTimeMillis() || canSendMessage) { // Fix the condition
                    Log.d("ChatFragment", "User is subscribed or can send message")
                    handleMessage(userMessage)
                } else {
                    Log.d("ChatFragment", "User is subscribed or can send message")
                    showRewardedAd()
                }
            } else {
                Log.d("ChatFragment", "User message is empty")
            }
        }

        binding.scanTextButton.setOnClickListener { showImageOrDocumentPickerDialog() }

        binding.voiceInputButton.setOnClickListener { startVoiceRecognition() }

        binding.shareButton.setOnClickListener { shareLastResponse() }

        // Apply the fading background to the share button
        binding.shareButton.background =
            ContextCompat.getDrawable(requireContext(), R.drawable.fading_background)

        // Check if there is a suggested message to pre-fill
        arguments?.getString("suggested_message")?.let { suggestedMessage ->
            binding.messageEditText.setText(suggestedMessage)
        }

        // Load chat history if available
        loadChatHistory()

        // Check if there is a conversation JSON to load
        arguments?.getString("conversation_json")?.let { conversationJson ->
            loadConversationFromJson(conversationJson)
        }

        PDFBoxResourceLoader.init(requireContext())

        // Initialize the permission launcher
        requestPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val isWritePermissionGranted =
                permissions[Manifest.permission.WRITE_EXTERNAL_STORAGE] ?: false
            val isReadPermissionGranted =
                permissions[Manifest.permission.READ_EXTERNAL_STORAGE] ?: false

            // For media-specific permissions on Android 13+
            val isReadMediaImagesGranted =
                permissions[Manifest.permission.READ_MEDIA_IMAGES] ?: false
            val isReadMediaVideoGranted = permissions[Manifest.permission.READ_MEDIA_VIDEO] ?: false
            val isReadMediaAudioGranted = permissions[Manifest.permission.READ_MEDIA_AUDIO] ?: false

            if (isWritePermissionGranted || isReadPermissionGranted || isReadMediaImagesGranted || isReadMediaVideoGranted || isReadMediaAudioGranted) {

            } else {
                showCustomToast("Storage permission is required to save the document")
            }
        }

        // Observe changes in the subscription status
        subscriptionViewModel.isAdFree.observe(viewLifecycleOwner, Observer { isAdFree ->
            updateSubscriptionStatus(isAdFree, subscriptionViewModel.expirationTime.value ?: 0L)
        })

        subscriptionViewModel.expirationTime.observe(
            viewLifecycleOwner,
            Observer { expirationTime ->
                updateSubscriptionStatus(
                    subscriptionViewModel.isAdFree.value ?: false,
                    expirationTime
                )
            })

        binding.messageEditText.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                hideKeyboard()
                binding.messageEditText.clearFocus()
            }
            false
        }

        // Set up button clicks
        binding.sendButton.setOnClickListener {
            val userMessage = binding.messageEditText.text.toString()
            if (userMessage.isNotEmpty()) {
                // Hide the keyboard when the send button is clicked
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

        // Send a greeting message when the chat is first opened
        sendGreetingMessage()

        // Add copy functionality to the EditText
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
    }

    private fun copyHighlightedText() {
        val start = binding.messageEditText.selectionStart
        val end = binding.messageEditText.selectionEnd
        val selectedText = binding.messageEditText.text.substring(start, end)

        val clipboard =
            requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Copied Text", selectedText)
        clipboard.setPrimaryClip(clip)

        showCustomToast("Text copied to clipboard")
    }

    private fun hideKeyboard() {
        val imm =
            requireActivity().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val currentFocus = requireActivity().currentFocus
        if (currentFocus is EditText) {
            imm.hideSoftInputFromWindow(currentFocus.windowToken, 0)
            currentFocus.clearFocus() // Clear focus from the EditText
        } else {
            // If nothing is focused, hide the keyboard
            imm.hideSoftInputFromWindow(view?.windowToken, 0)
        }
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        tts?.stop()
        tts?.shutdown()
    }

    private fun speakOut(text: String) {
        if (isTtsEnabled) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "")
        }
    }
    private fun initializeChat(model: String?, conversationId: String?) {
        // Use the model and conversation ID to initialize the chat
        // For example, you can set the model and conversation ID in the ViewModel or other components
        // This is a placeholder implementation
        if (model != null && conversationId != null) {
            // Initialize the chat with the provided model and conversation ID
            // Example:
            // chatViewModel.initializeChat(model, conversationId)
        }
    }


    private fun addMessageToChat(message: String, isUser: Boolean) {
        val newChatMessages = chatMessages.toMutableList()
        newChatMessages.add(ChatMessage(message, isUser))
        chatAdapter.updateChatMessages(newChatMessages)
        binding.recyclerView.scrollToPosition(newChatMessages.size - 1)
        saveChatHistory()

        // Speak out the message if TTS is enabled and it's an AI response
        if (!isUser && isTtsEnabled) {
            speakOut(message)
        }
    }

    // Function to detect text from image
    private fun detectTextFromImage(imageBitmap: Bitmap) {
        try {
            // Create an InputImage object from the Bitmap
            val image = InputImage.fromBitmap(imageBitmap, 0)

            // Get an instance of TextRecognizer
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

            // Process the image using the recognizer
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    // Task completed successfully
                    processTextBlock(visionText)
                }
                .addOnFailureListener { e ->
                    // Task failed with an exception
                    Log.e("ChatFragment", "Failed to detect text", e)
                    requireActivity().runOnUiThread {
                        showCustomToast("Failed to detect text")
                    }
                }
        } catch (e: Exception) {
            Log.e("ChatFragment", "Exception in detectTextFromImage", e)
            requireActivity().runOnUiThread {
                showCustomToast("An error occurred while detecting text")
            }
        }
    }

    private fun showSubscriptionDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Subscription Required")
            .setMessage("Please purchase a subscription to access this feature.")
            .setPositiveButton("Buy") { dialog, which ->
                subscriptionClickListener?.onSubscriptionClick()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // Process the recognized text blocks
    private fun processTextBlock(result: Text) {
        val resultText = result.text
        Log.d("ChatFragment", "Detected Text: $resultText")

        // Update UI with detected text
        if (resultText.isNotEmpty()) {
            requireActivity().runOnUiThread {
                binding.messageEditText.setText(resultText)
            }
        } else {
            requireActivity().runOnUiThread {
                showCustomToast("No text detected.")
            }
        }

        // Iterate through the text blocks, lines, and elements
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

    private fun addFollowUpQuestionsToChat(questions: List<String>) {
        if (isFollowUpEnabled) {
            binding.followUpQuestionsContainer.removeAllViews()
            for (question in questions) {
                val button = Button(requireContext()).apply {
                    text = question
                    textSize = 12f
                    setTypeface(typeface, Typeface.NORMAL) // Set to normal font style
                    background = ContextCompat.getDrawable(
                        requireContext(),
                        R.drawable.rounded_button
                    ) // Set circular background

                    // Set layout parameters with margin
                    val params = ViewGroup.MarginLayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        // Set margins (1mm = approximately 3.78 pixels)
                        setMargins(
                            0,
                            0,
                            0,
                            (1 * resources.displayMetrics.density).toInt()
                        ) // Apply bottom margin
                    }
                    layoutParams = params // Apply the params to the button

                    setOnClickListener {
                        binding.messageEditText.setText(question)
                    }
                }
                binding.followUpQuestionsContainer.addView(button)
            }
            binding.followUpQuestionsContainer.visibility = View.VISIBLE
        } else {
            binding.followUpQuestionsContainer.visibility = View.GONE
        }
    }

    private fun sendMessageToAPI(message: String) {
        if (currentModel != "gpt-3.5-turbo") {
            val sharedPreferences =
                requireContext().getSharedPreferences("prefs", Context.MODE_PRIVATE)
            val lastResetTime = sharedPreferences.getLong(modelLastResetKeys[currentModel]!!, 0)
            val currentTime = System.currentTimeMillis()

            if (currentTime - lastResetTime > TimeUnit.HOURS.toMillis(24)) {
                // Reset the counter if 24 hours have passed
                modelTokenCounts[currentModel] = 0
                sharedPreferences.edit().putLong(modelLastResetKeys[currentModel]!!, currentTime).apply()
            }

            if (modelTokenCounts[currentModel]!! >= modelTokenLimits[currentModel]!!) {
                showCustomToast("$currentModel token limit reached. Please switch to GPT 3.5.")
                return
            }
        }

        val messagesArray = JSONArray()
        for (chatMessage in chatMessages) {
            val messageObject = JSONObject().apply {
                put("role", if (chatMessage.isUser) "user" else "assistant")
                put("content", chatMessage.content)
            }
            messagesArray.put(messageObject)
        }

        val json = JSONObject().apply {
            put("model", currentModel)
            put("messages", messagesArray)
        }

        val body =
            json.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
        val request = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .post(body)
            .addHeader("Authorization", "Bearer $apiKey")
            .build()

        Log.d("ChatFragment", "Sending request: $json")

        // Show typing indicator
        showTypingIndicator()

        // Use coroutine to handle the network request
        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    client.newCall(request).execute()
                }

                val responseBody = response.body?.string()
                Log.d("ChatFragment", "Received response: $responseBody")

                if (!response.isSuccessful) {
                    Log.e("ChatFragment", "Unexpected code $response")
                    withContext(Dispatchers.Main) {
                        when (response.code) {
                            400 -> showCustomToast("Bad Request: Check your request parameters")
                            401 -> showCustomToast("Unauthorized: Check your API key")
                            403 -> showCustomToast("Forbidden: You don't have permission to access this resource")
                            500 -> showCustomToast("Server Error: Try again later")
                            else -> showCustomToast("Unexpected response from server")
                        }
                        if (currentModel != "gpt-3.5-turbo") {
                            handleModelError()
                        }
                        removeTypingIndicator()
                    }
                    return@launch
                }

                responseBody?.let {
                    try {
                        val jsonResponse = JSONObject(it)
                        Log.d("ChatFragment", "Parsed JSON response: $jsonResponse")
                        if (jsonResponse.has("choices")) {
                            val choices = jsonResponse.getJSONArray("choices")
                            if (choices.length() > 0) {
                                val reply = choices.getJSONObject(0).getJSONObject("message")
                                    .getString("content").trim()
                                withContext(Dispatchers.Main) {
                                    // Remove typing indicator before adding the reply
                                    removeTypingIndicator()
                                    // Add the reply to chat messages
                                    chatMessages.add(ChatMessage(reply, isUser = false))
                                    // Notify the adapter of the new message
                                    chatAdapter.notifyItemInserted(chatMessages.size - 1)
                                    // Scroll to the bottom of the RecyclerView
                                    binding.recyclerView.scrollToPosition(chatMessages.size - 1)
                                    // Generate follow-up questions
                                    generateFollowUpQuestions(reply)
                                }
                                // Update token count
                                if (currentModel != "gpt-3.5-turbo") {
                                    val usage = jsonResponse.getJSONObject("usage")
                                    val totalTokens = usage.getInt("total_tokens")
                                    updateModelTokenCount(totalTokens)
                                }
                            } else {
                                withContext(Dispatchers.Main) {
                                    showCustomToast("No choices found in the response")
                                    if (currentModel != "gpt-3.5-turbo") {
                                        handleModelError()
                                    }
                                    removeTypingIndicator()
                                }
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                showCustomToast("No choices found in the response")
                                if (currentModel != "gpt-3.5-turbo") {
                                    handleModelError()
                                }
                                removeTypingIndicator()
                            }
                        }
                    } catch (e: JSONException) {
                        Log.e("ChatFragment", "Failed to parse response", e)
                        withContext(Dispatchers.Main) {
                            showCustomToast("Failed to parse response")
                            if (currentModel != "gpt-3.5-turbo") {
                                handleModelError()
                            }
                            removeTypingIndicator()
                        }
                    }
                }
            } catch (e: IOException) {
                Log.e("ChatFragment", "Failed to get response", e)
                withContext(Dispatchers.Main) {
                    showCustomToast("Failed to get response")
                    if (currentModel != "gpt-3.5-turbo") {
                        handleModelError()
                    }
                    removeTypingIndicator()
                }
            }
        }
    }

    private fun generateFollowUpQuestions(response: String) {
        val prompt = "Based on the following response, generate 3 follow-up questions: $response"

        sendMessageToChatGPT(prompt) { followUpResponse ->
            val questions = followUpResponse.split("\n").filter { it.isNotBlank() }
            requireActivity().runOnUiThread {
                addFollowUpQuestionsToChat(questions)
            }
        }
    }

    private fun updateModelTokenCount(tokens: Int) {
        val sharedPreferences = requireContext().getSharedPreferences("prefs", Context.MODE_PRIVATE)
        modelTokenCounts[currentModel] = modelTokenCounts[currentModel]!! + tokens
        sharedPreferences.edit().putInt(modelTokenCountKeys[currentModel]!!, modelTokenCounts[currentModel]!!).apply()
    }

    private fun handleModelError() {
        // Decrement the model token count if an error occurs
        modelTokenCounts[currentModel] = maxOf(0, modelTokenCounts[currentModel]!! - 100) // Example decrement value
        val sharedPreferences = requireContext().getSharedPreferences("prefs", Context.MODE_PRIVATE)
        sharedPreferences.edit().putInt(modelTokenCountKeys[currentModel]!!, modelTokenCounts[currentModel]!!).apply()
        showCustomToast("An error occurred. $currentModel token count has been adjusted.")
    }

    private fun loadRewardedAd() {
        val adRequest = AdRequest.Builder().build()
        RewardedAd.load(
            requireContext(),
            "ca-app-pub-9180832030816304/2247664120", // Replace with your Ad Unit ID
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
        if (!isUserSubscribed) { // Only show ad if the user is not subscribed
            rewardedAd?.let { ad ->
                ad.show(requireActivity()) { rewardItem: RewardItem ->
                    // Handle the reward.
                    Log.d("ChatFragment", "User earned the reward.")
                    canSendMessage = true // Allow sending messages after watching the ad
                    showCustomToast("You can now send a message.")
                    loadRewardedAd()  // Load the next ad
                }
            } ?: run {
                Log.d("ChatFragment", "The rewarded ad wasn't ready yet.")
                showCustomToast("Free Daily usage is finished. Please Watch AD or Buy to continue with Chat!!")
                loadRewardedAd()  // Load the next ad
            }
        } else {
            showCustomToast("The ad is not ready yet. Please try again later.")
        }
    }

    private fun showCustomToast(message: String) {
        if (isAdded) {
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        } else {
            Log.w("ChatFragment", "Cannot show toast: Fragment not attached to context.")
        }
    }

    private fun loadConversationFromJson(conversationJson: String) {
        try {
            val messagesArray = JSONArray(conversationJson)
            for (i in 0 until messagesArray.length()) {
                val messageObject = messagesArray.getJSONObject(i)
                val content = messageObject.getString("content")
                val isUser = messageObject.getBoolean("isUser")
                chatMessages.add(ChatMessage(content, isUser))
            }
            chatAdapter.notifyDataSetChanged()
            binding.recyclerView.scrollToPosition(chatMessages.size - 1)
        } catch (e: JSONException) {
            Log.e("ChatFragment", "Failed to parse conversation JSON", e)
            showCustomToast("Failed to load conversation")
        }
    }

    private fun generateConversationId(): String {
        return "conversation_${System.currentTimeMillis()}"
    }

    private fun saveChatHistory() {
        val sharedPreferences = requireContext().getSharedPreferences("prefs", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        val messagesArray = JSONArray()
        for (chatMessage in chatMessages) {
            val messageObject = JSONObject().apply {
                put("content", chatMessage.content)
                put("isUser", chatMessage.isUser)
            }
            messagesArray.put(messageObject)
        }

        val chatObject = JSONObject().apply {
            put("id", conversationId ?: generateConversationId())
            put("title", "Chat with $currentModel on ${System.currentTimeMillis()}")
            put("messages", messagesArray)
        }

        val savedChatsArray = JSONArray(sharedPreferences.getString(chatHistoryKey, "[]"))
        val updatedChatsArray = JSONArray()

        for (i in 0 until savedChatsArray.length()) {
            val chat = savedChatsArray.getJSONObject(i)
            if (chat.optString("id") != conversationId) {
                updatedChatsArray.put(chat)
            }
        }

        updatedChatsArray.put(chatObject)
        editor.putString(chatHistoryKey, updatedChatsArray.toString())
        editor.apply()
    }

    private fun loadChatHistory() {
        val sharedPreferences = requireContext().getSharedPreferences("prefs", Context.MODE_PRIVATE)
        val savedChatsArray = JSONArray(sharedPreferences.getString(chatHistoryKey, "[]"))

        for (i in 0 until savedChatsArray.length()) {
            val chatObject = savedChatsArray.getJSONObject(i)
            if (chatObject.optString("id") == conversationId) {
                val messagesArray = chatObject.getJSONArray("messages")
                for (j in 0 until messagesArray.length()) {
                    val messageObject = messagesArray.getJSONObject(j)
                    val content = messageObject.getString("content")
                    val isUser = messageObject.getBoolean("isUser")
                    chatMessages.add(ChatMessage(content, isUser))
                }
                chatAdapter.notifyDataSetChanged()
                binding.recyclerView.scrollToPosition(chatMessages.size - 1)
                break
            }
        }
    }

    private fun checkCameraPermission() {
        checkAndRequestPermissions(arrayOf(Manifest.permission.CAMERA), CAMERA_REQUEST_CODE)
    }

    private fun checkAndRequestPermissions(permissions: Array<String>, requestCode: Int) {
        val permissionsToRequest = permissions.filter {
            ContextCompat.checkSelfPermission(
                requireContext(),
                it
            ) != PackageManager.PERMISSION_GRANTED
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
            WRITE_EXTERNAL_STORAGE_PERMISSION_REQUEST_CODE -> savePdfDocument(
                lastGeneratedResponse,
                "Generated_CV"
            )

            PICK_DOCUMENT_REQUEST_CODE -> openDocumentPicker()
        }
    }

    private fun dispatchTakePictureIntent() {
        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (takePictureIntent.resolveActivity(requireActivity().packageManager) != null) {
            captureImageLauncher.launch(takePictureIntent)
        }
    }
    // Function to save Bitmap as an image and get its URI
    private fun saveImage(bitmap: Bitmap): Uri {
        try {
            // Check the quality of the Bitmap
            checkBitmapQuality(bitmap)

            // Ensure Bitmap is in ARGB_8888 configuration
            val convertedBitmap = convertToARGB8888(bitmap)

            // Check the quality of the converted Bitmap
            checkBitmapQuality(convertedBitmap)

            val filename = "IMG_${System.currentTimeMillis()}.png" // Change extension to .png
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png") // Change MIME type to image/png
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
                put(MediaStore.Images.Media.IS_PENDING, true)
            }

            val resolver = requireContext().contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)

            uri?.let {
                resolver.openOutputStream(it)?.use { outputStream ->
                    convertedBitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream) // Use PNG compression
                }
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, false)
                resolver.update(uri, values, null, null)
            }

            return uri ?: throw IOException("Failed to create new MediaStore record.")
        } catch (e: Exception) {
            Log.e("ChatFragment", "Exception in saveImage", e)
            throw IOException("Failed to save image", e)
        }
    }

    // Function to check the quality of a Bitmap
    private fun checkBitmapQuality(bitmap: Bitmap) {
        // Check resolution
        val width = bitmap.width
        val height = bitmap.height
        Log.d("BitmapQuality", "Resolution: ${width}x${height}")

        // Check file size
        val byteArrayOutputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream) // Use PNG compression
        val byteArray = byteArrayOutputStream.toByteArray()
        val fileSizeInKB = byteArray.size / 1024
        Log.d("BitmapQuality", "File size: ${fileSizeInKB}KB")

        // Check color depth
        val colorDepth = when (bitmap.config) {
            Bitmap.Config.ALPHA_8 -> 8
            Bitmap.Config.RGB_565 -> 16
            Bitmap.Config.ARGB_4444 -> 16
            Bitmap.Config.ARGB_8888 -> 32
            else -> 0
        }
        Log.d("BitmapQuality", "Color depth: ${colorDepth}-bit")
    }

    // Function to convert Bitmap to ARGB_8888 configuration
    private fun convertToARGB8888(bitmap: Bitmap): Bitmap {
        return if (bitmap.config != Bitmap.Config.ARGB_8888) {
            val argbBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
            Log.d("BitmapConversion", "Converted Bitmap to ARGB_8888")
            argbBitmap
        } else {
            bitmap
        }
    }

    // Function to start image cropping
    private fun startImageCrop(uri: Uri) {
        val cropIntent = CropImage.activity(uri)
            .setGuidelines(CropImageView.Guidelines.ON)
            .getIntent(requireContext())
        cropImageLauncher.launch(cropIntent)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            CAMERA_REQUEST_CODE -> {
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    dispatchTakePictureIntent()
                } else {
                    showCustomToast("Camera permission is required to use this feature")
                }
            }

            WRITE_EXTERNAL_STORAGE_PERMISSION_REQUEST_CODE -> {
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    savePdfDocument(lastGeneratedResponse, "Generated_CV")
                } else {
                    showCustomToast("Storage permission is required to save the document")
                }
            }

            PICK_DOCUMENT_REQUEST_CODE -> {
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    openDocumentPicker()
                } else {
                    showCustomToast("Storage permission is required to pick a document")
                }
            }
        }
    }

    private lateinit var lastGeneratedResponse: String

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
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
    }

    private fun startVoiceRecognition() {
        if (!SpeechRecognizer.isRecognitionAvailable(requireContext())) {
            showCustomToast("Speech recognition is not available on this device")
            return
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak now")
        }

        startActivityForResult(intent, REQUEST_CODE_SPEECH_INPUT)
    }

    private fun showTypingIndicator() {
        chatMessages.add(ChatMessage("...", false, isTyping = true))
        chatAdapter.notifyItemInserted(chatMessages.size - 1)
        binding.recyclerView.scrollToPosition(chatMessages.size - 1)
    }

    private fun removeTypingIndicator() {
        if (chatMessages.isNotEmpty() && chatMessages.last().isTyping) {
            chatMessages.removeAt(chatMessages.size - 1)
            chatAdapter.notifyItemRemoved(chatMessages.size - 1)
        }
    }

    private fun shareLastResponse() {
        if (chatMessages.isNotEmpty()) {
            val lastMessage = chatMessages.lastOrNull { !it.isUser }?.content

            if (lastMessage != null && lastMessage.isNotBlank()) {
                val shareIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, lastMessage)
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

    private fun hasPermissions(context: Context, vararg permissions: String): Boolean {
        return permissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun handleMessage(message: String) {
        when {
            else -> {
                addMessageToChat(message, true)
                if (isUserSubscribed && subscriptionExpirationTime > System.currentTimeMillis() || canSendMessage) { // Fix the condition
                    sendMessageToAPI(message)
                    binding.messageEditText.text.clear()
                    if (!isUserSubscribed) {
                        canSendMessage =
                            false // Reset the flag after sending the message if the user is not subscribed
                    }
                } else if (checkDailyMessageLimit()) {
                    incrementMessageCount()
                    sendMessageToAPI(message)
                    binding.messageEditText.text.clear()
                } else {
                    showRewardedAd() // Show ad if the user is not subscribed and daily limit is reached
                }
            }
        }
    }

    private fun savePdfDocument(content: String, fileName: String) {
        val resolver = requireContext().contentResolver
        val pdfFileName = "$fileName.pdf"

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, pdfFileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS)
        }

        val uri = resolver.insert(MediaStore.Files.getContentUri("external"), contentValues)

        uri?.let {
            try {
                resolver.openFileDescriptor(uri, "w")?.use { descriptor ->
                    FileOutputStream(descriptor.fileDescriptor).use { outputStream ->
                        //  documentGeneration.writeStringToPdf(content, outputStream)
                    }
                }
                showCustomToast("PDF saved to Documents")
            } catch (e: IOException) {
                Log.e("ChatFragment", "Failed to save PDF", e)
                showCustomToast("Failed to save PDF")
            }
        } ?: run {
            showCustomToast("Failed to create new MediaStore record.")
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
        }

        val body =
            json.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
        val request = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .post(body)
            .addHeader("Authorization", "Bearer $apiKey")
            .build()

        Log.d("ChatFragment", "Sending request: $json")

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("ChatFragment", "Failed to get response", e)
                requireActivity().runOnUiThread {
                    showCustomToast("Failed to get response")
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                Log.d("ChatFragment", "Received response: $responseBody")

                if (!response.isSuccessful || responseBody == null) {
                    Log.e("ChatFragment", "Unexpected code $response")
                    requireActivity().runOnUiThread {
                        showCustomToast("Unexpected response from server")
                    }
                    return
                }

                val jsonResponse = JSONObject(responseBody)
                val choices = jsonResponse.optJSONArray("choices")

                if (choices != null && choices.length() > 0) {
                    val reply =
                        choices.getJSONObject(0).getJSONObject("message").getString("content")
                            .trim()
                    requireActivity().runOnUiThread {
                        callback(reply)
                    }
                } else {
                    requireActivity().runOnUiThread {
                        showCustomToast("No choices found in the response")
                    }
                }
            }
        })
    }

    private fun checkPOIClassPaths() {
        try {
            val poiDocumentProtectionDomain = POIXMLDocument::class.java.protectionDomain
            val poiFileSystemProtectionDomain = POIFSFileSystem::class.java.protectionDomain

            if (poiDocumentProtectionDomain != null) {
                Log.d(
                    "ChatFragment",
                    "Document class path: ${poiDocumentProtectionDomain.codeSource.location.path}"
                )
            } else {
                Log.e("ChatFragment", "POIXMLDocument protection domain is null")
            }

            if (poiFileSystemProtectionDomain != null) {
                Log.d(
                    "ChatFragment",
                    "POIFSFileSystem class path: ${poiFileSystemProtectionDomain.codeSource.location.path}"
                )
            } else {
                Log.e("ChatFragment", "POIFSFileSystem protection domain is null")
            }
        } catch (e: Exception) {
            Log.e("ChatFragment", "Error checking POI class paths", e)
        }
    }

    fun updateSubscriptionStatus(isAdFree: Boolean, expirationTime: Long) {
        // Cache the subscription status and expiration time
        isUserSubscribed = isAdFree
        subscriptionExpirationTime = expirationTime

        // Update the state of the rewarded ad based on subscription status
        if (isAdFree) {
            // Disable rewarded ads
            canSendMessage = true // Allow sending messages
        } else {
            // Enable rewarded ads
            loadRewardedAd() // Load a new ad
        }

        // Handle expiration time if needed
        if (expirationTime <= System.currentTimeMillis()) {
            // Subscription has expired
            canSendMessage = false // Disable sending messages until the user watches an ad
        }
    }

    private fun openDocumentPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(
                Intent.EXTRA_MIME_TYPES,
                arrayOf(
                    "application/pdf",
                    "application/msword",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                )
            )
        }
        startActivityForResult(intent, PICK_DOCUMENT_REQUEST_CODE)
    }

    private fun handleDocUpload(uri: Uri) {
        try {
            val inputStream = requireContext().contentResolver.openInputStream(uri)
            val mimeType = requireContext().contentResolver.getType(uri)
            when (mimeType) {
                "application/msword" -> {
                    val doc = HWPFDocument(inputStream)
                    val extractor = WordExtractor(doc)
                    val text = extractor.text
                    binding.messageEditText.setText(text)
                }

                "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> {
                    val doc = XWPFDocument(OPCPackage.open(inputStream))
                    val extractor = XWPFWordExtractor(doc)
                    val text = extractor.text
                    binding.messageEditText.setText(text)
                }

                "application/pdf" -> {
                    try {
                        val pdfDocument = PDDocument.load(inputStream)
                        val pdfStripper = PDFTextStripper()
                        val text = pdfStripper.getText(pdfDocument)
                        pdfDocument.close()
                        binding.messageEditText.setText(text)
                    } catch (e: IOException) {
                        if (e.message?.contains("GlyphList") == true) {
                            Log.e("ChatFragment", "GlyphList file not found", e)
                            showCustomToast("Error: GlyphList file not found. Please ensure the PDFBox resources are correctly included.")
                        } else {
                            Log.e("ChatFragment", "Failed to read PDF document", e)
                            showCustomToast("Failed to read PDF document")
                        }
                    } catch (e: Exception) {
                        Log.e("ChatFragment", "Unexpected error while reading PDF document", e)
                        showCustomToast("Unexpected error while reading PDF document")
                    }
                }

                else -> {
                    showCustomToast("Unsupported file format")
                }
            }
        } catch (e: IOException) {
            Log.e("ChatFragment", "Failed to read document", e)
            showCustomToast("Failed to read document")
        }
    }

    private fun showImageOrDocumentPickerDialog() {
        val options = arrayOf("Capture Image", "Pick Document")
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("Choose an option")
        builder.setItems(options) { dialog, which ->
            when (which) {
                0 -> checkCameraPermission()
                1 -> openDocumentPicker()
            }
        }
        builder.show()
    }

    private fun sendGreetingMessage() {
        val greetingMessage = "Hello! How can I assist you today?"
        addMessageToChat(greetingMessage, false)
    }

    private fun checkDailyMessageLimit(): Boolean {
        val sharedPreferences =
            requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastResetTime = sharedPreferences.getLong(LAST_RESET_TIME_KEY, 0)
        val currentTime = System.currentTimeMillis()

        // Reset the message count if 24 hours have passed since the last reset
        if (currentTime - lastResetTime > TimeUnit.HOURS.toMillis(24)) {
            sharedPreferences.edit().putLong(LAST_RESET_TIME_KEY, currentTime)
                .putInt(MESSAGE_COUNT_KEY, 0).apply()
        }

        val messageCount = sharedPreferences.getInt(MESSAGE_COUNT_KEY, 0)
        return messageCount < DAILY_MESSAGE_LIMIT
    }

    private fun incrementMessageCount() {
        val sharedPreferences =
            requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val messageCount = sharedPreferences.getInt(MESSAGE_COUNT_KEY, 0)
        sharedPreferences.edit().putInt(MESSAGE_COUNT_KEY, messageCount + 1).apply()
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

    private fun loadChatHistoryById(chatId: String) {
        val sharedPreferences = requireContext().getSharedPreferences("prefs", Context.MODE_PRIVATE)
        val savedChatsArray = JSONArray(sharedPreferences.getString(chatHistoryKey, "[]"))

        chatMessages.clear()

        for (i in 0 until savedChatsArray.length()) {
            val chatObject = savedChatsArray.getJSONObject(i)
            if (chatObject.getString("id") == chatId) {
                val messagesArray = chatObject.getJSONArray("messages")
                for (j in 0 until messagesArray.length()) {
                    val messageObject = messagesArray.getJSONObject(j)
                    val content = messageObject.getString("content")
                    val isUser = messageObject.getBoolean("isUser")
                    chatMessages.add(ChatMessage(content, isUser))
                }
                chatAdapter.notifyDataSetChanged()
                binding.recyclerView.scrollToPosition(chatMessages.size - 1)
                break
            }
        }
    }

    private fun showChatGptOptionsDialog() {
        showCustomDialogWithOverlay()
    }

    private fun showCustomDialogWithOverlay() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_with_overlay, null)
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(true)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val options = arrayOf(
            "GPT-3.5 Turbo",
            "GPT-4o",
            "GPT-4o Mini",
            "O1",
            "O1 Mini",
            "GPT-4o Realtime Preview",
            "GPT-4o Audio Preview",
            "GPT-4 Turbo",
            "DALL-E 3",
            "TTS-1"
        )

        val listView = dialogView.findViewById<ListView>(R.id.optionsListView)
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, options)
        listView.adapter = adapter

        listView.setOnItemClickListener { _, _, position, _ ->
            if (isUserSubscribed && subscriptionExpirationTime > System.currentTimeMillis()) {
                currentModel = when (position) {
                    0 -> "gpt-3.5-turbo"
                    1 -> "gpt-4o"
                    2 -> "gpt-4o-mini"
                    3 -> "o1"
                    4 -> "o1-mini"
                    5 -> "gpt-4o-realtime-preview"
                    6 -> "gpt-4o-audio-preview"
                    7 -> "gpt-4-turbo"
                    8 -> "dall-e-3"
                    9 -> "tts-1"
                    else -> "gpt-3.5-turbo"
                }
                showCustomToast("Switched to $currentModel")
            } else {
                showSubscriptionDialog()
            }
            dialog.dismiss()
        }

        dialog.setOnDismissListener {
            hideOverlay() // Hide the overlay when the dialog is dismissed
        }

        showOverlay() // Show the overlay before displaying the dialog
        dialog.show()
    }

    private fun showOverlay() {
        Log.d("ChatFragment", "Showing overlay")
        binding.subscriptionOverlay.visibility = View.VISIBLE
    }

    private fun hideOverlay() {
        Log.d("ChatFragment", "Hiding overlay")
        binding.subscriptionOverlay.visibility = View.GONE
    }
}