package com.playstudio.aiteacher.profile

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.playstudio.aiteacher.databinding.ItemRecentChatBinding
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Compact adapter for displaying recent chat history in profile
 */
class RecentChatAdapter(
    private val onChatClick: (ChatSessionEntity) -> Unit
) : ListAdapter<ChatSessionEntity, RecentChatAdapter.RecentChatViewHolder>(ChatDiffCallback()) {

    override fun submitList(list: List<ChatSessionEntity>?) {
        android.util.Log.d("RecentChatAdapter", "submitList called with ${list?.size} items")
        super.submitList(list)
    }

    override fun getItemCount(): Int {
        val count = super.getItemCount()
        android.util.Log.d("RecentChatAdapter", "getItemCount returning: $count")
        return count
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecentChatViewHolder {
        android.util.Log.d("RecentChatAdapter", "onCreateViewHolder called")
        val binding = ItemRecentChatBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return RecentChatViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RecentChatViewHolder, position: Int) {
        val item = getItem(position)
        android.util.Log.d("RecentChatAdapter", "onBindViewHolder: position=$position, title='${item.title}', messages=${item.messageCount}")
        holder.bind(item)
    }

    inner class RecentChatViewHolder(
        private val binding: ItemRecentChatBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(chat: ChatSessionEntity) {
            binding.apply {
                // Chat title
                chatTitleText.text = chat.title.ifEmpty { "Untitled Chat" }

                // Chat preview
                chatPreviewText.text = chat.lastMessagePreview?.take(50) ?: "No messages yet"

                // AI Model badge
                chatModelBadge.text = formatModelName(chat.aiModelUsed)

                // Time since last update
                chatTimeText.text = formatTimeAgo(chat.updatedAt)

                // Set favorite icon if needed
                if (chat.isFavorite) {
                    chatIconImageView.setImageResource(com.playstudio.aiteacher.R.drawable.ic_star_filled)
                } else {
                    chatIconImageView.setImageResource(com.playstudio.aiteacher.R.drawable.ic_chat)
                }

                // Click listener
                root.setOnClickListener {
                    onChatClick(chat)
                }
            }
        }

        private fun formatModelName(modelName: String): String {
            return when {
                modelName.contains("gpt-4", ignoreCase = true) -> "GPT-4"
                modelName.contains("gpt-3.5", ignoreCase = true) -> "GPT-3.5"
                modelName.contains("claude", ignoreCase = true) -> "Claude"
                modelName.contains("gemini", ignoreCase = true) -> "Gemini"
                else -> modelName.take(6).uppercase()
            }
        }

        private fun formatTimeAgo(date: Date): String {
            val now = Date()
            val diffInMillis = now.time - date.time
            
            return when {
                diffInMillis < TimeUnit.MINUTES.toMillis(1) -> "now"
                diffInMillis < TimeUnit.HOURS.toMillis(1) -> {
                    val minutes = TimeUnit.MILLISECONDS.toMinutes(diffInMillis)
                    "${minutes}m"
                }
                diffInMillis < TimeUnit.DAYS.toMillis(1) -> {
                    val hours = TimeUnit.MILLISECONDS.toHours(diffInMillis)
                    "${hours}h"
                }
                diffInMillis < TimeUnit.DAYS.toMillis(7) -> {
                    val days = TimeUnit.MILLISECONDS.toDays(diffInMillis)
                    "${days}d"
                }
                else -> {
                    val dateFormat = SimpleDateFormat("MMM dd", Locale.getDefault())
                    dateFormat.format(date)
                }
            }
        }
    }

    private class ChatDiffCallback : DiffUtil.ItemCallback<ChatSessionEntity>() {
        override fun areItemsTheSame(oldItem: ChatSessionEntity, newItem: ChatSessionEntity): Boolean {
            return oldItem.sessionId == newItem.sessionId
        }

        override fun areContentsTheSame(oldItem: ChatSessionEntity, newItem: ChatSessionEntity): Boolean {
            return oldItem == newItem
        }
    }
}