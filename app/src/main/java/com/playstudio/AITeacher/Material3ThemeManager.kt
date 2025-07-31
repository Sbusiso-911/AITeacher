package com.playstudio.AITeacher

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import com.playstudio.aiteacher.R

class Material3ThemeManager(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("material3_theme", Context.MODE_PRIVATE)
    
    companion object {
        private const val THEME_KEY = "theme_mode"
        private const val THEME_LIGHT = "light"
        private const val THEME_DARK = "dark"
        private const val THEME_SYSTEM = "system"
    }
    
    fun applyTheme(themeMode: String = getCurrentTheme()) {
        when (themeMode) {
            THEME_LIGHT -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            }
            THEME_DARK -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            }
            THEME_SYSTEM -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            }
        }
        saveTheme(themeMode)
    }
    
    fun getCurrentTheme(): String {
        return prefs.getString(THEME_KEY, THEME_SYSTEM) ?: THEME_SYSTEM
    }
    
    private fun saveTheme(themeMode: String) {
        prefs.edit().putString(THEME_KEY, themeMode).apply()
    }
    
    fun isDarkMode(): Boolean {
        return when (getCurrentTheme()) {
            THEME_DARK -> true
            THEME_LIGHT -> false
            else -> {
                val nightModeFlags = context.resources.configuration.uiMode and 
                    android.content.res.Configuration.UI_MODE_NIGHT_MASK
                nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES
            }
        }
    }
    
    fun toggleTheme() {
        val currentTheme = getCurrentTheme()
        val newTheme = when (currentTheme) {
            THEME_LIGHT -> THEME_DARK
            THEME_DARK -> THEME_SYSTEM
            else -> THEME_LIGHT
        }
        applyTheme(newTheme)
    }
    
    fun getThemeResources(): ThemeResources {
        return if (isDarkMode()) {
            ThemeResources.Dark
        } else {
            ThemeResources.Light
        }
    }
}

sealed class ThemeResources {
    abstract val messageBubbleUser: Int
    abstract val messageBubbleAI: Int
    abstract val inputField: Int
    abstract val sendButton: Int
    abstract val iconButton: Int
    abstract val appBar: Int
    abstract val chip: Int
    abstract val glassSurface: Int
    
    object Light : ThemeResources() {
        override val messageBubbleUser = R.drawable.bg_message_user_material3
        override val messageBubbleAI = R.drawable.bg_message_ai_material3
        override val inputField = R.drawable.bg_input_field_material3
        override val sendButton = R.drawable.bg_send_button_material3
        override val iconButton = R.drawable.bg_icon_button_material3
        override val appBar = R.drawable.bg_app_bar_material3
        override val chip = R.drawable.bg_chip_material3
        override val glassSurface = R.drawable.bg_glassmorphism_surface
    }
    
    object Dark : ThemeResources() {
        override val messageBubbleUser = R.drawable.bg_message_user_dark_material3
        override val messageBubbleAI = R.drawable.bg_message_ai_dark_material3
        override val inputField = R.drawable.bg_input_field_dark_material3
        override val sendButton = R.drawable.bg_send_button_dark_material3
        override val iconButton = R.drawable.bg_icon_button_dark_material3
        override val appBar = R.drawable.bg_app_bar_dark_material3
        override val chip = R.drawable.bg_chip_dark_material3
        override val glassSurface = R.drawable.bg_glassmorphism_surface_dark
    }
}