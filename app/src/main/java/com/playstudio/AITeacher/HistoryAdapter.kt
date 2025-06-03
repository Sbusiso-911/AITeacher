package com.playstudio.aiteacher

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.playstudio.aiteacher.R

import com.playstudio.aiteacher.history.ConversationEntity

class HistoryAdapter(
    private var conversations: List<ConversationEntity>,
    private val onItemClick: (ConversationEntity) -> Unit
) : RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder>() {

    class HistoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val conversationTextView: TextView = itemView.findViewById(R.id.conversation_text)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_conversation, parent, false)
        return HistoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        val conversation = conversations[position]
        holder.conversationTextView.text = conversation.title
        holder.itemView.setOnClickListener { onItemClick(conversation) }
    }

    override fun getItemCount(): Int {
        return conversations.size
    }

    fun submitList(newList: List<ConversationEntity>) {
        conversations = newList
        notifyDataSetChanged()
    }
}