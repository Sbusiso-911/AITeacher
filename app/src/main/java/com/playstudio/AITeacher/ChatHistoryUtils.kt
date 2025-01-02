package com.playstudio.aiteacher.utils

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object ChatHistoryUtils {

    private const val PREFS_NAME = "ChatPrefs"
    private const val CHAT_HISTORY_KEY = "chatHistory"

    fun saveChatHistory(context: Context, chatHistory: List<String>) {
        val sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        val json = Gson().toJson(chatHistory)
        editor.putString(CHAT_HISTORY_KEY, json)
        editor.apply()
    }

    fun getChatHistory(context: Context): List<String> {
        val sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = sharedPreferences.getString(CHAT_HISTORY_KEY, null)
        return if (json != null) {
            val type = object : TypeToken<List<String>>() {}.type
            Gson().fromJson(json, type)
        } else {
            emptyList()
        }
    }
}