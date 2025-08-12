package com.playstudio.aiteacher.profile

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.playstudio.aiteacher.R
import com.playstudio.aiteacher.SettingsActivity
import com.playstudio.aiteacher.databinding.ActivityProfileBinding
import com.playstudio.aiteacher.SubscriptionUIManager
import com.playstudio.aiteacher.billing.GooglePlayBillingSync
import com.playstudio.aiteacher.history.DatabaseProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.Date
import android.util.Log
import kotlinx.coroutines.tasks.await

class ProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileBinding
    private lateinit var firebaseAuthService: FirebaseAuthenticationService
    private lateinit var firestoreSubscriptionManager: FirestoreSubscriptionManager
    private lateinit var subscriptionUIManager: SubscriptionUIManager
    private lateinit var billingSync: GooglePlayBillingSync
    private lateinit var profileManager: ProfileManager
    private lateinit var dailyTokenTracker: DailyTokenTracker
    private lateinit var recentChatAdapter: RecentChatAdapter

    private lateinit var imagePickerLauncher: ActivityResultLauncher<Intent>
    private lateinit var cameraLauncher: ActivityResultLauncher<Intent>

    private var currentUser: FirebaseAuthenticationService.FirestoreUser? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize services (Firestore only)
        firebaseAuthService = FirebaseAuthenticationService(this)
        firestoreSubscriptionManager = FirestoreSubscriptionManager(this)
        subscriptionUIManager = SubscriptionUIManager(this)
        billingSync = GooglePlayBillingSync(this)
        profileManager = ProfileManager(this)
        dailyTokenTracker = DailyTokenTracker(this)
        
        // Initialize recent chat adapter
        recentChatAdapter = RecentChatAdapter { chatSession ->
            // Navigate to ChatHistoryActivity when chat is clicked
            val intent = Intent(this, ChatHistoryActivity::class.java)
            intent.putExtra("highlight_session_id", chatSession.sessionId)
            startActivity(intent)
        }

        // Setup activity result launchers
        setupActivityResultLaunchers()

        // Apply glassmorphism styling
        setupGlassmorphismStatusBar()

        // Setup action bar
        setupActionBar()

        // Setup UI
        setupUI()

        // Handle intent extras for authentication flow
        handleAuthenticationIntentExtras()

        // Load profile data
        loadProfileData()
    }

    private fun setupActivityResultLaunchers() {
        imagePickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let { uri ->
                    handleImageSelection(uri)
                }
            }
        }

        cameraLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.extras?.get("data")?.let { bitmap ->
                    handleCameraCapture(bitmap as Bitmap)
                }
            }
        }
    }

    private fun setupGlassmorphismStatusBar() {
        window?.apply {
            statusBarColor = getColor(R.color.glass_gradient_start)
            navigationBarColor = getColor(R.color.glass_gradient_end)

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                decorView.windowInsetsController?.setSystemBarsAppearance(
                    0,
                    android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                )
            }
        }
    }

    private fun handleAuthenticationIntentExtras() {
        val showRegistration = intent.getBooleanExtra("show_registration", false)
        val showLogin = intent.getBooleanExtra("show_login", false)
        
        Log.d("ProfileActivity", "Intent extras - showRegistration: $showRegistration, showLogin: $showLogin")
        Log.d("ProfileActivity", "Current auth status: ${firebaseAuthService.isSignedIn()}")
        
        // Only redirect for authentication if user is not signed in AND intent extras are present
        if (!firebaseAuthService.isSignedIn()) {
            if (showRegistration) {
                Log.d("ProfileActivity", "User not authenticated - redirecting to RegisterActivity")
                val intent = Intent(this, RegisterActivity::class.java)
                intent.putExtra("return_to_subscription", true)
                startActivity(intent)
                finish()
                return
            } else if (showLogin) {
                Log.d("ProfileActivity", "User not authenticated - redirecting to LoginActivity") 
                val intent = Intent(this, LoginActivity::class.java)
                intent.putExtra("return_to_subscription", true)
                startActivity(intent)
                finish()
                return
            }
        } else {
            Log.d("ProfileActivity", "User is already authenticated - showing profile normally")
        }
    }

    private fun setupActionBar() {
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setHomeAsUpIndicator(R.drawable.ic_arrow_back)
            title = "Profile"
        }
    }

    private fun setupUI() {
        binding.apply {
            // Profile picture click listener
            profileImageView.setOnClickListener {
                showImageSelectionDialog()
            }
            
            // Debug: Long press on profile image to populate test data
            profileImageView.setOnLongClickListener {
                lifecycleScope.launch {
                    populateTestDataForDebugging()
                    Toast.makeText(this@ProfileActivity, "Debug: Test data populated", Toast.LENGTH_SHORT).show()
                }
                true
            }

            // Edit profile button
            editProfileButton.setOnClickListener {
                showEditProfileDialog()
            }

            // Subscription card click listener
            subscriptionCard.setOnClickListener {
                startActivity(Intent(this@ProfileActivity, SubscriptionActivity::class.java))
            }

            // Settings button
            settingsButton.setOnClickListener {
                startActivity(Intent(this@ProfileActivity, SettingsActivity::class.java))
            }

            // Clear History button
            clearHistoryButton.setOnClickListener {
                showClearHistoryConfirmationDialog()
            }

            // Logout button
            logoutButton.setOnClickListener {
                logout()
            }

            // Setup recent chat history RecyclerView
            recentChatHistoryRecyclerView.apply {
                adapter = recentChatAdapter
                layoutManager = androidx.recyclerview.widget.LinearLayoutManager(
                    this@ProfileActivity, 
                    androidx.recyclerview.widget.LinearLayoutManager.VERTICAL, 
                    false
                )
                isNestedScrollingEnabled = false
            }

            // Statistics cards
            chatHistoryCard.setOnClickListener {
                startActivity(Intent(this@ProfileActivity, ChatHistoryActivity::class.java))
            }

            usageAnalyticsCard.setOnClickListener {
                startActivity(Intent(this@ProfileActivity, UsageAnalyticsActivity::class.java))
            }
        }
    }

    private fun loadProfileData() {
        lifecycleScope.launch {
            try {
                binding.progressBar.visibility = android.view.View.VISIBLE

                // Check if user is authenticated - only redirect if explicitly requested via intent extras
                if (!firebaseAuthService.isSignedIn()) {
                    val showRegistration = intent.getBooleanExtra("show_registration", false)
                    val showLogin = intent.getBooleanExtra("show_login", false)
                    
                    if (showRegistration || showLogin) {
                        Log.w("ProfileActivity", "User not authenticated and authentication requested, redirecting to login")
                        redirectToLogin()
                        return@launch
                    } else {
                        Log.w("ProfileActivity", "User not authenticated but no explicit auth request - showing login message")
                        binding.progressBar.visibility = android.view.View.GONE
                        showLoginMessage()
                        return@launch
                    }
                }

                // Check authentication first
                val isAuthenticated = firebaseAuthService.isSignedIn()
                val currentFirebaseUid = firebaseAuthService.getCurrentFirebaseUid()
                val directFirebaseUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                
                Log.e("ProfileActivity", "=== AUTHENTICATION DEBUG ===")
                Log.e("ProfileActivity", "FirebaseAuthService: isSignedIn=$isAuthenticated, uid=$currentFirebaseUid")
                Log.e("ProfileActivity", "Direct Firebase Auth: user=${directFirebaseUser?.uid}, email=${directFirebaseUser?.email}")
                Log.e("ProfileActivity", "Direct Firebase Auth: isEmailVerified=${directFirebaseUser?.isEmailVerified}")
                
                // Test Firestore permissions directly
                val testFirestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                try {
                    val testDoc = testFirestore.collection("users").document(directFirebaseUser?.uid ?: "unknown").get().await()
                    Log.e("ProfileActivity", "Firestore test: Direct document access SUCCESSFUL, exists=${testDoc.exists()}")
                } catch (e: Exception) {
                    Log.e("ProfileActivity", "Firestore test: Direct document access FAILED", e)
                }
                
                // Also check what's in SharedPreferences for debugging
                val sharedPrefs = getSharedPreferences("prefs", Context.MODE_PRIVATE)
                val chatHistoryJson = sharedPrefs.getString("chat_history", "[]") ?: "[]"
                Log.e("ProfileActivity", "SharedPrefs chat_history length: ${chatHistoryJson.length}")
                if (chatHistoryJson.length > 10) {
                    Log.e("ProfileActivity", "SharedPrefs sample: ${chatHistoryJson.take(300)}")
                }
                
                if (!isAuthenticated || currentFirebaseUid == null) {
                    Log.e("ProfileActivity", "User is not properly authenticated")
                    showLoginMessage()
                    return@launch
                }

                // Try to load user data from Firestore, but continue if it fails
                Log.e("ProfileActivity", "Attempting to load user data from Firestore for uid: $currentFirebaseUid")
                try {
                    currentUser = firebaseAuthService.getCurrentUser()
                    
                    if (currentUser == null) {
                        Log.w("ProfileActivity", "No user data found in Firestore, trying to create user profile...")
                        try {
                            createUserProfileInFirestore()
                            kotlinx.coroutines.delay(1000)
                            currentUser = firebaseAuthService.getCurrentUser()
                        } catch (e: Exception) {
                            Log.w("ProfileActivity", "Failed to create user profile in Firestore", e)
                        }
                    }
                } catch (e: Exception) {
                    Log.w("ProfileActivity", "Error loading user data from Firestore", e)
                    currentUser = null
                }
                
                // If Firestore fails, create a basic user object from Firebase Auth
                if (currentUser == null && directFirebaseUser != null) {
                    Log.e("ProfileActivity", "Firestore failed, creating basic user from Firebase Auth")
                    currentUser = createBasicUserFromFirebaseAuth(directFirebaseUser)
                }
                
                if (currentUser != null) {
                    Log.e("ProfileActivity", "User data loaded successfully: email=${currentUser!!.email}, name=${currentUser!!.fullName}")
                } else {
                    Log.e("ProfileActivity", "Failed to load user data")
                    showErrorMessage("Failed to load profile data")
                    return@launch
                }

                // Initialize ProfileManager and sync chat history to Firestore
                Log.e("ProfileActivity", "=== INITIALIZING PROFILE AND SYNCING CHAT HISTORY ===")
                try {
                    // Debug: First let's see what's actually in SharedPreferences
                    profileManager.debugSharedPreferences()
                    
                    // For debugging: Let's do a fresh sync to clear old data
                    val freshSyncSuccess = profileManager.clearFirestoreAndFreshSync()
                    Log.e("ProfileActivity", "Fresh sync result: $freshSyncSuccess")
                    
                    val profileInitSuccess = profileManager.initializeUserProfile()
                    Log.e("ProfileActivity", "Profile initialization result: $profileInitSuccess")
                    
                    // Also migrate stats for backwards compatibility
                    migrateChatHistoryStatsToFirestore()
                } catch (e: Exception) {
                    Log.w("ProfileActivity", "Profile initialization/migration failed, continuing", e)
                }
                
                // Force a full billing sync to ensure we have latest subscription data
                Log.d("ProfileActivity", "Force syncing Google Play Billing to Firestore...")
                try {
                    // Always do a full sync rather than just checking if needed
                    val syncResult = billingSync.syncSubscriptionToFirestore()
                    Log.d("ProfileActivity", "Billing sync result: $syncResult")
                    
                    // Wait a moment for Firestore write to propagate
                    kotlinx.coroutines.delay(1000)
                } catch (e: Exception) {
                    Log.w("ProfileActivity", "Billing sync failed, continuing with Firestore data", e)
                }
                
                // Load subscription status from Firestore (this is now the single source of truth)
                Log.d("ProfileActivity", "Loading subscription status from Firestore...")
                val firestoreSubscriptionStatus = try {
                    billingSync.getSubscriptionStatusForDisplay()
                } catch (e: Exception) {
                    Log.e("ProfileActivity", "Error loading subscription status", e)
                    // Create default subscription status
                    com.playstudio.aiteacher.profile.FirestoreSubscriptionManager.SubscriptionStatus(
                        isActive = false,
                        planType = "free",
                        daysRemaining = 0,
                        isExpired = false,
                        features = emptyList()
                    )
                }
                
                Log.d("ProfileActivity", "Subscription status loaded: isActive=${firestoreSubscriptionStatus.isActive}, planType=${firestoreSubscriptionStatus.planType}")

                // Update UI
                updateUI()
                updateSubscriptionUIWithFirestore(firestoreSubscriptionStatus)

            } catch (e: Exception) {
                Log.e("ProfileActivity", "Error loading profile", e)
                Toast.makeText(this@ProfileActivity, "Error loading profile: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                binding.progressBar.visibility = android.view.View.GONE
            }
        }
    }
    
    private fun showLoginMessage() {
        // Show a message in the profile screen instead of redirecting
        binding.apply {
            profileImageView.setImageResource(R.drawable.ic_premium_users)
            profileNameText.text = "Not Logged In"
            profileEmailText.text = "Please log in to view your profile"
            subscriptionStatusText.text = "Authentication Required"
            
            // Show login button
            editProfileButton.text = "Log In"
            editProfileButton.setOnClickListener {
                val intent = Intent(this@ProfileActivity, LoginActivity::class.java)
                intent.putExtra("return_to_profile", true)
                startActivity(intent)
                finish() // Close ProfileActivity so user returns to MainActivity after login
            }
        }
    }
    
    private fun redirectToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun updateUI() {
        val user = currentUser
        if (user == null) {
            Log.w("ProfileActivity", "Cannot update UI - user data is null")
            showNoDataMessage()
            return
        }

        binding.apply {
            // Profile header
            profileNameText.text = user.fullName.ifEmpty { "Unknown User" }
            profileEmailText.text = user.email.ifEmpty { "No email" }

            // Load profile picture
            user.profilePictureUrl?.let { url ->
                // In a real app, use an image loading library like Glide or Picasso
                // For now, we'll just show a placeholder
                profileImageView.setImageResource(R.drawable.ic_premium_users)
            } ?: run {
                profileImageView.setImageResource(R.drawable.ic_premium_users)
            }

            // Member since
            val dateFormat = SimpleDateFormat("MMM yyyy", Locale.getDefault())
            val memberSince = if (user.createdAt > 0) {
                "Member since ${dateFormat.format(Date(user.createdAt))}"
            } else {
                "Member since recently"
            }
            memberSinceText.text = memberSince

            // Statistics from Firestore user - show actual data
            totalChatsText.text = user.totalChats.toString()
            totalMessagesText.text = user.totalMessages.toString()
            totalTokensText.text = if (user.tokenCount > 1000) "${user.tokenCount / 1000}K" else user.tokenCount.toString()
            favoriteChatsText.text = "0" // Will be updated with Firestore data
            
            // Update statistics with real Firestore chat data and load recent chats
            lifecycleScope.launch {
                try {
                    val firestoreStats = profileManager.getChatStatisticsFromFirestore()
                    if (firestoreStats.isNotEmpty()) {
                        // Update UI with accurate Firestore statistics
                        totalChatsText.text = (firestoreStats["totalSessions"] ?: user.totalChats).toString()
                        totalMessagesText.text = (firestoreStats["totalMessages"] ?: user.totalMessages).toString()
                        favoriteChatsText.text = (firestoreStats["favoriteChats"] ?: 0).toString()
                        Log.d("ProfileActivity", "Updated UI with Firestore chat statistics: $firestoreStats")
                    }
                    
                    // Load recent chat history for the Total Chats card
                    Log.e("ProfileActivity", "=== STARTING CHAT HISTORY LOAD ===")
                    Log.e("ProfileActivity", "Calling profileManager.getRecentChatActivity(5)")
                    val recentChats = profileManager.getRecentChatActivity(5)
                    Log.e("ProfileActivity", "=== CHAT HISTORY RESULT: ${recentChats.size} chats ===")
                    if (recentChats.isNotEmpty()) {
                        Log.e("ProfileActivity", "About to submit list to adapter with ${recentChats.size} items")
                        Log.e("ProfileActivity", "First chat: title='${recentChats[0].title}', messages=${recentChats[0].messageCount}")
                        recentChatAdapter.submitList(recentChats)
                        recentChatHistoryRecyclerView.visibility = android.view.View.VISIBLE
                        emptyChatHistoryLayout.visibility = android.view.View.GONE
                        Log.e("ProfileActivity", "Loaded ${recentChats.size} recent chats, RecyclerView visibility: ${recentChatHistoryRecyclerView.visibility}")
                        
                        // Check adapter count after a delay (ListAdapter uses async diff)
                        recentChatHistoryRecyclerView.postDelayed({
                            Log.d("ProfileActivity", "After delay - Adapter itemCount: ${recentChatAdapter.itemCount}")
                            Log.d("ProfileActivity", "RecyclerView childCount: ${recentChatHistoryRecyclerView.childCount}")
                        }, 100)
                    } else {
                        recentChatHistoryRecyclerView.visibility = android.view.View.GONE
                        emptyChatHistoryLayout.visibility = android.view.View.VISIBLE
                        Log.e("ProfileActivity", "=== NO RECENT CHATS FOUND, SHOWING EMPTY STATE ===")
                    }
                    
                } catch (e: Exception) {
                    Log.w("ProfileActivity", "Failed to load Firestore data", e)
                    // Show empty state on error
                    recentChatHistoryRecyclerView.visibility = android.view.View.GONE
                    emptyChatHistoryLayout.visibility = android.view.View.VISIBLE
                }
            }

            // Storage usage (estimated from token count)
            val storageUsedKB = if (user.tokenCount > 0) {
                (user.tokenCount * 4) / 1024.0 // Rough estimation: 4 bytes per token
            } else {
                0.0
            }
            storageUsedText.text = String.format("%.1f KB", storageUsedKB)

            // Last activity
            val lastActivityFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            val lastActivity = if (user.lastLoginAt > 0) {
                "Last active: ${lastActivityFormat.format(Date(user.lastLoginAt))}"
            } else {
                "Last active: Today"
            }
            lastActivityText.text = lastActivity
        }
        
        Log.d("ProfileActivity", "UI updated with user data: ${user.email}, chats: ${user.totalChats}, messages: ${user.totalMessages}")
    }
    
    private fun showNoDataMessage() {
        binding.apply {
            profileNameText.text = "Loading..."
            profileEmailText.text = "Please wait..."
            memberSinceText.text = "Loading profile data..."
            totalChatsText.text = "0"
            totalMessagesText.text = "0"
            totalTokensText.text = "0"
            favoriteChatsText.text = "0"
            storageUsedText.text = "0 KB"
            lastActivityText.text = "Loading..."
        }
    }
    
    private fun showErrorMessage(message: String) {
        binding.apply {
            profileNameText.text = "Error"
            profileEmailText.text = message
            memberSinceText.text = "Unable to load profile"
            totalChatsText.text = "?"
            totalMessagesText.text = "?"
            totalTokensText.text = "?"
            favoriteChatsText.text = "?"
            storageUsedText.text = "? KB"
            lastActivityText.text = "Unknown"
        }
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun updateSubscriptionUIWithFirestore(
        firestoreStatus: FirestoreSubscriptionManager.SubscriptionStatus
    ) {
        binding.apply {
            if (firestoreStatus.isActive && !firestoreStatus.isExpired) {
                // Active subscription from Firestore
                val planDisplayName = when (firestoreStatus.planType) {
                    "basic" -> "Essential Plan"
                    "pro" -> "Professional Plan"
                    "premium" -> "Premium Plan"
                    "ultra_premium" -> "Enterprise Max"
                    else -> "Free Plan"
                }
                
                val planDescription = when (firestoreStatus.planType) {
                    "basic" -> "Basic AI experience with essential features"
                    "pro" -> "Enhanced AI experience with advanced features"
                    "premium" -> "Ultimate AI experience with all features"
                    "ultra_premium" -> "Enterprise-grade AI with unlimited everything"
                    else -> "Basic AI chat with limited features"
                }

                planNameText.text = planDisplayName
                planDescriptionText.text = planDescription

                // Subscription Tier Display
                subscriptionTierText.text = dailyTokenTracker.getTierDisplayName(firestoreStatus.planType)

                // Status
                subscriptionStatusText.text = "Active (${firestoreStatus.daysRemaining} days left)"
                subscriptionStatusText.setTextColor(getColor(R.color.glass_accent))

                // Daily token usage tracking
                val dailyUsage = dailyTokenTracker.getDailyUsage(firestoreStatus.planType)
                
                // Update daily tokens progress
                dailyTokensProgressText.text = dailyTokenTracker.formatDailyUsage(dailyUsage)
                dailyTokensProgressBar.progress = dailyTokenTracker.formatUsagePercentage(dailyUsage)
                tokenResetTimeText.text = dailyTokenTracker.getResetTimeText(dailyUsage)
                
                // Set progress bar color based on usage
                val progressColor = when {
                    dailyUsage.isOverLimit -> getColor(R.color.glass_text_secondary)
                    dailyUsage.isNearLimit -> getColor(R.color.glass_secondary)
                    else -> getColor(R.color.glass_accent)
                }
                dailyTokensProgressBar.progressTintList = android.content.res.ColorStateList.valueOf(progressColor)
                
                // Monthly progress (existing user data)
                val user = currentUser
                if (user != null) {
                    // Estimate monthly usage based on subscription type
                    val monthlyLimit = when (firestoreStatus.planType) {
                        "basic" -> 150000L
                        "pro" -> 300000L
                        "premium" -> 1000000L
                        "ultra_premium" -> -1L // Unlimited
                        else -> 30000L // Free
                    }
                    
                    if (monthlyLimit > 0) {
                        val monthlyProgress = ((user.tokenCount.toFloat() / monthlyLimit) * 100).toInt().coerceAtMost(100)
                        monthlyProgressBar.progress = monthlyProgress
                        monthlyProgressText.text = "${user.tokenCount / 1000}K / ${monthlyLimit / 1000}K"
                    } else {
                        monthlyProgressBar.progress = 0
                        monthlyProgressText.text = "Unlimited monthly usage"
                    }
                }

                // Next billing date from subscription data
                firestoreStatus.subscription?.let { subscription ->
                    val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                    val nextBillingDate = Date(subscription.endDate)
                    nextBillingText.text = "Next billing: ${dateFormat.format(nextBillingDate)}"
                }
            } else {
                // Free plan or expired subscription
                planNameText.text = "Free Plan"
                planDescriptionText.text = "Basic AI chat with limited features"
                subscriptionTierText.text = "FREE"
                
                if (firestoreStatus.isExpired) {
                    subscriptionStatusText.text = "Expired - Tap to renew"
                    subscriptionStatusText.setTextColor(getColor(R.color.glass_text_secondary))
                } else {
                    subscriptionStatusText.text = "Free"
                    subscriptionStatusText.setTextColor(getColor(R.color.glass_text_secondary))
                }

                // Daily token usage tracking for free plan
                val dailyUsage = dailyTokenTracker.getDailyUsage("free")
                
                // Update daily tokens progress
                dailyTokensProgressText.text = dailyTokenTracker.formatDailyUsage(dailyUsage)
                dailyTokensProgressBar.progress = dailyTokenTracker.formatUsagePercentage(dailyUsage)
                tokenResetTimeText.text = dailyTokenTracker.getResetTimeText(dailyUsage)
                
                // Set progress bar color for free plan
                val progressColor = when {
                    dailyUsage.isOverLimit -> getColor(R.color.glass_text_secondary)
                    dailyUsage.isNearLimit -> getColor(R.color.glass_secondary)
                    else -> getColor(R.color.glass_accent)
                }
                dailyTokensProgressBar.progressTintList = android.content.res.ColorStateList.valueOf(progressColor)
                
                // Monthly progress for free plan
                val user = currentUser
                if (user != null) {
                    val monthlyLimit = 30000L // Free plan monthly limit
                    val monthlyProgress = ((user.tokenCount.toFloat() / monthlyLimit) * 100).toInt().coerceAtMost(100)
                    monthlyProgressBar.progress = monthlyProgress
                    monthlyProgressText.text = "${user.tokenCount / 1000}K / ${monthlyLimit / 1000}K"
                } else {
                    monthlyProgressBar.progress = 0
                    monthlyProgressText.text = "No usage data"
                }
                nextBillingText.text = "No billing"
            }
        }
    }

    private fun showImageSelectionDialog() {
        val options = arrayOf("Camera", "Gallery", "Remove Photo")
        val builder = androidx.appcompat.app.AlertDialog.Builder(this, R.style.BlueDialogTheme)
        builder.setTitle("Select Profile Picture")
        builder.setItems(options) { _, which ->
            when (which) {
                0 -> openCamera()
                1 -> openGallery()
                2 -> removeProfilePicture()
            }
        }
        builder.show()
    }

    private fun openCamera() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (intent.resolveActivity(packageManager) != null) {
            cameraLauncher.launch(intent)
        } else {
            Toast.makeText(this, "Camera not available", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        imagePickerLauncher.launch(intent)
    }

    private fun handleImageSelection(uri: Uri) {
        try {
            val inputStream: InputStream? = contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            handleCameraCapture(bitmap)
        } catch (e: Exception) {
            Toast.makeText(this, "Error loading image: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleCameraCapture(bitmap: Bitmap) {
        lifecycleScope.launch {
            try {
                // For now, just update the UI - profile picture upload would need Firebase Storage
                binding.profileImageView.setImageBitmap(bitmap)
                Toast.makeText(this@ProfileActivity, "Profile picture updated locally", Toast.LENGTH_SHORT).show()
                // TODO: Implement Firebase Storage upload for profile pictures
            } catch (e: Exception) {
                Toast.makeText(this@ProfileActivity, "Error updating profile picture: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun removeProfilePicture() {
        lifecycleScope.launch {
            try {
                val uid = firebaseAuthService.getCurrentFirebaseUid()
                if (uid != null) {
                    // Update Firestore user document to remove profile picture
                    val updatedUser = currentUser?.copy(profilePictureUrl = null)
                    if (updatedUser != null) {
                        val saved = firebaseAuthService.saveUser(updatedUser)
                        if (saved) {
                            currentUser = updatedUser
                            binding.profileImageView.setImageResource(R.drawable.ic_premium_users)
                            Toast.makeText(this@ProfileActivity, "Profile picture removed", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this@ProfileActivity, "Failed to remove profile picture", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    Toast.makeText(this@ProfileActivity, "User not authenticated", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@ProfileActivity, "Error removing profile picture: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showEditProfileDialog() {
        val user = currentUser ?: return

        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_profile, null)
        val nameEditText = dialogView.findViewById<android.widget.EditText>(R.id.nameEditText)
        val emailEditText = dialogView.findViewById<android.widget.EditText>(R.id.emailEditText)

        nameEditText.setText(user.fullName)
        emailEditText.setText(user.email)
        emailEditText.isEnabled = false // Email cannot be changed

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this, R.style.BlueDialogTheme)
            .setTitle("Edit Profile")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val newName = nameEditText.text.toString().trim()
                if (newName.isNotEmpty()) {
                    updateProfile(newName)
                }
            }
            .setNegativeButton("Cancel", null)
            .create()

        dialog.show()
    }

    private fun updateProfile(newName: String) {
        lifecycleScope.launch {
            try {
                val uid = firebaseAuthService.getCurrentFirebaseUid()
                if (uid != null) {
                    // Update Firestore user document with new name
                    val updatedUser = currentUser?.copy(fullName = newName)
                    if (updatedUser != null) {
                        val saved = firebaseAuthService.saveUser(updatedUser)
                        if (saved) {
                            currentUser = updatedUser
                            binding.profileNameText.text = newName
                            Toast.makeText(this@ProfileActivity, "Profile updated successfully", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this@ProfileActivity, "Failed to update profile", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    Toast.makeText(this@ProfileActivity, "User not authenticated", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@ProfileActivity, "Error updating profile: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun logout() {
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this, R.style.BlueDialogTheme)
            .setTitle("Logout")
            .setMessage("Are you sure you want to logout?")
            .setPositiveButton("Logout") { _, _ ->
                performLogout()
            }
            .setNegativeButton("Cancel", null)
            .create()

        dialog.show()
    }

    private fun performLogout() {
        lifecycleScope.launch {
            try {
                // Sign out from Firebase only (Firestore-only architecture)
                val firebaseSuccess = firebaseAuthService.signOut()
                
                if (firebaseSuccess) {
                    // Clear any cached subscription data
                    val sharedPreferences = getSharedPreferences("prefs", MODE_PRIVATE)
                    sharedPreferences.edit().clear().apply()
                    
                    // Navigate to login screen
                    val intent = Intent(this@ProfileActivity, LoginActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                } else {
                    Toast.makeText(this@ProfileActivity, "Logout failed", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("ProfileActivity", "Error during logout", e)
                Toast.makeText(this@ProfileActivity, "Error during logout: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showClearHistoryConfirmationDialog() {
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this, R.style.BlueDialogTheme)
            .setTitle("Clear Chat History")
            .setMessage("This will permanently delete ALL your chat history from both local storage and cloud backup. This action cannot be undone.\n\nAre you sure you want to proceed?")
            .setIcon(R.drawable.ic_delete)
            .setPositiveButton("Clear All") { _, _ ->
                performClearHistory()
            }
            .setNegativeButton("Cancel", null)
            .create()

        // Make the positive button red to indicate destructive action
        dialog.setOnShowListener {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
                ?.setTextColor(getColor(R.color.glass_accent))
        }

        dialog.show()
    }

    private fun performClearHistory() {
        lifecycleScope.launch {
            try {
                binding.progressBar.visibility = android.view.View.VISIBLE
                
                Log.e("ProfileActivity", "=== USER INITIATED CHAT HISTORY DELETION ===")
                
                val success = profileManager.clearAllChatHistory()
                
                if (success) {
                    Log.e("ProfileActivity", "Chat history cleared successfully")
                    Toast.makeText(this@ProfileActivity, "Chat history cleared successfully", Toast.LENGTH_LONG).show()
                    
                    // Refresh the profile to reflect the cleared state
                    loadProfileData()
                    
                    // Also clear the recent chat adapter immediately
                    recentChatAdapter.submitList(emptyList())
                    binding.recentChatHistoryRecyclerView.visibility = android.view.View.GONE
                    binding.emptyChatHistoryLayout.visibility = android.view.View.VISIBLE
                    
                } else {
                    Log.e("ProfileActivity", "Chat history clearing failed or was partial")
                    Toast.makeText(this@ProfileActivity, "Failed to clear all chat history. Some data may remain.", Toast.LENGTH_LONG).show()
                }
                
            } catch (e: Exception) {
                Log.e("ProfileActivity", "Error during chat history clearing", e)
                Toast.makeText(this@ProfileActivity, "Error clearing chat history: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                binding.progressBar.visibility = android.view.View.GONE
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    override fun onResume() {
        super.onResume()
        // Only refresh profile data if user is already authenticated to avoid redirect loops
        if (firebaseAuthService.isSignedIn()) {
            Log.d("ProfileActivity", "User authenticated on resume - refreshing profile data")
            refreshProfileDataOnly()
        } else {
            Log.d("ProfileActivity", "User not authenticated on resume - skipping data refresh")
        }
    }
    
    private suspend fun createUserProfileInFirestore() {
        try {
            val uid = firebaseAuthService.getCurrentFirebaseUid()
            if (uid != null) {
                Log.d("ProfileActivity", "Creating user profile for UID: $uid")
                
                // Get Firebase user for additional info
                val firebaseAuth = com.google.firebase.auth.FirebaseAuth.getInstance()
                val firebaseUser = firebaseAuth.currentUser
                
                val firestoreUser = FirebaseAuthenticationService.FirestoreUser(
                    uid = uid,
                    email = firebaseUser?.email ?: "",
                    fullName = firebaseUser?.displayName ?: "User",
                    profilePictureUrl = firebaseUser?.photoUrl?.toString(),
                    createdAt = System.currentTimeMillis(),
                    lastLoginAt = System.currentTimeMillis(),
                    subscriptionTier = "FREE",
                    subscriptionStatus = "INACTIVE",
                    subscriptionExpiresAt = 0L,
                    messageCount = 0,
                    tokenCount = 0L,
                    totalChats = 0,
                    totalMessages = 0
                )
                
                val saved = firebaseAuthService.saveUser(firestoreUser)
                if (saved) {
                    Log.d("ProfileActivity", "User profile created successfully in Firestore")
                } else {
                    Log.e("ProfileActivity", "Failed to create user profile in Firestore")
                }
            } else {
                Log.e("ProfileActivity", "Cannot create user profile - user not authenticated")
            }
        } catch (e: Exception) {
            Log.e("ProfileActivity", "Error creating user profile in Firestore", e)
        }
    }
    
    private fun refreshProfileDataOnly() {
        // Refresh profile data without authentication checks or redirects
        lifecycleScope.launch {
            try {
                binding.progressBar.visibility = android.view.View.VISIBLE
                
                // Load user data from Firestore (already authenticated)
                currentUser = firebaseAuthService.getCurrentUser()
                
                // Sync chat history to Firestore and migrate stats if needed
                try {
                    // Ensure chat history is synced to Firestore for cross-device access
                    val syncSuccess = profileManager.syncChatHistoryToFirestore()
                    Log.d("ProfileActivity", "Profile refresh chat history sync result: $syncSuccess")
                    
                    migrateChatHistoryStatsToFirestore()
                } catch (e: Exception) {
                    Log.w("ProfileActivity", "Chat history sync/migration failed during refresh", e)
                }
                
                // Force billing sync and load subscription status from Firestore
                try {
                    val syncResult = billingSync.syncSubscriptionToFirestore()
                    Log.d("ProfileActivity", "Refresh billing sync result: $syncResult")
                    kotlinx.coroutines.delay(500) // Brief delay for Firestore consistency
                } catch (e: Exception) {
                    Log.w("ProfileActivity", "Billing sync failed during refresh", e)
                }
                
                val firestoreSubscriptionStatus = billingSync.getSubscriptionStatusForDisplay()
                
                // Update UI
                updateUI()
                updateSubscriptionUIWithFirestore(firestoreSubscriptionStatus)
                
            } catch (e: Exception) {
                Log.e("ProfileActivity", "Error refreshing profile", e)
                Toast.makeText(this@ProfileActivity, "Error refreshing profile: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                binding.progressBar.visibility = android.view.View.GONE
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Cleanup billing sync service
        try {
            billingSync.cleanup()
        } catch (e: Exception) {
            Log.w("ProfileActivity", "Error cleaning up billing sync", e)
        }
    }
    
    /**
     * Migrate chat history statistics from local Room database to Firestore user profile
     * This ensures ProfileActivity shows actual chat and message counts
     */
    private suspend fun migrateChatHistoryStatsToFirestore() {
        try {
            val uid = firebaseAuthService.getCurrentFirebaseUid()
            if (uid == null) {
                Log.w("ProfileActivity", "Cannot migrate chat stats - user not authenticated")
                return
            }
            
            Log.d("ProfileActivity", "Attempting to migrate chat history stats for user: $uid")
            
            // Try to get actual stats from local Room database
            val (localTotalChats, localTotalMessages) = try {
                val database = DatabaseProvider.database
                val conversations = database.conversationDao().getConversations().first()
                val totalChats = conversations.size
                
                var totalMessages = 0
                conversations.forEach { conversation ->
                    val messages = database.messageDao().getMessages(conversation.id).first()
                    totalMessages += messages.size
                }
                
                Log.d("ProfileActivity", "Found local chat stats: $totalChats chats, $totalMessages messages")
                Pair(totalChats, totalMessages)
            } catch (e: Exception) {
                Log.w("ProfileActivity", "Could not access local database for stats, using SharedPreferences fallback", e)
                // Fallback: Try to get stats from SharedPreferences or other sources
                val sharedPrefs = getSharedPreferences("chat_stats", MODE_PRIVATE)
                val totalChats = sharedPrefs.getInt("total_chats", 0)
                val totalMessages = sharedPrefs.getInt("total_messages", 0)
                Pair(totalChats, totalMessages)
            }
            
            // Get current user data
            val currentUser = firebaseAuthService.getCurrentUser()
            if (currentUser != null) {
                // Update user profile with actual or estimated chat statistics
                val shouldUpdate = currentUser.totalChats < localTotalChats || 
                                   currentUser.totalMessages < localTotalMessages ||
                                   (currentUser.totalChats == 0 && currentUser.totalMessages == 0 && (localTotalChats > 0 || localTotalMessages > 0))
                
                if (shouldUpdate) {
                    Log.d("ProfileActivity", "Updating user profile with chat stats: $localTotalChats chats, $localTotalMessages messages")
                    val updatedUser = currentUser.copy(
                        totalChats = localTotalChats.coerceAtLeast(currentUser.totalChats),
                        totalMessages = localTotalMessages.coerceAtLeast(currentUser.totalMessages),
                        messageCount = localTotalMessages.coerceAtLeast(currentUser.messageCount),
                        lastLoginAt = System.currentTimeMillis()
                    )
                    
                    val saved = firebaseAuthService.saveUser(updatedUser)
                    if (saved) {
                        Log.d("ProfileActivity", "Successfully updated chat stats in Firestore profile")
                        this.currentUser = updatedUser
                        
                        // Save stats to SharedPreferences for future fallback
                        val sharedPrefs = getSharedPreferences("chat_stats", MODE_PRIVATE)
                        sharedPrefs.edit()
                            .putInt("total_chats", localTotalChats)
                            .putInt("total_messages", localTotalMessages)
                            .apply()
                    } else {
                        Log.e("ProfileActivity", "Failed to save updated chat stats to Firestore")
                    }
                } else {
                    Log.d("ProfileActivity", "User already has current chat stats: ${currentUser.totalChats} chats, ${currentUser.totalMessages} messages")
                }
            }
            
        } catch (e: Exception) {
            Log.e("ProfileActivity", "Error migrating chat history stats", e)
        }
    }
    
    /**
     * Create a basic user object from Firebase Auth when Firestore fails
     * This allows the profile to display basic information and current chat history from SharedPreferences
     */
    private fun createBasicUserFromFirebaseAuth(firebaseUser: com.google.firebase.auth.FirebaseUser): FirebaseAuthenticationService.FirestoreUser {
        Log.e("ProfileActivity", "Creating basic user from Firebase Auth: uid=${firebaseUser.uid}, email=${firebaseUser.email}")
        
        // Get current chat history from SharedPreferences to populate statistics
        val sharedPrefs = getSharedPreferences("prefs", android.content.Context.MODE_PRIVATE)
        val chatHistoryJson = sharedPrefs.getString("chat_history", "[]") ?: "[]"
        
        var messageCount = 0
        var totalChats = 0
        
        try {
            val chatArray = org.json.JSONArray(chatHistoryJson)
            totalChats = chatArray.length()
            
            // Count messages across all chats
            for (i in 0 until chatArray.length()) {
                val chatObject = chatArray.getJSONObject(i)
                val messages = chatObject.optJSONArray("messages")
                if (messages != null) {
                    messageCount += messages.length()
                }
            }
        } catch (e: Exception) {
            Log.w("ProfileActivity", "Error parsing SharedPreferences chat data", e)
        }
        
        return FirebaseAuthenticationService.FirestoreUser(
            uid = firebaseUser.uid,
            email = firebaseUser.email ?: "unknown@example.com",
            fullName = firebaseUser.displayName ?: "User",
            profilePictureUrl = firebaseUser.photoUrl?.toString(),
            createdAt = firebaseUser.metadata?.creationTimestamp ?: System.currentTimeMillis(),
            lastLoginAt = System.currentTimeMillis(),
            lastSyncAt = System.currentTimeMillis(),
            subscriptionTier = "FREE",
            subscriptionStatus = "INACTIVE",
            subscriptionExpiresAt = 0L,
            messageCount = messageCount,
            tokenCount = (messageCount * 50L), // Estimate ~50 tokens per message
            totalChats = totalChats,
            totalMessages = messageCount
        )
    }

    /**
     * Debug method to populate test data for testing UI display
     * Can be called manually during development to test profile display
     */
    private suspend fun populateTestDataForDebugging() {
        try {
            val uid = firebaseAuthService.getCurrentFirebaseUid()
            if (uid == null) {
                Log.w("ProfileActivity", "Cannot populate test data - user not authenticated")
                return
            }
            
            val currentUser = firebaseAuthService.getCurrentUser()
            if (currentUser != null) {
                // Populate with realistic test data
                val updatedUser = currentUser.copy(
                    totalChats = 15,  // Test data
                    totalMessages = 127, // Test data
                    messageCount = 127,
                    tokenCount = 15000L, // Test data
                    lastLoginAt = System.currentTimeMillis()
                )
                
                val saved = firebaseAuthService.saveUser(updatedUser)
                if (saved) {
                    Log.d("ProfileActivity", "Populated test data for debugging")
                    this.currentUser = updatedUser
                    
                    // Update UI to reflect test data
                    updateUI()
                } else {
                    Log.e("ProfileActivity", "Failed to save test data")
                }
            }
        } catch (e: Exception) {
            Log.e("ProfileActivity", "Error populating test data", e)
        }
    }
}