package com.henryg.obdcarplay.shared

import android.content.Intent
import androidx.car.app.Screen
import androidx.car.app.Session
import com.henryg.obdcarplay.ui.obdcluster.ObdCarScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Automotive Car App Session.
 *
 * Creates an automotive-specific ObdViewModelBase with mock polling
 * (since Android Automotive doesn't have direct USB access like mobile).
 * In production, this would connect to a head unit service or CAN bus.
 */
class MyCarAppSession : Session() {
    override fun onCreateScreen(intent: Intent): Screen {
        val viewModel = object : ObdViewModelBase() {
            // Scope for the polling coroutine (bound to session lifecycle)
            private val scope = CoroutineScope(Dispatchers.Default + Job())

            override fun startPolling() {
                // Start mock polling loop for automotive
                scope.launch {
                    while (true) {
                        val timer = (System.nanoTime().toDouble() / 1e7) % (2 * Math.PI)
                        val oilTimer = (timer * 1.5) % (2 * Math.PI)

                        updateObdData(ObdData(
                            waterTemp = 90 + (10 * Math.sin(timer)).toInt(),
                            oilTemp = 70 + (10 * Math.sin(oilTimer)).toInt(),
                            afr = 1.4 + 0.2 * Math.cos(timer),
                            boostKpa = 10 + (5 * Math.sin(oilTimer)).toInt()
                        ))
                        delay(16)
                    }
                }
            }
        }
        viewModel.startPolling()
        return ObdCarScreen(carContext, viewModel)
    }
}