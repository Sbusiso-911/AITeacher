package com.playstudio.aiteacher

import android.content.Context
import android.graphics.Typeface
import android.os.Build
import androidx.core.content.res.ResourcesCompat
import java.lang.reflect.Field

object ReplaceFonts {

    fun overrideFont(context: Context, staticTypefaceFieldName: String, fontRes: Int) {
        val customFont: Typeface? = ResourcesCompat.getFont(context, fontRes)
        replaceFont(staticTypefaceFieldName, customFont)
    }

    private fun replaceFont(staticTypefaceFieldName: String, newTypeface: Typeface?) {
        try {
            val staticField: Field = Typeface::class.java.getDeclaredField(staticTypefaceFieldName)
            staticField.isAccessible = true
            staticField.set(null, newTypeface)
        } catch (e: NoSuchFieldException) {
            e.printStackTrace()
        } catch (e: IllegalAccessException) {
            e.printStackTrace()
        }
    }
}