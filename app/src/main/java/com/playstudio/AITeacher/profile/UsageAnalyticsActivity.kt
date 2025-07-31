package com.playstudio.aiteacher.profile

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.playstudio.aiteacher.R

class UsageAnalyticsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_usage_analytics)
        
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = "Usage Analytics"
        }
    }
    
    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}