# 🔧 BuildConfig Setup for API Keys

## 🚨 Current Issue
The app is missing `DEEPSEEK_API_KEY` and `GEMINI_API_KEY` in BuildConfig, causing compilation errors.

## ✅ Solution: Add API Keys to BuildConfig

### Step 1: Update build.gradle.kts (app level)

Add these lines to your `app/build.gradle.kts` file:

```kotlin
android {
    defaultConfig {
        // ... existing config ...
        
        // Add API keys as BuildConfig fields
        buildConfigField("String", "API_KEY", "\"${project.findProperty("OPENAI_API_KEY") ?: ""}\"")
        buildConfigField("String", "DEEPSEEK_API_KEY", "\"${project.findProperty("DEEPSEEK_API_KEY") ?: ""}\"")
        buildConfigField("String", "GEMINI_API_KEY", "\"${project.findProperty("GEMINI_API_KEY") ?: ""}\"")
        buildConfigField("String", "ANTHROPIC_API_KEY", "\"${project.findProperty("ANTHROPIC_API_KEY") ?: ""}\"")
    }
    
    buildTypes {
        debug {
            // Debug-specific config
            buildConfigField("String", "API_KEY", "\"${project.findProperty("OPENAI_API_KEY") ?: ""}\"")
            buildConfigField("String", "DEEPSEEK_API_KEY", "\"${project.findProperty("DEEPSEEK_API_KEY") ?: ""}\"")
            buildConfigField("String", "GEMINI_API_KEY", "\"${project.findProperty("GEMINI_API_KEY") ?: ""}\"")
            buildConfigField("String", "ANTHROPIC_API_KEY", "\"${project.findProperty("ANTHROPIC_API_KEY") ?: ""}\"")
        }
        
        release {
            // Release-specific config  
            buildConfigField("String", "API_KEY", "\"${project.findProperty("OPENAI_API_KEY") ?: ""}\"")
            buildConfigField("String", "DEEPSEEK_API_KEY", "\"${project.findProperty("DEEPSEEK_API_KEY") ?: ""}\"")
            buildConfigField("String", "GEMINI_API_KEY", "\"${project.findProperty("GEMINI_API_KEY") ?: ""}\"")
            buildConfigField("String", "ANTHROPIC_API_KEY", "\"${project.findProperty("ANTHROPIC_API_KEY") ?: ""}\"")
        }
    }
}
```

### Step 2: Add API Keys to gradle.properties

Create or update `gradle.properties` in your project root:

```properties
# API Keys for AI providers
OPENAI_API_KEY=your-openai-api-key-here
DEEPSEEK_API_KEY=your-deepseek-api-key-here
GEMINI_API_KEY=your-gemini-api-key-here
ANTHROPIC_API_KEY=your-anthropic-api-key-here
```

### Step 3: Update .gitignore

Make sure your `.gitignore` includes:
```
# API Keys
local.properties
gradle.properties
*.keystore
```

### Step 4: Alternative - Direct BuildConfig (Less Secure)

If you prefer hardcoding (NOT recommended for production):

```kotlin
android {
    defaultConfig {
        buildConfigField("String", "API_KEY", "\"sk-your-openai-key\"")
        buildConfigField("String", "DEEPSEEK_API_KEY", "\"your-deepseek-key\"")
        buildConfigField("String", "GEMINI_API_KEY", "\"your-gemini-key\"")
        buildConfigField("String", "ANTHROPIC_API_KEY", "\"your-anthropic-key\"")
    }
}
```

## 🎯 After Setup, Update ChatFragment

Once BuildConfig fields are added, you can update the `getApiKeyForModel` method:

```kotlin
private fun getApiKeyForModel(model: AIModel): String {
    return when (model.provider) {
        "OpenAI" -> BuildConfig.API_KEY
        "DeepSeek" -> BuildConfig.DEEPSEEK_API_KEY.takeIf { it.isNotEmpty() } ?: BuildConfig.API_KEY
        "Anthropic" -> anthropicApiKey
        "Google" -> BuildConfig.GEMINI_API_KEY.takeIf { it.isNotEmpty() } ?: BuildConfig.API_KEY
        else -> BuildConfig.API_KEY
    }
}
```

## 🔄 Current Temporary Fix

For now, the app will use OpenAI keys as fallback for all providers. This means:
- ✅ **OpenAI models** will work correctly
- ⚠️ **DeepSeek models** will fail (wrong API endpoint + wrong key)
- ⚠️ **Gemini models** will fail (wrong API endpoint + wrong key)
- ✅ **Anthropic models** will work (separate key already configured)

## 📋 Quick Setup Checklist

1. **Add BuildConfig fields** to `build.gradle.kts`
2. **Add API keys** to `gradle.properties`
3. **Update .gitignore** to exclude sensitive files
4. **Get API keys** from respective providers:
   - OpenAI: [platform.openai.com/api-keys](https://platform.openai.com/api-keys)
   - DeepSeek: [platform.deepseek.com](https://platform.deepseek.com)
   - Gemini: [aistudio.google.com/apikey](https://aistudio.google.com/apikey)
   - Anthropic: [console.anthropic.com](https://console.anthropic.com)
5. **Clean and rebuild** your project
6. **Test each provider** individually

## 🚀 Benefits After Setup

- ✅ **All AI providers work correctly**
- ✅ **Proper API routing** to respective endpoints
- ✅ **Secure key management**
- ✅ **No compilation errors**
- ✅ **Production-ready** configuration

## 🔒 Security Notes

- **Never commit API keys** to version control
- **Use different keys** for debug/release builds
- **Rotate keys regularly** for security
- **Monitor API usage** to prevent abuse
- **Set usage limits** where possible

Remember: The current fallback approach means DeepSeek and Gemini models won't work until you add their respective API keys!