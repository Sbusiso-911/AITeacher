package com.playstudio.aiteacher.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.playstudio.aiteacher.R
import com.playstudio.aiteacher.models.Formula

class FormulasAdapter(
    private val formulas: List<Formula>,
    private val onFormulaClick: (Formula) -> Unit
) : RecyclerView.Adapter<FormulasAdapter.FormulaViewHolder>() {

    inner class FormulaViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameText: TextView = itemView.findViewById(R.id.formulaName)
        private val latexText: TextView = itemView.findViewById(R.id.formulaLatex)
        private val descriptionText: TextView = itemView.findViewById(R.id.formulaDescription)

        fun bind(formula: Formula) {
            nameText.text = formula.name
            latexText.text = formula.latex
            descriptionText.text = formula.description
            
            itemView.setOnClickListener {
                onFormulaClick(formula)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FormulaViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_formula, parent, false)
        return FormulaViewHolder(view)
    }

    override fun onBindViewHolder(holder: FormulaViewHolder, position: Int) {
        holder.bind(formulas[position])
    }

    override fun getItemCount(): Int = formulas.size
}