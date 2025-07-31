package com.playstudio.aiteacher.backend

import android.content.Context
import android.util.Log
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.playstudio.aiteacher.profile.AuthenticationService
import com.playstudio.aiteacher.profile.ChatSessionEntity
import com.playstudio.aiteacher.profile.ProfileManager
import com.playstudio.aiteacher.profile.UserEntity
import kotlinx.coroutines.launch

/**
 * Example integration of UnifiedBackendClient with existing ChatFragment
 * Shows how to modify ChatFragment.kt to use the unified backend system
 * 
 * INTEGRATION INSTRUCTIONS:
 * 1. Add these imports to ChatFragment.kt:
 *    import com.playstudio.aiteacher.backend.UnifiedDataManager
 *    import androidx.lifecycle.lifecycleScope
 *    import kotlinx.coroutines.launch
 * 
 * 2. Add this property to ChatFragment class:
 *    private lateinit var dataManager: UnifiedDataManager
 * 
 * 3. Initialize in onViewCreated:
 *    dataManager = UnifiedDataManager.getInstance(requireContext())
 * 
 * 4. Replace existing authentication and profile calls with dataManager methods
 */
class BackendIntegrationExample(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner
) {
    
    private val dataManager = UnifiedDataManager.getInstance(context)
    
    /**
     * Example: How to handle user login in your existing activities
     * Replace your existing login logic with this
     */
    fun handleUserLogin(email: String, password: String, rememberMe: Boolean) {
        lifecycleOwner.lifecycleScope.launch {
            try {
                // Show loading indicator
                showLoadingIndicator(true)
                
                val result = dataManager.login(email, password, rememberMe)
                
                if (result.success) {
                    // Login successful - data is automatically loaded and synced
                    showMessage("Login successful!")
                    
                    // Navigate to main screen
                    navigateToMainScreen()
                    
                    // Start observing data changes
                    startObservingData()
                    
                } else {
                    showMessage("Login failed: ${result.message}")
                }
                
            } catch (e: Exception) {
                Log.e("LoginExample", "Login error", e)
                showMessage("Login error: ${e.message}")
            } finally {
                showLoadingIndicator(false)
            }
        }
    }
    
    /**
     * Example: How to handle user registration
     */
    fun handleUserRegistration(email: String, password: String, fullName: String) {
        lifecycleOwner.lifecycleScope.launch {
            try {
                showLoadingIndicator(true)
                
                val registrationData = AuthenticationService.RegistrationData(
                    email = email,
                    password = password,
                    fullName = fullName,
                    themePreference = "system",
                    languageSetting = "en"
                )
                
                val result = dataManager.register(registrationData)
                
                if (result.success) {
                    showMessage("Registration successful!")
                    navigateToMainScreen()
                    startObservingData()
                } else {
                    showMessage("Registration failed: ${result.message}")
                }
                
            } catch (e: Exception) {
                Log.e("RegistrationExample", "Registration error", e)
                showMessage("Registration error: ${e.message}")
            } finally {
                showLoadingIndicator(false)
            }
        }
    }
    
    /**
     * Example: How to observe and display user profile data
     * Add this to your ProfileActivity or SettingsActivity
     */
    private fun startObservingData() {
        // Observe user profile changes
        lifecycleOwner.lifecycleScope.launch {
            dataManager.userProfile.collect { user ->
                if (user != null) {
                    updateProfileUI(user)
                } else {
                    // User logged out
                    navigateToLoginScreen()
                }
            }
        }
        
        // Observe chat history changes
        lifecycleOwner.lifecycleScope.launch {
            dataManager.chatHistory.collect { sessions ->
                updateChatHistoryUI(sessions)
            }
        }
        
        // Observe subscription status changes
        lifecycleOwner.lifecycleScope.launch {
            dataManager.subscriptionStatus.collect { status ->
                updateSubscriptionUI(status)
            }
        }
        
        // Observe sync status for showing sync indicators
        lifecycleOwner.lifecycleScope.launch {
            dataManager.syncStatus.collect { status ->
                updateSyncStatusUI(status)
            }
        }
        
        // Observe online status
        lifecycleOwner.lifecycleScope.launch {
            dataManager.isOnline.collect { isOnline ->
                updateOnlineStatusUI(isOnline)
            }
        }
    }
    
    /**
     * Example: How to update user profile settings
     * Add this to your SettingsFragment or ProfileActivity
     */
    fun updateUserTheme(theme: String) {
        lifecycleOwner.lifecycleScope.launch {
            val success = dataManager.updateUserProfile(
                mapOf("themePreference" to theme)
            )
            
            if (success) {
                showMessage("Theme updated successfully")
                // UI will automatically update through the dataManager.userProfile flow
            } else {
                showMessage("Failed to update theme")
            }
        }
    }
    
    /**
     * Example: How to handle chat operations
     * Add this to your ChatFragment or ChatActivity
     */
    fun toggleChatFavorite(sessionId: Long) {
        lifecycleOwner.lifecycleScope.launch {
            val success = dataManager.toggleChatFavorite(sessionId)
            
            if (success) {
                // UI will automatically update through dataManager.chatHistory flow
                showMessage("Chat favorite status updated")
            } else {
                showMessage("Failed to update favorite status")
            }
        }
    }
    
    /**
     * Example: How to search chat history
     */
    fun searchChats(query: String) {
        lifecycleOwner.lifecycleScope.launch {
            val results = dataManager.searchChatHistory(query)
            updateSearchResultsUI(results)
        }
    }
    
    /**
     * Example: How to export chat data
     */
    fun exportChat(sessionId: Long) {
        lifecycleOwner.lifecycleScope.launch {
            val exportData = dataManager.exportChat(sessionId)
            
            if (exportData != null) {
                // Handle export (share, save to file, etc.)
                showExportDialog(exportData)
            } else {
                showMessage("Failed to export chat")
            }
        }
    }
    
    /**
     * Example: How to switch to webapp seamlessly
     * Add this to your menu or toolbar
     */
    fun switchToWebapp() {
        lifecycleOwner.lifecycleScope.launch {
            try {
                showLoadingIndicator(true)
                
                val result = dataManager.generateWebappToken()
                
                if (result.success && result.webappUrl != null) {
                    // Open webapp in browser with authenticated token
                    openUrlInBrowser(result.webappUrl)
                    showMessage("Switched to webapp successfully")
                } else {
                    showMessage("Failed to switch to webapp: ${result.message}")
                }
                
            } catch (e: Exception) {
                Log.e("WebappSwitch", "Error switching to webapp", e)
                showMessage("Error switching to webapp: ${e.message}")
            } finally {
                showLoadingIndicator(false)
            }
        }
    }
    
    /**
     * Example: How to handle subscription checks
     */
    fun checkSubscriptionFeature(feature: String): Boolean {
        return lifecycleOwner.lifecycleScope.launch {
            dataManager.hasFeature(feature)
        }.let { true } // This is simplified - you'd need to use async/await properly
    }
    
    /**
     * Example: How to force sync when user pulls to refresh
     */
    fun onRefreshPulled() {
        lifecycleOwner.lifecycleScope.launch {
            val success = dataManager.performFullSync()
            
            if (success) {
                showMessage("Data synced successfully")
            } else {
                showMessage("Sync failed - check your connection")
            }
            
            // Hide refresh indicator
            hideRefreshIndicator()
        }
    }
    
    /**
     * Example: How to check sync status and show appropriate UI
     */
    private fun updateSyncStatusUI(status: UnifiedDataManager.DataSyncStatus) {
        when (status) {
            UnifiedDataManager.DataSyncStatus.IDLE -> {
                // Hide sync indicator
                hideSyncIndicator()
            }
            UnifiedDataManager.DataSyncStatus.SYNCING -> {
                // Show sync indicator
                showSyncIndicator("Syncing data...")
            }
            UnifiedDataManager.DataSyncStatus.ERROR -> {
                // Show error indicator
                showSyncError("Sync failed")
            }
            UnifiedDataManager.DataSyncStatus.CONFLICT_DETECTED -> {
                // Show conflict resolution dialog
                showConflictDialog()
            }
            UnifiedDataManager.DataSyncStatus.OFFLINE -> {
                // Show offline indicator
                showOfflineIndicator()
            }
        }
    }
    
    /**
     * Example: How to show user statistics
     */
    fun showUserStatistics() {
        lifecycleOwner.lifecycleScope.launch {
            val stats = dataManager.getUserStatistics()
            
            // Display stats in your UI
            displayStatistics(stats)
        }
    }
    
    // Mock UI methods - replace these with your actual UI update methods
    private fun showLoadingIndicator(show: Boolean) { /* Your loading UI */ }
    private fun showMessage(message: String) { /* Your toast/snackbar */ }
    private fun navigateToMainScreen() { /* Your navigation */ }
    private fun navigateToLoginScreen() { /* Your navigation */ }
    private fun updateProfileUI(user: UserEntity) { /* Update profile UI */ }
    private fun updateChatHistoryUI(sessions: List<ChatSessionEntity>) { /* Update chat list */ }
    private fun updateSubscriptionUI(status: String) { /* Update subscription UI */ }
    private fun updateOnlineStatusUI(isOnline: Boolean) { /* Update online indicator */ }
    private fun updateSearchResultsUI(results: List<ChatSessionEntity>) { /* Update search results */ }
    private fun showExportDialog(exportData: ProfileManager.ChatExportData) { /* Show export options */ }
    private fun openUrlInBrowser(url: String) { /* Open URL in browser */ }
    private fun hideRefreshIndicator() { /* Hide pull-to-refresh */ }
    private fun showSyncIndicator(message: String) { /* Show sync status */ }
    private fun hideSyncIndicator() { /* Hide sync indicator */ }
    private fun showSyncError(message: String) { /* Show sync error */ }
    private fun showConflictDialog() { /* Show conflict resolution */ }
    private fun showOfflineIndicator() { /* Show offline status */ }
    private fun displayStatistics(stats: Map<String, Any>) { /* Show user stats */ }
}

