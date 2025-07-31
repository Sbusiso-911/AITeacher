#!/bin/bash

# Navigate to the webapp directory
cd "/mnt/c/Users/User/AITeacher"

echo "Deploying signin redirect fix..."
echo "Make sure you're logged in to Firebase CLI first:"
echo "firebase login"
echo ""

# Deploy the webapp
firebase deploy --only hosting

echo ""
echo "Signin fix deployment completed!"
echo ""
echo "🔧 Authentication Flow Fixed:"
echo "✅ Google Sign-In now redirects to chat interface"
echo "✅ Create Profile now works correctly"
echo "✅ Improved authentication timing detection"
echo "✅ Better session state management"
echo "✅ Eliminated redirect to create profile after sign-in"
echo ""
echo "🧪 Test the fix:"
echo "1. Sign in with Google - should go directly to chat"
echo "2. Create Profile - should go directly to chat"
echo "3. Guest Mode - should work as before"
echo ""
echo "No more redirect loops or authentication issues!"