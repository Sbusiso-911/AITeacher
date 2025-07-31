#!/bin/bash

# Navigate to the webapp directory
cd "/mnt/c/Users/User/AITeacher"

echo "Deploying webapp with history fix and authentication options..."
echo "Make sure you're logged in to Firebase CLI first:"
echo "firebase login"
echo ""

# Deploy the webapp
firebase deploy --only hosting

echo ""
echo "Webapp deployment completed!"
echo ""
echo "🔧 Features Updated:"
echo "✅ Chat History Display - View saved messages in history modal"
echo "✅ Guest Mode History - See current session messages"
echo "✅ Authentication Options - Guest/Create Profile/Google Sign-In"
echo "✅ Message Timeline - Shows timestamps and AI models used"
echo "✅ Auto-login prevention - No more forced Google authentication"
echo "✅ Mobile sync support - Full integration with Android app"
echo ""
echo "🎯 Users can now choose from 3 options:"
echo "- Continue as Guest (no login, demo responses)"
echo "- Create Profile (custom email/password account)"
echo "- Sign in with Google (quick authentication)"
echo "- Switch between mobile and webapp seamlessly"
echo ""
echo "Complete authentication flexibility implemented!"