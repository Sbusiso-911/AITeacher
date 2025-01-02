package com.playstudio.aiteacher // Your package name

import android.content.Context
import android.graphics.LinearGradient
import android.graphics.Shader
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView

class GradientTextView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatTextView(context, attrs, defStyleAttr) {

    init {
        setUpGradient()
    }

    private fun setUpGradient() {
        // Set up your gradient colors
        val startColor = 0xFFFF4500.toInt() // Orange Red
        val endColor = 0xFFFFD700.toInt()   // Gold
        val shader = LinearGradient(
            0f, 0f, 0f, textSize,
            startColor, endColor,
            Shader.TileMode.CLAMP
        )
        paint.shader = shader
    }
}

