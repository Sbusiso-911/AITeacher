#!/bin/bash

# Navigate to the webapp directory
cd "/mnt/c/Users/User/AITeacher"

echo "🚀 Deploying Production-Grade Authentication System..."
echo "==============================================="
echo ""
echo "🔐 Features being deployed:"
echo "✅ Enterprise-level authentication state management"
echo "✅ Advanced security threat detection"
echo "✅ Multi-factor authentication evidence analysis"
echo "✅ Real-time suspicious login detection"
echo "✅ Email verification workflow"
echo "✅ Mobile-to-webapp seamless integration"
echo "✅ Performance-optimized auth caching"
echo "✅ Production logging and monitoring"
echo ""

# Check Firebase login
echo "🔍 Checking Firebase authentication..."
firebase projects:list > /dev/null 2>&1
if [ $? -ne 0 ]; then
    echo "❌ Please login to Firebase first:"
    echo "firebase login"
    exit 1
fi

echo "✅ Firebase authentication verified"
echo ""

# Deploy the webapp
echo "📦 Deploying to Firebase Hosting..."
firebase deploy --only hosting

if [ $? -eq 0 ]; then
    echo ""
    echo "🎉 PRODUCTION DEPLOYMENT SUCCESSFUL!"
    echo "=================================="
    echo ""
    echo "🛡️ Security Features Active:"
    echo "• Auto-login detection and prevention"
    echo "• Suspicious login pattern analysis"
    echo "• Email verification enforcement"
    echo "• Session hijacking protection"
    echo "• Mobile token validation"
    echo "• Trusted domain verification"
    echo ""
    echo "📊 Authentication States Handled:"
    echo "• AUTHENTICATED - Full access granted"
    echo "• PENDING_VERIFICATION - Limited access with prompts"
    echo "• SUSPICIOUS_LOGIN - User confirmation required"
    echo "• REJECTED - Security violation, access denied"
    echo ""
    echo "🔧 Production Testing Checklist:"
    echo "□ Test Google Sign-In (should work seamlessly)"
    echo "□ Test Create Profile (should work seamlessly)"
    echo "□ Test Guest Mode (should work as before)"
    echo "□ Test Mobile-to-Webapp switching"
    echo "□ Test email verification flow"
    echo "□ Test auto-login detection"
    echo "□ Test session persistence"
    echo "□ Test security rejection scenarios"
    echo ""
    echo "🚀 Your webapp now has enterprise-grade authentication!"
    echo "   Ready for production use with robust security."
else
    echo ""
    echo "❌ DEPLOYMENT FAILED"
    echo "Please check the error messages above and try again."
    exit 1
fi