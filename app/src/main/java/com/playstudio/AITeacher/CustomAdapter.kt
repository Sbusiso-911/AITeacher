package com.playstudio.aiteacher

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView

class CustomAdapter(context: Context, private val resource: Int, private val items: Array<String>) :
    ArrayAdapter<String>(context, resource, items) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view: View = convertView ?: LayoutInflater.from(context).inflate(resource, parent, false)
        val textView: TextView = view.findViewById(android.R.id.text1)
        textView.text = items[position]
        textView.setTextColor(context.resources.getColor(android.R.color.white, null))
        return view
    }
}