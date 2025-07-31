package com.playstudio.aiteacher.profile

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.*
import java.util.concurrent.TimeUnit

class SubscriptionManager(private val context: Context) {
    
    private val database = com.playstudio.aiteacher.profile.ProfileDatabase.getDatabase(context)
    private val subscriptionDao = database.subscriptionDao()
    private val usageAnalyticsDao = database.usageAnalyticsDao()
    private val authService = AuthenticationService(context)
    
    companion object {
        private const val TAG = "SubscriptionManager"
    }
    
    data class SubscriptionPlan(
        val planType: String,
        val displayName: String,
        val description: String,
        val features: List<String>,
        val monthlyPrice: Double,
        val yearlyPrice: Double,
        val currency: String = "USD",
        val maxMessages: Int,
        val maxTokens: Int,
        val maxStorage: Double, // in MB
        val hasEliteTools: Boolean,
        val hasVoiceFeatures: Boolean,
        val hasImageProcessing: Boolean,
        val hasPrioritySupport: Boolean,
        val hasOfflineAccess: Boolean
    )
    
    data class UsageLimits(
        val maxMessages: Int,
        val maxTokens: Int,
        val maxStorage: Double,
        val currentMessages: Int,
        val currentTokens: Int,
        val currentStorage: Double,
        val resetDate: Date
    )
    
    data class SubscriptionStatus(
        val isActive: Boolean,
        val plan: SubscriptionPlan,
        val subscription: SubscriptionEntity?,
        val usageLimits: UsageLimits,
        val daysRemaining: Int,
        val isTrialActive: Boolean,
        val trialDaysRemaining: Int,
        val autoRenew: Boolean,
        val nextBillingDate: Date?
    )
    
    // Subscription Plans
    fun getAvailablePlans(): List<SubscriptionPlan> {
        return listOf(
            SubscriptionPlan(
                planType = "free",
                displayName = "Free",
                description = "Basic AI chat with limited features",
                features = listOf(
                    "50 messages per month",
                    "Basic AI models",
                    "Standard response time",
                    "Limited chat history",
                    "Community support"
                ),
                monthlyPrice = 0.0,
                yearlyPrice = 0.0,
                maxMessages = 50,
                maxTokens = 10000,
                maxStorage = 100.0,
                hasEliteTools = false,
                hasVoiceFeatures = false,
                hasImageProcessing = false,
                hasPrioritySupport = false,
                hasOfflineAccess = false
            ),
            SubscriptionPlan(
                planType = "pro",
                displayName = "Pro",
                description = "Enhanced AI experience with advanced features",
                features = listOf(
                    "500 messages per month",
                    "Advanced AI models",
                    "Priority response time",
                    "Unlimited chat history",
                    "Basic elite tools",
                    "Voice messages",
                    "Image processing",
                    "Email support"
                ),
                monthlyPrice = 9.99,
                yearlyPrice = 99.99,
                maxMessages = 500,
                maxTokens = 100000,
                maxStorage = 1000.0,
                hasEliteTools = true,
                hasVoiceFeatures = true,
                hasImageProcessing = true,
                hasPrioritySupport = false,
                hasOfflineAccess = false
            ),
            SubscriptionPlan(
                planType = "premium",
                displayName = "Premium",
                description = "Ultimate AI experience with all features",
                features = listOf(
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
                ),
                monthlyPrice = 19.99,
                yearlyPrice = 199.99,
                maxMessages = Int.MAX_VALUE,
                maxTokens = Int.MAX_VALUE,
                maxStorage = Double.MAX_VALUE,
                hasEliteTools = true,
                hasVoiceFeatures = true,
                hasImageProcessing = true,
                hasPrioritySupport = true,
                hasOfflineAccess = true
            )
        )
    }
    
    fun getPlanByType(planType: String): SubscriptionPlan? {
        return getAvailablePlans().find { it.planType == planType }
    }
    
