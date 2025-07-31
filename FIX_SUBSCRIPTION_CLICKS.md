# 🔧 Fix Subscription Button Clicks

## 🎯 **Problem:**
The subscription buttons don't respond because your `showSubscriptionOptions()` method is still trying to access old view IDs that don't exist in the new layout.

## ⚡ **IMMEDIATE FIX - Replace Your showSubscriptionOptions() Method**

Find your `showSubscriptionOptions()` method in MainActivity.kt and replace it **completely** with this:

```kotlin
private fun showSubscriptionOptions() {
    try {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_subscription, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        // Get references to NEW view IDs from your updated layout
        val basicSubscription = dialogView.findViewById<LinearLayout>(R.id.basicSubscription)
        val proSubscription = dialogView.findViewById<LinearLayout>(R.id.proSubscription)
        val premiumSubscription = dialogView.findViewById<LinearLayout>(R.id.premiumSubscription)
        val ultraSubscription = dialogView.findViewById<LinearLayout>(R.id.ultraSubscription)
        
        val basicPrice = dialogView.findViewById<TextView>(R.id.basicPrice)
        val proPrice = dialogView.findViewById<TextView>(R.id.proPrice)
        val premiumPrice = dialogView.findViewById<TextView>(R.id.premiumPrice)
        val ultraPrice = dialogView.findViewById<TextView>(R.id.ultraPrice)
        
        val btnBuy = dialogView.findViewById<Button>(R.id.btnBuy)
        val btnClose = dialogView.findViewById<TextView>(R.id.btnClose)

        // Track selected subscription
        var selectedProductId: String? = null

        // Update prices with Google Play billing if available
        updateSubscriptionPrices(basicPrice, proPrice, premiumPrice, ultraPrice)

        // Selection function to highlight selected tier
        fun selectSubscription(productId: String, selectedView: LinearLayout, tierName: String) {
            // Reset all backgrounds to unselected
            basicSubscription?.setBackgroundResource(R.drawable.subscription_option_unselected)
            proSubscription?.setBackgroundResource(R.drawable.subscription_option_unselected)
            premiumSubscription?.setBackgroundResource(R.drawable.subscription_option_unselected)
            ultraSubscription?.setBackgroundResource(R.drawable.subscription_option_unselected)
            
            // Highlight selected tier
            selectedView.setBackgroundResource(R.drawable.subscription_option_selected)
            
            selectedProductId = productId
            btnBuy?.isEnabled = true
            btnBuy?.text = "🔥 UPGRADE TO $tierName"
            
            Log.d("MainActivity", "Selected subscription: $productId")
        }

        // Set up click listeners for NEW layout IDs
        basicSubscription?.setOnClickListener { 
            selectSubscription("basic_monthly_subscription", basicSubscription, "ESSENTIAL")
        }
        
        proSubscription?.setOnClickListener { 
            selectSubscription("pro_monthly_plan", proSubscription, "PROFESSIONAL") // Use your actual Product ID
        }
        
        premiumSubscription?.setOnClickListener { 
            selectSubscription("premium_monthly_subscription", premiumSubscription, "PREMIUM")
        }
        
        ultraSubscription?.setOnClickListener { 
            selectSubscription("ultra_monthly_subscription", ultraSubscription, "ENTERPRISE")
        }

        // Purchase button click
        btnBuy?.setOnClickListener {
            selectedProductId?.let { productId ->
                try {
                    Log.d("MainActivity", "Starting purchase flow for: $productId")
                    startPurchaseFlow(productId)
                    dialog.dismiss()
                } catch (e: Exception) {
                    Log.e("MainActivity", "Error starting purchase flow", e)
                    showCustomToast("Error starting purchase. Please try again.")
                }
            } ?: run {
                showCustomToast("Please select a subscription plan first")
            }
        }

        // Close button click
        btnClose?.setOnClickListener {
            Log.d("MainActivity", "Subscription dialog closed")
            dialog.dismiss()
        }

        // Show the dialog
        dialog.show()
        
        Log.d("MainActivity", "Subscription dialog shown successfully")
        
    } catch (e: Exception) {
        Log.e("MainActivity", "Error showing subscription dialog", e)
        showCustomToast("Error loading subscription options")
    }
}

// Add this helper method to update prices
private fun updateSubscriptionPrices(
    basicPrice: TextView?,
    proPrice: TextView?,
    premiumPrice: TextView?,
    ultraPrice: TextView?
) {
    try {
        // Update with Google Play billing prices if available
        productDetailsMap["basic_monthly_subscription"]?.let { productDetails ->
            val price = productDetails.subscriptionOfferDetails?.firstOrNull()
                ?.pricingPhases?.pricingPhaseList?.firstOrNull()?.formattedPrice
            if (price != null) {
                basicPrice?.text = price
                Log.d("MainActivity", "Updated Basic price: $price")
            }
        }
        
        productDetailsMap["pro_monthly_plan"]?.let { productDetails -> // Note: Using your actual Product ID
            val price = productDetails.subscriptionOfferDetails?.firstOrNull()
                ?.pricingPhases?.pricingPhaseList?.firstOrNull()?.formattedPrice
            if (price != null) {
                proPrice?.text = price
                Log.d("MainActivity", "Updated Pro price: $price")
            }
        }
        
        productDetailsMap["premium_monthly_subscription"]?.let { productDetails ->
            val price = productDetails.subscriptionOfferDetails?.firstOrNull()
                ?.pricingPhases?.pricingPhaseList?.firstOrNull()?.formattedPrice
            if (price != null) {
                premiumPrice?.text = price
                Log.d("MainActivity", "Updated Premium price: $price")
            }
        }
        
        productDetailsMap["ultra_monthly_subscription"]?.let { productDetails ->
            val price = productDetails.subscriptionOfferDetails?.firstOrNull()
                ?.pricingPhases?.pricingPhaseList?.firstOrNull()?.formattedPrice
            if (price != null) {
                ultraPrice?.text = price
                Log.d("MainActivity", "Updated Ultra price: $price")
            }
        }
    } catch (e: Exception) {
        Log.e("MainActivity", "Error updating subscription prices", e)
    }
}
```

