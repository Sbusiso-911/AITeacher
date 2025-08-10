package com.playstudio.aiteacher

import android.app.AlertDialog
import android.content.Intent
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.playstudio.aiteacher.credits.*
import com.playstudio.aiteacher.pricing.ComplexityLevel
import com.playstudio.aiteacher.pricing.SubscriptionTier
import com.playstudio.aiteacher.profile.SubscriptionActivity
import com.playstudio.aiteacher.ContactUsActivity
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.*

/**
 * Extension functions for ChatFragment to integrate the unified credit system
 * Add these functions to your existing ChatFragment class
 */

/**
 * Check if user can send a message before processing
 * Call this before sending any message to the AI
 */
fun Fragment.checkCreditAvailabilityBeforeMessage(
    userId: String,
    modelId: String,
    tier: SubscriptionTier,
    messageText: String,
    onApproved: (cost: Double) -> Unit,
    onDenied: (reason: String, alternatives: List<ModelAlternative>) -> Unit,
    onEmergency: (cost: Double, emergencyAmount: Double) -> Unit
) {
    lifecycleScope.launch {
        val integration = UnifiedCreditIntegration.getInstance(requireContext())
        
        // Estimate complexity based on message length and content
        val complexity = determineComplexity(messageText)
        
        when (val result = integration.canSendMessage(userId, modelId, tier)) {
            is MessagePermissionResult.Allowed -> {
                // Show cost preview if usage is high
                if (result.usagePercentage > 0.7) {
                    showCostPreviewDialog(result.cost, result.remainingCredits) {
                        onApproved(result.cost)
                    }
                } else {
                    onApproved(result.cost)
                }
            }
            
            is MessagePermissionResult.AllowedWithEmergency -> {
                showEmergencyCreditsDialog(result.cost, result.emergencyAmount) { useEmergency ->
                    if (useEmergency) {
                        onEmergency(result.cost, result.emergencyAmount)
                    } else {
                        onDenied("Insufficient credits", emptyList())
                    }
                }
            }
            
            is MessagePermissionResult.Denied -> {
                onDenied(result.reason, result.suggestedAlternatives)
            }
            
            is MessagePermissionResult.EmergencyStop -> {
                showEmergencyStopDialog(result.reason)
            }
            
            is MessagePermissionResult.Error -> {
                Toast.makeText(requireContext(), "Error: ${result.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

/**
 * Process the message after AI response is received
 * Call this after receiving the AI response with actual token counts
 */
fun Fragment.processCreditDeduction(
    userId: String,
    modelId: String,
    inputTokens: Int,
    outputTokens: Int,
    tier: SubscriptionTier,
    conversationId: String? = null,
    messageId: String? = null,
    useEmergencyCredits: Boolean = false,
    onSuccess: (remainingCredits: Double) -> Unit,
    onFailure: (reason: String) -> Unit
) {
    lifecycleScope.launch {
        val integration = UnifiedCreditIntegration.getInstance(requireContext())
        
        val result = integration.processCompletedMessage(
            userId, modelId, inputTokens, outputTokens, tier, conversationId, messageId, useEmergencyCredits
        )
        
        if (result.success) {
            onSuccess(result.balanceAfter)
            
            // Update credit display in UI
            updateCreditBalanceDisplay(result.balanceAfter, tier)
            
            // Show low credit warning if needed
            val usagePercentage = (SubscriptionTiers.getConfig(tier).dailyCredits - result.balanceAfter) / 
                                SubscriptionTiers.getConfig(tier).dailyCredits
            if (usagePercentage > 0.8) {
                showLowCreditWarning(result.balanceAfter, tier)
            }
        } else {
            onFailure(result.reason)
        }
    }
}

/**
 * Show real-time cost preview in the chat interface
 */
fun Fragment.setupCostPreviewForChat(
    userId: String,
    tier: SubscriptionTier,
    creditBalanceTextView: android.widget.TextView,
    modelSelectionButton: android.widget.Button? = null
) {
    lifecycleScope.launch {
        val integration = UnifiedCreditIntegration.getInstance(requireContext())
        val preview = integration.getCostPreviewForChat(userId, tier)
        
        // Update credit balance display
        val currencyFormat = NumberFormat.getCurrencyInstance(Locale.US)
        val balanceText = "${currencyFormat.format(preview.remainingCredits)} / ${currencyFormat.format(preview.dailyAllowance)}"
        creditBalanceTextView.text = balanceText
        
        // Color code based on usage percentage
        val color = when {
            preview.usagePercentage > 0.9 -> android.graphics.Color.RED
            preview.usagePercentage > 0.7 -> android.graphics.Color.parseColor("#FF8C00") // Orange
            else -> android.graphics.Color.parseColor("#4CAF50") // Green
        }
        creditBalanceTextView.setTextColor(color)
        
        // Show recommendations if low on credits
        if (preview.lowCreditWarning && preview.modelRecommendations.isNotEmpty()) {
            showModelRecommendations(preview.modelRecommendations)
        }
        
        // Update model selection button if provided
        modelSelectionButton?.setOnClickListener {
            showModelSelectionDialog(preview.modelRecommendations) { selectedModelId ->
                // Handle model selection
                onModelSelected(selectedModelId)
            }
        }
    }
}

/**
 * Show credit dashboard button in chat interface
 */
fun Fragment.addCreditDashboardButton(parentView: android.view.ViewGroup) {
    val button = android.widget.Button(requireContext()).apply {
        text = "💰 Credits"
        setOnClickListener {
            val intent = Intent(requireContext(), UnifiedCreditDashboardActivity::class.java)
            startActivity(intent)
        }
    }
    parentView.addView(button)
}

// Private helper functions

private fun determineComplexity(messageText: String): ComplexityLevel {
    return when {
        messageText.length < 50 -> ComplexityLevel.LOW
        messageText.contains(Regex("code|algorithm|complex|analysis|explain", RegexOption.IGNORE_CASE)) -> ComplexityLevel.HIGH
        else -> ComplexityLevel.MEDIUM
    }
}

private fun Fragment.showCostPreviewDialog(cost: Double, remainingCredits: Double, onProceed: () -> Unit) {
    val currencyFormat = NumberFormat.getCurrencyInstance(Locale.US)
    val message = "This message will cost ${currencyFormat.format(cost)}\n" +
                 "Remaining credits: ${currencyFormat.format(remainingCredits)}"
    
    AlertDialog.Builder(requireContext())
        .setTitle("💰 Cost Preview")
        .setMessage(message)
        .setPositiveButton("Send Message") { _, _ -> onProceed() }
        .setNegativeButton("Cancel", null)
        .show()
}

private fun Fragment.showEmergencyCreditsDialog(cost: Double, emergencyAmount: Double, onDecision: (Boolean) -> Unit) {
    val currencyFormat = NumberFormat.getCurrencyInstance(Locale.US)
    val message = "You're ${currencyFormat.format(emergencyAmount)} short for this message.\n" +
                 "Use emergency credits? (Limited to \$0.50/day)"
    
    AlertDialog.Builder(requireContext())
        .setTitle("⚠️ Emergency Credits")
        .setMessage(message)
        .setPositiveButton("Use Emergency Credits") { _, _ -> onDecision(true) }
        .setNegativeButton("Cancel") { _, _ -> onDecision(false) }
        .show()
}

private fun Fragment.showEmergencyStopDialog(reason: String) {
    AlertDialog.Builder(requireContext())
        .setTitle("🚨 Emergency Stop")
        .setMessage("Service temporarily unavailable: $reason\nPlease contact support.")
        .setPositiveButton("Contact Support") { _, _ ->
            val intent = Intent(requireContext(), ContactUsActivity::class.java)
            startActivity(intent)
        }
        .setNeutralButton("OK", null)
        .show()
}

private fun Fragment.showLowCreditWarning(remainingCredits: Double, tier: SubscriptionTier) {
    val currencyFormat = NumberFormat.getCurrencyInstance(Locale.US)
    val message = "⚠️ Low credits: ${currencyFormat.format(remainingCredits)} remaining\n" +
                 "Consider upgrading your plan or switching to cheaper models."
    
    AlertDialog.Builder(requireContext())
        .setTitle("Low Credit Warning")
        .setMessage(message)
        .setPositiveButton("Upgrade Plan") { _, _ ->
            val intent = Intent(requireContext(), SubscriptionActivity::class.java)
            startActivity(intent)
        }
        .setNegativeButton("View Dashboard") { _, _ ->
            val intent = Intent(requireContext(), UnifiedCreditDashboardActivity::class.java)
            startActivity(intent)
        }
        .setNeutralButton("Continue", null)
        .show()
}

private fun Fragment.showModelRecommendations(recommendations: List<SmartModelRecommendation.ModelRecommendation>) {
    val items = recommendations.map { "${it.model.displayName} (${it.messagesRemaining} msgs)" }.toTypedArray()
    
    AlertDialog.Builder(requireContext())
        .setTitle("💡 Recommended Models")
        .setItems(items) { _, which ->
            val selected = recommendations[which]
            onModelSelected(selected.model.modelId)
        }
        .setNegativeButton("Cancel", null)
        .show()
}

private fun Fragment.showModelSelectionDialog(
    recommendations: List<SmartModelRecommendation.ModelRecommendation>,
    onSelection: (String) -> Unit
) {
    val items = recommendations.map { recommendation ->
        val reason = when (recommendation.reason) {
            SmartModelRecommendation.RecommendationReason.BEST_VALUE -> "💡 Best Value"
            SmartModelRecommendation.RecommendationReason.MOST_AFFORDABLE -> "💰 Cheapest"
            SmartModelRecommendation.RecommendationReason.HIGHEST_QUALITY -> "⭐ Premium"
            else -> ""
        }
        "${recommendation.model.displayName} $reason (${recommendation.messagesRemaining} msgs)"
    }.toTypedArray()
    
    AlertDialog.Builder(requireContext())
        .setTitle("🤖 Select AI Model")
        .setItems(items) { _, which ->
            onSelection(recommendations[which].model.modelId)
        }
        .setNegativeButton("Cancel", null)
        .show()
}

private fun Fragment.updateCreditBalanceDisplay(remainingCredits: Double, tier: SubscriptionTier) {
    // Update your credit display views here
    val creditTextView = view?.findViewById<android.widget.TextView>(R.id.text_credit_balance)
    if (creditTextView != null) {
        val currencyFormat = NumberFormat.getCurrencyInstance(Locale.US)
        creditTextView.text = currencyFormat.format(remainingCredits)
    }
}

private fun Fragment.onModelSelected(modelId: String) {
    // Handle model selection in your chat interface
    // This should update your current model selection and refresh the UI
    Toast.makeText(requireContext(), "Model selected: $modelId", Toast.LENGTH_SHORT).show()
}