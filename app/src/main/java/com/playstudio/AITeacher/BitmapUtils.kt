package com.playstudio.aiteacher // ensure this matches your package structure

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream

object BitmapUtils {
    fun bitmapToByteArray(bitmap: Bitmap): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)
        return stream.toByteArray()
    }
}