## 🔧 **Also Update Your queryAvailableSubscriptions() Method**

Make sure your billing query uses the correct Product IDs:

```kotlin
private fun queryAvailableSubscriptions() {
    val productIds = listOf(
        "basic_monthly_subscription",
        "pro_monthly_plan", // Note: Using your actual Product ID from Google Play
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
            Log.d("MainActivity", "Found ${productDetailsList.size} subscription products")
            
            productDetailsList.forEach { productDetails ->
                productDetailsMap[productDetails.productId] = productDetails
                val price = productDetails.subscriptionOfferDetails?.firstOrNull()
                    ?.pricingPhases?.pricingPhaseList?.firstOrNull()?.formattedPrice
                Log.d("MainActivity", "Product: ${productDetails.productId}, Price: $price")
            }
        } else {
            Log.e("MainActivity", "Error querying subscriptions: ${billingResult.debugMessage}")
        }
    }
}
```

## 🎯 **What This Fixes:**

✅ **Button clicks work** - Uses correct view IDs from new layout  
✅ **Visual feedback** - Highlights selected subscription tier  
✅ **Purchase flow** - Connects to your existing billing system  
✅ **Error handling** - Prevents crashes with try-catch blocks  
✅ **Logging** - Shows what's happening in logcat  

## 🧪 **Test After Making Changes:**

1. **Build and run** your app
2. **Open subscription dialog** - Should show without crashing
3. **Click on different tiers** - Should highlight the selected tier
4. **Check logcat** - Should see "Selected subscription: [product_id]"
5. **Click upgrade button** - Should start purchase flow

**This will make your subscription buttons responsive immediately!** 🚀