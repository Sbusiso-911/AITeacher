# AI Teacher App - Context Optimization Guide

## Purpose
This file helps software engineers understand what information is needed to build and optimize the AI Teacher app efficiently. It provides context summaries and key technical details to minimize repetitive explanations and maximize development productivity.

---

## 🔧 CORE ARCHITECTURE OVERVIEW

### Primary Technologies
- **Language**: Kotlin (Android)
- **Audio Processing**: OpenAI Realtime API, AudioTrack, MediaRecorder
- **Networking**: OkHttp WebSocket connections
- **Database**: Room (profile system), SharedPreferences (settings)
- **UI**: Fragment-based with RecyclerView adapters
- **Async**: Kotlin Coroutines with Dispatchers.Main/IO

### Key Components
1. **ChatFragment.kt** - Main chat interface with voice interaction
2. **RealtimeVoiceAgent.kt** - WebSocket-based real-time voice communication
3. **AudioApiHandler.kt** - OpenAI audio API integration (TTS, STT, audio chat)
4. **AudioControlsView.kt** - Voice recording and playback UI component
5. **Profile System** - User authentication and subscription management

---

## 🎯 CRITICAL IMPLEMENTATION DETAILS

### Voice System Architecture
```
User Speech → AudioRecord → RealtimeVoiceAgent → OpenAI Realtime API
                                                      ↓
AI Response ← AudioTrack ← ChatFragment ← WebSocket Audio Stream
```

### Known Working Solutions
- **WebSocket Connection**: Uses synchronized continuation to prevent "Already resumed" crashes
- **AudioTrack Management**: Single streaming track (MODE_STREAM) prevents dual voice conflicts
- **Voice Detection**: High thresholds (5000+ audio level) with 5s debounce prevents AI self-interruption
- **Fragment Safety**: Always check `isAdded && context != null` before UI operations

### Anti-Patterns to Avoid
- ❌ Multiple AudioTrack instances in STATIC mode (causes dual voices)
- ❌ Low voice detection thresholds (causes AI self-interruption)
- ❌ Missing thread safety in WebSocket callbacks
- ❌ UI operations without fragment lifecycle checks

---

## 📋 CURRENT STATE SUMMARY

### ✅ WORKING FEATURES
- Real-time speech-to-speech voice interaction via OpenAI Realtime API
- Single voice output with streaming AudioTrack
- Anti-self-interruption protection with multi-layer voice detection
- Crash-resistant WebSocket connection handling
- Fragment lifecycle-safe UI operations
- Cost-efficient API usage (eliminated duplicate calls)

### 🔄 ACTIVE INTEGRATIONS
- **OpenAI Realtime API**: WebSocket connection for low-latency voice chat
- **Profile System**: User authentication with subscription management
- **Cost Tracking**: Token usage monitoring for different AI models
- **Theme System**: Multiple UI themes with glassmorphism design

### 🚨 KNOWN LIMITATIONS
- Audio latency: Larger buffers may introduce slight delay
- Voice detection: Fixed thresholds may not work in all noise environments
- Model support: Limited to OpenAI models with audio capabilities

---

## 🛠️ DEVELOPMENT PATTERNS

### Error Handling Strategy
```kotlin
// WebSocket connections
return@withContext suspendCoroutine { continuation ->
    var isResumed = false
    // ... connection logic
    synchronized(this@RealtimeVoiceAgent) {
        if (!isResumed) {
            isResumed = true
            continuation.resume(result)
        }
    }
}

// Fragment operations
if (isAdded && context != null) {
    // Safe to perform UI operations
}

// AudioTrack operations
if (audioTrack?.playState == AudioTrack.PLAYSTATE_PLAYING) {
    audioTrack.write(audioData, 0, audioData.size, AudioTrack.WRITE_NON_BLOCKING)
}
```

### Voice Detection Implementation
```kotlin
// Multi-layer interruption protection
private var audioLevelThreshold = 5000 // High threshold
private var lastAiSpeakStartTime = 0L
private var lastInterruptTime = 0L

// Interruption logic with multiple safety checks
if (isAICurrentlySpeaking && userSpeechDetected) {
    if (currentTime - lastInterruptTime > 5000) { // 5s debounce
        if (currentTime - lastAiSpeakStartTime > 1000) { // 1s protection
            if (audioLevel > audioLevelThreshold * 1.5) { // Double threshold
                // Only then interrupt
            }
        }
    }
}
```

---

## 📁 FILE LOCATION REFERENCE

