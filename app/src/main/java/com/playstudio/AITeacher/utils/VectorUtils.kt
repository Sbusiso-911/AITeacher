package com.playstudio.aiteacher.utils

import android.content.Context
import android.graphics.*
import android.graphics.drawable.AnimatedVectorDrawable
import android.graphics.drawable.VectorDrawable
import androidx.core.content.ContextCompat
import androidx.vectordrawable.graphics.drawable.AnimatedVectorDrawableCompat
import androidx.vectordrawable.graphics.drawable.VectorDrawableCompat
import com.playstudio.aiteacher.models.*

/**
 * Utility class for working with vector drawables and theme graphics
 */
object VectorUtils {
    
    /**
     * Load vector drawable with fallback handling
     */
    fun loadVectorDrawable(
        context: Context,
        drawableRes: Int,
        animated: Boolean = false
    ): android.graphics.drawable.Drawable? {
        return try {
            if (animated) {
                loadAnimatedVectorDrawable(context, drawableRes)
            } else {
                loadStaticVectorDrawable(context, drawableRes)
            }
        } catch (e: Exception) {
            // Fallback to regular drawable loading
            ContextCompat.getDrawable(context, drawableRes)
        }
    }
    
    /**
     * Load static vector drawable
     */
    private fun loadStaticVectorDrawable(
        context: Context,
        drawableRes: Int
    ): VectorDrawable? {
        return try {
            VectorDrawableCompat.create(context.resources, drawableRes, context.theme)
                as? VectorDrawable
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Load animated vector drawable
     */
    private fun loadAnimatedVectorDrawable(
        context: Context,
        drawableRes: Int
    ): AnimatedVectorDrawable? {
        return try {
            AnimatedVectorDrawableCompat.create(context, drawableRes) as? AnimatedVectorDrawable
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Apply theme colors to vector drawable
     */
    fun applyThemeColors(
        drawable: android.graphics.drawable.Drawable?,
        theme: AITheme,
        context: Context
    ): android.graphics.drawable.Drawable? {
        if (drawable == null) return null
        
        val tintedDrawable = drawable.mutate()
        
        // Apply primary color tint
        val primaryColor = ContextCompat.getColor(context, theme.colors.primary)
        tintedDrawable.setTint(primaryColor)
        
        return tintedDrawable
    }
    
    /**
     * Create bitmap from vector drawable
     */
    fun vectorToBitmap(
        drawable: android.graphics.drawable.Drawable,
        width: Int = drawable.intrinsicWidth,
        height: Int = drawable.intrinsicHeight
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(
            width,
            height,
            Bitmap.Config.ARGB_8888
        )
        
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        
        return bitmap
    }
    
    /**
     * Create themed background pattern
     */
    fun createThemedBackground(
        context: Context,
        theme: AITheme,
        width: Int,
        height: Int
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        // Get theme colors
        val backgroundColor = ContextCompat.getColor(context, theme.colors.background)
        val primaryColor = ContextCompat.getColor(context, theme.colors.primary)
        val accentColor = ContextCompat.getColor(context, theme.colors.accent)
        
        // Fill background
        canvas.drawColor(backgroundColor)
        
        // Create pattern based on theme category
        when (theme.category) {
            ThemeCategory.CYBERPUNK -> drawCyberpunkPattern(canvas, primaryColor, accentColor, width, height)
            ThemeCategory.NEURAL -> drawNeuralPattern(canvas, primaryColor, accentColor, width, height)
            ThemeCategory.QUANTUM -> drawQuantumPattern(canvas, primaryColor, accentColor, width, height)
            ThemeCategory.ORGANIC -> drawOrganicPattern(canvas, primaryColor, accentColor, width, height)
        }
        
        return bitmap
    }
    
    /**
     * Draw cyberpunk circuit pattern
     */
    private fun drawCyberpunkPattern(
        canvas: Canvas,
        primaryColor: Int,
        accentColor: Int,
        width: Int,
        height: Int
    ) {
        val paint = Paint().apply {
            isAntiAlias = true
            strokeWidth = 2f
            style = Paint.Style.STROKE
        }
        
        // Draw circuit lines
        paint.color = primaryColor
        paint.alpha = 100
        
        val gridSize = 50
        for (x in 0..width step gridSize) {
            canvas.drawLine(x.toFloat(), 0f, x.toFloat(), height.toFloat(), paint)
        }
        
        for (y in 0..height step gridSize) {
            canvas.drawLine(0f, y.toFloat(), width.toFloat(), y.toFloat(), paint)
        }
        
        // Draw circuit nodes
        paint.style = Paint.Style.FILL
        paint.color = accentColor
        paint.alpha = 150
        
        for (x in 0..width step gridSize) {
            for (y in 0..height step gridSize) {
                if ((x / gridSize + y / gridSize) % 3 == 0) {
                    canvas.drawCircle(x.toFloat(), y.toFloat(), 4f, paint)
                }
            }
        }
    }
    
    /**
     * Draw neural network pattern
     */
    private fun drawNeuralPattern(
        canvas: Canvas,
        primaryColor: Int,
        accentColor: Int,
        width: Int,
        height: Int
    ) {
        val paint = Paint().apply {
            isAntiAlias = true
            strokeWidth = 1.5f
            style = Paint.Style.STROKE
        }
        
        // Draw neural connections
        paint.color = primaryColor
        paint.alpha = 80
        
        val centerX = width / 2f
        val centerY = height / 2f
        val nodeCount = 12
        val radius = minOf(width, height) / 3f
        
        for (i in 0 until nodeCount) {
            val angle1 = (2 * Math.PI * i / nodeCount).toFloat()
            val angle2 = (2 * Math.PI * (i + 1) / nodeCount).toFloat()
            
            val x1 = centerX + kotlin.math.cos(angle1) * radius
            val y1 = centerY + kotlin.math.sin(angle1) * radius
            val x2 = centerX + kotlin.math.cos(angle2) * radius
            val y2 = centerY + kotlin.math.sin(angle2) * radius
            
            canvas.drawLine(x1, y1, x2, y2, paint)
        }
        
        // Draw neural nodes
        paint.style = Paint.Style.FILL
        paint.color = accentColor
        paint.alpha = 120
        
        for (i in 0 until nodeCount) {
            val angle = (2 * Math.PI * i / nodeCount).toFloat()
            val x = centerX + kotlin.math.cos(angle) * radius
            val y = centerY + kotlin.math.sin(angle) * radius
            
            canvas.drawCircle(x, y, 6f, paint)
        }
    }
    
    /**
     * Draw quantum particle pattern
     */
    private fun drawQuantumPattern(
        canvas: Canvas,
        primaryColor: Int,
        accentColor: Int,
        width: Int,
        height: Int
    ) {
        val paint = Paint().apply {
            isAntiAlias = true
            strokeWidth = 1f
            style = Paint.Style.STROKE
        }
        
        val centerX = width / 2f
        val centerY = height / 2f
        
        // Draw orbital rings
        paint.color = primaryColor
        paint.alpha = 60
        
        val orbitRadii = listOf(50f, 80f, 120f, 160f)
        orbitRadii.forEach { radius ->
            canvas.drawCircle(centerX, centerY, radius, paint)
        }
        
        // Draw particles
        paint.style = Paint.Style.FILL
        paint.color = accentColor
        paint.alpha = 100
        
        orbitRadii.forEachIndexed { index, radius ->
            val particleCount = 3 + index
            for (i in 0 until particleCount) {
                val angle = (2 * Math.PI * i / particleCount).toFloat()
                val x = centerX + kotlin.math.cos(angle) * radius
                val y = centerY + kotlin.math.sin(angle) * radius
                
                canvas.drawCircle(x, y, 3f, paint)
            }
        }
    }
    
    /**
     * Draw organic branch pattern
     */
    private fun drawOrganicPattern(
        canvas: Canvas,
        primaryColor: Int,
        accentColor: Int,
        width: Int,
        height: Int
    ) {
        val paint = Paint().apply {
            isAntiAlias = true
            strokeWidth = 3f
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
        
        // Draw main trunk
        paint.color = primaryColor
        paint.alpha = 120
        
        val trunkX = width / 2f
        canvas.drawLine(trunkX, height.toFloat(), trunkX, height * 0.3f, paint)
        
        // Draw branches
        paint.strokeWidth = 2f
        paint.alpha = 100
        
        val branchCount = 8
        for (i in 0 until branchCount) {
            val startY = height * (0.9f - i * 0.08f)
            val endY = startY - height * 0.15f
            val offset = (i % 2 * 2 - 1) * width * 0.2f
            val endX = trunkX + offset
            
            // Curved branch using quadratic Bézier
            val path = Path()
            path.moveTo(trunkX, startY)
            path.quadTo(trunkX + offset * 0.3f, startY - height * 0.05f, endX, endY)
            canvas.drawPath(path, paint)
            
            // Draw leaves
            paint.style = Paint.Style.FILL
            paint.color = accentColor
            paint.alpha = 80
            canvas.drawCircle(endX, endY, 5f, paint)
            paint.style = Paint.Style.STROKE
        }
    }
    
    /**
     * Optimize vector drawable for performance
     */
    fun optimizeVectorForPerformance(
        drawable: android.graphics.drawable.Drawable,
        quality: AnimationQuality
    ): android.graphics.drawable.Drawable {
        return when (quality) {
            AnimationQuality.LOW -> {
                // Reduce complexity for low-end devices
                drawable.alpha = 200 // Slightly transparent to reduce rendering load
                drawable
            }
            AnimationQuality.MEDIUM -> {
                drawable.alpha = 230
                drawable
            }
            AnimationQuality.HIGH -> drawable
        }
    }
    
    /**
     * Create path for complex shapes
     */
    fun createComplexPath(elements: List<ThemeElement>): Path {
        val path = Path()
        
        elements.forEach { element ->
            when (element.type) {
                ElementType.CIRCUIT -> addCircuitPath(path, element)
                ElementType.NEURAL_NODE -> addNeuralNodePath(path, element)
                ElementType.ORBIT -> addOrbitPath(path, element)
                ElementType.BRANCH -> addBranchPath(path, element)
                else -> {
                    // Add circle for generic elements
                    path.addCircle(
                        element.position.x,
                        element.position.y,
                        10f * element.scale,
                        Path.Direction.CW
                    )
                }
            }
        }
        
        return path
    }
    
    private fun addCircuitPath(path: Path, element: ThemeElement) {
        val size = 20f * element.scale
        path.addRect(
            element.position.x - size,
            element.position.y - size,
            element.position.x + size,
            element.position.y + size,
            Path.Direction.CW
        )
    }
    
    private fun addNeuralNodePath(path: Path, element: ThemeElement) {
        path.addCircle(
            element.position.x,
            element.position.y,
            8f * element.scale,
            Path.Direction.CW
        )
    }
    
    private fun addOrbitPath(path: Path, element: ThemeElement) {
        val radius = 50f * element.scale
        path.addCircle(
            element.position.x,
            element.position.y,
            radius,
            Path.Direction.CW
        )
    }
    
    private fun addBranchPath(path: Path, element: ThemeElement) {
        val length = 40f * element.scale
        path.moveTo(element.position.x, element.position.y)
        path.lineTo(
            element.position.x + length * kotlin.math.cos(element.rotation),
            element.position.y + length * kotlin.math.sin(element.rotation)
        )
    }
    
    /**
     * Apply blur effect to drawable
     */
    fun applyBlurEffect(
        bitmap: Bitmap,
        radius: Float = 5f
    ): Bitmap {
        val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        
        val paint = Paint().apply {
            isAntiAlias = true
            maskFilter = BlurMaskFilter(radius, BlurMaskFilter.Blur.NORMAL)
        }
        
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return output
    }
    
    /**
     * Create gradient based on theme
     */
    fun createThemeGradient(
        theme: AITheme,
        context: Context,
        width: Int,
        height: Int
    ): LinearGradient {
        val primaryColor = ContextCompat.getColor(context, theme.colors.primary)
        val secondaryColor = ContextCompat.getColor(context, theme.colors.secondary)
        val backgroundColor = ContextCompat.getColor(context, theme.colors.background)
        
        return LinearGradient(
            0f, 0f, width.toFloat(), height.toFloat(),
            intArrayOf(backgroundColor, primaryColor, secondaryColor),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
    }
}