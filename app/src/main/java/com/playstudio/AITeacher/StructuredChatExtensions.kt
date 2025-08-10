package com.playstudio.aiteacher

import android.util.Log
import android.view.LayoutInflater
import android.widget.LinearLayout
import androidx.lifecycle.lifecycleScope
import com.playstudio.aiteacher.api.StructuredAPIHandler
import com.playstudio.aiteacher.models.*
import kotlinx.coroutines.flow.collect
import com.playstudio.aiteacher.ui.StructuredContentView
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

/**
 * Extensions for ChatFragment to support structured educational responses
 */

// Store structured API handler instances using a WeakHashMap to avoid memory leaks
private val structuredAPIHandlers = java.util.WeakHashMap<ChatFragment, StructuredAPIHandler>()

// Add structured API handler as a property
private var ChatFragment.structuredAPIHandler: StructuredAPIHandler?
    get() = structuredAPIHandlers[this]
    set(value) {
        if (value != null) {
            structuredAPIHandlers[this] = value
        } else {
            structuredAPIHandlers.remove(this)
        }
    }

// Extension to initialize structured outputs
fun ChatFragment.initializeStructuredOutputs(okHttpClient: OkHttpClient) {
    structuredAPIHandler = StructuredAPIHandler(okHttpClient)
}

/**
 * NEW: Streaming message processing for better UX - no timeouts!
 */
fun ChatFragment.processStructuredMessage(
    userMessage: String,
    requestSpecificType: Boolean = false
) {
    val handler = structuredAPIHandler ?: run {
        Log.e("StructuredChat", "StructuredAPIHandler not initialized")
        return
    }

    lifecycleScope.launch {
        try {
            // Determine if we should use enhanced rendering (much more selective)
            val useEnhancedRendering = shouldUseEnhancedRendering(userMessage) || requestSpecificType

            if (useEnhancedRendering) {
                // Get chat history for context
                val chatHistory = getChatHistoryForAPI()
                
                // Start streaming with adaptive content rendering
                processStreamingAdaptiveContent(
                    handler = handler,
                    userMessage = userMessage,
                    chatHistory = chatHistory
                )
            } else {
                // Use regular chat completion for most questions
                fallbackToRegularChat(userMessage)
            }
        } catch (e: Exception) {
            Log.e("StructuredChat", "Error in structured message processing", e)
            hideTypingIndicator()
            showError("Failed to process message: ${e.message}")
        }
    }
}

/**
 * NEW: Handle streaming adaptive content with real-time updates
 */
private suspend fun ChatFragment.processStreamingAdaptiveContent(
    handler: StructuredAPIHandler,
    userMessage: String,
    chatHistory: List<Pair<String, String>>
) {
    // Create a streaming message that will be updated in real-time
    val streamingMessage = ChatMessage(
        id = java.util.UUID.randomUUID().toString(),
        content = "🤔 Thinking...", // Initial content
        isUser = false,
        timestamp = System.currentTimeMillis(),
        isTyping = false,
        containsRichContent = true
    )
    
    // Add the streaming message to chat immediately
    chatMessages.add(streamingMessage)
    updateChatUI()
    
    try {
        // Collect streaming updates - use structured outputs following OpenAI guidelines
        handler.getStructuredStreamingResponse(
            userMessage = userMessage,
            chatHistory = chatHistory,
            model = getCurrentModelForStreaming()
        ).collect { update ->
            when (update) {
                is SimpleStreamingUpdate.Progress -> {
                    // Update message with progress
                    updateStreamingMessage(streamingMessage, "⏳ ${update.message}")
                }
                is SimpleStreamingUpdate.ContentChunk -> {
                    // Update message with accumulated content
                    updateStreamingMessage(streamingMessage, update.accumulatedContent)
                }
                is SimpleStreamingUpdate.Complete -> {
                    // Final update - render with adaptive content renderer
                    Log.d("StructuredChat", "✅ Received SimpleStreamingUpdate.Complete")
                    handleFinalAdaptiveContent(streamingMessage, update.finalContent, userMessage)
                }
                is SimpleStreamingUpdate.Error -> {
                    // Handle error and show in the same message (no duplication)
                    Log.e("StructuredChat", "Streaming error: ${update.message}")
                    val errorMessage = if (update.message.contains("400")) {
                        "I upgraded your request to use a compatible model. Please try your question again."
                    } else {
                        "Sorry, I encountered an error: ${update.message}"
                    }
                    updateStreamingMessage(streamingMessage, "⚠️ $errorMessage")
                    // Don't call fallbackToRegularChat - it creates duplicate messages
                }
            }
        }
    } catch (e: Exception) {
        Log.e("StructuredChat", "Streaming failed", e)
        updateStreamingMessage(streamingMessage, "⚠️ Failed to get response. Please try again.")
        // Don't call fallbackToRegularChat to avoid duplicate messages
    }
}

