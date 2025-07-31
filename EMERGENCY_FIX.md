# 🚨 EMERGENCY FIX - Stop the Crash Immediately

## **Step 1: Find Line 2628 in MainActivity.kt**

Look for this code around line 2628:

```kotlin
// Apply strikethrough
applyStrikethrough(weeklyOriginalPrice, weeklyOriginalPrice.text.toString())
applyStrikethrough(monthlyOriginalPrice, monthlyOriginalPrice.text.toString())
applyStrikethrough(yearlyOriginalPrice, yearlyOriginalPrice.text.toString())

// Highlight the yearly plan as best value
val yearlyPlanContainer = dialogView.findViewById<View>(R.id.yearlySubscription)
yearlyPlanContainer.setBackgroundColor(ContextCompat.getColor(this, R.color.best_value_background))
```

## **Step 2: Comment Out These Lines**

Replace the problematic code with this:

```kotlin
// TEMPORARY FIX: Comment out old layout references
// Apply strikethrough
// applyStrikethrough(weeklyOriginalPrice, weeklyOriginalPrice.text.toString())
// applyStrikethrough(monthlyOriginalPrice, monthlyOriginalPrice.text.toString())
// applyStrikethrough(yearlyOriginalPrice, yearlyOriginalPrice.text.toString())

// Highlight the yearly plan as best value
// val yearlyPlanContainer = dialogView.findViewById<View>(R.id.yearlySubscription)
// yearlyPlanContainer.setBackgroundColor(ContextCompat.getColor(this, R.color.best_value_background))

// TEMPORARY: Just show a basic working dialog
val btnBuy = dialogView.findViewById<Button>(R.id.btnBuy)
val btnClose = dialogView.findViewById<TextView>(R.id.btnClose)

btnBuy?.setOnClickListener {
    // For now, just use your first product ID or a default one
    startPurchaseFlow("monthly_subscription") // Use your existing product ID
    dialog.dismiss()
}

btnClose?.setOnClickListener {
    dialog.dismiss()
}

// Enable the buy button by default for testing
btnBuy?.isEnabled = true
```

## **Step 3: Find and Comment Out More Old References**

Look for other references to old view IDs in the same method and comment them out:

```kotlin
// Comment out these if they exist:
// val weeklyPrice = dialogView.findViewById<TextView>(R.id.weeklyPrice)
// val weeklyOriginalPrice = dialogView.findViewById<TextView>(R.id.weeklyOriginalPrice)
// val monthlyPrice = dialogView.findViewById<TextView>(R.id.monthlyPrice)
// val monthlyOriginalPrice = dialogView.findViewById<TextView>(R.id.monthlyOriginalPrice)
// val yearlyPrice = dialogView.findViewById<TextView>(R.id.yearlyPrice)
// val yearlyOriginalPrice = dialogView.findViewById<TextView>(R.id.yearlyOriginalPrice)
```

## **This Will:**
✅ **Stop the immediate crash**
✅ **Show the new subscription dialog**  
✅ **Allow basic testing**
✅ **Buy you time to implement the full fix**

## **After This Emergency Fix:**
1. **Test that the app doesn't crash**
2. **Verify the new dialog shows**
3. **Then implement the full replacement method**

**This is just a temporary bandaid to stop the bleeding!** 🩹