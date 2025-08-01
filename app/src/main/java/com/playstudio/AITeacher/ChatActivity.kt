package com.playstudio.aiteacher

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.commit
import com.playstudio.aiteacher.databinding.ActivityChatBinding

class ChatActivity : AppCompatActivity(), ChatFragment.OnSubscriptionClickListener {

    private lateinit var binding: ActivityChatBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setHomeAsUpIndicator(R.drawable.ic_arrow_back)
        }

        if (savedInstanceState == null) {
            val chatFragment = ChatFragment()
            chatFragment.arguments = intent.extras

            // Pass subscription status and suggested message to the ChatFragment
            val isAdFree = intent.getBooleanExtra("is_ad_free", false)
            val expirationTime = intent.getLongExtra("expiration_time", 0)
            val suggestedMessage = intent.getStringExtra("suggested_message")
            val bundle = Bundle().apply {
                putBoolean("is_ad_free", isAdFree)
                putLong("expiration_time", expirationTime)
                putString("suggested_message", suggestedMessage)
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

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                true
            }
            else -> super.onOptionsItemSelected(item)
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
}