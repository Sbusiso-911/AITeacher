# Firestore API Key Management Setup

## Database Structure

Create a Firestore collection with the following structure:

```
api_keys (collection)
└── active_config (document)
    ├── openai: "your-openai-api-key-here"
    ├── anthropic: "your-anthropic-api-key-here"  
    ├── google: "your-google-api-key-here"
    ├── grok: "your-grok-api-key-here"
    └── deepseek: "your-deepseek-api-key-here"
```

## Setup Steps

1. **Create Firestore Database:**
   - Go to Firebase Console
   - Create new project or select existing
   - Enable Firestore Database
   - Choose production mode

2. **Create Collection:**
   - Collection ID: `api_keys`
   - Document ID: `active_config`

3. **Add Fields:**
   ```
   Field Name: openai
   Field Type: string  
   Field Value: sk-proj-your-actual-openai-key...
   
   Field Name: anthropic
   Field Type: string
   Field Value: sk-ant-api03-your-actual-anthropic-key...
   
   Field Name: google
   Field Type: string
   Field Value: your-actual-google-api-key
   
   Field Name: grok
   Field Type: string  
   Field Value: xai-your-actual-grok-key
   
   Field Name: deepseek
   Field Type: string
   Field Value: sk-your-actual-deepseek-key
   ```

4. **Security Rules (Important!):**
   ```javascript
   rules_version = '2';
   service cloud.firestore {
     match /databases/{database}/documents {
       // Only allow reads from api_keys collection
       // NO writes from client apps for security
       match /api_keys/{document} {
         allow read: if request.auth != null || true; // Adjust based on your auth
         allow write: if false; // Never allow writes from client
       }
     }
   }
   ```

## How It Works

1. **App Startup:** App fetches keys once and caches in memory
2. **API Calls:** Use cached keys (zero latency)
3. **Key Rotation:** You update keys in Firestore console
4. **Smart Transition:** System automatically handles the gap between rotation and user restart

### Smooth Transition During Key Rotation

**The Problem:** When you rotate a key in Firestore, users still have the old cached key until they restart the app.

**The Solution:** Smart Auto-Refresh System

1. **User makes API call** with cached (old) key
2. **API returns 401/403 error** (key expired)
3. **System detects auth error** automatically
4. **Fetches new key** from Firestore immediately
5. **Retries API call** with fresh key
6. **User gets response** without any interruption

**User Experience:**
- No app crashes
- No error messages  
- Slight delay on first call after rotation (~1-2 seconds)
- All subsequent calls use fresh cached key

## Key Rotation Process

When OpenAI forces key rotation:

1. Go to Firestore Console
2. Navigate to `api_keys` → `active_config`
3. Update the `openai` field with new key
4. Save changes
5. Users automatically get new key on next app launch

**No app updates required!**

## Cost Estimation

- 1 read per app launch (~2-3 times daily per user)
- 1M users = ~3M reads/day
- Firestore: $0.06 per 100K reads
- Daily cost: ~$1.80 for 1M users
- Very cost-effective!

## Fallback System

If Firestore fails, app automatically falls back to BuildConfig keys, ensuring your app always works.

## Implementation Example

To use the smart retry system in your API handlers:

```kotlin
// Replace old manual API calls:
val response = okHttpClient.newCall(request).execute()

// With smart retry wrapper:
val response = executeApiCallWithRetry(requestBodyJson, model)
```

The wrapper automatically:
- Detects 401/403 auth errors
- Refreshes keys from Firestore
- Retries with fresh keys
- Handles failures gracefully

## Benefits

✅ **Zero Downtime** - No service interruption during key rotation  
✅ **Smooth UX** - Users never see errors or crashes  
✅ **Automatic** - No manual intervention needed  
✅ **Reliable** - Multiple fallback mechanisms  
✅ **Fast** - Minimal latency impact (only on first call after rotation)