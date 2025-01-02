package com.playstudio.aiteacher

import android.content.res.Configuration
import androidx.multidex.MultiDexApplication
import java.util.Locale

class MyApplication : MultiDexApplication() {
    override fun onCreate() {
        super.onCreate()
        // Set default font globally
        val fontResId = FontManager.getFont(this) // Gets the selected font from SharedPreferences
        ReplaceFonts.overrideFont(this, "SERIF", fontResId)

        // Set the default locale when the app starts
        setLocale(Locale.getDefault().language)
    }

    fun setLocale(languageCode: String) {
        val locale = Locale(languageCode)
        Locale.setDefault(locale)
        val config = Configuration()
        config.setLocale(locale)
        resources.updateConfiguration(config, resources.displayMetrics)
    }
}