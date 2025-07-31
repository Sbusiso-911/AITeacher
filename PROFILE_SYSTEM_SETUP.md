# AI Chat Teacher - Profile System Setup Guide

## Overview
This guide explains how to integrate and use the comprehensive user profile system that has been implemented for the AI Chat Teacher app.

## 🚀 Features Implemented

### 1. **User Authentication System**
- **Secure Registration/Login**: Email/password with encrypted storage
- **Password Reset**: Temporary password generation system
- **Session Management**: JWT-like tokens with expiration
- **Remember Me**: Persistent login sessions
- **Account Management**: Profile updates and account deletion

### 2. **User Profile Management**
- **Profile Data**: Full name, email, profile picture, preferences
- **Theme Settings**: Dark/light/system theme preferences
- **Language Settings**: Multi-language support
- **Notification Settings**: Customizable notification preferences
- **Auto-backup**: Automatic data backup preferences

### 3. **Advanced Chat History System**
- **Comprehensive Storage**: All conversations with metadata
- **Search & Filter**: Search by content, filter by date/model/category
- **Categories & Tags**: Organize conversations by topic
- **Favorites**: Mark important conversations
- **Export Options**: Text, JSON, and PDF export capabilities
- **Bulk Operations**: Select multiple chats for actions

### 4. **Subscription Management**
- **Three-Tier Plans**: Free, Pro, Premium with different features
- **Usage Tracking**: Real-time monitoring of messages, tokens, storage
- **Billing History**: Complete transaction records
- **Free Trials**: 7-day trial system
- **Feature Gating**: Automatic restriction based on subscription level

### 5. **Usage Analytics**
- **Detailed Tracking**: Messages, tokens, features used, time spent
- **Analytics Dashboard**: Visual representation of usage patterns
- **Storage Monitoring**: Track and manage storage usage
- **Performance Metrics**: Response times and efficiency tracking

## 🔧 Integration Guide

### Step 1: Basic Integration

Add this to your activity where you want to check user status:

```kotlin
import com.playstudio.aiteacher.profile.ProfileIntegration

class YourActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Check if user is logged in
        if (ProfileIntegration.isUserLoggedIn(this)) {
            // User is logged in, proceed normally
            setupLoggedInUI()
        } else {
            // User not logged in, show login option
            ProfileIntegration.showLoginRequired(this)
        }
    }
}
```

### Step 2: Feature Gating

Check if user has premium features:

```kotlin
lifecycleScope.launch {
    if (ProfileIntegration.hasSubscriptionFeature(this@YourActivity, "elite_tools")) {
        // User has access to elite tools
        enableEliteTools()
    } else {
        // Show subscription required dialog
        ProfileIntegration.showSubscriptionRequired(this@YourActivity, "Elite Tools")
    }
}
```

### Step 3: Usage Tracking

Record usage for analytics:

```kotlin
lifecycleScope.launch {
    // Before sending a message, check if user can send
    if (ProfileIntegration.canSendMessage(this@YourActivity)) {
        // Send message
        sendMessage()
        
        // Record usage
        ProfileIntegration.recordUsage(
            context = this@YourActivity,
            messagesSent = 1,
            tokensConsumed = 150,
            featuresUsed = listOf("text_generation"),
            eliteToolsUsed = mapOf("grammar_check" to 1)
        )
    } else {
        // User has reached message limit
        ProfileIntegration.showSubscriptionRequired(this@YourActivity, "More Messages")
    }
}
```

### Step 4: Navigation Integration

Add profile menu items to your existing menu:

```kotlin
override fun onCreateOptionsMenu(menu: Menu?): Boolean {
    menuInflater.inflate(R.menu.your_menu, menu)
    
    // Add profile menu items
    menu?.add(0, R.id.action_profile, 100, "Profile")
    menu?.add(0, R.id.action_chat_history, 101, "Chat History")
    menu?.add(0, R.id.action_subscription, 102, "Subscription")
    
    return true
}

override fun onOptionsItemSelected(item: MenuItem): Boolean {
    return when (item.itemId) {
        R.id.action_profile -> {
            ProfileIntegration.navigateToProfile(this)
            true
        }
        R.id.action_chat_history -> {
            ProfileIntegration.navigateToChatHistory(this)
            true
        }
        R.id.action_subscription -> {
            ProfileIntegration.navigateToSubscription(this)
            true
        }
        else -> super.onOptionsItemSelected(item)
    }
}
```

