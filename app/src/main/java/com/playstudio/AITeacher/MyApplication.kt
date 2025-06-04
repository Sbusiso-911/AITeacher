package com.playstudio.aiteacher

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.res.Configuration
import android.os.Build
import android.util.Log
import androidx.multidex.MultiDexApplication
import androidx.work.Configuration as WorkManagerConfiguration
import androidx.work.WorkManager
import com.google.ar.core.Config
import com.playstudio.aiteacher.history.DatabaseProvider
import java.util.Locale

class MyApplication : MultiDexApplication() {

    override fun onCreate() {
        super.onCreate()

        DatabaseProvider.init(this)



        // Rest of your initialization code
        val workManagerConfig = WorkManagerConfiguration.Builder()
            .setMinimumLoggingLevel(android.util.Log.DEBUG)
            .build()
        WorkManager.initialize(this, workManagerConfig)

        val fontResId = FontManager.getFont(this)
        ReplaceFonts.overrideFont(this, "SERIF", fontResId)

        setLocale(Locale.getDefault().language)
        createNotificationChannel()
    }



    fun setLocale(languageCode: String) {
        val locale = Locale(languageCode)
        Locale.setDefault(locale)
        val config = Configuration()
        config.setLocale(locale)
        resources.updateConfiguration(config, resources.displayMetrics)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "daily_reminder_channel",
                "Daily Reminder",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Reminders for the app"
            }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }
}