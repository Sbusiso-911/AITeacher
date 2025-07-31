package com.playstudio.aiteacher.profile

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.playstudio.aiteacher.R
import com.playstudio.aiteacher.SubscriptionUIManager
import com.playstudio.aiteacher.databinding.ActivitySubscriptionBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class SubscriptionActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivitySubscriptionBinding
    private lateinit var firebaseAuthService: FirebaseAuthenticationService
    private lateinit var firestoreSubscriptionManager: FirestoreSubscriptionManager
    private lateinit var subscriptionUIManager: SubscriptionUIManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySubscriptionBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Initialize services
        firebaseAuthService = FirebaseAuthenticationService(this)
        firestoreSubscriptionManager = FirestoreSubscriptionManager(this)
        subscriptionUIManager = SubscriptionUIManager(this)
        
        // Check authentication
        if (!firebaseAuthService.isSignedIn()) {
            Log.w("SubscriptionActivity", "User not authenticated, redirecting to login")
            redirectToLogin()
            return
        }
        
        setupActionBar()
        setupUI()
        loadSubscriptionData()
    }
    
    private fun redirectToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
    
    private fun setupActionBar() {
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = "Subscription Management"
        }
    }
    
    private fun setupUI() {
        binding.apply {
            // Upgrade/manage subscription buttons
            upgradeButton.setOnClickListener {
                // Navigate to subscription purchase flow
                // This should redirect to your main subscription selection screen
                Toast.makeText(this@SubscriptionActivity, "Navigate to subscription plans", Toast.LENGTH_SHORT).show()
            }
            
            cancelButton.setOnClickListener {
                showCancelSubscriptionDialog()
            }
            
            renewButton.setOnClickListener {
                renewSubscription()
            }
            
            managePaymentButton.setOnClickListener {
                // Navigate to payment management
                Toast.makeText(this@SubscriptionActivity, "Payment management coming soon", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun loadSubscriptionData() {
        lifecycleScope.launch {
            try {
                binding.progressBar.visibility = android.view.View.VISIBLE
                
                val subscriptionStatus = firestoreSubscriptionManager.getSubscriptionStatus()
                updateSubscriptionUI(subscriptionStatus)
                
            } catch (e: Exception) {
                Log.e("SubscriptionActivity", "Error loading subscription data", e)
                Toast.makeText(this@SubscriptionActivity, "Error loading subscription: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                binding.progressBar.visibility = android.view.View.GONE
            }
        }
    }
    
    private fun updateSubscriptionUI(status: FirestoreSubscriptionManager.SubscriptionStatus) {
        binding.apply {
            if (status.isActive && !status.isExpired) {
                // Active subscription
                val planName = when (status.planType) {
                    "basic" -> "Essential Plan"
                    "pro" -> "Professional Plan"
                    "premium" -> "Premium Plan"
                    else -> "Unknown Plan"
                }
                
                currentPlanText.text = planName
                subscriptionStatusText.text = "Active"
                subscriptionStatusText.setTextColor(getColor(R.color.glass_accent))
                
                daysRemainingText.text = "${status.daysRemaining} days remaining"
                
                // Show subscription features
                status.subscription?.let { subscription ->
                    val features = subscription.features.joinToString("\n• ", "• ")
                    featuresText.text = features
                    
                    // Billing info
                    val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                    val nextBillingDate = Date(subscription.endDate)
                    nextBillingText.text = "Next billing: ${dateFormat.format(nextBillingDate)}"
                    
                    val billingCycle = subscription.billingCycle.capitalize()
                    billingCycleText.text = "Billing: $billingCycle"
                    
                    priceText.text = "$${subscription.pricePaid}/${subscription.billingCycle}"
                }
                
                // Show management buttons
                upgradeButton.text = "Upgrade Plan"
                cancelButton.visibility = android.view.View.VISIBLE
                renewButton.visibility = android.view.View.GONE
                
            } else if (status.isExpired) {
                // Expired subscription
                currentPlanText.text = "Expired Plan"
                subscriptionStatusText.text = "Expired"
                subscriptionStatusText.setTextColor(getColor(R.color.glass_text_secondary))
                
                daysRemainingText.text = "Subscription expired"
                featuresText.text = "• Basic AI models\n• Limited messages\n• Standard support"
                
                nextBillingText.text = "No active billing"
                billingCycleText.text = ""
                priceText.text = "Free"
                
                // Show renewal options
                upgradeButton.text = "Renew Subscription"
                cancelButton.visibility = android.view.View.GONE
                renewButton.visibility = android.view.View.VISIBLE
                
            } else {
                // Free plan
                currentPlanText.text = "Free Plan"
                subscriptionStatusText.text = "Free"
                subscriptionStatusText.setTextColor(getColor(R.color.glass_text_secondary))
                
                daysRemainingText.text = "No expiration"
                featuresText.text = "• Basic AI models\n• 50 messages/month\n• Community support"
                
                nextBillingText.text = "No billing"
                billingCycleText.text = ""
                priceText.text = "Free"
                
                // Show upgrade options
                upgradeButton.text = "Upgrade to Premium"
                cancelButton.visibility = android.view.View.GONE
                renewButton.visibility = android.view.View.GONE
            }
        }
    }
    
    private fun showCancelSubscriptionDialog() {
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this, R.style.BlueDialogTheme)
            .setTitle("Cancel Subscription")
            .setMessage("Are you sure you want to cancel your subscription? You'll lose access to premium features at the end of your current billing period.")
            .setPositiveButton("Cancel Subscription") { _, _ ->
                cancelSubscription()
            }
            .setNegativeButton("Keep Subscription", null)
            .create()
        
        dialog.show()
    }
    
    private fun cancelSubscription() {
        lifecycleScope.launch {
            try {
                val success = firestoreSubscriptionManager.cancelSubscription()
                if (success) {
                    Toast.makeText(this@SubscriptionActivity, "Subscription cancelled successfully", Toast.LENGTH_SHORT).show()
                    loadSubscriptionData() // Refresh UI
                } else {
                    Toast.makeText(this@SubscriptionActivity, "Failed to cancel subscription", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("SubscriptionActivity", "Error cancelling subscription", e)
                Toast.makeText(this@SubscriptionActivity, "Error cancelling subscription: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun renewSubscription() {
        lifecycleScope.launch {
            try {
                val success = firestoreSubscriptionManager.renewSubscription()
                if (success) {
                    Toast.makeText(this@SubscriptionActivity, "Subscription renewed successfully", Toast.LENGTH_SHORT).show()
                    loadSubscriptionData() // Refresh UI
                } else {
                    Toast.makeText(this@SubscriptionActivity, "Failed to renew subscription", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("SubscriptionActivity", "Error renewing subscription", e)
                Toast.makeText(this@SubscriptionActivity, "Error renewing subscription: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
    
    override fun onResume() {
        super.onResume()
        // Refresh subscription data when returning to this activity
        if (firebaseAuthService.isSignedIn()) {
            loadSubscriptionData()
        }
    }
}