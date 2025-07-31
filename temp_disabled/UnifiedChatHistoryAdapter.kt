package com.playstudio.aiteacher.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.playstudio.aiteacher.R
import com.playstudio.aiteacher.databinding.ItemUnifiedChatHistoryBinding
import com.playstudio.aiteacher.profile.ChatSessionEntity
import java.text.SimpleDateFormat
import java.util.*

/**
 * Adapter for unified chat history with sync status and action buttons
 */
class UnifiedChatHistoryAdapter(
    private val onChatClick: (ChatSessionEntity) -> Unit,
    private val onFavoriteClick: (ChatSessionEntity) -> Unit,
    private val onShareClick: (ChatSessionEntity) -> Unit,
    private val onExportClick: (ChatSessionEntity) -> Unit,
    private val onDeleteClick: (ChatSessionEntity) -> Unit
) : RecyclerView.Adapter<UnifiedChatHistoryAdapter.ChatHistoryViewHolder>() {
    
    private var chatSessions = listOf<ChatSessionEntity>()
    private val dateFormatter = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    private val timeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())
    
    fun updateChatSessions(newSessions: List<ChatSessionEntity>) {
        val diffCallback = ChatSessionDiffCallback(chatSessions, newSessions)
        val diffResult = DiffUtil.calculateDiff(diffCallback)
        
        chatSessions = newSessions
        diffResult.dispatchUpdatesTo(this)
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatHistoryViewHolder {
        val binding = ItemUnifiedChatHistoryBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ChatHistoryViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: ChatHistoryViewHolder, position: Int) {
        holder.bind(chatSessions[position])
    }
    
    override fun getItemCount(): Int = chatSessions.size
    
    inner class ChatHistoryViewHolder(
        private val binding: ItemUnifiedChatHistoryBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(chatSession: ChatSessionEntity) {
            with(binding) {
                // Basic chat info
                tvChatTitle.text = chatSession.title
                tvChatCategory.text = chatSession.category
                tvAiModel.text = chatSession.aiModelUsed
                tvLastUpdate.text = formatDate(chatSession.updatedAt)
                
                // Message count
                tvMessageCount.text = "${chatSession.totalMessages} messages"
                
                // Favorite status
                ivFavorite.setImageResource(
                    if (chatSession.isFavorite) R.drawable.ic_star_filled 
                    else R.drawable.ic_star_outline
                )
                
                // Archive status
                if (chatSession.isArchived) {
                    tvArchiveStatus.visibility = View.VISIBLE
                    tvArchiveStatus.text = "Archived"
                } else {
                    tvArchiveStatus.visibility = View.GONE
                }
                
                // Sync status indicator
                updateSyncStatus(chatSession)
                
                // AI model icon
                updateModelIcon(chatSession.aiModelUsed)
                
                // Click listeners
                root.setOnClickListener {
                    onChatClick(chatSession)
                }
                
                ivFavorite.setOnClickListener {
                    onFavoriteClick(chatSession)
                }
                
                btnShare.setOnClickListener {
                    onShareClick(chatSession)
                }
                
                btnExport.setOnClickListener {
                    onExportClick(chatSession)
                }
                
                btnDelete.setOnClickListener {
                    onDeleteClick(chatSession)
                }
                
                // Long click for additional options
                root.setOnLongClickListener {
                    showContextMenu(chatSession)
                    true
                }
            }
        }
        
        private fun formatDate(date: Date): String {
            val now = Calendar.getInstance()
            val chatDate = Calendar.getInstance().apply { time = date }
            
            return when {
                // Today
                now.get(Calendar.DAY_OF_YEAR) == chatDate.get(Calendar.DAY_OF_YEAR) &&
                now.get(Calendar.YEAR) == chatDate.get(Calendar.YEAR) -> {
                    "Today ${timeFormatter.format(date)}"
                }
                // Yesterday
                now.get(Calendar.DAY_OF_YEAR) - 1 == chatDate.get(Calendar.DAY_OF_YEAR) &&
                now.get(Calendar.YEAR) == chatDate.get(Calendar.YEAR) -> {
                    "Yesterday ${timeFormatter.format(date)}"
                }
                // This year
                now.get(Calendar.YEAR) == chatDate.get(Calendar.YEAR) -> {
                    SimpleDateFormat("MMM dd", Locale.getDefault()).format(date)
                }
                // Previous years
                else -> dateFormatter.format(date)
            }
        }
        
        private fun updateSyncStatus(chatSession: ChatSessionEntity) {
            with(binding) {
                // Show sync indicator based on sync status
                // This would be enhanced with actual sync metadata
                ivSyncStatus.setImageResource(
                    when {
                        chatSession.isArchived -> R.drawable.ic_archive
                        chatSession.updatedAt.time > System.currentTimeMillis() - 60000 -> {
                            // Recently updated - show synced
                            R.drawable.ic_sync_success
                        }
                        else -> R.drawable.ic_sync_pending
                    }
                )
                
                // Sync status text
                val syncText = when {
                    chatSession.isArchived -> "Archived"
                    chatSession.updatedAt.time > System.currentTimeMillis() - 60000 -> "Synced"
                    else -> "Local only"
                }
                tvSyncStatus.text = syncText
            }
        }
        
        private fun updateModelIcon(aiModel: String) {
            val iconRes = when {
                aiModel.contains("gpt", ignoreCase = true) -> R.drawable.ic_openai
                aiModel.contains("claude", ignoreCase = true) -> R.drawable.ic_anthropic
                aiModel.contains("gemini", ignoreCase = true) -> R.drawable.ic_google
                aiModel.contains("llama", ignoreCase = true) -> R.drawable.ic_meta
                else -> R.drawable.ic_ai
            }
            binding.ivModelIcon.setImageResource(iconRes)
        }
        
        private fun showContextMenu(chatSession: ChatSessionEntity) {
            val context = binding.root.context
            val popupMenu = androidx.appcompat.widget.PopupMenu(context, binding.root)
            
            popupMenu.menuInflater.inflate(R.menu.chat_item_menu, popupMenu.menu)
            
            // Update menu items based on chat status
            popupMenu.menu.findItem(R.id.action_favorite)?.title = 
                if (chatSession.isFavorite) "Remove from Favorites" else "Add to Favorites"
            
            popupMenu.menu.findItem(R.id.action_archive)?.title = 
                if (chatSession.isArchived) "Unarchive" else "Archive"
            
            popupMenu.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.action_favorite -> {
                        onFavoriteClick(chatSession)
                        true
                    }
                    R.id.action_share -> {
                        onShareClick(chatSession)
                        true
                    }
                    R.id.action_export -> {
                        onExportClick(chatSession)
                        true
                    }
                    R.id.action_archive -> {
                        // Archive functionality would be added here
                        true
                    }
                    R.id.action_delete -> {
                        onDeleteClick(chatSession)
                        true
                    }
                    else -> false
                }
            }
            
            popupMenu.show()
        }
    }
    
    private class ChatSessionDiffCallback(
        private val oldList: List<ChatSessionEntity>,
        private val newList: List<ChatSessionEntity>
    ) : DiffUtil.Callback() {
        
        override fun getOldListSize(): Int = oldList.size
        
        override fun getNewListSize(): Int = newList.size
        
        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition].sessionId == newList[newItemPosition].sessionId
        }
        
        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            val oldItem = oldList[oldItemPosition]
            val newItem = newList[newItemPosition]
            
            return oldItem.title == newItem.title &&
                    oldItem.updatedAt == newItem.updatedAt &&
                    oldItem.isFavorite == newItem.isFavorite &&
                    oldItem.isArchived == newItem.isArchived &&
                    oldItem.totalMessages == newItem.totalMessages
        }
    }
}