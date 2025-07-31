package com.playstudio.AITeacher

import android.animation.*
import android.content.Context
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import androidx.core.animation.doOnEnd
import androidx.interpolator.view.animation.FastOutSlowInInterpolator

object Material3AnimationUtils {
    
    fun animateButtonPress(view: View, onAnimationEnd: (() -> Unit)? = null) {
        val scaleX = ObjectAnimator.ofFloat(view, "scaleX", 1f, 0.95f)
        val scaleY = ObjectAnimator.ofFloat(view, "scaleY", 1f, 0.95f)
        
        val animatorSet = AnimatorSet().apply {
            playTogether(scaleX, scaleY)
            duration = 100
            interpolator = DecelerateInterpolator()
        }
        
        animatorSet.doOnEnd {
            animateButtonRelease(view, onAnimationEnd)
        }
        
        animatorSet.start()
    }
    
    private fun animateButtonRelease(view: View, onAnimationEnd: (() -> Unit)? = null) {
        val scaleX = ObjectAnimator.ofFloat(view, "scaleX", 0.95f, 1f)
        val scaleY = ObjectAnimator.ofFloat(view, "scaleY", 0.95f, 1f)
        
        val animatorSet = AnimatorSet().apply {
            playTogether(scaleX, scaleY)
            duration = 150
            interpolator = AccelerateDecelerateInterpolator()
        }
        
        animatorSet.doOnEnd {
            onAnimationEnd?.invoke()
        }
        
        animatorSet.start()
    }
    
    fun animateMessageSlideIn(view: View, fromRight: Boolean = true, delay: Long = 0) {
        val startX = if (fromRight) view.width.toFloat() else -view.width.toFloat()
        
        view.translationX = startX
        view.alpha = 0f
        
        val translateX = ObjectAnimator.ofFloat(view, "translationX", startX, 0f)
        val alpha = ObjectAnimator.ofFloat(view, "alpha", 0f, 1f)
        
        val animatorSet = AnimatorSet().apply {
            playTogether(translateX, alpha)
            duration = 300
            startDelay = delay
            interpolator = FastOutSlowInInterpolator()
        }
        
        animatorSet.start()
    }
    
    fun animateChipFadeIn(view: View, delay: Long = 0) {
        view.alpha = 0f
        view.translationY = 20f
        
        val alpha = ObjectAnimator.ofFloat(view, "alpha", 0f, 1f)
        val translateY = ObjectAnimator.ofFloat(view, "translationY", 20f, 0f)
        
        val animatorSet = AnimatorSet().apply {
            playTogether(alpha, translateY)
            duration = 250
            startDelay = delay + 100
            interpolator = DecelerateInterpolator()
        }
        
        animatorSet.start()
    }
    
    fun animateViewScale(view: View, scale: Float, duration: Long = 200) {
        val scaleX = ObjectAnimator.ofFloat(view, "scaleX", view.scaleX, scale)
        val scaleY = ObjectAnimator.ofFloat(view, "scaleY", view.scaleY, scale)
        
        AnimatorSet().apply {
            playTogether(scaleX, scaleY)
            this.duration = duration
            interpolator = FastOutSlowInInterpolator()
            start()
        }
    }
    
    fun animateElevationChange(view: View, fromElevation: Float, toElevation: Float) {
        val elevationAnimator = ObjectAnimator.ofFloat(view, "elevation", fromElevation, toElevation)
        elevationAnimator.apply {
            duration = 150
            interpolator = FastOutSlowInInterpolator()
            start()
        }
    }
    
    fun createRippleEffect(view: View, centerX: Float, centerY: Float) {
        val maxRadius = kotlin.math.max(view.width, view.height).toFloat()
        
        val rippleAnimator = ValueAnimator.ofFloat(0f, maxRadius).apply {
            duration = 300
            interpolator = DecelerateInterpolator()
            
            addUpdateListener { animator ->
                val radius = animator.animatedValue as Float
                // This would need a custom drawable or canvas drawing for full ripple effect
                // For now, we'll use a simple scale animation
                val scale = 1f + (radius / maxRadius) * 0.1f
                view.scaleX = scale
                view.scaleY = scale
            }
            
            doOnEnd {
                view.scaleX = 1f
                view.scaleY = 1f
            }
        }
        
        rippleAnimator.start()
    }
    
    fun animateThemeTransition(context: Context, onTransitionComplete: () -> Unit) {
        // Simple fade transition for theme changes
        val duration = 200L
        
        // This would typically involve animating the entire activity/fragment
        // For now, we'll just call the completion handler after a delay
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            onTransitionComplete()
        }, duration)
    }
    
    fun animateProgressIndicator(view: View, isVisible: Boolean) {
        val targetAlpha = if (isVisible) 1f else 0f
        val targetScale = if (isVisible) 1f else 0.8f
        
        val alpha = ObjectAnimator.ofFloat(view, "alpha", view.alpha, targetAlpha)
        val scaleX = ObjectAnimator.ofFloat(view, "scaleX", view.scaleX, targetScale)
        val scaleY = ObjectAnimator.ofFloat(view, "scaleY", view.scaleY, targetScale)
        
        AnimatorSet().apply {
            playTogether(alpha, scaleX, scaleY)
            duration = 200
            interpolator = FastOutSlowInInterpolator()
            
            if (!isVisible) {
                doOnEnd { view.visibility = View.GONE }
            } else {
                view.visibility = View.VISIBLE
            }
            
            start()
        }
    }
}