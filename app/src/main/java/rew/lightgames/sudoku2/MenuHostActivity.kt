package rew.lightgames.sudoku2

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.fragment.NavHostFragment
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView

class MenuHostActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_menu)

        val root = findViewById<android.view.View>(android.R.id.content)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val adRequest = AdRequest.Builder().build()
        val adView = findViewById<AdView>(R.id.adView)
        if (ConsentManager.canRequestAds()) {
            adView.loadAd(adRequest)
        }

        if (
            savedInstanceState == null &&
            intent.getBooleanExtra(EXTRA_OPEN_DIFFICULTY, false)
        ) {
            val navHost = supportFragmentManager.findFragmentById(R.id.nav_host_fragment)
                as NavHostFragment
            navHost.navController.navigate(R.id.SecondFragment)
        }
    }

    companion object {
        const val EXTRA_OPEN_DIFFICULTY = "open_difficulty_selection"
    }
}
