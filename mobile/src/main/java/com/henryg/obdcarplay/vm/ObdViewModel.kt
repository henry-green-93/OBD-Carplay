package com.henryg.obdcarplay.vm

import com.henryg.obdcarplay.shared.ObdData
import com.henryg.obdcarplay.shared.ObdViewModelBase
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow

/**
 * Mobile ViewModel that extends the shared base with JNI bindings.
 *
 * In production this would call real native OBD2 code via USB/HIDL.
 * Currently uses sin/cos oscillation mock data at ~60Hz.
 */
class ObdViewModel : ObdViewModelBase() {

    external fun updateData(water: Int, oil: Int, afr: Double, boost: Int)
    external fun getNativeTimestamp(): Long

    // Mock-only override (replaces the abstract base impl)
    override fun nativeFetchNextMockData() {
        // Simulate 60Hz sin/cos oscillation:
        val waterTimer = (System.nanoTime().toDouble() / 1e7) % (2 * Math.PI)
        val oilTimer = (waterTimer * 1.5) % (2 * Math.PI)

        currentWaterTemp = 90 + (10 * Math.sin(waterTimer)).toInt()
        currentOilTemp = 70 + (10 * Math.sin(oilTimer)).toInt()
        currentAfr = 1.4 + 0.2 * Math.cos(waterTimer)
        currentBoostKpa = 10 + (5 * Math.sin(oilTimer)).toInt()
    }
}
