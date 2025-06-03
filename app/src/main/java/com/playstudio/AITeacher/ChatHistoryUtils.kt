package com.playstudio.aiteacher.utils

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.playstudio.aiteacher.ChatMessage

/** Utility object for persisting and retrieving chat conversations. */
object ChatHistoryUtils {

    private const val PREFS_NAME = "ChatPrefs"
    private const val CHAT_HISTORY_KEY = "chatHistory"

    private val gson = Gson()

    /** Model representing a stored conversation. */
    data class Conversation(
        val id: String,
        val title: String,
        val messages: List<ChatMessage>
    )

    /** Save or update a conversation. */
    fun saveConversation(context: Context, conversation: Conversation) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = prefs.getString(CHAT_HISTORY_KEY, "[]")
        val type = object : TypeToken<MutableList<Conversation>>() {}.type
        val conversations: MutableList<Conversation> = gson.fromJson(current, type) ?: mutableListOf()

        val index = conversations.indexOfFirst { it.id == conversation.id }
        if (index >= 0) {
            conversations[index] = conversation
        } else {
            conversations.add(conversation)
        }

        prefs.edit().putString(CHAT_HISTORY_KEY, gson.toJson(conversations)).apply()
    }

    /** Retrieve a conversation by its id. */
    fun getConversation(context: Context, id: String): Conversation? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = prefs.getString(CHAT_HISTORY_KEY, "[]")
        val type = object : TypeToken<List<Conversation>>() {}.type
        val conversations: List<Conversation> = gson.fromJson(current, type) ?: return null
        return conversations.firstOrNull { it.id == id }
    }

    /** Retrieve all saved conversations. */
    fun getAllConversations(context: Context): List<Conversation> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = prefs.getString(CHAT_HISTORY_KEY, "[]")
        val type = object : TypeToken<List<Conversation>>() {}.type
        return gson.fromJson(current, type) ?: emptyList()
    }

    /** Delete a conversation by id. */
    fun deleteConversation(context: Context, id: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = prefs.getString(CHAT_HISTORY_KEY, "[]")
        val type = object : TypeToken<MutableList<Conversation>>() {}.type
        val conversations: MutableList<Conversation> = gson.fromJson(current, type) ?: mutableListOf()
        val updated = conversations.filter { it.id != id }
        prefs.edit().putString(CHAT_HISTORY_KEY, gson.toJson(updated)).apply()
    }

    /** Remove all stored conversations. */
    fun deleteAllConversations(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(CHAT_HISTORY_KEY, "[]").apply()
    }
}