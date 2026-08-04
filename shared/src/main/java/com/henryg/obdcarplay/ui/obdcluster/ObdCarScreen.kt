package com.henryg.obdcarplay.ui.obdcluster

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.Metric
import androidx.car.app.model.Template
import com.henryg.obdcarplay.shared.ObdViewModelBase

/**
 * Automotive Car App Screen rendering the OBD2 numeric cluster.
 *
 * Uses Car App Template API (MessageTemplate with metrics) instead of
 * Compose since automotive apps run in a host-provided UI shell.
 */
class ObdCarScreen(carContext: CarContext, viewModel: ObdViewModelBase) : Screen(carContext) {

    private val _viewModel = viewModel

    override fun onGetTemplate(): Template {
        val data = _viewModel.obdState.value
        val waterMetric = Metric.Builder()
            .setText("${data.waterTemp}\u00B0C")
            .setSecondaryText("Water Temp")
            .build()

        val oilMetric = Metric.Builder()
            .setText("${data.oilTemp}\u00B0C")
            .setSecondaryText("Oil Temp")
            .build()

        val afrMetric = Metric.Builder()
            .setText(data.afrBoostDisplay)
            .setSecondaryText("AFR + Boost")
            .build()

        return androidx.car.app.model.MessageTemplate.Builder(
            "OBD2 Cluster"
        )
            .setHeaderAction(Action.APP_ICON)
            .setPrimaryText("OBD2 Numeric Cluster")
            .setMetrics(listOf(waterMetric, oilMetric, afrMetric))
            .build()
    }
}
