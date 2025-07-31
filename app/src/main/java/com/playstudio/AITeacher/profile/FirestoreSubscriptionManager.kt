package com.playstudio.aiteacher.profile

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import java.util.*

/**
 * Firestore-based subscription manager that requires Firebase authentication
 * Replaces SharedPreferences and local database storage for subscriptions
 */
class FirestoreSubscriptionManager(private val context: Context) {
    
    private val firestore = FirebaseFirestore.getInstance()
    private val firebaseAuth = FirebaseAuth.getInstance()
    
    companion object {
        private const val TAG = "FirestoreSubscriptionManager"
        private const val SUBSCRIPTIONS_COLLECTION = "subscriptions"
        private const val USER_SUBSCRIPTIONS_COLLECTION = "users"
    }
    
    data class SubscriptionData(
        val userId: String = "",
        val planType: String = "free",
        val status: String = "inactive", // active, inactive, expired, cancelled
        val startDate: Long = 0L,
        val endDate: Long = 0L,
        val billingCycle: String = "monthly", // monthly, yearly
        val pricePaid: Double = 0.0,
        val currency: String = "USD",
        val autoRenew: Boolean = false,
        val trialEndDate: Long? = null,
        val productId: String? = null,
        val orderId: String? = null,
        val purchaseToken: String? = null,
        val features: List<String> = emptyList(),
        val createdAt: Long = System.currentTimeMillis(),
        val updatedAt: Long = System.currentTimeMillis()
    )
    
    /**
     * Check if user is authenticated before any subscription operation
     */
    private fun requireAuthentication(): String {
        val currentUser = firebaseAuth.currentUser
        if (currentUser == null) {
            throw IllegalStateException("User must be authenticated to manage subscriptions")
        }
        return currentUser.uid
    }
    
    /**
     * Save subscription to Firestore (replaces SharedPreferences)
     */
    suspend fun saveSubscription(subscriptionData: SubscriptionData): Boolean {
        return try {
            val userId = requireAuthentication()
            val dataWithUserId = subscriptionData.copy(userId = userId)
            
            // Save to both collections for redundancy and different access patterns
            // 1. User-specific subscription document
            firestore.collection(USER_SUBSCRIPTIONS_COLLECTION)
                .document(userId)
                .collection("subscriptions")
                .document("current")
                .set(dataWithUserId, SetOptions.merge())
                .await()
            
            // 2. Root-level subscriptions collection for admin access
            firestore.collection(SUBSCRIPTIONS_COLLECTION)
                .document(userId)
                .set(dataWithUserId, SetOptions.merge())
                .await()
            
            Log.d(TAG, "Subscription saved successfully to Firestore for user: $userId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error saving subscription to Firestore", e)
            false
        }
    }
    
