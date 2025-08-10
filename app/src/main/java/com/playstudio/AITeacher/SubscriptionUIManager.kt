package com.playstudio.aiteacher

import android.app.Activity
import android.content.Context
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.rewarded.RewardedAd
import com.playstudio.aiteacher.pricing.AIModel
import com.playstudio.aiteacher.pricing.SubscriptionTier
import com.playstudio.aiteacher.profile.SubscriptionManager
import com.playstudio.aiteacher.profile.FirestoreSubscriptionManager
import com.playstudio.aiteacher.profile.FirebaseAuthenticationService
import kotlinx.coroutines.launch

/**
 * Manages subscription-based UI changes across the entire app
 * - Hides/shows buy buttons based on subscription status
 * - Removes ads for subscribed users
 * - Updates AI model availability in ChatFragment
 * - Syncs subscription status with Firebase
 */
class SubscriptionUIManager(private val context: Context) {
    
    private val subscriptionManager = SubscriptionManager(context)
    private val firestoreSubscriptionManager = FirestoreSubscriptionManager(context)
    private val firebaseAuthService = FirebaseAuthenticationService(context)
    
    companion object {
        private const val TAG = "SubscriptionUIManager"
        
        // View IDs that should be hidden for subscribed users
        private val BUY_BUTTON_IDS = listOf(
            R.id.btn_subscribe,
            R.id.btn_upgrade,
            R.id.btn_buy_premium,
            R.id.btn_get_pro,
            R.id.subscription_button,
            R.id.premium_button,
            R.id.upgrade_button
        )
        
        // Alias for subscription button IDs (used in security fixes)
        private val SUBSCRIPTION_BUTTON_IDS = BUY_BUTTON_IDS
        
        /**
         * Static method to ensure suggested questions are always visible
         * Can be called from any activity or fragment without creating SubscriptionUIManager instance
         */
        @JvmStatic
        fun forceShowSuggestedQuestions(activity: Activity) {
            try {
                val manager = SubscriptionUIManager(activity)
                manager.ensureSuggestedQuestionsVisible(activity)
                Log.d(TAG, "Force-showed suggested questions from static method")
            } catch (e: Exception) {
                Log.e(TAG, "Error force-showing suggested questions", e)
            }
        }
        
        /**
         * Static method to ensure suggested questions are always visible in a specific view
         */
        @JvmStatic
        fun forceShowSuggestedQuestions(view: View) {
            try {
                val manager = SubscriptionUIManager(view.context)
                manager.ensureSuggestedQuestionsVisible(view)
                Log.d(TAG, "Force-showed suggested questions in view from static method")
            } catch (e: Exception) {
                Log.e(TAG, "Error force-showing suggested questions in view", e)
            }
        }
        
        // Ad view IDs that should be hidden for subscribed users
        private val AD_VIEW_IDS = listOf(
            R.id.adView,
            R.id.banner_ad,
            R.id.interstitial_ad_container,
            R.id.rewarded_ad_container,
            R.id.native_ad_container
        )
    }
    
    /**
     * Update UI based on current subscription status
     * Call this in onResume() of activities/fragments
     */
    suspend fun updateUIForSubscriptionStatus(activity: Activity) {
        try {
            // SECURITY FIX: Block subscription UI access for unauthenticated users
            if (!firebaseAuthService.isSignedIn()) {
                Log.d(TAG, "User not authenticated, hiding subscription UI and showing authentication prompt")
                hideSubscriptionUIForUnauthenticatedUser(activity)
                return
            }
            
            // Get subscription status from Firestore
            val firestoreStatus = firestoreSubscriptionManager.getSubscriptionStatus()
            val isSubscribed = firestoreStatus.isActive && !firestoreStatus.isExpired
            val subscriptionTier = getSubscriptionTier(firestoreStatus.planType)
            
            Log.d(TAG, "Updating UI for subscription status: isSubscribed=$isSubscribed, tier=$subscriptionTier, expired=${firestoreStatus.isExpired}")
            
            // Update buy buttons based on subscription status
            updateBuyButtons(activity, isSubscribed)
            
            // Update ads
            updateAdVisibility(activity, isSubscribed)
            
            // Update subscription status text
            updateSubscriptionStatusText(activity, firestoreStatus)
            
            // CRITICAL: Always ensure suggested questions remain visible
            ensureSuggestedQuestionsVisible(activity)
            
            // Notify that UI has been updated
            Log.d(TAG, "UI updated successfully for subscription status")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error updating UI for subscription status", e)
            // Fallback to free tier if error
            updateUIForFreeTier(activity)
        }
    }
    
