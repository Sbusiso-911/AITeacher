package com.playstudio.aiteacher.ui

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import com.playstudio.aiteacher.R
import com.playstudio.aiteacher.databinding.ViewSecurityAnalysisBinding
import com.playstudio.aiteacher.models.*

/**
 * Simplified view for displaying security analysis in clear problem-solution format
 * Designed to make users say "Oh, I get it!" instead of "What is this?"
 */
class SecurityAnalysisView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val binding: ViewSecurityAnalysisBinding
    private var currentAnalysis: SecurityAnalysisResponse? = null

    init {
        orientation = VERTICAL
        binding = ViewSecurityAnalysisBinding.inflate(LayoutInflater.from(context), this, true)
    }

    fun setSecurityAnalysis(analysis: SecurityAnalysisResponse) {
        currentAnalysis = analysis
        displayAnalysis(analysis)
    }

    private fun displayAnalysis(analysis: SecurityAnalysisResponse) {
        // Clear and setup
        clearAllViews()
        
        // Analysis type header
        setupAnalysisHeader(analysis.analysisType)
        
        // THE PROBLEM section - What was broken?
        setupProblemSection(analysis.problem)
        
        // THE SOLUTION section - What did we fix?
        setupSolutionSection(analysis.solution)
        
        // THE RESULT section - What works now?
        setupResultSection(analysis.result)
        
        // NEXT ACTION section - What should user do? (optional)
        analysis.nextAction?.let { setupActionSection(it) }
        
        // Make everything visible
        binding.root.visibility = View.VISIBLE
    }

    private fun setupAnalysisHeader(analysisType: SecurityAnalysisType) {
        val title = when (analysisType) {
            SecurityAnalysisType.ACCOUNT_SECURITY -> "Account Security Analysis"
            SecurityAnalysisType.DATA_PROTECTION -> "Data Protection Analysis" 
            SecurityAnalysisType.NETWORK_SECURITY -> "Network Security Analysis"
            SecurityAnalysisType.SYSTEM_VULNERABILITY -> "System Vulnerability Analysis"
        }
        
        binding.analysisTypeTitle.text = title
        binding.analysisTypeTitle.visibility = View.VISIBLE
    }

    private fun setupProblemSection(problem: SecurityProblem) {
        // Problem section header
        binding.problemSectionTitle.text = "THE PROBLEM"
        binding.problemSectionTitle.visibility = View.VISIBLE
        
        // Problem title with severity indicator
        binding.problemTitle.text = problem.title
        binding.problemTitle.visibility = View.VISIBLE
        
        // Severity badge
        setupSeverityBadge(problem.severity)
        
        // Problem description in simple language
        binding.problemDescription.text = problem.description
        binding.problemDescription.visibility = View.VISIBLE
        
        // Impact explanation  
        binding.problemImpact.text = "Impact: ${problem.impact}"
        binding.problemImpact.visibility = View.VISIBLE
        
        binding.problemSection.visibility = View.VISIBLE
    }

    private fun setupSeverityBadge(severity: SecuritySeverity) {
        val (text, color) = when (severity) {
            SecuritySeverity.LOW -> "Low Risk" to R.color.severity_low
            SecuritySeverity.MEDIUM -> "Medium Risk" to R.color.severity_medium  
            SecuritySeverity.HIGH -> "High Risk" to R.color.severity_high
            SecuritySeverity.CRITICAL -> "Critical Risk" to R.color.severity_critical
        }
        
        binding.severityBadge.text = text
        binding.severityBadge.setBackgroundColor(ContextCompat.getColor(context, color))
        binding.severityBadge.visibility = View.VISIBLE
    }

    private fun setupSolutionSection(solution: SecuritySolution) {
        // Solution section header
        binding.solutionSectionTitle.text = "THE SOLUTION"
        binding.solutionSectionTitle.visibility = View.VISIBLE
        
        // Solution title
        binding.solutionTitle.text = solution.title
        binding.solutionTitle.visibility = View.VISIBLE
        
        // What we did description
        binding.solutionDescription.text = solution.description
        binding.solutionDescription.visibility = View.VISIBLE
        
        // How it works explanation
        binding.solutionHowItWorks.text = "How it works: ${solution.howItWorks}"
        binding.solutionHowItWorks.visibility = View.VISIBLE
        
        binding.solutionSection.visibility = View.VISIBLE
    }

    private fun setupResultSection(result: SecurityResult) {
        // Result section header
        binding.resultSectionTitle.text = "THE RESULT"
        binding.resultSectionTitle.visibility = View.VISIBLE
        
        // Before/After comparison
        binding.resultBefore.text = "Before: ${result.before}"
        binding.resultBefore.visibility = View.VISIBLE
        
        binding.resultAfter.text = "Now: ${result.after}"
        binding.resultAfter.visibility = View.VISIBLE
        
        // Clear outcome
        binding.resultOutcome.text = result.outcome
        binding.resultOutcome.visibility = View.VISIBLE
        
        binding.resultSection.visibility = View.VISIBLE
    }

    private fun setupActionSection(action: SecurityAction) {
        // Action section header
        binding.actionSectionTitle.text = if (action.isUrgent) "URGENT ACTION NEEDED" else "RECOMMENDED ACTION"
        binding.actionSectionTitle.visibility = View.VISIBLE
        
        // Set urgent styling if needed
        if (action.isUrgent) {
            binding.actionSectionTitle.setTextColor(ContextCompat.getColor(context, R.color.severity_high))
            binding.actionSection.setBackgroundResource(R.drawable.urgent_action_background)
        } else {
            binding.actionSection.setBackgroundResource(R.drawable.recommended_action_background)
        }
        
        // Action title
        binding.actionTitle.text = action.title
        binding.actionTitle.visibility = View.VISIBLE
        
        // Action description
        binding.actionDescription.text = action.description
        binding.actionDescription.visibility = View.VISIBLE
        
        binding.actionSection.visibility = View.VISIBLE
    }

    private fun clearAllViews() {
        // Hide all sections initially
        binding.analysisTypeTitle.visibility = View.GONE
        binding.problemSection.visibility = View.GONE
        binding.solutionSection.visibility = View.GONE
        binding.resultSection.visibility = View.GONE
        binding.actionSection.visibility = View.GONE
        
        // Hide all individual elements
        listOf(
            binding.problemSectionTitle, binding.problemTitle, binding.severityBadge,
            binding.problemDescription, binding.problemImpact,
            binding.solutionSectionTitle, binding.solutionTitle, 
            binding.solutionDescription, binding.solutionHowItWorks,
            binding.resultSectionTitle, binding.resultBefore, 
            binding.resultAfter, binding.resultOutcome,
            binding.actionSectionTitle, binding.actionTitle, binding.actionDescription
        ).forEach { it.visibility = View.GONE }
    }

    /**
     * Get the current analysis for external access
     */
    fun getCurrentAnalysis(): SecurityAnalysisResponse? = currentAnalysis

    /**
     * Interface for handling user interactions with the security analysis
     */
    interface OnSecurityActionListener {
        fun onActionClicked(action: SecurityAction)
        fun onAnalysisExpanded(analysisType: SecurityAnalysisType)
    }

    private var actionListener: OnSecurityActionListener? = null

    fun setOnSecurityActionListener(listener: OnSecurityActionListener) {
        this.actionListener = listener
    }
}