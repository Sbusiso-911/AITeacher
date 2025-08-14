package com.playstudio.aiteacher

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.commit
import com.playstudio.aiteacher.databinding.ActivityChatBinding

class ChatActivity : AppCompatActivity(), ChatFragment.OnSubscriptionClickListener {

    private lateinit var binding: ActivityChatBinding
    private lateinit var themeManager: ThemeManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize theme manager and apply current theme
        themeManager = ThemeManager(this)
        applyCurrentTheme()
        
        // Apply glassmorphism styling to system bars
        setupGlassmorphismStatusBar()

        if (savedInstanceState == null) {
            val chatFragment = ChatFragment()

            // Start with any extras passed from the caller (e.g. conversation_id)
            val bundle = Bundle(intent.extras ?: Bundle())

            // Pass subscription status and suggested message to the ChatFragment
            val isAdFree = intent.getBooleanExtra("is_ad_free", false)
            val expirationTime = intent.getLongExtra("expiration_time", 0)
            val suggestedMessage = intent.getStringExtra("suggested_message")
            val selectedModel = intent.getStringExtra("selected_model")
            val autoShowImagePicker = intent.getBooleanExtra("auto_show_image_picker", false)
            val autoShowDocumentPicker = intent.getBooleanExtra("auto_show_document_picker", false)
            val autoSelectModel = intent.getBooleanExtra("auto_select_model", false)
            val autoStartLiveVoice = intent.getBooleanExtra("auto_start_live_voice", false)
            val voiceAgentType = intent.getStringExtra("voice_agent_type")
            bundle.apply {
                putBoolean("is_ad_free", isAdFree)
                putLong("expiration_time", expirationTime)
                putString("suggested_message", suggestedMessage)
                putString("selected_model", selectedModel)
                putBoolean("auto_show_image_picker", autoShowImagePicker)
                putBoolean("auto_show_document_picker", autoShowDocumentPicker)
                putBoolean("auto_select_model", autoSelectModel)
                putBoolean("auto_start_live_voice", autoStartLiveVoice)
                putString("voice_agent_type", voiceAgentType)
            }
            chatFragment.arguments = bundle

            supportFragmentManager.commit {
                replace(R.id.fragment_container, chatFragment)
            }
        }

        handleSharedEmail(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleSharedEmail(intent)
    }
    
    override fun onResume() {
        super.onResume()
        // Refresh theme in case it was changed in settings
        if (::themeManager.isInitialized) {
            applyCurrentTheme()
        }
    }

    private fun handleSharedEmail(intent: Intent) {
        if (Intent.ACTION_SEND == intent.action && intent.type != null) {
            val streamUri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            if (streamUri != null) {
                EmailProviderHelper(this).extractEmailContent(Intent().setData(streamUri)) { message ->
                    message?.let {
                        injectEmailIntoChat(it.subject, it.body, it.from)
                    }
                }
                return
            }

            val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT) ?: "(No Subject)"
            val body = intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""
            val sender = intent.getStringExtra(Intent.EXTRA_EMAIL)
            injectEmailIntoChat(subject, body, sender)
        }
    }

    private fun injectEmailIntoChat(subject: String, body: String, sender: String?) {
        val formattedMessage = buildString {
            append("I received the following email\n")
            sender?.let { append("From: $it\n") }
            append("Subject: $subject\n\n")
            append(body)
            append("\n\nPlease draft a concise reply and use the send_email_by_voice tool to compose it.")
        }

        val fragment = supportFragmentManager.findFragmentById(R.id.fragment_container) as? ChatFragment
        if (fragment != null) {
            fragment.setQuestionText(formattedMessage)
        } else {
            val newFragment = ChatFragment().apply {
                arguments = Bundle().apply {
                    putString("prefilled_question", formattedMessage)
                }
            }
            supportFragmentManager.commit {
                replace(R.id.fragment_container, newFragment)
            }
        }
    }


    override fun onSubscriptionClick() {
        // Delegate the subscription click event to MainActivity
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("action", "buy_subscription")
        }
        startActivity(intent)
    }

    override fun onBackPressed() {
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        super.onBackPressed()
    }

    private fun setupGlassmorphismStatusBar() {
        // Apply glassmorphism colors to system bars
        window?.apply {
            statusBarColor = getColor(R.color.glass_gradient_start)
            navigationBarColor = getColor(R.color.glass_gradient_end)
            
            // Ensure status bar content is visible on dark background
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                decorView.windowInsetsController?.setSystemBarsAppearance(
                    0, // Clear light status bar flag
                    android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                )
            } else {
                @Suppress("DEPRECATION")
                decorView.systemUiVisibility = decorView.systemUiVisibility and 
                    android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
            }
        }
    }

    private fun startNewConversation() {
        // Create a new ChatFragment to start fresh conversation
        val newFragment = ChatFragment()
        supportFragmentManager.commit {
            replace(R.id.fragment_container, newFragment)
        }
    }

    private fun shareConversation() {
        // Share app link since we don't have access to current conversation
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "Check out this AI Chat Teacher app! It's an amazing AI-powered learning assistant.")
            putExtra(Intent.EXTRA_SUBJECT, "AI Chat Teacher - AI Learning Assistant")
        }
        startActivity(Intent.createChooser(shareIntent, "Share via"))
    }
    
    private fun applyCurrentTheme() {
        val themeBackgroundView = findViewById<ImageView>(R.id.ai_theme_background)
        themeBackgroundView?.let { view ->
            themeManager.applyThemeToView(view)
        }
    }
}