package com.playstudio.aiteacher.profile

import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.playstudio.aiteacher.R
import kotlinx.coroutines.tasks.await
import java.util.*

/**
 * Firebase Authentication Service with Google Sign-In integration
 * Stores all user data exclusively in Firestore - no local database storage
 */
class FirebaseAuthenticationService(private val context: Context) {
    
    private val firebaseAuth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    
    companion object {
        private const val TAG = "FirebaseAuthService"
        const val RC_SIGN_IN = 9001
    }
    
    // Google Sign-In configuration
    private val googleSignInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestIdToken(context.getString(R.string.default_web_client_id)) // From google-services.json
        .requestEmail()
        .build()
    
    private val googleSignInClient: GoogleSignInClient = GoogleSignIn.getClient(context, googleSignInOptions)
    
    data class FirebaseAuthResult(
        val success: Boolean,
        val user: FirestoreUser? = null,
        val message: String = "",
        val isNewUser: Boolean = false
    )
    
    data class FirestoreUser(
        val uid: String = "",
        val email: String = "",
        val fullName: String = "",
        val profilePictureUrl: String? = null,
        val createdAt: Long = System.currentTimeMillis(),
        val lastLoginAt: Long = System.currentTimeMillis(),
        val lastSyncAt: Long = 0L,
        val subscriptionTier: String = "FREE",
        val subscriptionStatus: String = "INACTIVE",
        val subscriptionExpiresAt: Long = 0L,
        val messageCount: Int = 0,
        val tokenCount: Long = 0L,
        val totalChats: Int = 0,
        val totalMessages: Int = 0
    )
    
    /**
     * Get Google Sign-In Intent for activity
     */
    fun getGoogleSignInIntent(): Intent {
        return googleSignInClient.signInIntent
    }
    
