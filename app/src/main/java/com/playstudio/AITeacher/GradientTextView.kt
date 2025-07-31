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
        val startColor = 0xFFFFFFFF.toInt()  // White (with alpha)
        val endColor = 0xFFCCCCCC.toInt()   // Light gray

        val shader = LinearGradient(
            0f, 0f, 0f, textSize,
            startColor, endColor,
            Shader.TileMode.CLAMP
        )
        paint.shader = shader
    }
}

