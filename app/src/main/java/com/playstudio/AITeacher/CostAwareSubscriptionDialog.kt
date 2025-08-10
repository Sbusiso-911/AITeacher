package com.playstudio.aiteacher

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.android.billingclient.api.ProductDetails
// Removed databinding import to fix compilation issues
// import com.playstudio.aiteacher.databinding.DialogCostAwareSubscriptionBinding
import com.playstudio.aiteacher.MainActivity
import com.playstudio.aiteacher.R
import com.playstudio.aiteacher.pricing.*
import com.playstudio.aiteacher.profile.FirebaseAuthenticationService
import com.playstudio.aiteacher.profile.ProfileActivity
import kotlinx.coroutines.launch
import android.content.Intent
import android.widget.Toast

/**
 * Cost-aware subscription dialog that integrates with the new pricing system
 * Shows tier-specific AI model access and real pricing
 */
class CostAwareSubscriptionDialog(
    private val context: Context,
    private val costManager: CostManager,
    private val onPurchaseSelected: (SubscriptionTier, String) -> Unit, // tier, productId
    private val onDismiss: () -> Unit
) {

    private lateinit var dialog: Dialog
    private lateinit var dialogView: View
    
    // UI elements
    private lateinit var btnPurchase: Button
    private lateinit var btnClose: TextView
    private lateinit var currentTierText: TextView
    private lateinit var currentUsageText: TextView
    private lateinit var freeTier: LinearLayout
    private lateinit var basicTier: LinearLayout
    private lateinit var proTier: LinearLayout
    private lateinit var premiumTier: LinearLayout
    private lateinit var ultraTier: LinearLayout
    private lateinit var basicPrice: TextView
    private lateinit var proPrice: TextView
    private lateinit var premiumPrice: TextView
    private lateinit var ultraPrice: TextView
    private var selectedTier: SubscriptionTier? = null
    private val firebaseAuthService = FirebaseAuthenticationService(context)
    private val productIdMap = mapOf(
        SubscriptionTier.BASIC to "basic_monthly_subscription",
        SubscriptionTier.PRO to "pro_monthly_subscription", 
        SubscriptionTier.PREMIUM to "premium_monthly_subscription",
        SubscriptionTier.ENTERPRISE to "ultra_monthly_subscription"
    )
    
    companion object {
        private const val TAG = "CostAwareSubscriptionDialog"
    }

    fun show() {
        dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_cost_aware_subscription, null)
        
        dialog = Dialog(context).apply {
            setContentView(dialogView)
            window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setCancelable(true)
            setCanceledOnTouchOutside(true)
        }

        initializeViews()
        setupUI()
        loadCurrentUserStatus()
        
        dialog.show()
    }
    
    private fun initializeViews() {
        btnPurchase = dialogView.findViewById(R.id.btnPurchase)
        btnClose = dialogView.findViewById(R.id.btnClose)
        currentTierText = dialogView.findViewById(R.id.currentTierText)
        currentUsageText = dialogView.findViewById(R.id.currentUsageText)
        freeTier = dialogView.findViewById(R.id.freeTier)
        basicTier = dialogView.findViewById(R.id.basicTier)
        proTier = dialogView.findViewById(R.id.proTier)
        premiumTier = dialogView.findViewById(R.id.premiumTier)
        ultraTier = dialogView.findViewById(R.id.ultraTier)
        basicPrice = dialogView.findViewById(R.id.basicPrice)
        proPrice = dialogView.findViewById(R.id.proPrice)
        premiumPrice = dialogView.findViewById(R.id.premiumPrice)
        ultraPrice = dialogView.findViewById(R.id.ultraPrice)
    }

    private fun setupUI() {
        // Update pricing from the new cost-aware system
        updatePricingDisplay()
        
        // Set up tier selection listeners
        setupTierSelectionListeners()
        
        // Update UI based on authentication status
        updateUIForAuthenticationStatus()
        
        // Set up action buttons
        btnPurchase.setOnClickListener {
            // Enhanced authentication check with retry logic
            val currentUser = firebaseAuthService.getCurrentFirebaseUid()
            Log.d(TAG, "Purchase button clicked - current user: $currentUser")
            
            if (!firebaseAuthService.isSignedIn()) {
                Log.w(TAG, "User not authenticated (uid: $currentUser), showing authentication required dialog")
                showAuthenticationRequiredDialog()
                return@setOnClickListener
            }
            
            selectedTier?.let { tier ->
                val productId = productIdMap[tier]
                if (productId != null) {
                    onPurchaseSelected(tier, productId)
                    dialog.dismiss()
                } else {
                    Log.e(TAG, "No product ID found for tier: $tier")
                }
            }
        }
        
        btnClose.setOnClickListener {
            dialog.dismiss()
            onDismiss()
        }
        
        dialog.setOnDismissListener {
            onDismiss()
        }
    }

    private fun updatePricingDisplay() {
        // Update prices from the new subscription plans
        basicPrice.text = "$${SubscriptionPlans.BASIC_PLAN.price}/month"
        proPrice.text = "$${SubscriptionPlans.PRO_PLAN.price}/month"
        premiumPrice.text = "$${SubscriptionPlans.PREMIUM_PLAN.price}/month"
        ultraPrice.text = "$${SubscriptionPlans.ENTERPRISE_PLAN.price}/month"
    }

    private fun setupTierSelectionListeners() {
        basicTier.setOnClickListener { selectTier(SubscriptionTier.BASIC, basicTier) }
        proTier.setOnClickListener { selectTier(SubscriptionTier.PRO, proTier) }
        premiumTier.setOnClickListener { selectTier(SubscriptionTier.PREMIUM, premiumTier) }
        ultraTier.setOnClickListener { selectTier(SubscriptionTier.ENTERPRISE, ultraTier) }
    }

    private fun selectTier(tier: SubscriptionTier, selectedView: LinearLayout) {
        // Reset all tier backgrounds
        basicTier.setBackgroundResource(R.drawable.subscription_option_unselected)
        proTier.setBackgroundResource(R.drawable.subscription_option_unselected)
        premiumTier.setBackgroundResource(R.drawable.subscription_option_unselected)
        ultraTier.setBackgroundResource(R.drawable.subscription_option_unselected)
        
        // Highlight selected tier
        selectedView.setBackgroundResource(R.drawable.subscription_option_selected)
        
        selectedTier = tier
        btnPurchase.isEnabled = true
        
        // Update button text based on authentication status and selected tier
        val isSignedIn = firebaseAuthService.isSignedIn()
        val currentUser = firebaseAuthService.getCurrentFirebaseUid()
        Log.d(TAG, "Updating button text - isSignedIn: $isSignedIn, currentUser: $currentUser")
        
        if (!isSignedIn) {
            btnPurchase.text = "🔐 CREATE ACCOUNT TO SUBSCRIBE"
        } else {
            val plan = SubscriptionPlans.getPlanForTier(tier)
            btnPurchase.text = "🔥 UPGRADE TO ${plan.name.uppercase()}"
        }
        
        Log.d(TAG, "Selected tier: $tier")
    }

    private fun loadCurrentUserStatus() {
        if (context is MainActivity) {
            context.lifecycleScope.launch {
                try {
                    val userStatus = costManager.getUserCostStatus()
                    
                    // Update current status display
                    currentTierText.text = "Current: ${userStatus.tier.displayName}"
                    currentUsageText.text = "${userStatus.messageCount}/${userStatus.messageLimit} messages used"
                    
                    // Update free tier status
                    if (userStatus.tier == SubscriptionTier.FREE) {
                        freeTier.setBackgroundResource(R.drawable.subscription_option_selected)
                        updateCurrentTierDisplay(SubscriptionTier.FREE)
                    } else {
                        // User has a paid subscription
                        updateCurrentTierDisplay(userStatus.tier)
                    }
                    
                    Log.d(TAG, "Loaded user status: tier=${userStatus.tier}, usage=${userStatus.usagePercentage}%")
                } catch (e: Exception) {
                    Log.e(TAG, "Error loading user status", e)
                }
            }
        }
    }

    private fun updateCurrentTierDisplay(currentTier: SubscriptionTier) {
        // Update the current tier badge and disable/highlight appropriately
        when (currentTier) {
            SubscriptionTier.FREE -> {
                // Free tier is current - show it as selected
                freeTier.setBackgroundResource(R.drawable.subscription_option_selected)
            }
            SubscriptionTier.BASIC -> {
                basicTier.setBackgroundResource(R.drawable.subscription_option_selected)
                updateTierToCurrent(basicTier, "✅ CURRENT PLAN")
            }
            SubscriptionTier.PRO -> {
                proTier.setBackgroundResource(R.drawable.subscription_option_selected)
                updateTierToCurrent(proTier, "✅ CURRENT PLAN")
            }
            SubscriptionTier.PREMIUM -> {
                premiumTier.setBackgroundResource(R.drawable.subscription_option_selected)
                updateTierToCurrent(premiumTier, "✅ CURRENT PLAN")
            }
            SubscriptionTier.ENTERPRISE -> {
                ultraTier.setBackgroundResource(R.drawable.subscription_option_selected)
                updateTierToCurrent(ultraTier, "✅ CURRENT PLAN")
            }
        }
    }

    private fun updateTierToCurrent(tierLayout: LinearLayout, badgeText: String) {
        // Find and update the badge in the tier layout
        for (i in 0 until tierLayout.childCount) {
            val child = tierLayout.getChildAt(i)
            if (child is TextView && (child.text.toString().contains("🎯") || 
                child.text.toString().contains("🔥") || 
                child.text.toString().contains("💎") ||
                child.text.toString().contains("🚀"))) {
                child.text = badgeText
                break
            }
        }
        
        // Disable clicking on current tier
        tierLayout.isClickable = false
        tierLayout.alpha = 0.7f
    }

    /**
     * Update pricing with real Google Play billing prices
     */
    fun updateWithBillingPrices(productDetailsMap: Map<String, ProductDetails>) {
        productDetailsMap.forEach { (productId, productDetails) ->
            val price = productDetails.subscriptionOfferDetails?.firstOrNull()
                ?.pricingPhases?.pricingPhaseList?.firstOrNull()?.formattedPrice ?: "N/A"
                
            when (productId) {
                "basic_monthly_subscription" -> basicPrice.text = price
                "pro_monthly_subscription" -> proPrice.text = price
                "premium_monthly_subscription" -> premiumPrice.text = price
                "ultra_monthly_subscription" -> ultraPrice.text = price
            }
        }
        
        Log.d(TAG, "Updated pricing with Google Play billing prices")
    }

    /**
     * Show models available for a specific tier - Updated for single token pool system
     */
    private fun showModelsForTier(tier: SubscriptionTier): String {
        return when (tier) {
            SubscriptionTier.FREE -> "ALL MODELS available • 1,000 tokens/day • Expensive models cost more"
            SubscriptionTier.BASIC -> "ALL MODELS available • 2,000 tokens/day • Better value per token"
            SubscriptionTier.PREMIUM -> "ALL MODELS available • 4,000 tokens/day • Even better pricing"
            SubscriptionTier.PRO -> "ALL MODELS available • 8,000 tokens/day • Professional pricing"
            SubscriptionTier.ENTERPRISE -> "ALL MODELS available • 16,000 tokens/day • Best pricing"
        }
    }

    private fun showAuthenticationRequiredDialog() {
        val authDialog = androidx.appcompat.app.AlertDialog.Builder(context, R.style.BlueDialogTheme)
            .setTitle("Account Required")
            .setMessage("You need to create an account before purchasing a subscription. This helps us secure your subscription and sync it across devices.")
            .setPositiveButton("Create Account") { _, _ ->
                // Navigate to profile/login screen
                val intent = Intent(context, ProfileActivity::class.java)
                intent.putExtra("show_registration", true)
                context.startActivity(intent)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { _, _ ->
                // Stay in dialog
            }
            .create()
        
        authDialog.show()
    }
    
    /**
     * Check if dialog should be shown based on authentication status
     */
    fun shouldShow(): Boolean {
        return true // Always show dialog, but purchase will require auth
    }
    
    /**
     * Update UI based on authentication status
     */
    private fun updateUIForAuthenticationStatus() {
        val isSignedIn = firebaseAuthService.isSignedIn()
        val currentUser = firebaseAuthService.getCurrentFirebaseUid()
        Log.d(TAG, "updateUIForAuthenticationStatus - isSignedIn: $isSignedIn, currentUser: $currentUser")
        
        if (!isSignedIn) {
            // Update purchase button to indicate authentication required
            btnPurchase.text = "🔐 CREATE ACCOUNT TO SUBSCRIBE"
            btnPurchase.setBackgroundResource(R.drawable.gradient_blue_green_secondary)
            
            // Add authentication notice
            val authNotice = "⚠️ Account required for subscription purchase"
            // You could add this to a TextView in the layout if available
        } else {
            // User is authenticated, show normal purchase flow
            selectedTier?.let { tier ->
                val plan = SubscriptionPlans.getPlanForTier(tier)
                btnPurchase.text = "🔥 UPGRADE TO ${plan.name.uppercase()}"
                btnPurchase.setBackgroundResource(R.drawable.gradient_blue_green_primary)
            }
        }
    }

    /**
     * Refresh authentication status and update UI
     * Call this method after authentication state changes
     */
    fun refreshAuthenticationStatus() {
        Log.d(TAG, "Refreshing authentication status in dialog")
        updateUIForAuthenticationStatus()
        
        // Also refresh button text if a tier is selected
        selectedTier?.let { tier ->
            val isSignedIn = firebaseAuthService.isSignedIn()
            if (isSignedIn) {
                val plan = SubscriptionPlans.getPlanForTier(tier)
                btnPurchase.text = "🔥 UPGRADE TO ${plan.name.uppercase()}"
            } else {
                btnPurchase.text = "🔐 CREATE ACCOUNT TO SUBSCRIBE"
            }
        }
    }

    fun dismiss() {
        if (::dialog.isInitialized && dialog.isShowing) {
            dialog.dismiss()
        }
    }
}