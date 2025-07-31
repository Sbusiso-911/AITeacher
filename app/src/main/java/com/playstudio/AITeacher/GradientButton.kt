package com.playstudio.aiteacher

import android.content.Context
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import androidx.annotation.ArrayRes
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton

class GradientButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : MaterialButton(context, attrs, defStyleAttr) {

    // Rainbow colors as default for text gradient
    private val defaultRainbowColors = intArrayOf(
        Color.RED, Color.YELLOW, Color.GREEN, Color.CYAN, Color.BLUE, Color.MAGENTA
    )

    // Background gradient colors
    private var backgroundColors: IntArray = intArrayOf(
        ContextCompat.getColor(context, R.color.premium_gradient_start),
        ContextCompat.getColor(context, R.color.premium_gradient_end)
    )

    // Text gradient colors (defaults to rainbow)
    private var textColors: IntArray = defaultRainbowColors

    private var gradientPositions: FloatArray? = null
    private var textShader: Shader? = null
    private var cornerRadius: Float = 16f.dpToPx()
    private var isRainbowTextEnabled: Boolean = true

    init {
        initAttributes(attrs)
        setupButton()
    }

    private fun initAttributes(attrs: AttributeSet?) {
        context.theme.obtainStyledAttributes(
            attrs,
            R.styleable.GradientButton,
            0,
            0
        ).apply {
            try {
                // Handle background colors
                if (hasValue(R.styleable.GradientButton_backgroundColors)) {
                    val bgColorsResId = getResourceId(R.styleable.GradientButton_backgroundColors, 0)
                    if (bgColorsResId != 0) {
                        backgroundColors = resources.obtainTypedArray(bgColorsResId).let { ta ->
                            val colors = IntArray(ta.length()).apply {
                                for (i in 0 until ta.length()) {
                                    this[i] = ta.getColor(i, 0)
                                }
                            }
                            ta.recycle()
                            colors
                        }
                    }
                }

                // Handle text colors
                if (hasValue(R.styleable.GradientButton_textColors)) {
                    val textColorsResId = getResourceId(R.styleable.GradientButton_textColors, 0)
                    if (textColorsResId != 0) {
                        textColors = resources.obtainTypedArray(textColorsResId).let { ta ->
                            val colors = IntArray(ta.length()).apply {
                                for (i in 0 until ta.length()) {
                                    this[i] = ta.getColor(i, 0)
                                }
                            }
                            ta.recycle()
                            colors
                        }
                        isRainbowTextEnabled = false
                    }
                }

                cornerRadius = getDimension(R.styleable.GradientButton_cornerRadius, cornerRadius)
            } finally {
                recycle()
            }
        }
    }

    private fun setupButton() {
        // Create gradient background
        val backgroundGradient = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = this@GradientButton.cornerRadius
            colors = backgroundColors
            gradientType = GradientDrawable.LINEAR_GRADIENT
            orientation = GradientDrawable.Orientation.LEFT_RIGHT
        }
        background = backgroundGradient

        // Set up text gradient
        post {
            textShader = if (isRainbowTextEnabled) {
                LinearGradient(
                    0f, 0f, width.toFloat(), 0f,
                    defaultRainbowColors,
                    null,
                    Shader.TileMode.CLAMP
                )
            } else {
                LinearGradient(
                    0f, 0f, width.toFloat(), 0f,
                    textColors,
                    gradientPositions,
                    Shader.TileMode.CLAMP
                )
            }
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        paint.shader = textShader
        super.onDraw(canvas)
    }

    fun setBackgroundGradientColors(colors: IntArray) {
        backgroundColors = colors
        setupButton()
    }

    fun setBackgroundGradientColors(@ArrayRes colorsResId: Int) {
        backgroundColors = resources.obtainTypedArray(colorsResId).let { ta ->
            IntArray(ta.length()).apply {
                for (i in 0 until ta.length()) {
                    this[i] = ta.getColor(i, 0)
                }
            }.also { ta.recycle() }
        }
        setupButton()
    }

    fun setTextGradientColors(colors: IntArray) {
        textColors = colors
        isRainbowTextEnabled = false
        setupButton()
    }

    fun setTextGradientColors(@ArrayRes colorsResId: Int) {
        textColors = resources.obtainTypedArray(colorsResId).let { ta ->
            IntArray(ta.length()).apply {
                for (i in 0 until ta.length()) {
                    this[i] = ta.getColor(i, 0)
                }
            }.also { ta.recycle() }
        }
        isRainbowTextEnabled = false
        setupButton()
    }

    fun enableRainbowText(enable: Boolean) {
        isRainbowTextEnabled = enable
        setupButton()
    }

    fun setCornerRadius(radiusDp: Float) {
        cornerRadius = radiusDp.dpToPx()
        setupButton()
    }

    private fun Float.dpToPx(): Float {
        return this * resources.displayMetrics.density
    }
}