package com.playstudio.aiteacher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

class YourBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        // Handle the broadcast message here
        Toast.makeText(context, "Broadcast received!", Toast.LENGTH_SHORT).show()
    }
}