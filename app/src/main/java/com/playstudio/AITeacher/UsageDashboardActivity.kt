package com.playstudio.aiteacher

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.playstudio.aiteacher.databinding.ActivityUsageDashboardBinding
import com.playstudio.aiteacher.pricing.AIModel
import com.playstudio.aiteacher.pricing.SubscriptionTier
import com.playstudio.aiteacher.pricing.UsageTracker
import com.playstudio.aiteacher.pricing.SmartUpgradeManager
import com.playstudio.aiteacher.pricing.SmartUpgradeRecommendation
import com.playstudio.aiteacher.credits.CreditManager
import com.playstudio.aiteacher.credits.SubscriptionTiers

class UsageDashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityUsageDashboardBinding
    private lateinit var usageTracker: UsageTracker
    private lateinit var usageAdapter: UsageAdapter
    private lateinit var smartUpgradeManager: SmartUpgradeManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUsageDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupActionBar()
        setupUsageTracker()
        setupRecyclerView()
        setupUpgradeButton()
        loadUsageData()
    }

    private fun setupActionBar() {
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
            title = "Daily Usage Dashboard"
        }
    }

    private fun setupUsageTracker() {
        usageTracker = UsageTracker(this)
        smartUpgradeManager = SmartUpgradeManager(this)
    }

    private fun setupRecyclerView() {
        usageAdapter = UsageAdapter()
        binding.recyclerViewUsage.apply {
            layoutManager = LinearLayoutManager(this@UsageDashboardActivity)
            adapter = usageAdapter
        }
    }

    private fun setupUpgradeButton() {
        val userTier = getCurrentUserTier()

        // Hide upgrade button if user is at max tier
        if (smartUpgradeManager.isAtMaxTier(userTier)) {
            binding.buttonUpgrade.visibility = android.view.View.GONE
            return
        }

        // Get smart upgrade recommendation
        val recommendation = smartUpgradeManager.getUpgradeRecommendation(userTier)

        if (recommendation != null) {
            // Update button text based on recommendation
            binding.buttonUpgrade.text = smartUpgradeManager.getUpgradeButtonText(recommendation)

            // Set button color based on urgency
            val buttonColor = when (recommendation.urgency) {
                com.playstudio.aiteacher.pricing.SmartUpgradeUrgency.URGENT -> Color.parseColor("#F44336") // Red
                com.playstudio.aiteacher.pricing.SmartUpgradeUrgency.RECOMMENDED -> Color.parseColor("#FF9800") // Orange
                com.playstudio.aiteacher.pricing.SmartUpgradeUrgency.SUGGESTED -> Color.parseColor("#2196F3") // Blue
                com.playstudio.aiteacher.pricing.SmartUpgradeUrgency.OPTIONAL -> Color.parseColor("#4CAF50") // Green
            }
            binding.buttonUpgrade.setBackgroundColor(buttonColor)

            // Set click listener to show upgrade dialog
            binding.buttonUpgrade.setOnClickListener {
                showSmartUpgradeDialog(recommendation)
            }
        } else {
            // Fallback to regular upgrade button
            binding.buttonUpgrade.setOnClickListener {
                val intent = android.content.Intent(this, MainActivity::class.java)
                intent.putExtra("show_subscription_dialog", true)
                startActivity(intent)
                finish()
            }
        }
    }

    private fun showSmartUpgradeDialog(recommendation: SmartUpgradeRecommendation) {
        val title = "Upgrade to ${recommendation.recommendedTier.displayName}"
        val message = smartUpgradeManager.getUpgradeMessage(recommendation)

        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Upgrade Now") { _, _ ->
                // Navigate to subscription with recommended tier
                val intent = android.content.Intent(this, MainActivity::class.java)
                intent.putExtra("show_subscription_dialog", true)
                intent.putExtra("recommended_tier", recommendation.recommendedTier.name)
                intent.putExtra("discount_percentage", recommendation.discount)
                startActivity(intent)
                finish()
            }
            .setNegativeButton("Maybe Later", null)
            .show()
    }

    private fun loadUsageData() {
        val userTier = getCurrentUserTier()
        val usageSummary = usageTracker.getUsageSummary(userTier)

        // Update header info
        binding.textViewCurrentPlan.text = "Current Plan: ${userTier.displayName}"
        binding.textViewTotalModels.text = "Available Models: ${usageSummary.size}"

        // Calculate totals
        val totalUsed = usageSummary.values.sumOf { it.currentUsage }
        val totalRemaining = usageSummary.values.sumOf { it.remainingUsage.coerceAtMost(1000) } // Cap at 1000 for unlimited

        binding.textViewTotalUsed.text = "Total Used Today: $totalUsed"
        binding.textViewTotalRemaining.text = "Total Remaining: ${if (totalRemaining > 1000) "∞" else totalRemaining.toString()}"

        // Show credit balance using the new credit system
        val creditManager = com.playstudio.aiteacher.credits.CreditManager.getInstance(this)
        val tierConfig = com.playstudio.aiteacher.credits.SubscriptionTiers.getConfig(userTier)
        val remainingCredits = creditManager.getRemainingCredits("default_user", userTier)
        binding.textViewCreditBalance.text = "Credits: ${String.format("%.2f", remainingCredits)} / ${tierConfig.dailyCredits}"

        // Update adapter
        usageAdapter.updateUsageData(usageSummary.values.toList())
    }

    private fun getCurrentUserTier(): SubscriptionTier {
        val subscriptionPrefs = getSharedPreferences("subscription_prefs", Context.MODE_PRIVATE)
        val isActive = subscriptionPrefs.getBoolean("subscription_active", false)
        val expiryTime = subscriptionPrefs.getLong("expiration_time", 0)
        val tierName = subscriptionPrefs.getString("subscription_tier", "FREE")

        val currentTime = System.currentTimeMillis()
        val isExpired = expiryTime <= currentTime

        return if (isActive && !isExpired) {
            try {
                SubscriptionTier.valueOf(tierName ?: "FREE")
            } catch (e: IllegalArgumentException) {
                SubscriptionTier.FREE
            }
        } else {
            SubscriptionTier.FREE
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    class UsageAdapter : RecyclerView.Adapter<UsageAdapter.UsageViewHolder>() {

        private var usageData: List<UsageTracker.UsageInfo> = emptyList()

        fun updateUsageData(newData: List<UsageTracker.UsageInfo>) {
            usageData = newData
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UsageViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_usage_dashboard, parent, false)
            return UsageViewHolder(view)
        }

        override fun onBindViewHolder(holder: UsageViewHolder, position: Int) {
            holder.bind(usageData[position])
        }

        override fun getItemCount(): Int = usageData.size

        class UsageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val textModelName: TextView = itemView.findViewById(R.id.text_model_name)
            private val textUsageCount: TextView = itemView.findViewById(R.id.text_usage_count)
            private val textUsageLimit: TextView = itemView.findViewById(R.id.text_usage_limit)
            private val textRemainingCount: TextView = itemView.findViewById(R.id.text_remaining_count)
            private val progressBar: View = itemView.findViewById(R.id.progress_bar)
            private val progressBarFill: View = itemView.findViewById(R.id.progress_bar_fill)

            fun bind(usageInfo: UsageTracker.UsageInfo) {
                textModelName.text = usageInfo.modelName
                textUsageCount.text = "${usageInfo.currentUsage}"

                if (usageInfo.usageLimit == -1) {
                    textUsageLimit.text = "∞"
                    textRemainingCount.text = "∞"
                    textRemainingCount.setTextColor(Color.parseColor("#4CAF50"))
                } else {
                    textUsageLimit.text = "${usageInfo.usageLimit}"
                    textRemainingCount.text = "${usageInfo.remainingUsage}"

                    // Color coding for remaining usage
                    when {
                        usageInfo.remainingUsage <= 0 -> {
                            textRemainingCount.setTextColor(Color.parseColor("#F44336")) // Red
                        }
                        usageInfo.remainingUsage <= 3 -> {
                            textRemainingCount.setTextColor(Color.parseColor("#FF9800")) // Orange
                        }
                        else -> {
                            textRemainingCount.setTextColor(Color.parseColor("#4CAF50")) // Green
                        }
                    }
                }

                // Update progress bar
                if (usageInfo.usageLimit > 0) {
                    val progress = (usageInfo.currentUsage.toFloat() / usageInfo.usageLimit.toFloat()).coerceIn(0f, 1f)
                    val progressBarWidth = (progressBar.layoutParams.width * progress).toInt()

                    val layoutParams = progressBarFill.layoutParams
                    layoutParams.width = progressBarWidth
                    progressBarFill.layoutParams = layoutParams

                    // Color progress bar based on usage
                    val progressColor = when {
                        progress >= 0.9f -> Color.parseColor("#F44336") // Red
                        progress >= 0.7f -> Color.parseColor("#FF9800") // Orange
                        else -> Color.parseColor("#4CAF50") // Green
                    }
                    progressBarFill.setBackgroundColor(progressColor)
                } else {
                    // Unlimited usage
                    progressBarFill.layoutParams.width = 0
                    progressBarFill.setBackgroundColor(Color.parseColor("#4CAF50"))
                }
            }
        }
    }
}