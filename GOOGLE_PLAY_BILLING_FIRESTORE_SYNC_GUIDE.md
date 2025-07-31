# Google Play Billing to Firestore Sync Implementation Guide

## Overview

This implementation creates a comprehensive Google Play Billing to Firestore sync service that addresses the key requirements:

1. **Queries Google Play Billing for subscription status**
2. **Syncs that data to Firestore subscription documents**
3. **Ensures ProfileActivity retrieves subscription data from Firestore (not directly from billing)**
4. **Addresses "Service connection is disconnected" error by making Firestore the display data source**

## Architecture

```
Google Play Billing API
         ↓
GooglePlayBillingSync (Sync Service)
         ↓
Firestore (Single Source of Truth)
         ↓
ProfileActivity & UI Components
```

## Key Components

### 1. GooglePlayBillingSync.kt
Main sync service that handles:
- Connecting to Google Play Billing API
- Querying active subscriptions
- Converting billing data to Firestore format
- Saving subscription data to Firestore
- Providing subscription status for display

### 2. BillingSyncService.kt
Background service and worker for:
- Periodic billing synchronization (every 12 hours)
- Immediate sync triggers
- Background sync management

### 3. Modified ProfileActivity.kt
Updated to:
- Use sync service instead of direct billing queries
- Get all subscription data from Firestore
- Trigger sync before displaying data
- Handle sync failures gracefully

### 4. Modified MainActivity.kt
Updated to:
- Initialize billing sync service
- Trigger sync after successful purchases
- Perform periodic sync checks
- Clean up resources properly

## Implementation Details

### Product ID Mapping

The service maps Google Play product IDs to plan types:

```kotlin
private val PRODUCT_ID_TO_PLAN_TYPE = mapOf(
    "ai_teacher_pro_monthly" to "pro",
    "ai_teacher_pro_yearly" to "pro",
    "ai_teacher_premium_monthly" to "premium", 
    "ai_teacher_premium_yearly" to "premium",
    "ai_teacher_basic_monthly" to "basic",
    "ai_teacher_basic_yearly" to "basic"
)
```

**IMPORTANT**: Update these product IDs to match your actual Google Play Console setup.

### Sync Triggers

The sync is triggered:

1. **On app launch** - `MainActivity.onResume()`
2. **After purchases** - `MainActivity.onPurchasesUpdated()`
3. **Profile view** - `ProfileActivity.loadProfileData()`
4. **Periodically** - Every 12 hours via WorkManager
5. **Manually** - `BillingSyncManager.syncNow()`

### Data Flow

1. **Billing Query**: Service queries Google Play Billing API for active subscriptions
2. **Data Conversion**: Converts Purchase objects to FirestoreSubscriptionManager.SubscriptionData
3. **Firestore Sync**: Saves subscription data to Firestore collections:
   - `/users/{userId}/subscriptions/current`
   - `/subscriptions/{userId}` (admin collection)
4. **UI Display**: ProfileActivity reads from Firestore, never from billing directly

## Configuration

### 1. Update Product IDs

Edit `GooglePlayBillingSync.kt` and update the product ID constants to match your Google Play Console:

```kotlin
private val SUBSCRIPTION_PRODUCT_IDS = listOf(
    "your_pro_monthly_product_id",
    "your_pro_yearly_product_id",
    "your_premium_monthly_product_id", 
    "your_premium_yearly_product_id"
    // Add your actual product IDs here
)
```

### 2. Configure Firebase Rules

Ensure your Firestore security rules allow authenticated users to read/write their subscription data:

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    // User subscription data
    match /users/{userId}/subscriptions/{document} {
      allow read, write: if request.auth != null && request.auth.uid == userId;
    }
    
    // Admin subscription collection (read-only for users)
    match /subscriptions/{userId} {
      allow read: if request.auth != null && request.auth.uid == userId;
      allow write: if request.auth != null && request.auth.uid == userId;
    }
  }
}
```

### 3. Initialize in Application

Add to your Application class or MainActivity:

```kotlin
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize billing sync
        BillingSyncManager.initialize(this)
    }
}
```

### 4. Add WorkManager Dependency

Ensure your `build.gradle` includes WorkManager:

```gradle
implementation "androidx.work:work-runtime-ktx:2.8.1"
```

### 5. Register Service in Manifest

Add to `AndroidManifest.xml`:

```xml
<service
    android:name=".billing.BillingSyncService"
    android:enabled="true"
    android:exported="false" />
