package com.playstudio.AITeacher.models

import com.playstudio.aiteacher.R
/**
 * Theme configuration and factory for creating theme instances
 */
object ThemeFactory {
    
    /**
     * Create a Cyber Robot theme instance
     */
    fun createCyberRobotTheme(): AITheme {
        return AITheme(
            id = ThemeVariants.CYBER_ROBOT,
            name = "Cyber Robot",
            description = "Futuristic AI robot with neural networks",
            category = ThemeCategory.CYBERPUNK,
            colors = ThemeColors(
                primary = R.color.cyber_primary,
                secondary = R.color.cyber_secondary,
                background = R.color.cyber_background,
                accent = R.color.cyber_accent,
                primaryTransparent50 = R.color.cyber_primary_50,
                primaryTransparent30 = R.color.cyber_primary_30,
                primaryTransparent10 = R.color.cyber_primary_10
            ),
            drawables = ThemeDrawables(
                staticDrawable = R.drawable.theme_cyber_robot,
                animatedDrawable = R.drawable.theme_cyber_robot_animated
            ),
            animations = ThemeAnimations(
                primaryAnimationType = AnimationType.PULSE,
                duration = 2000L,
                interpolator = AnimationInterpolator.ACCELERATE_DECELERATE
            ),
            elements = listOf(
                ThemeElement(
                    type = ElementType.HEAD,
                    position = ElementPosition(200f, 200f),
                    isAnimated = true,
                    animationType = AnimationType.GLOW
                ),
                ThemeElement(
                    type = ElementType.BODY,
                    position = ElementPosition(200f, 300f),
                    isAnimated = false
                ),
                ThemeElement(
                    type = ElementType.ANTENNA,
                    position = ElementPosition(200f, 155f),
                    isAnimated = true,
                    animationType = AnimationType.PULSE
                ),
                ThemeElement(
                    type = ElementType.EYES,
                    position = ElementPosition(200f, 210f),
                    isAnimated = true,
                    animationType = AnimationType.FADE
                )
            ),
            isAnimated = true,
            isPremium = false
        )
    }
    
    /**
     * Create a Digital Brain theme instance
     */
    fun createDigitalBrainTheme(): AITheme {
        return AITheme(
            id = ThemeVariants.DIGITAL_BRAIN,
            name = "Digital Brain",
            description = "Abstract AI consciousness with neural networks",
            category = ThemeCategory.NEURAL,
            colors = ThemeColors(
                primary = R.color.brain_primary,
                secondary = R.color.brain_secondary,
                background = R.color.brain_background,
                accent = R.color.brain_accent,
                primaryTransparent50 = R.color.brain_primary_50,
                primaryTransparent30 = R.color.brain_primary_30,
                primaryTransparent10 = R.color.brain_primary_10
            ),
            drawables = ThemeDrawables(
                staticDrawable = R.drawable.theme_digital_brain,
                animatedDrawable = R.drawable.theme_digital_brain_animated
            ),
            animations = ThemeAnimations(
                primaryAnimationType = AnimationType.WAVE,
                duration = 4000L,
                interpolator = AnimationInterpolator.ACCELERATE_DECELERATE
            ),
            elements = listOf(
                ThemeElement(
                    type = ElementType.CORE,
                    position = ElementPosition(200f, 300f),
                    isAnimated = true,
                    animationType = AnimationType.SCALE
                ),
                ThemeElement(
                    type = ElementType.NEURAL_NODE,
                    position = ElementPosition(200f, 250f),
                    isAnimated = true,
                    animationType = AnimationType.PULSE
                ),
                ThemeElement(
                    type = ElementType.SYNAPSE,
                    position = ElementPosition(180f, 265f),
                    isAnimated = true,
                    animationType = AnimationType.FADE
                )
            ),
            isAnimated = true,
            isPremium = true
        )
    }
    
