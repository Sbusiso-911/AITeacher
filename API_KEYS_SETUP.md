# 🔑 API Keys Setup Guide

## Overview
Your AI Chat Teacher app now supports multiple AI providers. Each provider requires its own API key for authentication.

## 🎯 Supported Providers

### 1. **OpenAI** (GPT models, DALL-E, Whisper, TTS)
- **Models**: GPT-4o, GPT-4 Turbo, GPT-3.5 Turbo, DALL-E 3, O1, O3-Mini
- **API Key**: `OPENAI_API_KEY`
- **Get Key**: [OpenAI Platform](https://platform.openai.com/api-keys)

### 2. **DeepSeek** (Chinese AI models)
- **Models**: DeepSeek-Chat, DeepSeek-R1 (Reasoning)
- **API Key**: `DEEPSEEK_API_KEY`
- **Get Key**: [DeepSeek Platform](https://platform.deepseek.com)

### 3. **Google Gemini** (Google's AI models)
- **Models**: Gemini 2.5 Flash, Gemini Pro
- **API Key**: `GEMINI_API_KEY`
- **Get Key**: [Google AI Studio](https://aistudio.google.com/apikey)

### 4. **Anthropic** (Claude models)
- **Models**: Claude Sonnet 4, Claude Opus 4
- **API Key**: `ANTHROPIC_API_KEY`
- **Get Key**: [Anthropic Console](https://console.anthropic.com)

## 🔧 Setup Instructions

### Method 1: Environment Variables (Recommended)
```bash
# Add to your ~/.bashrc or ~/.zshrc
export OPENAI_API_KEY="your-openai-api-key"
export DEEPSEEK_API_KEY="your-deepseek-api-key"
export GEMINI_API_KEY="your-gemini-api-key"
export ANTHROPIC_API_KEY="your-anthropic-api-key"
```

### Method 2: gradle.properties (Project-wide)
Add to your `gradle.properties` file:
```properties
OPENAI_API_KEY=your-openai-api-key
DEEPSEEK_API_KEY=your-deepseek-api-key
GEMINI_API_KEY=your-gemini-api-key
ANTHROPIC_API_KEY=your-anthropic-api-key
```

### Method 3: BuildConfig (Direct)
Add to your `build.gradle.kts` (app level):
```kotlin
android {
    buildTypes {
        debug {
            buildConfigField("String", "API_KEY", "\"${project.findProperty("OPENAI_API_KEY") ?: ""}\"")
            buildConfigField("String", "DEEPSEEK_API_KEY", "\"${project.findProperty("DEEPSEEK_API_KEY") ?: ""}\"")
            buildConfigField("String", "GEMINI_API_KEY", "\"${project.findProperty("GEMINI_API_KEY") ?: ""}\"")
            buildConfigField("String", "ANTHROPIC_API_KEY", "\"${project.findProperty("ANTHROPIC_API_KEY") ?: ""}\"")
        }
        release {
            // Same for release
        }
    }
}
```

## 🚀 Getting Your API Keys

### OpenAI API Key
1. Go to [OpenAI Platform](https://platform.openai.com/api-keys)
2. Sign up or log in
3. Click "Create new secret key"
4. Copy the key (starts with `sk-`)

### DeepSeek API Key
1. Go to [DeepSeek Platform](https://platform.deepseek.com)
2. Create an account
3. Navigate to API Keys section
4. Generate a new API key

### Google Gemini API Key
1. Go to [Google AI Studio](https://aistudio.google.com/apikey)
2. Sign in with your Google account
3. Click "Create API Key"
4. Copy the generated key

### Anthropic API Key
1. Go to [Anthropic Console](https://console.anthropic.com)
2. Create an account
3. Navigate to API Keys
4. Generate a new key

## 🔒 Security Best Practices

### ❌ Don't Do This
```kotlin
// Never hardcode API keys in your source code
val apiKey = "sk-1234567890abcdef" // BAD!
```

### ✅ Do This Instead
```kotlin
// Use BuildConfig for secure key management
val apiKey = BuildConfig.API_KEY
```

### 🛡️ Security Checklist
- [ ] Never commit API keys to version control
- [ ] Use environment variables or secure build configuration
- [ ] Add `local.properties` to `.gitignore`
- [ ] Use different keys for development and production
- [ ] Regularly rotate your API keys
- [ ] Monitor API usage and costs

## 📊 Cost Management

### Free Tiers Available
- **OpenAI**: $5 free credits for new accounts
- **DeepSeek**: Free tier with usage limits
- **Google Gemini**: Free tier with daily limits
- **Anthropic**: Free tier with message limits

### Cost Optimization
- **Use cheaper models for simple tasks** (GPT-3.5 Turbo, Gemini Flash)
- **Implement usage limits** per user/subscription tier
- **Monitor API usage** through provider dashboards
- **Cache responses** when appropriate

## 🧪 Testing Your Setup

### Test Each Provider
```kotlin
// Test OpenAI
val openaiModel = AIModel.GPT_35_TURBO
val openaiKey = getApiKeyForModel(openaiModel)
println("OpenAI Key: ${openaiKey.take(10)}...")

// Test DeepSeek
val deepseekModel = AIModel.DEEPSEEK
val deepseekKey = getApiKeyForModel(deepseekModel)
println("DeepSeek Key: ${deepseekKey.take(10)}...")

// Test Google Gemini
val geminiModel = AIModel.GEMINI
val geminiKey = getApiKeyForModel(geminiModel)
println("Gemini Key: ${geminiKey.take(10)}...")
```

## 🚨 Troubleshooting

### Common Issues

**"API Key not found"**
- Check if the key is properly set in BuildConfig
- Verify the key format (OpenAI keys start with `sk-`)
- Ensure the key is not expired

**"Invalid API Key"**
- Double-check the key spelling
- Verify the key has the correct permissions
- Make sure you're using the right key for the right provider

**"Rate limit exceeded"**
- You've hit the API usage limits
- Check your provider's dashboard for usage
- Consider upgrading your plan or implementing usage limits

### Debug API Keys
```kotlin
private fun debugApiKeys() {
    Log.d("API_KEYS", "OpenAI: ${BuildConfig.API_KEY.take(10)}...")
    Log.d("API_KEYS", "DeepSeek: ${BuildConfig.DEEPSEEK_API_KEY?.take(10)}...")
    Log.d("API_KEYS", "Gemini: ${BuildConfig.GEMINI_API_KEY?.take(10)}...")
    Log.d("API_KEYS", "Anthropic: ${BuildConfig.ANTHROPIC_API_KEY?.take(10)}...")
}
```

## 📈 Next Steps

1. **Set up all API keys** for the providers you want to use
2. **Test each model** to ensure they work correctly
3. **Implement usage monitoring** to track costs
4. **Set up usage limits** per subscription tier
5. **Monitor performance** and costs regularly

## 🎉 Benefits of Multi-Provider Setup

- **Redundancy**: If one provider is down, others still work
- **Cost Optimization**: Choose the cheapest model for each task
- **Feature Diversity**: Access unique features from each provider
- **Better User Experience**: More model options for users
- **Competitive Advantage**: Stay ahead with latest AI capabilities

Remember to keep your API keys secure and monitor your usage to avoid unexpected charges!