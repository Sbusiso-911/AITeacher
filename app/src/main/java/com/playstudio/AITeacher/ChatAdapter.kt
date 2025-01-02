package com.playstudio.aiteacher

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.playstudio.aiteacher.databinding.FragmentChatBinding

class ChatAdapter(
    private val chatMessages: MutableList<ChatMessage>,
    private val binding: FragmentChatBinding
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_SENT = 1
        private const val VIEW_TYPE_RECEIVED = 2
        private const val VIEW_TYPE_TYPING = 3
    }

    override fun getItemViewType(position: Int): Int {
        return when {
            chatMessages[position].isTyping -> VIEW_TYPE_TYPING
            chatMessages[position].isUser -> VIEW_TYPE_SENT
            else -> VIEW_TYPE_RECEIVED
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        Log.d("ChatAdapter", "onCreateViewHolder called with viewType: $viewType")
        return when (viewType) {
            VIEW_TYPE_SENT -> {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_message_sent, parent, false)
                SentMessageViewHolder(view)
            }
            VIEW_TYPE_RECEIVED -> {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_message_received, parent, false)
                ReceivedMessageViewHolder(view, binding)
            }
            VIEW_TYPE_TYPING -> {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_typing_indicator, parent, false)
                TypingIndicatorViewHolder(view)
            }
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val chatMessage = chatMessages[position]
        Log.d("ChatAdapter", "onBindViewHolder called for position: $position, message: ${chatMessage.content}")
        when (holder) {
            is SentMessageViewHolder -> holder.bind(chatMessage)
            is ReceivedMessageViewHolder -> holder.bind(chatMessage)
            is TypingIndicatorViewHolder -> holder.bind()
        }
    }

    override fun getItemCount(): Int = chatMessages.size

    fun addMessage(chatMessage: ChatMessage) {
        chatMessages.add(chatMessage)
        notifyItemInserted(chatMessages.size - 1)
        binding.recyclerView.smoothScrollToPosition(chatMessages.size - 1)
    }

    fun updateChatMessages(newChatMessages: List<ChatMessage>) {
        val diffCallback = ChatDiffCallback(chatMessages, newChatMessages)
        val diffResult = DiffUtil.calculateDiff(diffCallback)

        chatMessages.clear()
        chatMessages.addAll(newChatMessages)
        diffResult.dispatchUpdatesTo(this)
    }

    class SentMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val messageTextView: TextView = itemView.findViewById(R.id.messageTextView)
        private val copyIcon: ImageView = itemView.findViewById(R.id.copy_icon)

        fun bind(chatMessage: ChatMessage) {
            messageTextView.text = chatMessage.content
            messageTextView.textAlignment = View.TEXT_ALIGNMENT_TEXT_END

            // Set long-click listener to copy message to clipboard
            itemView.setOnLongClickListener {
                copyToClipboard(chatMessage.content)
                true
            }

            // Set click listener for the copy icon
            copyIcon.setOnClickListener {
                copyToClipboard(chatMessage.content)
            }
        }

        private fun copyToClipboard(text: String) {
            val clipboard = itemView.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Chat Message", text)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(itemView.context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
        }
    }

    class ReceivedMessageViewHolder(itemView: View, private val binding: FragmentChatBinding) : RecyclerView.ViewHolder(itemView) {
        private val messageTextView: TypeWriterTextView = itemView.findViewById(R.id.messageTextView)
        private val followUpContainer: LinearLayout = itemView.findViewById(R.id.followUpQuestionsContainer)
        private val copyIcon: ImageView = itemView.findViewById(R.id.copy_icon)

        fun bind(chatMessage: ChatMessage) {
            messageTextView.setCharacterDelay(1) // Set delay in ms
            messageTextView.animateText(chatMessage.content)
            messageTextView.textAlignment = View.TEXT_ALIGNMENT_TEXT_START

            // Clear previous follow-up questions
            followUpContainer.removeAllViews()

            // Add follow-up questions if available
            if (chatMessage.followUpQuestions.isNotEmpty()) {
                followUpContainer.visibility = View.VISIBLE
                for (question in chatMessage.followUpQuestions) {
                    val button = Button(itemView.context).apply {
                        text = question
                        setOnClickListener {
                            // Handle follow-up question click
                            binding.messageEditText.setText(question)
                        }
                    }
                    followUpContainer.addView(button)
                }
            } else {
                followUpContainer.visibility = View.GONE
            }

            // Set long-click listener to copy message to clipboard
            itemView.setOnLongClickListener {
                copyToClipboard(chatMessage.content)
                true
            }

            // Set click listener for the copy icon
            copyIcon.setOnClickListener {
                copyToClipboard(chatMessage.content)
            }
        }

        private fun copyToClipboard(text: String) {
            val clipboard = itemView.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Chat Message", text)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(itemView.context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
        }
    }

    class TypingIndicatorViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun bind() {
            // No binding needed for typing indicator
        }
    }

    class ChatDiffCallback(
        private val oldList: List<ChatMessage>,
        private val newList: List<ChatMessage>
    ) : DiffUtil.Callback() {

        override fun getOldListSize(): Int = oldList.size

        override fun getNewListSize(): Int = newList.size

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            // Assuming each message has a unique ID or timestamp
            return oldList[oldItemPosition].content == newList[newItemPosition].content
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition] == newList[newItemPosition]
        }
    }
}