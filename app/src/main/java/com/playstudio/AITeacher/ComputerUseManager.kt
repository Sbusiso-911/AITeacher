package com.playstudio.aiteacher

import android.app.Activity
import android.app.Instrumentation
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.util.Base64
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
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

class ComputerUseManager(private var activity: Activity? = null) {

    constructor() : this(null)

    fun attachActivity(activity: Activity) {
        this.activity = activity
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val responsesUrl = "https://api.openai.com/v1/responses"

    suspend fun startSession(prompt: String): JSONObject? = withContext(Dispatchers.IO) {
        val bodyJson = JSONObject().apply {
            put("model", "computer-use-preview")
            put("tools", JSONArray().put(JSONObject().apply {
                put("type", "computer_use_preview")
                put("display_width", 1024)
                put("display_height", 768)
                put("environment", "browser")
            }))
            put("input", JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("content", JSONArray().put(JSONObject().apply {
                    put("type", "input_text")
                    put("text", prompt)
                }))
            }))
            put("reasoning", JSONObject().put("summary", "concise"))
            put("truncation", "auto")
        }

        val request = Request.Builder()
            .url(responsesUrl)
            .post(bodyJson.toString().toRequestBody("application/json".toMediaTypeOrNull()))
            .addHeader("Authorization", "Bearer ${BuildConfig.API_KEY}")
            .addHeader("Content-Type", "application/json")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string()
                return@withContext body?.let { JSONObject(it) }
            }
        } catch (e: Exception) {
            Log.e("ComputerUseManager", "startSession failed: ${e.message}")
            null
        }
    }

    suspend fun sendScreenshot(
        previousResponseId: String,
        callId: String,
        screenshotBase64: String,
        acknowledgedChecks: JSONArray? = null
    ): JSONObject? = withContext(Dispatchers.IO) {
        val inputObj = JSONObject().apply {
            put("type", "computer_call_output")
            put("call_id", callId)
            acknowledgedChecks?.let { put("acknowledged_safety_checks", it) }
            put("output", JSONObject().apply {
                put("type", "input_image")
                put("image_url", "data:image/png;base64,$screenshotBase64")
            })
        }

        val bodyJson = JSONObject().apply {
            put("model", "computer-use-preview")
            put("previous_response_id", previousResponseId)
            put("tools", JSONArray().put(JSONObject().apply {
                put("type", "computer_use_preview")
                put("display_width", 1024)
                put("display_height", 768)
                put("environment", "browser")
            }))
            put("input", JSONArray().put(inputObj))
            put("truncation", "auto")
        }

        val request = Request.Builder()
            .url(responsesUrl)
            .post(bodyJson.toString().toRequestBody("application/json".toMediaTypeOrNull()))
            .addHeader("Authorization", "Bearer ${BuildConfig.API_KEY}")
            .addHeader("Content-Type", "application/json")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string()
                return@withContext body?.let { JSONObject(it) }
            }
        } catch (e: Exception) {
            Log.e("ComputerUseManager", "sendScreenshot failed: ${e.message}")
            null
        }
    }

    fun captureScreenshot(): String {
        val act = activity ?: return ""
        val rootView: View = act.window.decorView.rootView
        val bitmap = Bitmap.createBitmap(rootView.width, rootView.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        rootView.draw(canvas)

        val output = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        val bytes = output.toByteArray()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    suspend fun handleModelAction(action: JSONObject) {
        when (action.optString("type")) {
            "click" -> {
                val x = action.optInt("x")
                val y = action.optInt("y")
                performClick(x, y)
            }
            "scroll" -> {
                val x = action.optInt("x")
                val y = action.optInt("y")
                val scrollX = action.optInt("scroll_x")
                val scrollY = action.optInt("scroll_y")
                performScroll(x, y, scrollX, scrollY)
            }
            "keypress" -> {
                val keys = action.optJSONArray("keys")
                if (keys != null) {
                    for (i in 0 until keys.length()) {
                        pressKey(keys.getString(i))
                    }
                }
            }
            "type" -> {
                val text = action.optString("text")
                typeText(text)
            }
            "wait" -> {
                delay(2000)
            }
        }
    }

    private fun performClick(x: Int, y: Int) {
        val inst = Instrumentation()
        val downTime = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x.toFloat(), y.toFloat(), 0)
        val up = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, x.toFloat(), y.toFloat(), 0)
        inst.sendPointerSync(down)
        inst.sendPointerSync(up)
    }

    private fun performScroll(x: Int, y: Int, scrollX: Int, scrollY: Int) {
        val inst = Instrumentation()
        val startX = x
        val startY = y
        val endX = x + scrollX
        val endY = y + scrollY
        val duration = 300
        val downTime = SystemClock.uptimeMillis()
        inst.sendPointerSync(MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, startX.toFloat(), startY.toFloat(), 0))
        val steps = 10
        for (i in 1..steps) {
            val eventTime = downTime + i * duration / steps
            val mx = startX + ((endX - startX) * i / steps)
            val my = startY + ((endY - startY) * i / steps)
            inst.sendPointerSync(MotionEvent.obtain(downTime, eventTime, MotionEvent.ACTION_MOVE, mx.toFloat(), my.toFloat(), 0))
        }
        inst.sendPointerSync(MotionEvent.obtain(downTime, downTime + duration, MotionEvent.ACTION_UP, endX.toFloat(), endY.toFloat(), 0))
    }

    private fun pressKey(key: String) {
        val inst = Instrumentation()
        val keyCode = when (key.uppercase()) {
            "ENTER" -> KeyEvent.KEYCODE_ENTER
            "SPACE" -> KeyEvent.KEYCODE_SPACE
            else -> KeyEvent.keyCodeFromString("KEYCODE_${key.uppercase()}")
        }
        if (keyCode != KeyEvent.KEYCODE_UNKNOWN) {
            inst.sendKeyDownUpSync(keyCode)
        }
    }

    private fun typeText(text: String) {
        val inst = Instrumentation()
        inst.sendStringSync(text)
    }
}

