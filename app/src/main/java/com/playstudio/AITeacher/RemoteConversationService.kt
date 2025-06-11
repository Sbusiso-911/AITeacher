package com.playstudio.aiteacher

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject

class RemoteConversationService(private val baseUrl: String = "http://10.0.2.2:3000") {

    private val client = OkHttpClient()

    fun fetchConversation(conversationId: String): List<ChatMessage> {
        val request = Request.Builder()
            .url("$baseUrl/conversations/$conversationId")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            val body = response.body?.string() ?: return emptyList()
            val jsonArray = JSONArray(body)
            val result = mutableListOf<ChatMessage>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                result.add(
                    ChatMessage(
                        id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                        content = obj.getString("content"),
                        isUser = obj.optBoolean("isUser", true)
                    )
                )
            }
            return result
        }
    }

    fun appendMessage(conversationId: String, message: ChatMessage) {
        val json = JSONObject().apply {
            put("id", message.id)
            put("content", message.content)
            put("isUser", message.isUser)
        }
        val requestBody: RequestBody = json.toString().toRequestBody("application/json".toMediaTypeOrNull())
        val request = Request.Builder()
            .url("$baseUrl/conversations/$conversationId/messages")
            .post(requestBody)
            .build()
        client.newCall(request).execute().use { response ->
            // ignore result
        }
    }
}
