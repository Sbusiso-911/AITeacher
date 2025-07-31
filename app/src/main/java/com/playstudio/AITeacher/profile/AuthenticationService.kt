package com.playstudio.aiteacher.profile

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.MessageDigest
import java.util.*

class AuthenticationService(private val context: Context) {
    
    private val database = com.playstudio.aiteacher.profile.ProfileDatabase.getDatabase(context)
    private val userDao = database.userDao()
    private val usageAnalyticsDao = database.usageAnalyticsDao()
    
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    
    private val encryptedPrefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "auth_preferences",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    
    companion object {
        private const val TAG = "AuthenticationService"
        private const val PREF_USER_ID = "user_id"
        private const val PREF_AUTH_TOKEN = "auth_token"
        private const val PREF_REFRESH_TOKEN = "refresh_token"
        private const val PREF_LOGIN_TIME = "login_time"
        private const val PREF_REMEMBER_ME = "remember_me"
        private const val TOKEN_EXPIRY_HOURS = 24
        private const val REFRESH_TOKEN_EXPIRY_DAYS = 30
    }
    
    data class AuthResult(
        val success: Boolean,
        val user: UserEntity? = null,
        val message: String = "",
        val token: String? = null
    )
    
    data class RegistrationData(
        val email: String,
        val password: String,
        val fullName: String,
        val preferredAiModels: List<String> = emptyList(),
        val themePreference: String = "system",
        val languageSetting: String = "en",
        val newsletterSubscribed: Boolean = false,
        val productUpdatesSubscribed: Boolean = false,
        val promotionalEmailsSubscribed: Boolean = false
    )
    
    data class GoogleSignInData(
        val email: String,
        val fullName: String,
        val googleId: String,
        val profilePictureUrl: String? = null,
        val newsletterSubscribed: Boolean = false,
        val productUpdatesSubscribed: Boolean = false,
        val promotionalEmailsSubscribed: Boolean = false
    )
    
    suspend fun register(registrationData: RegistrationData): AuthResult {
        return try {
            // Validate input
            if (!isValidEmail(registrationData.email)) {
                return AuthResult(false, message = "Invalid email format")
            }
            
            if (!isValidPassword(registrationData.password)) {
                return AuthResult(false, message = "Password must be at least 8 characters with uppercase, lowercase, number, and special character")
            }
            
            // Check if user already exists
            val existingUser = userDao.getUserByEmail(registrationData.email)
            if (existingUser != null) {
                return AuthResult(false, message = "User already exists with this email")
            }
            
            // Create new user
            val newUser = UserEntity(
                userId = 0, // Room will auto-generate
                email = registrationData.email,
                passwordHash = hashPassword(registrationData.password),
                fullName = registrationData.fullName,
                preferredAiModels = registrationData.preferredAiModels,
                themePreference = registrationData.themePreference,
                languageSetting = registrationData.languageSetting,
                createdAt = Date(),
                updatedAt = Date(),
                authProvider = "email",
                newsletterSubscribed = registrationData.newsletterSubscribed,
                productUpdatesSubscribed = registrationData.productUpdatesSubscribed,
                promotionalEmailsSubscribed = registrationData.promotionalEmailsSubscribed
            )
            
            // Insert user and get generated ID
            val userId = userDao.insertUser(newUser)
            val savedUser = newUser.copy(userId = userId)
            
            // Generate authentication token
            val token = generateAuthToken(savedUser)
            saveAuthSession(savedUser, token)
            
            // Track registration analytics
            val analytics = UsageAnalyticsEntity(
                userId = userId,
                date = Date(),
                chatSessionsStarted = 0,
                featuresAccessed = listOf("registration")
            )
            usageAnalyticsDao.insertUsageAnalytics(analytics)
            
            AuthResult(true, savedUser, "Registration successful", token)
        } catch (e: Exception) {
            Log.e(TAG, "Registration error", e)
            AuthResult(false, message = "Registration failed: ${e.message}")
        }
    }
    