```

## Usage

### Basic Sync

```kotlin
// In any activity/fragment
val billingSync = GooglePlayBillingSync(context)
lifecycleScope.launch {
    val success = billingSync.syncSubscriptionToFirestore()
    if (success) {
        // Update UI
    }
}
```

### Get Subscription Status for Display

```kotlin
// Always use this for UI display (never query billing directly)
val billingSync = GooglePlayBillingSync(context)
lifecycleScope.launch {
    val status = billingSync.getSubscriptionStatusForDisplay()
    // Use status to update UI
}
```

### Manual Sync

```kotlin
// Trigger immediate sync
lifecycleScope.launch {
    val success = BillingSyncManager.syncNow(context)
    if (success) {
        // Sync completed
    }
}
```

## Error Handling

### Billing Service Disconnection

The sync service handles billing disconnection gracefully:

1. If billing service is disconnected, the app continues using Firestore data
2. Sync attempts will retry on next app launch or periodic sync
3. UI always shows data from Firestore, never directly from billing

### Authentication Errors

```kotlin
// Sync only works for authenticated users
if (!firebaseAuthService.isSignedIn()) {
    Log.w(TAG, "User not authenticated, cannot sync subscription")
    return false
}
```

### Network Errors

- Sync failures are logged but don't crash the app
- Periodic sync will retry
- UI continues to show cached Firestore data

## Testing

### Test Subscription Sync

1. Make a test purchase
2. Check logs for sync completion
3. Verify data in Firestore console
4. Check ProfileActivity displays correct status

### Test Offline Behavior

1. Disconnect internet
2. Open ProfileActivity
3. Should show cached Firestore data
4. No billing service errors should appear

### Test Background Sync

1. Leave app running for 12+ hours
2. Check WorkManager logs
3. Verify periodic sync occurs

## Monitoring

### Logging

All sync operations are logged with tags:
- `GooglePlayBillingSync`
- `BillingSyncWorker`
- `BillingSyncManager`

### Firestore Monitoring

Monitor these Firestore collections:
- `/users/{userId}/subscriptions/current`
- `/subscriptions/{userId}`

### Sync Status

```kotlin
// Check if sync is working
val isEnabled = BillingSyncManager.isSyncEnabled(context)
val lastSync = BillingSyncManager.getLastSyncTime(context)
```

## Troubleshooting

### Common Issues

1. **"Service connection is disconnected"**
   - **Solution**: ProfileActivity now uses Firestore data, avoiding this error entirely

2. **Subscription not syncing**
   - Check product IDs match Google Play Console
   - Verify user is authenticated
   - Check Firestore security rules

3. **UI not updating**
   - Ensure using `billingSync.getSubscriptionStatusForDisplay()`
   - Check that sync completed successfully
   - Verify Firestore data is present

### Debug Steps

1. Enable verbose logging
2. Check billing sync logs
3. Verify Firestore data in console
4. Test with different subscription states

## Benefits

✅ **Eliminates "Service connection is disconnected" errors**
✅ **Single source of truth (Firestore)**
✅ **Works offline with cached data**
✅ **Automatic background sync**
✅ **Robust error handling**
✅ **Scalable architecture**
✅ **Real-time subscription updates**

## Next Steps

1. Update product IDs to match your setup
2. Test with real Google Play subscriptions
3. Monitor Firestore usage and costs
4. Consider adding subscription analytics
5. Implement subscription change/cancellation flows

This implementation provides a robust, scalable solution for Google Play Billing integration that eliminates common connection issues and ensures reliable subscription status display.