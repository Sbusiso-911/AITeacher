package com.playstudio.AITeacher.utils

import android.animation.*
import android.content.Context
import android.graphics.drawable.AnimatedVectorDrawable
import android.view.View
import android.view.animation.*
import androidx.core.animation.doOnEnd
import androidx.core.animation.doOnStart
import com.playstudio.AITeacher.models.*

/**
 * Utility class for creating and managing AI theme animations
 */
object AnimationUtils {
    
    /**
     * Create animation based on type and configuration
     */
    fun createAnimation(
        context: Context?,
        animationType: AnimationType,
        config: ThemeAnimations
    ): Animation {
        return when (animationType) {
            AnimationType.PULSE -> createPulseAnimation(config)
            AnimationType.ROTATE -> createRotateAnimation(config)
            AnimationType.SCALE -> createScaleAnimation(config)
            AnimationType.FADE -> createFadeAnimation(config)
            AnimationType.TRANSLATE -> createTranslateAnimation(config)
            AnimationType.GLOW -> createGlowAnimation(config)
            AnimationType.WAVE -> createWaveAnimation(config)
            AnimationType.MORPH -> createMorphAnimation(config)
        }
    }
    
    /**
     * Create pulse animation (scale + alpha)
     */
    private fun createPulseAnimation(config: ThemeAnimations): Animation {
        val animationSet = AnimationSet(true)
        
        val scaleAnimation = ScaleAnimation(
            0.95f, 1.05f, 0.95f, 1.05f,
            Animation.RELATIVE_TO_SELF, 0.5f,
            Animation.RELATIVE_TO_SELF, 0.5f
        )
        
        val alphaAnimation = AlphaAnimation(0.7f, 1.0f)
        
        listOf(scaleAnimation, alphaAnimation).forEach { animation ->
            animation.duration = config.duration
            animation.repeatCount = config.repeatCount
            animation.repeatMode = when (config.repeatMode) {
                AnimationRepeatMode.REVERSE -> Animation.REVERSE
                AnimationRepeatMode.RESTART -> Animation.RESTART
            }
            animation.interpolator = getInterpolator(config.interpolator)
        }
        
        animationSet.addAnimation(scaleAnimation)
        animationSet.addAnimation(alphaAnimation)
        
        return animationSet
    }
    
    /**
     * Create rotation animation
     */
    private fun createRotateAnimation(config: ThemeAnimations): Animation {
        return RotateAnimation(
            0f, 360f,
            Animation.RELATIVE_TO_SELF, 0.5f,
            Animation.RELATIVE_TO_SELF, 0.5f
        ).apply {
            duration = config.duration
            repeatCount = config.repeatCount
            interpolator = getInterpolator(config.interpolator)
        }
    }
    
    /**
     * Create scale animation
     */
    private fun createScaleAnimation(config: ThemeAnimations): Animation {
        return ScaleAnimation(
            0.8f, 1.2f, 0.8f, 1.2f,
            Animation.RELATIVE_TO_SELF, 0.5f,
            Animation.RELATIVE_TO_SELF, 0.5f
        ).apply {
            duration = config.duration
            repeatCount = config.repeatCount
            repeatMode = when (config.repeatMode) {
                AnimationRepeatMode.REVERSE -> Animation.REVERSE
                AnimationRepeatMode.RESTART -> Animation.RESTART
            }
            interpolator = getInterpolator(config.interpolator)
        }
    }
    
    /**
     * Create fade animation
     */
    private fun createFadeAnimation(config: ThemeAnimations): Animation {
        return AlphaAnimation(0.3f, 1.0f).apply {
            duration = config.duration
            repeatCount = config.repeatCount
            repeatMode = when (config.repeatMode) {
                AnimationRepeatMode.REVERSE -> Animation.REVERSE
                AnimationRepeatMode.RESTART -> Animation.RESTART
            }
            interpolator = getInterpolator(config.interpolator)
        }
    }
    
