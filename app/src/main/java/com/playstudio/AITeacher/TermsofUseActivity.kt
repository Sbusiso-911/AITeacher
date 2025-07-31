package com.playstudio.aiteacher

import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import com.playstudio.aiteacher.databinding.ActivityTermsOfUseBinding
import com.playstudio.aiteacher.R


class TermsOfUseActivity : AppCompatActivity() {
    private lateinit var binding: ActivityTermsOfUseBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTermsOfUseBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Enable the back button in the action bar
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Terms of Use"

        // Set the terms of use text
        binding.termsOfUseText.text = getString(R.string.terms_of_use_text)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                // Handle the back button press
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}