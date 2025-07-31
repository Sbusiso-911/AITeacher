package com.playstudio.aiteacher.models

import com.google.gson.annotations.SerializedName

/**
 * Structured response models for AI Teacher educational content
 * Based on OpenAI Structured Outputs for consistent, reliable formatting
 */

data class EducationalResponse(
    @SerializedName("response_type")
    val responseType: ResponseType,
    
    @SerializedName("subject")
    val subject: String,
    
    @SerializedName("difficulty_level")
    val difficultyLevel: DifficultyLevel,
    
    @SerializedName("content")
    val content: StructuredContent,
    
    @SerializedName("metadata")
    val metadata: ResponseMetadata,
    
    @SerializedName("interactive_elements")
    val interactiveElements: List<InteractiveElement>? = null
)

data class StructuredContent(
    @SerializedName("main_explanation")
    val mainExplanation: String,
    
    @SerializedName("steps")
    val steps: List<LearningStep>? = null,
    
    @SerializedName("examples")
    val examples: List<Example>? = null,
    
    @SerializedName("key_concepts")
    val keyConcepts: List<String>? = null,
    
    @SerializedName("practice_questions")
    val practiceQuestions: List<Question>? = null,
    
    @SerializedName("code_snippets")
    val codeSnippets: List<CodeSnippet>? = null,
    
    @SerializedName("formulas")
    val formulas: List<Formula>? = null
)


data class LearningStep(
    @SerializedName("step_number")
    val stepNumber: Int,
    
    @SerializedName("title")
    val title: String,
    
    @SerializedName("explanation")
    val explanation: String,
    
    @SerializedName("output")
    val output: String? = null,
    
    @SerializedName("visual_aid")
    val visualAid: String? = null,
    
    @SerializedName("hints")
    val hints: List<String>? = null
)


data class Example(
    @SerializedName("title")
    val title: String,
    
    @SerializedName("description")
    val description: String,
    
    @SerializedName("input")
    val input: String? = null,
    
    @SerializedName("solution")
    val solution: String,
    
    @SerializedName("explanation")
    val explanation: String? = null
)


data class Question(
    @SerializedName("id")
    val id: String,
    
    @SerializedName("question_type")
    val questionType: QuestionType,
    
    @SerializedName("question_text")
    val questionText: String,
    
    @SerializedName("options")
    val options: List<String>? = null,
    
    @SerializedName("correct_answer")
    val correctAnswer: String,
    
    @SerializedName("explanation")
    val explanation: String,
    
    @SerializedName("difficulty")
    val difficulty: DifficultyLevel,
    
    @SerializedName("points")
    val points: Int = 1
)


data class CodeSnippet(
    @SerializedName("language")
    val language: String,
    
    @SerializedName("title")
    val title: String,
    
    @SerializedName("code")
    val code: String,
    
    @SerializedName("explanation")
    val explanation: String,
    
    @SerializedName("output")
    val output: String? = null
)


data class Formula(
    @SerializedName("name")
    val name: String,
    
    @SerializedName("latex")
    val latex: String,
    
    @SerializedName("plain_text")
    val plainText: String,
    
    @SerializedName("description")
    val description: String,
    
    @SerializedName("variables")
    val variables: Map<String, String>? = null
)


data class InteractiveElement(
    @SerializedName("element_type")
    val elementType: InteractiveElementType,
    
    @SerializedName("title")
    val title: String,
    
    @SerializedName("description")
    val description: String,
    
    @SerializedName("data")
    val data: String // JSON string containing element-specific data
)


data class ResponseMetadata(
    @SerializedName("estimated_reading_time")
    val estimatedReadingTime: Int, // in minutes
    
    @SerializedName("prerequisites")
    val prerequisites: List<String>? = null,
    
    @SerializedName("next_topics")
    val nextTopics: List<String>? = null,
    
    @SerializedName("learning_objectives")
    val learningObjectives: List<String>? = null,
    
    @SerializedName("tags")
    val tags: List<String>? = null,
    
    @SerializedName("confidence_score")
    val confidenceScore: Float? = null
)

// Enums for structured responses
enum class ResponseType {
    @SerializedName("explanation")
    EXPLANATION,
    
    @SerializedName("step_by_step")
    STEP_BY_STEP,
    
    @SerializedName("quiz")
    QUIZ,
    
    @SerializedName("lesson")
    LESSON,
    
    @SerializedName("practice")
    PRACTICE,
    
    @SerializedName("summary")
    SUMMARY,
    
    @SerializedName("code_tutorial")
    CODE_TUTORIAL
}

enum class DifficultyLevel {
    @SerializedName("beginner")
    BEGINNER,
    
    @SerializedName("intermediate")
    INTERMEDIATE,
    
    @SerializedName("advanced")
    ADVANCED,
    
    @SerializedName("expert")
    EXPERT
}

enum class QuestionType {
    @SerializedName("multiple_choice")
    MULTIPLE_CHOICE,
    
    @SerializedName("true_false")
    TRUE_FALSE,
    
    @SerializedName("short_answer")
    SHORT_ANSWER,
    
    @SerializedName("fill_blank")
    FILL_BLANK,
    
    @SerializedName("code_completion")
    CODE_COMPLETION
}

enum class InteractiveElementType {
    @SerializedName("progress_tracker")
    PROGRESS_TRACKER,
    
    @SerializedName("interactive_diagram")
    INTERACTIVE_DIAGRAM,
    
    @SerializedName("code_playground")
    CODE_PLAYGROUND,
    
    @SerializedName("formula_renderer")
    FORMULA_RENDERER,
    
    @SerializedName("quiz_widget")
    QUIZ_WIDGET
}