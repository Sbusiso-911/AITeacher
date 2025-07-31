package com.playstudio.aiteacher.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.playstudio.aiteacher.R
import com.playstudio.aiteacher.databinding.ItemQuestionBinding
import com.playstudio.aiteacher.models.Question
import com.playstudio.aiteacher.models.QuestionType

/**
 * Adapter for displaying interactive quiz questions with different question types
 */
class QuestionsAdapter(
    private val questions: List<Question>,
    private val onAnswerSelected: (Question, String) -> Unit
) : RecyclerView.Adapter<QuestionsAdapter.QuestionViewHolder>() {

    private val answeredQuestions = mutableMapOf<String, Pair<String, Boolean>>() // questionId -> (answer, isCorrect)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QuestionViewHolder {
        val binding = ItemQuestionBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return QuestionViewHolder(binding)
    }

    override fun onBindViewHolder(holder: QuestionViewHolder, position: Int) {
        holder.bind(questions[position], position + 1)
    }

    override fun getItemCount() = questions.size

    inner class QuestionViewHolder(
        private val binding: ItemQuestionBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        // Additional UI elements not in binding
        private val questionCard: CardView = itemView.findViewById(R.id.questionCard)
        private val answerFeedbackSection: LinearLayout = itemView.findViewById(R.id.answerFeedbackSection)
        private val feedbackIcon: ImageView = itemView.findViewById(R.id.feedbackIcon)
        private val feedbackText: TextView = itemView.findViewById(R.id.feedbackText)
        private val explanationText: TextView = itemView.findViewById(R.id.explanationText)

        fun bind(question: Question, questionNumber: Int) {
            // Question header
            binding.questionNumber.text = questionNumber.toString()
            binding.questionText.text = question.questionText
            binding.questionPoints.text = "${question.points} pt${if (question.points != 1) "s" else ""}"
            
            // Difficulty indicator
            val difficultyLevel = question.difficulty ?: com.playstudio.aiteacher.models.DifficultyLevel.BEGINNER
            val difficultyColor = when (difficultyLevel) {
                com.playstudio.aiteacher.models.DifficultyLevel.BEGINNER -> R.color.difficulty_beginner
                com.playstudio.aiteacher.models.DifficultyLevel.INTERMEDIATE -> R.color.difficulty_intermediate
                com.playstudio.aiteacher.models.DifficultyLevel.ADVANCED -> R.color.difficulty_advanced
                com.playstudio.aiteacher.models.DifficultyLevel.EXPERT -> R.color.difficulty_expert
            }
            binding.difficultyIndicator.setBackgroundColor(
                ContextCompat.getColor(itemView.context, difficultyColor)
            )

            // Setup question based on type
            when (question.questionType) {
                QuestionType.MULTIPLE_CHOICE -> setupMultipleChoice(question)
                QuestionType.TRUE_FALSE -> setupTrueFalse(question)
                QuestionType.SHORT_ANSWER -> setupShortAnswer(question)
                QuestionType.FILL_BLANK -> setupFillBlank(question)
                QuestionType.CODE_COMPLETION -> setupCodeCompletion(question)
            }

            // Show previous answer if exists
            val previousAnswer = answeredQuestions[question.id]
            if (previousAnswer != null) {
                showAnswerResult(question, previousAnswer.first, previousAnswer.second)
            }
        }

        private fun setupMultipleChoice(question: Question) {
            binding.multipleChoiceContainer.visibility = View.VISIBLE
            binding.shortAnswerContainer.visibility = View.GONE
            binding.codeCompletionContainer.visibility = View.GONE

            // Clear previous options
            binding.optionsRadioGroup.removeAllViews()

            // Add radio buttons for each option
            question.options?.forEachIndexed { index, option ->
                val radioButton = RadioButton(itemView.context).apply {
                    text = option
                    textSize = 14f
                    setTextColor(ContextCompat.getColor(context, R.color.glass_text_primary))
                    setPadding(16, 12, 16, 12)
                    id = index
                }
                binding.optionsRadioGroup.addView(radioButton)
            }

            // Handle selection
            binding.optionsRadioGroup.setOnCheckedChangeListener { _, checkedId ->
                if (checkedId != -1) {
                    val selectedOption = question.options?.get(checkedId) ?: ""
                    val isCorrect = selectedOption == question.correctAnswer
                    answeredQuestions[question.id] = Pair(selectedOption, isCorrect)
                    
                    showAnswerResult(question, selectedOption, isCorrect)
                    onAnswerSelected(question, selectedOption)
                }
            }
        }

        private fun setupTrueFalse(question: Question) {
            binding.multipleChoiceContainer.visibility = View.VISIBLE
            binding.shortAnswerContainer.visibility = View.GONE
            binding.codeCompletionContainer.visibility = View.GONE

            // Clear and add True/False options
            binding.optionsRadioGroup.removeAllViews()

            val trueButton = RadioButton(itemView.context).apply {
                text = "True"
                textSize = 14f
                setTextColor(ContextCompat.getColor(context, R.color.glass_text_primary))
                setPadding(16, 12, 16, 12)
                id = 0
            }

            val falseButton = RadioButton(itemView.context).apply {
                text = "False"
                textSize = 14f
                setTextColor(ContextCompat.getColor(context, R.color.glass_text_primary))
                setPadding(16, 12, 16, 12)
                id = 1
            }

            binding.optionsRadioGroup.addView(trueButton)
            binding.optionsRadioGroup.addView(falseButton)

            // Handle selection
            binding.optionsRadioGroup.setOnCheckedChangeListener { _, checkedId ->
                if (checkedId != -1) {
                    val selectedAnswer = if (checkedId == 0) "True" else "False"
                    val isCorrect = selectedAnswer.equals(question.correctAnswer, ignoreCase = true)
                    answeredQuestions[question.id] = Pair(selectedAnswer, isCorrect)
                    
                    showAnswerResult(question, selectedAnswer, isCorrect)
                    onAnswerSelected(question, selectedAnswer)
                }
            }
        }

        private fun setupShortAnswer(question: Question) {
            binding.multipleChoiceContainer.visibility = View.GONE
            binding.shortAnswerContainer.visibility = View.VISIBLE
            binding.codeCompletionContainer.visibility = View.GONE

            binding.shortAnswerInput.hint = "Enter your answer..."
            
            // Submit button
            binding.submitAnswerButton.setOnClickListener {
                val userAnswer = binding.shortAnswerInput.text.toString().trim()
                if (userAnswer.isNotEmpty()) {
                    val isCorrect = userAnswer.equals(question.correctAnswer, ignoreCase = true)
                    answeredQuestions[question.id] = Pair(userAnswer, isCorrect)
                    
                    showAnswerResult(question, userAnswer, isCorrect)
                    onAnswerSelected(question, userAnswer)
                    
                    // Disable input after submission
                    binding.shortAnswerInput.isEnabled = false
                    binding.submitAnswerButton.isEnabled = false
                }
            }
        }

        private fun setupFillBlank(question: Question) {
            // Similar to short answer but with placeholder text showing the blank
            setupShortAnswer(question)
            binding.shortAnswerInput.hint = "Fill in the blank..."
        }

        private fun setupCodeCompletion(question: Question) {
            binding.multipleChoiceContainer.visibility = View.GONE
            binding.shortAnswerContainer.visibility = View.GONE
            binding.codeCompletionContainer.visibility = View.VISIBLE

            binding.codeQuestionText.text = question.questionText
            binding.codeAnswerInput.hint = "Complete the code..."
            
            // Submit button for code
            binding.submitCodeButton.setOnClickListener {
                val userCode = binding.codeAnswerInput.text.toString().trim()
                if (userCode.isNotEmpty()) {
                    // For code completion, we might want more flexible matching
                    val isCorrect = checkCodeAnswer(userCode, question.correctAnswer)
                    answeredQuestions[question.id] = Pair(userCode, isCorrect)
                    
                    showAnswerResult(question, userCode, isCorrect)
                    onAnswerSelected(question, userCode)
                    
                    // Disable input after submission
                    binding.codeAnswerInput.isEnabled = false
                    binding.submitCodeButton.isEnabled = false
                }
            }
        }

        private fun showAnswerResult(question: Question, userAnswer: String, isCorrect: Boolean) {
            answerFeedbackSection.visibility = View.VISIBLE
            
            if (isCorrect) {
                feedbackIcon.setImageResource(R.drawable.ic_check_circle)
                feedbackIcon.setColorFilter(
                    ContextCompat.getColor(itemView.context, R.color.success_green)
                )
                feedbackText.text = "Correct! 🎉"
                feedbackText.setTextColor(
                    ContextCompat.getColor(itemView.context, R.color.success_green)
                )
                questionCard.setCardBackgroundColor(
                    ContextCompat.getColor(itemView.context, R.color.success_background)
                )
            } else {
                feedbackIcon.setImageResource(R.drawable.ic_cancel)
                feedbackIcon.setColorFilter(
                    ContextCompat.getColor(itemView.context, R.color.error_red)
                )
                feedbackText.text = "Incorrect. The correct answer is: ${question.correctAnswer}"
                feedbackText.setTextColor(
                    ContextCompat.getColor(itemView.context, R.color.error_red)
                )
                questionCard.setCardBackgroundColor(
                    ContextCompat.getColor(itemView.context, R.color.error_background)
                )
            }

            // Show explanation
            explanationText.text = question.explanation
            explanationText.visibility = View.VISIBLE
        }

        private fun checkCodeAnswer(userCode: String, correctAnswer: String): Boolean {
            // Simple check - could be enhanced with more sophisticated code comparison
            return userCode.replace("\\s+".toRegex(), "")
                .equals(correctAnswer.replace("\\s+".toRegex(), ""), ignoreCase = true)
        }
    }

    fun getScore(): Pair<Int, Int> {
        val correctAnswers = answeredQuestions.values.count { it.second }
        val totalQuestions = questions.size
        return Pair(correctAnswers, totalQuestions)
    }

    fun getTotalPoints(): Int {
        return answeredQuestions.entries.sumOf { (questionId, answer) ->
            if (answer.second) {
                questions.find { it.id == questionId }?.points ?: 0
            } else 0
        }
    }

    fun resetAnswers() {
        answeredQuestions.clear()
        notifyDataSetChanged()
    }
}