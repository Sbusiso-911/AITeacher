package com.playstudio.aiteacher.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.playstudio.aiteacher.R
import com.playstudio.aiteacher.models.Example

class ExamplesAdapter(
    private val examples: List<Example>,
    private val onExampleClick: (Example) -> Unit
) : RecyclerView.Adapter<ExamplesAdapter.ExampleViewHolder>() {

    inner class ExampleViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val titleText: TextView = itemView.findViewById(R.id.exampleTitle)
        private val descriptionText: TextView = itemView.findViewById(R.id.exampleDescription)
        private val solutionText: TextView = itemView.findViewById(R.id.exampleSolution)

        fun bind(example: Example) {
            titleText.text = example.title
            descriptionText.text = example.description
            solutionText.text = example.solution
            
            itemView.setOnClickListener {
                onExampleClick(example)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ExampleViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_example, parent, false)
        return ExampleViewHolder(view)
    }

    override fun onBindViewHolder(holder: ExampleViewHolder, position: Int) {
        holder.bind(examples[position])
    }

    override fun getItemCount(): Int = examples.size
}