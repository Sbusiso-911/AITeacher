package com.playstudio.aiteacher.auth

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.playstudio.aiteacher.backend.UnifiedDataManager
import com.playstudio.aiteacher.databinding.ActivityUnifiedRegisterBinding
import com.playstudio.aiteacher.profile.AuthenticationService
import kotlinx.coroutines.launch

/**
 * Unified Registration Activity
 * Creates new user account with automatic sync setup
 */
class UnifiedRegisterActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "UnifiedRegisterActivity"
    }
    
    private lateinit var binding: ActivityUnifiedRegisterBinding
    private lateinit var dataManager: UnifiedDataManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUnifiedRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        dataManager = UnifiedDataManager.getInstance(this)
        
        setupUI()
    }
    
    private fun setupUI() {
        binding.btnRegister.setOnClickListener {
            handleRegistration()
        }
        
        binding.btnBackToLogin.setOnClickListener {
            finish() // Go back to login
        }
        
        binding.btnAlreadyHaveAccount.setOnClickListener {
            finish() // Go back to login
        }
    }
    
    private fun handleRegistration() {
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString()
        val confirmPassword = binding.etConfirmPassword.text.toString()
        val fullName = binding.etFullName.text.toString().trim()
        val agreeToTerms = binding.cbAgreeToTerms.isChecked
        val subscribeNewsletter = binding.cbSubscribeNewsletter.isChecked
        
        // Validation
        if (email.isEmpty() || password.isEmpty() || fullName.isEmpty()) {
            showError("Please fill in all required fields")
            return
        }
        
        if (password != confirmPassword) {
            showError("Passwords do not match")
            return
        }
        
        if (password.length < 8) {
            showError("Password must be at least 8 characters long")
            return
        }
        
        if (!agreeToTerms) {
            showError("Please agree to the Terms of Service and Privacy Policy")
            return
        }
        
        showLoading(true)
        
        lifecycleScope.launch {
            try {
                val registrationData = AuthenticationService.RegistrationData(
                    email = email,
                    password = password,
                    fullName = fullName,
                    preferredAiModels = listOf("gpt-3.5-turbo"), // Default model
                    themePreference = "system",
                    languageSetting = "en",
                    newsletterSubscribed = subscribeNewsletter,
                    productUpdatesSubscribed = true, // Always subscribe to important updates
                    promotionalEmailsSubscribed = subscribeNewsletter
                )
                
                val result = dataManager.register(registrationData)
                
                if (result.success) {
                    showSuccess("Registration successful! Welcome to AI Teacher!")
                    
                    // Initialize user data
                    dataManager.initialize()
                    
                    // Navigate to main activity
                    navigateToMainActivity()
                } else {
                    showError("Registration failed: ${result.message}")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Registration error", e)
                showError("Registration error: ${e.message}")
            } finally {
                showLoading(false)
            }
        }
    }
    
    private fun navigateToMainActivity() {
        val intent = Intent(this, com.playstudio.aiteacher.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }
    
    private fun showLoading(show: Boolean) {
        binding.progressBar.visibility = if (show) android.view.View.VISIBLE else android.view.View.GONE
        binding.btnRegister.isEnabled = !show
        binding.btnBackToLogin.isEnabled = !show
    }
    
    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        Log.w(TAG, "Error: $message")
    }
    
    private fun showSuccess(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        Log.i(TAG, "Success: $message")
    }
}