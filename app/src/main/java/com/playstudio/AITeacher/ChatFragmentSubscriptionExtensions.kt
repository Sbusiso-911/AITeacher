package com.playstudio.aiteacher

//import android.app.AlertDialog
import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.playstudio.aiteacher.pricing.AIModel
import com.playstudio.aiteacher.pricing.SubscriptionTier
import com.playstudio.aiteacher.profile.FirebaseAuthenticationService
import com.playstudio.aiteacher.profile.ProfileActivity
import kotlinx.coroutines.launch
import android.content.Intent
import androidx.appcompat.app.AlertDialog
import android.view.ContextThemeWrapper
import com.playstudio.aiteacher.R

/**
 * Extension functions for ChatFragment to handle subscription-based AI model selection
 */

/**
 * Show AI model selection dialog with subscription-tier filtering
 */
fun Fragment.showSubscriptionAwareModelDialog(
    subscriptionUIManager: SubscriptionUIManager,
    onModelSelected: (AIModel) -> Unit
) {
    lifecycleScope.launch {
        try {
            val availableModels = subscriptionUIManager.getAvailableAIModels()
            val userTier = subscriptionUIManager.getUserSubscriptionTier()

            if (availableModels.isEmpty()) {
                showNoModelsAvailableDialog()
                return@launch
            }

            showModelSelectionDialog(availableModels, userTier, onModelSelected)

        } catch (e: Exception) {
            Log.e("ChatFragment", "Error showing model dialog", e)
        }
    }
}

/**
 * Show the actual model selection dialog
 */
private fun Fragment.showModelSelectionDialog(
    availableModels: List<AIModel>,
    userTier: SubscriptionTier,
    onModelSelected: (AIModel) -> Unit
) {
    val context = requireContext()
    val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_ai_model_selection, null)

    // Setup RecyclerView with models
    val recyclerView = dialogView.findViewById<RecyclerView>(R.id.rv_ai_models)
    val tierTextView = dialogView.findViewById<TextView>(R.id.tv_current_tier)
    val creditTextView = dialogView.findViewById<TextView>(R.id.tv_credit_balance)

    tierTextView.text = "Current Plan: ${userTier.name.replace("_", " ")}"

    val poolTier = userTier.toTokenPoolTier()
    val tokenPool = com.playstudio.aiteacher.credits.TokenPoolManager.getInstance(context)
    val remainingTokens = tokenPool.getRemainingDailyTokens("default_user", poolTier).toInt()
    val dailyAllocation = poolTier.tokenAllocation.toInt()
    creditTextView.text = "Tokens: $remainingTokens / $dailyAllocation"

    val usageTracker = com.playstudio.aiteacher.pricing.UsageTracker(context)
    val modelAdapter = SubscriptionAwareModelAdapter(availableModels, userTier, usageTracker) { selectedModel ->
        onModelSelected(selectedModel)
    }

    recyclerView.layoutManager = GridLayoutManager(context, 2)
    recyclerView.adapter = modelAdapter

    AlertDialog.Builder(ContextThemeWrapper(context, R.style.GlassDialogTheme))
        .setTitle("Select AI Model")
        .setView(dialogView)
        .setNegativeButton("Cancel", null)
        .setNeutralButton("Upgrade Plan") { _, _ ->
            showUpgradeDialog(context)
        }
        .show()
}

/**
 * Show dialog when no models are available
 */
private fun Fragment.showNoModelsAvailableDialog() {
    android.app.AlertDialog.Builder(requireContext())
        .setTitle("No AI Models Available")
        .setMessage("Please subscribe to access AI models.")
        .setPositiveButton("Subscribe") { _, _ ->
            showUpgradeDialog(requireContext())
        }
        .setNegativeButton("Cancel", null)
        .show()
}

/**
 * Show upgrade dialog
 */