    /**
     * Create translate animation
     */
    private fun createTranslateAnimation(config: ThemeAnimations): Animation {
        return TranslateAnimation(
            Animation.RELATIVE_TO_SELF, -0.05f,
            Animation.RELATIVE_TO_SELF, 0.05f,
            Animation.RELATIVE_TO_SELF, -0.02f,
            Animation.RELATIVE_TO_SELF, 0.02f
        ).apply {
            duration = config.duration
            repeatCount = config.repeatCount
            repeatMode = when (config.repeatMode) {
                AnimationRepeatMode.REVERSE -> Animation.REVERSE
                AnimationRepeatMode.RESTART -> Animation.RESTART
            }
            interpolator = getInterpolator(config.interpolator)
        }
    }
    
    /**
     * Create glow animation (enhanced alpha with scale)
     */
    private fun createGlowAnimation(config: ThemeAnimations): Animation {
        val animationSet = AnimationSet(true)
        
        val alphaAnimation = AlphaAnimation(0.5f, 1.0f)
        val scaleAnimation = ScaleAnimation(
            0.98f, 1.02f, 0.98f, 1.02f,
            Animation.RELATIVE_TO_SELF, 0.5f,
            Animation.RELATIVE_TO_SELF, 0.5f
        )
        
        listOf(alphaAnimation, scaleAnimation).forEach { animation ->
            animation.duration = config.duration
            animation.repeatCount = config.repeatCount
            animation.repeatMode = Animation.REVERSE
            animation.interpolator = getInterpolator(config.interpolator)
        }
        
        animationSet.addAnimation(alphaAnimation)
        animationSet.addAnimation(scaleAnimation)
        
        return animationSet
    }
    
    /**
     * Create wave animation (complex multi-stage)
     */
    private fun createWaveAnimation(config: ThemeAnimations): Animation {
        val animationSet = AnimationSet(false) // Don't share interpolator
        
        // Wave scale
        val scaleAnimation = ScaleAnimation(
            1.0f, 1.3f, 1.0f, 1.3f,
            Animation.RELATIVE_TO_SELF, 0.5f,
            Animation.RELATIVE_TO_SELF, 0.5f
        ).apply {
            duration = config.duration
            repeatCount = config.repeatCount
            repeatMode = Animation.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
        }
        
        // Wave fade
        val alphaAnimation = AlphaAnimation(0.8f, 0.0f).apply {
            duration = config.duration
            repeatCount = config.repeatCount
            repeatMode = Animation.REVERSE
            interpolator = LinearInterpolator()
        }
        
        animationSet.addAnimation(scaleAnimation)
        animationSet.addAnimation(alphaAnimation)
        
        return animationSet
    }
    
    /**
     * Create morph animation (complex transformation)
     */
    private fun createMorphAnimation(config: ThemeAnimations): Animation {
        val animationSet = AnimationSet(true)
        
        val scaleAnimation = ScaleAnimation(
            0.9f, 1.1f, 1.1f, 0.9f,
            Animation.RELATIVE_TO_SELF, 0.5f,
            Animation.RELATIVE_TO_SELF, 0.5f
        )
        
        val rotateAnimation = RotateAnimation(
            0f, 180f,
            Animation.RELATIVE_TO_SELF, 0.5f,
            Animation.RELATIVE_TO_SELF, 0.5f
        )
        
        val alphaAnimation = AlphaAnimation(1.0f, 0.7f)
        
        listOf(scaleAnimation, rotateAnimation, alphaAnimation).forEach { animation ->
            animation.duration = config.duration
            animation.repeatCount = config.repeatCount
            animation.repeatMode = Animation.REVERSE
            animation.interpolator = getInterpolator(config.interpolator)
        }
        
        animationSet.addAnimation(scaleAnimation)
        animationSet.addAnimation(rotateAnimation)
        animationSet.addAnimation(alphaAnimation)
        
        return animationSet
    }
    
