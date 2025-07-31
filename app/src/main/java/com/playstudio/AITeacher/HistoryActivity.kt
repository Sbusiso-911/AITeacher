package com.playstudio.aiteacher

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.playstudio.aiteacher.databinding.ActivityHistoryBinding
import com.playstudio.aiteacher.history.DatabaseProvider
import com.playstudio.aiteacher.history.HistoryRepository
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class HistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoryBinding
    private lateinit var adapter: HistoryAdapter
    private lateinit var themeManager: ThemeManager
    private val viewModel by viewModels<HistoryViewModel> {
        HistoryViewModel.Factory(HistoryRepository(DatabaseProvider.database))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize theme manager and apply current theme
        themeManager = ThemeManager(this)
        applyCurrentTheme()

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        adapter = HistoryAdapter { conversation ->
            val intent = Intent().apply {
                putExtra("conversation_id", conversation.id)
            }
            setResult(Activity.RESULT_OK, intent)
            finish()
        }
        binding.historyRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.historyRecyclerView.adapter = adapter

        lifecycleScope.launch {
            viewModel.conversations.collectLatest { conversations: List<com.playstudio.aiteacher.history.ConversationEntity> ->
                adapter.submitList(conversations)
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
    
    override fun onResume() {
        super.onResume()
        // Refresh theme in case it was changed
        if (::themeManager.isInitialized) {
            applyCurrentTheme()
        }
    }
    
    private fun applyCurrentTheme() {
        val themeBackgroundView = findViewById<ImageView>(R.id.ai_theme_background)
        themeBackgroundView?.let { view ->
            themeManager.applyThemeToView(view)
        }
    }
}