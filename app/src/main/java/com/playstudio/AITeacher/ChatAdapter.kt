package com.playstudio.aiteacher

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color // Keep this
import android.text.Html
import android.text.Spannable
import android.text.SpannableString
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.text.style.UnderlineSpan
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter // Import ListAdapter
import androidx.recyclerview.widget.RecyclerView
// Remove: import com.playstudio.aiteacher.databinding.FragmentChatBinding
// Remove: import kotlinx.coroutines.CoroutineScope
// Remove: import kotlinx.coroutines.Dispatchers
// Remove: import kotlinx.coroutines.launch
// Remove: import kotlinx.coroutines.withContext

// Import your ViewBinding classes for item layouts
import com.playstudio.aiteacher.databinding.ItemMessageReceivedBinding
import com.playstudio.aiteacher.databinding.ItemMessageSentBinding
import com.playstudio.aiteacher.databinding.ItemTypingIndicatorBinding


class ChatAdapter(
    // Remove chatMessages, binding, coroutineScope from constructor
    private val onCitationClicked: (citation: com.playstudio.aiteacher.ChatFragment.Citation) -> Unit,
    private val onFollowUpQuestionClicked: (question: String) -> Unit,
    private val onLoadMoreRequested: () -> Unit // Callback for pagination
) : ListAdapter<ChatMessage, RecyclerView.ViewHolder>(ChatMessageDiffUtilCallback()) { // Use ListAdapter

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long {
        return getItem(position).id.hashCode().toLong()
    }

    companion object {
        private const val VIEW_TYPE_SENT = 1
        private const val VIEW_TYPE_RECEIVED = 2
        private const val VIEW_TYPE_TYPING = 3
        // PAGE_SIZE can be defined in the Fragment or ViewModel if it drives the fetch logic
    }

    // isLoading state should be managed by the Fragment/ViewModel that handles data fetching
    // private var isLoading = false

    override fun getItemViewType(position: Int): Int {
        val message = getItem(position) // Use getItem() from ListAdapter
        return when {
            message.isTyping -> VIEW_TYPE_TYPING
            message.isUser -> VIEW_TYPE_SENT
            else -> VIEW_TYPE_RECEIVED
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        Log.d("ChatAdapter", "onCreateViewHolder called with viewType: $viewType")
        return when (viewType) {
            VIEW_TYPE_SENT -> {
                // Use ViewBinding
                val binding = ItemMessageSentBinding.inflate(inflater, parent, false)
                SentMessageViewHolder(binding)
            }
            VIEW_TYPE_RECEIVED -> {
                // Use ViewBinding
                val binding = ItemMessageReceivedBinding.inflate(inflater, parent, false)
                ReceivedMessageViewHolder(binding, onCitationClicked, onFollowUpQuestionClicked)
            }
            VIEW_TYPE_TYPING -> {
                // Use ViewBinding
                val binding = ItemTypingIndicatorBinding.inflate(inflater, parent, false)
                TypingIndicatorViewHolder(binding)
            }
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val chatMessage = getItem(position) // Use getItem() from ListAdapter
        Log.d("ChatAdapter", "onBindViewHolder called for position: $position, message: ${chatMessage.content}")
        when (holder) {
            is SentMessageViewHolder -> holder.bind(chatMessage)
            is ReceivedMessageViewHolder -> holder.bind(chatMessage)
            is TypingIndicatorViewHolder -> holder.bind()
        }

        // Pagination: Trigger load more if near the end of the list (for loading older messages at the top)
        // This typically means checking if position is close to 0
        if (position < 5 && currentList.isNotEmpty() && !getItem(0).isTyping) { // Example threshold: 5 items from the top
            onLoadMoreRequested()
        }
    }

    // getItemCount() is handled by ListAdapter

    // Remove addMessage, updateChatMessages, addMessages, loadMoreMessages, fetchOlderMessages.
    // This logic will move to the Fragment/ViewModel and use `submitList()`.

    class SentMessageViewHolder(private val binding: ItemMessageSentBinding) : // Use ViewBinding
        RecyclerView.ViewHolder(binding.root) {

        fun bind(chatMessage: ChatMessage) {
            binding.messageTextView.text = Html.fromHtml(chatMessage.content, Html.FROM_HTML_MODE_COMPACT) // Or LEGACY if needed
            binding.messageTextView.movementMethod = LinkMovementMethod.getInstance()
            // binding.messageTextView.textAlignment = View.TEXT_ALIGNMENT_TEXT_END // Already set in XML likely

            itemView.setOnLongClickListener {
                copyToClipboard(chatMessage.content, itemView.context)
                true
            }
            binding.copyIcon.setOnClickListener {
                copyToClipboard(chatMessage.content, itemView.context)
            }
        }

        private fun copyToClipboard(text: String, context: Context) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Chat Message", text)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
        }
    }

    class ReceivedMessageViewHolder(
        private val binding: ItemMessageReceivedBinding, // Use ViewBinding
        private val onCitationClickedCallback: (citation: com.playstudio.aiteacher.ChatFragment.Citation) -> Unit,
        private val onFollowUpQuestionClickedCallback: (question: String) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        // The views for follow-up questions (headerButton, questionsContainer)
        // are better defined in the XML layout (item_message_received.xml)
        // and inflated/bound via ViewBinding.
        // Dynamically creating them here is less efficient and harder to manage.

        // Assuming item_message_received.xml now contains:
        // - a FrameLayout R.id.messageContentContainer
        // - a TextView R.id.messageTextView (initially in messageContentContainer)
        // - a LinearLayout R.id.followUpSection (initially GONE)
        //   - a Button R.id.followUpHeaderButton
        //   - a LinearLayout R.id.followUpButtonsContainer
        // - an ImageView R.id.copyIcon

        private var richWebView: RichMessageWebView? = null // Keep if needed

        fun bind(chatMessage: ChatMessage) {
            binding.messageContentContainer.removeAllViews() // Clear previous content (TextView or WebView)

            if (chatMessage.containsRichContent && chatMessage.content.isNotBlank()) {
                binding.messageTextView.visibility = View.GONE // Hide default TextView
                richWebView = RichMessageWebView(itemView.context).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT
                    )
                }
                binding.messageContentContainer.addView(richWebView)
                richWebView?.displayFormattedContent(chatMessage.content)
            } else {
                binding.messageTextView.visibility = View.VISIBLE
                if (chatMessage.citations.isNotEmpty() && chatMessage.content.isNotBlank()) {
                    applyCitations(chatMessage, binding.messageTextView)
                } else {
                    binding.messageTextView.text = Html.fromHtml(chatMessage.content, Html.FROM_HTML_MODE_COMPACT)
                }
                // Add messageTextView back if it was removed or ensure it's the one visible
                if (binding.messageTextView.parent == null) {
                    binding.messageContentContainer.addView(binding.messageTextView)
                }
            }
            binding.messageTextView.movementMethod = LinkMovementMethod.getInstance()


            // Handle follow-up questions
            if (chatMessage.followUpQuestions.isNotEmpty()) {
                binding.followUpSection.visibility = View.VISIBLE
                binding.followUpButtonsContainer.removeAllViews() // Clear previous buttons

                var isFollowUpExpanded = false // State per item
                binding.followUpHeaderButton.text = "Suggested Follow-ups ▼"
                binding.followUpButtonsContainer.visibility = View.GONE

                binding.followUpHeaderButton.setOnClickListener {
                    isFollowUpExpanded = !isFollowUpExpanded
                    if (isFollowUpExpanded) {
                        binding.followUpButtonsContainer.visibility = View.VISIBLE
                        binding.followUpHeaderButton.text = "Suggested Follow-ups ▲"
                    } else {
                        binding.followUpButtonsContainer.visibility = View.GONE
                        binding.followUpHeaderButton.text = "Suggested Follow-ups ▼"
                    }
                }

                chatMessage.followUpQuestions.forEach { question ->
                    // It's better to have a separate layout for follow-up buttons (e.g., item_follow_up_button.xml)
                    // and inflate it, rather than creating Buttons programmatically for styling consistency.
                    // For now, programmatic creation:
                    val button = Button(itemView.context).apply {
                        text = question
                        // Add styling (e.g., from a style resource)
                        // setTextColor(Color.parseColor("#E1DFDF")) // Example
                        // background = null // Example
                        setOnClickListener {
                            onFollowUpQuestionClickedCallback(question)
                        }
                    }
                    binding.followUpButtonsContainer.addView(button)
                }
            } else {
                binding.followUpSection.visibility = View.GONE
            }

            binding.copyIcon.setOnClickListener {
                copyToClipboard(chatMessage.content, itemView.context)
            }
        }

        private fun applyCitations(chatMessage: ChatMessage, textView: TextView) {
            try {
                val spannable = SpannableString(chatMessage.content)
                chatMessage.citations.sortedByDescending { it.startIndex }.forEach { citation ->
                    if (citation.startIndex >= 0 && citation.endIndex <= spannable.length && citation.startIndex < citation.endIndex) {
                        val clickableSpan = object : ClickableSpan() {
                            override fun onClick(widget: View) {
                                onCitationClickedCallback(citation)
                            }
                        }
                        spannable.setSpan(clickableSpan, citation.startIndex, citation.endIndex, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                        spannable.setSpan(ForegroundColorSpan(Color.BLUE), citation.startIndex, citation.endIndex, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                        spannable.setSpan(UnderlineSpan(), citation.startIndex, citation.endIndex, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    } else {
                        Log.w("ChatAdapter", "Invalid citation indices: $citation for content length ${spannable.length}")
                    }
                }
                textView.text = spannable
            } catch (e: Exception) {
                Log.e("ChatAdapter", "Error applying spans: ${e.message}. Content: '${chatMessage.content}'")
                textView.text = Html.fromHtml(chatMessage.content, Html.FROM_HTML_MODE_COMPACT) // Fallback
            }
        }


        private fun copyToClipboard(text: String, context: Context) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Chat Message", text)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
        }
    }

    class TypingIndicatorViewHolder(private val binding: ItemTypingIndicatorBinding) : // Use ViewBinding
        RecyclerView.ViewHolder(binding.root) {
        fun bind() {
            // If using Lottie: binding.lottieAnimationView.playAnimation()
        }
    }
}

// Define DiffUtil.ItemCallback (not DiffUtil.Callback)
class ChatMessageDiffUtilCallback : DiffUtil.ItemCallback<ChatMessage>() {
    override fun areItemsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean {
        return oldItem == newItem // Relies on data class's generated equals()
    }
}