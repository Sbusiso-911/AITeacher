package com.playstudio.aiteacher

import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceFragmentCompat

class SettingsActivity : AppCompatActivity() {

    private lateinit var themeManager: ThemeManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        Log.d("SettingsActivity", "SettingsActivity created")

        // Initialize theme manager and apply current theme
        themeManager = ThemeManager(this)
        applyCurrentTheme()

        supportFragmentManager
            .beginTransaction()
            .replace(R.id.settings_container, SettingsFragment())
            .commit()

        // Enable the back button in the action bar
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Settings"

        // Apply the current font to this activity
        applyCurrentFont()
    }

    override fun onResume() {
        super.onResume()
        supportActionBar?.title = "Settings"
        // Refresh theme in case it was changed
        if (::themeManager.isInitialized) {
            applyCurrentTheme()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun applyCurrentFont() {
        FontManager.applyFontToView(this, findViewById(android.R.id.content))
    }
    
    private fun applyCurrentTheme() {
        val themeBackgroundView = findViewById<ImageView>(R.id.ai_theme_background)
        themeBackgroundView?.let { view ->
            themeManager.applyThemeToView(view)
        }
    }
}