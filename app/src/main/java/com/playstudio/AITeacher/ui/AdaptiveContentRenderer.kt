package com.playstudio.aiteacher.ui

import android.content.Context
import android.text.Html
import android.text.method.LinkMovementMethod
import android.text.util.Linkify
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.playstudio.aiteacher.R
import java.util.regex.Pattern

/**
 * Adaptive content renderer that dynamically creates UI based on content structure
 * rather than forcing rigid predefined layouts
 */
class AdaptiveContentRenderer(private val context: Context) {

    companion object {
        private const val TAG = "AdaptiveContentRenderer"
    }

    /**
     * Parse markdown-style content and render appropriate UI components
     */
    fun renderContent(content: String, container: LinearLayout) {
        Log.d(TAG, "🎨 Rendering content (${content.length} chars) to container")
        container.removeAllViews()
        
        try {
            // Parse content into structured sections
            val sections = parseContentSections(content)
            Log.d(TAG, "🎨 Parsed ${sections.size} sections")
            
            // Render each section with appropriate styling
            sections.forEachIndexed { index, section ->
                Log.d(TAG, "🎨 Rendering section $index: ${section.type}")
                when (section.type) {
                    SectionType.HEADING -> renderHeading(section, container)
                    SectionType.PARAGRAPH -> renderParagraph(section, container)
                    SectionType.LIST -> renderList(section, container)
                    SectionType.CODE_BLOCK -> renderCodeBlock(section, container)
                    SectionType.QUOTE -> renderQuote(section, container)
                    SectionType.MATH -> renderMathBlock(section, container)
                    SectionType.STEPS -> renderStepByStep(section, container)
                }
            }
            
            Log.d(TAG, "🎨 Finished rendering ${sections.size} sections to container (child count: ${container.childCount})")
        } catch (e: Exception) {
            Log.e(TAG, "🎨 Error rendering adaptive content", e)
            // Fallback to simple text rendering
            renderFallbackContent(content, container)
        }
    }

    private fun parseContentSections(content: String): List<ContentSection> {
        val sections = mutableListOf<ContentSection>()
        val lines = content.split("\n")
        var currentSection: StringBuilder? = null
        var currentType: SectionType? = null
        
        for (line in lines) {
            val trimmedLine = line.trim()
            
            when {
                // Headings
                trimmedLine.startsWith("# ") -> {
                    finishCurrentSection(sections, currentSection, currentType)
                    currentSection = StringBuilder(trimmedLine.substring(2))
                    currentType = SectionType.HEADING
                }
                trimmedLine.startsWith("## ") -> {
                    finishCurrentSection(sections, currentSection, currentType)
                    currentSection = StringBuilder(trimmedLine.substring(3))
                    currentType = SectionType.HEADING
                }
                
                // Code blocks
                trimmedLine.startsWith("```") -> {
                    if (currentType == SectionType.CODE_BLOCK) {
                        finishCurrentSection(sections, currentSection, currentType)
                        currentSection = null
                        currentType = null
                    } else {
                        finishCurrentSection(sections, currentSection, currentType)
                        currentSection = StringBuilder()
                        currentType = SectionType.CODE_BLOCK
                    }
                }
                
                // Lists
                trimmedLine.startsWith("- ") || trimmedLine.startsWith("* ") -> {
                    if (currentType != SectionType.LIST) {
                        finishCurrentSection(sections, currentSection, currentType)
                        currentSection = StringBuilder()
                        currentType = SectionType.LIST
                    }
                    currentSection?.append(trimmedLine)?.append("\n")
                }
                
                // Numbered lists/steps
                trimmedLine.matches(Regex("^\\d+\\.\\s.*")) -> {
                    if (currentType != SectionType.STEPS) {
                        finishCurrentSection(sections, currentSection, currentType)
                        currentSection = StringBuilder()
                        currentType = SectionType.STEPS
                    }
                    currentSection?.append(trimmedLine)?.append("\n")
                }
                
                // Quotes
                trimmedLine.startsWith("> ") -> {
                    if (currentType != SectionType.QUOTE) {
                        finishCurrentSection(sections, currentSection, currentType)
                        currentSection = StringBuilder()
                        currentType = SectionType.QUOTE
                    }
                    currentSection?.append(trimmedLine.substring(2))?.append("\n")
                }
                
                // Math expressions
                trimmedLine.contains(Regex("[∫∑√π∞≠≤≥±×÷]")) || 
                trimmedLine.contains(Regex("\\$.*\\$")) -> {
                    if (currentType != SectionType.MATH) {
                        finishCurrentSection(sections, currentSection, currentType)
                        currentSection = StringBuilder()
                        currentType = SectionType.MATH
                    }
                    currentSection?.append(trimmedLine)?.append("\n")
                }
                
                // Empty lines
                trimmedLine.isEmpty() -> {
                    if (currentType == SectionType.PARAGRAPH) {
                        finishCurrentSection(sections, currentSection, currentType)
                        currentSection = null
                        currentType = null
                    }
                }
                
                // Regular paragraphs
                else -> {
                    if (currentType != SectionType.PARAGRAPH && currentType != SectionType.CODE_BLOCK &&
                        currentType != SectionType.LIST && currentType != SectionType.STEPS && 
                        currentType != SectionType.QUOTE && currentType != SectionType.MATH) {
                        finishCurrentSection(sections, currentSection, currentType)
                        currentSection = StringBuilder()
                        currentType = SectionType.PARAGRAPH
                    }
                    
                    if (currentType != SectionType.HEADING) {
                        if (currentSection?.isNotEmpty() == true) {
                            currentSection.append(" ")
                        }
                        currentSection?.append(trimmedLine)
                    }
                }
            }
        }
        
        // Finish the last section
        finishCurrentSection(sections, currentSection, currentType)
        
        return sections
    }
    
