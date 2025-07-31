package com.playstudio.aiteacher

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.Worker
import androidx.work.WorkerParameters

class ReminderWorker(context: Context, workerParams: WorkerParameters) : Worker(context, workerParams) {

    override fun doWork(): Result {
        Log.d("ReminderWorker", "ReminderWorker executed")
        showReminderNotification()
        return Result.success()
    }

    private fun showReminderNotification() {
        Log.d("ReminderWorker", "Attempting to show notification")
        val notificationManager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "daily_reminder_channel",
                "Daily Reminder",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Channel for daily reminder notifications"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val notificationIntent = Intent(applicationContext, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, "daily_reminder_channel")
            .setContentTitle("Reminder")
            .setContentText("You haven't used the app in the last 24 hours. Come back and continue learning!")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(1, notification)
        Log.d("ReminderWorker", "Reminder notification shown")
    }
}