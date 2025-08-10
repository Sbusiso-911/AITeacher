package com.playstudio.aiteacher

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.playstudio.aiteacher.credits.*
import com.playstudio.aiteacher.pricing.SubscriptionTier
import com.playstudio.aiteacher.profile.SubscriptionActivity
import com.playstudio.aiteacher.HistoryActivity
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.*

class UnifiedCreditDashboardActivity : AppCompatActivity() {

    private lateinit var creditRepository: CreditRepository
    private lateinit var smartRecommendation: SmartModelRecommendation
    private lateinit var modelCostAdapter: ModelCostAdapter
    private lateinit var usageBreakdownAdapter: UsageBreakdownAdapter
    private lateinit var recommendationsAdapter: RecommendationsAdapter
    
    private val userId = "default_user" // Replace with actual user ID from your auth system
    private var currentTier = SubscriptionTier.BASIC // Replace with actual user tier
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_unified_credit_dashboard)
        
        initializeComponents()
        setupRecyclerViews()
        setupClickListeners()
        loadDashboardData()
    }
    
    override fun onResume() {
        super.onResume()
        // Refresh data when returning to dashboard
        loadDashboardData()
    }
    
    private fun initializeComponents() {
        creditRepository = CreditRepository.getInstance(this)
        val creditManager = CreditManager.getInstance(this)
        smartRecommendation = SmartModelRecommendation(creditManager)
    }
    
    private fun setupRecyclerViews() {
        // Model Cost Preview RecyclerView
        modelCostAdapter = ModelCostAdapter { modelId: String ->
            showModelDetails(modelId)
        }
        findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.recycler_model_costs).apply {
            layoutManager = LinearLayoutManager(this@UnifiedCreditDashboardActivity)
            adapter = modelCostAdapter
        }
        
        // Usage Breakdown RecyclerView
        usageBreakdownAdapter = UsageBreakdownAdapter()
        findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.recycler_usage_breakdown).apply {
            layoutManager = LinearLayoutManager(this@UnifiedCreditDashboardActivity)
            adapter = usageBreakdownAdapter
        }
        
        // Recommendations RecyclerView
        recommendationsAdapter = RecommendationsAdapter { recommendation: SmartModelRecommendation.ModelRecommendation ->
            showRecommendationDetails(recommendation)
        }
        findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.recycler_recommendations).apply {
            layoutManager = LinearLayoutManager(this@UnifiedCreditDashboardActivity)
            adapter = recommendationsAdapter
        }
    }
    
    private fun setupClickListeners() {
        findViewById<android.widget.Button>(R.id.button_upgrade_plan).setOnClickListener {
            // Navigate to subscription upgrade
            startActivity(Intent(this, SubscriptionActivity::class.java))
        }
        
        findViewById<android.widget.Button>(R.id.button_purchase_credits).setOnClickListener {
            // Handle additional credit purchase
            showCreditPurchaseDialog()
        }
        
        findViewById<android.widget.Button>(R.id.button_view_history).setOnClickListener {
            // Navigate to detailed usage history
            startActivity(Intent(this, HistoryActivity::class.java))
        }
    }
    
    private fun loadDashboardData() {
        lifecycleScope.launch {
            try {
                // Load basic credit info
                val creditManager = CreditManager.getInstance(this@UnifiedCreditDashboardActivity)
                val remainingCredits = creditManager.getRemainingCredits(userId, currentTier)
                val usagePercentage = creditManager.getCreditUsagePercentage(userId, currentTier)
                val config = SubscriptionTiers.getConfig(currentTier)
                
                // Load usage analytics
                val analytics = creditRepository.getUsageAnalytics(userId, currentTier, 1) // Today only
                
                // Load cost preview
                val costPreview = creditRepository.getCostPreview(userId, currentTier)
                
                // Update UI
                updateCreditBalance(remainingCredits, config.dailyCredits, usagePercentage)
                updateUsageStats(analytics)
                updateModelCosts(costPreview)
                updateRecommendations(costPreview.recommendations)
                updateRolloverInfo(remainingCredits, config.dailyCredits)
                
                // Show low credit warning if needed
                if (costPreview.lowCreditWarning) {
                    showLowCreditWarning(remainingCredits)
                }
                
            } catch (e: Exception) {
                showErrorMessage("Failed to load dashboard data: ${e.message}")
            }
        }
    }
    
    private fun updateCreditBalance(remaining: Double, dailyAllowance: Double, usagePercentage: Double) {
        val currencyFormat = NumberFormat.getCurrencyInstance(Locale.US)
        
        findViewById<android.widget.TextView>(R.id.text_current_plan).text = 
            "Current Plan: ${currentTier.displayName}"
            
        findViewById<android.widget.TextView>(R.id.text_credit_balance).text = 
            currencyFormat.format(remaining)
            
        findViewById<android.widget.TextView>(R.id.text_daily_allowance).text = 
            "of ${currencyFormat.format(dailyAllowance)} daily allowance"
            
        findViewById<android.widget.ProgressBar>(R.id.progress_credit_usage).progress = 
            (usagePercentage * 100).toInt()
            
        findViewById<android.widget.TextView>(R.id.text_usage_percentage).text = 
            "${(usagePercentage * 100).toInt()}% used today"
    }
    
    private fun updateUsageStats(analytics: UsageAnalytics) {
        val currencyFormat = NumberFormat.getCurrencyInstance(Locale.US)
        
        findViewById<android.widget.TextView>(R.id.text_messages_today).text = 
            analytics.totalMessages.toString()
            
        findViewById<android.widget.TextView>(R.id.text_cost_today).text = 
            currencyFormat.format(analytics.totalCreditsSpent)
            
        findViewById<android.widget.TextView>(R.id.text_avg_cost).text = 
            currencyFormat.format(analytics.averageCostPerMessage)
            
        // Update usage breakdown
        usageBreakdownAdapter.updateData(analytics.modelUsageBreakdown)
    }
    
    private fun updateModelCosts(costPreview: CostPreview) {
        val modelCostData = costPreview.recommendations.take(8).map { recommendation ->
            ModelCostItem(
                modelName = recommendation.model.displayName,
                modelId = recommendation.model.modelId,
                estimatedCost = recommendation.estimatedCost,
                messagesRemaining = recommendation.messagesRemaining,
                reason = recommendation.reason,
                capabilities = recommendation.model.capabilities
            )
        }
        modelCostAdapter.updateData(modelCostData)
    }
    
    private fun updateRecommendations(recommendations: List<SmartModelRecommendation.ModelRecommendation>) {
        val topRecommendations = recommendations.take(3).filter { 
            it.reason != SmartModelRecommendation.RecommendationReason.INSUFFICIENT_CREDITS 
        }
        recommendationsAdapter.updateData(topRecommendations)
    }
    
    private fun updateRolloverInfo(remainingCredits: Double, dailyAllowance: Double) {
        val rolloverAmount = maxOf(0.0, remainingCredits - dailyAllowance)
        val rolloverLayout = findViewById<android.view.View>(R.id.layout_rollover_info)
        
        if (rolloverAmount > 0) {
            rolloverLayout.visibility = View.VISIBLE
            val currencyFormat = NumberFormat.getCurrencyInstance(Locale.US)
            findViewById<android.widget.TextView>(R.id.text_rollover_amount).text = 
                currencyFormat.format(rolloverAmount)
        } else {
            rolloverLayout.visibility = View.GONE
        }
    }
    
    private fun showModelDetails(modelId: String) {
        lifecycleScope.launch {
            val recommendation = smartRecommendation.getRecommendations(userId, currentTier)
                .find { it.model.modelId == modelId } ?: return@launch
                
            val explanation = smartRecommendation.generateCostExplanation(
                modelId, currentTier, creditRepository.getCostPreview(userId, currentTier).remainingCredits
            )
            
            android.app.AlertDialog.Builder(this@UnifiedCreditDashboardActivity)
                .setTitle(recommendation.model.displayName)
                .setMessage(explanation)
                .setPositiveButton("Select Model") { _, _ ->
                    // Return to chat with selected model
                    val intent = Intent().apply {
                        putExtra("selected_model", modelId)
                    }
                    setResult(RESULT_OK, intent)
                    finish()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }
    
    private fun showRecommendationDetails(recommendation: SmartModelRecommendation.ModelRecommendation) {
        val message = when (recommendation.reason) {
            SmartModelRecommendation.RecommendationReason.BEST_VALUE -> 
                "Great balance of capability and cost. Perfect for most tasks."
            SmartModelRecommendation.RecommendationReason.MOST_AFFORDABLE -> 
                "Cheapest option available. Best for simple questions."
            SmartModelRecommendation.RecommendationReason.HIGHEST_QUALITY -> 
                "Top-tier model with maximum capabilities. Use for complex tasks."
            SmartModelRecommendation.RecommendationReason.EMERGENCY_ONLY -> 
                "Very expensive model. Use sparingly for critical tasks only."
            else -> "Model information"
        }
        
        android.app.AlertDialog.Builder(this)
            .setTitle("💡 ${recommendation.model.displayName}")
            .setMessage("$message\n\nMessages remaining: ${recommendation.messagesRemaining}")
            .setPositiveButton("Use This Model") { _, _ ->
                val intent = Intent().apply {
                    putExtra("selected_model", recommendation.model.modelId)
                }
                setResult(RESULT_OK, intent)
                finish()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun showCreditPurchaseDialog() {
        // TODO: Implement credit purchase flow
        android.app.AlertDialog.Builder(this)
            .setTitle("💳 Purchase Additional Credits")
            .setMessage("Additional credit purchase feature coming soon!")
            .setPositiveButton("OK", null)
            .show()
    }
    
    private fun showLowCreditWarning(remainingCredits: Double) {
        val currencyFormat = NumberFormat.getCurrencyInstance(Locale.US)
        android.app.AlertDialog.Builder(this)
            .setTitle("⚠️ Low Credit Warning")
            .setMessage("You have ${currencyFormat.format(remainingCredits)} remaining. Consider switching to cheaper models or upgrading your plan.")
            .setPositiveButton("Upgrade Plan") { _, _ ->
                startActivity(Intent(this, SubscriptionActivity::class.java))
            }
            .setNegativeButton("Continue", null)
            .show()
    }
    
    private fun showErrorMessage(message: String) {
        android.app.AlertDialog.Builder(this)
            .setTitle("Error")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }
}