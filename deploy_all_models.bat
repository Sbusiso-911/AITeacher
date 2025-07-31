@echo off
echo 🚀 Deploying All AI Models from Android App to Webapp...
echo =======================================================
echo.
echo 📱 AI Models being added to webapp:
echo.
echo 💚 FREE TIER:
echo   • GPT-4.1 nano - Ultra-fast and cost-effective
echo.
echo 🔵 BASIC TIER:
echo   • GPT-4.1 mini - Compact but powerful
echo   • GPT-4o mini - Efficient multimodal model
echo   • Gemini 1.5 Flash - Fast Google model with long context
echo   • Gemini 2.0 Flash - Next-generation Google model
echo.
echo 🟣 PRO TIER:
echo   • Claude Sonnet 4 - Balanced Claude model with excellent reasoning
echo   • GPT-4.1 - Advanced reasoning and problem-solving
echo   • o4-mini - Reasoning-focused model
echo.
echo 🟠 PREMIUM TIER:
echo   • o3 - Most advanced reasoning model
echo   • GPT-4o Realtime (Text) - Real-time conversation model
echo   • GPT-image-1 - Advanced image generation model
echo.
echo 🔴 ULTRA PREMIUM TIER:
echo   • Claude Opus 4 - Most capable Claude model
echo   • GPT-4o Realtime (Audio) - Real-time audio conversation
echo.

echo 🔍 Checking Firebase authentication...
firebase projects:list >nul 2>&1
if errorlevel 1 (
    echo ❌ Please login to Firebase first:
    echo firebase login
    pause
    exit /b 1
)

echo ✅ Firebase authentication verified
echo.

echo 📦 Deploying Firebase Functions...
firebase deploy --only functions
if errorlevel 1 (
    echo ❌ Functions deployment failed
    pause
    exit /b 1
)

echo ✅ Functions deployed successfully
echo.

echo 🌐 Deploying Webapp...
firebase deploy --only hosting
if errorlevel 1 (
    echo ❌ Webapp deployment failed
    pause
    exit /b 1
)

echo.
echo 🎉 ALL AI MODELS DEPLOYED SUCCESSFULLY!
echo ======================================
echo.
echo ✅ Features Added:
echo   • 13 AI models from Android app now available in webapp
echo   • Subscription tier-based model access
echo   • Enhanced model cards with pricing and capabilities
echo   • Star ratings for model quality (1-5 stars)
echo   • Daily usage limits displayed
echo   • Cost per 1M tokens pricing information
echo   • Tier badges (FREE, BASIC, PRO, PREMIUM, ULTRA)
echo.
echo 🧪 Test the Models:
echo   1. Visit your webapp
echo   2. Sign in or use guest mode
echo   3. Click "Switch Model" button
echo   4. See all 13 AI models with detailed information
echo   5. Models are filtered by subscription tier
echo.
echo 🔄 Perfect Android ↔️ Webapp sync achieved!

pause