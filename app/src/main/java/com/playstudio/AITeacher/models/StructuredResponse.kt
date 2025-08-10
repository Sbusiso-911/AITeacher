package com.playstudio.aiteacher.models

import com.google.gson.annotations.SerializedName

/**
 * NEW: Learning-focused response models
 * Redesigned for comprehensive subject teaching like textbooks and experienced teachers
 */

// MAIN: Comprehensive Learning Content Model
data class LearningContent(
    @SerializedName("topic_title")
    val topicTitle: String,
    
    @SerializedName("subject_area")
    val subjectArea: String,
    
    @SerializedName("content_type")
    val contentType: ContentType,
    
    @SerializedName("introduction")
    val introduction: Introduction,
    
    @SerializedName("core_content")
    val coreContent: CoreContent,
    
    @SerializedName("practical_examples")
    val practicalExamples: List<PracticalExample>,
    
    @SerializedName("applications")
    val applications: Applications,
    
    @SerializedName("reading_time_minutes")
    val readingTimeMinutes: Int
)

data class Introduction(
    @SerializedName("hook")
    val hook: String,
    
    @SerializedName("overview")
    val overview: String,
    
    @SerializedName("real_world_relevance")
    val realWorldRelevance: String
)

data class CoreContent(
    @SerializedName("fundamental_concepts")
    val fundamentalConcepts: String,
    
    @SerializedName("detailed_explanation")
    val detailedExplanation: String,
    
    @SerializedName("key_principles")
    val keyPrinciples: List<KeyPrinciple>,
    
    @SerializedName("advanced_concepts")
    val advancedConcepts: String? = null
)

data class KeyPrinciple(
    @SerializedName("principle")
    val principle: String,
    
    @SerializedName("explanation")
    val explanation: String,
    
    @SerializedName("importance")
    val importance: String
)

data class PracticalExample(
    @SerializedName("example_title")
    val exampleTitle: String,
    
    @SerializedName("context")
    val context: String,
    
    @SerializedName("application")
    val application: String,
    
    @SerializedName("outcome")
    val outcome: String
)

data class Applications(
    @SerializedName("common_uses")
    val commonUses: List<String>,
    
    @SerializedName("professional_applications")
    val professionalApplications: List<String>? = null,
    
    @SerializedName("everyday_relevance")
    val everydayRelevance: String
)

enum class ContentType {
    @SerializedName("comprehensive_explanation")
    COMPREHENSIVE_EXPLANATION,
    
    @SerializedName("concept_overview")
    CONCEPT_OVERVIEW,
    
    @SerializedName("detailed_guide")
    DETAILED_GUIDE,
    
    @SerializedName("practical_tutorial")
    PRACTICAL_TUTORIAL
}

// INTERACTIVE SESSION Model - Separate from educational content
data class InteractiveSession(
    @SerializedName("session_type")
    val sessionType: SessionType,
    
    @SerializedName("topic_focus")
    val topicFocus: String,
    
    @SerializedName("session_intro")
    val sessionIntro: String,
    
    @SerializedName("questions")
    val questions: List<InteractiveQuestion>,
    
    @SerializedName("encouragement")
    val encouragement: String
)

data class InteractiveQuestion(
    @SerializedName("question_id")
    val questionId: String,
    
    @SerializedName("question_text")
    val questionText: String,
    
    @SerializedName("question_type")
    val questionType: InteractiveQuestionType,
    
    @SerializedName("options")
    val options: List<String>? = null,
    
    @SerializedName("correct_answer")
    val correctAnswer: String,
    
    @SerializedName("explanation")
    val explanation: String
)

enum class SessionType {
    @SerializedName("knowledge_check")
    KNOWLEDGE_CHECK,
    
    @SerializedName("practice_quiz")
    PRACTICE_QUIZ,
    
    @SerializedName("discussion_questions")
    DISCUSSION_QUESTIONS,
    
    @SerializedName("application_scenarios")
    APPLICATION_SCENARIOS
}

enum class InteractiveQuestionType {
    @SerializedName("multiple_choice")
    MULTIPLE_CHOICE,
    
    @SerializedName("open_ended")
    OPEN_ENDED,
    
    @SerializedName("true_false")
    TRUE_FALSE,
    
    @SerializedName("scenario_based")
    SCENARIO_BASED
}

// LEGACY: Keep old models for backward compatibility during transition
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

// LEGACY: Old structured content model (keep for compatibility)
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

// NEW: Simplified Security Analysis Models
data class SecurityAnalysisResponse(
    @SerializedName("analysis_type")
    val analysisType: SecurityAnalysisType,
    
    @SerializedName("problem")
    val problem: SecurityProblem,
    
    @SerializedName("solution")
    val solution: SecuritySolution,
    
    @SerializedName("result")
    val result: SecurityResult,
    
    @SerializedName("next_action")
    val nextAction: SecurityAction? = null
)

data class SecurityProblem(
    @SerializedName("title")
    val title: String,
    
    @SerializedName("description")
    val description: String,
    
    @SerializedName("severity")
    val severity: SecuritySeverity,
    
    @SerializedName("impact")
    val impact: String
)

data class SecuritySolution(
    @SerializedName("title")
    val title: String,
    
    @SerializedName("description")
    val description: String,
    
    @SerializedName("how_it_works")
    val howItWorks: String
)

data class SecurityResult(
    @SerializedName("before")
    val before: String,
    
    @SerializedName("after")
    val after: String,
    
    @SerializedName("outcome")
    val outcome: String
)

data class SecurityAction(
    @SerializedName("title")
    val title: String,
    
    @SerializedName("description")
    val description: String,
    
    @SerializedName("is_urgent")
    val isUrgent: Boolean = false
)

enum class SecurityAnalysisType {
    @SerializedName("account_security")
    ACCOUNT_SECURITY,
    
    @SerializedName("data_protection")
    DATA_PROTECTION,
    
    @SerializedName("network_security")
    NETWORK_SECURITY,
    
    @SerializedName("system_vulnerability")
    SYSTEM_VULNERABILITY
}

enum class SecuritySeverity {
    @SerializedName("low")
    LOW,
    
    @SerializedName("medium")
    MEDIUM,
    
    @SerializedName("high")
    HIGH,
    
    @SerializedName("critical")
    CRITICAL
}