package com.playstudio.aiteacher

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Base64
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.delay
import com.playstudio.aiteacher.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class ComputerUseManager(
    private val activity: Activity,
    private val onUpdate: (message: String) -> Unit // Callback for UI updates
) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val responsesUrl = "https://api.openai.com/v1/responses"
    private var overlayView: View? = null
    private val windowManager by lazy {
        activity.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    var accessibilityService: MyAccessibilityService? = null
    private var currentResponseId: String? = null

    suspend fun startComputerUseSession(prompt: String): String = withContext(Dispatchers.IO) {
        val displayMetrics = activity.resources.displayMetrics
        val initialScreenshot = captureScreenshot()

        val bodyJson = JSONObject().apply {
            put("model", "computer-use-preview")
            put("tools", JSONArray().apply {
                put(JSONObject().apply {
                    put("type", "computer_use_preview")
                    put("display_width", displayMetrics.widthPixels)
                    put("display_height", displayMetrics.heightPixels)
                    put("environment", "browser")
                })
            })
            put("input", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", JSONArray().apply {
                        put(JSONObject().apply {
                            put("type", "input_text")
                            put("text", prompt)
                        })
                        put(JSONObject().apply {
                            put("type", "input_image")
                            put("image_url", "data:image/png;base64,$initialScreenshot")
                        })
                    })
                })
            })
            put("reasoning", JSONObject().apply {
                put("summary", "concise")
            })
            put("truncation", "auto")
        }

        val request = Request.Builder()
            .url(responsesUrl)
            .post(bodyJson.toString().toRequestBody("application/json".toMediaTypeOrNull()))
            .addHeader("Authorization", "Bearer ${BuildConfig.API_KEY}")
            .addHeader("Content-Type", "application/json")
            .build()

        try {
            val response = client.newCall(request).execute()
            val body = response.body?.string()
            Log.d("ComputerUseManager", "Initial API Response: $body")

            if (!response.isSuccessful || body == null) {
                val errorMsg = "API Error ${response.code}: ${body ?: "Unknown error"}"
                withContext(Dispatchers.Main) { onUpdate(errorMsg) }
                return@withContext errorMsg
            }

            val responseJson = JSONObject(body)
            currentResponseId = responseJson.optString("id")
            return@withContext executeComputerUseLoop(responseJson)

        } catch (e: Exception) {
            Log.e("ComputerUseManager", "startComputerUseSession failed: ${e.message}", e)
            val errorMsg = "Error starting session: ${e.localizedMessage}"
            withContext(Dispatchers.Main) { onUpdate(errorMsg) }
            return@withContext errorMsg
        }
    }

    private suspend fun executeComputerUseLoop(initialResponse: JSONObject): String {
        var currentResponse = initialResponse
        val maxIterations = 10
        var iteration = 0
        val resultsSummary = mutableListOf<String>()
        var anActionOrMessageOccurredInThisIteration: Boolean = false // <<< CORRECT INITIALIZATION

        var lastAssistantMessageTextInLoop: String? = null

        while (iteration < maxIterations) {
            iteration++
            anActionOrMessageOccurredInThisIteration = false // Re-initialize for each iteration
            lastAssistantMessageTextInLoop = null

            Log.d("ComputerUseManager", "Computer Use Loop Iteration: $iteration, Response ID: ${currentResponse.optString("id")}")

            val outputArray = currentResponse.optJSONArray("output")
            var computerCallFromOutput: JSONObject? = null
            var reasoningText = ""

            outputArray?.let { outputs ->
                for (i in 0 until outputs.length()) {
                    val item = outputs.getJSONObject(i)
                    when (item.optString("type")) {
                        "computer_call" -> {
                            computerCallFromOutput = item
                            anActionOrMessageOccurredInThisIteration = true
                        }
                        "reasoning" -> {
                            val summaryArray = item.optJSONArray("summary")
                            if (summaryArray != null && summaryArray.length() > 0) {
                                val summaryItem = summaryArray.getJSONObject(0)
                                reasoningText = summaryItem.optString("text", "")
                                if (reasoningText.isNotEmpty()) anActionOrMessageOccurredInThisIteration = true
                            }
                        }
                        "message" -> {
                            if (item.optString("role") == "assistant") {
                                val contentArray = item.optJSONArray("content")
                                if (contentArray != null && contentArray.length() > 0) {
                                    val firstContentItem = contentArray.optJSONObject(0)
                                    if (firstContentItem != null && firstContentItem.optString("type") == "output_text") {
                                        lastAssistantMessageTextInLoop = firstContentItem.optString("text")
                                        if (lastAssistantMessageTextInLoop?.isNotEmpty() == true) anActionOrMessageOccurredInThisIteration = true
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (reasoningText.isNotEmpty()) {
                val msg = "AI Reasoning: $reasoningText"
                resultsSummary.add(msg)
                withContext(Dispatchers.Main) { onUpdate(msg) }
            }

            lastAssistantMessageTextInLoop?.let { text ->
                if (text.isNotEmpty()) {
                    resultsSummary.add(text)
                    withContext(Dispatchers.Main) { onUpdate(text) }
                }
            }

            val currentComputerCall = computerCallFromOutput

            if (currentComputerCall == null) {
                if (!anActionOrMessageOccurredInThisIteration && iteration > 1) {
                    val msg = "No further actions from AI."
                    resultsSummary.add(msg)
                }
                Log.d("ComputerUseManager", "No computer_call found or no relevant output in iteration $iteration, ending loop.")
                break
            }

            val pendingSafetyChecks = currentComputerCall.optJSONArray("pending_safety_checks")
            if (pendingSafetyChecks != null && pendingSafetyChecks.length() > 0) {
                val safetyCheck = pendingSafetyChecks.getJSONObject(0)
                val message = safetyCheck.optString("message")
                val msg = "⚠️ Safety Check: $message"
                resultsSummary.add(msg)
                withContext(Dispatchers.Main) { onUpdate(msg) }
            }

            val action = currentComputerCall.optJSONObject("action")
            val callId = currentComputerCall.optString("call_id")

            if (action != null && callId != null && callId.isNotEmpty()) {
                val actionDescription = action.optString("type", "unknown_action")
                val actionExecutionMessage = "AI is performing action: $actionDescription..."
                withContext(Dispatchers.Main) { onUpdate(actionExecutionMessage) }

                val actionResult = executeAction(action)
                val msg = "Action result ($actionDescription): $actionResult"
                resultsSummary.add(msg)
                withContext(Dispatchers.Main) { onUpdate(msg) }

                delay(1000)
                val screenshot = captureScreenshot()
                if (screenshot.isEmpty()) {
                    val errorMsg = "Failed to capture screenshot after action."
                    resultsSummary.add(errorMsg)
                    withContext(Dispatchers.Main) { onUpdate(errorMsg) }
                    break
                }

                val nextResponse = sendScreenshotResponse(callId, screenshot, pendingSafetyChecks)
                if (nextResponse == null) {
                    val errorMsg = "Failed to get response after sending screenshot."
                    resultsSummary.add(errorMsg)
                    withContext(Dispatchers.Main) { onUpdate(errorMsg) }
                    break
                }
                currentResponse = nextResponse
            } else {
                val msg = "No valid action or call_id found in computer_call for iteration $iteration."
                resultsSummary.add(msg)
                withContext(Dispatchers.Main) { onUpdate(msg) }
                break
            }
        }

        // Determine the final message
        val finalMessageText: String = if (iteration >= maxIterations && anActionOrMessageOccurredInThisIteration) {
            "Maximum iterations reached."
        } else if (resultsSummary.isNotEmpty() && lastAssistantMessageTextInLoop == null && anActionOrMessageOccurredInThisIteration && iteration < maxIterations) {
            // Only add "session ended" if it didn't end due to max iterations AND an action occurred AND no final AI text
            "Computer use session ended."
        } else {
            "" // Default to no generic final message
        }


        if (finalMessageText.isNotEmpty()) {
            val lastActualMessageSentToUi = resultsSummary.lastOrNull {
                !it.startsWith("AI Reasoning:") &&
                        !it.startsWith("Action result") &&
                        !it.startsWith("⚠️ Safety Check:")
            }
            // Only send the generic final message if it's not redundant with what was already sent
            if (lastActualMessageSentToUi == null ||
                (!lastActualMessageSentToUi.contains(finalMessageText, ignoreCase = true) &&
                        (lastAssistantMessageTextInLoop == null || !lastActualMessageSentToUi.contains(lastAssistantMessageTextInLoop!!, ignoreCase = true)))
            ) {
                withContext(Dispatchers.Main) { onUpdate(finalMessageText) }
                resultsSummary.add(finalMessageText)
            }
        }
        return resultsSummary.joinToString("\n")
    }

    // ... (The rest of the file: sendScreenshotResponse, executeAction, captureScreenshot, performClick, etc. is identical to your last provided correct version)
    private suspend fun sendScreenshotResponse(
        callId: String,
        screenshotBase64: String,
        pendingSafetyChecks: JSONArray?
    ): JSONObject? = withContext(Dispatchers.IO) {
        val displayMetrics = activity.resources.displayMetrics
        val bodyJson = JSONObject().apply {
            put("model", "computer-use-preview")
            currentResponseId?.let { put("previous_response_id", it) }
            put("tools", JSONArray().apply {
                put(JSONObject().apply {
                    put("type", "computer_use_preview")
                    put("display_width", displayMetrics.widthPixels)
                    put("display_height", displayMetrics.heightPixels)
                    put("environment", "browser") // Ensure environment is browser
                })
            })
            put("input", JSONArray().apply {
                put(JSONObject().apply {
                    put("call_id", callId)
                    put("type", "computer_call_output")
                    put("acknowledged_safety_checks", pendingSafetyChecks ?: JSONArray()) // Always include, even if empty
                    put("output", JSONObject().apply {
                        put("type", "input_image")
                        put("image_url", "data:image/png;base64,$screenshotBase64")
                    })
                })
            })
            put("truncation", "auto")
        }

        val request = Request.Builder()
            .url(responsesUrl)
            .post(bodyJson.toString().toRequestBody("application/json".toMediaTypeOrNull()))
            .addHeader("Authorization", "Bearer ${BuildConfig.API_KEY}")
            .addHeader("Content-Type", "application/json")
            .build()

        try {
            val response = client.newCall(request).execute()
            val body = response.body?.string()
            Log.d("ComputerUseManager", "Screenshot Response: $body")

            if (!response.isSuccessful || body == null) {
                Log.e("ComputerUseManager", "sendScreenshotResponse failed with code ${response.code}: $body")
                return@withContext null
            }
            val responseJson = JSONObject(body)
            currentResponseId = responseJson.optString("id")
            return@withContext responseJson
        } catch (e: Exception) {
            Log.e("ComputerUseManager", "Exception in sendScreenshotResponse: ${e.message}", e)
            return@withContext null
        }
    }

    private suspend fun executeAction(action: JSONObject): String {
        val actionType = action.optString("type")
        return try {
            when (actionType) {
                "click" -> {
                    val x = action.optInt("x")
                    val y = action.optInt("y")
                    performClick(x, y)
                    "Clicked at ($x, $y)"
                }
                "scroll" -> {
                    val x = action.optInt("x")
                    val y = action.optInt("y")
                    val scrollX = action.optInt("scroll_x", 0)
                    val scrollY = action.optInt("scroll_y", 0)
                    performScroll(x, y, scrollX, scrollY)
                    "Scrolled at ($x, $y) by ($scrollX, $scrollY)"
                }
                "keypress" -> {
                    val keys = action.optJSONArray("keys")
                    val keyStrings = mutableListOf<String>()
                    if (keys != null && keys.length() > 0) {
                        for (i in 0 until keys.length()) {
                            val key = keys.getString(i)
                            performKeyPress(key)
                            keyStrings.add(key)
                        }
                        "Pressed keys: ${keyStrings.joinToString(", ")}"
                    } else { "No keys specified for keypress" }
                }
                "type" -> {
                    val text = action.optString("text")
                    performType(text)
                    "Typed: $text"
                }
                "wait" -> {
                    val durationMs = action.optLong("duration_ms", 2000) // Check if API provides duration
                    delay(durationMs)
                    "Waited ${durationMs}ms"
                }
                "screenshot" -> "Screenshot action noted (system captures implicitly)"
                else -> "Unknown action: $actionType"
            }
        } catch (e: Exception) {
            Log.e("ComputerUseManager", "Error executing action $actionType: ${e.message}", e)
            "Error executing $actionType: ${e.localizedMessage}"
        }
    }

    fun captureScreenshot(): String {
        try {
            val rootView: View = activity.window.decorView.rootView
            if (rootView.width == 0 || rootView.height == 0) {
                Log.e("ComputerUseManager", "Cannot capture screenshot, root view has no dimensions or is not yet drawn.")
                return ""
            }
            val bitmap = Bitmap.createBitmap(rootView.width, rootView.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            rootView.draw(canvas)
            val output = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 90, output)
            return Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e("ComputerUseManager", "Failed to capture screenshot: ${e.message}", e)
            return ""
        }
    }

    private suspend fun performClick(x: Int, y: Int) {
        accessibilityService?.let { service ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
                val gesture = GestureDescription.Builder()
                    .addStroke(GestureDescription.StrokeDescription(path, 0, 100))
                    .build()
                withContext(Dispatchers.Main) {
                    service.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
                        override fun onCompleted(gestureDescription: GestureDescription?) {
                            super.onCompleted(gestureDescription)
                            Log.d("ComputerUseManager", "Click gesture completed at ($x, $y)")
                        }
                        override fun onCancelled(gestureDescription: GestureDescription?) {
                            super.onCancelled(gestureDescription)
                            Log.e("ComputerUseManager", "Click gesture cancelled at ($x, $y)")
                        }
                    }, null)
                }
                delay(300)
            } else {
                Log.w("ComputerUseManager", "Gesture dispatch not available below Android N for click. Showing indicator.")
                showClickIndicator(x, y)
            }
        } ?: run {
            Log.w("ComputerUseManager", "AccessibilityService not available for click. Showing indicator.")
            showClickIndicator(x, y)
        }
    }

    private suspend fun performScroll(x: Int, y: Int, scrollX: Int, scrollY: Int) {
        accessibilityService?.let { service ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val path = Path().apply {
                    moveTo(x.toFloat(), y.toFloat())
                    lineTo((x + scrollX).toFloat(), (y + scrollY).toFloat())
                }
                val gesture = GestureDescription.Builder()
                    .addStroke(GestureDescription.StrokeDescription(path, 10, 300))
                    .build()
                withContext(Dispatchers.Main) {
                    service.dispatchGesture(gesture, null, null)
                }
                delay(400)
            } else {
                Log.w("ComputerUseManager", "Gesture dispatch not available below Android N for scroll.")
            }
        } ?: Log.w("ComputerUseManager", "AccessibilityService not available for scroll.")
    }

    private suspend fun performKeyPress(key: String) {
        accessibilityService?.let { service ->
            var actionPerformed = true
            withContext(Dispatchers.Main) {
                when (key.lowercase()) {
                    "enter" -> service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                    "back", "escape" -> service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                    "home" -> service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
                    "recent", "recents" -> service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
                    else -> {
                        Log.d("ComputerUseManager", "Key press not implemented for global action: $key")
                        actionPerformed = false
                    }
                }
            }
            if(actionPerformed) delay(200)
        } ?: Log.w("ComputerUseManager", "AccessibilityService not available for key press.")
    }

    private suspend fun performType(text: String) {
        accessibilityService?.let { service ->
            val rootNode = withContext(Dispatchers.Main) { service.rootInActiveWindow }
            val focusedNode = rootNode?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)

            if (focusedNode != null && (focusedNode.isEditable || focusedNode.className == "android.widget.EditText")) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    val arguments = android.os.Bundle()
                    arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
                    withContext(Dispatchers.Main) {
                        val success = focusedNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
                        if (success) Log.d("ComputerUseManager", "Typed '$text' into focused EditText.")
                        else Log.w("ComputerUseManager", "Failed to type '$text' into focused EditText.")
                    }
                    delay(300)
                } else {
                    Log.w("ComputerUseManager", "Set text action not available below Lollipop.")
                }
            } else {
                Log.w("ComputerUseManager", "No focused editable field found to type into. Text: '$text'")
            }
            focusedNode?.recycle()
            rootNode?.recycle()
        } ?: Log.w("ComputerUseManager", "AccessibilityService not available for typing.")
    }

    private suspend fun showClickIndicator(x: Int, y: Int) = withContext(Dispatchers.Main) {
        if (!Settings.canDrawOverlays(activity)) {
            Log.w("ComputerUseManager", "Cannot draw overlays. Requesting permission.")
            try {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${activity.packageName}"))
                activity.startActivity(intent)
                onUpdate("Overlay permission needed to show click indicators.")
            } catch (e: Exception) {
                Log.e("ComputerUseManager", "Could not start ACTION_MANAGE_OVERLAY_PERMISSION", e)
                onUpdate("Error requesting overlay permission.")
            }
            return@withContext
        }

        try {
            overlayView?.let { if (it.isAttachedToWindow) windowManager.removeView(it) }

            val indicator = View(activity).apply {
                setBackgroundColor(0x80FF0000.toInt())
            }
            val layoutParams = WindowManager.LayoutParams(
                50, 50,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                },
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                this.x = x - 25
                this.y = y - 25
            }
            windowManager.addView(indicator, layoutParams)
            overlayView = indicator
            delay(1500)
            overlayView?.let { if (it.isAttachedToWindow) windowManager.removeView(it) }
            overlayView = null
        } catch (e: Exception) {
            Log.e("ComputerUseManager", "Failed to show click indicator: ${e.message}", e)
        }
    }

    fun cleanup() {
        overlayView?.let {
            try {
                if (it.isAttachedToWindow) windowManager.removeView(it) else TODO()
            } catch (e: Exception) {
                Log.e("ComputerUseManager", "Error removing overlay during cleanup: ${e.message}")
            }
        }
        overlayView = null
        currentResponseId = null
    }
}
