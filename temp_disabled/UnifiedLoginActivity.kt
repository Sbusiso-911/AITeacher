package com.playstudio.aiteacher.auth

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.playstudio.aiteacher.R
import com.playstudio.aiteacher.backend.UnifiedDataManager
import com.playstudio.aiteacher.databinding.ActivityUnifiedLoginBinding
import com.playstudio.aiteacher.profile.AuthenticationService
import kotlinx.coroutines.launch

/**
 * Unified Login Activity - Handles both email/password and Google Sign-In
 * Provides seamless access to synchronized data across Android app and webapp
 */
class UnifiedLoginActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "UnifiedLoginActivity"
        private const val RC_SIGN_IN = 9001
    }
    
    private lateinit var binding: ActivityUnifiedLoginBinding
    private lateinit var dataManager: UnifiedDataManager
    private lateinit var googleSignInClient: GoogleSignInClient
    
    // Google Sign-In launcher
    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            handleGoogleSignInResult(result.data)
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUnifiedLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Initialize data manager
        dataManager = UnifiedDataManager.getInstance(this)
        
        // Check if already logged in
        if (dataManager.isLoggedIn()) {
            navigateToMainActivity()
            return
        }
        
        setupGoogleSignIn()
        setupUI()
    }
    
    private fun setupGoogleSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        
        googleSignInClient = GoogleSignIn.getClient(this, gso)
    }
    
    private fun setupUI() {
        // Email/Password Login
        binding.btnLogin.setOnClickListener {
            handleEmailLogin()
        }
        
        // Google Sign-In
        binding.btnGoogleSignIn.setOnClickListener {
            handleGoogleSignIn()
        }
        
        // Register new account
        binding.btnRegister.setOnClickListener {
            startActivity(Intent(this, UnifiedRegisterActivity::class.java))
        }
        
        // Forgot Password
        binding.btnForgotPassword.setOnClickListener {
            handleForgotPassword()
        }
        
        // Continue as Guest (limited features)
        binding.btnContinueGuest.setOnClickListener {
            handleGuestMode()
        }
        
        // Demo Account (for testing)
        binding.btnDemoAccount.setOnClickListener {
            handleDemoLogin()
        }
    }
    
    /**
     * Handle email/password login
     */
    private fun handleEmailLogin() {
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString()
        val rememberMe = binding.cbRememberMe.isChecked
        
        if (email.isEmpty() || password.isEmpty()) {
            showError("Please enter both email and password")
            return
        }
        
        showLoading(true)
        
        lifecycleScope.launch {
            try {
                val result = dataManager.login(email, password, rememberMe)
                
                if (result.success) {
                    showSuccess("Login successful!")
                    
                    // Initialize user data and sync
                    dataManager.initialize()
                    
                    navigateToMainActivity()
                } else {
                    showError("Login failed: ${result.message}")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Login error", e)
                showError("Login error: ${e.message}")
            } finally {
                showLoading(false)
            }
        }
    }
    
    /**
     * Handle Google Sign-In
     */
    private fun handleGoogleSignIn() {
        val signInIntent = googleSignInClient.signInIntent
        googleSignInLauncher.launch(signInIntent)
    }
    
    /**
     * Process Google Sign-In result
     */
    private fun handleGoogleSignInResult(data: Intent?) {
        try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account = task.getResult(ApiException::class.java)
            
            if (account != null) {
                showLoading(true)
                
                lifecycleScope.launch {
                    try {
                        val googleSignInData = AuthenticationService.GoogleSignInData(
                            email = account.email ?: "",
                            fullName = account.displayName ?: "",
                            googleId = account.id ?: "",
                            profilePictureUrl = account.photoUrl?.toString()
                        )
                        
                        val authService = AuthenticationService(this@UnifiedLoginActivity)
                        val result = authService.signInWithGoogle(googleSignInData)
                        
                        if (result.success) {
                            // Initialize data manager for the logged-in user
                            dataManager.initialize()
                            
                            showSuccess("Google Sign-In successful!")
                            navigateToMainActivity()
                        } else {
                            showError("Google Sign-In failed: ${result.message}")
                        }
                        
                    } catch (e: Exception) {
                        Log.e(TAG, "Google Sign-In processing error", e)
                        showError("Google Sign-In error: ${e.message}")
                    } finally {
                        showLoading(false)
                    }
                }
            }
            
        } catch (e: ApiException) {
            Log.w(TAG, "Google sign in failed", e)
            showError("Google Sign-In failed: ${e.message}")
        }
    }
    
    /**
     * Handle forgot password
     */
    private fun handleForgotPassword() {
        val email = binding.etEmail.text.toString().trim()
        
        if (email.isEmpty()) {
            showError("Please enter your email address")
            return
        }
        
        showLoading(true)
        
        lifecycleScope.launch {
            try {
                val authService = AuthenticationService(this@UnifiedLoginActivity)
                val result = authService.resetPassword(email)
                
                if (result.success) {
                    showSuccess("Password reset instructions sent to your email")
                    // In production, this would send an email
                    // For now, show the temporary password
                    showError("${result.message}")
                } else {
                    showError("Password reset failed: ${result.message}")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Password reset error", e)
                showError("Password reset error: ${e.message}")
            } finally {
                showLoading(false)
            }
        }
    }
    
    /**
     * Handle guest mode (limited features)
     */
    private fun handleGuestMode() {
        showSuccess("Continuing as guest (limited features available)")
        navigateToMainActivity()
    }
    
    /**
     * Handle demo account login (for testing)
     */
    private fun handleDemoLogin() {
        showLoading(true)
        
        lifecycleScope.launch {
            try {
                val result = dataManager.login("demo@aiteacher.com", "Demo123!", false)
                
                if (result.success) {
                    showSuccess("Demo account login successful!")
                    dataManager.initialize()
                    navigateToMainActivity()
                } else {
                    // Create demo account if it doesn't exist
                    val demoData = AuthenticationService.RegistrationData(
                        email = "demo@aiteacher.com",
                        password = "Demo123!",
                        fullName = "Demo User",
                        themePreference = "system",
                        languageSetting = "en"
                    )
                    
                    val registerResult = dataManager.register(demoData)
                    if (registerResult.success) {
                        showSuccess("Demo account created and logged in!")
                        dataManager.initialize()
                        navigateToMainActivity()
                    } else {
                        showError("Demo login failed: ${registerResult.message}")
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Demo login error", e)
                showError("Demo login error: ${e.message}")
            } finally {
                showLoading(false)
            }
        }
    }
    
    /**
     * Navigate to main activity after successful login
     */
    private fun navigateToMainActivity() {
        val intent = Intent(this, com.playstudio.aiteacher.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }
    
    /**
     * UI Helper Methods
     */
    private fun showLoading(show: Boolean) {
        binding.progressBar.visibility = if (show) android.view.View.VISIBLE else android.view.View.GONE
        binding.btnLogin.isEnabled = !show
        binding.btnGoogleSignIn.isEnabled = !show
        binding.btnRegister.isEnabled = !show
    }
    
    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        Log.w(TAG, "Error: $message")
    }
    
    private fun showSuccess(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        Log.i(TAG, "Success: $message")
    }
    
    override fun onStart() {
        super.onStart()
        
        // Check if user is already signed in with Google
        val account = GoogleSignIn.getLastSignedInAccount(this)
        if (account != null && dataManager.isLoggedIn()) {
            navigateToMainActivity()
        }
    }
}