/**
 * Update streaming message content in real-time
 */
private fun ChatFragment.updateStreamingMessage(message: ChatMessage, newContent: String) {
    // Find and update the message
    val index = chatMessages.indexOfFirst { it.id == message.id }
    if (index != -1) {
        Log.d("StructuredChat", "🔄 Updating streaming message ${message.id} at index $index. Content length: ${newContent.length}")
        val existingMessage = chatMessages[index]
        
        // Create updated message preserving all structured content fields
        val updatedMessage = message.copy(
            content = newContent,
            // Preserve structured content from existing message if it exists
            structuredContentJson = existingMessage.structuredContentJson ?: message.structuredContentJson,
            structuredContent = existingMessage.structuredContent ?: message.structuredContent,
            learningContent = existingMessage.learningContent ?: message.learningContent
        )
        chatMessages[index] = updatedMessage
        
        Log.d("StructuredChat", "🔄 Updated message preserved JSON length: ${updatedMessage.structuredContentJson?.length ?: 0}")
        
        // Update UI
        updateChatUI()
    } else {
        Log.w("StructuredChat", "⚠️ Could not find streaming message ${message.id} to update")
    }
}

/**
 * Handle final adaptive content with dynamic rendering
 */
private fun ChatFragment.handleFinalAdaptiveContent(
    streamingMessage: ChatMessage,
    finalContent: String,
    originalUserMessage: String
) {
    try {
        val index = chatMessages.indexOfFirst { it.id == streamingMessage.id }
        if (index != -1) {
            val existingMessage = chatMessages[index]
            
            // Store final content for adaptive rendering
            Log.d("StructuredChat", "📝 Storing adaptive content for message ${streamingMessage.id}")
            
            // Update with final content and mark as containing rich content for adaptive rendering
            val updatedMessage = existingMessage.copy(
                content = finalContent,
                containsRichContent = true // This triggers adaptive rendering in ChatAdapter
            )
            chatMessages[index] = updatedMessage
            
            // Force UI update for final content (bypass throttling)
            Log.d("StructuredChat", "📱 Force updating UI for final adaptive content")
            val newList = ArrayList(chatMessages)
            chatAdapter.submitList(newList) {
                binding.recyclerView.scrollToPosition(chatMessages.size - 1)
            }
        }
        
        Log.d("StructuredChat", "✅ Streaming adaptive content completed successfully")
        
    } catch (e: Exception) {
        Log.e("StructuredChat", "Error finalizing adaptive content", e)
        updateStreamingMessage(streamingMessage, "⚠️ Failed to display content")
    }
}

// Add throttling for UI updates - lighter throttling since we fixed batching
private var lastUIUpdateTime = 0L
private const val UI_UPDATE_THROTTLE_MS = 200L // Max 5 updates per second, since batching is now fixed

/**
 * Update chat UI efficiently with throttling to prevent infinite loops
 */
private fun ChatFragment.updateChatUI() {
    val currentTime = System.currentTimeMillis()
    if (currentTime - lastUIUpdateTime < UI_UPDATE_THROTTLE_MS) {
        Log.d("StructuredChat", "⏰ UI update throttled, skipping")
        return
    }
    lastUIUpdateTime = currentTime
    
    Log.d("StructuredChat", "📱 Updating chat UI with ${chatMessages.size} messages")
    val newList = ArrayList(chatMessages)
    chatAdapter.submitList(newList) {
        binding.recyclerView.scrollToPosition(chatMessages.size - 1)
    }
}

/**
 * Handle NEW learning content response by creating appropriate UI.
 * If rendering fails, falls back to regular chat for the original user message.
 */
