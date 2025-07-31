package com.playstudio.aiteacher.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.core.content.ContextCompat
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.playstudio.aiteacher.R
import com.playstudio.aiteacher.backend.UnifiedDataManager
import com.playstudio.aiteacher.databinding.ActivityUnifiedSubscriptionBinding
import kotlinx.coroutines.launch

/**
 * Unified Subscription Activity
 * Shows synchronized subscription status and provides upgrade options
 * Works seamlessly between Android app and webapp
 */
class UnifiedSubscriptionActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "UnifiedSubscription"
    }
    
    private lateinit var binding: ActivityUnifiedSubscriptionBinding
    private lateinit var dataManager: UnifiedDataManager
    
    private var currentSubscriptionStatus = "free"
    private var userStatistics: Map<String, Any> = emptyMap()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUnifiedSubscriptionBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        dataManager = UnifiedDataManager.getInstance(this)
        
        setupUI()
        startObservingData()
        loadUserData()
    }
    
    private fun setupUI() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
            title = "Subscription & Usage"
        }
        
        // Plan selection buttons
        binding.btnSelectFree.setOnClickListener {
            selectPlan("free")
        }
        
        binding.btnSelectPro.setOnClickListener {
            selectPlan("pro")
        }
        
        binding.btnSelectPremium.setOnClickListener {
            selectPlan("premium")
        }
        
        // Action buttons
        binding.btnUpgrade.setOnClickListener {
            handleUpgrade()
        }
        
        binding.btnManageSubscription.setOnClickListener {
            manageSubscription()
        }
        
        binding.btnSwitchToWebapp.setOnClickListener {
            switchToWebapp()
        }
        
        binding.btnViewUsageDetails.setOnClickListener {
            viewUsageDetails()
        }
        
        // Refresh
        binding.swipeRefreshLayout.setOnRefreshListener {
            refreshSubscriptionData()
        }
    }
    
    private fun startObservingData() {
        // Observe subscription status changes
        lifecycleScope.launch {
            dataManager.subscriptionStatus.collect { status ->
                currentSubscriptionStatus = status
                updateSubscriptionUI(status)
            }
        }
        
        // Observe user profile changes
        lifecycleScope.launch {
            dataManager.userProfile.collect { user ->
                user?.let { updateUserInfo(it) }
            }
        }
        
        // Observe sync status
        lifecycleScope.launch {
            dataManager.syncStatus.collect { status ->
                updateSyncStatus(status)
            }
        }
    }
    
    private fun loadUserData() {
        lifecycleScope.launch {
            try {
                showLoading(true)
                
                // Load subscription status
                val subscriptionStatus = dataManager.getSubscriptionStatus(forceRefresh = true)
                currentSubscriptionStatus = subscriptionStatus
                
                // Load user statistics
                userStatistics = dataManager.getUserStatistics()
                
                updateSubscriptionUI(subscriptionStatus)
                updateUsageStatistics(userStatistics)
                
            } catch (e: Exception) {
                Log.e(TAG, "Error loading user data", e)
                showError("Failed to load subscription data")
            } finally {
                showLoading(false)
            }
        }
    }
    
    private fun updateSubscriptionUI(status: String) {
        // Reset all plan selections
        binding.cardFreePlan.isSelected = false
        binding.cardProPlan.isSelected = false
        binding.cardPremiumPlan.isSelected = false
        
        // Highlight current plan
        when (status) {
            "free" -> {
                binding.cardFreePlan.isSelected = true
                binding.tvCurrentPlan.text = "Current Plan: Free"
                binding.btnUpgrade.visibility = View.VISIBLE
                binding.btnManageSubscription.visibility = View.GONE
                updateFreePlanUI()
            }
            "pro" -> {
                binding.cardProPlan.isSelected = true
                binding.tvCurrentPlan.text = "Current Plan: Pro"
                binding.btnUpgrade.visibility = View.VISIBLE
                binding.btnManageSubscription.visibility = View.VISIBLE
                updateProPlanUI()
            }
            "premium" -> {
                binding.cardPremiumPlan.isSelected = true
                binding.tvCurrentPlan.text = "Current Plan: Premium"
                binding.btnUpgrade.visibility = View.GONE
                binding.btnManageSubscription.visibility = View.VISIBLE
                updatePremiumPlanUI()
            }
        }
        
        // Update feature availability indicators
        updateFeatureAvailability(status)
    }
    
    private fun updateFreePlanUI() {
        binding.tvPlanStatus.text = "You're on the free plan"
        binding.tvPlanDescription.text = "Upgrade to unlock premium AI models and features"
        binding.btnUpgrade.text = "Upgrade to Pro"
    }
    
    private fun updateProPlanUI() {
        binding.tvPlanStatus.text = "You have Pro access"
        binding.tvPlanDescription.text = "Enjoying advanced features! Upgrade to Premium for unlimited access"
        binding.btnUpgrade.text = "Upgrade to Premium"
    }
    
    private fun updatePremiumPlanUI() {
        binding.tvPlanStatus.text = "You have Premium access"
        binding.tvPlanDescription.text = "You have full access to all features and AI models"
    }
    
    private fun updateFeatureAvailability(status: String) {
        val hasProFeatures = status == "pro" || status == "premium"
        val hasPremiumFeatures = status == "premium"
        
        // Update feature indicators
        updateFeatureStatus(binding.featureGpt4Access, hasPremiumFeatures)
        updateFeatureStatus(binding.featureClaudeAccess, hasProFeatures)
        updateFeatureStatus(binding.featureVoiceMode, hasProFeatures)
        updateFeatureStatus(binding.featureImageGeneration, hasPremiumFeatures)
        updateFeatureStatus(binding.featureUnlimitedChats, hasPremiumFeatures)
        updateFeatureStatus(binding.featureCloudSync, hasProFeatures)
        updateFeatureStatus(binding.featureWebappAccess, hasProFeatures)
        updateFeatureStatus(binding.featurePrioritySupport, hasPremiumFeatures)
    }
    
    private fun updateFeatureStatus(featureView: View, hasAccess: Boolean) {
        val icon = featureView.findViewById<android.widget.ImageView>(R.id.iv_feature_icon)
        val text = featureView.findViewById<android.widget.TextView>(R.id.tv_feature_text)
        
        if (hasAccess) {
            icon.setImageResource(R.drawable.ic_check_circle)
            icon.setColorFilter(ContextCompat.getColor(this, R.color.success_green))
            text.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
        } else {
            icon.setImageResource(R.drawable.ic_lock)
            icon.setColorFilter(ContextCompat.getColor(this, R.color.text_secondary))
            text.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        }
    }
    
    private fun updateUsageStatistics(stats: Map<String, Any>) {
        // Update usage statistics display
        binding.tvTotalChats.text = "${stats["total_chats"] ?: 0}"
        binding.tvTotalMessages.text = "${stats["total_user_messages"] ?: 0}"
        binding.tvTokensUsed.text = "${stats["total_tokens"] ?: 0}"
        
        val storageUsed = stats["storage_used_mb"] as? Double ?: 0.0
        binding.tvStorageUsed.text = String.format("%.1f MB", storageUsed)
        
        val accountAgeDays = stats["account_age_days"] as? Long ?: 0
        binding.tvAccountAge.text = "$accountAgeDays days"
        
        // Models used
        val modelsUsed = stats["models_used"] as? List<String> ?: emptyList()
        binding.tvModelsUsed.text = "${modelsUsed.size} models"
        
        // Update usage progress bars
        updateUsageLimits(stats)
    }
    
    private fun updateUsageLimits(stats: Map<String, Any>) {
        val totalMessages = stats["total_user_messages"] as? Int ?: 0
        val totalTokens = stats["total_tokens"] as? Int ?: 0
        
        // Set limits based on subscription
        val (messageLimit, tokenLimit) = when (currentSubscriptionStatus) {
            "free" -> Pair(50, 10000)
            "pro" -> Pair(500, 100000)
            "premium" -> Pair(-1, -1) // Unlimited
        }
        
        if (messageLimit > 0) {
            val messageProgress = (totalMessages.toFloat() / messageLimit * 100).toInt()
            binding.progressMessages.progress = messageProgress.coerceAtMost(100)
            binding.tvMessageLimit.text = "$totalMessages / $messageLimit messages"
        } else {
            binding.progressMessages.progress = 0
            binding.tvMessageLimit.text = "$totalMessages messages (unlimited)"
        }
        
        if (tokenLimit > 0) {
            val tokenProgress = (totalTokens.toFloat() / tokenLimit * 100).toInt()
            binding.progressTokens.progress = tokenProgress.coerceAtMost(100)
            binding.tvTokenLimit.text = "$totalTokens / $tokenLimit tokens"
        } else {
            binding.progressTokens.progress = 0
            binding.tvTokenLimit.text = "$totalTokens tokens (unlimited)"
        }
    }
    
    private fun updateUserInfo(user: com.playstudio.aiteacher.profile.UserEntity) {
        binding.tvUserName.text = user.fullName
        binding.tvUserEmail.text = user.email
        
        // Load profile picture if available
        user.profilePictureUrl?.let { url ->
            // Load with Glide or similar
            // Glide.with(this).load(url).into(binding.ivUserProfile)
        }
    }
    
    private fun selectPlan(planType: String) {
        if (planType == currentSubscriptionStatus) {
            showMessage("You're already on the $planType plan")
            return
        }
        
        when (planType) {
            "pro" -> showUpgradeDialog("Pro", "Unlock advanced AI models and features")
            "premium" -> showUpgradeDialog("Premium", "Get unlimited access to all features")
            else -> {
                showMessage("Free plan selected")
            }
        }
    }
    
    private fun showUpgradeDialog(planName: String, description: String) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Upgrade to $planName")
            .setMessage("$description\n\nThis will redirect you to the webapp to complete the upgrade process.")
            .setPositiveButton("Continue") { _, _ ->
                handleUpgrade()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun handleUpgrade() {
        lifecycleScope.launch {
            try {
                showLoading(true)
                
                // Generate webapp token with subscription upgrade intent
                val result = dataManager.generateWebappToken()
                
                if (result.success && result.webappUrl != null) {
                    val upgradeUrl = "${result.webappUrl}?action=upgrade&plan=${getTargetPlan()}"
                    
                    val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(upgradeUrl))
                    startActivity(intent)
                    
                    showSuccess("Redirecting to webapp for upgrade...")
                } else {
                    showError("Failed to connect to webapp: ${result.message}")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error handling upgrade", e)
                showError("Upgrade failed: ${e.message}")
            } finally {
                showLoading(false)
            }
        }
    }
    
    private fun getTargetPlan(): String {
        return when (currentSubscriptionStatus) {
            "free" -> "pro"
            "pro" -> "premium"
            else -> "premium"
        }
    }
    
    private fun manageSubscription() {
        lifecycleScope.launch {
            try {
                val result = dataManager.generateWebappToken()
                
                if (result.success && result.webappUrl != null) {
                    val manageUrl = "${result.webappUrl}?action=manage_subscription"
                    
                    val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(manageUrl))
                    startActivity(intent)
                    
                    showSuccess("Opening subscription management...")
                } else {
                    showError("Failed to connect to webapp")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error opening subscription management", e)
                showError("Failed to open subscription management")
            }
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
                    
                    showSuccess("Switched to webapp - all your data is synced!")
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
    
    private fun viewUsageDetails() {
        val intent = Intent(this, com.playstudio.aiteacher.profile.UsageAnalyticsActivity::class.java)
        startActivity(intent)
    }
    
    private fun refreshSubscriptionData() {
        lifecycleScope.launch {
            try {
                val success = dataManager.performFullSync()
                if (success) {
                    loadUserData()
                    showSuccess("Subscription data refreshed")
                } else {
                    showError("Failed to refresh - check your connection")
                }
            } finally {
                binding.swipeRefreshLayout.isRefreshing = false
            }
        }
    }
    
    private fun updateSyncStatus(status: UnifiedDataManager.DataSyncStatus) {
        val syncText = when (status) {
            UnifiedDataManager.DataSyncStatus.SYNCING -> "Syncing subscription data..."
            UnifiedDataManager.DataSyncStatus.ERROR -> "Sync failed"
            UnifiedDataManager.DataSyncStatus.OFFLINE -> "Offline - showing cached data"
            else -> ""
        }
        
        binding.tvSyncStatus.text = syncText
        binding.tvSyncStatus.visibility = if (syncText.isEmpty()) View.GONE else View.VISIBLE
    }
    
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.subscription_menu, menu)
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.action_refresh -> {
                refreshSubscriptionData()
                true
            }
            R.id.action_billing_history -> {
                viewBillingHistory()
                true
            }
            R.id.action_contact_support -> {
                contactSupport()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    private fun viewBillingHistory() {
        lifecycleScope.launch {
            val result = dataManager.generateWebappToken()
            if (result.success && result.webappUrl != null) {
                val billingUrl = "${result.webappUrl}?action=billing_history"
                val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(billingUrl))
                startActivity(intent)
            }
        }
    }
    
    private fun contactSupport() {
        val intent = Intent(this, com.playstudio.aiteacher.ContactUsActivity::class.java)
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
    
    private fun showMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}