/**
 * STEP-BY-STEP INTEGRATION GUIDE:
 * 
 * 1. ADD TO YOUR ChatFragment.kt:
 *    ```kotlin
 *    class ChatFragment : Fragment() {
 *        private lateinit var dataManager: UnifiedDataManager
 *        
 *        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
 *            super.onViewCreated(view, savedInstanceState)
 *            
 *            // Initialize data manager
 *            dataManager = UnifiedDataManager.getInstance(requireContext())
 *            
 *            // Initialize data and start observing
 *            lifecycleScope.launch {
 *                dataManager.initialize()
 *            }
 *            
 *            startObservingData()
 *        }
 *        
 *        private fun startObservingData() {
 *            // Observe chat history changes
 *            lifecycleScope.launch {
 *                dataManager.chatHistory.collect { sessions ->
 *                    // Update your chat RecyclerView adapter
 *                    chatAdapter.updateData(sessions)
 *                }
 *            }
 *            
 *            // Observe user profile for subscription status
 *            lifecycleScope.launch {
 *                dataManager.subscriptionStatus.collect { status ->
 *                    updateUIBasedOnSubscription(status)
 *                }
 *            }
 *        }
 *    }
 *    ```
 * 
 * 2. REPLACE AUTHENTICATION CALLS:
 *    Instead of: authService.login(email, password)
 *    Use: dataManager.login(email, password, rememberMe)
 * 
 * 3. REPLACE PROFILE OPERATIONS:
 *    Instead of: profileManager.updateThemePreference(theme)
 *    Use: dataManager.updateUserProfile(mapOf("themePreference" to theme))
 * 
 * 4. ADD WEBAPP SWITCHING:
 *    ```kotlin
 *    private fun onSwitchToWebapp() {
 *        lifecycleScope.launch {
 *            val result = dataManager.generateWebappToken()
 *            if (result.success && result.webappUrl != null) {
 *                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(result.webappUrl))
 *                startActivity(intent)
 *            }
 *        }
 *    }
 *    ```
 * 
 * 5. ADD SYNC STATUS UI:
 *    ```kotlin
 *    lifecycleScope.launch {
 *        dataManager.syncStatus.collect { status ->
 *            when (status) {
 *                UnifiedDataManager.DataSyncStatus.SYNCING -> showSyncIndicator()
 *                UnifiedDataManager.DataSyncStatus.OFFLINE -> showOfflineIndicator()
 *                else -> hideSyncIndicator()
 *            }
 *        }
 *    }
 *    ```
 */