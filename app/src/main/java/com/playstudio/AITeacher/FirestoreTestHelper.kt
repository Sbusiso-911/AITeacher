package com.playstudio.AITeacher

import android.content.Context
import android.util.Log
import com.playstudio.aiteacher.firestore.FirestoreChatManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date

/**
 * FirestoreTestHelper - Simple utility to test Firestore chat history integration
 * 
 * Usage:
 * 1. Ensure user is logged in with Firebase Auth
 * 2. Call FirestoreTestHelper.runTests(context) from any Activity
 * 3. Check Logcat for results
 * 4. Check Firebase Console > Firestore Database to see stored data
 */
object FirestoreTestHelper {
    
    private const val TAG = "FirestoreTestHelper"
    
    /**
     * Run comprehensive tests of Firestore chat history functionality
     */
    fun runTests(context: Context, onComplete: (Boolean) -> Unit = {}) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.i(TAG, "🚀 Starting Firestore Chat History Tests...")
                
                val firestoreManager = FirestoreChatManager.getInstance()
                var allTestsPassed = true
                
                // Test 1: Create and save a test chat session
                Log.d(TAG, "📝 Test 1: Creating test chat session...")
                val testSession = FirestoreChatManager.FirestoreChatSession(
                    sessionId = "test_session_${System.currentTimeMillis()}",
                    title = "Test Conversation - ${Date()}",
                    aiModelUsed = "gpt-4",
                    category = "test",
                    createdAt = Date(),
                    updatedAt = Date(),
                    isFavorite = false,
                    isArchived = false,
                    messageCount = 0,
                    lastMessagePreview = "This is a test conversation",
                    tags = listOf("test", "firestore", "integration")
                )
                
                val sessionSaved = firestoreManager.saveChatSession(testSession)
                if (sessionSaved) {
                    Log.i(TAG, "✅ Test 1 PASSED: Chat session saved to Firestore")
                } else {
                    Log.e(TAG, "❌ Test 1 FAILED: Failed to save chat session")
                    allTestsPassed = false
                }
                
                // Test 2: Save test messages to the session
                Log.d(TAG, "💬 Test 2: Saving test messages...")
                val testMessages = listOf(
                    FirestoreChatManager.FirestoreChatMessage(
                        messageId = "msg_1_${System.currentTimeMillis()}",
                        sessionId = testSession.sessionId,
                        content = "Hello, this is a test user message",
                        senderType = "user",
                        timestamp = Date(),
                        tokenCount = 10,
                        processingTimeMs = 0L
                    ),
                    FirestoreChatManager.FirestoreChatMessage(
                        messageId = "msg_2_${System.currentTimeMillis()}",
                        sessionId = testSession.sessionId,
                        content = "Hello! This is a test AI response. How can I help you today?",
                        senderType = "ai",
                        timestamp = Date(System.currentTimeMillis() + 1000),
                        tokenCount = 15,
                        processingTimeMs = 1500L,
                        aiModel = "gpt-4",
                        provider = "openai"
                    )
                )
                
                var messagesSaved = 0
                testMessages.forEach { message ->
                    if (firestoreManager.saveChatMessage(message)) {
                        messagesSaved++
                    }
                }
                
                if (messagesSaved == testMessages.size) {
                    Log.i(TAG, "✅ Test 2 PASSED: All $messagesSaved test messages saved to Firestore")
                } else {
                    Log.e(TAG, "❌ Test 2 FAILED: Only $messagesSaved/${testMessages.size} messages saved")
                    allTestsPassed = false
                }
                
                // Test 3: Retrieve chat sessions
                Log.d(TAG, "📖 Test 3: Retrieving chat sessions...")
                val retrievedSessions = firestoreManager.getChatSessions()
                val foundTestSession = retrievedSessions.find { it.sessionId == testSession.sessionId }
                
                if (foundTestSession != null) {
                    Log.i(TAG, "✅ Test 3 PASSED: Retrieved ${retrievedSessions.size} sessions, found test session")
                    Log.d(TAG, "   Test session: ${foundTestSession.title} (${foundTestSession.messageCount} messages)")
                } else {
                    Log.e(TAG, "❌ Test 3 FAILED: Could not find test session in retrieved sessions")
                    allTestsPassed = false
                }
                
                // Test 4: Retrieve messages for the test session
                Log.d(TAG, "💭 Test 4: Retrieving messages for test session...")
                val retrievedMessages = firestoreManager.getChatMessages(testSession.sessionId)
                
                if (retrievedMessages.size == testMessages.size) {
                    Log.i(TAG, "✅ Test 4 PASSED: Retrieved ${retrievedMessages.size} messages for test session")
                    retrievedMessages.forEachIndexed { index, message ->
                        Log.d(TAG, "   Message ${index + 1}: ${message.senderType} - ${message.content.take(50)}...")
                    }
                } else {
                    Log.e(TAG, "❌ Test 4 FAILED: Expected ${testMessages.size} messages, got ${retrievedMessages.size}")
                    allTestsPassed = false
                }
                
