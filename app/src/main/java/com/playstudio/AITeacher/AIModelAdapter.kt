package com.playstudio.aiteacher

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.cardview.widget.CardView

data class AIModel(
    val id: String,
    val name: String,
    val provider: String,
    val icon: String,
    val backgroundColor: String,
    val textColor: String,
    val providerColor: String,
    val isNew: Boolean,
    val category: String
)

class AIModelAdapter(
    private val models: List<AIModel>,
    private val onModelSelected: (AIModel) -> Unit
) : RecyclerView.Adapter<AIModelAdapter.ModelViewHolder>() {

    private var selectedPosition = -1

    class ModelViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val modelName: TextView = view.findViewById(R.id.tv_model_name)
        val providerName: TextView = view.findViewById(R.id.tv_provider)
        val tierBadge: TextView = view.findViewById(R.id.tv_tier_badge)
        val capabilities: TextView = view.findViewById(R.id.tv_capabilities)
        val costPerMessage: TextView = view.findViewById(R.id.tv_cost_per_message)
        val cardView: CardView = view as CardView
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ModelViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_ai_model_card, parent, false)
        return ModelViewHolder(view)
    }

    override fun onBindViewHolder(holder: ModelViewHolder, position: Int) {
        val model = models[position]
        
        // Set model data
        holder.modelName.text = model.name
        holder.providerName.text = model.provider
        
        // Set tier badge
        holder.tierBadge.text = model.category.uppercase()
        
        // Set capabilities (simplified for basic model)
        holder.capabilities.text = "Basic AI Model"
        
        // Set cost per message
        holder.costPerMessage.text = "Free tier"
        
        // Set colors
        try {
            val bgColor = android.graphics.Color.parseColor(model.backgroundColor)
            val textColor = android.graphics.Color.parseColor(model.textColor)
            val providerColor = android.graphics.Color.parseColor(model.providerColor)
            
            holder.cardView.setCardBackgroundColor(bgColor)
            holder.modelName.setTextColor(textColor)
            holder.providerName.setTextColor(providerColor)
        } catch (e: Exception) {
            // Fallback to default colors if parsing fails
            holder.cardView.setCardBackgroundColor(
                ContextCompat.getColor(holder.itemView.context, R.color.white)
            )
            holder.modelName.setTextColor(
                ContextCompat.getColor(holder.itemView.context, R.color.black)
            )
            holder.providerName.setTextColor(
                ContextCompat.getColor(holder.itemView.context, R.color.dark_grey)
            )
        }
        
        // Handle selection
        holder.itemView.setOnClickListener {
            val previousPosition = selectedPosition
            selectedPosition = holder.adapterPosition
            
            if (previousPosition != -1) {
                notifyItemChanged(previousPosition)
            }
            notifyItemChanged(selectedPosition)
            
            onModelSelected(model)
        }
        
        // Update selection state
        if (position == selectedPosition) {
            // Add a subtle highlight for selected item
            holder.cardView.alpha = 0.8f
        } else {
            holder.cardView.alpha = 1.0f
        }
    }

    override fun getItemCount() = models.size

    private fun getIconResource(iconName: String, context: android.content.Context): Int {
        return when (iconName) {
            "openai-spiral" -> R.drawable.ic_ai
            "deepseek-logo" -> R.drawable.ic_ai
            "x-logo" -> R.drawable.ic_ai
            "anthropic-logo" -> R.drawable.ic_ai
            "google-gemini" -> R.drawable.ic_ai
            else -> R.drawable.ic_ai
        }
    }
}