    suspend fun login(email: String, password: String, rememberMe: Boolean = false): AuthResult {
        return try {
            // Validate input
            if (!isValidEmail(email)) {
                return AuthResult(false, message = "Invalid email format")
            }
            
            val passwordHash = hashPassword(password)
            val user = userDao.authenticateUser(email, passwordHash)
            
            if (user == null) {
                return AuthResult(false, message = "Invalid email or password")
            }
            
            if (!user.isActive) {
                return AuthResult(false, message = "Account is deactivated. Please contact support.")
            }
            
            // Update last login
            val updatedUser = user.copy(
                lastLogin = Date(),
                updatedAt = Date()
            )
            userDao.updateUser(updatedUser)
            
            // Generate authentication token
            val token = generateAuthToken(updatedUser)
            saveAuthSession(updatedUser, token, rememberMe)
            
            // Track login analytics
            val analytics = UsageAnalyticsEntity(
                userId = user.userId,
                date = Date(),
                featuresAccessed = listOf("login"),
                chatSessionsStarted = 1
            )
            usageAnalyticsDao.insertUsageAnalytics(analytics)
            
            AuthResult(true, updatedUser, "Login successful", token)
        } catch (e: Exception) {
            Log.e(TAG, "Login error", e)
            AuthResult(false, message = "Login failed: ${e.message}")
        }
    }
    
    suspend fun logout(): Boolean {
        return try {
            clearAuthSession()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Logout error", e)
            false
        }
    }
    
    suspend fun signInWithGoogle(googleSignInData: GoogleSignInData): AuthResult {
        return try {
            val existingUser = userDao.getUserByEmail(googleSignInData.email)
            
            val user = if (existingUser != null) {
                // Update existing user with Google data
                val updatedUser = existingUser.copy(
                    fullName = googleSignInData.fullName,
                    profilePictureUrl = googleSignInData.profilePictureUrl ?: existingUser.profilePictureUrl,
                    googleId = googleSignInData.googleId,
                    authProvider = "google",
                    lastLogin = Date(),
                    updatedAt = Date()
                )
                userDao.updateUser(updatedUser)
                updatedUser
            } else {
                // Create new user with Google data
                val newUser = UserEntity(
                    userId = 0, // Room will auto-generate
                    email = googleSignInData.email,
                    passwordHash = null,
                    fullName = googleSignInData.fullName,
                    profilePictureUrl = googleSignInData.profilePictureUrl,
                    googleId = googleSignInData.googleId,
                    authProvider = "google",
                    createdAt = Date(),
                    updatedAt = Date(),
                    lastLogin = Date(),
                    newsletterSubscribed = googleSignInData.newsletterSubscribed,
                    productUpdatesSubscribed = googleSignInData.productUpdatesSubscribed,
                    promotionalEmailsSubscribed = googleSignInData.promotionalEmailsSubscribed
                )
                val userId = userDao.insertUser(newUser)
                newUser.copy(userId = userId)
            }
            
            // Generate authentication token
            val token = generateAuthToken(user)
            saveAuthSession(user, token)
            
            // Track Google sign-in analytics
            val analytics = UsageAnalyticsEntity(
                userId = user.userId,
                date = Date(),
                featuresAccessed = listOf("google_signin"),
                chatSessionsStarted = 1
            )
            usageAnalyticsDao.insertUsageAnalytics(analytics)
            
            AuthResult(true, user, "Google Sign-In successful", token)
        } catch (e: Exception) {
            Log.e(TAG, "Google Sign-In error", e)
            AuthResult(false, message = "Google Sign-In failed: ${e.message}")
        }
    }
    
