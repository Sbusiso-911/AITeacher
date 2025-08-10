package com.playstudio.aiteacher.api

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.playstudio.aiteacher.ChatMessage
import java.text.SimpleDateFormat
import java.util.*

/**
 * AI Function Call Manager
 * Integrates OpenAI function calling with the existing chat system
 */
class AIFunctionCallManager(private val context: Context, private val apiKey: String) {
    
    companion object {
        private const val TAG = "AIFunctionCallManager"
    }
    
    private val functionCaller = OpenAIFunctionCaller(apiKey)
    private val educationalFunctions = EducationalFunctions
    
    /**
     * Process a chat message with AI function calling capabilities
     */
    suspend fun processMessageWithFunctions(
        message: String,
        includeWebSearch: Boolean = false,
        includeEducationalFunctions: Boolean = true,
        model: String = "gpt-4.1"
    ): ChatMessage = withContext(Dispatchers.IO) {
        
        try {
            Log.d(TAG, "Processing message with functions: $message")
            
            // Determine which functions to include
            val functions = mutableListOf<OpenAIFunctionCaller.FunctionDefinition>()
            
            if (includeEducationalFunctions) {
                // Add educational functions based on message content
                functions.addAll(getRelevantEducationalFunctions(message))
                Log.d(TAG, "Added ${functions.size} educational functions")
            }
            
            // Make initial API call
            val response = functionCaller.callWithFunctions(
                input = message,
                functions = functions,
                model = model,
                includeWebSearch = includeWebSearch,
                toolChoice = "auto"
            )
            
            // Handle the response
            when {
                // Direct text response (no function calls)
                response.outputText != null && !response.requiresFunctionExecution -> {
                    Log.d(TAG, "Direct text response received")
                    createChatMessage(response.outputText, isUser = false)
                }
                
                // Function calls need to be executed
                response.requiresFunctionExecution -> {
                    Log.d(TAG, "Function calls detected: ${response.functionCalls.size}")
                    handleFunctionCalls(message, response, functions, model)
                }
                
                // Fallback
                else -> {
                    createChatMessage("I'm having trouble processing your request. Could you please rephrase it?", isUser = false)
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error processing message with functions", e)
            createChatMessage("I encountered an error while processing your request. Please try again.", isUser = false)
        }
    }
    
    /**
     * Handle function calls by executing them and getting final response
     */
    private suspend fun handleFunctionCalls(
        originalMessage: String,
        response: OpenAIFunctionCaller.OpenAIResponse,
        functions: List<OpenAIFunctionCaller.FunctionDefinition>,
        model: String
    ): ChatMessage = withContext(Dispatchers.IO) {
        
        try {
            val executedCalls = mutableListOf<OpenAIFunctionCaller.FunctionCallResult>()
            
            // Execute each function call
            for (functionCall in response.functionCalls) {
                Log.d(TAG, "Executing function: ${functionCall.name}")
                
                val result = when {
                    // Educational functions
                    isEducationalFunction(functionCall.name) -> {
                        educationalFunctions.executeFunction(functionCall.name, functionCall.arguments)
                    }
                    
                    // Voice command functions (integrated with existing system)
                    isVoiceCommandFunction(functionCall.name) -> {
                        executeVoiceCommand(functionCall.name, functionCall.arguments)
                    }
                    
                    // Unknown function
                    else -> {
                        Log.w(TAG, "Unknown function: ${functionCall.name}")
                        "Function '${functionCall.name}' is not implemented"
                    }
                }
                
                executedCalls.add(functionCall.copy(result = result))
                Log.d(TAG, "Function ${functionCall.name} executed successfully")
            }
            
            // Get final response with function results
            val finalResponse = functionCaller.executeAndContinue(
                originalInput = originalMessage,
                functionCalls = executedCalls,
                functions = functions,
                model = model
            )
            
            // Return the final chat message
            finalResponse.outputText?.let { outputText ->
                createChatMessage(outputText, isUser = false, functionCallsExecuted = executedCalls.size)
            } ?: createChatMessage("I processed your request but couldn't generate a proper response.", isUser = false)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error handling function calls", e)
            createChatMessage("I encountered an error while executing the requested functions. Please try again.", isUser = false)
        }
    }
    
    /**
     * Get relevant educational functions based on message content
     */
    private fun getRelevantEducationalFunctions(message: String): List<OpenAIFunctionCaller.FunctionDefinition> {
        val lowerMessage = message.lowercase()
        val allFunctions = educationalFunctions.getAllFunctions()
        
        // Smart function selection based on keywords
        val relevantFunctions = mutableListOf<OpenAIFunctionCaller.FunctionDefinition>()
        
        when {
            // Lesson-related keywords
            lowerMessage.contains("lesson") || lowerMessage.contains("learn about") || lowerMessage.contains("teach me") -> {
                relevantFunctions.add(allFunctions.find { it.name == "get_lesson_content" }!!)
                relevantFunctions.add(allFunctions.find { it.name == "explain_concept" }!!)
            }
            
            // Quiz/assessment keywords
            lowerMessage.contains("quiz") || lowerMessage.contains("test") || lowerMessage.contains("practice") -> {
                relevantFunctions.add(allFunctions.find { it.name == "create_practice_quiz" }!!)
                relevantFunctions.add(allFunctions.find { it.name == "assess_student_knowledge" }!!)
            }
            
            // Explanation keywords
            lowerMessage.contains("explain") || lowerMessage.contains("what is") || lowerMessage.contains("how does") -> {
                relevantFunctions.add(allFunctions.find { it.name == "explain_concept" }!!)
                relevantFunctions.add(allFunctions.find { it.name == "generate_examples" }!!)
            }
            
            // Homework keywords
            lowerMessage.contains("homework") || lowerMessage.contains("help with") || lowerMessage.contains("stuck on") -> {
                relevantFunctions.add(allFunctions.find { it.name == "get_homework_help" }!!)
                relevantFunctions.add(allFunctions.find { it.name == "assess_student_knowledge" }!!)
            }
            
            // Study plan keywords
            lowerMessage.contains("study plan") || lowerMessage.contains("how to study") || lowerMessage.contains("prepare for") -> {
                relevantFunctions.add(allFunctions.find { it.name == "create_study_plan" }!!)
                relevantFunctions.add(allFunctions.find { it.name == "search_educational_resources" }!!)
            }
            
            // Examples keywords
            lowerMessage.contains("example") || lowerMessage.contains("show me") || lowerMessage.contains("demonstrate") -> {
                relevantFunctions.add(allFunctions.find { it.name == "generate_examples" }!!)
                relevantFunctions.add(allFunctions.find { it.name == "explain_concept" }!!)
            }
            
            // Resource search keywords
            lowerMessage.contains("resources") || lowerMessage.contains("materials") || lowerMessage.contains("where can i") -> {
                relevantFunctions.add(allFunctions.find { it.name == "search_educational_resources" }!!)
            }
            
            // Default: include most common functions
            else -> {
                relevantFunctions.add(allFunctions.find { it.name == "explain_concept" }!!)
                relevantFunctions.add(allFunctions.find { it.name == "generate_examples" }!!)
                relevantFunctions.add(allFunctions.find { it.name == "get_homework_help" }!!)
            }
        }
        
        // Always include lesson content and concept explanation as they're universally useful
        if (!relevantFunctions.any { it.name == "get_lesson_content" }) {
            relevantFunctions.add(allFunctions.find { it.name == "get_lesson_content" }!!)
        }
        
        Log.d(TAG, "Selected ${relevantFunctions.size} relevant functions for message: $message")
        return relevantFunctions
    }
    
    /**
     * Check if function is an educational function
     */
    fun isEducationalFunction(functionName: String): Boolean {
        return educationalFunctions.getAllFunctions().any { it.name == functionName }
    }
    
    /**
     * Check if function is a voice command function
     */
    private fun isVoiceCommandFunction(functionName: String): Boolean {
        return functionName in listOf("set_alarm", "send_email", "set_reminder", "start_voice_chat")
    }
    
    /**
     * Execute voice command functions (integration with existing voice system)
     */
    private suspend fun executeVoiceCommand(functionName: String, arguments: String): String {
        // This would integrate with your existing voice command system
        // For now, return a success message
        return when (functionName) {
            "set_alarm" -> "Voice command processed: Alarm has been set"
            "send_email" -> "Voice command processed: Email composer opened"
            "set_reminder" -> "Voice command processed: Reminder has been created"
            "start_voice_chat" -> "Voice command processed: Voice chat session started"
            else -> "Voice command '$functionName' executed successfully"
        }
    }
    
    /**
     * Create a ChatMessage object
     */
    private fun createChatMessage(
        content: String, 
        isUser: Boolean, 
        functionCallsExecuted: Int = 0
    ): ChatMessage {
        val timestamp = System.currentTimeMillis()
        val messageId = "msg_${System.currentTimeMillis()}_${kotlin.random.Random.nextInt(1000)}"
        
        val enhancedContent = if (functionCallsExecuted > 0 && !isUser) {
            "$content\n\n✨ *Enhanced with AI function calling ($functionCallsExecuted functions executed)*"
        } else {
            content
        }
        
        return ChatMessage(
            id = messageId,
            content = enhancedContent,
            isUser = isUser,
            isTyping = false,
            timestamp = timestamp,
            containsRichContent = functionCallsExecuted > 0
        )
    }
    
    /**
     * Quick access methods for specific educational features
     */
    
    suspend fun explainConcept(concept: String, gradeLevel: String = "High School"): ChatMessage {
        return processMessageWithFunctions(
            message = "Explain the concept of $concept for $gradeLevel level students",
            includeEducationalFunctions = true,
            includeWebSearch = false
        )
    }
    
    suspend fun createQuiz(topic: String, difficulty: String = "medium", questionCount: Int = 5): ChatMessage {
        return processMessageWithFunctions(
            message = "Create a $difficulty practice quiz on $topic with $questionCount questions",
            includeEducationalFunctions = true,
            includeWebSearch = false
        )
    }
    
    suspend fun getHomeworkHelp(question: String, subject: String): ChatMessage {
        return processMessageWithFunctions(
            message = "I need help with this $subject homework question: $question",
            includeEducationalFunctions = true,
            includeWebSearch = false
        )
    }
    
    suspend fun searchResources(topic: String): ChatMessage {
        return processMessageWithFunctions(
            message = "Find educational resources for learning about $topic",
            includeEducationalFunctions = true,
            includeWebSearch = true
        )
    }
}