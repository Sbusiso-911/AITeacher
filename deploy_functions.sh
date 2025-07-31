#!/bin/bash

# Navigate to the webapp directory
cd "/mnt/c/Users/User/AITeacher"

echo "Deploying Firebase Functions with mobile sync support..."
echo "Make sure you're logged in to Firebase CLI first:"
echo "firebase login"
echo ""

# Deploy the functions
firebase deploy --only functions

echo ""
echo "Firebase Functions deployment completed!"
echo "The following functions are now available:"
echo "- saveChatMessage (existing)"
echo "- getUserStats (existing)" 
echo "- updateWebappProfile (existing)"
echo "- getWebappProfile (existing)"
echo "- syncUserData (existing)"
echo "- generateWebappToken (existing)"
echo "- validateWebappToken (existing)"
echo "- clearChat (existing)"
echo "- getChatHistory (existing)"
echo "- syncMobileProfile (NEW - fixes mobile sync)"
echo "- syncMobileChatHistory (NEW - fixes mobile sync)"
echo "- getAvailableModels (NEW - fixes webapp models)"
echo "- chat (NEW - fixes webapp AI chat)"
echo ""
echo "These new functions will resolve the mobile data sync issues!"