    /**
     * Get interpolator based on type
     */
    private fun getInterpolator(interpolatorType: AnimationInterpolator): Interpolator {
        return when (interpolatorType) {
            AnimationInterpolator.LINEAR -> LinearInterpolator()
            AnimationInterpolator.ACCELERATE -> AccelerateInterpolator()
            AnimationInterpolator.DECELERATE -> DecelerateInterpolator()
            AnimationInterpolator.ACCELERATE_DECELERATE -> AccelerateDecelerateInterpolator()
            AnimationInterpolator.BOUNCE -> BounceInterpolator()
            AnimationInterpolator.OVERSHOOT -> OvershootInterpolator()
            AnimationInterpolator.ANTICIPATE -> AnticipateInterpolator()
        }
    }
    
    /**
     * Create theme transition animation
     */
    fun createThemeTransition(
        fromTheme: AITheme,
        toTheme: AITheme,
        duration: Long = 500L
    ): AnimatorSet {
        val animatorSet = AnimatorSet()
        
        // Fade out current theme
        val fadeOut = ObjectAnimator.ofFloat(null, "alpha", 1f, 0f).apply {
            this.duration = duration / 2
        }
        
        // Scale down current theme
        val scaleDownX = ObjectAnimator.ofFloat(null, "scaleX", 1f, 0.95f).apply {
            this.duration = duration / 2
        }
        val scaleDownY = ObjectAnimator.ofFloat(null, "scaleY", 1f, 0.95f).apply {
            this.duration = duration / 2
        }
        
        // Fade in new theme
        val fadeIn = ObjectAnimator.ofFloat(null, "alpha", 0f, 1f).apply {
            this.duration = duration / 2
            startDelay = duration / 2
        }
        
        // Scale up new theme
        val scaleUpX = ObjectAnimator.ofFloat(null, "scaleX", 0.95f, 1f).apply {
            this.duration = duration / 2
            startDelay = duration / 2
        }
        val scaleUpY = ObjectAnimator.ofFloat(null, "scaleY", 0.95f, 1f).apply {
            this.duration = duration / 2
            startDelay = duration / 2
        }
        
        animatorSet.playTogether(fadeOut, scaleDownX, scaleDownY, fadeIn, scaleUpX, scaleUpY)
        animatorSet.interpolator = AccelerateDecelerateInterpolator()
        
        return animatorSet
    }
    
    /**
     * Apply animation to view with lifecycle management
     */
    fun applyAnimationToView(
        view: View,
        animation: Animation,
        onStart: (() -> Unit)? = null,
        onEnd: (() -> Unit)? = null
    ) {
        animation.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation?) {
                onStart?.invoke()
            }
            
            override fun onAnimationEnd(animation: Animation?) {
                onEnd?.invoke()
            }
            
            override fun onAnimationRepeat(animation: Animation?) {
                // No action needed
            }
        })
        
        view.startAnimation(animation)
    }
    
    /**
     * Start animated vector drawable with fallback
     */
    fun startAnimatedVector(drawable: AnimatedVectorDrawable?, onError: (() -> Unit)? = null) {
        try {
            drawable?.start()
        } catch (e: Exception) {
            onError?.invoke()
        }
    }
    
    /**
     * Stop all animations on a view
     */
    fun stopAllAnimations(view: View) {
        view.clearAnimation()
        view.animate().cancel()
    }
    
    /**
     * Create performance-optimized animation based on device capabilities
     */
    fun createOptimizedAnimation(
        animationType: AnimationType,
        config: ThemeAnimations,
        quality: AnimationQuality
    ): Animation {
        val optimizedConfig = when (quality) {
            AnimationQuality.LOW -> config.copy(
                duration = config.duration * 2, // Slower for battery
                repeatCount = if (config.repeatCount == -1) 3 else minOf(config.repeatCount, 3)
            )
            AnimationQuality.MEDIUM -> config.copy(
                duration = (config.duration * 1.5).toLong()
            )
            AnimationQuality.HIGH -> config
        }
        
        return createAnimation(null, animationType, optimizedConfig)
    }
    
    /**
     * Batch animation operations for performance
     */
    fun batchAnimations(operations: List<() -> Unit>) {
        operations.forEach { it.invoke() }
    }
}