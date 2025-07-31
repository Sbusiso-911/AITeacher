package com.playstudio.aiteacher.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.playstudio.aiteacher.R
import com.playstudio.aiteacher.backend.UnifiedDataManager
import com.playstudio.aiteacher.databinding.ActivityUnifiedChatHistoryBinding
import com.playstudio.aiteacher.profile.ChatSessionEntity
import kotlinx.coroutines.launch

/**
 * Unified Chat History Activity
 * Shows synchronized chat history from both local and cloud storage
 * Supports search, favorites, export, and seamless webapp switching
 */
class UnifiedChatHistoryActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "UnifiedChatHistory"
    }
    
    private lateinit var binding: ActivityUnifiedChatHistoryBinding
    private lateinit var dataManager: UnifiedDataManager
    private lateinit var chatHistoryAdapter: UnifiedChatHistoryAdapter
    
    private var allChatSessions = listOf<ChatSessionEntity>()
    private var currentFilter = FilterType.ALL
    
    enum class FilterType {
        ALL, FAVORITES, RECENT, BY_MODEL
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUnifiedChatHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Initialize data manager
        dataManager = UnifiedDataManager.getInstance(this)
        
        setupUI()
        setupRecyclerView()
        startObservingData()
        
        // Handle back press
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
            }
        })
    }
    
    private fun setupUI() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
            title = "Chat History"
        }
        
        // Search functionality
        binding.searchView.setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                query?.let { searchChats(it) }
                return true
            }
            
            override fun onQueryTextChange(newText: String?): Boolean {
                if (newText.isNullOrEmpty()) {
                    showAllChats()
                } else {
                    searchChats(newText)
                }
                return true
            }
        })
        
        // Filter buttons
        binding.btnFilterAll.setOnClickListener {
            setFilter(FilterType.ALL)
        }
        
        binding.btnFilterFavorites.setOnClickListener {
            setFilter(FilterType.FAVORITES)
        }
        
        binding.btnFilterRecent.setOnClickListener {
            setFilter(FilterType.RECENT)
        }
        
        // Refresh functionality
        binding.swipeRefreshLayout.setOnRefreshListener {
            refreshChatHistory()
        }
        
        // Empty state button
        binding.btnStartNewChat.setOnClickListener {
            startNewChat()
        }
    }
    
    private fun setupRecyclerView() {
        chatHistoryAdapter = UnifiedChatHistoryAdapter(
            onChatClick = { chatSession ->
                openChat(chatSession)
            },
            onFavoriteClick = { chatSession ->
                toggleFavorite(chatSession)
            },
            onShareClick = { chatSession ->
                shareChat(chatSession)
            },
            onExportClick = { chatSession ->
                exportChat(chatSession)
            },
            onDeleteClick = { chatSession ->
                deleteChat(chatSession)
            }
        )
        
        binding.recyclerViewChatHistory.apply {
            layoutManager = LinearLayoutManager(this@UnifiedChatHistoryActivity)
            adapter = chatHistoryAdapter
        }
    }
    
    private fun startObservingData() {
        // Observe chat history changes
        lifecycleScope.launch {
            dataManager.chatHistory.collect { sessions ->
                allChatSessions = sessions
                updateChatList(sessions)
                updateEmptyState(sessions.isEmpty())
            }
        }
        
        // Observe sync status
        lifecycleScope.launch {
            dataManager.syncStatus.collect { status ->
                updateSyncStatus(status)
            }
        }
        
        // Observe online status
        lifecycleScope.launch {
            dataManager.isOnline.collect { isOnline ->
                updateOnlineStatus(isOnline)
            }
        }
        
        // Observe user profile for subscription status
        lifecycleScope.launch {
            dataManager.userProfile.collect { user ->
                user?.let { updateUserInfo(it) }
            }
        }
    }
    
    private fun setFilter(filterType: FilterType) {
        currentFilter = filterType
        
        // Update button states
        binding.btnFilterAll.isSelected = filterType == FilterType.ALL
        binding.btnFilterFavorites.isSelected = filterType == FilterType.FAVORITES
        binding.btnFilterRecent.isSelected = filterType == FilterType.RECENT
        
        // Apply filter
        when (filterType) {
            FilterType.ALL -> showAllChats()
            FilterType.FAVORITES -> showFavoriteChats()
            FilterType.RECENT -> showRecentChats()
            FilterType.BY_MODEL -> showChatsByModel()
        }
    }
    
    private fun showAllChats() {
        updateChatList(allChatSessions)
        binding.tvFilterStatus.text = "Showing all chats (${allChatSessions.size})"
    }
    
    private fun showFavoriteChats() {
        lifecycleScope.launch {
            try {
                val favoriteChats = dataManager.getFavoriteChatHistory()
                updateChatList(favoriteChats)
                binding.tvFilterStatus.text = "Showing favorite chats (${favoriteChats.size})"
            } catch (e: Exception) {
                Log.e(TAG, "Error loading favorite chats", e)
                showError("Failed to load favorite chats")
            }
        }
    }
    
    private fun showRecentChats() {
        val recentChats = allChatSessions.sortedByDescending { it.updatedAt }.take(20)
        updateChatList(recentChats)
        binding.tvFilterStatus.text = "Showing recent chats (${recentChats.size})"
    }
    
    private fun showChatsByModel() {
        // Group by AI model and show in organized way
        val groupedChats = allChatSessions.groupBy { it.aiModelUsed }
        // For now, just show all - could implement expandable groups later
        updateChatList(allChatSessions)
        binding.tvFilterStatus.text = "Grouped by model (${groupedChats.size} models)"
    }
    
    private fun searchChats(query: String) {
        lifecycleScope.launch {
            try {
                val searchResults = dataManager.searchChatHistory(query)
                updateChatList(searchResults)
                binding.tvFilterStatus.text = "Search results for \"$query\" (${searchResults.size})"
            } catch (e: Exception) {
                Log.e(TAG, "Error searching chats", e)
                showError("Search failed: ${e.message}")
            }
        }
    }
    
    private fun refreshChatHistory() {
        lifecycleScope.launch {
            try {
                val success = dataManager.performFullSync()
                if (success) {
                    showSuccess("Chat history refreshed")
                } else {
                    showError("Failed to refresh - check your connection")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error refreshing chat history", e)
                showError("Refresh failed: ${e.message}")
            } finally {
                binding.swipeRefreshLayout.isRefreshing = false
            }
        }
    }
    
    private fun openChat(chatSession: ChatSessionEntity) {
        // Open the specific chat session
        val intent = Intent(this, com.playstudio.aiteacher.ChatActivity::class.java).apply {
            putExtra("session_id", chatSession.sessionId)
            putExtra("session_title", chatSession.title)
        }
        startActivity(intent)
    }
    
    private fun toggleFavorite(chatSession: ChatSessionEntity) {
        lifecycleScope.launch {
            try {
                val success = dataManager.toggleChatFavorite(chatSession.sessionId)
                if (success) {
                    val message = if (chatSession.isFavorite) "Removed from favorites" else "Added to favorites"
                    showSuccess(message)
                } else {
                    showError("Failed to update favorite status")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error toggling favorite", e)
                showError("Failed to update favorite: ${e.message}")
            }
        }
    }
    
    private fun shareChat(chatSession: ChatSessionEntity) {
        lifecycleScope.launch {
            try {
                val exportData = dataManager.exportChat(chatSession.sessionId)
                if (exportData != null) {
                    val shareText = """
                        Chat: ${exportData.title}
                        AI Model: ${exportData.aiModel}
                        Messages: ${exportData.messageCount}
                        Date: ${exportData.createdAt}
                        
                        Shared from AI Teacher App
                    """.trimIndent()
                    
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "AI Teacher Chat: ${exportData.title}")
                        putExtra(Intent.EXTRA_TEXT, shareText)
                    }
                    startActivity(Intent.createChooser(shareIntent, "Share Chat"))
                } else {
                    showError("Failed to prepare chat for sharing")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error sharing chat", e)
                showError("Share failed: ${e.message}")
            }
        }
    }
    
    private fun exportChat(chatSession: ChatSessionEntity) {
        lifecycleScope.launch {
            try {
                val exportData = dataManager.exportChat(chatSession.sessionId)
                if (exportData != null) {
                    // Show export options dialog
                    showExportDialog(exportData)
                } else {
                    showError("Failed to export chat")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error exporting chat", e)
                showError("Export failed: ${e.message}")
            }
        }
    }
    
    private fun deleteChat(chatSession: ChatSessionEntity) {
        AlertDialog.Builder(this)
            .setTitle("Delete Chat")
            .setMessage("Are you sure you want to delete \"${chatSession.title}\"?\n\nThis action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch {
                    try {
                        val success = dataManager.deleteChat(chatSession.sessionId)
                        if (success) {
                            showSuccess("Chat deleted")
                        } else {
                            showError("Failed to delete chat")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error deleting chat", e)
                        showError("Delete failed: ${e.message}")
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun startNewChat() {
        val intent = Intent(this, com.playstudio.aiteacher.ChatActivity::class.java)
        startActivity(intent)
    }
    
    private fun updateChatList(chatSessions: List<ChatSessionEntity>) {
        chatHistoryAdapter.updateChatSessions(chatSessions)
    }
    
    private fun updateEmptyState(isEmpty: Boolean) {
        binding.emptyStateLayout.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.recyclerViewChatHistory.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }
    
    private fun updateSyncStatus(status: UnifiedDataManager.DataSyncStatus) {
        when (status) {
            UnifiedDataManager.DataSyncStatus.SYNCING -> {
                binding.tvSyncStatus.text = "Syncing..."
                binding.tvSyncStatus.visibility = View.VISIBLE
            }
            UnifiedDataManager.DataSyncStatus.ERROR -> {
                binding.tvSyncStatus.text = "Sync failed"
                binding.tvSyncStatus.visibility = View.VISIBLE
            }
            UnifiedDataManager.DataSyncStatus.OFFLINE -> {
                binding.tvSyncStatus.text = "Offline mode"
                binding.tvSyncStatus.visibility = View.VISIBLE
            }
            else -> {
                binding.tvSyncStatus.visibility = View.GONE
            }
        }
    }
    
    private fun updateOnlineStatus(isOnline: Boolean) {
        binding.ivOnlineStatus.setImageResource(
            if (isOnline) R.drawable.ic_online else R.drawable.ic_offline
        )
    }
    
    private fun updateUserInfo(user: com.playstudio.aiteacher.profile.UserEntity) {
        binding.tvUserName.text = user.fullName
        
        // Update subscription badge
        lifecycleScope.launch {
            val subscriptionStatus = dataManager.getSubscriptionStatus()
            val statusText = when (subscriptionStatus) {
                "premium" -> "Premium"
                "pro" -> "Pro"
                else -> "Free"
            }
            binding.tvSubscriptionStatus.text = statusText
        }
    }
    
    private fun showExportDialog(exportData: com.playstudio.aiteacher.profile.ProfileManager.ChatExportData) {
        val options = arrayOf("Export as Text", "Export as JSON", "Share Chat Link")
        
        AlertDialog.Builder(this)
            .setTitle("Export Chat")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> exportAsText(exportData)
                    1 -> exportAsJson(exportData)
                    2 -> shareWebappLink(exportData)
                }
            }
            .show()
    }
    
    private fun exportAsText(exportData: com.playstudio.aiteacher.profile.ProfileManager.ChatExportData) {
        // Implementation for text export
        showSuccess("Text export feature coming soon")
    }
    
    private fun exportAsJson(exportData: com.playstudio.aiteacher.profile.ProfileManager.ChatExportData) {
        // Implementation for JSON export
        showSuccess("JSON export feature coming soon")
    }
    
    private fun shareWebappLink(exportData: com.playstudio.aiteacher.profile.ProfileManager.ChatExportData) {
        lifecycleScope.launch {
            try {
                val webappResult = dataManager.generateWebappToken()
                if (webappResult.success && webappResult.webappUrl != null) {
                    val shareUrl = "${webappResult.webappUrl}?chat=${exportData.sessionId}"
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "AI Teacher Chat: ${exportData.title}")
                        putExtra(Intent.EXTRA_TEXT, "View this AI Teacher chat: $shareUrl")
                    }
                    startActivity(Intent.createChooser(shareIntent, "Share Chat Link"))
                } else {
                    showError("Failed to generate webapp link")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error sharing webapp link", e)
                showError("Failed to share link: ${e.message}")
            }
        }
    }
    
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.chat_history_menu, menu)
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.action_switch_webapp -> {
                switchToWebapp()
                true
            }
            R.id.action_export_all -> {
                exportAllChats()
                true
            }
            R.id.action_settings -> {
                openSettings()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    private fun switchToWebapp() {
        lifecycleScope.launch {
            try {
                showLoading(true)
                
                val result = dataManager.generateWebappToken()
                if (result.success && result.webappUrl != null) {
                    val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(result.webappUrl))
                    startActivity(intent)
                    showSuccess("Switched to webapp - your chats are synced!")
                } else {
                    showError("Failed to switch to webapp: ${result.message}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error switching to webapp", e)
                showError("Error switching to webapp: ${e.message}")
            } finally {
                showLoading(false)
            }
        }
    }
    
    private fun exportAllChats() {
        lifecycleScope.launch {
            try {
                val stats = dataManager.getUserStatistics()
                val totalChats = stats["total_chats"] as? Int ?: 0
                
                if (totalChats == 0) {
                    showError("No chats to export")
                    return@launch
                }
                
                showSuccess("Exporting $totalChats chats... (Feature coming soon)")
                // Implementation would export all chats
                
            } catch (e: Exception) {
                Log.e(TAG, "Error exporting all chats", e)
                showError("Export failed: ${e.message}")
            }
        }
    }
    
    private fun openSettings() {
        val intent = Intent(this, com.playstudio.aiteacher.SettingsActivity::class.java)
        startActivity(intent)
    }
    
    private fun showLoading(show: Boolean) {
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
    }
    
    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        Log.w(TAG, "Error: $message")
    }
    
    private fun showSuccess(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        Log.i(TAG, "Success: $message")
    }
}