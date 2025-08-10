package com.playstudio.aiteacher.ui

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import com.playstudio.aiteacher.R
import com.playstudio.aiteacher.databinding.ViewLearningContentBinding
import com.playstudio.aiteacher.models.*
import com.playstudio.aiteacher.ui.adapters.PracticalExamplesAdapter

/**
 * NEW: Learning-focused content view
 * Displays comprehensive educational content like a textbook chapter
 * Replaces the old step-by-step problem-solving approach
 */
class LearningContentView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val binding: ViewLearningContentBinding
    private var currentContent: LearningContent? = null
    private var onInteractiveSessionRequested: ((String) -> Unit)? = null

    init {
        orientation = VERTICAL
        binding = ViewLearningContentBinding.inflate(LayoutInflater.from(context), this, true)
        setupViews()
        Log.d("LearningContentView", "✨ New learning-focused view initialized")
    }

    private fun setupViews() {
        // Setup RecyclerView for practical examples
        binding.practicalExamplesRecyclerView.layoutManager = LinearLayoutManager(context)
        
        // Setup interactive session button
        binding.startInteractiveSessionButton.setOnClickListener {
            currentContent?.topicTitle?.let { topic ->
                onInteractiveSessionRequested?.invoke(topic)
            }
        }
    }

    fun setLearningContent(content: LearningContent) {
        currentContent = content
        displayContent(content)
    }

    private fun displayContent(content: LearningContent) {
        Log.d("LearningContentView", "📚 Displaying comprehensive content: ${content.topicTitle}")
        
        // Header information
        binding.subjectAreaChip.text = content.subjectArea.replaceFirstChar { it.uppercase() }
        binding.readingTimeText.text = "${content.readingTimeMinutes} min read"
        binding.topicTitleText.text = content.topicTitle

        // Introduction section
        setupIntroduction(content.introduction)
        
        // Core content section
        setupCoreContent(content.coreContent)
        
        // Practical examples
        setupPracticalExamples(content.practicalExamples)
        
        // Applications
        setupApplications(content.applications)
        
        Log.d("LearningContentView", "✅ Content display completed")
    }

    private fun setupIntroduction(introduction: Introduction) {
        binding.hookText.text = introduction.hook
        binding.overviewText.text = introduction.overview
        binding.realWorldRelevanceText.text = introduction.realWorldRelevance
    }

    private fun setupCoreContent(coreContent: CoreContent) {
        binding.fundamentalConceptsText.text = coreContent.fundamentalConcepts
        binding.detailedExplanationText.text = coreContent.detailedExplanation
        
        // Setup key principles
        binding.keyPrinciplesContainer.removeAllViews()
        
        // Re-add the title
        val titleView = TextView(context).apply {
            text = "🔑 Key Principles"
            setTextColor(context.getColor(R.color.glass_text_primary))
            textSize = 18f
            setTypeface(null, android.graphics.Typeface.BOLD)
            typeface = resources.getFont(R.font.montserrat)
            val layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            layoutParams.bottomMargin = 24
            this.layoutParams = layoutParams
        }
        binding.keyPrinciplesContainer.addView(titleView)
        
        // Add principles
        coreContent.keyPrinciples.forEach { principle ->
            val principleView = createKeyPrincipleView(principle)
            binding.keyPrinciplesContainer.addView(principleView)
        }
        
        // Advanced concepts (if available)
        coreContent.advancedConcepts?.let { advanced ->
            binding.advancedConceptsText.text = advanced
            binding.advancedConceptsText.visibility = View.VISIBLE
        }
    }

    private fun createKeyPrincipleView(principle: KeyPrinciple): View {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = context.getDrawable(R.drawable.bg_key_principle)
            setPadding(48, 36, 48, 36)
            val layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            layoutParams.bottomMargin = 48
            this.layoutParams = layoutParams
        }

        val principleTitle = TextView(context).apply {
            text = principle.principle
            setTextColor(context.getColor(R.color.glass_text_primary))
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            typeface = resources.getFont(R.font.montserrat)
            val layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            layoutParams.bottomMargin = 24
            this.layoutParams = layoutParams
        }

        val principleExplanation = TextView(context).apply {
            text = principle.explanation
            setTextColor(context.getColor(R.color.glass_text_primary))
            textSize = 14f
            typeface = resources.getFont(R.font.montserrat)
            setLineSpacing(12f, 1f)
            val layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            layoutParams.bottomMargin = 24
            this.layoutParams = layoutParams
        }

        val principleImportance = TextView(context).apply {
            text = "💡 Why this matters: ${principle.importance}"
            setTextColor(context.getColor(R.color.glass_text_secondary))
            textSize = 13f
            typeface = resources.getFont(R.font.montserrat)
            setLineSpacing(12f, 1f)
        }

        container.addView(principleTitle)
        container.addView(principleExplanation)
        container.addView(principleImportance)

        return container
    }

    private fun setupPracticalExamples(examples: List<PracticalExample>) {
        if (examples.isNotEmpty()) {
            val adapter = PracticalExamplesAdapter(examples)
            binding.practicalExamplesRecyclerView.adapter = adapter
        }
    }

    private fun setupApplications(applications: Applications) {
        // Common uses
        binding.commonUsesContainer.removeAllViews()
        
        // Re-add title
        val commonUsesTitle = TextView(context).apply {
            text = "Common Applications:"
            setTextColor(context.getColor(R.color.glass_text_primary))
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            typeface = resources.getFont(R.font.montserrat)
            val layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            layoutParams.bottomMargin = 18
            this.layoutParams = layoutParams
        }
        binding.commonUsesContainer.addView(commonUsesTitle)
        
        applications.commonUses.forEach { use ->
            val useView = createApplicationItem(use)
            binding.commonUsesContainer.addView(useView)
        }

        // Professional applications (if available)
        applications.professionalApplications?.let { professional ->
            if (professional.isNotEmpty()) {
                binding.professionalApplicationsContainer.visibility = View.VISIBLE
                binding.professionalApplicationsContainer.removeAllViews()
                
                val professionalTitle = TextView(context).apply {
                    text = "Professional Use:"
                    setTextColor(context.getColor(R.color.glass_text_primary))
                    textSize = 16f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    typeface = resources.getFont(R.font.montserrat)
                    val layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    layoutParams.bottomMargin = 18
                    this.layoutParams = layoutParams
                }
                binding.professionalApplicationsContainer.addView(professionalTitle)
                
                professional.forEach { app ->
                    val appView = createApplicationItem(app)
                    binding.professionalApplicationsContainer.addView(appView)
                }
            }
        }

        // Everyday relevance
        binding.everydayRelevanceText.text = applications.everydayRelevance
    }

    private fun createApplicationItem(application: String): View {
        return TextView(context).apply {
            text = "• $application"
            setTextColor(context.getColor(R.color.glass_text_primary))
            textSize = 14f
            typeface = resources.getFont(R.font.montserrat)
            setLineSpacing(12f, 1f)
            setPadding(48, 12, 48, 12)
            val layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            layoutParams.bottomMargin = 12
            this.layoutParams = layoutParams
        }
    }

    // Public interface
    fun setOnInteractiveSessionRequestedListener(listener: (String) -> Unit) {
        onInteractiveSessionRequested = listener
    }

    interface OnContentInteractionListener {
        fun onInteractiveSessionRequested(topicTitle: String)
        fun onExampleClicked(example: PracticalExample)
    }

    private var contentInteractionListener: OnContentInteractionListener? = null

    fun setOnContentInteractionListener(listener: OnContentInteractionListener) {
        this.contentInteractionListener = listener
    }
}