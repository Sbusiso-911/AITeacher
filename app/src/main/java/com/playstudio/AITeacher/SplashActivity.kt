package com.playstudio.aiteacher

import android.content.Intent
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.AnimationSet
import android.view.animation.RotateAnimation
import android.view.animation.ScaleAnimation
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat

class SplashActivity : AppCompatActivity() {

    private lateinit var typingTextView: TextView
    private lateinit var logoImageView: ImageView
    private val handler = Handler(Looper.getMainLooper())
    private val typingDelay: Long = 100 // Delay in milliseconds between each character

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        typingTextView = findViewById(R.id.typingTextView)
        logoImageView = findViewById(R.id.logoImageView)

        // Load the dot matrix font
        val dotMatrixFont: Typeface? = ResourcesCompat.getFont(this, R.font.dot_matrix)

        // Set the dot matrix font to the TextView
        typingTextView.typeface = dotMatrixFont

        val text1 = "AI Teacher, ALWAYS READY TO EMPOWER YOU."

        val spannableString1 = SpannableString(text1)
        spannableString1.setSpan(StyleSpan(Typeface.BOLD), 0, text1.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

        // Apply gradient color effect to the text
        applyGradientColor(spannableString1, text1)

        // Start the combined animations
        startCombinedAnimations()

        // Start the typing animation after a short delay to allow the combined animations to complete
        handler.postDelayed({
            typeText(spannableString1)
        }, 1500) // 1500ms delay for combined animations
    }

    private fun applyGradientColor(spannableString: SpannableString, text: String) {
        val shader = LinearGradient(
            0f, 0f, 0f, typingTextView.textSize,
            intArrayOf(Color.RED, Color.YELLOW, Color.GREEN, Color.CYAN, Color.BLUE, Color.MAGENTA),
            null, Shader.TileMode.CLAMP
        )
        typingTextView.paint.shader = shader
    }

    private fun startCombinedAnimations() {
        val fadeIn = AlphaAnimation(0.0f, 1.0f)
        fadeIn.duration = 500 // Duration of the fade-in effect

        val scale = ScaleAnimation(
            0.5f, 1.0f, // Start and end values for the X axis scaling
            0.5f, 1.0f, // Start and end values for the Y axis scaling
            Animation.RELATIVE_TO_SELF, 0.5f, // Pivot point of X scaling
            Animation.RELATIVE_TO_SELF, 0.5f // Pivot point of Y scaling
        )
        scale.duration = 500 // Duration of the scale effect

        val rotate = RotateAnimation(
            0f, 720f, // Start and end values for the rotation (2 full rotations)
            Animation.RELATIVE_TO_SELF, 0.5f, // Pivot point of X rotation
            Animation.RELATIVE_TO_SELF, 0.5f // Pivot point of Y rotation
        )
        rotate.duration = 1000 // Duration of the rotation effect

        val animationSet = AnimationSet(true)
        animationSet.addAnimation(fadeIn)
        animationSet.addAnimation(scale)
        animationSet.addAnimation(rotate)

        logoImageView.startAnimation(animationSet)
        logoImageView.alpha = 1.0f // Ensure the ImageView is fully visible after the animation

        // Apply fade-in animation to the TextView separately
        val textFadeIn = AlphaAnimation(0.0f, 1.0f)
        textFadeIn.duration = 500 // Duration of the fade-in effect
        typingTextView.startAnimation(textFadeIn)
        typingTextView.alpha = 1.0f // Ensure the TextView is fully visible after the animation
    }

    private fun typeText(fullText: SpannableString) {
        val length = fullText.length
        var index = 0

        val runnable = object : Runnable {
            override fun run() {
                if (index <= length) {
                    typingTextView.text = fullText.subSequence(0, index)
                    index++
                    handler.postDelayed(this, typingDelay)
                } else {
                    // After typing is done, start the MainActivity
                    startMainActivity()
                }
            }
        }

        handler.post(runnable)
    }

    private fun startMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }
}