private fun ChatFragment.handleLearningContentResponse(
    content: LearningContent,
    originalUserMessage: String
) {
    Log.d("StructuredChat", "▶️ handleLearningContentResponse(topic=${content.topicTitle}, subject=${content.subjectArea})")
    try {
        hideTypingIndicator()

        // Create structured content view
        val structuredContentView = StructuredContentView(requireContext())
        structuredContentView.setLearningContent(content)
        
        // Set up interaction listener for analytics and progress tracking
        structuredContentView.setOnContentInteractionListener(object : StructuredContentView.OnContentInteractionListener {
            override fun onQuestionAnswered(questionId: String, answer: String, isCorrect: Boolean) {
                trackQuestionAnswer(questionId, answer, isCorrect)
            }

            override fun onStepCompleted(stepNumber: Int) {
                trackStepCompletion(stepNumber)
            }

            override fun onExampleViewed(exampleTitle: String) {
                trackExampleView(exampleTitle)
            }

            override fun onFormulaUsed(formulaName: String) {
                trackFormulaUsage(formulaName)
            }

            override fun onCodeExecuted(codeLanguage: String, codeTitle: String) {
                trackCodeExecution(codeLanguage, codeTitle)
            }
        })

        // Create a chat message with learning content
        val learningMessage = ChatMessage(
            id = java.util.UUID.randomUUID().toString(),
            content = "${content.topicTitle}\n\n${content.introduction.hook}",
            isUser = false,
            timestamp = System.currentTimeMillis(),
            isTyping = false,
            containsRichContent = true
        ).apply {
            // Store learning content for persistence
            storeLearningContent(content)
        }

        // Add to chat and update UI
        addMessageToChat(learningMessage, structuredContentView)
        
        // Track usage for analytics
        trackLearningContent(content)

    } catch (e: Exception) {
        Log.e("StructuredChat", "Error handling learning content", e)
        showError("Failed to display educational content")
        fallbackToRegularChat(originalUserMessage)
        return
    }
}

/**
 * Add message with structured content - now properly handled by RecyclerView adapter
 */
private fun ChatFragment.addMessageToChat(message: ChatMessage, contentView: StructuredContentView) {
    Log.d("StructuredChat", "▶️ addMessageToChat(structuredMessage id=${message.id})")
    
    // Add message to the list
    chatMessages.add(message)
    
    // Create a new list instance for DiffUtil to properly detect changes
    // but avoid unnecessary recreations by using ArrayList constructor
    val newList = ArrayList(chatMessages)
    chatAdapter.submitList(newList) {
        // Callback executed after the list is updated - scroll to bottom
        binding.recyclerView.scrollToPosition(chatMessages.size - 1)
    }
    
    // Save conversation
    saveConversation()
}

/**
 * Determine if message should use enhanced rendering (based on content type, not forced triggers)
 */
fun shouldUseEnhancedRendering(message: String): Boolean {
    // Let AI decide - only use enhanced rendering for specific content patterns
    val enhancedTriggers = setOf(
        "step by step", "tutorial", "lesson", "guide",
        "explain in detail", "comprehensive", "detailed explanation"
    )
    
    val messageLower = message.lowercase()
    val result = enhancedTriggers.any { messageLower.contains(it) } ||
           message.length > 100 // Only longer, complex questions
    
    Log.d("StructuredChat", "🤔 shouldUseEnhancedRendering('$message') = $result")
    return result
}

/**
 * Get current model suitable for structured outputs (must support json_schema)
 */
private fun ChatFragment.getCurrentModelForStreaming(): String {
    val currentModel = getCurrentModel()
    Log.d("StructuredChat", "🔧 getCurrentModel() returned: '$currentModel'")
    
    // Only use models that support structured outputs as per OpenAI docs
    val selectedModel = when {
        currentModel.contains("gpt-4o-2024-08-06") -> "gpt-4o-2024-08-06"
        currentModel.contains("gpt-4o-mini") -> "gpt-4o-mini" 
        currentModel.contains("gpt-4o") -> "gpt-4o-mini" // Use mini for speed but with structured support
        currentModel.contains("gpt-4") -> "gpt-4o-mini" // Fallback to supported model
        currentModel.contains("gpt-3.5") -> "gpt-4o-mini" // IMPORTANT: gpt-3.5 doesn't support structured outputs
        else -> "gpt-4o-mini" // Default: fastest model with structured output support
    }
    
    Log.d("StructuredChat", "🔧 Mapped '$currentModel' -> '$selectedModel' for structured outputs")
    return selectedModel
}