    // Subscription Management
    suspend fun getCurrentSubscriptionStatus(): SubscriptionStatus? {
        return try {
            val user = authService.getCurrentUser() ?: return null
            val subscription = subscriptionDao.getActiveSubscription(user.userId)
            
            val planType = subscription?.planType ?: "free"
            val plan = getPlanByType(planType) ?: return null
            
            val usageLimits = getCurrentUsageLimits()
            val daysRemaining = if (subscription != null) {
                val timeDiff = subscription.endDate.time - System.currentTimeMillis()
                TimeUnit.MILLISECONDS.toDays(timeDiff).toInt()
            } else 0
            
            val isTrialActive = subscription?.trialEndDate?.after(Date()) ?: false
            val trialDaysRemaining = if (isTrialActive && subscription?.trialEndDate != null) {
                val timeDiff = subscription.trialEndDate.time - System.currentTimeMillis()
                TimeUnit.MILLISECONDS.toDays(timeDiff).toInt()
            } else 0
            
            SubscriptionStatus(
                isActive = subscription?.status == "active",
                plan = plan,
                subscription = subscription,
                usageLimits = usageLimits,
                daysRemaining = daysRemaining,
                isTrialActive = isTrialActive,
                trialDaysRemaining = trialDaysRemaining,
                autoRenew = subscription?.autoRenew ?: false,
                nextBillingDate = subscription?.endDate
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error getting subscription status", e)
            null
        }
    }
    
    suspend fun subscribe(planType: String, billingCycle: String, orderId: String? = null, productId: String? = null): Boolean {
        return try {
            val user = authService.getCurrentUser() ?: return false
            val plan = getPlanByType(planType) ?: return false
            
            // Calculate dates
            val startDate = Date()
            val endDate = if (billingCycle == "yearly") {
                Calendar.getInstance().apply {
                    time = startDate
                    add(Calendar.YEAR, 1)
                }.time
            } else {
                Calendar.getInstance().apply {
                    time = startDate
                    add(Calendar.MONTH, 1)
                }.time
            }
            
            // Create subscription locally
            val subscription = SubscriptionEntity(
                userId = user.userId,
                planType = planType,
                status = "active",
                startDate = startDate,
                endDate = endDate,
                billingCycle = billingCycle,
                pricePaid = if (billingCycle == "yearly") plan.yearlyPrice else plan.monthlyPrice,
                currency = plan.currency,
                autoRenew = true,
                featuresIncluded = plan.features,
                createdAt = startDate,
                updatedAt = startDate
            )
            
            subscriptionDao.insertSubscription(subscription)
            
            // Sync subscription status to Firebase for webapp access
            syncSubscriptionToFirebase(planType, billingCycle, endDate, orderId, productId)
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error subscribing", e)
            false
        }
    }
    
    // Sync subscription status to Firebase Functions
    private suspend fun syncSubscriptionToFirebase(
        planType: String, 
        billingCycle: String, 
        endDate: Date,
        orderId: String? = null,
        productId: String? = null
    ) {
        try {
            val functions = com.google.firebase.functions.FirebaseFunctions.getInstance()
            val updateSubscriptionStatus = functions.getHttpsCallable("updateSubscriptionStatus")
            
            val subscriptionData = hashMapOf(
                "status" to planType,
                "tier" to when(planType) {
                    "free" -> "FREE"
                    "pro" -> "PRO" 
                    "premium" -> "PREMIUM"
                    else -> "FREE"
                },
                "expiry" to endDate.time,
                "purchaseDate" to System.currentTimeMillis(),
                "productId" to productId,
                "orderId" to orderId,
                "autoRenewing" to true
            )
            
            val data = hashMapOf("subscriptionData" to subscriptionData)
            
            updateSubscriptionStatus.call(data)
                .addOnSuccessListener { result ->
                    Log.d(TAG, "Successfully synced subscription to Firebase: $result")
                }
                .addOnFailureListener { exception ->
                    Log.e(TAG, "Failed to sync subscription to Firebase", exception)
                }
                
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing subscription to Firebase", e)
        }
    }
    
    suspend fun upgradeSubscription(newPlanType: String): Boolean {
        return try {
            val user = authService.getCurrentUser() ?: return false
            val currentSubscription = subscriptionDao.getActiveSubscription(user.userId) ?: return false
            
            // Update current subscription
            val updatedSubscription = currentSubscription.copy(
                planType = newPlanType,
                updatedAt = Date()
            )
            
            subscriptionDao.updateSubscription(updatedSubscription)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error upgrading subscription", e)
            false
        }
    }
    
    suspend fun cancelSubscription(): Boolean {
        return try {
            val user = authService.getCurrentUser() ?: return false
            val subscription = subscriptionDao.getActiveSubscription(user.userId) ?: return false
            
            // Update subscription to cancelled
            subscriptionDao.updateSubscriptionStatus(subscription.subscriptionId, "cancelled")
            subscriptionDao.updateAutoRenew(subscription.subscriptionId, false)
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling subscription", e)
            false
        }
    }
    
    suspend fun renewSubscription(): Boolean {
        return try {
            val user = authService.getCurrentUser() ?: return false
            val subscription = subscriptionDao.getActiveSubscription(user.userId) ?: return false
            
            // Extend subscription
            val newEndDate = if (subscription.billingCycle == "yearly") {
                Calendar.getInstance().apply {
                    time = subscription.endDate
                    add(Calendar.YEAR, 1)
                }.time
            } else {
                Calendar.getInstance().apply {
                    time = subscription.endDate
                    add(Calendar.MONTH, 1)
                }.time
            }
            
            subscriptionDao.updateEndDate(subscription.subscriptionId, newEndDate)
            subscriptionDao.updateSubscriptionStatus(subscription.subscriptionId, "active")
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error renewing subscription", e)
            false
        }
    }
    
    suspend fun startFreeTrial(planType: String): Boolean {
        return try {
            val user = authService.getCurrentUser() ?: return false
            val plan = getPlanByType(planType) ?: return false
            
            // Check if user already had a trial
            val existingSubscriptions = mutableListOf<SubscriptionEntity>()
            subscriptionDao.getSubscriptionsByUser(user.userId).collect { 
                existingSubscriptions.addAll(it) 
            }
            
            if (existingSubscriptions.any { it.trialEndDate != null }) {
                return false // User already had a trial
            }
            
            // Create trial subscription
            val startDate = Date()
            val trialEndDate = Calendar.getInstance().apply {
                time = startDate
                add(Calendar.DAY_OF_MONTH, 7) // 7-day trial
            }.time
            
            val subscription = SubscriptionEntity(
                userId = user.userId,
                planType = planType,
                status = "trial",
                startDate = startDate,
                endDate = trialEndDate,
                billingCycle = "monthly",
                pricePaid = 0.0,
                currency = plan.currency,
                autoRenew = false,
                trialEndDate = trialEndDate,
                featuresIncluded = plan.features,
                createdAt = startDate,
                updatedAt = startDate
            )
            
            subscriptionDao.insertSubscription(subscription)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error starting free trial", e)
            false
        }
    }
    
    // Usage Tracking
    suspend fun getCurrentUsageLimits(): UsageLimits {
        return try {
            val user = authService.getCurrentUser() ?: return getDefaultUsageLimits()
            val subscription = subscriptionDao.getActiveSubscription(user.userId)
            val plan = getPlanByType(subscription?.planType ?: "free") ?: return getDefaultUsageLimits()
            
            // Get current month usage
            val calendar = Calendar.getInstance()
            val startOfMonth = Calendar.getInstance().apply {
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.time
            
            val endOfMonth = Calendar.getInstance().apply {
                set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
                set(Calendar.MILLISECOND, 999)
            }.time
            
            val currentMessages = usageAnalyticsDao.getMessagesSentInPeriod(user.userId, startOfMonth, endOfMonth)
            val currentTokens = usageAnalyticsDao.getTokensConsumedInPeriod(user.userId, startOfMonth, endOfMonth)
            val currentStorage = usageAnalyticsDao.getTotalStorageUsed(user.userId)
            
            // Calculate reset date (first day of next month)
            val resetDate = Calendar.getInstance().apply {
                add(Calendar.MONTH, 1)
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.time
            
            UsageLimits(
                maxMessages = plan.maxMessages,
                maxTokens = plan.maxTokens,
                maxStorage = plan.maxStorage,
                currentMessages = currentMessages,
                currentTokens = currentTokens,
                currentStorage = currentStorage,
                resetDate = resetDate
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error getting usage limits", e)
            getDefaultUsageLimits()
        }
    }
    
    private fun getDefaultUsageLimits(): UsageLimits {
        val freePlan = getPlanByType("free")!!
        return UsageLimits(
            maxMessages = freePlan.maxMessages,
            maxTokens = freePlan.maxTokens,
            maxStorage = freePlan.maxStorage,
            currentMessages = 0,
            currentTokens = 0,
            currentStorage = 0.0,
            resetDate = Date()
        )
    }
    
    suspend fun canSendMessage(): Boolean {
        val limits = getCurrentUsageLimits()
        return limits.currentMessages < limits.maxMessages
    }
    
    suspend fun canUseTokens(tokensNeeded: Int): Boolean {
        val limits = getCurrentUsageLimits()
        return (limits.currentTokens + tokensNeeded) <= limits.maxTokens
    }
    
    suspend fun canUseStorage(storageNeeded: Double): Boolean {
        val limits = getCurrentUsageLimits()
        return (limits.currentStorage + storageNeeded) <= limits.maxStorage
    }
    
    suspend fun hasFeature(feature: String): Boolean {
        val status = getCurrentSubscriptionStatus() ?: return false
        return when (feature) {
            "elite_tools" -> status.plan.hasEliteTools
            "voice_features" -> status.plan.hasVoiceFeatures
            "image_processing" -> status.plan.hasImageProcessing
            "priority_support" -> status.plan.hasPrioritySupport
            "offline_access" -> status.plan.hasOfflineAccess
            else -> false
        }
    }
    
    // Billing History
    fun getBillingHistory(): Flow<List<SubscriptionEntity>> {
        return flow {
            try {
                val user = authService.getCurrentUser()
                if (user != null) {
                    subscriptionDao.getSubscriptionsByUser(user.userId).collect { subscriptions ->
                        emit(subscriptions)
                    }
                } else {
                    emit(emptyList())
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting billing history", e)
                emit(emptyList())
            }
        }
    }
    
    suspend fun getTotalSpent(): Double {
        return try {
            val user = authService.getCurrentUser() ?: return 0.0
            subscriptionDao.getTotalRevenue(user.userId)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting total spent", e)
            0.0
        }
    }
    
    // Usage Analytics
    fun getUsageAnalytics(): Flow<List<UsageAnalyticsEntity>> {
        return flow {
            try {
                val user = authService.getCurrentUser()
                if (user != null) {
                    usageAnalyticsDao.getLastThirtyDaysUsage(user.userId).collect { usage ->
                        emit(usage)
                    }
                } else {
                    emit(emptyList())
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting usage analytics", e)
                emit(emptyList())
            }
        }
    }
    
    suspend fun recordUsage(
        messagesSent: Int = 0,
        tokensConsumed: Int = 0,
        featuresUsed: List<String> = emptyList(),
        eliteToolsUsed: Map<String, Int> = emptyMap(),
        voiceMessages: Int = 0,
        imagesProcessed: Int = 0,
        filesUploaded: Int = 0,
        storageUsed: Double = 0.0,
        timeSpent: Int = 0
    ) {
        try {
            val user = authService.getCurrentUser() ?: return
            val today = Date()
            
            // Get existing usage for today
            val existingUsage = usageAnalyticsDao.getUsageForDate(user.userId, today)
            
            if (existingUsage != null) {
                // Update existing usage
                val updatedUsage = existingUsage.copy(
                    messagesSent = existingUsage.messagesSent + messagesSent,
                    tokensConsumed = existingUsage.tokensConsumed + tokensConsumed,
                    featuresAccessed = (existingUsage.featuresAccessed + featuresUsed).distinct(),
                    eliteToolsUsed = existingUsage.eliteToolsUsed.toMutableMap().apply {
                        eliteToolsUsed.forEach { (tool, count) ->
                            this[tool] = (this[tool] ?: 0) + count
                        }
                    },
                    voiceMessagesSent = existingUsage.voiceMessagesSent + voiceMessages,
                    imagesProcessed = existingUsage.imagesProcessed + imagesProcessed,
                    filesUploaded = existingUsage.filesUploaded + filesUploaded,
                    storageUsedMb = existingUsage.storageUsedMb + storageUsed,
                    totalTimeSpentMinutes = existingUsage.totalTimeSpentMinutes + timeSpent
                )
                usageAnalyticsDao.updateUsageAnalytics(updatedUsage)
            } else {
                // Create new usage record
                val newUsage = UsageAnalyticsEntity(
                    userId = user.userId,
                    date = today,
                    messagesSent = messagesSent,
                    tokensConsumed = tokensConsumed,
                    featuresAccessed = featuresUsed,
                    eliteToolsUsed = eliteToolsUsed,
                    voiceMessagesSent = voiceMessages,
                    imagesProcessed = imagesProcessed,
                    filesUploaded = filesUploaded,
                    storageUsedMb = storageUsed,
                    totalTimeSpentMinutes = timeSpent
                )
                usageAnalyticsDao.insertUsageAnalytics(newUsage)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error recording usage", e)
        }
    }
}