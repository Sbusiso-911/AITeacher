# Complete Subscription System Replacement Guide

## 🎯 **Overview**
This guide shows you how to completely replace your existing subscription system with the new cost-aware pricing tiers and update Google Play Console accordingly.

## 📋 **Step 1: Update Google Play Console**

### **Delete Old Subscription Products**
1. Go to Google Play Console → Your App → Monetization → Subscriptions
2. **Deactivate** (don't delete) your old subscription products:
   - Weekly subscriptions
   - Monthly subscriptions  
   - Yearly subscriptions
   - Any other old product IDs

### **Create New Subscription Products**
Create these **EXACT** product IDs in Google Play Console:

| Product ID | Name | Price | Billing Period | Description |
|------------|------|-------|----------------|-------------|
| `basic_monthly_subscription` | Essential Plan | **$9.99** | Monthly | 100 messages/day, GPT-4.1 mini access |
| `pro_monthly_subscription` | Professional Plan | **$39.99** | Monthly | 300 messages/day, Claude Sonnet 4 access |
| `premium_monthly_subscription` | Premium Plan | **$79.99** | Monthly | 500 messages/day, Advanced AI features |
| `ultra_monthly_subscription` | Enterprise Max | **$299.99** | Monthly | 1000 messages/day, ALL models including Claude Opus 4 |

### **Important Google Play Notes:**
- Set all subscriptions to **Monthly billing**
- Add **free trial periods** if desired (e.g., 7 days)
- Set **grace periods** for failed payments
- Configure **account hold** for payment issues

## 📱 **Step 2: Replace Your MainActivity Methods**

### **A. Update Product ID Lists**
Find your existing product ID arrays and replace them with:

```kotlin
// Replace your existing product ID lists with these
private val subscriptionProductIds = listOf(
    "basic_monthly_subscription",
    "pro_monthly_subscription", 
    "premium_monthly_subscription",
    "ultra_monthly_subscription"
)

// Keep old IDs for migration purposes (temporary)
private val legacyProductIds = listOf(
    "weekly_subscription",
    "monthly_subscription", 
    "yearly_subscription"
    // Add your existing old product IDs here
)
```

### **B. Update Your `showSubscriptionOptions()` Method**
Replace your existing `showSubscriptionOptions()` method with this:

```kotlin
private fun showSubscriptionOptions() {
    val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_subscription_new, null)
    val dialog = AlertDialog.Builder(this)
        .setView(dialogView)
        .setCancelable(true)
        .create()

    // Initialize cost manager
    val costManager = CostManager.getInstance(this)
    var selectedTier: SubscriptionTier? = null
    var selectedProductId: String? = null

    // Get references to subscription options
    val basicSubscription = dialogView.findViewById<LinearLayout>(R.id.basicSubscription)
    val proSubscription = dialogView.findViewById<LinearLayout>(R.id.proSubscription)
    val premiumSubscription = dialogView.findViewById<LinearLayout>(R.id.premiumSubscription)
    val ultraSubscription = dialogView.findViewById<LinearLayout>(R.id.ultraSubscription)
    
    // Get references to price views
    val basicPrice = dialogView.findViewById<TextView>(R.id.basicPrice)
    val proPrice = dialogView.findViewById<TextView>(R.id.proPrice)
    val premiumPrice = dialogView.findViewById<TextView>(R.id.premiumPrice)
    val ultraPrice = dialogView.findViewById<TextView>(R.id.ultraPrice)
    
    val btnBuy = dialogView.findViewById<Button>(R.id.btnBuy)
    val btnClose = dialogView.findViewById<TextView>(R.id.btnClose)

    // Update prices with real Google Play billing data
    updatePricesFromBilling(basicPrice, proPrice, premiumPrice, ultraPrice)

    // Selection handlers
    fun selectSubscription(tier: SubscriptionTier, productId: String, selectedView: LinearLayout) {
        // Reset all backgrounds
        basicSubscription.setBackgroundResource(R.drawable.subscription_option_unselected)
        proSubscription.setBackgroundResource(R.drawable.subscription_option_unselected)
        premiumSubscription.setBackgroundResource(R.drawable.subscription_option_unselected)
        ultraSubscription.setBackgroundResource(R.drawable.subscription_option_unselected)
        
        // Highlight selected
        selectedView.setBackgroundResource(R.drawable.subscription_option_selected)
        
        selectedTier = tier
        selectedProductId = productId
        btnBuy.isEnabled = true
        btnBuy.text = "🔥 UPGRADE TO ${tier.displayName.uppercase()}"
    }

    // Set up click listeners
    basicSubscription.setOnClickListener { 
        selectSubscription(SubscriptionTier.BASIC, "basic_monthly_subscription", basicSubscription)
    }
    proSubscription.setOnClickListener { 
        selectSubscription(SubscriptionTier.PRO, "pro_monthly_subscription", proSubscription)
    }
    premiumSubscription.setOnClickListener { 
        selectSubscription(SubscriptionTier.PREMIUM, "premium_monthly_subscription", premiumSubscription)
    }
    ultraSubscription.setOnClickListener { 
        selectSubscription(SubscriptionTier.ULTRA_PREMIUM, "ultra_monthly_subscription", ultraSubscription)
    }

    // Purchase button
    btnBuy.setOnClickListener {
        selectedProductId?.let { productId ->
            startPurchaseFlow(productId)
            dialog.dismiss()
        }
    }

    // Close button
    btnClose.setOnClickListener {
        dialog.dismiss()
    }

    dialog.show()
}

// Helper method to update prices
private fun updatePricesFromBilling(
    basicPrice: TextView, 
    proPrice: TextView, 
    premiumPrice: TextView, 
    ultraPrice: TextView
) {
    // Update with Google Play billing prices if available
    productDetailsMap["basic_monthly_subscription"]?.let { productDetails ->
        val price = productDetails.subscriptionOfferDetails?.firstOrNull()
            ?.pricingPhases?.pricingPhaseList?.firstOrNull()?.formattedPrice
        if (price != null) basicPrice.text = price
    }
    
    productDetailsMap["pro_monthly_subscription"]?.let { productDetails ->
        val price = productDetails.subscriptionOfferDetails?.firstOrNull()
            ?.pricingPhases?.pricingPhaseList?.firstOrNull()?.formattedPrice
        if (price != null) proPrice.text = price
    }
    
    productDetailsMap["premium_monthly_subscription"]?.let { productDetails ->
        val price = productDetails.subscriptionOfferDetails?.firstOrNull()
            ?.pricingPhases?.pricingPhaseList?.firstOrNull()?.formattedPrice
        if (price != null) premiumPrice.text = price
    }
    
    productDetailsMap["ultra_monthly_subscription"]?.let { productDetails ->
        val price = productDetails.subscriptionOfferDetails?.firstOrNull()
            ?.pricingPhases?.pricingPhaseList?.firstOrNull()?.formattedPrice
        if (price != null) ultraPrice.text = price
    }
}
```

### **C. Update Your `queryAvailableSubscriptions()` Method**
Replace your existing subscription query method:

```kotlin
private fun queryAvailableSubscriptions() {
    val productList = subscriptionProductIds.map { productId ->
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
            
            // Clear old product details
            productDetailsMap.clear()
            
            // Store new product details
            productDetailsList.forEach { productDetails ->
                productDetailsMap[productDetails.productId] = productDetails
                val price = productDetails.subscriptionOfferDetails?.firstOrNull()
                    ?.pricingPhases?.pricingPhaseList?.firstOrNull()?.formattedPrice
                Log.d("MainActivity", "Product: ${productDetails.productId}, Price: $price")
            }
        } else {
            Log.e("MainActivity", "Error querying subscriptions: ${billingResult.debugMessage}")
            showCustomToast("Error loading subscription options")
        }
    }
}
```

### **D. Update Your `onPurchasesUpdated()` Method**
Add handling for new subscription tiers:

```kotlin
override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
        for (purchase in purchases) {
            val productId = purchase.products.firstOrNull()
            
            when (productId) {
                // Handle new subscription tiers
                "basic_monthly_subscription" -> handleNewSubscription(purchase, SubscriptionTier.BASIC)
                "pro_monthly_subscription" -> handleNewSubscription(purchase, SubscriptionTier.PRO)
                "premium_monthly_subscription" -> handleNewSubscription(purchase, SubscriptionTier.PREMIUM)
                "ultra_monthly_subscription" -> handleNewSubscription(purchase, SubscriptionTier.ULTRA_PREMIUM)
                
                // Handle legacy subscriptions (for existing users)
                in legacyProductIds -> handleLegacySubscription(purchase)
                
                else -> {
                    Log.w("MainActivity", "Unknown product ID: $productId")
                    handlePurchase(purchase) // Fallback to existing logic
                }
            }
        }
    } else {
        Log.e("MainActivity", "Purchase failed: ${billingResult.debugMessage}")
        showCustomToast("Purchase failed: ${billingResult.debugMessage}")
    }
}

// New method to handle subscription purchases
private fun handleNewSubscription(purchase: Purchase, tier: SubscriptionTier) {
    if (verifyPurchase(purchase)) {
        // Initialize cost manager and set tier
        val costManager = CostManager.getInstance(this)
        costManager.setUserSubscriptionTier(tier = tier)
        
        // Update subscription status
        updateSubscriptionStatus(true, purchase.purchaseTime + (30L * 24 * 60 * 60 * 1000))
        
        // Update UI
        setSubscriptionTypeAndBadge(
            badge = tier.displayName.uppercase(),
            text = tier.displayName
        )
        
        // Show success message
        showCustomToast("Welcome to ${tier.displayName}! 🎉 Enjoy unlimited AI access!")
        
        // Update chat fragment
        updateChatFragmentSubscriptionStatus()
        
        Log.d("MainActivity", "Successfully activated ${tier.displayName} subscription")
    } else {
        Log.e("MainActivity", "Purchase verification failed")
        showCustomToast("Purchase verification failed. Please contact support.")
    }
}

// Handle legacy subscriptions for existing users
private fun handleLegacySubscription(purchase: Purchase) {
    // Map legacy products to new tiers (you decide the mapping)
    val legacyTier = when (purchase.products.firstOrNull()) {
        "weekly_subscription" -> SubscriptionTier.PRO
        "monthly_subscription" -> SubscriptionTier.PRO  
        "yearly_subscription" -> SubscriptionTier.PREMIUM
        else -> SubscriptionTier.BASIC
    }
    
    handleNewSubscription(purchase, legacyTier)
}
```

## 🔧 **Step 3: Add Cost Manager Integration**

Add these methods to your MainActivity:

```kotlin
// Add this property to your MainActivity class
private lateinit var costManager: CostManager

// Add this to your onCreate() method after billing setup
private fun initializeCostManager() {
    costManager = CostManager.getInstance(this)
}

// Call this in onCreate()
override fun onCreate(savedInstanceState: Bundle?) {
    // ... your existing code ...
    setupBillingClient()
    initializeCostManager() // Add this line
}
```

## 🎨 **Step 4: Replace Your Existing Layout File**

**Option A: Replace completely**
```bash
# Backup your existing file
mv app/src/main/res/layout/dialog_subscription.xml app/src/main/res/layout/dialog_subscription_old.xml

# Use the new layout
mv app/src/main/res/layout/dialog_subscription_new.xml app/src/main/res/layout/dialog_subscription.xml
```

**Option B: Keep both and test gradually**
- Use `dialog_subscription_new.xml` for testing
- Switch your `showSubscriptionOptions()` to use the new layout
- Remove old layout after testing

## 🧪 **Step 5: Testing Strategy**

### **Phase 1: Test with Google Play Console**
1. Upload APK to **Internal Testing**
2. Add test accounts
3. Test each subscription tier purchase
4. Verify Google Play billing works

### **Phase 2: Verify Cost Tracking**
```kotlin
// Add temporary debug button to test cost tracking
private fun testCostTracking() {
    lifecycleScope.launch {
        val status = costManager.getUserCostStatus()
        showCustomToast("Tier: ${status.tier}, Usage: ${status.usagePercentage}%")
    }
}
```

### **Phase 3: User Migration**
1. **Soft launch** to small user group
2. **Monitor** subscription conversion rates
3. **Migrate** existing subscribers gracefully
4. **Full rollout** after verification

## 📊 **Step 6: Monitoring and Analytics**

Add tracking for the new subscription system:

```kotlin
private fun trackSubscriptionEvent(eventName: String, tier: SubscriptionTier, price: Double) {
    // Add your analytics tracking here (Firebase, etc.)
    Log.d("SubscriptionAnalytics", "$eventName: ${tier.displayName} - $${price}")
}

// Call this when showing dialog
trackSubscriptionEvent("subscription_dialog_shown", SubscriptionTier.FREE, 0.0)

// Call this when user selects tier
trackSubscriptionEvent("subscription_tier_selected", selectedTier, tierPrice)

// Call this when purchase completes
trackSubscriptionEvent("subscription_purchased", tier, price)
```

## ⚠️ **Important Migration Notes**

### **Existing Subscribers**
- **Don't lose existing subscribers** - map their old subscriptions to appropriate new tiers
- **Grandfather** existing pricing for loyal users if needed
- **Communicate changes** via in-app notifications

### **Pricing Strategy**
- **A/B test** the new pricing with small user groups first
- **Monitor** subscription conversion rates closely
- **Adjust** pricing based on user feedback and metrics

### **Cost Protection**
- The new system includes **strict usage limits** to prevent cost overruns
- **Emergency brakes** at $500/day per user
- **Model-specific limits** (Claude Opus 4: 3 uses/day max)

This complete replacement ensures you get profitable subscription pricing while maintaining a smooth user experience! 🚀