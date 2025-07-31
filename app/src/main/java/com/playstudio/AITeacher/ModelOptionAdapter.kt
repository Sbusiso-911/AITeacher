package com.playstudio.aiteacher

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import com.playstudio.aiteacher.R

class ModelOptionAdapter(context: Context, private val options: Array<String>, private val descriptions: Array<String>) :
    ArrayAdapter<String>(context, R.layout.list_item_model_option, options) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.list_item_model_option, parent, false)
        val modelNameTextView = view.findViewById<TextView>(R.id.modelNameTextView)
        val modelDescriptionTextView = view.findViewById<TextView>(R.id.modelDescriptionTextView)
        modelNameTextView.text = options[position]
        modelDescriptionTextView.text = descriptions[position]
        return view
    }
}