    private fun finishCurrentSection(
        sections: MutableList<ContentSection>,
        currentSection: StringBuilder?,
        currentType: SectionType?
    ) {
        if (currentSection != null && currentType != null && currentSection.isNotEmpty()) {
            sections.add(ContentSection(currentType, currentSection.toString().trim()))
        }
    }

    private fun renderHeading(section: ContentSection, container: LinearLayout) {
        val textView = TextView(context).apply {
            text = section.content
            textSize = 20f
            setTextColor(ContextCompat.getColor(context, R.color.glass_text_primary))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(0, dpToPx(16), 0, dpToPx(8))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        container.addView(textView)
    }

    private fun renderParagraph(section: ContentSection, container: LinearLayout) {
        val textView = TextView(context).apply {
            // Convert plain URLs to clickable HTML links
            val contentWithLinks = convertUrlsToHtml(section.content)
            text = Html.fromHtml(contentWithLinks, Html.FROM_HTML_MODE_COMPACT)
            textSize = 16f
            setTextColor(ContextCompat.getColor(context, R.color.glass_text_primary))
            movementMethod = LinkMovementMethod.getInstance()
            setPadding(0, dpToPx(4), 0, dpToPx(8))
            setLineSpacing(dpToPx(2).toFloat(), 1.0f)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            // Enable auto-link detection as backup
            autoLinkMask = Linkify.WEB_URLS
        }
        container.addView(textView)
    }

    private fun renderList(section: ContentSection, container: LinearLayout) {
        val listContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(16), dpToPx(4), 0, dpToPx(8))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        
        section.content.split("\n").forEach { item ->
            if (item.trim().isNotEmpty()) {
                val textView = TextView(context).apply {
                    val cleanItem = item.trim().removePrefix("- ").removePrefix("* ")
                    val itemWithLinks = convertUrlsToHtml("• $cleanItem")
                    text = Html.fromHtml(itemWithLinks, Html.FROM_HTML_MODE_COMPACT)
                    textSize = 16f
                    setTextColor(ContextCompat.getColor(context, R.color.glass_text_primary))
                    movementMethod = LinkMovementMethod.getInstance()
                    autoLinkMask = Linkify.WEB_URLS
                    setPadding(0, dpToPx(2), 0, dpToPx(2))
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                }
                listContainer.addView(textView)
            }
        }
        
        container.addView(listContainer)
    }

