package com.playstudio.AITeacher

import android.content.Context
import android.content.SharedPreferences
import android.graphics.drawable.AnimatedVectorDrawable
import android.graphics.drawable.Drawable
import android.view.View
import android.view.animation.*
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.playstudio.AITeacher.models.*
import kotlinx.coroutines.*

/**
 * Enhanced AI theme management class that handles theme switching, animations, and persistence
 */
class AIThemeManager private constructor(private val context: Context) {
    
    companion object {
        @Volatile
        private var INSTANCE: AIThemeManager? = null
        
        private const val PREFS_NAME = "ai_theme_preferences"
        private const val KEY_CURRENT_THEME = "current_theme_id"
        private const val KEY_ANIMATIONS_ENABLED = "animations_enabled"
        private const val KEY_ANIMATION_QUALITY = "animation_quality"
        private const val KEY_PARTICLES_ENABLED = "particles_enabled"
        
        fun getInstance(context: Context): AIThemeManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AIThemeManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val preferences: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val themeScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    // LiveData for theme state
    private val _currentTheme = MutableLiveData<AITheme>()
    val currentTheme: LiveData<AITheme> = _currentTheme
    
    private val _isAnimating = MutableLiveData<Boolean>(false)
    val isAnimating: LiveData<Boolean> = _isAnimating
    
    private val _themeConfig = MutableLiveData<ThemeConfig>()
    val themeConfig: LiveData<ThemeConfig> = _themeConfig
    
    // Theme state management
    private var currentThemeState: ThemeState? = null
    private val activeAnimations = mutableListOf<Animation>()
    
    init {
        loadSavedConfiguration()
        initializeDefaultTheme()
    }
    
    /**
     * Load saved configuration from SharedPreferences
     */
    private fun loadSavedConfiguration() {
        val animationsEnabled = preferences.getBoolean(KEY_ANIMATIONS_ENABLED, true)
        val particlesEnabled = preferences.getBoolean(KEY_PARTICLES_ENABLED, true)
        val animationQualityOrdinal = preferences.getInt(KEY_ANIMATION_QUALITY, AnimationQuality.HIGH.ordinal)
        val animationQuality = AnimationQuality.values()[animationQualityOrdinal]
        
        val config = ThemeConfig(
            enableAnimations = animationsEnabled,
            enableParticles = particlesEnabled,
            animationQuality = animationQuality,
            frameRate = when(animationQuality) {
                AnimationQuality.LOW -> 30
                AnimationQuality.MEDIUM -> 45
                AnimationQuality.HIGH -> 60
            }
        )
        
        _themeConfig.value = config
    }
    
    /**
     * Initialize with saved or default theme
     */
    private fun initializeDefaultTheme() {
        val savedThemeId = preferences.getString(KEY_CURRENT_THEME, ThemeVariants.CYBER_ROBOT)
        val theme = ThemeFactory.getThemeById(savedThemeId!!) ?: ThemeFactory.createCyberRobotTheme()
        _currentTheme.value = theme
        currentThemeState = ThemeState(currentTheme = theme)
    }
    
    /**
     * Get current theme
     */
    fun getCurrentTheme(): AITheme {
        return _currentTheme.value ?: ThemeFactory.createCyberRobotTheme()
    }
    
    /**
     * Set new theme with optional animation
     */
    fun setTheme(theme: AITheme, animate: Boolean = true) {
        themeScope.launch {
            if (animate && _themeConfig.value?.enableAnimations == true) {
                _isAnimating.value = true
                animateThemeTransition(getCurrentTheme(), theme)
            } else {
                applyThemeInstantly(theme)
            }
            
            // Save to preferences
            preferences.edit()
                .putString(KEY_CURRENT_THEME, theme.id)
                .apply()
        }
    }
    
    /**
     * Get all available themes
     */
    fun getAllThemes(): List<AITheme> {
        return ThemeFactory.getAllThemes()
    }
    
    /**
     * Apply theme to a view
     */
    fun applyThemeToView(view: View, theme: AITheme? = null) {
        val targetTheme = theme ?: getCurrentTheme()
        
        when (view) {
            is ImageView -> applyThemeToImageView(view, targetTheme)
            else -> applyThemeToGenericView(view, targetTheme)
        }
    }
    
    /**
     * Start theme animation
     */
    fun startThemeAnimation(theme: AITheme? = null) {
        val targetTheme = theme ?: getCurrentTheme()
        val config = _themeConfig.value ?: return
        
        if (!config.enableAnimations || !targetTheme.isAnimated) return
        
        themeScope.launch {
            _isAnimating.value = true
            
            when (targetTheme.animations.primaryAnimationType) {
                AnimationType.PULSE -> startPulseAnimation(targetTheme)
                AnimationType.ROTATE -> startRotateAnimation(targetTheme)
                AnimationType.SCALE -> startScaleAnimation(targetTheme)
                AnimationType.FADE -> startFadeAnimation(targetTheme)
                AnimationType.TRANSLATE -> startTranslateAnimation(targetTheme)
                AnimationType.GLOW -> startGlowAnimation(targetTheme)
                AnimationType.WAVE -> startWaveAnimation(targetTheme)
                AnimationType.MORPH -> startMorphAnimation(targetTheme)
            }
        }
    }
    
    /**
     * Stop all animations
     */
    fun stopAllAnimations() {
        activeAnimations.forEach { it.cancel() }
        activeAnimations.clear()
        _isAnimating.value = false
    }
    
    /**
     * Update theme configuration
     */
    fun updateThemeConfig(config: ThemeConfig) {
        _themeConfig.value = config
        
        // Save to preferences
        preferences.edit()
            .putBoolean(KEY_ANIMATIONS_ENABLED, config.enableAnimations)
            .putBoolean(KEY_PARTICLES_ENABLED, config.enableParticles)
            .putInt(KEY_ANIMATION_QUALITY, config.animationQuality.ordinal)
            .apply()
        
        // Restart animations if enabled
        if (config.enableAnimations) {
            startThemeAnimation()
        } else {
            stopAllAnimations()
        }
    }
    
    /**
     * Get drawable for theme
     */
    fun getThemeDrawable(theme: AITheme, animated: Boolean = true): Drawable? {
        val drawableRes = if (animated && theme.isAnimated) {
            theme.drawables.animatedDrawable
        } else {
            theme.drawables.staticDrawable
        }
        
        return ContextCompat.getDrawable(context, drawableRes)
    }
    
    /**
     * Get color for theme
     */
    fun getThemeColor(theme: AITheme, colorType: String): Int {
        val colorRes = when (colorType) {
            "primary" -> theme.colors.primary
            "secondary" -> theme.colors.secondary
            "background" -> theme.colors.background
            "accent" -> theme.colors.accent
            else -> theme.colors.primary
        }
        
        return ContextCompat.getColor(context, colorRes)
    }
    
    // Private helper methods
    
    private suspend fun animateThemeTransition(fromTheme: AITheme, toTheme: AITheme) {
        // Implement smooth transition between themes
        val transitionDuration = 500L
        
        // Crossfade animation
        delay(transitionDuration)
        applyThemeInstantly(toTheme)
        _isAnimating.value = false
    }
    
    private fun applyThemeInstantly(theme: AITheme) {
        _currentTheme.value = theme
        currentThemeState = ThemeState(currentTheme = theme)
    }
    
    private fun applyThemeToImageView(imageView: ImageView, theme: AITheme) {
        val config = _themeConfig.value ?: return
        val drawable = getThemeDrawable(theme, config.enableAnimations)
        
        imageView.setImageDrawable(drawable)
        
        // Start animation if it's an animated vector drawable
        if (drawable is AnimatedVectorDrawable && config.enableAnimations) {
            drawable.start()
        }
    }
    
    private fun applyThemeToGenericView(view: View, theme: AITheme) {
        // Apply background color
        view.setBackgroundColor(getThemeColor(theme, "background"))
    }
    
    // Animation implementations
    
    private fun startPulseAnimation(theme: AITheme) {
        val animation = createPulseAnimation(theme.animations.duration)
        activeAnimations.add(animation)
    }
    
    private fun startRotateAnimation(theme: AITheme) {
        val animation = createRotateAnimation(theme.animations.duration)
        activeAnimations.add(animation)
    }
    
    private fun startScaleAnimation(theme: AITheme) {
        val animation = createScaleAnimation(theme.animations.duration)
        activeAnimations.add(animation)
    }
    
    private fun startFadeAnimation(theme: AITheme) {
        val animation = createFadeAnimation(theme.animations.duration)
        activeAnimations.add(animation)
    }
    
    private fun startTranslateAnimation(theme: AITheme) {
        val animation = createTranslateAnimation(theme.animations.duration)
        activeAnimations.add(animation)
    }
    
    private fun startGlowAnimation(theme: AITheme) {
        val animation = createGlowAnimation(theme.animations.duration)
        activeAnimations.add(animation)
    }
    
    private fun startWaveAnimation(theme: AITheme) {
        val animation = createWaveAnimation(theme.animations.duration)
        activeAnimations.add(animation)
    }
    
    private fun startMorphAnimation(theme: AITheme) {
        val animation = createMorphAnimation(theme.animations.duration)
        activeAnimations.add(animation)
    }
    
    // Animation creation methods
    
    private fun createPulseAnimation(duration: Long): Animation {
        return ScaleAnimation(
            0.95f, 1.05f, 0.95f, 1.05f,
            Animation.RELATIVE_TO_SELF, 0.5f,
            Animation.RELATIVE_TO_SELF, 0.5f
        ).apply {
            this.duration = duration
            repeatCount = Animation.INFINITE
            repeatMode = Animation.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
        }
    }
    
    private fun createRotateAnimation(duration: Long): Animation {
        return RotateAnimation(
            0f, 360f,
            Animation.RELATIVE_TO_SELF, 0.5f,
            Animation.RELATIVE_TO_SELF, 0.5f
        ).apply {
            this.duration = duration
            repeatCount = Animation.INFINITE
            interpolator = LinearInterpolator()
        }
    }
    
    private fun createScaleAnimation(duration: Long): Animation {
        return ScaleAnimation(
            0.8f, 1.2f, 0.8f, 1.2f,
            Animation.RELATIVE_TO_SELF, 0.5f,
            Animation.RELATIVE_TO_SELF, 0.5f
        ).apply {
            this.duration = duration
            repeatCount = Animation.INFINITE
            repeatMode = Animation.REVERSE
            interpolator = BounceInterpolator()
        }
    }
    
    private fun createFadeAnimation(duration: Long): Animation {
        return AlphaAnimation(0.3f, 1.0f).apply {
            this.duration = duration
            repeatCount = Animation.INFINITE
            repeatMode = Animation.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
        }
    }
    
    private fun createTranslateAnimation(duration: Long): Animation {
        return TranslateAnimation(
            Animation.RELATIVE_TO_SELF, -0.1f,
            Animation.RELATIVE_TO_SELF, 0.1f,
            Animation.RELATIVE_TO_SELF, 0f,
            Animation.RELATIVE_TO_SELF, 0f
        ).apply {
            this.duration = duration
            repeatCount = Animation.INFINITE
            repeatMode = Animation.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
        }
    }
    
    private fun createGlowAnimation(duration: Long): Animation {
        return AlphaAnimation(0.5f, 1.0f).apply {
            this.duration = duration
            repeatCount = Animation.INFINITE
            repeatMode = Animation.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
        }
    }
    
    private fun createWaveAnimation(duration: Long): Animation {
        return AnimationSet(true).apply {
            addAnimation(createScaleAnimation(duration))
            addAnimation(createFadeAnimation(duration))
        }
    }
    
    private fun createMorphAnimation(duration: Long): Animation {
        return AnimationSet(true).apply {
            addAnimation(createScaleAnimation(duration))
            addAnimation(createRotateAnimation(duration / 2))
        }
    }
    
    /**
     * Clean up resources
     */
    fun cleanup() {
        stopAllAnimations()
        themeScope.cancel()
    }
}