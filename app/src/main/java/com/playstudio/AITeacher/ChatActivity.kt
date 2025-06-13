package com.playstudio.aiteacher

import android.content.Intent
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