/**
 * Get chat history in format needed for API
 */
private fun ChatFragment.getChatHistoryForAPI(): List<Pair<String, String>> {
    return chatMessages
        .filterNot { it.isTyping }
        .takeLast(10) // Limit history to prevent token overflow
        .map { message ->
            val role = if (message.isUser) "user" else "assistant"
            val content = message.content
            Pair(role, content)
        }
}

/**
 * Fallback to regular chat completion
 */
private fun ChatFragment.fallbackToRegularChat(userMessage: String) {
    // Call the existing chat completion method
    // This will use the existing processChatCompletionInternal method
    processChatCompletionInternal(userMessage)
}

/**
 * Analytics tracking methods
 */
private fun ChatFragment.trackQuestionAnswer(questionId: String, answer: String, isCorrect: Boolean) {
    Log.d("Analytics", "Question answered: $questionId, correct: $isCorrect")
    // Implement analytics tracking
}

private fun ChatFragment.trackStepCompletion(stepNumber: Int) {
    Log.d("Analytics", "Step completed: $stepNumber")
    // Implement step completion tracking
}

private fun ChatFragment.trackExampleView(exampleTitle: String) {
    Log.d("Analytics", "Example viewed: $exampleTitle")
    // Implement example view tracking
}

private fun ChatFragment.trackFormulaUsage(formulaName: String) {
    Log.d("Analytics", "Formula used: $formulaName")
    // Implement formula usage tracking
}

private fun ChatFragment.trackCodeExecution(codeLanguage: String, codeTitle: String) {
    Log.d("Analytics", "Code executed: $codeLanguage - $codeTitle")
    // Implement code execution tracking
}

private fun ChatFragment.trackLearningContent(content: LearningContent) {
    Log.d("Analytics", "Learning content: ${content.topicTitle}, subject: ${content.subjectArea}")
    // Implement learning content tracking
}

// Extension to store learning content in chat message is already implemented in ChatMessage.kt
// Remove this duplicate empty implementation

/**
 * Show/hide typing indicator methods
 */
private fun ChatFragment.showTypingIndicator() {
    // Add typing message to chat
    val typingMessage = ChatMessage(
        id = java.util.UUID.randomUUID().toString(),
        content = "AI Teacher is thinking...",
        isUser = false,
        timestamp = System.currentTimeMillis(),
        isTyping = true
    )
    chatMessages.add(typingMessage)
    
    // Submit the updated list to the ListAdapter
    val newList = ArrayList(chatMessages)
    chatAdapter.submitList(newList) {
        binding.recyclerView.scrollToPosition(chatMessages.size - 1)
    }
}

private fun ChatFragment.hideTypingIndicator() {
    // Remove typing indicator
    val typingIndex = chatMessages.indexOfLast { it.isTyping }
    if (typingIndex != -1) {
        chatMessages.removeAt(typingIndex)
        // Submit the updated list to the ListAdapter
        val newList = ArrayList(chatMessages)
        chatAdapter.submitList(newList)
    }
}

private fun ChatFragment.showError(message: String) {
    // Show error message to user
    val errorMessage = ChatMessage(
        id = java.util.UUID.randomUUID().toString(),
        content = "⚠️ $message",
        isUser = false,
        timestamp = System.currentTimeMillis(),
        isTyping = false
    )
    chatMessages.add(errorMessage)
    
    // Submit the updated list to the ListAdapter
    val newList = ArrayList(chatMessages)
    chatAdapter.submitList(newList) {
        binding.recyclerView.scrollToPosition(chatMessages.size - 1)
    }
}


/**
 * Enhanced message processing that replaces the current send message logic
 */
fun ChatFragment.enhancedSendMessage(message: String) {
    // Add user message to chat immediately
    val userMessage = ChatMessage(
        id = java.util.UUID.randomUUID().toString(),
        content = message,
        isUser = true,
        timestamp = System.currentTimeMillis(),
        isTyping = false
    )
    chatMessages.add(userMessage)
    
    // Submit the updated list to the ListAdapter
    val newList = ArrayList(chatMessages)
    chatAdapter.submitList(newList) {
        // Scroll to bottom after list is updated
        binding.recyclerView.scrollToPosition(chatMessages.size - 1)
    }
    
    // Clear input
    binding.messageEditText.text.clear()
    
    // Process with structured outputs
    processStructuredMessage(message)
}