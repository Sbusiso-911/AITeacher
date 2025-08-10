package com.playstudio.aiteacher.billing

import android.content.Context
import android.content.SharedPreferences
import com.playstudio.aiteacher.pricing.SubscriptionTier
import java.util.Calendar
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/**
 * Handles prorated upgrades and upgrade credits since Google Play Billing
 * doesn't support dynamic pricing or automatic proration
 */
class ProrationManager(private val context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences("proration_prefs", Context.MODE_PRIVATE)
    
    // Subscription prices (in cents to avoid floating point issues)
    private val tierPrices = mapOf(
        SubscriptionTier.BASIC to 999,      // $9.99
        SubscriptionTier.PRO to 1999,       // $19.99
        SubscriptionTier.PREMIUM to 2999,   // $29.99
        SubscriptionTier.ENTERPRISE to 4999  // $49.99
    )
    
    /**
     * Calculate upgrade credit for current subscription
     */
    fun calculateUpgradeCredit(
        currentTier: SubscriptionTier,
        currentSubscriptionStartTime: Long,
        currentSubscriptionEndTime: Long
    ): UpgradeCredit {
        if (currentTier == SubscriptionTier.FREE) {
            return UpgradeCredit(0, 0, 0.0f)
        }
        
        val currentTime = System.currentTimeMillis()
        val subscriptionDuration = currentSubscriptionEndTime - currentSubscriptionStartTime
        val remainingTime = currentSubscriptionEndTime - currentTime
        
        // Calculate remaining percentage
        val remainingPercentage = if (subscriptionDuration > 0) {
            (remainingTime.toFloat() / subscriptionDuration.toFloat()).coerceIn(0f, 1f)
        } else 0f
        
        val currentTierPrice = tierPrices[currentTier] ?: 0
        val creditAmount = (currentTierPrice * remainingPercentage).roundToInt()
        
        return UpgradeCredit(
            creditAmountCents = creditAmount,
            remainingDays = TimeUnit.MILLISECONDS.toDays(remainingTime).toInt(),
            remainingPercentage = remainingPercentage
        )
    }
    
    /**
     * Calculate effective upgrade price after applying credit
     */
    fun calculateUpgradePrice(
        currentTier: SubscriptionTier,
        targetTier: SubscriptionTier,
        upgradeCredit: UpgradeCredit,
        usageBasedDiscountPercent: Int = 0
    ): UpgradePrice {
        val targetTierPrice = tierPrices[targetTier] ?: 0
        val creditAmount = upgradeCredit.creditAmountCents
        
        // Apply usage-based discount
        val discountAmount = (targetTierPrice * usageBasedDiscountPercent / 100.0).roundToInt()
        
        // Calculate final price
        val finalPrice = (targetTierPrice - creditAmount - discountAmount).coerceAtLeast(0)
        
        return UpgradePrice(
            originalPrice = targetTierPrice,
            creditApplied = creditAmount,
            discountApplied = discountAmount,
            finalPrice = finalPrice,
            savingsTotal = creditAmount + discountAmount
        )
    }
    
    /**
     * Store pending upgrade credit to be applied after purchase
     */
    fun storePendingUpgradeCredit(
        orderId: String,
        creditAmount: Int,
        originalTier: SubscriptionTier,
        targetTier: SubscriptionTier
    ) {
        prefs.edit()
            .putInt("pending_credit_$orderId", creditAmount)
            .putString("pending_original_tier_$orderId", originalTier.name)
            .putString("pending_target_tier_$orderId", targetTier.name)
            .putLong("pending_credit_timestamp_$orderId", System.currentTimeMillis())
            .apply()
    }
    
    /**
     * Apply pending upgrade credit after successful purchase
     */
    fun applyPendingUpgradeCredit(orderId: String): Boolean {
        val creditAmount = prefs.getInt("pending_credit_$orderId", 0)
        if (creditAmount > 0) {
            // Add credit to user's account balance
            addAccountCredit(creditAmount)
            
            // Clear pending credit
            prefs.edit()
                .remove("pending_credit_$orderId")
                .remove("pending_original_tier_$orderId")
                .remove("pending_target_tier_$orderId")
                .remove("pending_credit_timestamp_$orderId")
                .apply()
            
            return true
        }
        return false
    }
    
    /**
     * Add credit to user's account balance
     */
    private fun addAccountCredit(creditAmountCents: Int) {
        val currentBalance = prefs.getInt("account_credit_cents", 0)
        val newBalance = currentBalance + creditAmountCents
        prefs.edit()
            .putInt("account_credit_cents", newBalance)
            .putLong("last_credit_added", System.currentTimeMillis())
            .apply()
    }
    
    /**
     * Get current account credit balance
     */
    fun getAccountCreditBalance(): Int {
        return prefs.getInt("account_credit_cents", 0)
    }
    
    /**
     * Use account credit for purchase
     */
    fun useAccountCredit(amountCents: Int): Boolean {
        val currentBalance = getAccountCreditBalance()
        if (currentBalance >= amountCents) {
            val newBalance = currentBalance - amountCents
            prefs.edit()
                .putInt("account_credit_cents", newBalance)
                .putLong("last_credit_used", System.currentTimeMillis())
                .apply()
            return true
        }
        return false
    }
    
    /**
     * Format price in cents to dollar string
     */
    fun formatPrice(cents: Int): String {
        val dollars = cents / 100.0
        return "$%.2f".format(dollars)
    }
    
    /**
     * Get upgrade recommendation message with pricing
     */
    fun getUpgradeRecommendationMessage(
        currentTier: SubscriptionTier,
        targetTier: SubscriptionTier,
        upgradeCredit: UpgradeCredit,
        upgradePrice: UpgradePrice,
        usageBasedDiscountPercent: Int
    ): String {
        return buildString {
            append("💰 Upgrade Pricing Breakdown:\n\n")
            append("${targetTier.displayName} Plan: ${formatPrice(upgradePrice.originalPrice)}\n")
            
            if (upgradeCredit.creditAmountCents > 0) {
                append("Remaining subscription credit: -${formatPrice(upgradeCredit.creditAmountCents)}\n")
            }
            
            if (usageBasedDiscountPercent > 0) {
                append("Usage-based discount ($usageBasedDiscountPercent% OFF): -${formatPrice(upgradePrice.discountApplied)}\n")
            }
            
            append("─────────────────────────────\n")
            append("Total upgrade cost: ${formatPrice(upgradePrice.finalPrice)}\n")
            
            if (upgradePrice.savingsTotal > 0) {
                append("✅ You save: ${formatPrice(upgradePrice.savingsTotal)}\n")
            }
            
            if (upgradeCredit.remainingDays > 0) {
                append("\n📅 ${upgradeCredit.remainingDays} days remaining on current plan")
            }
        }
    }
}

/**
 * Data classes for upgrade calculations
 */
data class UpgradeCredit(
    val creditAmountCents: Int,
    val remainingDays: Int,
    val remainingPercentage: Float
)

data class UpgradePrice(
    val originalPrice: Int,
    val creditApplied: Int,
    val discountApplied: Int,
    val finalPrice: Int,
    val savingsTotal: Int
)

/**
 * Upgrade method options
 */
enum class UpgradeMethod {
    IMMEDIATE_CHARGE,    // Charge difference immediately
    NEXT_BILLING_CYCLE,  // Apply at next billing cycle
    ACCOUNT_CREDIT       // Use account credit system
}