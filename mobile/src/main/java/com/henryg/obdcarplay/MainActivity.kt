package com.henryg.obdcarplay

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.henryg.obdcarplay.ui.screens.Dashboard
import com.henryg.obdcarplay.ui.theme.OBDCarPlayTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            OBDCarPlayTheme {
                Dashboard()
            }
        }
    }
}
