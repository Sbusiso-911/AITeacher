package com.playstudio.aiteacher.credits

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.playstudio.aiteacher.R
import java.text.NumberFormat
import java.util.*

/**
 * Data classes for adapter items
 */
data class ModelCostItem(
    val modelName: String,
    val modelId: String,
    val estimatedCost: Double,
    val messagesRemaining: Int,
    val reason: SmartModelRecommendation.RecommendationReason,
    val capabilities: Int
)

/**
 * Adapter for displaying model costs and availability
 */
class ModelCostAdapter(
    private val onModelClick: (String) -> Unit
) : RecyclerView.Adapter<ModelCostAdapter.ModelCostViewHolder>() {

    private var items = listOf<ModelCostItem>()
    private val currencyFormat = NumberFormat.getCurrencyInstance(Locale.US)

    fun updateData(newItems: List<ModelCostItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ModelCostViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_model_cost_preview, parent, false)
        return ModelCostViewHolder(view)
    }

    override fun onBindViewHolder(holder: ModelCostViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class ModelCostViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textModelName: TextView = itemView.findViewById(R.id.text_model_name)
        private val textCostPerMessage: TextView = itemView.findViewById(R.id.text_cost_per_message)
        private val textMessagesRemaining: TextView = itemView.findViewById(R.id.text_messages_remaining)
        private val textRecommendationReason: TextView = itemView.findViewById(R.id.text_recommendation_reason)

        fun bind(item: ModelCostItem) {
            textModelName.text = item.modelName
            textCostPerMessage.text = "${currencyFormat.format(item.estimatedCost)}/msg"
            
            when {
                item.messagesRemaining == 0 -> {
                    textMessagesRemaining.text = "❌ Insufficient credits"
                    textMessagesRemaining.setTextColor(itemView.context.getColor(android.R.color.holo_red_dark))
                    itemView.alpha = 0.5f
                }
                item.messagesRemaining >= 50 -> {
                    textMessagesRemaining.text = "✅ 50+ messages"
                    textMessagesRemaining.setTextColor(itemView.context.getColor(android.R.color.holo_green_dark))
                    itemView.alpha = 1.0f
                }
                else -> {
                    textMessagesRemaining.text = "⚡ ${item.messagesRemaining} messages"
                    textMessagesRemaining.setTextColor(itemView.context.getColor(android.R.color.holo_orange_dark))
                    itemView.alpha = 1.0f
                }
            }
            
            textRecommendationReason.text = when (item.reason) {
                SmartModelRecommendation.RecommendationReason.BEST_VALUE -> "💡 Best Value"
                SmartModelRecommendation.RecommendationReason.MOST_AFFORDABLE -> "💰 Most Affordable"
                SmartModelRecommendation.RecommendationReason.HIGHEST_QUALITY -> "⭐ Highest Quality"
                SmartModelRecommendation.RecommendationReason.EMERGENCY_ONLY -> "⚠️ Premium"
                SmartModelRecommendation.RecommendationReason.INSUFFICIENT_CREDITS -> "❌ No Credits"
            }
            
            itemView.setOnClickListener {
                if (item.messagesRemaining > 0) {
                    onModelClick(item.modelId)
                }
            }
        }
    }
}

/**
 * Adapter for displaying usage breakdown by model
 */
class UsageBreakdownAdapter : RecyclerView.Adapter<UsageBreakdownAdapter.UsageViewHolder>() {

    private var items = listOf<ModelUsageSummary>()
    private val currencyFormat = NumberFormat.getCurrencyInstance(Locale.US)

    fun updateData(newItems: List<ModelUsageSummary>) {
        items = newItems.sortedByDescending { it.totalCost }
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UsageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_usage_breakdown, parent, false)
        return UsageViewHolder(view)
    }

    override fun onBindViewHolder(holder: UsageViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class UsageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textModelName: TextView = itemView.findViewById(R.id.text_model_name)
        private val textMessageCount: TextView = itemView.findViewById(R.id.text_message_count)
        private val textTotalCost: TextView = itemView.findViewById(R.id.text_total_cost)
        private val textAvgCost: TextView = itemView.findViewById(R.id.text_avg_cost)

        fun bind(item: ModelUsageSummary) {
            textModelName.text = item.modelName
            textMessageCount.text = "${item.messageCount} messages"
            textTotalCost.text = currencyFormat.format(item.totalCost)
            
            val avgCost = if (item.messageCount > 0) item.totalCost / item.messageCount else 0.0
            textAvgCost.text = "${currencyFormat.format(avgCost)}/msg"
        }
    }
}

/**
 * Adapter for displaying smart recommendations
 */
class RecommendationsAdapter(
    private val onRecommendationClick: (SmartModelRecommendation.ModelRecommendation) -> Unit
) : RecyclerView.Adapter<RecommendationsAdapter.RecommendationViewHolder>() {

    private var items = listOf<SmartModelRecommendation.ModelRecommendation>()

    fun updateData(newItems: List<SmartModelRecommendation.ModelRecommendation>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecommendationViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_recommendation, parent, false)
        return RecommendationViewHolder(view)
    }

    override fun onBindViewHolder(holder: RecommendationViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class RecommendationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textRecommendation: TextView = itemView.findViewById(R.id.text_recommendation)
        private val textReason: TextView = itemView.findViewById(R.id.text_reason)

        fun bind(item: SmartModelRecommendation.ModelRecommendation) {
            val icon = when (item.reason) {
                SmartModelRecommendation.RecommendationReason.BEST_VALUE -> "💡"
                SmartModelRecommendation.RecommendationReason.MOST_AFFORDABLE -> "💰"
                SmartModelRecommendation.RecommendationReason.HIGHEST_QUALITY -> "⭐"
                SmartModelRecommendation.RecommendationReason.EMERGENCY_ONLY -> "⚠️"
                else -> "ℹ️"
            }
            
            textRecommendation.text = "$icon Try ${item.model.displayName}"
            
            textReason.text = when (item.reason) {
                SmartModelRecommendation.RecommendationReason.BEST_VALUE -> 
                    "Great balance of quality and cost - ${item.messagesRemaining} messages available"
                SmartModelRecommendation.RecommendationReason.MOST_AFFORDABLE -> 
                    "Most cost-effective option - ${item.messagesRemaining} messages available"
                SmartModelRecommendation.RecommendationReason.HIGHEST_QUALITY -> 
                    "Premium model for complex tasks - ${item.messagesRemaining} messages available"
                SmartModelRecommendation.RecommendationReason.EMERGENCY_ONLY -> 
                    "Use sparingly for critical tasks - ${item.messagesRemaining} messages available"
                else -> "Model recommendation"
            }
            
            itemView.setOnClickListener {
                onRecommendationClick(item)
            }
        }
    }
}