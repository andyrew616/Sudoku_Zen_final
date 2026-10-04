package rew.lightgames.sudoku2

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences

import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat

class OptionsActivity : AppCompatActivity() {

    private lateinit var sharedPreferences: SharedPreferences
    private val PREF_NAME = "my_preferences"

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_options)

        val root = findViewById<android.view.View>(android.R.id.content)
        WindowInsetsControllerCompat(window, root).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        sharedPreferences = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

        val musicSwitch: SwitchCompat = findViewById(R.id.musicSwitch)
        val soundEffectsSwitch: SwitchCompat = findViewById(R.id.soundEffectsSwitch)
        val autoNotesSwitch: SwitchCompat = findViewById(R.id.autoNotesSwitch)


        // Set initial state of UI elements
        musicSwitch.isChecked = sharedPreferences.getBoolean("music", true)
        soundEffectsSwitch.isChecked = sharedPreferences.getBoolean("sound_effects", true)
        autoNotesSwitch.isChecked = sharedPreferences.getBoolean(
            MainActivity.PREF_AUTO_NOTES,
            false
        )
        updateSwitchStateDescription(musicSwitch)
        updateSwitchStateDescription(soundEffectsSwitch)
        updateSwitchStateDescription(autoNotesSwitch)


        // Save new state when it changes
        musicSwitch.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit().putBoolean("music", isChecked).apply()
            updateSwitchStateDescription(musicSwitch)
        }

        soundEffectsSwitch.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit().putBoolean("sound_effects", isChecked).apply()
            updateSwitchStateDescription(soundEffectsSwitch)
        }

        autoNotesSwitch.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit()
                .putBoolean(MainActivity.PREF_AUTO_NOTES, isChecked)
                .apply()
            updateSwitchStateDescription(autoNotesSwitch)
        }

        findViewById<View>(R.id.privacyPolicyButton).setOnClickListener {
            val policyUrl = getString(R.string.privacy_policy_url).trim()
            if (policyUrl.isEmpty()) {
                showPrivacyPolicyMessage(R.string.privacy_policy_unavailable)
            } else {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(policyUrl)))
                } catch (_: ActivityNotFoundException) {
                    showPrivacyPolicyMessage(R.string.privacy_policy_no_browser)
                }
            }
        }

        val privacyRow = findViewById<View>(R.id.privacyOptionsRow)
        if (ConsentManager.isPrivacyOptionsRequired()) {
            privacyRow.visibility = View.VISIBLE
            privacyRow.setOnClickListener {
                ConsentManager.showPrivacyOptionsForm(this)
            }
            ViewCompat.setAccessibilityDelegate(
                privacyRow,
                object : AccessibilityDelegateCompat() {
                    override fun onInitializeAccessibilityNodeInfo(
                        host: View,
                        info: AccessibilityNodeInfoCompat
                    ) {
                        super.onInitializeAccessibilityNodeInfo(host, info)
                        info.className = Button::class.java.name
                    }
                }
            )
        } else {
            privacyRow.visibility = View.GONE
        }

    }
    private fun showPrivacyPolicyMessage(message: Int) {
        AlertDialog.Builder(this)
            .setTitle(R.string.privacy_policy)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
    fun onBackButtonClicked(view: View) {
        onBackPressedDispatcher.onBackPressed()
    }

    private fun updateSwitchStateDescription(toggle: SwitchCompat) {
        ViewCompat.setStateDescription(
            toggle,
            getString(
                if (toggle.isChecked) R.string.settings_switch_on
                else R.string.settings_switch_off
            )
        )
    }

}
