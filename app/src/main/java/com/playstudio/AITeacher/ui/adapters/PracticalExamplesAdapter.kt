package com.playstudio.aiteacher.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.playstudio.aiteacher.R
import com.playstudio.aiteacher.models.PracticalExample

/**
 * Adapter for displaying practical examples in the new learning-focused approach
 * Shows real-world contexts and applications
 */
class PracticalExamplesAdapter(
    private val examples: List<PracticalExample>,
    private val onExampleClicked: ((PracticalExample) -> Unit)? = null
) : RecyclerView.Adapter<PracticalExamplesAdapter.ExampleViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ExampleViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_practical_example, parent, false)
        return ExampleViewHolder(view)
    }

    override fun onBindViewHolder(holder: ExampleViewHolder, position: Int) {
        holder.bind(examples[position])
    }

    override fun getItemCount(): Int = examples.size

    inner class ExampleViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val titleText: TextView = itemView.findViewById(R.id.exampleTitleText)
        private val contextText: TextView = itemView.findViewById(R.id.contextText)
        private val applicationText: TextView = itemView.findViewById(R.id.applicationText)
        private val outcomeText: TextView = itemView.findViewById(R.id.outcomeText)

        fun bind(example: PracticalExample) {
            titleText.text = example.exampleTitle
            contextText.text = example.context
            applicationText.text = example.application
            outcomeText.text = example.outcome

            itemView.setOnClickListener {
                onExampleClicked?.invoke(example)
            }
        }
    }
}