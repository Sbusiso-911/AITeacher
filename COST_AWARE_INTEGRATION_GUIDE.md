# Cost-Aware Subscription Integration Guide

## Overview
This guide shows how to integrate the new cost-aware subscription system into your existing MainActivity.

## 1. Add to MainActivity Class

### Step 1: Add import statements at the top of MainActivity.kt
```kotlin
import com.playstudio.aiteacher.pricing.*
```

### Step 2: Add CostManager initialization in onCreate()
Add this after your existing billing setup:
```kotlin
// Add this in onCreate() after setupBillingClient()
setupCostAwareSubscriptionSystem()
```

### Step 3: Update your subscription button click handler
Find your existing subscription button click handler and replace it with:
```kotlin
// Replace your existing subscription button onClick
subscribeButton.setOnClickListener {
    handleSubscriptionButtonClick()
}
```

### Step 4: Update your onPurchasesUpdated method
Add this to handle new subscription tiers. You can keep your existing method and add this logic:
```kotlin
override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
    // Your existing logic for old subscriptions
    // ... existing code ...
    
    // Add new cost-aware handling
    onPurchasesUpdatedCostAware(billingResult, purchases)
}
```

### Step 5: Add the integration methods
Copy all the methods from `CostAwareMainActivityIntegration.kt` into your MainActivity class.

## 2. Google Play Console Setup

### Step 1: Create new subscription products in Google Play Console
1. Go to Google Play Console → Your App → Monetization → Subscriptions
2. Create these new subscription products:

| Product ID | Name | Price | Billing Period |
|------------|------|-------|----------------|
| `basic_monthly_subscription` | Essential Plan | $9.99 | Monthly |
| `pro_monthly_subscription` | Professional Plan | $39.99 | Monthly |
| `premium_monthly_subscription` | Premium Plan | $79.99 | Monthly |
| `ultra_monthly_subscription` | Enterprise Max | $299.99 | Monthly |

### Step 2: Configure subscription benefits
For each subscription, add these details:
- **Basic**: 100 messages/day, GPT-4.1 mini access
- **Pro**: 300 messages/day, Claude Sonnet 4 access  
- **Premium**: 500 messages/day, o3 and advanced AI features
- **Ultra**: 1000 messages/day, ALL models including Claude Opus 4

## 3. Testing the Integration

### Step 1: Test with Google Play Console
1. Upload your app to Internal Testing
2. Add test accounts for subscription testing
3. Test the purchase flow with test payment methods

### Step 2: Verify cost tracking
```kotlin
// Add this button to test cost tracking (remove before production)
testButton.setOnClickListener {
    showCurrentSubscriptionStatus()
}
```

## 4. UI Integration

### Option A: Replace existing subscription dialog
Replace your existing `showSubscriptionDialog()` calls with:
```kotlin
showCostAwareSubscriptionDialog()
```

### Option B: Add alongside existing dialog
Keep your existing dialogs and add a new button:
```kotlin
newSubscriptionButton.setOnClickListener {
    showCostAwareSubscriptionDialog() 
}
```

## 5. ChatFragment Integration

### Update your ChatFragment to use cost checking
In your ChatFragment's message sending method, add:
```kotlin
private fun sendMessage(message: String) {
    lifecycleScope.launch {
        val costManager = CostManager.getInstance(requireContext())
        val result = costManager.checkAndSelectModel(
            messageText = message,
            hasImages = /* check if message has images */,
            hasDocuments = /* check if message has documents */
        )
        
        when (result) {
            is ChatRequestResult.Approved -> {
                // Proceed with API call using result.selectedModel
                makeAPICall(result.selectedModel, message)
            }
            is ChatRequestResult.BudgetExceeded -> {
                // Show upgrade dialog
                showUpgradeDialog(result.suggestedUpgrade)
            }
            is ChatRequestResult.NoAccess -> {
                // Show subscription required dialog
                showSubscriptionDialog()
            }
            is ChatRequestResult.Error -> {
                // Handle error
                showError(result.message)
            }
        }
    }
}
```

## 6. Recording Actual Costs

After each successful API call, record the actual cost:
```kotlin
private fun onAPICallComplete(model: AIModel, inputTokens: Int, outputTokens: Int) {
    lifecycleScope.launch {
        val costManager = CostManager.getInstance(requireContext())
        val result = costManager.recordActualCost(
            model = model,
            inputTokens = inputTokens,
            outputTokens = outputTokens
        )
        
        when (result) {
            is CostTrackingResult.Approved -> {
                // Cost recorded successfully
                Log.d("ChatFragment", "Cost recorded: ${result.cost}")
            }
            else -> {
                // Handle cost tracking issues
                Log.w("ChatFragment", "Cost tracking issue: $result")
            }
        }
    }
}
```

## 7. Migration from Old System

### Step 1: Identify existing subscribers
```kotlin
private fun migrateExistingSubscribers() {
    val costManager = CostManager.getInstance(this)
    
    // Check existing subscription status
    if (isUserSubscribed()) {
        // Migrate to appropriate tier based on existing subscription
        val currentTier = SubscriptionTier.PRO // Adjust based on your logic
        costManager.setUserSubscriptionTier(tier = currentTier)
    }
}
```

### Step 2: Handle transition period
You can run both old and new systems in parallel during migration:
```kotlin
// In your subscription check logic
if (useNewCostAwareSystem) {
    showCostAwareSubscriptionDialog()
} else {
    showSubscriptionDialog() // Your existing dialog
}
```

## 8. Error Handling

### Add proper error handling for edge cases:
```kotlin
try {
    showCostAwareSubscriptionDialog()
} catch (e: Exception) {
    Log.e("MainActivity", "Error showing cost-aware dialog", e)
    // Fallback to old dialog
    showSubscriptionDialog()
}
```

## 9. Production Checklist

Before deploying:
- [ ] Test all subscription tiers with real Google Play billing
- [ ] Verify cost tracking accuracy with test API calls
- [ ] Test model-specific daily limits (especially Claude Opus 4)
- [ ] Verify emergency cost limits prevent overruns
- [ ] Test upgrade/downgrade scenarios
- [ ] Ensure proper error handling for billing failures
- [ ] Test with different user states (new users, existing subscribers)

## 10. Monitoring and Analytics

Add monitoring to track the new system:
```kotlin
// Track subscription tier changes
private fun trackSubscriptionChange(oldTier: SubscriptionTier, newTier: SubscriptionTier) {
    // Add your analytics tracking here
    Log.d("Subscription", "Tier changed: $oldTier -> $newTier")
}

// Track cost usage patterns  
private fun trackCostUsage(userId: String, dailyCost: Double, tier: SubscriptionTier) {
    // Add your analytics tracking here
    Log.d("CostUsage", "User: $userId, Cost: $dailyCost, Tier: $tier")
}
```

This integration ensures you get:
✅ **Profitable subscription pricing** with realistic cost estimates
✅ **Model-specific usage limits** to prevent cost overruns  
✅ **Emergency cost controls** with $500/day absolute limits
✅ **Smart model selection** based on complexity and budget
✅ **Google Play Billing integration** for seamless payments
✅ **Backward compatibility** with your existing system