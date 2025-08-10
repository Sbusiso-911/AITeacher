package com.playstudio.aiteacher.billing

import android.content.Context
import android.util.Log
import com.android.billingclient.api.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.playstudio.aiteacher.profile.FirestoreSubscriptionManager
import com.playstudio.aiteacher.profile.FirebaseAuthenticationService
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import java.util.*
import kotlin.coroutines.resume

/**
 * Google Play Billing to Firestore Sync Service
 * 
 * This service handles:
 * 1. Querying Google Play Billing for active subscriptions
 * 2. Syncing subscription data to Firestore
 * 3. Ensuring ProfileActivity gets data from Firestore (not directly from billing)
 * 4. Fixing "Service connection is disconnected" errors by using Firestore as display source
 */
class GooglePlayBillingSync(private val context: Context) : PurchasesUpdatedListener {
    
    private lateinit var billingClient: BillingClient
    private val firestore = FirebaseFirestore.getInstance()
    private val firebaseAuth = FirebaseAuth.getInstance()
    private val firestoreSubscriptionManager = FirestoreSubscriptionManager(context)
    private val firebaseAuthService = FirebaseAuthenticationService(context)
    
    companion object {
        private const val TAG = "GooglePlayBillingSync"
        
        // Subscription product IDs - UPDATED to match Google Play Console setup
        private val SUBSCRIPTION_PRODUCT_IDS = listOf(
            "basic_monthly_subscription",
            "premium_monthly_subscription",
            "pro_monthly_plan",
            "ultra_monthly_subscription"
        )
        
        // Product ID to plan type mapping
        private val PRODUCT_ID_TO_PLAN_TYPE = mapOf(
            "basic_monthly_subscription" to "basic",
            "premium_monthly_subscription" to "premium",
            "pro_monthly_plan" to "pro",
            "ultra_monthly_subscription" to "ultra_premium"
        )
        
        private val PRODUCT_ID_TO_BILLING_CYCLE = mapOf(
            "basic_monthly_subscription" to "monthly",
            "premium_monthly_subscription" to "monthly",
            "pro_monthly_plan" to "monthly",
            "ultra_monthly_subscription" to "monthly"
        )
    }
    