    suspend fun resetPassword(email: String): AuthResult {
        return try {
            val user = userDao.getUserByEmail(email)
            if (user == null) {
                return AuthResult(false, message = "No account found with this email address")
            }
            
            // Generate temporary password
            val tempPassword = generateTempPassword()
            val newPasswordHash = hashPassword(tempPassword)
            
            // Update user password
            val updatedUser = user.copy(
                passwordHash = newPasswordHash,
                updatedAt = Date()
            )
            userDao.updateUser(updatedUser)
            
            // Track password reset analytics
            val analytics = UsageAnalyticsEntity(
                userId = user.userId,
                date = Date(),
                featuresAccessed = listOf("password_reset")
            )
            usageAnalyticsDao.insertUsageAnalytics(analytics)
            
            // In a real app, send email with temporary password
            // For now, we'll return it in the message for testing
            AuthResult(true, message = "Temporary password: $tempPassword (In production, this would be sent via email)")
        } catch (e: Exception) {
            Log.e(TAG, "Password reset error", e)
            AuthResult(false, message = "Password reset failed: ${e.message}")
        }
    }
    
    suspend fun changePassword(currentPassword: String, newPassword: String): AuthResult {
        return try {
            val currentUser = getCurrentUser()
            if (currentUser == null) {
                return AuthResult(false, message = "No user logged in")
            }
            
            // Validate current password for email users
            if (currentUser.authProvider == "email" && currentUser.passwordHash != null) {
                val currentPasswordHash = hashPassword(currentPassword)
                if (currentPasswordHash != currentUser.passwordHash) {
                    return AuthResult(false, message = "Current password is incorrect")
                }
            }
            
            // Validate new password
            if (!isValidPassword(newPassword)) {
                return AuthResult(false, message = "New password must be at least 8 characters with uppercase, lowercase, number, and special character")
            }
            
            // Update password
            val updatedUser = currentUser.copy(
                passwordHash = hashPassword(newPassword),
                updatedAt = Date()
            )
            userDao.updateUser(updatedUser)
            
            // Track password change analytics
            val analytics = UsageAnalyticsEntity(
                userId = currentUser.userId,
                date = Date(),
                featuresAccessed = listOf("password_change")
            )
            usageAnalyticsDao.insertUsageAnalytics(analytics)
            
            AuthResult(true, updatedUser, "Password changed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Password change error", e)
            AuthResult(false, message = "Password change failed: ${e.message}")
        }
    }
    
    // Session Management
    private fun saveAuthSession(user: UserEntity, token: String, rememberMe: Boolean = false) {
        encryptedPrefs.edit().apply {
            putLong(PREF_USER_ID, user.userId)
            putString(PREF_AUTH_TOKEN, token)
            putLong(PREF_LOGIN_TIME, System.currentTimeMillis())
            putBoolean(PREF_REMEMBER_ME, rememberMe)
            
            if (rememberMe) {
                val refreshToken = generateRefreshToken(user)
                putString(PREF_REFRESH_TOKEN, refreshToken)
            }
            
            apply()
        }
    }
    
    private fun clearAuthSession() {
        encryptedPrefs.edit().clear().apply()
    }
    
