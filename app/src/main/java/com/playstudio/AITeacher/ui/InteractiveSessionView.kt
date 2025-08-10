package com.playstudio.aiteacher.ui

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import androidx.recyclerview.widget.LinearLayoutManager
import com.playstudio.aiteacher.databinding.ViewInteractiveSessionBinding
import com.playstudio.aiteacher.models.*
import com.playstudio.aiteacher.ui.adapters.InteractiveQuestionsAdapter

/**
 * NEW: Interactive Session View
 * Handles dedicated Q&A mode separate from educational content
 * Provides clear knowledge testing without cluttering learning material
 */
class InteractiveSessionView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val binding: ViewInteractiveSessionBinding
    private var currentSession: InteractiveSession? = null
    private var questionsAdapter: InteractiveQuestionsAdapter? = null
    private var onBackToLearningRequested: (() -> Unit)? = null
    
    private var answeredQuestions = 0
    private var totalQuestions = 0

    init {
        orientation = VERTICAL
        binding = ViewInteractiveSessionBinding.inflate(LayoutInflater.from(context), this, true)
        setupViews()
        Log.d("InteractiveSessionView", "🎯 Interactive session view initialized")
    }

    private fun setupViews() {
        // Setup RecyclerView for questions
        binding.questionsRecyclerView.layoutManager = LinearLayoutManager(context)
        
        // Setup action buttons
        binding.backToLearningButton.setOnClickListener {
            onBackToLearningRequested?.invoke()
        }
        
        binding.reviewAnswersButton.setOnClickListener {
            questionsAdapter?.showAllAnswers()
        }
    }

    fun setInteractiveSession(session: InteractiveSession) {
        currentSession = session
        totalQuestions = session.questions.size
        answeredQuestions = 0
        displaySession(session)
    }

    private fun displaySession(session: InteractiveSession) {
        Log.d("InteractiveSessionView", "🎯 Starting interactive session: ${session.topicFocus}")
        
        // Header information
        binding.sessionTypeText.text = getSessionTypeDisplay(session.sessionType)
        binding.topicFocusText.text = session.topicFocus
        binding.sessionIntroText.text = session.sessionIntro
        binding.encouragementText.text = session.encouragement
        
        // Setup questions
        setupQuestions(session.questions)
        
        // Initialize progress
        updateProgress()
        
        Log.d("InteractiveSessionView", "✅ Interactive session display completed")
    }

    private fun getSessionTypeDisplay(sessionType: SessionType): String {
        return when (sessionType) {
            SessionType.KNOWLEDGE_CHECK -> "💭 Knowledge Check"
            SessionType.PRACTICE_QUIZ -> "📝 Practice Quiz"
            SessionType.DISCUSSION_QUESTIONS -> "💬 Discussion Questions"
            SessionType.APPLICATION_SCENARIOS -> "🎭 Application Scenarios"
        }
    }

    private fun setupQuestions(questions: List<InteractiveQuestion>) {
        val adapter = InteractiveQuestionsAdapter(
            questions = questions,
            onQuestionAnswered = { questionId, isCorrect ->
                onQuestionAnswered(questionId, isCorrect)
            }
        )
        questionsAdapter = adapter
        binding.questionsRecyclerView.adapter = adapter
    }

    private fun onQuestionAnswered(questionId: String, isCorrect: Boolean) {
        answeredQuestions++
        updateProgress()
        
        // Check if all questions are answered
        if (answeredQuestions >= totalQuestions) {
            onAllQuestionsCompleted()
        }
        
        Log.d("InteractiveSessionView", "Question answered: $questionId, correct: $isCorrect, progress: $answeredQuestions/$totalQuestions")
    }

    private fun updateProgress() {
        val progress = if (totalQuestions > 0) {
            (answeredQuestions * 100) / totalQuestions
        } else 0
        
        binding.sessionProgress.progress = progress
        binding.progressText.text = "$answeredQuestions/$totalQuestions"
    }

    private fun onAllQuestionsCompleted() {
        // Show completion message or enable review
        binding.reviewAnswersButton.isEnabled = true
        Log.d("InteractiveSessionView", "🎉 All questions completed!")
        
        // Could show completion animation or summary here
    }

    // Public interface
    fun setOnBackToLearningRequestedListener(listener: () -> Unit) {
        onBackToLearningRequested = listener
    }

    interface OnSessionInteractionListener {
        fun onQuestionAnswered(questionId: String, selectedAnswer: String, isCorrect: Boolean)
        fun onSessionCompleted(correctAnswers: Int, totalQuestions: Int)
        fun onBackToLearningRequested()
    }

    private var sessionInteractionListener: OnSessionInteractionListener? = null

    fun setOnSessionInteractionListener(listener: OnSessionInteractionListener) {
        this.sessionInteractionListener = listener
    }

    fun resetSession() {
        answeredQuestions = 0
        updateProgress()
        questionsAdapter?.resetQuestions()
    }
}