    /**
     * Retrieve subscription from Firestore (replaces SharedPreferences)
     */
    suspend fun getSubscription(): SubscriptionData? {
        return try {
            val userId = requireAuthentication()
            
            // Try user-specific collection first
            val userDoc = firestore.collection(USER_SUBSCRIPTIONS_COLLECTION)
                .document(userId)
                .collection("subscriptions")
                .document("current")
                .get()
                .await()
            
            if (userDoc.exists()) {
                userDoc.toObject(SubscriptionData::class.java)
            } else {
                // Fallback to root-level collection
                val rootDoc = firestore.collection(SUBSCRIPTIONS_COLLECTION)
                    .document(userId)
                    .get()
                    .await()
                
                if (rootDoc.exists()) {
                    rootDoc.toObject(SubscriptionData::class.java)
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error retrieving subscription from Firestore", e)
            null
        }
    }
    
    /**
     * Check if user has active subscription
     */
    suspend fun hasActiveSubscription(): Boolean {
        return try {
            val subscription = getSubscription()
            subscription?.let {
                it.status == "active" && it.endDate > System.currentTimeMillis()
            } ?: false
        } catch (e: Exception) {
            Log.e(TAG, "Error checking active subscription", e)
            false
        }
    }
    
    /**
     * Get subscription status with detailed info
     */
    suspend fun getSubscriptionStatus(): SubscriptionStatus {
        return try {
            val userId = requireAuthentication()
            Log.d(TAG, "Getting subscription status for user: $userId")
            
            val subscription = getSubscription()
            if (subscription == null) {
                Log.d(TAG, "No subscription found, creating default free subscription document")
                // Create default free subscription document
                createDefaultSubscription()
                
                SubscriptionStatus(
                    isActive = false,
                    planType = "free",
                    daysRemaining = 0,
                    isExpired = false,
                    features = emptyList()
                )
            } else {
                Log.d(TAG, "Found subscription: planType=${subscription.planType}, status=${subscription.status}")
                val currentTime = System.currentTimeMillis()
                val isActive = subscription.status == "active" && subscription.endDate > currentTime
                val isExpired = subscription.endDate <= currentTime
                val daysRemaining = if (isActive) {
                    ((subscription.endDate - currentTime) / (24 * 60 * 60 * 1000)).toInt()
                } else 0
                
                SubscriptionStatus(
                    isActive = isActive,
                    planType = subscription.planType,
                    daysRemaining = daysRemaining,
                    isExpired = isExpired,
                    features = subscription.features,
                    subscription = subscription
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting subscription status", e)
            SubscriptionStatus(
                isActive = false,
                planType = "free",
                daysRemaining = 0,
                isExpired = false,
                features = emptyList()
            )
        }
    }
    
    /**
     * Create default free subscription document for new users
     */
    private suspend fun createDefaultSubscription() {
        try {
            val userId = requireAuthentication()
            val defaultSubscription = SubscriptionData(
                userId = userId,
                planType = "free",
                status = "inactive",
                startDate = System.currentTimeMillis(),
                endDate = 0L,
                billingCycle = "none",
                pricePaid = 0.0,
                currency = "USD",
                autoRenew = false,
                features = emptyList(),
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            
            val saved = saveSubscription(defaultSubscription)
            if (saved) {
                Log.d(TAG, "Default free subscription created for user: $userId")
            } else {
                Log.e(TAG, "Failed to create default subscription for user: $userId")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating default subscription", e)
        }
    }
    
    /**
     * Purchase subscription (requires authentication)
     */
    suspend fun purchaseSubscription(
        planType: String,
        billingCycle: String,
        pricePaid: Double,
        productId: String,
        orderId: String,
        purchaseToken: String
    ): Boolean {
        return try {
            val userId = requireAuthentication()
            
            val durationMonths = if (billingCycle == "yearly") 12 else 1
            val startDate = System.currentTimeMillis()
            val endDate = Calendar.getInstance().apply {
                timeInMillis = startDate
                add(Calendar.MONTH, durationMonths)
            }.timeInMillis
            
            val features = when (planType) {
                "pro" -> listOf(
                    "Advanced AI models", "Priority response", "Voice messages", 
                    "Image processing", "Email support"
                )
                "premium" -> listOf(
                    "All AI models", "Fastest response", "Advanced voice features",
                    "Advanced image processing", "Priority support", "Offline access"
                )
                else -> listOf("Basic AI models", "Standard response")
            }
            
            val subscriptionData = SubscriptionData(
                userId = userId,
                planType = planType,
                status = "active",
                startDate = startDate,
                endDate = endDate,
                billingCycle = billingCycle,
                pricePaid = pricePaid,
                autoRenew = true,
                productId = productId,
                orderId = orderId,
                purchaseToken = purchaseToken,
                features = features
            )
            
            saveSubscription(subscriptionData)
        } catch (e: Exception) {
            Log.e(TAG, "Error purchasing subscription", e)
            false
        }
    }
    
    /**
     * Cancel subscription
     */
    suspend fun cancelSubscription(): Boolean {
        return try {
            val subscription = getSubscription()
            if (subscription != null) {
                val cancelledSubscription = subscription.copy(
                    status = "cancelled",
                    autoRenew = false,
                    updatedAt = System.currentTimeMillis()
                )
                saveSubscription(cancelledSubscription)
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling subscription", e)
            false
        }
    }
    
    /**
     * Renew subscription
     */
    suspend fun renewSubscription(): Boolean {
        return try {
            val subscription = getSubscription()
            if (subscription != null) {
                val durationMonths = if (subscription.billingCycle == "yearly") 12 else 1
                val newEndDate = Calendar.getInstance().apply {
                    timeInMillis = subscription.endDate
                    add(Calendar.MONTH, durationMonths)
                }.timeInMillis
                
                val renewedSubscription = subscription.copy(
                    status = "active",
                    endDate = newEndDate,
                    updatedAt = System.currentTimeMillis()
                )
                saveSubscription(renewedSubscription)
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error renewing subscription", e)
            false
        }
    }
    
    /**
     * Clear subscription data (for testing or logout)
     */
    suspend fun clearSubscription(): Boolean {
        return try {
            val userId = requireAuthentication()
            
            // Delete from both collections
            firestore.collection(USER_SUBSCRIPTIONS_COLLECTION)
                .document(userId)
                .collection("subscriptions")
                .document("current")
                .delete()
                .await()
            
            firestore.collection(SUBSCRIPTIONS_COLLECTION)
                .document(userId)
                .delete()
                .await()
            
            Log.d(TAG, "Subscription cleared successfully from Firestore")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing subscription from Firestore", e)
            false
        }
    }
    
    data class SubscriptionStatus(
        val isActive: Boolean,
        val planType: String,
        val daysRemaining: Int,
        val isExpired: Boolean,
        val features: List<String>,
        val subscription: SubscriptionData? = null
    )
}