package com.playstudio.aiteacher

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Typeface
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat

object FontManager {
    private const val PREFS_NAME = "font_prefs"
    private const val KEY_SELECTED_FONT = "selected_font"

    val FONT1 = R.font.font3
    val FONT2 = R.font.font1
    val FONT3 = R.font.font2
    val FONT4 = R.font.font4
    val FONT5 = R.font.font5

    private val DEFAULT_FONT = FONT4
    private val TAG = "FontManager"

    fun setFont(context: Context, fontResId: Int) {
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        editor.putInt(KEY_SELECTED_FONT, fontResId)
        editor.apply()
    }

    fun getFont(context: Context): Int {
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        // Ensure we return a valid default if no preference is set
        return prefs.getInt(KEY_SELECTED_FONT, DEFAULT_FONT)
    }

    fun applyFontToView(context: Context, view: View) {
        val selectedFont = getFont(context)
        try {
            val typeface = ResourcesCompat.getFont(context, selectedFont)
            if (typeface != null) {
                applyTypefaceToView(view, typeface)
            } else {
                Log.e(TAG, "Error loading font resource ID $selectedFont, falling back to default")
                // Fallback to the default font if the selected font cannot be loaded
                applyDefaultFontToView(context, view)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error applying font resource ID $selectedFont: ${e.message}")
            // In case of an exception, fallback to the default font
            applyDefaultFontToView(context, view)
        }
    }

    private fun applyDefaultFontToView(context: Context, view: View) {
        val defaultTypeface = ResourcesCompat.getFont(context, DEFAULT_FONT)
        if (defaultTypeface != null) {
            applyTypefaceToView(view, defaultTypeface)
        } else {
            Log.e(TAG, "Error loading default font resource ID $DEFAULT_FONT")
        }
    }

    private fun applyTypefaceToView(view: View, typeface: Typeface) {
        if (view is TextView) {
            view.typeface = typeface
        } else if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                applyTypefaceToView(view.getChildAt(i), typeface)
            }
        }
    }

    fun applySelectedFont(context: Context) {
        if (context is AppCompatActivity) {
            val rootView = context.findViewById<View>(android.R.id.content)
            applyFontToView(context, rootView)
        } else {
            throw IllegalArgumentException("Context must be of type AppCompatActivity")
        }
    }
}