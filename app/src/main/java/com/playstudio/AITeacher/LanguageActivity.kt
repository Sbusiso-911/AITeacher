// src/main/java/com/example/AIChatTeacher/LanguagesActivity.kt
package com.playstudio.aiteacher

import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import com.playstudio.aiteacher.R


class LanguagesActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_languages)

         //Enable the back button in the action bar
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

   override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                //Handle the back button press
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}