package com.playstudio.aiteacher.models

// NEW: Streaming learning content updates for real-time UX
sealed class StreamingLearningUpdate {
    data class Progress(val message: String) : StreamingLearningUpdate()
    data class ContentUpdate(val partialContent: String) : StreamingLearningUpdate()
    data class Complete(val learningContent: LearningContent) : StreamingLearningUpdate()
    data class Error(val message: String) : StreamingLearningUpdate()
}