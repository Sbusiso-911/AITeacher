package com.playstudio.aiteacher.ui

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
//import androidx.media3.common.util.Log
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.playstudio.aiteacher.R
import com.playstudio.aiteacher.databinding.ViewStructuredContentBinding
import com.playstudio.aiteacher.models.*
import com.playstudio.aiteacher.ui.adapters.*

/**
 * Custom view for displaying structured educational content
 * Handles different types of educational responses with appropriate UI components
 */
class StructuredContentView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val binding: ViewStructuredContentBinding
    private var currentResponse: EducationalResponse? = null

    init {
        orientation = VERTICAL
        binding = ViewStructuredContentBinding.inflate(LayoutInflater.from(context), this, true)
        setupViews()
        Log.d("StructuredView", "▶️ StructuredContentView initialized")
        Log.d("StructuredView", "▶️ StructuredContentView initialized")
    }

    private fun setupViews() {
        // Initialize RecyclerViews with appropriate adapters
        binding.stepsRecyclerView.layoutManager = LinearLayoutManager(context)
        binding.examplesRecyclerView.layoutManager = LinearLayoutManager(context)
        binding.questionsRecyclerView.layoutManager = LinearLayoutManager(context)
        binding.codeSnippetsRecyclerView.layoutManager = LinearLayoutManager(context)
        binding.formulasRecyclerView.layoutManager = LinearLayoutManager(context)
    }

    fun setEducationalResponse(response: EducationalResponse) {
        currentResponse = response
        displayContent(response)
    }

    private fun displayContent(response: EducationalResponse) {
        Log.d("StructuredView", "▶️ displayContent: type=${response.responseType}, mainExp=${response.content.mainExplanation.take(30)}...")
        // Clear all views first
        clearAllViews()

        // Set main content
        setupMainContent(response)
        
        // Set metadata
        setupMetadata(response.metadata)
        
        // Display structured content based on type
        when (response.responseType) {
            ResponseType.STEP_BY_STEP -> {
                Log.d("StructuredView", "Setting up STEP_BY_STEP content")
                setupStepByStepContent(response.content)
            }
            ResponseType.QUIZ -> {
                Log.d("StructuredView", "Setting up QUIZ content")
                setupQuizContent(response.content)
            }
            ResponseType.LESSON -> {
                Log.d("StructuredView", "Setting up LESSON content")
                setupLessonContent(response.content)
            }
            ResponseType.CODE_TUTORIAL -> {
                Log.d("StructuredView", "Setting up CODE_TUTORIAL content")
                setupCodeTutorialContent(response.content)
            }
            ResponseType.PRACTICE -> {
                Log.d("StructuredView", "Setting up PRACTICE content")
                setupPracticeContent(response.content)
            }
            else -> {
                Log.d("StructuredView", "Setting up GENERAL content")
                setupGeneralContent(response.content)
            }
        }

        // Setup interactive elements
        response.interactiveElements?.let { setupInteractiveElements(it) }
        
        // Force visibility of main sections for debugging
        binding.mainExplanationText.visibility = View.VISIBLE
        
        // TEMPORARY: Force all sections visible to test collapse theory
        forceVisibilityForDebug()
        
        Log.d("StructuredView", "✅ displayContent completed")
    }
    
    // Helper method to force visibility of all sections for debugging
    private fun forceVisibilityForDebug() {
        binding.stepsSection.visibility = View.VISIBLE
        binding.examplesSection.visibility = View.VISIBLE
        binding.questionsSection.visibility = View.VISIBLE
        binding.codeSnippetsSection.visibility = View.VISIBLE
        binding.formulasSection.visibility = View.VISIBLE
        binding.keyConceptsSection.visibility = View.VISIBLE
        binding.prerequisitesSection.visibility = View.VISIBLE
        binding.nextTopicsSection.visibility = View.VISIBLE
        binding.learningObjectivesSection.visibility = View.VISIBLE
        binding.interactiveElementsSection.visibility = View.VISIBLE
        Log.d("StructuredView", "🔧 Forced all sections to VISIBLE for debugging")
    }

    private fun setupMainContent(response: EducationalResponse) {
        // Subject and difficulty indicators
        binding.subjectChip.text = response.subject.capitalize()
        binding.difficultyChip.text = response.difficultyLevel.name.lowercase().capitalize()
        
        // Set difficulty chip color based on level
        val difficultyColor = when (response.difficultyLevel) {
            DifficultyLevel.BEGINNER -> R.color.difficulty_beginner
            DifficultyLevel.INTERMEDIATE -> R.color.difficulty_intermediate
            DifficultyLevel.ADVANCED -> R.color.difficulty_advanced
            DifficultyLevel.EXPERT -> R.color.difficulty_expert
        }
        binding.difficultyChip.setBackgroundResource(difficultyColor)

        // Main explanation
        binding.mainExplanationText.text = response.content.mainExplanation
        Log.d("StructuredView", "Set main explanation: ${response.content.mainExplanation.take(50)}...")
        
        // Key concepts
        response.content.keyConcepts?.let { concepts ->
            if (concepts.isNotEmpty()) {
                binding.keyConceptsSection.visibility = View.VISIBLE
                binding.keyConceptsContainer.removeAllViews()
                concepts.forEach { concept ->
                    val chipView = createConceptChip(concept)
                    binding.keyConceptsContainer.addView(chipView)
                }
            }
        }
    }

    private fun setupMetadata(metadata: ResponseMetadata) {
        // Reading time
        binding.estimatedTimeText.text = "${metadata.estimatedReadingTime} min read"
        
        // Prerequisites
        metadata.prerequisites?.let { prereqs ->
            if (prereqs.isNotEmpty()) {
                binding.prerequisitesSection.visibility = View.VISIBLE
                binding.prerequisitesText.text = prereqs.joinToString(", ")
            }
        }
        
        // Next topics
        metadata.nextTopics?.let { topics ->
            if (topics.isNotEmpty()) {
                binding.nextTopicsSection.visibility = View.VISIBLE
                binding.nextTopicsText.text = topics.joinToString(", ")
            }
        }
        
        // Learning objectives
        metadata.learningObjectives?.let { objectives ->
            if (objectives.isNotEmpty()) {
                binding.learningObjectivesSection.visibility = View.VISIBLE
                binding.learningObjectivesList.removeAllViews()
                objectives.forEach { objective ->
                    val objectiveView = createObjectiveItem(objective)
                    binding.learningObjectivesList.addView(objectiveView)
                }
            }
        }
    }

    private fun setupStepByStepContent(content: StructuredContent) {
        content.steps?.let { steps ->
            if (steps.isNotEmpty()) {
                binding.stepsSection.visibility = View.VISIBLE
                val adapter = LearningStepsAdapter(steps) { step ->
                    // Handle step click (expand/collapse)
                    onStepClicked(step)
                }
                binding.stepsRecyclerView.adapter = adapter
            }
        }
        
        // Also show examples if available
        setupExamples(content.examples)
    }

    private fun setupQuizContent(content: StructuredContent) {
        content.practiceQuestions?.let { questions ->
            if (questions.isNotEmpty()) {
                binding.questionsSection.visibility = View.VISIBLE
                val adapter = QuestionsAdapter(questions) { question, selectedAnswer ->
                    // Handle answer selection
                    onQuestionAnswered(question, selectedAnswer)
                }
                binding.questionsRecyclerView.adapter = adapter
            }
        }
    }

    private fun setupLessonContent(content: StructuredContent) {
        // Show all relevant sections for a comprehensive lesson
        setupStepByStepContent(content)
        setupExamples(content.examples)
        setupFormulas(content.formulas)
        setupCodeSnippets(content.codeSnippets)
    }

    private fun setupCodeTutorialContent(content: StructuredContent) {
        setupCodeSnippets(content.codeSnippets)
        setupExamples(content.examples)
        setupStepByStepContent(content)
    }

    private fun setupPracticeContent(content: StructuredContent) {
        setupQuizContent(content)
        setupExamples(content.examples)
    }

    private fun setupGeneralContent(content: StructuredContent) {
        // Show all available content sections
        setupExamples(content.examples)
        setupFormulas(content.formulas)
        setupCodeSnippets(content.codeSnippets)
        
        content.practiceQuestions?.let { questions ->
            if (questions.isNotEmpty()) {
                binding.questionsSection.visibility = View.VISIBLE
                val adapter = QuestionsAdapter(questions) { question, selectedAnswer ->
                    onQuestionAnswered(question, selectedAnswer)
                }
                binding.questionsRecyclerView.adapter = adapter
            }
        }
    }

    private fun setupExamples(examples: List<Example>?) {
        examples?.let { exampleList ->
            if (exampleList.isNotEmpty()) {
                binding.examplesSection.visibility = View.VISIBLE
                val adapter = ExamplesAdapter(exampleList) { example ->
                    onExampleClicked(example)
                }
                binding.examplesRecyclerView.adapter = adapter
            }
        }
    }

    private fun setupFormulas(formulas: List<Formula>?) {
        formulas?.let { formulaList ->
            if (formulaList.isNotEmpty()) {
                binding.formulasSection.visibility = View.VISIBLE
                val adapter = FormulasAdapter(formulaList) { formula ->
                    onFormulaClicked(formula)
                }
                binding.formulasRecyclerView.adapter = adapter
            }
        }
    }

    private fun setupCodeSnippets(codeSnippets: List<CodeSnippet>?) {
        codeSnippets?.let { snippets ->
            if (snippets.isNotEmpty()) {
                binding.codeSnippetsSection.visibility = View.VISIBLE
                val adapter = CodeSnippetsAdapter(snippets) { snippet ->
                    onCodeSnippetClicked(snippet)
                }
                binding.codeSnippetsRecyclerView.adapter = adapter
            }
        }
    }

    private fun setupInteractiveElements(elements: List<InteractiveElement>) {
        binding.interactiveElementsSection.visibility = View.VISIBLE
        binding.interactiveElementsContainer.removeAllViews()
        
        elements.forEach { element ->
            val elementView = createInteractiveElementView(element)
            binding.interactiveElementsContainer.addView(elementView)
        }
    }

    private fun clearAllViews() {
        binding.stepsSection.visibility = View.GONE
        binding.examplesSection.visibility = View.GONE
        binding.questionsSection.visibility = View.GONE
        binding.codeSnippetsSection.visibility = View.GONE
        binding.formulasSection.visibility = View.GONE
        binding.keyConceptsSection.visibility = View.GONE
        binding.prerequisitesSection.visibility = View.GONE
        binding.nextTopicsSection.visibility = View.GONE
        binding.learningObjectivesSection.visibility = View.GONE
        binding.interactiveElementsSection.visibility = View.GONE
        
        binding.keyConceptsContainer.removeAllViews()
        binding.learningObjectivesList.removeAllViews()
        binding.interactiveElementsContainer.removeAllViews()
    }

    // Helper methods for creating UI elements
    private fun createConceptChip(concept: String): View {
        val chipView = android.widget.TextView(context).apply {
            text = concept
            background = context.getDrawable(R.drawable.chip_subject_background)
            setPadding(16, 8, 16, 8)
            setTextColor(context.getColor(R.color.glass_text_primary))
            textSize = 12f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(8, 4, 8, 4)
            }
        }
        return chipView
    }

    private fun createObjectiveItem(objective: String): View {
        val itemView = android.widget.TextView(context).apply {
            text = "• $objective"
            setTextColor(context.getColor(R.color.glass_text_primary))
            textSize = 14f
            setPadding(16, 8, 16, 8)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 4, 0, 4)
            }
        }
        return itemView
    }

    private fun createInteractiveElementView(element: InteractiveElement): View {
        return when (element.elementType) {
            InteractiveElementType.PROGRESS_TRACKER -> createProgressTracker(element)
            InteractiveElementType.INTERACTIVE_DIAGRAM -> createInteractiveDiagram(element)
            InteractiveElementType.CODE_PLAYGROUND -> createCodePlayground(element)
            InteractiveElementType.FORMULA_RENDERER -> createFormulaRenderer(element)
            InteractiveElementType.QUIZ_WIDGET -> createQuizWidget(element)
        }
    }

    // Interactive element creators
    private fun createProgressTracker(element: InteractiveElement): View {
        return android.widget.TextView(context).apply {
            text = "📊 ${element.title}"
            background = context.getDrawable(R.drawable.bg_metadata_section)
            setPadding(16, 12, 16, 12)
            setTextColor(context.getColor(R.color.glass_text_primary))
            textSize = 14f
        }
    }

    private fun createInteractiveDiagram(element: InteractiveElement): View {
        return android.widget.TextView(context).apply {
            text = "📈 ${element.title}"
            background = context.getDrawable(R.drawable.bg_metadata_section)
            setPadding(16, 12, 16, 12)
            setTextColor(context.getColor(R.color.glass_text_primary))
            textSize = 14f
        }
    }

    private fun createCodePlayground(element: InteractiveElement): View {
        return android.widget.TextView(context).apply {
            text = "💻 ${element.title}"
            background = context.getDrawable(R.drawable.code_syntax_background)
            setPadding(16, 12, 16, 12)
            setTextColor(context.getColor(R.color.glass_text_primary))
            textSize = 14f
        }
    }

    private fun createFormulaRenderer(element: InteractiveElement): View {
        return android.widget.TextView(context).apply {
            text = "∑ ${element.title}"
            background = context.getDrawable(R.drawable.formula_renderer_background)
            setPadding(16, 12, 16, 12)
            setTextColor(context.getColor(R.color.formula_text))
            textSize = 14f
        }
    }

    private fun createQuizWidget(element: InteractiveElement): View {
        return android.widget.TextView(context).apply {
            text = "🎯 ${element.title}"
            background = context.getDrawable(R.drawable.quiz_neutral_background)
            setPadding(16, 12, 16, 12)
            setTextColor(context.getColor(R.color.glass_text_primary))
            textSize = 14f
        }
    }

    // Event handlers
    private fun onStepClicked(step: LearningStep) {
        // Expand/collapse step details
        // Could show hints, visual aids, etc.
    }

    private fun onQuestionAnswered(question: Question, selectedAnswer: String) {
        // Handle quiz answer selection
        // Show correct/incorrect feedback
        // Update progress tracking
    }

    private fun onExampleClicked(example: Example) {
        // Show detailed example view
        // Could expand to show full solution
    }

    private fun onFormulaClicked(formula: Formula) {
        // Show formula details
        // Could display larger version with variable explanations
    }

    private fun onCodeSnippetClicked(snippet: CodeSnippet) {
        // Show code in full-screen editor
        // Allow copying, running (if supported)
    }

    // Public interface for parent components
    interface OnContentInteractionListener {
        fun onQuestionAnswered(questionId: String, answer: String, isCorrect: Boolean)
        fun onStepCompleted(stepNumber: Int)
        fun onExampleViewed(exampleTitle: String)
        fun onFormulaUsed(formulaName: String)
        fun onCodeExecuted(codeLanguage: String, codeTitle: String)
    }

    private var contentInteractionListener: OnContentInteractionListener? = null

    fun setOnContentInteractionListener(listener: OnContentInteractionListener) {
        this.contentInteractionListener = listener
    }
}