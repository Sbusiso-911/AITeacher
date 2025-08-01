package com.playstudio.aiteacher

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.playstudio.aiteacher.pricing.AIModel
import com.playstudio.aiteacher.pricing.SubscriptionTier

/**
 * RecyclerView adapter for AI model selection with usage-based subscription system
 */
class SubscriptionAwareModelAdapter(
    private val models: List<AIModel>,
    private val userTier: SubscriptionTier,
    private val usageTracker: com.playstudio.aiteacher.pricing.UsageTracker,
    private val onModelSelected: (AIModel) -> Unit
) : RecyclerView.Adapter<SubscriptionAwareModelAdapter.ModelViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ModelViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_ai_model_card, parent, false)
        return ModelViewHolder(view)
    }

    override fun onBindViewHolder(holder: ModelViewHolder, position: Int) {
        val model = models[position]
        holder.bind(model, userTier, usageTracker, onModelSelected)
    }

    override fun getItemCount(): Int = models.size

    class ModelViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cardView: CardView = itemView.findViewById(R.id.card_model)
        private val tvModelName: TextView = itemView.findViewById(R.id.tv_model_name)
        private val tvProvider: TextView = itemView.findViewById(R.id.tv_provider)
        private val tvTierBadge: TextView = itemView.findViewById(R.id.tv_tier_badge)
        private val tvCapabilities: TextView = itemView.findViewById(R.id.tv_capabilities)
        private val tvDailyLimit: TextView = itemView.findViewById(R.id.tv_daily_limit)
        private val tvCostPerMessage: TextView = itemView.findViewById(R.id.tv_cost_per_message)

        fun bind(model: AIModel, userTier: SubscriptionTier, usageTracker: com.playstudio.aiteacher.pricing.UsageTracker, onModelSelected: (AIModel) -> Unit) {
            // Set model information
            tvModelName.text = model.displayName
            tvProvider.text = model.provider
            
            // Set access status badge based on model availability for user's tier
            val (statusText, statusColor) = getModelAccessInfo(model, userTier)
            tvTierBadge.text = statusText
            tvTierBadge.setBackgroundColor(statusColor)
            
            // Set capabilities (star rating)
            val stars = "⭐".repeat(minOf(model.capabilities, 5))
            tvCapabilities.text = "$stars (${model.capabilities}/10)"
            
            // Set daily limit and usage info
            val usageLimit = model.getUsageLimitForTier(userTier)
            val currentUsage = usageTracker.getCurrentUsage(model.modelId)
            val remainingUsage = usageTracker.getRemainingUsage(model.modelId, userTier)
            
            tvDailyLimit.text = if (usageLimit == -1) {
                "Unlimited daily"
            } else {
                "$currentUsage/$usageLimit used today"
            }
            
            // Set cost per message using credit system
            val creditManager = com.playstudio.aiteacher.credits.CreditManager.getInstance(itemView.context)
            val costPerMessage = creditManager.calculateMessageCost(
                model.averageInputTokens,
                model.averageOutputTokens,
                model.modelId,
                userTier
            )
            tvCostPerMessage.text = if (costPerMessage < 0.001) {
                "~$0.00/msg"
            } else {
                "~$${String.format("%.3f", costPerMessage)}/msg"
            }
            
            // Check if user can use this model (has remaining usage)
            val canUse = usageTracker.canUseModel(model.modelId, userTier)
            
            // Style the card based on usage availability
            if (canUse) {
                cardView.setCardBackgroundColor(ContextCompat.getColor(itemView.context, R.color.white))
                cardView.alpha = 1.0f
                cardView.isClickable = true
                cardView.isFocusable = true
                
                cardView.setOnClickListener {
                    onModelSelected(model)
                }
            } else {
                cardView.setCardBackgroundColor(ContextCompat.getColor(itemView.context, R.color.gray_light))
                cardView.alpha = 0.6f
                cardView.isClickable = false
                cardView.isFocusable = false
                
                // Show usage limit reached message
                tvCostPerMessage.text = "Usage Limit Reached"
                tvCostPerMessage.setTextColor(ContextCompat.getColor(itemView.context, R.color.red))
            }
        }
        
        private fun getModelAccessInfo(model: AIModel, userTier: SubscriptionTier): Pair<String, Int> {
            // Check if the model has usage available for the user's tier
            val hasUsage = model.hasUsageRemainingForTier(userTier)
            val usageLimit = model.getUsageLimitForTier(userTier)
            
            return when {
                hasUsage && usageLimit > 0 -> {
                    // User can access this model with limited usage
                    when (userTier) {
                        SubscriptionTier.FREE -> Pair("FREE", Color.parseColor("#4CAF50"))
                        SubscriptionTier.BASIC -> Pair("ESSENTIAL", Color.parseColor("#2196F3"))
                        SubscriptionTier.PRO -> Pair("PRO", Color.parseColor("#9C27B0"))
                        SubscriptionTier.PREMIUM -> Pair("PREMIUM", Color.parseColor("#FF9800"))
                        SubscriptionTier.ULTRA_PREMIUM -> Pair("ULTRA", Color.parseColor("#F44336"))
                    }
                }
                hasUsage && usageLimit == -1 -> {
                    // User has unlimited access to this model
                    when (userTier) {
                        SubscriptionTier.FREE -> Pair("FREE", Color.parseColor("#4CAF50"))
                        SubscriptionTier.BASIC -> Pair("ESSENTIAL", Color.parseColor("#2196F3"))
                        SubscriptionTier.PRO -> Pair("PRO", Color.parseColor("#9C27B0"))
                        SubscriptionTier.PREMIUM -> Pair("PREMIUM", Color.parseColor("#FF9800"))
                        SubscriptionTier.ULTRA_PREMIUM -> Pair("ULTRA", Color.parseColor("#F44336"))
                    }
                }
                else -> {
                    // Model not available for this tier
                    Pair("UNAVAILABLE", Color.parseColor("#9E9E9E"))
                }
            }
        }
        
        private fun getTierLevel(tier: SubscriptionTier): Int {
            return when (tier) {
                SubscriptionTier.FREE -> 0
                SubscriptionTier.BASIC -> 1
                SubscriptionTier.PRO -> 2
                SubscriptionTier.PREMIUM -> 3
                SubscriptionTier.ULTRA_PREMIUM -> 4
            }
        }
        
    }
}