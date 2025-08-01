package com.playstudio.aiteacher

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment

class VoiceFeaturesDialog : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_promotion, null)
        view.findViewById<TextView>(R.id.promotionMessage).text = getString(R.string.voice_features_dialog_message)
        view.findViewById<Button>(R.id.btnClose).setOnClickListener { dismiss() }
        return AlertDialog.Builder(requireContext())
            .setView(view)
            .create()
    }
}
