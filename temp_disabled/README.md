# Temporarily Disabled Components

Due to Firebase dependency conflicts, the following components have been temporarily moved here:

## UI Components (Missing Layout Files)
- `UnifiedLoginActivity.kt` - Complete login UI with Google Sign-In
- `UnifiedRegisterActivity.kt` - User registration UI 
- `UnifiedChatHistoryActivity.kt` - Synchronized chat history UI
- `UnifiedChatHistoryAdapter.kt` - Chat history list adapter
- `UnifiedSubscriptionActivity.kt` - Subscription management UI

## Backend Components (Firebase Dependency Conflicts)  
- `RealtimeSyncService.kt` - Real-time Firestore synchronization
- `WebappSwitchingService.kt` - Legacy webapp switching (replaced by UnifiedBackendClient)

## Core Backend Still Working
The core unified backend system is fully functional without these components:
- ✅ `AuthenticationService.kt` - User authentication and profile management
- ✅ `UnifiedBackendClient.kt` - REST API client for cloud sync
- ✅ `UnifiedDataManager.kt` - Unified data access layer
- ✅ `ProfileManager.kt` - Local database operations
- ✅ All database entities and DAOs

## Re-enabling Steps
1. **Fix Firebase Dependency Conflict**: Resolve duplicate class `com.google.firebase.Timestamp`
2. **Create Missing Layouts**: Add layout files for UI activities
3. **Add Missing Resources**: Menu files, drawable icons, color resources
4. **Test Integration**: Verify in existing activities

## Alternative Integration
Use the core backend components directly as shown in `UNIFIED_BACKEND_INTEGRATION_EXAMPLE.md` while these UI components are disabled.