package com.playstudio.aiteacher

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class MyAccessibilityService : AccessibilityService() {

    companion object {
        var instance: MyAccessibilityService? = null
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this

        val info = AccessibilityServiceInfo().apply {
            // Set the type of events that this service wants to listen to
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED

            // Set the type of feedback your service will provide
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC

            // Default services are invoked only if no package-specific ones are present
            // for the type of AccessibilityEvent generated
            flags = AccessibilityServiceInfo.DEFAULT or
                    AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                    AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE or
                    AccessibilityServiceInfo.FLAG_REQUEST_ENHANCED_WEB_ACCESSIBILITY

            // We want to listen to all applications
            packageNames = null
        }

        serviceInfo = info
        Log.d("AccessibilityService", "Service connected and configured")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Handle accessibility events if needed
        // For computer use, we mainly need the gesture capabilities
    }

    override fun onInterrupt() {
        Log.d("AccessibilityService", "Service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        Log.d("AccessibilityService", "Service destroyed")
    }
}