    private fun renderStepByStep(section: ContentSection, container: LinearLayout) {
        val stepsContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(8))
            background = ContextCompat.getDrawable(context, R.drawable.rounded_corners_light)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        
        section.content.split("\n").forEach { step ->
            if (step.trim().isNotEmpty()) {
                val textView = TextView(context).apply {
                    text = step.trim()
                    textSize = 16f
                    setTextColor(ContextCompat.getColor(context, R.color.glass_text_primary))
                    setPadding(dpToPx(8), dpToPx(6), dpToPx(8), dpToPx(6))
                    background = ContextCompat.getDrawable(context, R.drawable.rounded_corners)
                }
                
                val layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, dpToPx(4), 0, dpToPx(4))
                }
                textView.layoutParams = layoutParams
                stepsContainer.addView(textView)
            }
        }
        
        container.addView(stepsContainer)
    }

    private fun renderCodeBlock(section: ContentSection, container: LinearLayout) {
        val codeView = TextView(context).apply {
            text = section.content
            textSize = 14f
            setTextColor(ContextCompat.getColor(context, R.color.glass_text_primary))
            background = ContextCompat.getDrawable(context, R.drawable.rounded_corners_grey)
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(12))
        }
        
        val layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(0, dpToPx(8), 0, dpToPx(8))
        }
        codeView.layoutParams = layoutParams
        container.addView(codeView)
    }

    private fun renderQuote(section: ContentSection, container: LinearLayout) {
        val quoteView = TextView(context).apply {
            text = "\"${section.content}\""
            textSize = 16f
            setTextColor(ContextCompat.getColor(context, R.color.glass_text_secondary))
            setTypeface(typeface, android.graphics.Typeface.ITALIC)
            setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(8))
            background = ContextCompat.getDrawable(context, R.drawable.rounded_corners_light)
        }
        
        val layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
        }
        quoteView.layoutParams = layoutParams
        container.addView(quoteView)
    }

    private fun renderMathBlock(section: ContentSection, container: LinearLayout) {
        val mathView = TextView(context).apply {
            text = section.content
            textSize = 18f
            setTextColor(ContextCompat.getColor(context, R.color.glass_text_primary))
            background = ContextCompat.getDrawable(context, R.drawable.rounded_corners_light)
            gravity = android.view.Gravity.CENTER
            setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(12))
        }
        
        val layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(0, dpToPx(8), 0, dpToPx(8))
        }
        mathView.layoutParams = layoutParams
        container.addView(mathView)
    }

    private fun renderFallbackContent(content: String, container: LinearLayout) {
        val textView = TextView(context).apply {
            val contentWithLinks = convertUrlsToHtml(content)
            text = Html.fromHtml(contentWithLinks, Html.FROM_HTML_MODE_COMPACT)
            textSize = 16f
            setTextColor(ContextCompat.getColor(context, R.color.glass_text_primary))
            movementMethod = LinkMovementMethod.getInstance()
            autoLinkMask = Linkify.WEB_URLS
            setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
            setLineSpacing(dpToPx(4).toFloat(), 1.0f)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        container.addView(textView)
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }

    /**
     * Convert plain text URLs to clickable HTML links
     */
    private fun convertUrlsToHtml(text: String): String {
        // Simplified pattern to match URLs
        val urlPattern = Pattern.compile(
            "(https?://[\\w\\-]+(\\.[\\w\\-]+)+([\\w\\-\\.,@?^=%&:/~\\+#]*[\\w\\-\\@?^=%&/~\\+#])?)|" +
            "(www\\.[\\w\\-]+(\\.[\\w\\-]+)+([\\w\\-\\.,@?^=%&:/~\\+#]*[\\w\\-\\@?^=%&/~\\+#])?)",
            Pattern.CASE_INSENSITIVE
        )
        
        var result = text
        val matcher = urlPattern.matcher(text)
        
        // Use a set to track URLs we've already processed to avoid duplicates
        val processedUrls = mutableSetOf<String>()
        
        while (matcher.find()) {
            val url = matcher.group()
            if (url !in processedUrls) {
                processedUrls.add(url)
                
                // Add protocol if missing
                val fullUrl = if (url.startsWith("http://") || url.startsWith("https://")) {
                    url
                } else {
                    "https://$url"
                }
                
                // Convert to HTML link if not already wrapped
                if (!result.contains("<a href=\"$fullUrl\">")) {
                    result = result.replace(url, "<a href=\"$fullUrl\">$url</a>")
                }
            }
        }
        
        return result
    }

    data class ContentSection(
        val type: SectionType,
        val content: String
    )

    enum class SectionType {
        HEADING,
        PARAGRAPH,
        LIST,
        STEPS,
        CODE_BLOCK,
        QUOTE,
        MATH
    }
}