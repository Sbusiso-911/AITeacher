package com.playstudio.aiteacher.profile

import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity

/**
 * Integration helper for the profile system
 * This class provides easy integration points for the existing app
 */
class ProfileIntegration {
    
    companion object {
        /**
         * Check if user is logged in
         * @param context Application context
         * @return true if user is logged in, false otherwise
         */
        fun isUserLoggedIn(context: Context): Boolean {
            return AuthenticationService(context).isLoggedIn()
        }
        
        /**
         * Get the current user
         * @param context Application context
         * @return UserEntity if logged in, null otherwise
         */
        suspend fun getCurrentUser(context: Context): UserEntity? {
            return AuthenticationService(context).getCurrentUser()
        }
        
        /**
         * Check if user has a specific subscription feature
         * @param context Application context
         * @param feature Feature name (e.g., "elite_tools", "voice_features")
         * @return true if user has the feature, false otherwise
         */
        suspend fun hasSubscriptionFeature(context: Context, feature: String): Boolean {
            return SubscriptionManager(context).hasFeature(feature)
        }
        
        /**
         * Check if user can send more messages (within usage limits)
         * @param context Application context
         * @return true if user can send messages, false if limit reached
         */
        suspend fun canSendMessage(context: Context): Boolean {
            return SubscriptionManager(context).canSendMessage()
        }
        
        /**
         * Check if user can use tokens (within usage limits)
         * @param context Application context
         * @param tokensNeeded Number of tokens needed
         * @return true if user can use tokens, false if limit reached
         */
        suspend fun canUseTokens(context: Context, tokensNeeded: Int): Boolean {
            return SubscriptionManager(context).canUseTokens(tokensNeeded)
        }
        
        /**
         * Record usage for analytics
         * @param context Application context
         * @param messagesSent Number of messages sent
         * @param tokensConsumed Number of tokens consumed
         * @param featuresUsed List of features used
         * @param eliteToolsUsed Map of elite tools used
         */
        suspend fun recordUsage(
            context: Context,
            messagesSent: Int = 0,
            tokensConsumed: Int = 0,
            featuresUsed: List<String> = emptyList(),
            eliteToolsUsed: Map<String, Int> = emptyMap()
        ) {
            SubscriptionManager(context).recordUsage(
                messagesSent = messagesSent,
                tokensConsumed = tokensConsumed,
                featuresUsed = featuresUsed,
                eliteToolsUsed = eliteToolsUsed
            )
        }
        
        /**
         * Navigate to login screen
         * @param activity Current activity
         */
        fun navigateToLogin(activity: AppCompatActivity) {
            val intent = Intent(activity, LoginActivity::class.java)
            activity.startActivity(intent)
        }
        
        /**
         * Navigate to profile screen
         * @param activity Current activity
         */
        fun navigateToProfile(activity: AppCompatActivity) {
            val intent = Intent(activity, ProfileActivity::class.java)
            activity.startActivity(intent)
        }
        
        /**
         * Navigate to chat history screen
         * @param activity Current activity
         */
        fun navigateToChatHistory(activity: AppCompatActivity) {
            val intent = Intent(activity, ChatHistoryActivity::class.java)
            activity.startActivity(intent)
        }
        
        /**
         * Navigate to subscription screen
         * @param activity Current activity
         */
        fun navigateToSubscription(activity: AppCompatActivity) {
            val intent = Intent(activity, SubscriptionActivity::class.java)
            activity.startActivity(intent)
        }
        
        /**
         * Show login required dialog
         * @param activity Current activity
         * @param message Optional message to show
         */
        fun showLoginRequired(activity: AppCompatActivity, message: String = "Please log in to continue") {
            val dialog = androidx.appcompat.app.AlertDialog.Builder(activity, com.playstudio.aiteacher.R.style.BlueDialogTheme)
                .setTitle("Login Required")
                .setMessage(message)
                .setPositiveButton("Login") { _, _ ->
                    navigateToLogin(activity)
                }
                .setNegativeButton("Cancel", null)
                .create()
            dialog.show()
        }
        
        /**
         * Show subscription required dialog
         * @param activity Current activity
         * @param feature Feature name that requires subscription
         */
        fun showSubscriptionRequired(activity: AppCompatActivity, feature: String) {
            val dialog = androidx.appcompat.app.AlertDialog.Builder(activity, com.playstudio.aiteacher.R.style.BlueDialogTheme)
                .setTitle("Premium Feature")
                .setMessage("$feature requires a premium subscription. Would you like to upgrade?")
                .setPositiveButton("Upgrade") { _, _ ->
                    navigateToSubscription(activity)
                }
                .setNegativeButton("Cancel", null)
                .create()
            dialog.show()
        }
    }
}