## 🎨 UI Components

### Profile Screen
- **Beautiful glassmorphism design** matching your app theme
- **Subscription status** with usage progress bars
- **Statistics cards** showing chat/message counts
- **Quick actions** for settings and logout

### Chat History Screen
- **Advanced search** with real-time filtering
- **Category and model filters** for organization
- **Bulk selection** for mass operations
- **Export capabilities** for data portability

### Login/Register Screens
- **Elegant forms** with validation
- **Secure authentication** with encrypted storage
- **Responsive design** for all screen sizes

## 📊 Database Schema

The system uses Room database with the following entities:

### Users Table
- `user_id` (Primary Key)
- `email`, `password_hash`, `full_name`
- `profile_picture_url`
- `preferences` (theme, language, notifications)
- `created_at`, `updated_at`, `last_login`

### Chat Sessions Table
- `session_id` (Primary Key)
- `user_id` (Foreign Key)
- `title`, `ai_model_used`, `category`
- `tags`, `is_favorite`, `is_archived`
- `message_count`, `conversation_summary`

### Chat Messages Table
- `message_id` (Primary Key)
- `session_id` (Foreign Key)
- `sender_type` (user/ai)
- `content`, `timestamp`
- `token_count`, `processing_time_ms`

### Subscriptions Table
- `subscription_id` (Primary Key)
- `user_id` (Foreign Key)
- `plan_type`, `status`, `billing_cycle`
- `start_date`, `end_date`, `features_included`

### Usage Analytics Table
- `usage_id` (Primary Key)
- `user_id` (Foreign Key)
- `date`, `messages_sent`, `tokens_consumed`
- `features_accessed`, `elite_tools_used`

## 🔐 Security Features

### Encrypted Storage
- Uses `androidx.security.security-crypto` for encrypted SharedPreferences
- Passwords are hashed using SHA-256
- Sensitive data is encrypted at rest

### Authentication
- JWT-like token system with expiration
- Session management with automatic cleanup
- Remember me functionality with secure refresh tokens

### Data Protection
- Input validation and sanitization
- Secure API communication
- Privacy-focused data collection

## 🎯 Usage Limits

### Free Plan (50 messages/month)
- Basic AI models
- Limited chat history
- Community support

### Pro Plan (500 messages/month)
- Advanced AI models
- Unlimited chat history
- Elite tools access
- Voice features
- Image processing
- Email support

### Premium Plan (Unlimited)
- All AI models
- All elite tools
- Advanced voice features
- Priority support
- Offline access
- Early access to new features

## 🚦 Getting Started

### 1. Launch the App
- New users will see the login screen
- Existing users can continue as guests
- Registration creates a new profile

### 2. Profile Setup
- Complete profile information
- Choose theme and language preferences
- Set up notification preferences

### 3. Start Chatting
- All conversations are automatically saved
- Usage is tracked in real-time
- Subscription limits are enforced

### 4. Manage Subscription
- View current plan and usage
- Upgrade/downgrade plans
- Access billing history

## 🔧 Maintenance

### Database Migrations
- Automatic schema updates
- Data preservation during upgrades
- Fallback to destructive migration if needed

### Analytics Cleanup
- Old usage data is automatically cleaned
- Configurable retention periods
- Privacy-compliant data management

### Performance Optimization
- Efficient database queries
- Lazy loading for large datasets
- Background processing for analytics

## 📱 User Experience

### Glassmorphism Design
- Consistent with your app's visual theme
- Smooth animations and transitions
- Responsive layout for all devices

### Intuitive Navigation
- Clear menu structure
- Contextual actions
- Easy access to all features

### Accessibility
- Screen reader support
- High contrast mode
- Keyboard navigation

## 🎉 Ready to Use!

The profile system is now fully integrated and ready to use. All components work together seamlessly to provide a comprehensive user experience while maintaining your app's existing design language and functionality.

## 📞 Support

For any questions or issues with the profile system, refer to the code documentation or check the implementation files in the `profile/` package.