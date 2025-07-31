# Quick Fix for Subscription Dialog Crash

## 🚨 **Problem**
Your existing `showSubscriptionOptions()` method is trying to access old view IDs that don't exist in the new layout, causing a NullPointerException.

## ⚡ **Quick Fix - Replace Your showSubscriptionOptions() Method**

Replace your entire `showSubscriptionOptions()` method in MainActivity.kt with this:

```kotlin
private fun showSubscriptionOptions() {
    try {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_subscription, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        // Get references to NEW view IDs
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
        updateNewSubscriptionPrices(basicPrice, proPrice, premiumPrice, ultraPrice)

        // Selection function
        fun selectSubscription(productId: String, selectedView: LinearLayout, tierName: String) {
            // Reset all backgrounds
            basicSubscription?.setBackgroundResource(R.drawable.subscription_option_unselected)
            proSubscription?.setBackgroundResource(R.drawable.subscription_option_unselected)
            premiumSubscription?.setBackgroundResource(R.drawable.subscription_option_unselected)
            ultraSubscription?.setBackgroundResource(R.drawable.subscription_option_unselected)
            
            // Highlight selected
            selectedView.setBackgroundResource(R.drawable.subscription_option_selected)
            
            selectedProductId = productId
            btnBuy?.isEnabled = true
            btnBuy?.text = "🔥 UPGRADE TO $tierName"
        }

        // Set up click listeners for new layout
        basicSubscription?.setOnClickListener { 
            selectSubscription("basic_monthly_subscription", basicSubscription, "ESSENTIAL")
        }
        proSubscription?.setOnClickListener { 
            selectSubscription("pro_monthly_subscription", proSubscription, "PRO")
        }
        premiumSubscription?.setOnClickListener { 
            selectSubscription("premium_monthly_subscription", premiumSubscription, "PREMIUM")
        }
        ultraSubscription?.setOnClickListener { 
            selectSubscription("ultra_monthly_subscription", ultraSubscription, "ENTERPRISE")
        }

        // Purchase button
        btnBuy?.setOnClickListener {
            selectedProductId?.let { productId ->
                try {
                    startPurchaseFlow(productId)
                    dialog.dismiss()
                } catch (e: Exception) {
                    Log.e("MainActivity", "Error starting purchase flow", e)
                    showCustomToast("Error starting purchase. Please try again.")
                }
            }
        }

        // Close button
        btnClose?.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
        
    } catch (e: Exception) {
        Log.e("MainActivity", "Error showing subscription dialog", e)
        showCustomToast("Error loading subscription options")
    }
}

// Add this helper method to your MainActivity
private fun updateNewSubscriptionPrices(
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
            if (price != null) basicPrice?.text = price
        }
        
        productDetailsMap["pro_monthly_subscription"]?.let { productDetails ->
            val price = productDetails.subscriptionOfferDetails?.firstOrNull()
                ?.pricingPhases?.pricingPhaseList?.firstOrNull()?.formattedPrice
            if (price != null) proPrice?.text = price
        }
        
        productDetailsMap["premium_monthly_subscription"]?.let { productDetails ->
            val price = productDetails.subscriptionOfferDetails?.firstOrNull()
                ?.pricingPhases?.pricingPhaseList?.firstOrNull()?.formattedPrice
            if (price != null) premiumPrice?.text = price
        }
        
        productDetailsMap["ultra_monthly_subscription"]?.let { productDetails ->
            val price = productDetails.subscriptionOfferDetails?.firstOrNull()
                ?.pricingPhases?.pricingPhaseList?.firstOrNull()?.formattedPrice
            if (price != null) ultraPrice?.text = price
        }
    } catch (e: Exception) {
        Log.e("MainActivity", "Error updating subscription prices", e)
    }
}
```

## 🔧 **Additional Fix - Update onPurchasesUpdated**

Also add this to handle the new subscription product IDs in your `onPurchasesUpdated` method:

```kotlin
// Add this to your onPurchasesUpdated method
override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
        for (purchase in purchases) {
            val productId = purchase.products.firstOrNull()
            
            // Handle new subscription tiers
            when (productId) {
                "basic_monthly_subscription",
                "pro_monthly_subscription", 
                "premium_monthly_subscription",
                "ultra_monthly_subscription" -> {
                    handleNewSubscriptionPurchase(purchase, productId)
                }
                else -> {
                    // Handle existing/legacy subscriptions
                    handlePurchase(purchase)
                }
            }
        }
    } else {
        Log.e("MainActivity", "Purchase failed: ${billingResult.debugMessage}")
        showCustomToast("Purchase failed: ${billingResult.debugMessage}")
    }
}

// Add this helper method
private fun handleNewSubscriptionPurchase(purchase: Purchase, productId: String) {
    if (verifyPurchase(purchase)) {
        // Map product ID to subscription status
        val tierName = when (productId) {
            "basic_monthly_subscription" -> "Essential"
            "pro_monthly_subscription" -> "Professional"
            "premium_monthly_subscription" -> "Premium"
            "ultra_monthly_subscription" -> "Enterprise"
            else -> "Premium"
        }
        
        // Update subscription status using your existing methods
        updateSubscriptionStatus(true, purchase.purchaseTime + (30L * 24 * 60 * 60 * 1000))
        setSubscriptionTypeAndBadge("PREMIUM", tierName)
        
        showCustomToast("Welcome to $tierName! 🎉")
        updateChatFragmentSubscriptionStatus()
        
        Log.d("MainActivity", "Successfully activated $tierName subscription")
    } else {
        Log.e("MainActivity", "Purchase verification failed for $productId")
        showCustomToast("Purchase verification failed. Please contact support.")
    }
}
```

## 🎯 **What This Fixes**

✅ **Removes NullPointerException** - No more crashes when opening subscription dialog  
✅ **Works with new layout** - Uses correct view IDs from the new dialog  
✅ **Handles new product IDs** - Supports the 4 new subscription tiers  
✅ **Graceful error handling** - Try-catch blocks prevent crashes  
✅ **Backward compatibility** - Still handles existing subscriptions  

## 🚀 **After Making These Changes**

1. **Test the subscription dialog** - Should open without crashing
2. **Verify tier selection** - Clicking tiers should highlight them
3. **Test purchase flow** - Should work with your existing billing
4. **Update Google Play Console** - Add the new product IDs when ready

This quick fix will get your app working immediately while you plan the full Google Play Console updates! 🎉