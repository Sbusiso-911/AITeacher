package com.playstudio.AITeacher.adapters

import android.graphics.drawable.AnimatedVectorDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.playstudio.AITeacher.AIThemeManager
import com.playstudio.aiteacher.R
import com.playstudio.AITeacher.models.AITheme
import com.playstudio.AITeacher.models.ThemeCategory
import kotlinx.coroutines.*

/**
 * Adapter for displaying AI themes in a grid layout with animations and selection handling
 */
class AIThemeAdapter(
    private val themeManager: AIThemeManager,
    private val onThemeSelected: (AITheme) -> Unit,
    private val onThemePreview: (AITheme) -> Unit
) : RecyclerView.Adapter<AIThemeAdapter.ThemeViewHolder>() {
    
    private var themes = listOf<AITheme>()
    private var selectedThemeId: String? = null
    private var showPremiumOnly = false
    private var categoryFilter: ThemeCategory? = null
    private val adapterScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    init {
        loadThemes()
    }
    
    /**
     * Load themes from theme manager
     */
    private fun loadThemes() {
        themes = themeManager.getAllThemes()
        selectedThemeId = themeManager.getCurrentTheme().id
        applyFilters()
    }
    
    /**
     * Apply filters to theme list
     */
    private fun applyFilters() {
        val filteredThemes = themes.filter { theme ->
            val passesCategory = categoryFilter?.let { theme.category == it } ?: true
            val passesPremium = if (showPremiumOnly) theme.isPremium else true
            passesCategory && passesPremium
        }
        
        themes = filteredThemes
        notifyDataSetChanged()
    }
    
    /**
     * Set category filter
     */
    fun setCategoryFilter(category: ThemeCategory?) {
        categoryFilter = category
        applyFilters()
    }
    
    /**
     * Set premium filter
     */
    fun setPremiumFilter(showPremiumOnly: Boolean) {
        this.showPremiumOnly = showPremiumOnly
        applyFilters()
    }
    
    /**
     * Update selected theme
     */
    fun setSelectedTheme(themeId: String) {
        val oldSelection = selectedThemeId
        selectedThemeId = themeId
        
        // Notify changes for old and new selection
        themes.indexOfFirst { it.id == oldSelection }.takeIf { it >= 0 }?.let {
            notifyItemChanged(it)
        }
        themes.indexOfFirst { it.id == themeId }.takeIf { it >= 0 }?.let {
            notifyItemChanged(it)
        }
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ThemeViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_ai_theme_card, parent, false)
        return ThemeViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: ThemeViewHolder, position: Int) {
        holder.bind(themes[position])
    }
    
    override fun getItemCount(): Int = themes.size
    
    /**
     * Clean up resources
     */
    fun cleanup() {
        adapterScope.cancel()
    }
    
    /**
     * ViewHolder for theme items
     */
    inner class ThemeViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        
        private val cardView: CardView = itemView as CardView
        private val imageViewPreview: ImageView = itemView.findViewById(R.id.imageViewThemePreview)
        private val textViewName: TextView = itemView.findViewById(R.id.textViewThemeName)
        private val textViewCategory: TextView = itemView.findViewById(R.id.textViewThemeCategory)
        private val layoutPremiumBadge: LinearLayout = itemView.findViewById(R.id.layoutPremiumBadge)
        private val viewSelectionIndicator: View = itemView.findViewById(R.id.viewSelectionIndicator)
        private val imageViewSelected: ImageView = itemView.findViewById(R.id.imageViewSelected)
        private val layoutAnimationIndicator: LinearLayout = itemView.findViewById(R.id.layoutAnimationIndicator)
        private val viewAnimationDot: View = itemView.findViewById(R.id.viewAnimationDot)
        private val progressBarLoading: ProgressBar = itemView.findViewById(R.id.progressBarThemeLoading)
        
        private var currentTheme: AITheme? = null
        private var animationJob: Job? = null
        
        fun bind(theme: AITheme) {
            currentTheme = theme
            
            // Set theme info
            textViewName.text = theme.name
            textViewCategory.text = getCategoryDisplayName(theme.category)
            
            // Show/hide premium badge
            layoutPremiumBadge.visibility = if (theme.isPremium) View.VISIBLE else View.GONE
            
            // Show/hide selection indicator
            val isSelected = theme.id == selectedThemeId
            viewSelectionIndicator.visibility = if (isSelected) View.VISIBLE else View.GONE
            imageViewSelected.visibility = if (isSelected) View.VISIBLE else View.GONE
            
            // Show/hide animation indicator
            layoutAnimationIndicator.visibility = if (theme.isAnimated) View.VISIBLE else View.GONE
            if (theme.isAnimated) {
                updateAnimationDot(theme)
            }
            
            // Load theme preview
            loadThemePreview(theme)
            
            // Set click listeners
            cardView.setOnClickListener {
                onThemeSelected(theme)
                setSelectedTheme(theme.id)
            }
            
            cardView.setOnLongClickListener {
                onThemePreview(theme)
                true
            }
            
            // Apply theme colors to card
            applyThemeColors(theme)
        }
        
        /**
         * Load theme preview image
         */
        private fun loadThemePreview(theme: AITheme) {
            progressBarLoading.visibility = View.VISIBLE
            imageViewPreview.visibility = View.GONE
            
            adapterScope.launch {
                try {
                    val drawable = themeManager.getThemeDrawable(theme, animated = false)
                    
                    withContext(Dispatchers.Main) {
                        progressBarLoading.visibility = View.GONE
                        imageViewPreview.visibility = View.VISIBLE
                        imageViewPreview.setImageDrawable(drawable)
                        
                        // Start animation if theme supports it and is selected
                        if (theme.isAnimated && theme.id == selectedThemeId) {
                            startPreviewAnimation(theme)
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        progressBarLoading.visibility = View.GONE
                        imageViewPreview.visibility = View.VISIBLE
                        // Set fallback image
                        imageViewPreview.setImageResource(R.drawable.ic_ai)
                    }
                }
            }
        }
        
        /**
         * Start preview animation for selected theme
         */
        private fun startPreviewAnimation(theme: AITheme) {
            animationJob?.cancel()
            
            if (theme.id == selectedThemeId && theme.isAnimated) {
                animationJob = adapterScope.launch {
                    delay(500) // Brief delay before starting animation
                    
                    withContext(Dispatchers.Main) {
                        val animatedDrawable = themeManager.getThemeDrawable(theme, animated = true)
                        imageViewPreview.setImageDrawable(animatedDrawable)
                        
                        if (animatedDrawable is AnimatedVectorDrawable) {
                            animatedDrawable.start()
                        }
                    }
                }
            }
        }
        
        /**
         * Update animation dot color based on theme
         */
        private fun updateAnimationDot(theme: AITheme) {
            val dotColor = when (theme.category) {
                ThemeCategory.CYBERPUNK -> R.color.cyber_primary
                ThemeCategory.NEURAL -> R.color.brain_primary
                ThemeCategory.QUANTUM -> R.color.quantum_primary
                ThemeCategory.ORGANIC -> R.color.forest_primary
            }
            
            viewAnimationDot.backgroundTintList = 
                itemView.context.getColorStateList(dotColor)
        }
        
        /**
         * Apply theme colors to card elements
         */
        private fun applyThemeColors(theme: AITheme) {
            val primaryColor = themeManager.getThemeColor(theme, "primary")
            val accentColor = themeManager.getThemeColor(theme, "accent")
            
            // Update selection indicator color
            viewSelectionIndicator.backgroundTintList = 
                itemView.context.getColorStateList(theme.colors.primary)
                
            // Update selected check color
            imageViewSelected.imageTintList = 
                itemView.context.getColorStateList(theme.colors.primary)
        }
        
        /**
         * Get display name for theme category
         */
        private fun getCategoryDisplayName(category: ThemeCategory): String {
            return when (category) {
                ThemeCategory.CYBERPUNK -> "🤖 Cyberpunk"
                ThemeCategory.NEURAL -> "🧠 Neural"
                ThemeCategory.QUANTUM -> "⚛️ Quantum"
                ThemeCategory.ORGANIC -> "🌿 Organic"
            }
        }
        
        /**
         * Stop any running animations
         */
        fun stopAnimations() {
            animationJob?.cancel()
            
            val drawable = imageViewPreview.drawable
            if (drawable is AnimatedVectorDrawable) {
                drawable.stop()
            }
        }
    }
    
    /**
     * Stop all animations when adapter is detached
     */
    override fun onViewRecycled(holder: ThemeViewHolder) {
        super.onViewRecycled(holder)
        holder.stopAnimations()
    }
}

/**
 * Extension functions for theme filtering
 */
fun List<AITheme>.filterByCategory(category: ThemeCategory?): List<AITheme> {
    return if (category != null) {
        filter { it.category == category }
    } else {
        this
    }
}

fun List<AITheme>.filterByPremium(premiumOnly: Boolean): List<AITheme> {
    return if (premiumOnly) {
        filter { it.isPremium }
    } else {
        this
    }
}

fun List<AITheme>.filterByAnimated(animatedOnly: Boolean): List<AITheme> {
    return if (animatedOnly) {
        filter { it.isAnimated }
    } else {
        this
    }
}