package com.playstudio.AITeacher.models

import android.graphics.drawable.Drawable
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes

/**
 * Data class representing an AI theme with all its visual properties
 */
data class AITheme(
    val id: String,
    val name: String,
    val description: String,
    val category: ThemeCategory,
    val colors: ThemeColors,
    val drawables: ThemeDrawables,
    val animations: ThemeAnimations,
    val elements: List<ThemeElement>,
    val isAnimated: Boolean = true,
    val isPremium: Boolean = false
)

/**
 * Theme categories for organization
 */
enum class ThemeCategory {
    CYBERPUNK,
    NEURAL,
    QUANTUM,
    ORGANIC
}

/**
 * Color scheme for a theme
 */
data class ThemeColors(
    @ColorRes val primary: Int,
    @ColorRes val secondary: Int,
    @ColorRes val background: Int,
    @ColorRes val accent: Int,
    @ColorRes val primaryTransparent50: Int? = null,
    @ColorRes val primaryTransparent30: Int? = null,
    @ColorRes val primaryTransparent10: Int? = null
)

/**
 * Drawable resources for a theme
 */
data class ThemeDrawables(
    @DrawableRes val staticDrawable: Int,
    @DrawableRes val animatedDrawable: Int,
    @DrawableRes val iconDrawable: Int? = null,
    @DrawableRes val backgroundPattern: Int? = null
)

/**
 * Animation configuration for a theme
 */
data class ThemeAnimations(
    val primaryAnimationType: AnimationType,
    val duration: Long = 2000L,
    val interpolator: AnimationInterpolator = AnimationInterpolator.ACCELERATE_DECELERATE,
    val repeatCount: Int = -1, // Infinite
    val repeatMode: AnimationRepeatMode = AnimationRepeatMode.REVERSE,
    val autoStart: Boolean = true
)

/**
 * Animation types available
 */
enum class AnimationType {
    PULSE,
    ROTATE,
    SCALE,
    FADE,
    TRANSLATE,
    MORPH,
    GLOW,
    WAVE
}

/**
 * Animation interpolators
 */
enum class AnimationInterpolator {
    LINEAR,
    ACCELERATE,
    DECELERATE,
    ACCELERATE_DECELERATE,
    BOUNCE,
    OVERSHOOT,
    ANTICIPATE
}

/**
 * Animation repeat modes
 */
enum class AnimationRepeatMode {
    RESTART,
    REVERSE
}

/**
 * Individual theme elements with positioning
 */
data class ThemeElement(
    val type: ElementType,
    val position: ElementPosition,
    val isAnimated: Boolean = false,
    val animationType: AnimationType? = null,
    val scale: Float = 1.0f,
    val rotation: Float = 0f,
    val alpha: Float = 1.0f
)

/**
 * Types of visual elements
 */
enum class ElementType {
    HEAD,
    BODY,
    ANTENNA,
    EYES,
    NEURAL_NODE,
    SYNAPSE,
    CIRCUIT,
    ORBIT,
    PARTICLE,
    BRANCH,
    LEAF,
    ROOT,
    CORE,
    WAVE,
    CRYSTAL,
    CUBE
}

/**
 * Position of an element
 */
data class ElementPosition(
    val x: Float,
    val y: Float,
    val z: Float = 0f // For 3D effects
)

/**
 * Theme configuration with performance settings
 */
data class ThemeConfig(
    val enableAnimations: Boolean = true,
    val enableParticles: Boolean = true,
    val animationQuality: AnimationQuality = AnimationQuality.HIGH,
    val frameRate: Int = 60,
    val enableGlow: Boolean = true,
    val enableShadows: Boolean = true,
    val particleCount: Int = 20
)

/**
 * Animation quality levels
 */
enum class AnimationQuality {
    LOW,    // 30fps, reduced effects
    MEDIUM, // 45fps, standard effects
    HIGH    // 60fps, full effects
}

/**
 * Predefined theme variants
 */
object ThemeVariants {
    const val CYBER_ROBOT = "cyber_robot"
    const val DIGITAL_BRAIN = "digital_brain"
    const val QUANTUM_AI = "quantum_ai"
    const val NEURAL_FOREST = "neural_forest"
}

/**
 * Theme state for managing current selection
 */
data class ThemeState(
    val currentTheme: AITheme,
    val isAnimating: Boolean = false,
    val animationProgress: Float = 0f,
    val lastUpdated: Long = System.currentTimeMillis()
)