package com.playstudio.aiteacher.profile

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.playstudio.aiteacher.R
import java.text.SimpleDateFormat
import java.util.*

class ChatHistoryAdapter(
    private val onItemClick: (ChatSessionEntity) -> Unit,
    private val onItemLongClick: (ChatSessionEntity) -> Unit,
    private val onFavoriteClick: (ChatSessionEntity) -> Unit
) : RecyclerView.Adapter<ChatHistoryAdapter.ChatHistoryViewHolder>() {
    
    private var chatSessions = mutableListOf<ChatSessionEntity>()
    private var selectedSessions = mutableSetOf<Long>()
    private var isSelectionMode = false
    
    fun updateSessions(newSessions: List<ChatSessionEntity>) {
        chatSessions.clear()
        chatSessions.addAll(newSessions)
        notifyDataSetChanged()
    }
    
    fun setSelectionMode(selectionMode: Boolean) {
        isSelectionMode = selectionMode
        notifyDataSetChanged()
    }
    
    fun setSelectedSessions(selected: Set<Long>) {
        selectedSessions.clear()
        selectedSessions.addAll(selected)
        notifyDataSetChanged()
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatHistoryViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_chat_history, parent, false)
        return ChatHistoryViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: ChatHistoryViewHolder, position: Int) {
        val session = chatSessions[position]
        holder.bind(session, isSelectionMode, selectedSessions.contains(session.sessionId))
    }
    
    override fun getItemCount(): Int = chatSessions.size
    
    inner class ChatHistoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val titleText: TextView = itemView.findViewById(R.id.titleText)
        private val previewText: TextView = itemView.findViewById(R.id.previewText)
        private val timestampText: TextView = itemView.findViewById(R.id.timestampText)
        private val modelBadge: TextView = itemView.findViewById(R.id.modelBadge)
        private val categoryBadge: TextView = itemView.findViewById(R.id.categoryBadge)
        private val messageCountText: TextView = itemView.findViewById(R.id.messageCountText)
        private val favoriteIcon: ImageView = itemView.findViewById(R.id.favoriteIcon)
        private val selectionIndicator: View = itemView.findViewById(R.id.selectionIndicator)
        
        fun bind(session: ChatSessionEntity, selectionMode: Boolean, isSelected: Boolean) {
            titleText.text = session.title
            previewText.text = session.lastMessagePreview ?: "No messages"
            
            // Format timestamp
            val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            timestampText.text = dateFormat.format(session.updatedAt)
            
            // Model badge
            modelBadge.text = session.aiModelUsed
            
            // Category badge
            categoryBadge.text = session.category.replaceFirstChar { it.uppercase() }
            
            // Message count
            messageCountText.text = "${session.messageCount} messages"
            
            // Favorite icon
            favoriteIcon.setImageResource(
                if (session.isFavorite) R.drawable.ic_star_filled else R.drawable.ic_star_outline
            )
            favoriteIcon.setOnClickListener { onFavoriteClick(session) }
            
            // Selection indicator
            selectionIndicator.visibility = if (selectionMode) View.VISIBLE else View.GONE
            selectionIndicator.isSelected = isSelected
            
            // Click listeners
            itemView.setOnClickListener { onItemClick(session) }
            itemView.setOnLongClickListener { 
                onItemLongClick(session)
                true
            }
            
            // Visual feedback for selection
            itemView.alpha = if (selectionMode && !isSelected) 0.6f else 1.0f
        }
    }
}