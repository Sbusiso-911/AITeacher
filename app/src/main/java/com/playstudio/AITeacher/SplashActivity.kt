package com.playstudio.aiteacher

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat

class SplashActivity : AppCompatActivity() {

    private lateinit var typingTextView: TextView
    private val handler = Handler(Looper.getMainLooper())
    private val typingDelay: Long = 100 // Delay in milliseconds between each character

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        typingTextView = findViewById(R.id.typingTextView)

        // Load the dot matrix font
        val dotMatrixFont: Typeface? = ResourcesCompat.getFont(this, R.font.dot_matrix)

        // Set the dot matrix font to the TextView
        typingTextView.typeface = dotMatrixFont

        val text1 = "Powered by GPT-4o\n"

        val spannableString1 = SpannableString(text1)
        spannableString1.setSpan(ForegroundColorSpan(Color.BLUE), 0, text1.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        spannableString1.setSpan(StyleSpan(Typeface.BOLD), 0, text1.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

        typeText(spannableString1)
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