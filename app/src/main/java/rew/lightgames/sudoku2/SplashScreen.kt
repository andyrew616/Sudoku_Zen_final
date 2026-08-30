package rew.lightgames.sudoku2


import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.ads.MobileAds
class SplashScreen : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)
        ConsentManager.initialize(this) {
            MobileAds.initialize(this) {
                Handler(Looper.getMainLooper()).postDelayed({
                    startActivity(Intent(this, MenuHostActivity::class.java))
                    finish()
                }, 2000)
            }
        }
    }
}