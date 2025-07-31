package com.playstudio.aiteacher.profile

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.playstudio.aiteacher.MainActivity
import com.playstudio.aiteacher.R
import com.playstudio.aiteacher.databinding.ActivityRegisterBinding
import kotlinx.coroutines.launch
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException

class RegisterActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityRegisterBinding
    private lateinit var firebaseAuthService: FirebaseAuthenticationService
    private lateinit var googleSignInClient: GoogleSignInClient
    
    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { activityResult ->
        if (activityResult.resultCode == RESULT_OK) {
            lifecycleScope.launch {
                try {
                    val result = firebaseAuthService.handleGoogleSignInResult(activityResult.data)
                    
                    if (result.success) {
                        Toast.makeText(this@RegisterActivity, "Google Sign-In successful!", Toast.LENGTH_SHORT).show()
                        navigateToMain()
                    } else {
                        Toast.makeText(this@RegisterActivity, result.message, Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(this@RegisterActivity, "Google Sign-In error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        firebaseAuthService = FirebaseAuthenticationService(this)
        
        setupGoogleSignIn()
        setupGlassmorphismStatusBar()
        setupUI()
    }
    
    private fun setupGlassmorphismStatusBar() {
        window?.apply {
            statusBarColor = getColor(R.color.glass_gradient_start)
            navigationBarColor = getColor(R.color.glass_gradient_end)
        }
    }
    
    private fun setupUI() {
        binding.apply {
            // Register button
            registerButton.setOnClickListener {
                val fullName = nameEditText.text.toString().trim()
                val email = emailEditText.text.toString().trim()
                val password = passwordEditText.text.toString().trim()
                val confirmPassword = confirmPasswordEditText.text.toString().trim()
                
                if (validateInput(fullName, email, password, confirmPassword)) {
                    performRegistration(fullName, email, password)
                }
            }
            
            // Login link
            loginText.setOnClickListener {
                finish()
            }
            
            // Google Sign-In button
            googleSignInButton.setOnClickListener {
                performGoogleSignIn()
            }
        }
    }
    
    private fun validateInput(fullName: String, email: String, password: String, confirmPassword: String): Boolean {
        if (fullName.isEmpty()) {
            binding.nameEditText.error = "Full name is required"
            return false
        }
        
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
        
        if (password.length < 8) {
            binding.passwordEditText.error = "Password must be at least 8 characters"
            return false
        }
        
        if (password != confirmPassword) {
            binding.confirmPasswordEditText.error = "Passwords do not match"
            return false
        }
        
        return true
    }
    
    private fun performRegistration(fullName: String, email: String, password: String) {
        lifecycleScope.launch {
            try {
                binding.progressBar.visibility = android.view.View.VISIBLE
                binding.registerButton.isEnabled = false
                
                val registrationData = AuthenticationService.RegistrationData(
                    email = email,
                    password = password,
                    fullName = fullName,
                    newsletterSubscribed = binding.newsletterCheckBox.isChecked,
                    productUpdatesSubscribed = binding.productUpdatesCheckBox.isChecked,
                    promotionalEmailsSubscribed = binding.promotionalEmailsCheckBox.isChecked
                )
                
                val result = firebaseAuthService.registerWithFirebase(
                    registrationData.email,
                    registrationData.password,
                    registrationData.fullName
                )
                
                if (result.success) {
                    Toast.makeText(this@RegisterActivity, "Registration successful!", Toast.LENGTH_SHORT).show()
                    navigateToMain()
                } else {
                    Toast.makeText(this@RegisterActivity, result.message, Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@RegisterActivity, "Registration error: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                binding.progressBar.visibility = android.view.View.GONE
                binding.registerButton.isEnabled = true
            }
        }
    }
    
    private fun setupGoogleSignIn() {
        // No setup needed - FirebaseAuthenticationService handles this
    }
    
    private fun performGoogleSignIn() {
        val signInIntent = firebaseAuthService.getGoogleSignInIntent()
        googleSignInLauncher.launch(signInIntent)
    }
    
    
    private fun navigateToMain() {
        val returnToSubscription = intent.getBooleanExtra("return_to_subscription", false)
        
        if (returnToSubscription) {
            Log.d("RegisterActivity", "Returning to subscription flow after successful registration")
            // Return to MainActivity but trigger subscription dialog
            val intent = Intent(this, MainActivity::class.java)
            intent.putExtra("show_subscription_dialog", true)
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