    /**
     * Initialize billing client for sync operations
     */
    suspend fun initializeBillingClient(): Boolean {
        return suspendCancellableCoroutine { continuation ->
            try {
                billingClient = BillingClient.newBuilder(context)
                    .setListener(this)
                    .enablePendingPurchases()
                    .build()
                
                billingClient.startConnection(object : BillingClientStateListener {
                    override fun onBillingSetupFinished(billingResult: BillingResult) {
                        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                            Log.d(TAG, "Billing client initialized successfully")
                            continuation.resume(true)
                        } else {
                            Log.e(TAG, "Failed to initialize billing client: ${billingResult.debugMessage}")
                            continuation.resume(false)
                        }
                    }
                    
                    override fun onBillingServiceDisconnected() {
                        Log.w(TAG, "Billing service disconnected")
                        continuation.resume(false)
                    }
                })
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing billing client", e)
                continuation.resume(false)
            }
        }
    }
    
    /**
     * Main sync method - call this to sync Google Play Billing with Firestore
     * This should be called:
     * - On app launch
     * - When user opens ProfileActivity
     * - After successful purchases
     * - Periodically (e.g., daily background sync)
     */
    suspend fun syncSubscriptionToFirestore(): Boolean {
        return try {
            // Check if user is authenticated
            if (!firebaseAuthService.isSignedIn()) {
                Log.w(TAG, "User not authenticated, cannot sync subscription")
                return false
            }
            
            val userId = firebaseAuth.currentUser?.uid
            if (userId == null) {
                Log.e(TAG, "No Firebase UID available for sync")
                return false
            }
            
            // Initialize billing client if needed
            if (!::billingClient.isInitialized || !billingClient.isReady) {
                val initialized = initializeBillingClient()
                if (!initialized) {
                    Log.e(TAG, "Could not initialize billing client for sync")
                    return false
                }
            }
            
            Log.d(TAG, "Starting subscription sync for user: $userId")
            
            // Query Google Play Billing for active subscriptions
            Log.d(TAG, "Querying Google Play Billing for active subscriptions...")
            val activeSubscriptions = queryActiveSubscriptions()
            Log.d(TAG, "Google Play Billing query completed, found ${activeSubscriptions.size} active subscriptions")
            
            if (activeSubscriptions.isEmpty()) {
                Log.d(TAG, "No active subscriptions found in Google Play Billing - syncing free tier to Firestore")
                // Sync free tier status to Firestore
                syncFreeTierToFirestore(userId)
                return true
            } else {
                Log.d(TAG, "Found ${activeSubscriptions.size} active subscription(s) in Google Play Billing:")
                activeSubscriptions.forEach { purchase ->
                    Log.d(TAG, "  - Product IDs: ${purchase.products}, State: ${purchase.purchaseState}, Acknowledged: ${purchase.isAcknowledged}")
                }
                // Sync active subscriptions to Firestore
                return syncActiveSubscriptionsToFirestore(userId, activeSubscriptions)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during subscription sync", e)
            false
        }
    }
    
    /**
     * Query Google Play Billing for active subscriptions
     */
    private suspend fun queryActiveSubscriptions(): List<Purchase> {
        return suspendCancellableCoroutine { continuation ->
            try {
                val params = QueryPurchasesParams.newBuilder()
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build()
                
                billingClient.queryPurchasesAsync(params) { billingResult, purchases ->
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        // Filter for active, acknowledged subscriptions
                        val activeSubscriptions = purchases.filter { purchase ->
                            purchase.purchaseState == Purchase.PurchaseState.PURCHASED &&
                            purchase.isAcknowledged &&
                            SUBSCRIPTION_PRODUCT_IDS.any { productId ->
                                purchase.products.contains(productId)
                            }
                        }
                        
                        Log.d(TAG, "Found ${activeSubscriptions.size} active subscriptions from ${purchases.size} total purchases")
                        continuation.resume(activeSubscriptions)
                    } else {
                        Log.e(TAG, "Failed to query purchases: ${billingResult.debugMessage}")
                        continuation.resume(emptyList())
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error querying active subscriptions", e)
                continuation.resume(emptyList())
            }
        }
    }
    
    /**
     * Sync active subscriptions from Google Play to Firestore
     */
    private suspend fun syncActiveSubscriptionsToFirestore(
        userId: String, 
        activeSubscriptions: List<Purchase>
    ): Boolean {
        return try {
            // Get the most recent/highest-tier subscription
            val primarySubscription = getPrimarySubscription(activeSubscriptions)
            
            if (primarySubscription == null) {
                Log.w(TAG, "No valid primary subscription found")
                return false
            }
            
            val productId = primarySubscription.products.firstOrNull { 
                SUBSCRIPTION_PRODUCT_IDS.contains(it) 
            }
            
            if (productId == null) {
                Log.e(TAG, "No valid product ID found in subscription")
                return false
            }
            
            val planType = PRODUCT_ID_TO_PLAN_TYPE[productId] ?: "free"
            val billingCycle = PRODUCT_ID_TO_BILLING_CYCLE[productId] ?: "monthly"
            
            // Calculate subscription end date based on purchase time and billing cycle
            val purchaseTime = primarySubscription.purchaseTime
            val endDate = calculateSubscriptionEndDate(purchaseTime, billingCycle)
            
            // Get features based on plan type
            val features = getFeaturesByPlanType(planType)
            
            // Create subscription data for Firestore
            val subscriptionData = FirestoreSubscriptionManager.SubscriptionData(
                userId = userId,
                planType = planType,
                status = "active",
                startDate = purchaseTime,
                endDate = endDate,
                billingCycle = billingCycle,
                pricePaid = 0.0, // We don't get price from Google Play API
                currency = "USD", // Default, could be enhanced to get actual currency
                autoRenew = primarySubscription.isAutoRenewing,
                productId = productId,
                orderId = primarySubscription.orderId,
                purchaseToken = primarySubscription.purchaseToken,
                features = features,
                createdAt = purchaseTime,
                updatedAt = System.currentTimeMillis()
            )
            
            // Save to Firestore
            val success = firestoreSubscriptionManager.saveSubscription(subscriptionData)
            
            if (success) {
                Log.d(TAG, "Successfully synced active subscription to Firestore: planType=$planType, endDate=${Date(endDate)}")
                
                // Also update user's subscription tier in their profile
                updateUserSubscriptionTier(userId, planType)
                
                return true
            } else {
                Log.e(TAG, "Failed to save subscription to Firestore")
                return false
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing active subscriptions to Firestore", e)
            false
        }
    }
    
    /**
     * Sync free tier status to Firestore when no active subscriptions
     */
    private suspend fun syncFreeTierToFirestore(userId: String) {
        try {
            Log.d(TAG, "Creating free tier subscription data for user: $userId")
            
            val freeSubscriptionData = FirestoreSubscriptionManager.SubscriptionData(
                userId = userId,
                planType = "free",
                status = "inactive",
                startDate = System.currentTimeMillis(),
                endDate = 0L,
                billingCycle = "none",
                pricePaid = 0.0,
                currency = "USD",
                autoRenew = false,
                features = getFeaturesByPlanType("free"),
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            
            Log.d(TAG, "Saving free tier subscription data to Firestore...")
            val success = firestoreSubscriptionManager.saveSubscription(freeSubscriptionData)
            
            if (success) {
                Log.d(TAG, "Successfully synced free tier status to Firestore for user: $userId")
                updateUserSubscriptionTier(userId, "free")
            } else {
                Log.e(TAG, "Failed to save free tier status to Firestore for user: $userId")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing free tier to Firestore for user: $userId", e)
        }
    }
    
    /**
     * Get the primary (highest tier/most recent) subscription
     */
    private fun getPrimarySubscription(subscriptions: List<Purchase>): Purchase? {
        if (subscriptions.isEmpty()) return null
        if (subscriptions.size == 1) return subscriptions.first()
        
        // Priority order: premium > pro > basic
        val tierPriority = mapOf("premium" to 3, "pro" to 2, "basic" to 1, "free" to 0)
        
        return subscriptions.maxByOrNull { purchase ->
            val productId = purchase.products.firstOrNull { SUBSCRIPTION_PRODUCT_IDS.contains(it) }
            val planType = PRODUCT_ID_TO_PLAN_TYPE[productId] ?: "free"
            val priority = tierPriority[planType] ?: 0
            
            // Secondary sort by purchase time (more recent first)
            priority * 1000000 + purchase.purchaseTime
        }
    }
    
    /**
     * Calculate subscription end date based on billing cycle
     */
    private fun calculateSubscriptionEndDate(purchaseTime: Long, billingCycle: String): Long {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = purchaseTime
        
        when (billingCycle) {
            "yearly" -> calendar.add(Calendar.YEAR, 1)
            "monthly" -> calendar.add(Calendar.MONTH, 1)
            else -> calendar.add(Calendar.MONTH, 1) // Default to monthly
        }
        
        return calendar.timeInMillis
    }
    
    /**
     * Get features list based on plan type
     */
    private fun getFeaturesByPlanType(planType: String): List<String> {
        return when (planType) {
            "basic" -> listOf(
                "500 messages per month",
                "Advanced AI models", 
                "Priority response time",
                "Unlimited chat history",
                "Basic elite tools",
                "Voice messages",
                "Image processing",
                "Email support"
            )
            "pro" -> listOf(
                "1000 messages per month",
                "Advanced AI models",
                "Priority response time", 
                "Unlimited chat history",
                "Basic elite tools",
                "Voice messages",
                "Image processing",
                "Email support"
            )
            "premium" -> listOf(
                "Unlimited messages",
                "All AI models",
                "Fastest response time",
                "Unlimited chat history", 
                "All elite tools",
                "Advanced voice features",
                "Advanced image processing",
                "Priority support",
                "Offline access",
                "Early access to new features"
            )
            "ultra_premium" -> listOf(
                "Unlimited everything",
                "All AI models + exclusive models",
                "Instant response time",
                "Unlimited chat history with advanced analytics", 
                "All elite tools + exclusive enterprise tools",
                "Advanced voice features + real-time transcription",
                "Advanced image processing + video processing",
                "24/7 priority support",
                "Offline access",
                "Early access to new features",
                "API access",
                "Custom integrations",
                "Dedicated account manager"
            )
            else -> listOf("50 messages per month", "Basic AI models", "Standard response time")
        }
    }
    
    /**
     * Update user's subscription tier in their Firestore profile
     */
    private suspend fun updateUserSubscriptionTier(userId: String, planType: String) {
        try {
            val userDocRef = firestore.collection("users").document(userId)
            
            val tierMapping = mapOf(
                "free" to "FREE",
                "basic" to "BASIC", 
                "pro" to "PRO",
                "premium" to "PREMIUM",
                "ultra_premium" to "ENTERPRISE"
            )
            
            val updates = mapOf(
                "subscriptionTier" to tierMapping[planType],
                "subscriptionStatus" to if (planType == "free") "INACTIVE" else "ACTIVE",
                "lastSyncAt" to System.currentTimeMillis()
            )
            
            userDocRef.set(updates, SetOptions.merge()).await()
            Log.d(TAG, "Updated user subscription tier to: ${tierMapping[planType]}")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error updating user subscription tier", e)
        }
    }
    
    /**
     * Handle real-time purchase updates from Google Play Billing
     * This ensures immediate sync when purchases occur
     */
    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: List<Purchase>?) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            Log.d(TAG, "New purchase detected, triggering sync")
            
            // Acknowledge purchases if needed
            for (purchase in purchases) {
                if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED && !purchase.isAcknowledged) {
                    acknowledgePurchase(purchase)
                }
            }
            
            // Trigger sync after a brief delay to ensure acknowledgment is processed
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    syncSubscriptionToFirestore()
                }
            }, 2000)
            
        } else if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            Log.d(TAG, "User canceled purchase")
        } else {
            Log.e(TAG, "Error in purchase update: ${billingResult.debugMessage}")
        }
    }
    
    /**
     * Acknowledge a purchase
     */
    private fun acknowledgePurchase(purchase: Purchase) {
        try {
            val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            
            billingClient.acknowledgePurchase(acknowledgePurchaseParams) { billingResult ->
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d(TAG, "Purchase acknowledged successfully")
                } else {
                    Log.e(TAG, "Failed to acknowledge purchase: ${billingResult.debugMessage}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error acknowledging purchase", e)
        }
    }
    
    /**
     * Get current subscription status from Firestore (for display purposes)
     * This is what ProfileActivity should use instead of querying billing directly
     */
    suspend fun getSubscriptionStatusForDisplay(): FirestoreSubscriptionManager.SubscriptionStatus {
        return try {
            // Always get from Firestore, never from Google Play Billing directly
            firestoreSubscriptionManager.getSubscriptionStatus()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting subscription status for display", e)
            // Return default free status if error
            FirestoreSubscriptionManager.SubscriptionStatus(
                isActive = false,
                planType = "free", 
                daysRemaining = 0,
                isExpired = false,
                features = emptyList()
            )
        }
    }
    
    /**
     * Cleanup billing client connection
     */
    fun cleanup() {
        try {
            if (::billingClient.isInitialized && billingClient.isReady) {
                billingClient.endConnection()
                Log.d(TAG, "Billing client connection ended")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up billing client", e)
        }
    }
    
    /**
     * Public method to check if sync is needed
     * Can be called periodically or on app launch
     */
    suspend fun checkAndSyncIfNeeded(): Boolean {
        return try {
            if (!firebaseAuthService.isSignedIn()) {
                Log.d(TAG, "User not authenticated, skipping sync check")
                return false
            }
            
            // Check when last sync occurred
            val subscription = firestoreSubscriptionManager.getSubscription()
            val lastSync = subscription?.updatedAt ?: 0L
            val currentTime = System.currentTimeMillis()
            val hoursSinceLastSync = (currentTime - lastSync) / (1000 * 60 * 60)
            
            // Sync if it's been more than 6 hours since last sync
            if (hoursSinceLastSync > 6) {
                Log.d(TAG, "Last sync was $hoursSinceLastSync hours ago, triggering sync")
                syncSubscriptionToFirestore()
            } else {
                Log.d(TAG, "Recent sync found (${hoursSinceLastSync}h ago), skipping")
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking sync status", e)
            false
        }
    }
    
    /**
     * Force sync with updated product IDs - use this after updating product ID mappings
     */
    suspend fun forceSyncWithUpdatedProductIds(): Boolean {
        return try {
            Log.d(TAG, "FORCE SYNC: Updated product IDs - forcing fresh sync from Google Play Billing")
            Log.d(TAG, "FORCE SYNC: Looking for these product IDs: $SUBSCRIPTION_PRODUCT_IDS")
            
            // Always sync regardless of last sync time
            val result = syncSubscriptionToFirestore()
            Log.d(TAG, "FORCE SYNC: Sync completed with result: $result")
            result
        } catch (e: Exception) {
            Log.e(TAG, "FORCE SYNC: Error during forced sync", e)
            false
        }
    }
}