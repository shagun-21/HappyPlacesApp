package com.example.happyplaces

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import com.example.happyplaces.activities.MainActivity
import kotlinx.android.synthetic.main.activity_splash_screen.*

class SplashScreenActivity : AppCompatActivity() {
    val splash_time_out:Long=2200
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash_screen)
        travel_lottie.animate().translationY(1400f).setDuration(1000).setStartDelay(4000)
        Handler().postDelayed({
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        },splash_time_out)
    }
}