### Core Voice Files
- `ChatFragment.kt` - Lines 7800-8300: Voice interaction logic
- `RealtimeVoiceAgent.kt` - Complete file: WebSocket voice agent implementation
- `AudioApiHandler.kt` - Complete file: OpenAI audio API wrapper
- `AudioControlsView.kt` - Complete file: Voice UI component

### Configuration Files
- `BuildConfig.API_KEY` - OpenAI API key configuration
- `app/build.gradle.kts` - Dependencies and build configuration
- `AndroidManifest.xml` - Permissions and app configuration

### Key Database Files
- `profile/ProfileDatabase.kt` - User data and authentication
- `pricing/CostTracker.kt` - API usage and cost monitoring
- `history/HistoryRepository.kt` - Chat history management

---

## 🔍 DEBUGGING STRATEGIES

### Common Issues & Solutions
1. **"Already resumed" crash**: Check WebSocket callback synchronization
2. **Dual voices**: Verify only one AudioTrack is active (disable old implementations)
3. **AI self-interruption**: Increase voice detection thresholds and debounce times
4. **Fragment crashes**: Add lifecycle checks before UI operations
5. **AudioTrack underruns**: Increase buffer size and use background threads

### Logging Patterns
- **Voice Detection**: `Log.d("ChatFragment", "Voice level: $audioLevel, threshold: $threshold")`
- **WebSocket**: `Log.d("RealtimeVoiceAgent", "WebSocket state: $connectionState")`
- **AudioTrack**: `Log.d("AudioPlayback", "Track state: ${audioTrack.playState}")`

### Performance Monitoring
- Monitor API token usage via CostTracker
- Track AudioTrack buffer health
- Watch for excessive coroutine launches
- Monitor fragment lifecycle state changes

---

## 💡 OPTIMIZATION GUIDELINES

### When to Use Each Tool
- **Grep/Glob**: Finding specific code patterns or function definitions
- **Read**: Examining complete file contents for context
- **Task**: Complex searches across multiple files or keywords
- **Edit/MultiEdit**: Making precise code changes
- **TodoWrite**: Planning multi-step implementations

### Context Management Rules
1. **Read existing implementation** before suggesting changes
2. **Check for related files** that might be affected
3. **Verify permissions and dependencies** in build files
4. **Test critical paths** like audio recording and WebSocket connections
5. **Maintain backward compatibility** unless explicitly asked to break it

### Code Review Checklist
- [ ] Thread safety in WebSocket callbacks
- [ ] Fragment lifecycle checks before UI operations
- [ ] AudioTrack state verification before operations
- [ ] Proper error handling with user-friendly messages
- [ ] Logging for debugging without performance impact
- [ ] Resource cleanup in onDestroy/onPause methods

---

## 🎨 UI/UX PATTERNS

### Theme System
- **Glassmorphism**: Primary theme with transparent backgrounds
- **Color Schemes**: Blue-green gradients with glass effects
- **Responsive Design**: Adapts to different screen sizes and orientations

### Voice Interaction UI
- **Recording Indicator**: Pulsing animation during voice input
- **Audio Level Visualization**: Real-time visual feedback
- **State Indicators**: Clear visual cues for listening/thinking/speaking states

---

## 📚 EXTERNAL DEPENDENCIES

### OpenAI APIs
- **Realtime API**: `wss://api.openai.com/v1/realtime` (WebSocket)
- **Chat Completions**: `https://api.openai.com/v1/chat/completions` (HTTP)
- **Audio APIs**: Speech-to-text, text-to-speech, audio chat completions

### Android Permissions Required
- `RECORD_AUDIO` - Voice input
- `INTERNET` - API communications
- `WRITE_EXTERNAL_STORAGE` - File operations (API < 29)

### Key Gradle Dependencies
- OkHttp for networking
- Room for database
- Kotlin Coroutines for async operations
- Glide for image loading
- Lottie for animations

---

## 🚀 QUICK START FOR NEW FEATURES

### Adding New Voice Capabilities
1. Check if feature requires Realtime API or standard Audio API
2. Update `RealtimeVoiceAgent.kt` for real-time features
3. Update `AudioApiHandler.kt` for standard audio processing
4. Add UI controls to `AudioControlsView.kt`
5. Integrate with `ChatFragment.kt` main flow

### Adding New AI Models
1. Update `pricing/AIModel.kt` with model capabilities
2. Add cost information to `CostTracker.kt`
3. Update UI selectors in adapters
4. Test audio compatibility if applicable

### Database Changes
1. Update entity classes in `profile/` or `history/`
2. Increment database version number
3. Add migration logic
4. Update repository and DAO interfaces

---

This context optimization guide should be updated whenever significant architectural changes are made. It serves as a quick reference to avoid re-explaining common patterns and focus on new development challenges.