    /**
     * Create a Quantum AI theme instance
     */
    fun createQuantumAITheme(): AITheme {
        return AITheme(
            id = ThemeVariants.QUANTUM_AI,
            name = "Quantum AI",
            description = "Quantum computing visualization with particles",
            category = ThemeCategory.QUANTUM,
            colors = ThemeColors(
                primary = R.color.quantum_primary,
                secondary = R.color.quantum_secondary,
                background = R.color.quantum_background,
                accent = R.color.quantum_accent,
                primaryTransparent50 = R.color.quantum_primary_50,
                primaryTransparent30 = R.color.quantum_primary_30,
                primaryTransparent10 = R.color.quantum_primary_10
            ),
            drawables = ThemeDrawables(
                staticDrawable = R.drawable.theme_quantum_ai,
                animatedDrawable = R.drawable.theme_quantum_ai_animated
            ),
            animations = ThemeAnimations(
                primaryAnimationType = AnimationType.ROTATE,
                duration = 10000L,
                interpolator = AnimationInterpolator.LINEAR
            ),
            elements = listOf(
                ThemeElement(
                    type = ElementType.CUBE,
                    position = ElementPosition(200f, 300f),
                    isAnimated = true,
                    animationType = AnimationType.ROTATE
                ),
                ThemeElement(
                    type = ElementType.ORBIT,
                    position = ElementPosition(200f, 300f),
                    isAnimated = true,
                    animationType = AnimationType.ROTATE
                ),
                ThemeElement(
                    type = ElementType.PARTICLE,
                    position = ElementPosition(320f, 300f),
                    isAnimated = true,
                    animationType = AnimationType.TRANSLATE
                )
            ),
            isAnimated = true,
            isPremium = true
        )
    }
    
    /**
     * Create a Neural Forest theme instance
     */
    fun createNeuralForestTheme(): AITheme {
        return AITheme(
            id = ThemeVariants.NEURAL_FOREST,
            name = "Neural Forest",
            description = "Organic AI with bio-tech fusion",
            category = ThemeCategory.ORGANIC,
            colors = ThemeColors(
                primary = R.color.forest_primary,
                secondary = R.color.forest_secondary,
                background = R.color.forest_background,
                accent = R.color.forest_accent,
                primaryTransparent50 = R.color.forest_primary_50,
                primaryTransparent30 = R.color.forest_primary_30,
                primaryTransparent10 = R.color.forest_primary_10
            ),
            drawables = ThemeDrawables(
                staticDrawable = R.drawable.theme_neural_forest,
                animatedDrawable = R.drawable.theme_neural_forest_animated
            ),
            animations = ThemeAnimations(
                primaryAnimationType = AnimationType.GLOW,
                duration = 5000L,
                interpolator = AnimationInterpolator.ACCELERATE_DECELERATE
            ),
            elements = listOf(
                ThemeElement(
                    type = ElementType.CORE,
                    position = ElementPosition(200f, 300f),
                    isAnimated = true,
                    animationType = AnimationType.SCALE
                ),
                ThemeElement(
                    type = ElementType.BRANCH,
                    position = ElementPosition(140f, 320f),
                    isAnimated = true,
                    animationType = AnimationType.FADE
                ),
                ThemeElement(
                    type = ElementType.LEAF,
                    position = ElementPosition(70f, 240f),
                    isAnimated = true,
                    animationType = AnimationType.PULSE
                ),
                ThemeElement(
                    type = ElementType.ROOT,
                    position = ElementPosition(200f, 500f),
                    isAnimated = false
                )
            ),
            isAnimated = true,
            isPremium = false
        )
    }
    
    /**
     * Get all available themes
     */
    fun getAllThemes(): List<AITheme> {
        return listOf(
            createCyberRobotTheme(),
            createDigitalBrainTheme(),
            createQuantumAITheme(),
            createNeuralForestTheme()
        )
    }
    
    /**
     * Get theme by ID
     */
    fun getThemeById(id: String): AITheme? {
        return getAllThemes().find { it.id == id }
    }
    
    /**
     * Get themes by category
     */
    fun getThemesByCategory(category: ThemeCategory): List<AITheme> {
        return getAllThemes().filter { it.category == category }
    }
    
    /**
     * Get free themes only
     */
    fun getFreeThemes(): List<AITheme> {
        return getAllThemes().filter { !it.isPremium }
    }
    
    /**
     * Get premium themes only
     */
    fun getPremiumThemes(): List<AITheme> {
        return getAllThemes().filter { it.isPremium }
    }
}