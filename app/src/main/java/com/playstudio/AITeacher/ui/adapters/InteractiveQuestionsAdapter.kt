package com.playstudio.aiteacher.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.recyclerview.widget.RecyclerView
import com.playstudio.aiteacher.R
import com.playstudio.aiteacher.models.InteractiveQuestion
import com.playstudio.aiteacher.models.InteractiveQuestionType

/**
 * Adapter for interactive questions in the dedicated Q&A session
 * Handles different question types with appropriate UI
 */
class InteractiveQuestionsAdapter(
    private val questions: List<InteractiveQuestion>,
    private val onQuestionAnswered: (String, Boolean) -> Unit
) : RecyclerView.Adapter<InteractiveQuestionsAdapter.QuestionViewHolder>() {

    private val answeredQuestions = mutableSetOf<String>()
    private val showAnswers = mutableSetOf<String>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QuestionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_interactive_question, parent, false)
        return QuestionViewHolder(view)
    }

    override fun onBindViewHolder(holder: QuestionViewHolder, position: Int) {
        holder.bind(questions[position], position + 1)
    }

    override fun getItemCount(): Int = questions.size

    fun showAllAnswers() {
        showAnswers.addAll(questions.map { it.questionId })
        notifyDataSetChanged()
    }

    fun resetQuestions() {
        answeredQuestions.clear()
        showAnswers.clear()
        notifyDataSetChanged()
    }

    inner class QuestionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val questionNumberText: TextView = itemView.findViewById(R.id.questionNumberText)
        private val questionText: TextView = itemView.findViewById(R.id.questionText)
        private val optionsContainer: LinearLayout = itemView.findViewById(R.id.optionsContainer)
        private val openAnswerEditText: EditText = itemView.findViewById(R.id.openAnswerEditText)
        private val submitButton: Button = itemView.findViewById(R.id.submitButton)
        private val explanationSection: LinearLayout = itemView.findViewById(R.id.explanationSection)
        private val explanationText: TextView = itemView.findViewById(R.id.explanationText)
        private val feedbackText: TextView = itemView.findViewById(R.id.feedbackText)

        fun bind(question: InteractiveQuestion, questionNumber: Int) {
            questionNumberText.text = "Question $questionNumber"
            questionText.text = question.questionText

            // Clear previous state
            optionsContainer.removeAllViews()
            optionsContainer.visibility = View.GONE
            openAnswerEditText.visibility = View.GONE
            submitButton.visibility = View.GONE
            explanationSection.visibility = View.GONE

            val isAnswered = answeredQuestions.contains(question.questionId)
            val shouldShowAnswer = showAnswers.contains(question.questionId)

            when (question.questionType) {
                InteractiveQuestionType.MULTIPLE_CHOICE -> {
                    setupMultipleChoice(question, isAnswered, shouldShowAnswer)
                }
                InteractiveQuestionType.TRUE_FALSE -> {
                    setupTrueFalse(question, isAnswered, shouldShowAnswer)
                }
                InteractiveQuestionType.OPEN_ENDED -> {
                    setupOpenEnded(question, isAnswered, shouldShowAnswer)
                }
                InteractiveQuestionType.SCENARIO_BASED -> {
                    setupScenarioBased(question, isAnswered, shouldShowAnswer)
                }
            }

            if (shouldShowAnswer || isAnswered) {
                showExplanation(question)
            }
        }

        private fun setupMultipleChoice(question: InteractiveQuestion, isAnswered: Boolean, shouldShowAnswer: Boolean) {
            optionsContainer.visibility = View.VISIBLE
            
            question.options?.let { options ->
                val radioGroup = RadioGroup(itemView.context)
                radioGroup.orientation = RadioGroup.VERTICAL
                
                options.forEachIndexed { index, option ->
                    val radioButton = RadioButton(itemView.context).apply {
                        text = option
                        id = index
                        textSize = 14f
                        setPadding(16, 12, 16, 12)
                        isEnabled = !isAnswered && !shouldShowAnswer
                        
                        if (shouldShowAnswer && option == question.correctAnswer) {
                            setTextColor(context.getColor(R.color.correct_answer))
                            setTypeface(null, android.graphics.Typeface.BOLD)
                        }
                    }
                    radioGroup.addView(radioButton)
                }
                
                radioGroup.setOnCheckedChangeListener { _, checkedId ->
                    if (!isAnswered && checkedId != -1) {
                        val selectedOption = options[checkedId]
                        val isCorrect = selectedOption == question.correctAnswer
                        answeredQuestions.add(question.questionId)
                        onQuestionAnswered(question.questionId, isCorrect)
                        notifyItemChanged(adapterPosition)
                    }
                }
                
                optionsContainer.addView(radioGroup)
            }
        }

        private fun setupTrueFalse(question: InteractiveQuestion, isAnswered: Boolean, shouldShowAnswer: Boolean) {
            optionsContainer.visibility = View.VISIBLE
            
            val radioGroup = RadioGroup(itemView.context)
            radioGroup.orientation = RadioGroup.HORIZONTAL
            
            val trueButton = RadioButton(itemView.context).apply {
                text = "True"
                id = 0
                textSize = 14f
                setPadding(16, 12, 16, 12)
                isEnabled = !isAnswered && !shouldShowAnswer
                
                if (shouldShowAnswer && question.correctAnswer.lowercase() == "true") {
                    setTextColor(context.getColor(R.color.correct_answer))
                    setTypeface(null, android.graphics.Typeface.BOLD)
                }
            }
            
            val falseButton = RadioButton(itemView.context).apply {
                text = "False"
                id = 1
                textSize = 14f
                setPadding(16, 12, 16, 12)
                isEnabled = !isAnswered && !shouldShowAnswer
                
                if (shouldShowAnswer && question.correctAnswer.lowercase() == "false") {
                    setTextColor(context.getColor(R.color.correct_answer))
                    setTypeface(null, android.graphics.Typeface.BOLD)
                }
            }
            
            radioGroup.addView(trueButton)
            radioGroup.addView(falseButton)
            
            radioGroup.setOnCheckedChangeListener { _, checkedId ->
                if (!isAnswered && checkedId != -1) {
                    val selectedAnswer = if (checkedId == 0) "true" else "false"
                    val isCorrect = selectedAnswer.lowercase() == question.correctAnswer.lowercase()
                    answeredQuestions.add(question.questionId)
                    onQuestionAnswered(question.questionId, isCorrect)
                    notifyItemChanged(adapterPosition)
                }
            }
            
            optionsContainer.addView(radioGroup)
        }

        private fun setupOpenEnded(question: InteractiveQuestion, isAnswered: Boolean, shouldShowAnswer: Boolean) {
            openAnswerEditText.visibility = View.VISIBLE
            submitButton.visibility = if (!isAnswered && !shouldShowAnswer) View.VISIBLE else View.GONE
            
            openAnswerEditText.isEnabled = !isAnswered && !shouldShowAnswer
            
            submitButton.setOnClickListener {
                val userAnswer = openAnswerEditText.text.toString().trim()
                if (userAnswer.isNotEmpty()) {
                    // For open-ended questions, we'll mark as correct for participation
                    answeredQuestions.add(question.questionId)
                    onQuestionAnswered(question.questionId, true)
                    notifyItemChanged(adapterPosition)
                }
            }
        }

        private fun setupScenarioBased(question: InteractiveQuestion, isAnswered: Boolean, shouldShowAnswer: Boolean) {
            // Similar to open-ended but with more context
            setupOpenEnded(question, isAnswered, shouldShowAnswer)
        }

        private fun showExplanation(question: InteractiveQuestion) {
            explanationSection.visibility = View.VISIBLE
            explanationText.text = question.explanation
            
            val isCorrect = answeredQuestions.contains(question.questionId)
            feedbackText.text = if (isCorrect) "✅ Well done!" else "💡 Here's the explanation:"
            feedbackText.setTextColor(
                itemView.context.getColor(
                    if (isCorrect) R.color.correct_answer else R.color.glass_text_secondary
                )
            )
        }
    }
}