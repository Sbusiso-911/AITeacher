package com.playstudio.aiteacher

import android.util.Log
import android.view.LayoutInflater
import android.widget.LinearLayout
import androidx.lifecycle.lifecycleScope
import com.playstudio.aiteacher.api.StructuredAPIHandler
import com.playstudio.aiteacher.models.*
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
 * Enhanced message processing with structured outputs
 */
fun ChatFragment.processStructuredMessage(
    userMessage: String,
    requestSpecificType: Boolean = false,
    responseType: ResponseType = ResponseType.EXPLANATION
) {
    val handler = structuredAPIHandler ?: run {
        Log.e("StructuredChat", "StructuredAPIHandler not initialized")
        return
    }

    lifecycleScope.launch {
        try {
            // Show typing indicator
            showTypingIndicator()

            // Determine if we should use structured output based on message content
            val useStructuredOutput = shouldUseStructuredOutput(userMessage) || requestSpecificType

            if (useStructuredOutput) {
                // Get chat history for context
                val chatHistory = getChatHistoryForAPI()
                
                // Request structured response
                val result = handler.getStructuredEducationalResponse(
                    userMessage = userMessage,
                    chatHistory = chatHistory,
                    model = getCurrentModelForStructured(),
                    requestQuiz = responseType == ResponseType.QUIZ,
                    requestStepByStep = responseType == ResponseType.STEP_BY_STEP
                )

                result.fold(
                    onSuccess = { educationalResponse ->
                        try {
                            handleStructuredResponse(educationalResponse, userMessage)
                        } catch (e: Exception) {
                            Log.e("StructuredChat", "Structured rendering failed, falling back", e)
                            fallbackToRegularChat(userMessage)
                        }
                    },
                    onFailure = { error ->
                        Log.e("StructuredChat", "Failed to get structured response", error)
                        fallbackToRegularChat(userMessage)
                    }
                )
            } else {
                // Use regular chat completion for simple questions
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
 * Handle structured educational response by creating appropriate UI.
 * If rendering fails, falls back to regular chat for the original user message.
 */
private fun ChatFragment.handleStructuredResponse(
    response: EducationalResponse,
    originalUserMessage: String
) {
    Log.d("StructuredChat", "▶️ handleStructuredResponse(responseType=${response.responseType}, subject=${response.subject})")
    try {
        hideTypingIndicator()

        // Create structured content view
        val structuredContentView = StructuredContentView(requireContext())
        structuredContentView.setEducationalResponse(response)
        
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

        // Create a chat message with structured content
        val structuredMessage = ChatMessage(
            id = java.util.UUID.randomUUID().toString(),
            content = response.content.mainExplanation,
            isUser = false,
            timestamp = System.currentTimeMillis(),
            isTyping = false,
            containsRichContent = true
        ).apply {
            // Store structured content for persistence
            storeStructuredContent(response)
        }

        // Add to chat and update UI
        addMessageToChat(structuredMessage, structuredContentView)
        
        // Track usage for analytics
        trackStructuredResponse(response)

    } catch (e: Exception) {
        Log.e("StructuredChat", "Error handling structured response", e)
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
 * Determine if message should use structured output
 */
fun shouldUseStructuredOutput(message: String): Boolean {
    val structuredTriggers = setOf(
        // Educational keywords
        "explain", "how to", "what is", "why does", "teach me", "learn about",
        "solve", "calculate", "find", "prove", "demonstrate",
        
        // Subject-specific keywords
        "math", "mathematics", "algebra", "geometry", "calculus",
        "programming", "code", "function", "algorithm",
        "science", "physics", "chemistry", "biology",
        "history", "literature", "grammar",
        
        // Request types
        "step by step", "example", "practice", "quiz", "test",
        "exercise", "problem", "solution", "tutorial"
    )
    
    val messageLower = message.lowercase()
    return structuredTriggers.any { messageLower.contains(it) } ||
           message.contains(Regex("[+\\-*/=<>^√∫∑π]")) || // Math symbols
           message.length > 50 // Longer questions likely benefit from structure
}

/**
 * Get current model suitable for structured outputs
 */
private fun ChatFragment.getCurrentModelForStructured(): String {
    val currentModel = getCurrentModel()
    
    // Map current model to structured output compatible model
    return when {
        currentModel.contains("gpt-4o") -> currentModel
        currentModel.contains("gpt-4") -> "gpt-4o-2024-08-06"
        currentModel.contains("gpt-3.5") -> "gpt-4o-mini"
        else -> "gpt-4o-mini" // Default fallback
    }
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

private fun ChatFragment.trackStructuredResponse(response: EducationalResponse) {
    Log.d("Analytics", "Structured response: ${response.responseType}, subject: ${response.subject}")
    // Implement comprehensive response tracking
}

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