    suspend fun getCurrentUser(): UserEntity? {
        return try {
            val userId = encryptedPrefs.getLong(PREF_USER_ID, 0)
            if (userId > 0) {
                return userDao.getUserById(userId)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting current user", e)
            null
        }
    }
    
    fun isLoggedIn(): Boolean {
        val userId = encryptedPrefs.getLong(PREF_USER_ID, 0)
        val token = encryptedPrefs.getString(PREF_AUTH_TOKEN, "")
        val loginTime = encryptedPrefs.getLong(PREF_LOGIN_TIME, 0)
        
        if (userId == 0L || token.isNullOrEmpty() || loginTime == 0L) {
            return false
        }
        
        // Check if token is expired
        val currentTime = System.currentTimeMillis()
        val tokenAge = currentTime - loginTime
        val tokenExpiryTime = TOKEN_EXPIRY_HOURS * 60 * 60 * 1000
        
        return tokenAge < tokenExpiryTime
    }
    
    fun getAuthToken(): String? {
        return encryptedPrefs.getString(PREF_AUTH_TOKEN, null)
    }
    
    // Utility Methods
    private fun hashPassword(password: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(password.toByteArray())
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
    
    private fun generateAuthToken(user: UserEntity): String {
        val tokenData = "${user.userId}:${user.email}:${System.currentTimeMillis()}"
        return Base64.getEncoder().encodeToString(tokenData.toByteArray())
    }
    
    private fun generateRefreshToken(user: UserEntity): String {
        val tokenData = "${user.userId}:${user.email}:${System.currentTimeMillis()}:refresh"
        return Base64.getEncoder().encodeToString(tokenData.toByteArray())
    }
    
    private fun generateTempPassword(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*"
        return (1..12)
            .map { chars.random() }
            .joinToString("")
    }
    
    private fun isValidEmail(email: String): Boolean {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }
    
    private fun isValidPassword(password: String): Boolean {
        return password.length >= 8 &&
                password.any { it.isUpperCase() } &&
                password.any { it.isLowerCase() } &&
                password.any { it.isDigit() } &&
                password.any { !it.isLetterOrDigit() }
    }
    
    // Account Management
    suspend fun updateProfile(updates: Map<String, Any>): AuthResult {
        return try {
            val currentUser = getCurrentUser()
            if (currentUser == null) {
                return AuthResult(false, message = "No user logged in")
            }
            
            var updatedUser = currentUser.copy(updatedAt = Date())
            
            updates.forEach { (key, value) ->
                when (key) {
                    "fullName" -> updatedUser = updatedUser.copy(fullName = value as String)
                    "profilePictureUrl" -> updatedUser = updatedUser.copy(profilePictureUrl = value as String?)
                    "preferredAiModels" -> updatedUser = updatedUser.copy(preferredAiModels = value as List<String>)
                    "themePreference" -> updatedUser = updatedUser.copy(themePreference = value as String)
                    "languageSetting" -> updatedUser = updatedUser.copy(languageSetting = value as String)
                    "notificationEnabled" -> updatedUser = updatedUser.copy(notificationEnabled = value as Boolean)
                    "autoBackupEnabled" -> updatedUser = updatedUser.copy(autoBackupEnabled = value as Boolean)
                    "newsletterSubscribed" -> updatedUser = updatedUser.copy(newsletterSubscribed = value as Boolean)
                    "productUpdatesSubscribed" -> updatedUser = updatedUser.copy(productUpdatesSubscribed = value as Boolean)
                    "promotionalEmailsSubscribed" -> updatedUser = updatedUser.copy(promotionalEmailsSubscribed = value as Boolean)
                }
            }
            
            userDao.updateUser(updatedUser)
            
            // Track profile update analytics
            val analytics = UsageAnalyticsEntity(
                userId = currentUser.userId,
                date = Date(),
                featuresAccessed = listOf("profile_update")
            )
            usageAnalyticsDao.insertUsageAnalytics(analytics)
            
            AuthResult(true, updatedUser, "Profile updated successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Profile update error", e)
            AuthResult(false, message = "Profile update failed: ${e.message}")
        }
    }
    
    suspend fun deleteAccount(): AuthResult {
        return try {
            val currentUser = getCurrentUser()
            if (currentUser == null) {
                return AuthResult(false, message = "No user logged in")
            }
            
            // Soft delete by marking as inactive
            val updatedUser = currentUser.copy(
                isActive = false,
                updatedAt = Date()
            )
            userDao.updateUser(updatedUser)
            
            // Track account deletion analytics
            val analytics = UsageAnalyticsEntity(
                userId = currentUser.userId,
                date = Date(),
                featuresAccessed = listOf("account_deletion")
            )
            usageAnalyticsDao.insertUsageAnalytics(analytics)
            
            // Clear session
            clearAuthSession()
            
            AuthResult(true, message = "Account deleted successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Account deletion error", e)
            AuthResult(false, message = "Account deletion failed: ${e.message}")
        }
    }
}