package com.playstudio.aiteacher.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import androidx.recyclerview.widget.RecyclerView
import com.playstudio.aiteacher.R
import com.playstudio.aiteacher.databinding.ItemLearningStepBinding
import com.playstudio.aiteacher.models.LearningStep

/**
 * Adapter for displaying learning steps in a structured, expandable format
 */
class LearningStepsAdapter(
    private val steps: List<LearningStep>,
    private val onStepClick: (LearningStep) -> Unit
) : RecyclerView.Adapter<LearningStepsAdapter.StepViewHolder>() {

    private val expandedSteps = mutableSetOf<Int>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StepViewHolder {
        val binding = ItemLearningStepBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return StepViewHolder(binding)
    }

    override fun onBindViewHolder(holder: StepViewHolder, position: Int) {
        holder.bind(steps[position], position)
    }

    override fun getItemCount() = steps.size

    inner class StepViewHolder(
        private val binding: ItemLearningStepBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(step: LearningStep, position: Int) {
            // Step number and title
            binding.stepNumber.text = step.stepNumber.toString()
            binding.stepTitle.text = step.title
            binding.stepExplanation.text = step.explanation
            
            // Output (if available)
            if (!step.output.isNullOrEmpty()) {
                binding.stepOutput.visibility = View.VISIBLE
                binding.stepOutput.text = step.output
            } else {
                binding.stepOutput.visibility = View.GONE
            }

            // Visual aid (if available)
            if (!step.visualAid.isNullOrEmpty()) {
                binding.visualAidSection.visibility = View.VISIBLE
                binding.visualAidDescription.text = step.visualAid
            } else {
                binding.visualAidSection.visibility = View.GONE
            }

            // Hints (if available)
            if (!step.hints.isNullOrEmpty()) {
                binding.hintsSection.visibility = View.VISIBLE
                binding.hintsContainer.removeAllViews()
                step.hints.forEach { hint ->
                    val hintView = createHintView(hint)
                    binding.hintsContainer.addView(hintView)
                }
            } else {
                binding.hintsSection.visibility = View.GONE
            }

            // Expansion state
            val isExpanded = expandedSteps.contains(position)
            binding.expandableContent.visibility = if (isExpanded) View.VISIBLE else View.GONE
            binding.expandIcon.rotation = if (isExpanded) 180f else 0f

            // Step progress indicator
            binding.stepProgressCircle.setBackgroundResource(
                if (isExpanded) R.drawable.step_completed_background
                else R.drawable.step_pending_background
            )

            // Click listener for expansion
            binding.stepHeader.setOnClickListener {
                toggleExpansion(position)
                onStepClick(step)
            }

            // Add step completion animation
            if (isExpanded) {
                val animation = AnimationUtils.loadAnimation(itemView.context, R.anim.fade_in)
                binding.expandableContent.startAnimation(animation)
            }
        }

        private fun createHintView(hint: String): View {
            return android.widget.TextView(itemView.context).apply {
                text = "💡 $hint"
                setTextColor(itemView.context.getColor(R.color.glass_text_secondary))
                textSize = 14f
                setPadding(16, 8, 16, 8)
                background = itemView.context.getDrawable(R.drawable.bg_visual_aid_field)
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 4, 0, 4)
                }
            }
        }
    }

    private fun toggleExpansion(position: Int) {
        if (expandedSteps.contains(position)) {
            expandedSteps.remove(position)
        } else {
            expandedSteps.add(position)
        }
        notifyItemChanged(position)
    }

    fun expandAllSteps() {
        expandedSteps.clear()
        expandedSteps.addAll(0 until steps.size)
        notifyDataSetChanged()
    }

    fun collapseAllSteps() {
        expandedSteps.clear()
        notifyDataSetChanged()
    }
}