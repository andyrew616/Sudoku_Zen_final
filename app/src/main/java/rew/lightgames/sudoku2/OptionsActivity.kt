package rew.lightgames.sudoku2

import android.content.Context
import android.content.SharedPreferences

import android.os.Bundle
import android.view.View
import android.widget.Switch
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class OptionsActivity : AppCompatActivity() {

    private lateinit var sharedPreferences: SharedPreferences
    private val PREF_NAME = "my_preferences"

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_options)

        val root = findViewById<android.view.View>(android.R.id.content)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        sharedPreferences = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

        val musicSwitch: SwitchCompat = findViewById(R.id.musicSwitch)
        val soundEffectsSwitch: SwitchCompat = findViewById(R.id.soundEffectsSwitch)


        // Set initial state of UI elements
        musicSwitch.isChecked = sharedPreferences.getBoolean("music", true)
        soundEffectsSwitch.isChecked = sharedPreferences.getBoolean("sound_effects", true)


        // Save new state when it changes
        musicSwitch.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit().putBoolean("music", isChecked).apply()
        }

        soundEffectsSwitch.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit().putBoolean("sound_effects", isChecked).apply()
        }

        val privacyRow = findViewById<View>(R.id.privacyOptionsRow)
        if (ConsentManager.isPrivacyOptionsRequired()) {
            privacyRow.visibility = View.VISIBLE
            privacyRow.setOnClickListener {
                ConsentManager.showPrivacyOptionsForm(this)
            }
        } else {
            privacyRow.visibility = View.GONE
        }

    }
    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
    fun onBackButtonClicked(view: View) {
        onBackPressedDispatcher.onBackPressed()
    }

}
