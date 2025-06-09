package com.playstudio.aiteacher

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Button
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment

class VoiceFeaturesDialog : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_voice_features, null)
        view.findViewById<Button>(R.id.btn_close_voice).setOnClickListener { dismiss() }
        return AlertDialog.Builder(requireContext())
            .setView(view)
            .create()
    }
}
