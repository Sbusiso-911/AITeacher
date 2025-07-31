package com.playstudio.aiteacher

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

class ThemeSelectionDialog(
    context: Context,
    private val currentTheme: ThemeManager.AITheme,
    private val onThemeSelected: (ThemeManager.AITheme) -> Unit
) : Dialog(context) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_theme_selection, null)
        setContentView(view)
        
        val recyclerView = view.findViewById<RecyclerView>(R.id.themesRecyclerView)
        recyclerView.layoutManager = GridLayoutManager(context, 2)
        
        val adapter = ThemeAdapter(ThemeManager.AITheme.values().toList(), currentTheme) { theme ->
            onThemeSelected(theme)
            dismiss()
        }
        recyclerView.adapter = adapter
        
        view.findViewById<View>(R.id.btnCancel).setOnClickListener { dismiss() }
    }
}

class ThemeAdapter(
    private val themes: List<ThemeManager.AITheme>,
    private val currentTheme: ThemeManager.AITheme,
    private val onThemeClick: (ThemeManager.AITheme) -> Unit
) : RecyclerView.Adapter<ThemeAdapter.ThemeViewHolder>() {

    class ThemeViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val preview: ImageView = itemView.findViewById(R.id.themePreview)
        val title: TextView = itemView.findViewById(R.id.themeTitle)
        val selectedIndicator: View = itemView.findViewById(R.id.selectedIndicator)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ThemeViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_theme_selection, parent, false)
        return ThemeViewHolder(view)
    }

    override fun onBindViewHolder(holder: ThemeViewHolder, position: Int) {
        val theme = themes[position]
        
        holder.title.text = theme.themeName
        try {
            holder.preview.setBackgroundResource(theme.drawableRes)
        } catch (e: Exception) {
            // If theme drawable fails to load, show a placeholder
            holder.preview.setBackgroundColor(holder.itemView.context.getColor(android.R.color.darker_gray))
        }
        holder.selectedIndicator.visibility = if (theme == currentTheme) View.VISIBLE else View.GONE
        
        holder.itemView.setOnClickListener { onThemeClick(theme) }
    }

    override fun getItemCount() = themes.size
}