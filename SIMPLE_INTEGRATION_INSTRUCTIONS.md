# Simple Integration Instructions for Cost-Aware Subscriptions

## Step 1: Add These Methods to Your MainActivity Class

Copy and paste these methods directly into your `MainActivity.kt` class:

```kotlin
// Add this import at the top of MainActivity.kt
import com.playstudio.aiteacher.pricing.*

// Add these private properties to your MainActivity class
private lateinit var costManager: CostManager
private var costAwareDialog: CostAwareSubscriptionDialog? = null

// Add this method to initialize the cost manager (call this in onCreate after billing setup)
private fun initializeCostManager() {
    costManager = CostManager.getInstance(this)
}

// Add this method to show the new subscription dialog
private fun showCostAwareSubscriptionDialog() {
    try {
        costAwareDialog = CostAwareSubscriptionDialog(
            context = this,
            costManager = costManager,
            onPurchaseSelected = { tier, productId ->
                Log.d("MainActivity", "User selected tier: $tier, productId: $productId")
                startPurchaseFlow(productId)
            },
            onDismiss = {
                Log.d("MainActivity", "Cost-aware subscription dialog dismissed")
                costAwareDialog = null
            }
        )
        
        // Update dialog with Google Play billing prices if available
        costAwareDialog?.updateWithBillingPrices(productDetailsMap)
        costAwareDialog?.show()
        
    } catch (e: Exception) {
        Log.e("MainActivity", "Error showing cost-aware dialog", e)
        // Fallback to your existing subscription dialog
        showSubscriptionDialog()
    }
}

// Add this method to handle new subscription purchases
private fun handleNewSubscriptionPurchase(purchase: Purchase, productId: String) {
    // Map product ID to subscription tier
    val tier = when (productId) {
        "basic_monthly_subscription" -> SubscriptionTier.BASIC
        "pro_monthly_subscription" -> SubscriptionTier.PRO
        "premium_monthly_subscription" -> SubscriptionTier.PREMIUM
        "ultra_monthly_subscription" -> SubscriptionTier.ULTRA_PREMIUM
        else -> {
            Log.w("MainActivity", "Unknown product ID: $productId")
            return
        }
    }
    
    // Verify the purchase
    if (verifyPurchase(purchase)) {
        // Update the cost manager with the new subscription tier
        if (::costManager.isInitialized) {
            costManager.setUserSubscriptionTier(tier = tier)
        }
        
        // Update subscription status
        updateSubscriptionStatus(true, purchase.purchaseTime + (30L * 24 * 60 * 60 * 1000))
        
        // Show success message
        showCustomToast("Successfully upgraded to ${tier.displayName}! 🎉")
        
        Log.d("MainActivity", "Successfully activated ${tier.displayName} subscription")
    } else {
        Log.e("MainActivity", "Purchase verification failed")
        showCustomToast("Purchase verification failed. Please contact support.")
    }
}

// Add this method to query new subscription products
private fun queryNewSubscriptionProducts() {
    val productIds = listOf(
        "basic_monthly_subscription",
        "pro_monthly_subscription", 
        "premium_monthly_subscription",
        "ultra_monthly_subscription"
    )
    
    val productList = productIds.map { productId ->
        QueryProductDetailsParams.Product.newBuilder()
            .setProductId(productId)
            .setProductType(BillingClient.ProductType.SUBS)
            .build()
    }

    val params = QueryProductDetailsParams.newBuilder()
        .setProductList(productList)
        .build()

    billingClient.queryProductDetailsAsync(params) { billingResult, productDetailsList ->
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            Log.d("MainActivity", "Found ${productDetailsList.size} new subscription products")
            
            productDetailsList.forEach { productDetails ->
                productDetailsMap[productDetails.productId] = productDetails
                Log.d("MainActivity", "Product: ${productDetails.productId}")
            }
        } else {
            Log.e("MainActivity", "Error querying new product details: ${billingResult.debugMessage}")
        }
    }
}
```

## Step 2: Modify Your Existing Methods

### In onCreate(), add this after your billing setup:
```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    // ... your existing code ...
    
    setupBillingClient()
    
    // Add this line
    initializeCostManager()
}
```

### In onBillingSetupFinished(), add the new product query:
```kotlin
override fun onBillingSetupFinished(billingResult: BillingResult) {
    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
        queryAvailableSubscriptions() // Your existing method
        queryNewSubscriptionProducts() // Add this line
    } else {
        Log.e("MainActivity", "Error setting up billing: ${billingResult.debugMessage}")
        showCustomToast("Error setting up billing: ${billingResult.debugMessage}")
    }
}
```

### In onPurchasesUpdated(), add handling for new subscriptions:
```kotlin
override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
    // Your existing logic...
    
    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
        for (purchase in purchases) {
            // Handle new subscription products
            val productId = purchase.products.firstOrNull()
            if (productId in listOf("basic_monthly_subscription", "pro_monthly_subscription", 
                                   "premium_monthly_subscription", "ultra_monthly_subscription")) {
                handleNewSubscriptionPurchase(purchase, productId)
            } else {
                // Your existing purchase handling
                handlePurchase(purchase)
            }
        }
    } else {
        Log.e("MainActivity", "Purchase failed: ${billingResult.debugMessage}")
        showCustomToast("Purchase failed: ${billingResult.debugMessage}")
    }
}
```

## Step 3: Update Your Subscription Button

Find your subscription button click handler and add the new dialog option:

```kotlin
// Option A: Replace your existing subscription button click
subscriptionButton.setOnClickListener {
    showCostAwareSubscriptionDialog()
}

// Option B: Add a new button for the new system alongside your existing one
newSubscriptionButton.setOnClickListener {
    showCostAwareSubscriptionDialog()
}
```

## Step 4: Google Play Console Setup

1. Go to Google Play Console → Your App → Monetization → Subscriptions
2. Create these new subscription products:

| Product ID | Name | Price | Billing Period |
|------------|------|-------|----------------|
| `basic_monthly_subscription` | Essential Plan | $9.99 | Monthly |
| `pro_monthly_subscription` | Professional Plan | $39.99 | Monthly |
| `premium_monthly_subscription` | Premium Plan | $79.99 | Monthly |
| `ultra_monthly_subscription` | Enterprise Max | $299.99 | Monthly |

## Step 5: Test the Integration

1. Build and run your app
2. Check logs for any errors
3. Test the subscription dialog flow
4. Verify billing integration works

## Step 6: Cleanup (Remove these compilation errors)

If you get any compilation errors, make sure you have:

1. Added the import: `import com.playstudio.aiteacher.pricing.*`
2. Added the methods above to your MainActivity class
3. The cost-aware pricing system files are in your project

This approach avoids the compilation errors by keeping everything within your existing MainActivity class and not relying on extension functions or external dependencies.