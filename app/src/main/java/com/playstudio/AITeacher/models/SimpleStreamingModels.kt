package com.playstudio.aiteacher.models

/**
 * Simplified streaming updates for adaptive content rendering
 */
sealed class SimpleStreamingUpdate {
    data class Progress(val message: String) : SimpleStreamingUpdate()
    data class ContentChunk(val accumulatedContent: String) : SimpleStreamingUpdate()
    data class Complete(val finalContent: String) : SimpleStreamingUpdate()
    data class Error(val message: String) : SimpleStreamingUpdate()
}