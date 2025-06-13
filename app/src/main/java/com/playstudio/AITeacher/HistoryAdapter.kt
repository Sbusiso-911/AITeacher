package com.playstudio.aiteacher

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.playstudio.aiteacher.R

import com.playstudio.aiteacher.history.ConversationEntity

class HistoryAdapter(
    private val onItemClick: (ConversationEntity) -> Unit
) : ListAdapter<ConversationEntity, HistoryAdapter.HistoryViewHolder>(DIFF_CALLBACK) {

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<ConversationEntity>() {
            override fun areItemsTheSame(oldItem: ConversationEntity, newItem: ConversationEntity): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: ConversationEntity, newItem: ConversationEntity): Boolean {
                return oldItem == newItem
            }
        }
    }

    class HistoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val conversationTextView: TextView = itemView.findViewById(R.id.conversation_text)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_conversation, parent, false)
        return HistoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        val conversation = getItem(position)
        holder.conversationTextView.text = conversation.title
        holder.itemView.setOnClickListener { onItemClick(conversation) }
    }
}