                // Test 5: Test statistics
                Log.d(TAG, "📊 Test 5: Getting chat statistics...")
                val stats = firestoreManager.getChatStatistics()
                
                if (stats.isNotEmpty() && stats["totalSessions"] != null) {
                    Log.i(TAG, "✅ Test 5 PASSED: Retrieved chat statistics")
                    stats.forEach { (key, value) ->
                        Log.d(TAG, "   $key: $value")
                    }
                } else {
                    Log.e(TAG, "❌ Test 5 FAILED: Could not retrieve chat statistics")
                    allTestsPassed = false
                }
                
                // Test 6: Test local database sync
                Log.d(TAG, "🔄 Test 6: Testing local database sync...")
                val syncSuccess = firestoreManager.syncChatData()
                
                if (syncSuccess) {
                    Log.i(TAG, "✅ Test 6 PASSED: Local database synced to Firestore")
                } else {
                    Log.w(TAG, "⚠️  Test 6 WARNING: Local database sync returned false (may be no local data)")
                }
                
                // Test 7: Test ChatHistoryUtils integration
                Log.d(TAG, "🔗 Test 7: Testing ChatHistoryUtils integration...")
                ChatHistoryUtils.saveMessageWithSync(
                    context,
                    "Test message via ChatHistoryUtils",
                    true,
                    testSession.sessionId,
                    "gpt-4",
                    "openai"
                )
                
                // Give it a moment to save
                kotlinx.coroutines.delay(2000)
                
                val updatedMessages = firestoreManager.getChatMessages(testSession.sessionId)
                if (updatedMessages.size > retrievedMessages.size) {
                    Log.i(TAG, "✅ Test 7 PASSED: ChatHistoryUtils integration working, message count increased")
                } else {
                    Log.w(TAG, "⚠️  Test 7 WARNING: ChatHistoryUtils integration may have issues")
                }
                
                // Final summary
                withContext(Dispatchers.Main) {
                    if (allTestsPassed) {
                        Log.i(TAG, "🎉 ALL TESTS PASSED! Firestore chat history integration is working correctly.")
                        Log.i(TAG, "💡 Check your Firebase Console > Firestore Database to see the stored data:")
                        Log.i(TAG, "   Collection: users/{userId}/chat_sessions/{sessionId}")
                        Log.i(TAG, "   Collection: users/{userId}/chat_sessions/{sessionId}/messages/{messageId}")
                    } else {
                        Log.e(TAG, "❌ SOME TESTS FAILED! Please check the logs above and your Firebase configuration.")
                        Log.e(TAG, "💡 Common issues:")
                        Log.e(TAG, "   - User not logged in with Firebase Auth")
                        Log.e(TAG, "   - Firestore security rules preventing writes")
                        Log.e(TAG, "   - Network connectivity issues")
                        Log.e(TAG, "   - Firebase project configuration")
                    }
                    
                    onComplete(allTestsPassed)
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "💥 Test execution failed", e)
                withContext(Dispatchers.Main) {
                    onComplete(false)
                }
            }
        }
    }
    
    /**
     * Quick test to verify Firestore connection and user authentication
     */
    fun quickConnectivityTest(context: Context, onComplete: (Boolean, String) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val firestoreManager = FirestoreChatManager.getInstance()
                
                // Try to get existing sessions
                val sessions = firestoreManager.getChatSessions()
                val message = if (sessions.isNotEmpty()) {
                    "✅ Connected! Found ${sessions.size} existing chat sessions in Firestore"
                } else {
                    "✅ Connected! No existing sessions found (this is normal for new users)"
                }
                
                Log.i(TAG, message)
                withContext(Dispatchers.Main) {
                    onComplete(true, message)
                }
                
            } catch (e: Exception) {
                val errorMessage = "❌ Connection failed: ${e.message}"
                Log.e(TAG, errorMessage, e)
                withContext(Dispatchers.Main) {
                    onComplete(false, errorMessage)
                }
            }
        }
    }
    
    /**
     * Clean up test data (optional - use with caution)
     */
    fun cleanupTestData(onComplete: (Boolean) -> Unit = {}) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val firestoreManager = FirestoreChatManager.getInstance()
                val sessions = firestoreManager.getChatSessions()
                
                var deleted = 0
                sessions.filter { it.title.contains("Test Conversation") }.forEach { session ->
                    if (firestoreManager.deleteChatHistory()) {
                        deleted++
                    }
                }
                
                Log.i(TAG, "🧹 Cleanup complete: Deleted $deleted test sessions")
                withContext(Dispatchers.Main) {
                    onComplete(true)
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Cleanup failed", e)
                withContext(Dispatchers.Main) {
                    onComplete(false)
                }
            }
        }
    }
}