    /**
     * Update UI for Fragment (ChatFragment, etc.)
     */
    suspend fun updateUIForSubscriptionStatus(fragment: Fragment) {
        try {
            // Check if user is authenticated
            if (!firebaseAuthService.isSignedIn()) {
                Log.d(TAG, "User not authenticated, showing free tier UI in fragment")
                fragment.view?.let { view ->
                    updateBuyButtons(view, false)
                    updateAdVisibility(view, false)
                }
                return
            }
            
            val firestoreStatus = firestoreSubscriptionManager.getSubscriptionStatus()
            val isSubscribed = firestoreStatus.isActive && !firestoreStatus.isExpired
            
            Log.d(TAG, "Updating Fragment UI for subscription status: isSubscribed=$isSubscribed")
            
            fragment.view?.let { view ->
                // Update buy buttons in fragment
                updateBuyButtons(view, isSubscribed)
                
                // Update ads in fragment
                updateAdVisibility(view, isSubscribed)
                
                // CRITICAL: Always ensure suggested questions remain visible
                ensureSuggestedQuestionsVisible(view)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error updating Fragment UI for subscription status", e)
            // Fallback to free tier
            fragment.view?.let { view ->
                updateBuyButtons(view, false)
                updateAdVisibility(view, false)
                // Always ensure suggested questions are visible, even in fallback
                ensureSuggestedQuestionsVisible(view)
            }
        }
    }
    
    /**
     * Update UI for free tier (unauthenticated users)
     */
    private fun updateUIForFreeTier(activity: Activity) {
        // Show all buy buttons
        updateBuyButtons(activity, false)
        // Show all ads
        updateAdVisibility(activity, false)
        // Update status text
        try {
            val statusTextViewIds = listOf(
                R.id.tv_subscription_status,
                R.id.subscription_status,
                R.id.plan_status,
                R.id.user_plan_text
            )
            
            statusTextViewIds.forEach { textViewId: Int ->
                try {
                    val textView = activity.findViewById<TextView>(textViewId)
                    textView?.text = "Free Plan - Sign in to upgrade"
                } catch (e: Exception) {
                    // Text view not found, continue
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating free tier status text", e)
        }
    }
    
    /**
     * Get available AI models based on subscription tier
     */
    suspend fun getAvailableAIModels(): List<AIModel> {
        return try {
            // Unauthenticated users get free tier models
            if (!firebaseAuthService.isSignedIn()) {
                return AIModel.getModelsForTier(SubscriptionTier.FREE)
            }
            
            val subscriptionStatus = firestoreSubscriptionManager.getSubscriptionStatus()
            val tier = getSubscriptionTier(subscriptionStatus.planType)
            val availableModels = AIModel.getModelsForTier(tier)
            
            Log.d(TAG, "Available AI models for tier $tier: ${availableModels.size} models")
            availableModels
            
        } catch (e: Exception) {
            Log.e(TAG, "Error getting available AI models", e)
            AIModel.getModelsForTier(SubscriptionTier.FREE) // Fallback to free tier
        }
    }
    
    /**
     * Check if user can access specific AI model
     */
    suspend fun canAccessModel(model: AIModel): Boolean {
        return try {
            // Unauthenticated users get free tier access
            if (!firebaseAuthService.isSignedIn()) {
                val freeModels = AIModel.getModelsForTier(SubscriptionTier.FREE)
                return model in freeModels
            }
            
            val subscriptionStatus = firestoreSubscriptionManager.getSubscriptionStatus()
            val userTier = getSubscriptionTier(subscriptionStatus.planType)
            val availableModels = AIModel.getModelsForTier(userTier)
            
            val canAccess = model in availableModels
            Log.d(TAG, "Can access model ${model.displayName}: $canAccess (user tier: $userTier)")
            canAccess
            
        } catch (e: Exception) {
            Log.e(TAG, "Error checking model access", e)
            false
        }
    }
    
    /**
     * Require authentication before subscription operations
     */
    suspend fun requireAuthenticationForSubscription(): Boolean {
        return if (!firebaseAuthService.isSignedIn()) {
            Log.w(TAG, "Authentication required for subscription operations")
            false
        } else {
            true
        }
    }
    
    /**
     * Update buy/subscription buttons visibility
     */
    private fun updateBuyButtons(activity: Activity, isSubscribed: Boolean) {
        BUY_BUTTON_IDS.forEach { buttonId: Int ->
            try {
                val button = activity.findViewById<Button>(buttonId)
                button?.visibility = if (isSubscribed) View.GONE else View.VISIBLE
                
                if (button != null) {
                    Log.d(TAG, "Updated buy button visibility: ${button.javaClass.simpleName} = ${if (isSubscribed) "GONE" else "VISIBLE"}")
                }
            } catch (e: Exception) {
                // Button not found in this activity, continue
            }
        }
        
        // Also check for any buttons with subscription-related text
        try {
            val rootView = activity.findViewById<View>(android.R.id.content)
            updateSubscriptionButtonsRecursively(rootView, isSubscribed)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating subscription buttons recursively", e)
        }
    }
    
    /**
     * Update buy/subscription buttons visibility in a view
     */
    private fun updateBuyButtons(view: View, isSubscribed: Boolean) {
        BUY_BUTTON_IDS.forEach { buttonId: Int ->
            try {
                val button = view.findViewById<Button>(buttonId)
                button?.visibility = if (isSubscribed) View.GONE else View.VISIBLE
            } catch (e: Exception) {
                // Button not found in this view, continue
            }
        }
        
        // Check for subscription-related buttons recursively
        updateSubscriptionButtonsRecursively(view, isSubscribed)
    }
    
    /**
     * Recursively find and update subscription buttons
     * IMPORTANT: Never hide suggested questions or chat-related elements
     */
    private fun updateSubscriptionButtonsRecursively(view: View, isSubscribed: Boolean) {
        // Skip entire containers that should never have hidden elements
        val viewClassName = view.javaClass.simpleName.lowercase()
        val viewIdName = try {
            view.resources?.getResourceEntryName(view.id)?.lowercase() ?: ""
        } catch (e: Exception) {
            ""
        }
        
        // Never hide elements in chat-related containers
        if (viewIdName.contains("question") ||
            viewIdName.contains("suggest") ||
            viewIdName.contains("chat") ||


            viewIdName.contains("message") ||
            viewIdName.contains("conversation") ||
            viewClassName.contains("recycler") ||
            view.tag?.toString()?.contains("chat") == true ||
            view.tag?.toString()?.contains("question") == true) {
            Log.d(TAG, "Skipping subscription button updates in chat-related container: $viewIdName")
            return
        }
        
        if (view is Button) {
            val buttonText = view.text.toString().lowercase()
            
            // NEVER hide suggested questions or any question-related buttons
            if (buttonText.contains("question") ||
                buttonText.contains("suggest") ||
                buttonText.contains("ask") ||
                buttonText.contains("?") ||
                buttonText.contains("what") ||
                buttonText.contains("how") ||
                buttonText.contains("why") ||
                buttonText.contains("when") ||
                buttonText.contains("where") ||
                view.tag?.toString()?.contains("question") == true ||
                view.tag?.toString()?.contains("suggest") == true) {
                // Always keep suggested questions visible
                Log.d(TAG, "Protecting suggested question from hiding: '$buttonText'")
                return
            }
            
            // Only hide buttons that are clearly subscription-related AND short
            if (buttonText.length <= 30 && // Only consider short button texts for subscription buttons
                (buttonText == "subscribe" || 
                 buttonText == "upgrade" ||
                 buttonText == "buy premium" ||
                 buttonText == "get pro" ||
                 buttonText == "premium" ||
                 buttonText.matches(Regex("(subscribe|upgrade|buy|premium|pro)\\s*(now|plan)?")))) {
                
                view.visibility = if (isSubscribed) View.GONE else View.VISIBLE
                Log.d(TAG, "Updated confirmed subscription button: '$buttonText' = ${if (isSubscribed) "GONE" else "VISIBLE"}")
            }
        } else if (view is ViewGroup) {
            // Continue recursively for containers that are not chat-related
            for (i in 0 until view.childCount) {
                updateSubscriptionButtonsRecursively(view.getChildAt(i), isSubscribed)
            }
        }
    }
    
    /**
     * Ensure suggested questions are always visible regardless of subscription status
     * Call this method after updating subscription UI to guarantee questions remain visible
     */
    fun ensureSuggestedQuestionsVisible(activity: Activity) {
        try {
            val rootView = activity.findViewById<View>(android.R.id.content)
            ensureSuggestedQuestionsVisibleRecursively(rootView)
            Log.d(TAG, "Ensured suggested questions are visible")
        } catch (e: Exception) {
            Log.e(TAG, "Error ensuring suggested questions are visible", e)
        }
    }
    
    /**
     * Ensure suggested questions are always visible in a specific view
     */
    fun ensureSuggestedQuestionsVisible(view: View) {
        try {
            ensureSuggestedQuestionsVisibleRecursively(view)
            Log.d(TAG, "Ensured suggested questions are visible in view")
        } catch (e: Exception) {
            Log.e(TAG, "Error ensuring suggested questions are visible in view", e)
        }
    }
    
    /**
     * Recursively ensure suggested questions are visible
     */
    private fun ensureSuggestedQuestionsVisibleRecursively(view: View) {
        if (view is Button) {
            val buttonText = view.text.toString().lowercase()
            
            // Make sure all question-related buttons are visible
            if (buttonText.contains("question") ||
                buttonText.contains("suggest") ||
                buttonText.contains("ask") ||
                buttonText.contains("?") ||
                buttonText.contains("what") ||
                buttonText.contains("how") ||
                buttonText.contains("why") ||
                buttonText.contains("when") ||
                buttonText.contains("where") ||
                view.tag?.toString()?.contains("question") == true ||
                view.tag?.toString()?.contains("suggest") == true) {
                
                view.visibility = View.VISIBLE
                Log.d(TAG, "Restored suggested question visibility: '$buttonText'")
            }
        } else if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                ensureSuggestedQuestionsVisibleRecursively(view.getChildAt(i))
            }
        }
    }
    
    /**
     * Update ad visibility based on subscription status
     */
    private fun updateAdVisibility(activity: Activity, isSubscribed: Boolean) {
        AD_VIEW_IDS.forEach { adViewId ->
            try {
                val adView = activity.findViewById<AdView>(adViewId)
                if (adView != null) {
                    adView.visibility = if (isSubscribed) View.GONE else View.VISIBLE
                    if (isSubscribed) {
                        adView.destroy() // Clean up ad resources
                    }
                    Log.d(TAG, "Updated ad visibility: ${adView.javaClass.simpleName} = ${if (isSubscribed) "GONE" else "VISIBLE"}")
                }
            } catch (e: Exception) {
                // Ad view not found in this activity, continue
            }
        }
        
        // Hide any other ad containers
        try {
            val rootView = activity.findViewById<View>(android.R.id.content)
            hideAdsRecursively(rootView, isSubscribed)
        } catch (e: Exception) {
            Log.e(TAG, "Error hiding ads recursively", e)
        }
    }
    
    /**
     * Update ad visibility in a view
     */
    private fun updateAdVisibility(view: View, isSubscribed: Boolean) {
        AD_VIEW_IDS.forEach { adViewId ->
            try {
                val adView = view.findViewById<AdView>(adViewId)
                if (adView != null) {
                    adView.visibility = if (isSubscribed) View.GONE else View.VISIBLE
                    if (isSubscribed) {
                        adView.destroy()
                    }
                }
            } catch (e: Exception) {
                // Ad view not found in this view, continue
            }
        }
        
        hideAdsRecursively(view, isSubscribed)
    }
    
    /**
     * Recursively find and hide ad views
     */
    private fun hideAdsRecursively(view: View, isSubscribed: Boolean) {
        if (view is AdView) {
            view.visibility = if (isSubscribed) View.GONE else View.VISIBLE
            if (isSubscribed) {
                view.destroy()
            }
            Log.d(TAG, "Updated ad view: ${view.javaClass.simpleName} = ${if (isSubscribed) "GONE" else "VISIBLE"}")
        } else if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                hideAdsRecursively(view.getChildAt(i), isSubscribed)
            }
        }
    }
    
    /**
     * Update subscription status text in UI
     */
    private fun updateSubscriptionStatusText(activity: Activity, subscriptionStatus: FirestoreSubscriptionManager.SubscriptionStatus) {
        try {
            // Look for subscription status text views
            val statusTextViewIds = listOf(
                R.id.tv_subscription_status,
                R.id.subscription_status,
                R.id.plan_status,
                R.id.user_plan_text
            )
            
            statusTextViewIds.forEach { textViewId: Int ->
                try {
                    val textView = activity.findViewById<TextView>(textViewId)
                    if (textView != null) {
                        val statusText = when {
                            subscriptionStatus.isActive && !subscriptionStatus.isExpired -> {
                                val planName = when (subscriptionStatus.planType) {
                                    "pro" -> "Professional Plan"
                                    "premium" -> "Premium Plan"
                                    else -> "Free Plan"
                                }
                                
                                when {
                                    subscriptionStatus.daysRemaining > 30 -> "✅ $planName Active - ${subscriptionStatus.daysRemaining} days left"
                                    subscriptionStatus.daysRemaining > 7 -> "⚠️ $planName - ${subscriptionStatus.daysRemaining} days left"
                                    subscriptionStatus.daysRemaining > 0 -> "🔔 $planName - Expires in ${subscriptionStatus.daysRemaining} days"
                                    else -> "❌ Subscription Expired"
                                }
                            }
                            subscriptionStatus.isExpired -> "❌ Subscription Expired - Tap to Renew"
                            else -> "Free Plan - Limited Access"
                        }
                        
                        textView.text = statusText
                        Log.d(TAG, "Updated subscription status text: $statusText")
                    }
                } catch (e: Exception) {
                    // Text view not found, continue
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating subscription status text", e)
        }
        
        // Always ensure suggested questions are visible after any UI update
        ensureSuggestedQuestionsVisible(activity)
    }
    
    /**
     * Handle subscription purchase completion (requires authentication)
     */
    suspend fun onSubscriptionPurchased(
        planType: String, 
        billingCycle: String = "monthly",
        orderId: String?, 
        productId: String?,
        purchaseToken: String,
        pricePaid: Double
    ): Boolean {
        return try {
            Log.d(TAG, "Processing subscription purchase: $planType")
            
            // Check if user is authenticated
            if (!firebaseAuthService.isSignedIn()) {
                Log.e(TAG, "User must be authenticated to purchase subscription")
                throw IllegalStateException("Authentication required for subscription purchase")
            }
            
            // Purchase through Firestore manager
            val success = firestoreSubscriptionManager.purchaseSubscription(
                planType = planType,
                billingCycle = billingCycle,
                pricePaid = pricePaid,
                productId = productId ?: "",
                orderId = orderId ?: "",
                purchaseToken = purchaseToken
            )
            
            if (success) {
                Log.d(TAG, "Subscription activated successfully in Firestore")
                
                // Update UI immediately for current context
                if (context is Activity) {
                    updateUIForSubscriptionStatus(context)
                }
            } else {
                Log.e(TAG, "Failed to activate subscription in Firestore")
            }
            
            success
            
        } catch (e: Exception) {
            Log.e(TAG, "Error processing subscription purchase", e)
            false
        }
    }
    
    /**
     * Check if user should see ads
     */
    suspend fun shouldShowAds(): Boolean {
        return try {
            // Unauthenticated users should see ads
            if (!firebaseAuthService.isSignedIn()) {
                return true
            }
            
            val subscriptionStatus = firestoreSubscriptionManager.getSubscriptionStatus()
            val shouldShow = !subscriptionStatus.isActive || subscriptionStatus.isExpired
            Log.d(TAG, "Should show ads: $shouldShow")
            shouldShow
        } catch (e: Exception) {
            Log.e(TAG, "Error checking if should show ads", e)
            true // Show ads by default if error
        }
    }
    
    /**
     * Get user's current subscription tier
     */
    suspend fun getUserSubscriptionTier(): SubscriptionTier {
        return try {
            // Unauthenticated users get free tier
            if (!firebaseAuthService.isSignedIn()) {
                return SubscriptionTier.FREE
            }
            
            val subscriptionStatus = firestoreSubscriptionManager.getSubscriptionStatus()
            val tier = if (subscriptionStatus.isActive && !subscriptionStatus.isExpired) {
                getSubscriptionTier(subscriptionStatus.planType)
            } else {
                SubscriptionTier.FREE
            }
            
            Log.d(TAG, "getUserSubscriptionTier from Firestore: planType=${subscriptionStatus.planType}, tier=$tier")
            tier
        } catch (e: Exception) {
            Log.e(TAG, "Error getting user subscription tier", e)
            SubscriptionTier.FREE
        }
    }
    
    /**
     * Convert plan type string to SubscriptionTier enum
     */
    private fun getSubscriptionTier(planType: String): SubscriptionTier {
        return when (planType.lowercase()) {
            "free" -> SubscriptionTier.FREE
            "basic", "essential" -> SubscriptionTier.BASIC  // Map "essential" to BASIC tier
            "pro" -> SubscriptionTier.PRO
            "premium" -> SubscriptionTier.PREMIUM
            "ultra_premium" -> SubscriptionTier.ENTERPRISE
            else -> SubscriptionTier.FREE
        }
    }
    
    /**
     * SECURITY FIX: Hide subscription UI and show authentication prompt for unauthenticated users
     */
    private fun hideSubscriptionUIForUnauthenticatedUser(activity: Activity) {
        try {
            // Hide all subscription-related buy buttons
            SUBSCRIPTION_BUTTON_IDS.forEach { buttonId ->
                try {
                    val button = activity.findViewById<Button>(buttonId)
                    if (button != null) {
                        button.visibility = View.GONE
                        Log.d(TAG, "Hidden subscription button for unauthenticated user: ${button.text}")
                    }
                } catch (e: Exception) {
                    // Button not found in this activity, continue
                }
            }
            
            // Show authentication prompt instead of subscription options
            showAuthenticationPrompt(activity)
            
            // Show free tier features only
            updateUIForFreeTier(activity)
            
            Log.d(TAG, "Successfully hidden subscription UI for unauthenticated user")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error hiding subscription UI for unauthenticated user", e)
        }
    }
    
    /**
     * Show authentication prompt to user
     */
    private fun showAuthenticationPrompt(activity: Activity) {
        try {
            // Try to find common text view IDs to show authentication message
            val possibleTextViewIds = listOf(
                "subscription_status_text",
                "status_text", 
                "current_plan_text",
                "subscription_message"
            )
            
            var messageView: TextView? = null
            
            // Try to find any of these text views
            for (idName in possibleTextViewIds) {
                try {
                    val resourceId = activity.resources.getIdentifier(idName, "id", activity.packageName)
                    if (resourceId != 0) {
                        messageView = activity.findViewById<TextView>(resourceId)
                        if (messageView != null) break
                    }
                } catch (e: Exception) {
                    // Continue to next ID
                }
            }
            
            messageView?.apply {
                text = "Sign in to access premium features and subscriptions"
                visibility = View.VISIBLE
                try {
                    setTextColor(ContextCompat.getColor(activity, R.color.glass_text_secondary))
                } catch (e: Exception) {
                    // Use default text color if resource not found
                    setTextColor(0x80FFFFFF.toInt()) // Semi-transparent white
                }
            }
            
        } catch (e: Exception) {
            Log.d(TAG, "Could not show authentication prompt text: ${e.message}")
        }
    }

    /**
     * Setup subscription UI manager for an Activity
     * Call this in onCreate() or onResume()
     */
    fun setupForActivity(activity: Activity) {
        if (activity is androidx.lifecycle.LifecycleOwner) {
            (activity as androidx.lifecycle.LifecycleOwner).lifecycleScope.launch {
                updateUIForSubscriptionStatus(activity)
            }
        }
    }
    
    /**
     * Setup subscription UI manager for a Fragment
     * Call this in onViewCreated() or onResume()
     */
    fun setupForFragment(fragment: Fragment) {
        fragment.lifecycleScope.launch {
            updateUIForSubscriptionStatus(fragment)
        }
    }
    
    /**
     * Data class for local subscription status
     */
    data class LocalSubscriptionStatus(
        val isActive: Boolean,
        val isExpired: Boolean,
        val tier: SubscriptionTier,
        val expirationTime: Long,
        val remainingDays: Int
    )
    
    /**
     * Check local subscription status (faster than remote check)
     */
    private fun checkLocalSubscriptionStatus(): LocalSubscriptionStatus {
        val sharedPreferences = context.getSharedPreferences("subscription_prefs", Context.MODE_PRIVATE)
        
        val isActive = sharedPreferences.getBoolean("subscription_active", false)
        val tierName = sharedPreferences.getString("subscription_tier", SubscriptionTier.FREE.name)
        val expirationTime = sharedPreferences.getLong("expiration_time", 0)
        
        val tier = try {
            SubscriptionTier.valueOf(tierName ?: SubscriptionTier.FREE.name)
        } catch (e: IllegalArgumentException) {
            SubscriptionTier.FREE
        }
        
        val currentTime = System.currentTimeMillis()
        val isExpired = expirationTime <= currentTime
        val remainingDays = if (isExpired) 0 else ((expirationTime - currentTime) / (24 * 60 * 60 * 1000L)).toInt()
        
        return LocalSubscriptionStatus(
            isActive = isActive,
            isExpired = isExpired,
            tier = tier,
            expirationTime = expirationTime,
            remainingDays = remainingDays
        )
    }
    
    /**
     * Check if subscription is expired and update stored status
     */
    private fun checkAndUpdateExpiredSubscription(): Boolean {
        val localStatus = checkLocalSubscriptionStatus()
        
        if (localStatus.isActive && localStatus.isExpired) {
            // Subscription has expired, update stored status
            val sharedPreferences = context.getSharedPreferences("subscription_prefs", Context.MODE_PRIVATE)
            sharedPreferences.edit().apply {
                putBoolean("subscription_active", false)
                putString("subscription_tier", SubscriptionTier.FREE.name)
                apply()
            }
            
            Log.d(TAG, "Subscription expired, updated to free tier")
            return true
        }
        
        return false
    }
}