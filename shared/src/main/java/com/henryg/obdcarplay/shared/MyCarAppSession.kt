package com.henryg.obdcarplay.shared

import android.content.Intent
import androidx.car.app.Screen
import androidx.car.app.Session
import com.henryg.obdcarplay.ui.obdcluster.ObdCarScreen

class MyCarAppSession : Session() {
    override fun onCreateScreen(intent: Intent): Screen {
        val viewModel = ObdViewModelBase()
        return ObdCarScreen(carContext, viewModel)
    }
}
