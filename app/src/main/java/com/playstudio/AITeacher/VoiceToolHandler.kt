package com.playstudio.aiteacher

interface VoiceToolHandler {
    suspend fun executeTool(functionName: String, argumentsJson: String): String
}
