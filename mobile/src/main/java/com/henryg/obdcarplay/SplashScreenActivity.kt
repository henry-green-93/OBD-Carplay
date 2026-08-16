package com.henryg.obdcarplay

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity

/**
 * Splash screen that displays the app logo briefly before launching MainActivity.
 * This prevents the white-screen hang while the OBD2 connection initializes.
 */
class SplashScreenActivity : ComponentActivity() {

    private val splashDelayMillis = 1500L // 1.5 second splash

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Delay launching MainActivity to let the splash display
        Handler(Looper.getMainLooper()).postDelayed({
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
        }, splashDelayMillis)
    }
}