private fun showUpgradeDialog(context: Context) {
    val firebaseAuthService = FirebaseAuthenticationService(context)

    // Check authentication before showing subscription
    if (!firebaseAuthService.isSignedIn()) {
        Log.w("ChatFragment", "User not authenticated, showing authentication required dialog")
        showAuthenticationRequiredDialog(context)
        return
    }

    // User is authenticated, proceed with subscription activity
    try {
        val intent = android.content.Intent(context, com.playstudio.aiteacher.profile.SubscriptionActivity::class.java)
        context.startActivity(intent)
    } catch (e: Exception) {
        Log.e("ChatFragment", "Error launching subscription activity", e)
    }
}

/**
 * Check if user can send message with current model
 */
suspend fun Fragment.canUserSendMessage(
    subscriptionUIManager: SubscriptionUIManager,
    selectedModel: AIModel?
): Boolean {
    return try {
        if (selectedModel == null) return false

        val canAccess = subscriptionUIManager.canAccessModel(selectedModel)
        if (!canAccess) {
            showModelNotAvailableDialog(selectedModel)
        }
        canAccess

    } catch (e: Exception) {
        Log.e("ChatFragment", "Error checking if user can send message", e)
        false
    }
}

/**
 * Show dialog when selected model is not available for user's tier
 */
private fun Fragment.showModelNotAvailableDialog(model: AIModel) {
    android.app.AlertDialog.Builder(requireContext())
        .setTitle("Model Not Available")
        .setMessage("${model.displayName} usage limit has been reached for your current subscription tier.")
        .setPositiveButton("Upgrade") { _, _ ->
            showUpgradeDialog(requireContext())
        }
        .setNegativeButton("Select Different Model") { _, _ ->
            // This will be handled by the calling function to show model dialog again
        }
        .show()
}

/**
 * Show authentication required dialog
 */
private fun showAuthenticationRequiredDialog(context: Context) {
    val authDialog = android.app.AlertDialog.Builder(context, R.style.BlueDialogTheme)
        .setTitle("Account Required")
        .setMessage("You need to create an account before purchasing a subscription. This helps us secure your subscription and sync it across devices.")
        .setPositiveButton("Create Account") { _, _ ->
            // Navigate to profile/login screen
            val intent = Intent(context, ProfileActivity::class.java)
            intent.putExtra("show_registration", true)
            context.startActivity(intent)
        }
        .setNegativeButton("Cancel") { _, _ ->
            // Stay in current view
        }
        .create()

    authDialog.show()
}

/**
 * Get model display text with subscription tier info
 */
fun AIModel.getDisplayTextWithTier(): String {
    // Since all models are available to all tiers, we just show the model name
    return displayName
}

/**
 * Get model description with pricing info
 */
fun AIModel.getDescriptionWithPricing(): String {
    val costPerMessage = calculateMessageCost()
    val costText = if (costPerMessage < 0.001) {
        "~$0.00"
    } else {
        "~$${String.format("%.3f", costPerMessage)}"
    }

    return buildString {
        append("$provider • ")
        append("$costText/msg")
        if (capabilities >= 8) append(" • ⭐ Advanced")
    }
}

/**
 * Update ChatFragment UI based on subscription status
 */
fun Fragment.updateChatUIForSubscription(subscriptionUIManager: SubscriptionUIManager) {
    lifecycleScope.launch {
        try {
            // Update UI through the subscription manager
            subscriptionUIManager.updateUIForSubscriptionStatus(this@updateChatUIForSubscription)

            // Update model selection button text based on available models
            val availableModels = subscriptionUIManager.getAvailableAIModels()
            val userTier = subscriptionUIManager.getUserSubscriptionTier()

            view?.findViewById<TextView>(R.id.tv_available_models)?.text =
                "${availableModels.size} models available (${userTier.name.replace("_", " ")} plan)"

        } catch (e: Exception) {
            Log.e("ChatFragment", "Error updating chat UI for subscription", e)
        }
    }
}