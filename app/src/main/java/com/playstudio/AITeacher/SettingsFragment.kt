package com.playstudio.aiteacher

import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.widget.ImageButton
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import java.util.Locale

class SettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        Log.d("SettingsFragment", "SettingsFragment created")
        setPreferencesFromResource(R.xml.preferences, rootKey)

        // Hide the "History" preference
        findPreference<Preference>("history")?.isVisible = false

        // Handle "Terms of Use" preference click
        findPreference<Preference>("terms_of_use")?.setOnPreferenceClickListener {
            Log.d("SettingsFragment", "Terms of Use clicked")
            val intent = Intent(activity, TermsOfUseActivity::class.java)
            startActivity(intent)
            true
        }

        // Handle "Privacy Policy" preference click
        findPreference<Preference>("privacy_policy")?.setOnPreferenceClickListener {
            Log.d("SettingsFragment", "Privacy Policy clicked")
            val intent = Intent(activity, PrivacyPolicyActivity::class.java)
            startActivity(intent)
            true
        }

        // Handle "Contact Us" preference click
        findPreference<Preference>("contact_us")?.setOnPreferenceClickListener {
            Log.d("SettingsFragment", "Contact Us clicked")
            val intent = Intent(activity, ContactUsActivity::class.java)
            startActivity(intent)
            true
        }

        // Handle "Rate Us" preference click
        findPreference<Preference>("rate_us")?.setOnPreferenceClickListener {
            Log.d("SettingsFragment", "Rate Us clicked")
            rateUs()
            true
        }

        // Handle language change
        findPreference<ListPreference>("language")?.setOnPreferenceChangeListener { _, newValue ->
            Log.d("SettingsFragment", "Language changed to $newValue")
            setLocale(newValue as String)
            true
        }

        // Handle font selection click
        findPreference<Preference>("font_selection")?.setOnPreferenceClickListener {
            Log.d("SettingsFragment", "Font Selection clicked")
            val intent = Intent(activity, FontSelectionActivity::class.java)
            startActivityForResult(intent, FontSelectionActivity.FONT_SELECTION_REQUEST_CODE)
            true
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        // Apply the current font to the fragment's view if it's not null
        view?.let { FontManager.applyFontToView(requireContext(), it) }
    }

    private fun setLocale(languageCode: String) {
        val app = requireActivity().application as MyApplication
        app.setLocale(languageCode)
        restartApp()
    }

    private fun restartApp() {
        val intent = Intent(requireContext(), MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        requireActivity().finish()
    }

    private fun rateUs() {
        val uri = Uri.parse("market://details?id=${requireContext().packageName}")
        val goToMarket = Intent(Intent.ACTION_VIEW, uri)
        try {
            startActivity(goToMarket)
        } catch (e: ActivityNotFoundException) {
            // If Google Play is not available, open the app's page in a web browser
            val webUri = Uri.parse("https://play.google.com/store/apps/details?id=${requireContext().packageName}")
            val webIntent = Intent(Intent.ACTION_VIEW, webUri)
            startActivity(webIntent)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == FontSelectionActivity.FONT_SELECTION_REQUEST_CODE && resultCode == AppCompatActivity.RESULT_OK) {
            activity?.recreate() // Recreate activity to apply the selected font
        }
    }
}