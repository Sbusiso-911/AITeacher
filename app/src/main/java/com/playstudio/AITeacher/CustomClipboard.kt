package com.playstudio.aiteacher

object CustomClipboard {
    private var copiedText: String? = null

    fun copy(text: String) {
        copiedText = text
    }

    fun paste(): String? {
        return copiedText
    }
}