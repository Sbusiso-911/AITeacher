package com.playstudio.aiteacher

import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.playstudio.aiteacher.credits.TokenPoolManager
import com.playstudio.aiteacher.pricing.SubscriptionTier
import kotlinx.coroutines.launch

class TokenPoolDashboardActivity : AppCompatActivity() {

    private lateinit var tokenPool: TokenPoolManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_token_pool_dashboard)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Token Pool"

        tokenPool = TokenPoolManager.getInstance(this)

        findViewById<RecyclerView>(R.id.rv_top_consumers).apply {
            layoutManager = LinearLayoutManager(this@TokenPoolDashboardActivity)
            adapter = TopConsumerAdapter()
        }

        render()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    private fun getCurrentUserTier(): SubscriptionTier {
        val subscriptionPrefs = getSharedPreferences("subscription_prefs", MODE_PRIVATE)
        val isActive = subscriptionPrefs.getBoolean("subscription_active", false)
        val expiryTime = subscriptionPrefs.getLong("expiration_time", 0)
        val tierName = subscriptionPrefs.getString("subscription_tier", "FREE")
        val currentTime = System.currentTimeMillis()
        val isExpired = expiryTime <= currentTime
        return if (isActive && !isExpired) {
            try { SubscriptionTier.valueOf(tierName ?: "FREE") } catch (_: Exception) { SubscriptionTier.FREE }
        } else SubscriptionTier.FREE
    }

    private fun render() {
        val planView = findViewById<TextView>(R.id.tv_current_plan)
        val dailyView = findViewById<TextView>(R.id.tv_daily_tokens)
        val progress = findViewById<ProgressBar>(R.id.progress_daily_usage)
        val percentView = findViewById<TextView>(R.id.tv_usage_percent)
        val globalPoolView = findViewById<TextView>(R.id.tv_global_pool)
        val consumersView = findViewById<RecyclerView>(R.id.rv_top_consumers)

        val tier = getCurrentUserTier()
        val poolTier = tier.toTokenPoolTier()
        val remainingDaily = tokenPool.getRemainingDailyTokens("default_user", poolTier)
        val allocation = poolTier.tokenAllocation
        val usedDaily = (allocation - remainingDaily).coerceAtLeast(0.0)
        val usagePct = if (allocation > 0) ((usedDaily / allocation) * 100).toInt() else 0

        planView.text = "Current Plan: ${tier.displayName}"
        dailyView.text = "Tokens: ${remainingDaily.toInt()} / ${allocation.toInt()}"
        progress.progress = usagePct
        percentView.text = "$usagePct% used today"

        val total = tokenPool.getTotalTokens().toInt()
        val used = tokenPool.getUsedTokens().toInt()
        val remaining = tokenPool.getRemainingTokens().toInt()
        val globalPct = if (total > 0) (used * 100 / total) else 0
        globalPoolView.text = "Global Pool — Total: $total • Used: $used • Remaining: $remaining ($globalPct%)"

        lifecycleScope.launch {
            val top = tokenPool.getTopConsumers(5)
            (consumersView.adapter as? TopConsumerAdapter)?.submit(top)
        }
    }

    private class TopConsumerAdapter : RecyclerView.Adapter<TopConsumerVH>() {
        private val items = mutableListOf<Pair<String, Double>>()
        fun submit(newItems: List<Pair<String, Double>>) {
            items.clear(); items.addAll(newItems); notifyDataSetChanged()
        }
        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): TopConsumerVH {
            val view = layoutInflater(parent).inflate(android.R.layout.simple_list_item_2, parent, false)
            return TopConsumerVH(view)
        }
        override fun onBindViewHolder(holder: TopConsumerVH, position: Int) { holder.bind(items[position]) }
        override fun getItemCount(): Int = items.size
        private fun layoutInflater(parent: android.view.ViewGroup) = android.view.LayoutInflater.from(parent.context)
    }

    private class TopConsumerVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val title = itemView.findViewById<TextView>(android.R.id.text1)
        private val subtitle = itemView.findViewById<TextView>(android.R.id.text2)
        fun bind(row: Pair<String, Double>) {
            title.text = row.first
            subtitle.text = "${row.second.toInt()} tokens"
        }
    }
}
