package com.playstudio.aiteacher

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.app.NotificationCompat

class EmailAccessibilityService : AccessibilityService() {

    private val emailPackages = setOf(
        "com.google.android.gm",
        "com.microsoft.office.outlook",
        "com.samsung.android.email.provider",
        "com.android.email",
        "com.yahoo.mobile.client.android.mail"
    )

    private val emailContent = StringBuilder()

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "EmailAccessibilityService connected")
        registerApprovalReceiver()
        serviceInfo = serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                    AccessibilityEvent.TYPE_VIEW_SCROLLED or
                    AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 100
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val packageName = event.packageName?.toString() ?: return
        if (!emailPackages.contains(packageName)) return

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        ) {
            processEmailContent()
        }
    }

    private fun processEmailContent() {
        val root = rootInActiveWindow ?: return
        emailContent.clear()
        extractText(root)
        root.recycle()
        val content = emailContent.toString().trim()
        if (content.isNotEmpty() && content.length > 50) {
            Log.d(TAG, "Email content extracted: ${'$'}{content.take(100)}")
            showNotification(content)
        }
    }

    private fun extractText(node: AccessibilityNodeInfo?) {
        if (node == null) return
        node.text?.let {
            val text = it.toString().trim()
            if (text.isNotEmpty()) emailContent.append(text).append(' ')
        }
        node.contentDescription?.let {
            val text = it.toString().trim()
            if (text.isNotEmpty()) emailContent.append(text).append(' ')
        }
        for (i in 0 until node.childCount) {
            extractText(node.getChild(i))
        }
    }

    private fun showNotification(content: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Email Extraction",
                NotificationManager.IMPORTANCE_HIGH
            )
            nm.createNotificationChannel(channel)
        }
        val approveIntent = Intent(ACTION_PROCESS_EMAIL).apply {
            putExtra(EXTRA_EMAIL_CONTENT, content)
        }
        val approvePending = PendingIntent.getBroadcast(
            this, 0, approveIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val dismissIntent = Intent(ACTION_DISMISS)
        val dismissPending = PendingIntent.getBroadcast(
            this, 1, dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_email)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.email_extracted))
            .setStyle(NotificationCompat.BigTextStyle().bigText(content.take(200)))
            .addAction(R.drawable.ic_ai, getString(R.string.action_process_email), approvePending)
            .addAction(R.drawable.ic_dismiss, getString(R.string.dismiss), dismissPending)
            .setAutoCancel(true)
            .build()
        nm.notify(NOTIFICATION_ID, notification)
    }

    private fun registerApprovalReceiver() {
        val filter = IntentFilter().apply {
            addAction(ACTION_PROCESS_EMAIL)
            addAction(ACTION_DISMISS)
        }
        approvalReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    ACTION_PROCESS_EMAIL -> {
                        val text = intent.getStringExtra(EXTRA_EMAIL_CONTENT) ?: return
                        // In a real implementation, send text to AI.
                        Log.d(TAG, "User approved AI processing: ${'$'}{text.take(100)}")
                    }
                    ACTION_DISMISS -> {
                        Log.d(TAG, "User dismissed email extraction")
                    }
                }
            }
        }
        registerReceiver(approvalReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(approvalReceiver)
        } catch (e: IllegalArgumentException) {
            // Receiver wasn't registered
        }
        super.onDestroy()
    }

    private lateinit var approvalReceiver: BroadcastReceiver

    override fun onInterrupt() {
        Log.d(TAG, "EmailAccessibilityService interrupted")
    }

    companion object {
        private const val TAG = "EmailAccessibilitySvc"
        private const val CHANNEL_ID = "EMAIL_EXTRACTION_CHANNEL"
        private const val NOTIFICATION_ID = 1001
        private const val ACTION_PROCESS_EMAIL = "PROCESS_EMAIL_WITH_AI"
        private const val ACTION_DISMISS = "DISMISS_EMAIL_EXTRACTION"
        private const val EXTRA_EMAIL_CONTENT = "email_content"
    }
}
