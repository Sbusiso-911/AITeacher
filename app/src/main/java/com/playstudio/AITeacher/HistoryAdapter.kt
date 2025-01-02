package com.playstudio.aiteacher

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.playstudio.aiteacher.R

class HistoryAdapter(
    private val conversations: List<String>,
    private val onCopyClick: (String) -> Unit
) : RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder>() {

    class HistoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val conversationTextView: TextView = itemView.findViewById(R.id.conversation_text)
        val copyImageView: ImageView = itemView.findViewById(R.id.copy_icon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_conversation, parent, false)
        return HistoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        val conversation = conversations[position]
        holder.conversationTextView.text = conversation
        holder.copyImageView.setOnClickListener {
            onCopyClick(conversation)
        }
    }

    override fun getItemCount(): Int {
        return conversations.size
    }
}