package com.playstudio.aiteacher

import android.os.Bundle
import android.widget.Button
import android.widget.RadioGroup
import androidx.appcompat.app.AppCompatActivity

class FontSelectionActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_font_selection)

        val radioGroupFonts = findViewById<RadioGroup>(R.id.radioGroupFonts)
        val btnSave = findViewById<Button>(R.id.btnSave)

        // Set the currently selected radio button based on user preference
        val selectedFont = FontManager.getFont(this)
        when (selectedFont) {
            FontManager.FONT1 -> radioGroupFonts.check(R.id.radioFont1)
            FontManager.FONT2 -> radioGroupFonts.check(R.id.radioFont2)
            FontManager.FONT3 -> radioGroupFonts.check(R.id.radioFont3)
            FontManager.FONT4 -> radioGroupFonts.check(R.id.radioFont4)
            FontManager.FONT5 -> radioGroupFonts.check(R.id.radioFont5)
        }

        // Save the selected font and return result when the save button is clicked
        btnSave.setOnClickListener {
            val selectedId = radioGroupFonts.checkedRadioButtonId
            when (selectedId) {
                R.id.radioFont1 -> FontManager.setFont(this, FontManager.FONT1)
                R.id.radioFont2 -> FontManager.setFont(this, FontManager.FONT2)
                R.id.radioFont3 -> FontManager.setFont(this, FontManager.FONT3)
                R.id.radioFont4 -> FontManager.setFont(this, FontManager.FONT4)
                R.id.radioFont5 -> FontManager.setFont(this, FontManager.FONT5)
            }

            // Return result and finish activity
            setResult(AppCompatActivity.RESULT_OK)
            finish()
        }
    }

    companion object {
        const val FONT_SELECTION_REQUEST_CODE = 1
    }
}