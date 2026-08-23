package com.henryg.obdcarplay.ui.obdcluster

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarText
import androidx.car.app.model.Template
import com.henryg.obdcarplay.shared.OBD2ConnectionStatus
import com.henryg.obdcarplay.shared.label
import com.henryg.obdcarplay.shared.ObdViewModelBase

/**
 * Automotive Car App Screen rendering the OBD2 numeric cluster.
 *
 * Uses Car App Template API (MessageTemplate with metrics) instead of
 * Compose since automotive apps run in a host-provided UI shell.
 * Displays connection status for both USB OBD2 reader and ECU.
 */
class ObdCarScreen(carContext: CarContext, viewModel: ObdViewModelBase) : Screen(carContext) {

    private val _viewModel = viewModel

    override fun onGetTemplate(): Template {
        val data = _viewModel.obdState.value
        val statusText = data.connectionStatus.label()

        return androidx.car.app.model.MessageTemplate.Builder(
            CarText.create("${data.waterTemp}\u00B0C | $statusText")
        )
            .setHeaderAction(Action.APP_ICON)
            .setTitle("OBD2 Numeric Cluster")
            .build()
    }
}
