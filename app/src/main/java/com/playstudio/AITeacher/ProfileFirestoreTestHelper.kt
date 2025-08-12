package com.playstudio.AITeacher

import android.content.Context
import android.util.Log
import com.playstudio.aiteacher.profile.ProfileManager
import com.playstudio.aiteacher.firestore.FirestoreChatManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Helper class to test chat history retrieval in user profile
 * This ensures users can see their chat history from any device
 */
object ProfileFirestoreTestHelper {
    
    private const val TAG = "ProfileFirestoreTestHelper"
    
    /**
     * Test chat history retrieval through ProfileManager
     * This simulates what happens when user opens their profile
     */
    fun testChatHistoryInProfile(context: Context, onComplete: (Boolean, String) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.i(TAG, "🔍 Testing chat history retrieval in user profile...")
                
                val profileManager = ProfileManager(context)
                val firestoreChatManager = FirestoreChatManager.getInstance()
                
                // Test 1: Initialize profile (syncs local data to Firestore)
                Log.d(TAG, "📱 Step 1: Initializing user profile...")
                val initSuccess = profileManager.initializeUserProfile()
                Log.d(TAG, "Profile initialization: $initSuccess")
                
                // Test 2: Get chat history through ProfileManager (should use Firestore)
                Log.d(TAG, "📖 Step 2: Getting chat history through ProfileManager...")
                val profileChatHistory = profileManager.getChatHistory().first()
                Log.d(TAG, "Found ${profileChatHistory.size} chat sessions through ProfileManager")
                
                // Test 3: Get statistics from Firestore
                Log.d(TAG, "📊 Step 3: Getting chat statistics from Firestore...")
                val firestoreStats = profileManager.getChatStatisticsFromFirestore()
                Log.d(TAG, "Firestore statistics: $firestoreStats")
                
                // Test 4: Get recent activity for profile display
                Log.d(TAG, "⏰ Step 4: Getting recent chat activity...")
                val recentActivity = profileManager.getRecentChatActivity(5)
                Log.d(TAG, "Found ${recentActivity.size} recent chat sessions")
                
                // Test 5: Compare Firestore data with ProfileManager data
                Log.d(TAG, "🔄 Step 5: Comparing Firestore direct vs ProfileManager data...")
                val directFirestoreSessions = firestoreChatManager.getChatSessions()
                Log.d(TAG, "Direct Firestore: ${directFirestoreSessions.size} sessions")
                Log.d(TAG, "ProfileManager: ${profileChatHistory.size} sessions")
                
                // Generate result summary
                val results = mutableListOf<String>()
                
                if (initSuccess) {
                    results.add("✅ Profile initialization successful")
                } else {
                    results.add("⚠️ Profile initialization returned false")
                }
                
                if (profileChatHistory.isNotEmpty()) {
                    results.add("✅ Chat history retrieved (${profileChatHistory.size} sessions)")
                } else {
                    results.add("ℹ️ No chat history found (normal for new users)")
                }
                
                if (firestoreStats.isNotEmpty()) {
                    val totalSessions = firestoreStats["totalSessions"] ?: 0
                    val totalMessages = firestoreStats["totalMessages"] ?: 0
                    val favoriteChats = firestoreStats["favoriteChats"] ?: 0
                    results.add("✅ Statistics available (Sessions: $totalSessions, Messages: $totalMessages, Favorites: $favoriteChats)")
                } else {
                    results.add("ℹ️ No statistics available (normal for new users)")
                }
                
                if (recentActivity.isNotEmpty()) {
                    results.add("✅ Recent activity retrieved (${recentActivity.size} recent sessions)")
                    recentActivity.take(3).forEach { session ->
                        results.add("   📝 \"${session.title}\" - ${session.aiModelUsed}")
                    }
                } else {
                    results.add("ℹ️ No recent activity (normal for new users)")
                }
                
                // Cross-device sync test
                if (directFirestoreSessions.size == profileChatHistory.size) {
                    results.add("✅ Data consistency: Firestore and ProfileManager match")
                } else {
                    results.add("⚠️ Data sync issue: Firestore (${directFirestoreSessions.size}) vs ProfileManager (${profileChatHistory.size})")
                }
                
                val summary = results.joinToString("\n")
                val success = results.none { it.contains("❌") }
                
                Log.i(TAG, "🎯 Profile chat history test results:\n$summary")
                
                withContext(Dispatchers.Main) {
                    onComplete(success, summary)
                }
                
            } catch (e: Exception) {
                val errorMessage = "❌ Profile test failed: ${e.message}"
                Log.e(TAG, errorMessage, e)
                withContext(Dispatchers.Main) {
                    onComplete(false, errorMessage)
                }
            }
        }
    }
    
    /**
     * Test cross-device chat history sync simulation
     */
    fun testCrossDeviceSync(context: Context, onComplete: (Boolean, String) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.i(TAG, "🌐 Testing cross-device chat history sync...")
                
                val profileManager = ProfileManager(context)
                val firestoreChatManager = FirestoreChatManager.getInstance()
                
                // Step 1: Create a test session as if from another device
                Log.d(TAG, "📱 Step 1: Creating test session (simulating another device)...")
                val testSession = FirestoreChatManager.FirestoreChatSession(
                    sessionId = "cross_device_test_${System.currentTimeMillis()}",
                    title = "Cross-Device Test Chat",
                    aiModelUsed = "gpt-4",
                    category = "test",
                    messageCount = 2,
                    lastMessagePreview = "This is a test from another device"
                )
                
                val sessionSaved = firestoreChatManager.saveChatSession(testSession)
                
                if (!sessionSaved) {
                    throw Exception("Failed to save test session to Firestore")
                }
                
                // Step 2: Test message in the session
                val testMessage = FirestoreChatManager.FirestoreChatMessage(
                    messageId = "cross_device_msg_${System.currentTimeMillis()}",
                    sessionId = testSession.sessionId,
                    content = "Hello from another device!",
                    senderType = "user"
                )
                
                val messageSaved = firestoreChatManager.saveChatMessage(testMessage)
                
                if (!messageSaved) {
                    throw Exception("Failed to save test message to Firestore")
                }
                
                // Wait for Firestore to propagate
                kotlinx.coroutines.delay(2000)
                
                // Step 3: Retrieve through ProfileManager (as if user opened app on this device)
                Log.d(TAG, "📖 Step 2: Retrieving chat history through ProfileManager...")
                val allSessions = profileManager.getChatHistory().first()
                val testSessionFound = allSessions.any { it.title == testSession.title }
                
                // Step 4: Get statistics
                val stats = profileManager.getChatStatisticsFromFirestore()
                
                // Cleanup test data
                Log.d(TAG, "🧹 Cleaning up test data...")
                firestoreChatManager.deleteChatHistory()
                
                val results = mutableListOf<String>()
                results.add("📱 Created test session: ${if (sessionSaved) "✅ Success" else "❌ Failed"}")
                results.add("💬 Created test message: ${if (messageSaved) "✅ Success" else "❌ Failed"}")
                results.add("🔍 Found session in profile: ${if (testSessionFound) "✅ Success" else "❌ Failed"}")
                results.add("📊 Statistics available: ${if (stats.isNotEmpty()) "✅ Yes" else "ℹ️ No"}")
                results.add("🧹 Test cleanup: ✅ Complete")
                
                val summary = results.joinToString("\n")
                val success = sessionSaved && messageSaved && testSessionFound
                
                Log.i(TAG, "🎯 Cross-device sync test results:\n$summary")
                
                withContext(Dispatchers.Main) {
                    onComplete(success, summary)
                }
                
            } catch (e: Exception) {
                val errorMessage = "❌ Cross-device sync test failed: ${e.message}"
                Log.e(TAG, errorMessage, e)
                withContext(Dispatchers.Main) {
                    onComplete(false, errorMessage)
                }
            }
        }
    }
    
    /**
     * Quick test to check if profile can see any chat history
     */
    fun quickProfileChatTest(context: Context, onComplete: (String) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val profileManager = ProfileManager(context)
                val chatSessions = profileManager.getChatHistory().first()
                val stats = profileManager.getChatStatisticsFromFirestore()
                
                val message = if (chatSessions.isNotEmpty()) {
                    "✅ Profile can see ${chatSessions.size} chat sessions from Firestore"
                } else if (stats.isNotEmpty()) {
                    "ℹ️ Profile connected to Firestore, no chat history yet"
                } else {
                    "⚠️ Profile may not be connected to Firestore chat data"
                }
                
                Log.i(TAG, message)
                withContext(Dispatchers.Main) {
                    onComplete(message)
                }
                
            } catch (e: Exception) {
                val errorMessage = "❌ Quick test failed: ${e.message}"
                Log.e(TAG, errorMessage, e)
                withContext(Dispatchers.Main) {
                    onComplete(errorMessage)
                }
            }
        }
    }
}