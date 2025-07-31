package com.playstudio.aiteacher.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.playstudio.aiteacher.R
import com.playstudio.aiteacher.models.CodeSnippet

class CodeSnippetsAdapter(
    private val codeSnippets: List<CodeSnippet>,
    private val onCodeClick: (CodeSnippet) -> Unit
) : RecyclerView.Adapter<CodeSnippetsAdapter.CodeViewHolder>() {

    inner class CodeViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val titleText: TextView = itemView.findViewById(R.id.codeTitle)
        private val languageText: TextView = itemView.findViewById(R.id.codeLanguage)
        private val codeText: TextView = itemView.findViewById(R.id.codeContent)
        private val explanationText: TextView = itemView.findViewById(R.id.codeExplanation)

        fun bind(codeSnippet: CodeSnippet) {
            titleText.text = codeSnippet.title
            languageText.text = codeSnippet.language
            codeText.text = codeSnippet.code
            explanationText.text = codeSnippet.explanation
            
            itemView.setOnClickListener {
                onCodeClick(codeSnippet)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CodeViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_code_snippet, parent, false)
        return CodeViewHolder(view)
    }

    override fun onBindViewHolder(holder: CodeViewHolder, position: Int) {
        holder.bind(codeSnippets[position])
    }

    override fun getItemCount(): Int = codeSnippets.size
}