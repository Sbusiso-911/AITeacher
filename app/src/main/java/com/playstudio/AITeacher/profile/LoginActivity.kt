package com.playstudio.aiteacher.profile

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.playstudio.aiteacher.MainActivity
import com.playstudio.aiteacher.R
import com.playstudio.aiteacher.databinding.ActivityLoginBinding
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityLoginBinding
    private lateinit var firebaseAuthService: FirebaseAuthenticationService
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        firebaseAuthService = FirebaseAuthenticationService(this)
        
        // Check if user is already logged in
        if (firebaseAuthService.isSignedIn()) {
            navigateToMain()
            return
        }
        
        setupGlassmorphismStatusBar()
        setupUI()
    }
    
    private fun setupGlassmorphismStatusBar() {
        window?.apply {
            statusBarColor = getColor(R.color.glass_gradient_start)
            navigationBarColor = getColor(R.color.glass_gradient_end)
            
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                decorView.windowInsetsController?.setSystemBarsAppearance(
                    0,
                    android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                )
            }
        }
    }
    
    private fun setupUI() {
        binding.apply {
            // Login button
            loginButton.setOnClickListener {
                val email = emailEditText.text.toString().trim()
                val password = passwordEditText.text.toString().trim()
                val rememberMe = rememberMeCheckBox.isChecked
                
                if (validateInput(email, password)) {
                    performLogin(email, password, rememberMe)
                }
            }
            
            // Register button
            registerButton.setOnClickListener {
                startActivity(Intent(this@LoginActivity, RegisterActivity::class.java))
            }
            
            // Forgot password
            forgotPasswordText.setOnClickListener {
                showForgotPasswordDialog()
            }
            
            // Guest mode
            guestModeText.setOnClickListener {
                navigateToMain()
            }
            
            // Debug: Long press on login button to test Firebase connection
            loginButton.setOnLongClickListener {
                testFirebaseConnection()
                true
            }
        }
    }
    
    private fun validateInput(email: String, password: String): Boolean {
        if (email.isEmpty()) {
            binding.emailEditText.error = "Email is required"
            return false
        }
        
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.emailEditText.error = "Invalid email format"
            return false
        }
        
        if (password.isEmpty()) {
            binding.passwordEditText.error = "Password is required"
            return false
        }
        
        return true
    }
    
    private fun performLogin(email: String, password: String, rememberMe: Boolean) {
        lifecycleScope.launch {
            try {
                binding.progressBar.visibility = android.view.View.VISIBLE
                binding.loginButton.isEnabled = false
                
                Log.d("LoginActivity", "Attempting login for email: $email")
                
                // Check network connectivity first
                if (!isNetworkAvailable()) {
                    Toast.makeText(this@LoginActivity, "No internet connection. Please check your network and try again.", Toast.LENGTH_LONG).show()
                    return@launch
                }
                
                val result = firebaseAuthService.signInWithFirebase(email, password)
                
                Log.d("LoginActivity", "Login result: success=${result.success}, message=${result.message}")
                
                if (result.success) {
                    Log.d("LoginActivity", "Login successful for user: ${result.user?.email}")
                    Toast.makeText(this@LoginActivity, "Login successful!", Toast.LENGTH_SHORT).show()
                    navigateToMain()
                } else {
                    Log.w("LoginActivity", "Login failed: ${result.message}")
                    
                    // Check if email is registered to provide better guidance
                    try {
                        val isEmailRegistered = firebaseAuthService.isEmailRegistered(email)
                        Log.d("LoginActivity", "Email $email registration status: $isEmailRegistered")
                        
                        val errorMessage = if (!isEmailRegistered) {
                            "No account found with this email. Please register first or check your email address."
                        } else {
                            result.message + "\n\nTip: Use 'Forgot Password' if you can't remember your password."
                        }
                        
                        Toast.makeText(this@LoginActivity, errorMessage, Toast.LENGTH_LONG).show()
                    } catch (e: Exception) {
                        Log.e("LoginActivity", "Error checking email registration", e)
                        Toast.makeText(this@LoginActivity, "Login failed: ${result.message}", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("LoginActivity", "Login error", e)
                Toast.makeText(this@LoginActivity, "Login error: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                binding.progressBar.visibility = android.view.View.GONE
                binding.loginButton.isEnabled = true
            }
        }
    }
    
    private fun isNetworkAvailable(): Boolean {
        return try {
            val connectivityManager = getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            val activeNetwork = connectivityManager.activeNetwork
            val networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
            networkCapabilities != null && (
                networkCapabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) ||
                networkCapabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR)
            )
        } catch (e: Exception) {
            Log.e("LoginActivity", "Error checking network connectivity", e)
            true // Default to true if we can't check
        }
    }
    
    private fun showForgotPasswordDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_forgot_password, null)
        val emailEditText = dialogView.findViewById<android.widget.EditText>(R.id.emailEditText)
        
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this, R.style.BlueDialogTheme)
            .setTitle("Reset Password")
            .setMessage("Enter your email address to reset your password")
            .setView(dialogView)
            .setPositiveButton("Reset") { _, _ ->
                val email = emailEditText.text.toString().trim()
                if (email.isNotEmpty() && android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                    performPasswordReset(email)
                } else {
                    Toast.makeText(this, "Please enter a valid email address", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .create()
        
        dialog.show()
    }
    
    private fun performPasswordReset(email: String) {
        lifecycleScope.launch {
            try {
                val result = firebaseAuthService.sendPasswordResetEmail(email)
                
                if (result.success) {
                    val dialog = androidx.appcompat.app.AlertDialog.Builder(this@LoginActivity, R.style.BlueDialogTheme)
                        .setTitle("Password Reset Email Sent")
                        .setMessage(result.message)
                        .setPositiveButton("OK", null)
                        .create()
                    dialog.show()
                } else {
                    Toast.makeText(this@LoginActivity, result.message, Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@LoginActivity, "Password reset error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun testFirebaseConnection() {
        lifecycleScope.launch {
            try {
                val connectivityManager = getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
                val isConnected = connectivityManager.activeNetwork != null
                
                val firebaseInitialized = try {
                    com.google.firebase.FirebaseApp.getInstance()
                    true
                } catch (e: Exception) {
                    false
                }
                
                val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                
                val diagnosticMessage = """
                    🔍 Firebase Diagnostic Results:
                    
                    ✓ Internet: ${if (isConnected) "Connected" else "❌ No Connection"}
                    ✓ Firebase: ${if (firebaseInitialized) "Initialized" else "❌ Not Initialized"}  
                    ✓ Auth State: ${if (currentUser != null) "User: ${currentUser.email}" else "Not signed in"}
                    
                    📱 Debug Info:
                    App Version: ${packageManager.getPackageInfo(packageName, 0).versionName}
                    
                    ${if (!isConnected) "⚠️ Check your internet connection" else ""}
                    ${if (!firebaseInitialized) "⚠️ Firebase not initialized - restart app" else ""}
                """.trimIndent()
                
                androidx.appcompat.app.AlertDialog.Builder(this@LoginActivity, R.style.BlueDialogTheme)
                    .setTitle("🔧 Firebase Connection Test")
                    .setMessage(diagnosticMessage)
                    .setPositiveButton("OK", null)
                    .show()
                    
            } catch (e: Exception) {
                Toast.makeText(this@LoginActivity, "Diagnostic failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun navigateToMain() {
        val returnToSubscription = intent.getBooleanExtra("return_to_subscription", false)
        val returnToProfile = intent.getBooleanExtra("return_to_profile", false)
        
        if (returnToSubscription) {
            Log.d("LoginActivity", "Returning to subscription flow after successful login")
            // Return to MainActivity but trigger subscription dialog
            val intent = Intent(this, MainActivity::class.java)
            intent.putExtra("show_subscription_dialog", true)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        } else if (returnToProfile) {
            Log.d("LoginActivity", "Returning to profile after successful login")
            // Return to ProfileActivity
            val intent = Intent(this, ProfileActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        } else {
            // Normal flow to MainActivity
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        }
        finish()
    }
}