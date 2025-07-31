package com.playstudio.aiteacher

import android.content.Context
import android.content.SharedPreferences
import android.view.View

class ThemeManager(private val context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
    
    enum class AITheme(val drawableRes: Int, val themeName: String) {
        HARDWARE_AI(R.drawable.bg_hardware_ai, "Hardware AI"),
        MATHEMATICS(R.drawable.bg_mathematics_new, "Mathematics"),
        PROGRAMMING(R.drawable.bg_programming_new, "Programming"), 
        DATA_SCIENCE(R.drawable.bg_data_science, "Data Science"),
        SCIENCE_TECH(R.drawable.bg_science_tech, "Science & Tech"),
        NEURAL_NETWORK(R.drawable.bg_neural_network, "Neural Network"),
        AI_ROBOT(R.drawable.bg_ai_robot, "AI Robot"),
        AI_BRAIN(R.drawable.bg_ai_brain, "AI Brain"),
        AI_QUANTUM(R.drawable.bg_ai_quantum, "AI Quantum"),
        AI_BIO(R.drawable.bg_ai_bio, "AI Bio"),
        AI_CIRCUIT(R.drawable.bg_ai_circuit, "AI Circuit")
    }
    
    fun getCurrentTheme(): AITheme {
        val savedTheme = prefs.getString("current_theme", AITheme.HARDWARE_AI.name)
        return try {
            AITheme.valueOf(savedTheme ?: AITheme.HARDWARE_AI.name)
        } catch (e: IllegalArgumentException) {
            // If the saved theme doesn't exist, fallback to default and update preferences
            setTheme(AITheme.HARDWARE_AI)
            AITheme.HARDWARE_AI
        }
    }
    
    fun setTheme(theme: AITheme) {
        prefs.edit().putString("current_theme", theme.name).apply()
    }
    
    fun applyThemeToView(view: View) {
        try {
            view.setBackgroundResource(getCurrentTheme().drawableRes)
        } catch (e: Exception) {
            // Fallback to a default background if theme fails to load
            view.setBackgroundColor(view.context.getColor(android.R.color.black))
        }
    }
    
    fun applyThemeToView(view: View, theme: AITheme) {
        try {
            view.setBackgroundResource(theme.drawableRes)
        } catch (e: Exception) {
            // Fallback to a default background if theme fails to load
            view.setBackgroundColor(view.context.getColor(android.R.color.black))
        }
    }
    
    fun getAllThemes(): Array<AITheme> = AITheme.values()
}