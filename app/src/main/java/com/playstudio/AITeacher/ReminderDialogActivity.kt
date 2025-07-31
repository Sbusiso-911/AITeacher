package com.playstudio.aiteacher

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class ReminderDialogActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.reminder_dialog)

        val reminderMessage: TextView = findViewById(R.id.reminder_message)
        val askNowButton: Button = findViewById(R.id.ask_now_button)

        // Retrieve the reminder message from the intent and set it in the TextView
        val message = intent.getStringExtra("reminder_message")
        reminderMessage.text = message

        askNowButton.setOnClickListener {
            val intent = Intent(this, ChatActivity::class.java)
            startActivity(intent)
            finish()
        }
    }

    override fun onBackPressed() {
        // Prevent the user from dismissing the dialog by pressing the back button
        moveTaskToBack(true)
        // Call the superclass method to satisfy the requirement
        super.onBackPressed()
    }
}