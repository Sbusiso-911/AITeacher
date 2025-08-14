package com.playstudio.aiteacher.profile

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.playstudio.aiteacher.R
import com.playstudio.aiteacher.databinding.ActivityChatHistoryBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import android.util.Log

class ChatHistoryActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityChatHistoryBinding
    private lateinit var profileManager: ProfileManager
    private lateinit var firebaseAuthService: FirebaseAuthenticationService
    private lateinit var chatHistoryAdapter: ChatHistoryAdapter
    
    private var allChatSessions = mutableListOf<ChatSessionEntity>()
    private var filteredChatSessions = mutableListOf<ChatSessionEntity>()
    
    private var selectedSessions = mutableSetOf<Long>()
    private var isSelectionMode = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        profileManager = ProfileManager(this)
        firebaseAuthService = FirebaseAuthenticationService(this)
        
        // Check authentication before proceeding
        if (!firebaseAuthService.isSignedIn()) {
            Log.w("ChatHistoryActivity", "User not authenticated, redirecting to login")
            redirectToLogin()
            return
        }
        
        setupGlassmorphismStatusBar()
        setupActionBar()
        setupRecyclerView()
        setupUI()
        loadChatHistory()
    }
    
    private fun redirectToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
    
    private fun setupGlassmorphismStatusBar() {
        window?.apply {
            statusBarColor = getColor(R.color.glass_gradient_start)
            navigationBarColor = getColor(R.color.glass_gradient_end)
        }
    }
    
    private fun setupActionBar() {
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setHomeAsUpIndicator(R.drawable.ic_arrow_back)
            title = "Chat History"
        }
    }
    
    private fun setupRecyclerView() {
        chatHistoryAdapter = ChatHistoryAdapter(
            onItemClick = { session ->
                if (isSelectionMode) {
                    toggleSelection(session.sessionId)
                } else {
                    openChatSession(session)
                }
            },
            onItemLongClick = { session ->
                enterSelectionMode(session.sessionId)
            },
            onFavoriteClick = { session ->
                toggleFavorite(session)
            }
        )
        
        binding.chatHistoryRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@ChatHistoryActivity)
            adapter = chatHistoryAdapter
        }

        // ensure empty state hidden until data loads
        binding.emptyStateLayout.visibility = android.view.View.GONE
    }
    
    private fun setupUI() {
        binding.apply {
            // Search functionality
            searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?): Boolean {
                    searchChats(query)
                    return true
                }
                
                override fun onQueryTextChange(newText: String?): Boolean {
                    searchChats(newText)
                    return true
                }
            })
            
            // Filter spinners
            setupFilterSpinners()
            
            // Floating action button
            fabNewChat.setOnClickListener {
                // Navigate to new chat
                finish()
            }
            
            // Bulk actions
            selectAllButton.setOnClickListener {
                selectAll()
            }
            
            deleteSelectedButton.setOnClickListener {
                deleteSelectedChats()
            }
            
            exportSelectedButton.setOnClickListener {
                exportSelectedChats()
            }
        }
    }
    
    private fun setupFilterSpinners() {
        // Category filter
        val categories = listOf("All Categories", "General", "Work", "Personal", "Creative", "Education")
        val categoryAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, categories)
        categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.categorySpinner.adapter = categoryAdapter
        
        binding.categorySpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                val selectedCategory = if (position == 0) null else categories[position].lowercase()
                filterByCategory(selectedCategory)
            }
            
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
        
        // Model filter
        val models = listOf("All Models", "GPT-4", "GPT-3.5", "Claude", "Gemini", "Llama")
        val modelAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, models)
        modelAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.modelSpinner.adapter = modelAdapter
        
        binding.modelSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                val selectedModel = if (position == 0) null else models[position]
                filterByModel(selectedModel)
            }
            
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
        
        // Sort options
        val sortOptions = listOf("Newest First", "Oldest First", "Most Messages", "Alphabetical")
        val sortAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, sortOptions)
        sortAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.sortSpinner.adapter = sortAdapter
        
        binding.sortSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                sortChats(position)
            }
            
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
    }
    
    private fun loadChatHistory() {
        lifecycleScope.launch {
            try {
                // Double-check authentication
                if (!firebaseAuthService.isSignedIn()) {
                    Log.w("ChatHistoryActivity", "User authentication lost, redirecting to login")
                    redirectToLogin()
                    return@launch
                }

                binding.progressBar.visibility = android.view.View.VISIBLE

                profileManager.getChatHistory().collect { sessions ->
                    // Hide the spinner after the first emission
                    if (binding.progressBar.visibility == android.view.View.VISIBLE) {
                        binding.progressBar.visibility = android.view.View.GONE
                    }

                    allChatSessions.clear()
                    allChatSessions.addAll(sessions)

                    applyFilters()
                    updateEmptyState()
                }
            } catch (ce: kotlinx.coroutines.CancellationException) {
                throw ce
            } catch (e: Exception) {
                Log.e("ChatHistoryActivity", "Error loading chat history", e)
                Toast.makeText(this@ChatHistoryActivity, "Error loading chat history: ${e.message}", Toast.LENGTH_SHORT).show()
                binding.progressBar.visibility = android.view.View.GONE
            }
        }
    }
    
    private fun searchChats(query: String?) {
        if (query.isNullOrBlank()) {
            applyFilters()
            return
        }
        
        lifecycleScope.launch {
            try {
                profileManager.searchChatHistory(query).collect { sessions ->
                    filteredChatSessions.clear()
                    filteredChatSessions.addAll(sessions)
                    chatHistoryAdapter.updateSessions(filteredChatSessions)
                    updateEmptyState()
                }
            } catch (ce: kotlinx.coroutines.CancellationException) {
                throw ce
            } catch (e: Exception) {
                Toast.makeText(this@ChatHistoryActivity, "Error searching chats: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun filterByCategory(category: String?) {
        if (category == null) {
            applyFilters()
            return
        }
        
        lifecycleScope.launch {
            try {
                profileManager.getChatHistoryByCategory(category).collect { sessions ->
                    filteredChatSessions.clear()
                    filteredChatSessions.addAll(sessions)
                    chatHistoryAdapter.updateSessions(filteredChatSessions)
                    updateEmptyState()
                }
            } catch (ce: kotlinx.coroutines.CancellationException) {
                throw ce
            } catch (e: Exception) {
                Toast.makeText(this@ChatHistoryActivity, "Error filtering by category: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun filterByModel(model: String?) {
        if (model == null) {
            applyFilters()
            return
        }
        
        lifecycleScope.launch {
            try {
                profileManager.getChatHistoryByModel(model).collect { sessions ->
                    filteredChatSessions.clear()
                    filteredChatSessions.addAll(sessions)
                    chatHistoryAdapter.updateSessions(filteredChatSessions)
                    updateEmptyState()
                }
            } catch (ce: kotlinx.coroutines.CancellationException) {
                throw ce
            } catch (e: Exception) {
                Toast.makeText(this@ChatHistoryActivity, "Error filtering by model: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun sortChats(sortOption: Int) {
        when (sortOption) {
            0 -> filteredChatSessions.sortByDescending { it.updatedAt }
            1 -> filteredChatSessions.sortBy { it.updatedAt }
            2 -> filteredChatSessions.sortByDescending { it.messageCount }
            3 -> filteredChatSessions.sortBy { it.title }
        }
        
        chatHistoryAdapter.updateSessions(filteredChatSessions)
    }
    
    private fun applyFilters() {
        filteredChatSessions.clear()
        filteredChatSessions.addAll(allChatSessions)
        chatHistoryAdapter.updateSessions(filteredChatSessions)
        updateEmptyState()
    }
    
    private fun updateEmptyState() {
        binding.emptyStateLayout.visibility = if (filteredChatSessions.isEmpty()) {
            android.view.View.VISIBLE
        } else {
            android.view.View.GONE
        }
    }
    
    private fun openChatSession(session: ChatSessionEntity) {
        // Navigate to chat activity with session ID
        val intent = Intent(this, com.playstudio.aiteacher.ChatActivity::class.java)
        intent.putExtra("session_id", session.sessionId)
        startActivity(intent)
    }
    
    private fun toggleFavorite(session: ChatSessionEntity) {
        lifecycleScope.launch {
            try {
                val success = profileManager.toggleChatFavorite(session.sessionId)
                if (success) {
                    // Update local data
                    val index = filteredChatSessions.indexOfFirst { it.sessionId == session.sessionId }
                    if (index != -1) {
                        filteredChatSessions[index] = session.copy(isFavorite = !session.isFavorite)
                        chatHistoryAdapter.notifyItemChanged(index)
                    }
                    
                    val message = if (session.isFavorite) "Removed from favorites" else "Added to favorites"
                    Toast.makeText(this@ChatHistoryActivity, message, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@ChatHistoryActivity, "Error updating favorite: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun enterSelectionMode(sessionId: Long) {
        isSelectionMode = true
        selectedSessions.clear()
        selectedSessions.add(sessionId)
        
        binding.bulkActionsLayout.visibility = android.view.View.VISIBLE
        binding.fabNewChat.visibility = android.view.View.GONE
        
        chatHistoryAdapter.setSelectionMode(true)
        chatHistoryAdapter.setSelectedSessions(selectedSessions)
        
        supportActionBar?.title = "Select Chats"
        invalidateOptionsMenu()
    }
    
    private fun exitSelectionMode() {
        isSelectionMode = false
        selectedSessions.clear()
        
        binding.bulkActionsLayout.visibility = android.view.View.GONE
        binding.fabNewChat.visibility = android.view.View.VISIBLE
        
        chatHistoryAdapter.setSelectionMode(false)
        chatHistoryAdapter.setSelectedSessions(emptySet())
        
        supportActionBar?.title = "Chat History"
        invalidateOptionsMenu()
    }
    
    private fun toggleSelection(sessionId: Long) {
        if (selectedSessions.contains(sessionId)) {
            selectedSessions.remove(sessionId)
        } else {
            selectedSessions.add(sessionId)
        }
        
        chatHistoryAdapter.setSelectedSessions(selectedSessions)
        
        if (selectedSessions.isEmpty()) {
            exitSelectionMode()
        }
    }
    
    private fun selectAll() {
        selectedSessions.clear()
        selectedSessions.addAll(filteredChatSessions.map { it.sessionId })
        chatHistoryAdapter.setSelectedSessions(selectedSessions)
    }
    
    private fun deleteSelectedChats() {
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this, R.style.BlueDialogTheme)
            .setTitle("Delete Chats")
            .setMessage("Are you sure you want to delete ${selectedSessions.size} selected chat(s)?")
            .setPositiveButton("Delete") { _, _ ->
                performBulkDelete()
            }
            .setNegativeButton("Cancel", null)
            .create()
        
        dialog.show()
    }
    
    private fun performBulkDelete() {
        lifecycleScope.launch {
            try {
                val success = profileManager.bulkDeleteChats(selectedSessions.toList())
                if (success) {
                    // Remove deleted sessions from local data
                    filteredChatSessions.removeAll { selectedSessions.contains(it.sessionId) }
                    allChatSessions.removeAll { selectedSessions.contains(it.sessionId) }
                    
                    chatHistoryAdapter.updateSessions(filteredChatSessions)
                    updateEmptyState()
                    
                    Toast.makeText(this@ChatHistoryActivity, "${selectedSessions.size} chats deleted", Toast.LENGTH_SHORT).show()
                    exitSelectionMode()
                } else {
                    Toast.makeText(this@ChatHistoryActivity, "Error deleting chats", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@ChatHistoryActivity, "Error deleting chats: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun exportSelectedChats() {
        lifecycleScope.launch {
            try {
                val exportData = mutableListOf<ProfileManager.ChatExportData>()
                
                for (sessionId in selectedSessions) {
                    val data = profileManager.exportChat(sessionId)
                    if (data != null) {
                        exportData.add(data)
                    }
                }
                
                if (exportData.isNotEmpty()) {
                    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                    val filename = "chat_export_$timestamp.txt"
                    
                    val content = buildString {
                        appendLine("Chat History Export")
                        appendLine("Generated: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}")
                        appendLine("Total Chats: ${exportData.size}")
                        appendLine("=".repeat(80))
                        appendLine()
                        
                        exportData.forEach { chat ->
                            appendLine("Chat: ${chat.title}")
                            appendLine("Model: ${chat.aiModel}")
                            appendLine("Category: ${chat.category}")
                            appendLine("Created: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(chat.createdAt)}")
                            appendLine("Messages: ${chat.messageCount}")
                            appendLine("-".repeat(50))
                            
                            chat.messages.forEach { message ->
                                val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(message.timestamp)
                                val sender = if (message.senderType == "user") "You" else "AI"
                                appendLine("[$timestamp] $sender:")
                                appendLine(message.content)
                                appendLine()
                            }
                            
                            appendLine("=".repeat(80))
                            appendLine()
                        }
                    }
                    
                    val filePath = profileManager.saveExportToFile(content, filename)
                    if (filePath != null) {
                        Toast.makeText(this@ChatHistoryActivity, "Exported to: $filePath", Toast.LENGTH_LONG).show()
                        exitSelectionMode()
                    } else {
                        Toast.makeText(this@ChatHistoryActivity, "Error saving export file", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this@ChatHistoryActivity, "No data to export", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@ChatHistoryActivity, "Error exporting chats: ${e.message}", Toast.LENGTH_SHORT).show()
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
                if (isSelectionMode) {
                    exitSelectionMode()
                } else {
                    onBackPressed()
                }
                true
            }
            R.id.action_favorites -> {
                showFavoritesOnly()
                true
            }
            R.id.action_export_all -> {
                exportAllChats()
                true
            }
            R.id.action_clear_history -> {
                showClearHistoryDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    private fun showFavoritesOnly() {
        lifecycleScope.launch {
            try {
                profileManager.getFavoriteChatHistory().collect { sessions ->
                    filteredChatSessions.clear()
                    filteredChatSessions.addAll(sessions)
                    chatHistoryAdapter.updateSessions(filteredChatSessions)
                    updateEmptyState()
                }
            } catch (e: Exception) {
                Toast.makeText(this@ChatHistoryActivity, "Error loading favorites: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun exportAllChats() {
        lifecycleScope.launch {
            try {
                val jsonContent = profileManager.exportAllChatsAsJson()
                if (jsonContent != null) {
                    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                    val filename = "all_chats_export_$timestamp.json"
                    
                    val filePath = profileManager.saveExportToFile(jsonContent, filename)
                    if (filePath != null) {
                        Toast.makeText(this@ChatHistoryActivity, "All chats exported to: $filePath", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this@ChatHistoryActivity, "Error saving export file", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this@ChatHistoryActivity, "No data to export", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@ChatHistoryActivity, "Error exporting all chats: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun showClearHistoryDialog() {
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this, R.style.BlueDialogTheme)
            .setTitle("Clear Chat History")
            .setMessage("Are you sure you want to delete all chat history? This action cannot be undone.")
            .setPositiveButton("Clear All") { _, _ ->
                clearAllHistory()
            }
            .setNegativeButton("Cancel", null)
            .create()
        
        dialog.show()
    }
    
    private fun clearAllHistory() {
        lifecycleScope.launch {
            try {
                val sessionIds = allChatSessions.map { it.sessionId }
                val success = profileManager.bulkDeleteChats(sessionIds)
                
                if (success) {
                    allChatSessions.clear()
                    filteredChatSessions.clear()
                    chatHistoryAdapter.updateSessions(filteredChatSessions)
                    updateEmptyState()
                    
                    Toast.makeText(this@ChatHistoryActivity, "All chat history cleared", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@ChatHistoryActivity, "Error clearing history", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@ChatHistoryActivity, "Error clearing history: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    override fun onBackPressed() {
        if (isSelectionMode) {
            exitSelectionMode()
        } else {
            super.onBackPressed()
        }
    }
}