    /**
     * Handle Google Sign-In result from activity
     */
    suspend fun handleGoogleSignInResult(data: Intent?): FirebaseAuthResult {
        return try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account = task.getResult(ApiException::class.java)
            signInWithGoogleAccount(account)
        } catch (e: ApiException) {
            Log.w(TAG, "Google sign in failed", e)
            FirebaseAuthResult(false, message = "Google Sign-In failed: ${e.message}")
        }
    }
    
    /**
     * Sign in with Google Account
     */
    private suspend fun signInWithGoogleAccount(account: GoogleSignInAccount): FirebaseAuthResult {
        return try {
            Log.d(TAG, "firebaseAuthWithGoogle:${account.id}")
            
            val credential = GoogleAuthProvider.getCredential(account.idToken, null)
            val authResult = firebaseAuth.signInWithCredential(credential).await()
            
            val firebaseUser = authResult.user
            if (firebaseUser != null) {
                val isNewUser = authResult.additionalUserInfo?.isNewUser == true
                
                // Create or update user in Firestore directly
                val firestoreUser = if (isNewUser) {
                    createUserFromGoogleAccount(account, firebaseUser.uid)
                } else {
                    updateUserFromGoogle(firebaseUser.uid, account)
                }
                
                if (firestoreUser != null) {
                    FirebaseAuthResult(
                        success = true,
                        user = firestoreUser,
                        isNewUser = isNewUser
                    )
                } else {
                    FirebaseAuthResult(false, message = "Failed to create user profile")
                }
            } else {
                FirebaseAuthResult(false, message = "Firebase authentication failed")
            }
        } catch (e: Exception) {
            Log.w(TAG, "signInWithCredential:failure", e)
            FirebaseAuthResult(false, message = "Authentication failed: ${e.message}")
        }
    }
    
    /**
     * Register new user with email and password + Firebase (Firestore only)
     */
    suspend fun registerWithFirebase(
        email: String, 
        password: String, 
        fullName: String
    ): FirebaseAuthResult {
        return try {
            // Create Firebase user
            val authResult = firebaseAuth.createUserWithEmailAndPassword(email, password).await()
            val firebaseUser = authResult.user
            
            if (firebaseUser != null) {
                // Create Firestore user directly
                val firestoreUser = FirestoreUser(
                    uid = firebaseUser.uid,
                    email = email,
                    fullName = fullName,
                    createdAt = System.currentTimeMillis(),
                    lastLoginAt = System.currentTimeMillis()
                )
                
                val saved = saveUser(firestoreUser)
                if (saved) {
                    Log.d(TAG, "New email user created in Firestore: $email")
                    FirebaseAuthResult(
                        success = true,
                        user = firestoreUser,
                        isNewUser = true
                    )
                } else {
                    // Clean up Firebase user if Firestore save fails
                    firebaseUser.delete()
                    FirebaseAuthResult(false, message = "Failed to save user profile")
                }
            } else {
                FirebaseAuthResult(false, message = "Failed to create Firebase user")
            }
        } catch (e: Exception) {
            Log.w(TAG, "createUserWithEmail:failure", e)
            
            // Provide more specific error messages for registration
            val errorMessage = when (e) {
                is com.google.firebase.auth.FirebaseAuthWeakPasswordException -> 
                    "Password is too weak. Please use at least 6 characters."
                is com.google.firebase.auth.FirebaseAuthInvalidCredentialsException -> 
                    "Invalid email address. Please enter a valid email."
                is com.google.firebase.auth.FirebaseAuthUserCollisionException -> 
                    "An account with this email already exists. Please sign in instead."
                else -> "Registration failed: ${e.message}"
            }
            
            FirebaseAuthResult(false, message = errorMessage)
        }
    }
    
    /**
     * Sign in with email and password + Firebase (Firestore only)
     */
    suspend fun signInWithFirebase(email: String, password: String): FirebaseAuthResult {
        return try {
            Log.d(TAG, "Attempting Firebase sign-in for email: $email")
            
            // Check if Firebase is initialized
            try {
                firebaseAuth.app
                Log.d(TAG, "Firebase app is initialized and ready")
            } catch (e: Exception) {
                Log.e(TAG, "Firebase app not initialized properly", e)
                return FirebaseAuthResult(false, message = "Firebase not initialized. Please restart the app.")
            }
            
            // Sign in with Firebase
            val authResult = firebaseAuth.signInWithEmailAndPassword(email, password).await()
            val firebaseUser = authResult.user
            
            Log.d(TAG, "Firebase auth completed. User: ${firebaseUser?.uid}, Email verified: ${firebaseUser?.isEmailVerified}")
            
            if (firebaseUser != null) {
                // Get user from Firestore
                Log.d(TAG, "Retrieving user profile from Firestore for UID: ${firebaseUser.uid}")
                val firestoreUser = getCurrentUser()
                if (firestoreUser != null) {
                    // Update last login time
                    val updatedUser = firestoreUser.copy(lastLoginAt = System.currentTimeMillis())
                    val saved = saveUser(updatedUser)
                    
                    Log.d(TAG, "User signed in successfully: ${firestoreUser.email}, profile updated: $saved")
                    FirebaseAuthResult(
                        success = true,
                        user = updatedUser,
                        isNewUser = false
                    )
                } else {
                    // User exists in Firebase but not in Firestore - create profile
                    Log.d(TAG, "User exists in Firebase but not in Firestore, creating profile...")
                    val newFirestoreUser = FirestoreUser(
                        uid = firebaseUser.uid,
                        email = email,
                        fullName = firebaseUser.displayName ?: "User",
                        createdAt = System.currentTimeMillis(),
                        lastLoginAt = System.currentTimeMillis()
                    )
                    
                    val saved = saveUser(newFirestoreUser)
                    if (saved) {
                        Log.d(TAG, "User profile created in Firestore during sign-in: $email")
                        FirebaseAuthResult(success = true, user = newFirestoreUser, isNewUser = true)
                    } else {
                        Log.e(TAG, "Failed to save new user profile to Firestore")
                        FirebaseAuthResult(false, message = "Failed to create user profile")
                    }
                }
            } else {
                Log.e(TAG, "Firebase auth succeeded but returned null user")
                FirebaseAuthResult(false, message = "Firebase sign-in failed - no user returned")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Firebase sign-in exception: ${e.javaClass.simpleName}: ${e.message}", e)
            
            // Provide more specific error messages
            val errorMessage = when (e) {
                is com.google.firebase.auth.FirebaseAuthInvalidCredentialsException -> {
                    Log.w(TAG, "Invalid credentials provided")
                    "Invalid email or password. Please check your credentials and try again."
                }
                is com.google.firebase.auth.FirebaseAuthInvalidUserException -> {
                    Log.w(TAG, "User not found: ${e.errorCode}")
                    "No account found with this email. Please register first or check your email address."
                }
                is com.google.firebase.auth.FirebaseAuthUserCollisionException -> {
                    Log.w(TAG, "User collision: ${e.errorCode}")
                    "An account with this email already exists."
                }
                is com.google.firebase.FirebaseNetworkException -> {
                    Log.w(TAG, "Network error during authentication")
                    "Network error. Please check your internet connection and try again."
                }
                is com.google.firebase.FirebaseTooManyRequestsException -> {
                    Log.w(TAG, "Too many requests")
                    "Too many failed attempts. Please wait a few minutes and try again."
                }
                else -> {
                    Log.w(TAG, "Unknown authentication error: ${e.javaClass.simpleName}")
                    "Sign-in failed: ${e.message}"
                }
            }
            
            FirebaseAuthResult(false, message = errorMessage)
        }
    }
    
    /**
     * Sign out from Firebase and Google (Firestore only)
     */
    suspend fun signOut(): Boolean {
        return try {
            firebaseAuth.signOut()
            googleSignInClient.signOut().await()
            Log.d(TAG, "User signed out successfully")
            true
        } catch (e: Exception) {
            Log.w(TAG, "signOut:failure", e)
            false
        }
    }
    
    /**
     * Check if user is signed in
     */
    fun isSignedIn(): Boolean {
        val user = firebaseAuth.currentUser
        val isSignedIn = user != null
        Log.d(TAG, "isSignedIn check: user=${user?.uid}, isSignedIn=$isSignedIn, email=${user?.email}")
        
        // Additional debug info
        if (user != null) {
            Log.d(TAG, "Firebase user details: isEmailVerified=${user.isEmailVerified}, providerId=${user.providerId}")
        } else {
            Log.d(TAG, "No Firebase user found - authentication state lost or never established")
        }
        
        return isSignedIn
    }
    
    /**
     * Get current Firebase user UID
     */
    fun getCurrentFirebaseUid(): String? {
        return firebaseAuth.currentUser?.uid
    }
    
    /**
     * Get current user data from Firestore
     */
    suspend fun getCurrentUser(): FirestoreUser? {
        val uid = getCurrentFirebaseUid() ?: return null
        
        return try {
            val document = firestore.collection("users").document(uid).get().await()
            if (document.exists()) {
                document.toObject(FirestoreUser::class.java)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting current user", e)
            null
        }
    }
    
    /**
     * Save user data to Firestore
     */
    suspend fun saveUser(user: FirestoreUser): Boolean {
        return try {
            firestore.collection("users").document(user.uid).set(user).await()
            Log.d(TAG, "User saved to Firestore: ${user.email}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error saving user to Firestore", e)
            false
        }
    }
    
    /**
     * Update user's last login time
     */
    suspend fun updateLastLogin(): Boolean {
        val uid = getCurrentFirebaseUid() ?: return false
        
        return try {
            firestore.collection("users").document(uid)
                .update("lastLoginAt", System.currentTimeMillis()).await()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error updating last login", e)
            false
        }
    }
    
    /**
     * Send password reset email
     */
    suspend fun sendPasswordResetEmail(email: String): FirebaseAuthResult {
        return try {
            firebaseAuth.sendPasswordResetEmail(email).await()
            Log.d(TAG, "Password reset email sent to: $email")
            FirebaseAuthResult(
                success = true, 
                message = "Password reset email sent. Please check your inbox and spam folder."
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error sending password reset email", e)
            
            val errorMessage = when (e) {
                is com.google.firebase.auth.FirebaseAuthInvalidUserException -> 
                    "No account found with this email address. Please register first."
                is com.google.firebase.auth.FirebaseAuthInvalidCredentialsException -> 
                    "Invalid email address. Please enter a valid email."
                else -> "Failed to send password reset email: ${e.message}"
            }
            
            FirebaseAuthResult(false, message = errorMessage)
        }
    }
    
    /**
     * Update user statistics when they send messages or use features
     * Call this from ChatFragment or other places where usage occurs
     */
    suspend fun updateUserStats(messagesIncrement: Int = 0, tokensIncrement: Long = 0): Boolean {
        val uid = getCurrentFirebaseUid() ?: return false
        
        return try {
            val userDocRef = firestore.collection("users").document(uid)
            
            // Get current user to increment existing values
            val currentUser = getCurrentUser()
            if (currentUser != null) {
                val updatedUser = currentUser.copy(
                    messageCount = currentUser.messageCount + messagesIncrement,
                    totalMessages = currentUser.totalMessages + messagesIncrement,
                    tokenCount = currentUser.tokenCount + tokensIncrement,
                    lastLoginAt = System.currentTimeMillis()
                )
                
                userDocRef.set(updatedUser).await()
                Log.d(TAG, "Updated user stats: +$messagesIncrement messages, +$tokensIncrement tokens")
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating user stats", e)
            false
        }
    }
    
    /**
     * Increment total chats when a new conversation is started
     */
    suspend fun incrementTotalChats(): Boolean {
        val uid = getCurrentFirebaseUid() ?: return false
        
        return try {
            val userDocRef = firestore.collection("users").document(uid)
            
            // Get current user to increment existing values
            val currentUser = getCurrentUser()
            if (currentUser != null) {
                val updatedUser = currentUser.copy(
                    totalChats = currentUser.totalChats + 1,
                    lastLoginAt = System.currentTimeMillis()
                )
                
                userDocRef.set(updatedUser).await()
                Log.d(TAG, "Incremented total chats to: ${updatedUser.totalChats}")
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error incrementing total chats", e)
            false
        }
    }
    
    /**
     * Check if email is already registered in Firebase (without signing in)
     */
    suspend fun isEmailRegistered(email: String): Boolean {
        return try {
            val signInMethods = firebaseAuth.fetchSignInMethodsForEmail(email).await()
            val isRegistered = signInMethods.signInMethods?.isNotEmpty() == true
            Log.d(TAG, "Email $email registration check: isRegistered=$isRegistered, methods=${signInMethods.signInMethods}")
            isRegistered
        } catch (e: Exception) {
            Log.e(TAG, "Error checking email registration", e)
            false
        }
    }
    
    private suspend fun createUserFromGoogleAccount(account: GoogleSignInAccount, firebaseUid: String): FirestoreUser? {
        return try {
            val firestoreUser = FirestoreUser(
                uid = firebaseUid,
                email = account.email ?: "",
                fullName = account.displayName ?: "",
                profilePictureUrl = account.photoUrl?.toString(),
                createdAt = System.currentTimeMillis(),
                lastLoginAt = System.currentTimeMillis()
            )
            
            val saved = saveUser(firestoreUser)
            if (saved) {
                Log.d(TAG, "New Google user created in Firestore: ${firestoreUser.email}")
                firestoreUser
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating user from Google account", e)
            null
        }
    }
    
    private suspend fun updateUserFromGoogle(firebaseUid: String, account: GoogleSignInAccount): FirestoreUser? {
        return try {
            val existingUser = getCurrentUser()
            if (existingUser != null) {
                val updatedUser = existingUser.copy(
                    email = account.email ?: existingUser.email,
                    fullName = account.displayName ?: existingUser.fullName,
                    profilePictureUrl = account.photoUrl?.toString() ?: existingUser.profilePictureUrl,
                    lastLoginAt = System.currentTimeMillis()
                )
                
                val saved = saveUser(updatedUser)
                if (saved) {
                    Log.d(TAG, "Existing Google user updated in Firestore: ${updatedUser.email}")
                    updatedUser
                } else {
                    null
                }
            } else {
                // User doesn't exist, create new one
                createUserFromGoogleAccount(account, firebaseUid)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating user from Google account", e)
            null
        }
    }
}