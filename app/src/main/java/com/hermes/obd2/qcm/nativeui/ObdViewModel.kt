package com.hermes.obd2.qcm.nativeui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class ObdData(
    val waterTemp: Int = 0,
    val oilTemp: Int = 0,
    val afrBoost: String = "0.0, 0Vac"
)

class ObdViewModel : ViewModel() {
    private val _obdState = MutableStateFlow(ObdData())
    val obdState: StateFlow<ObdData> = _obdState

    init {
        viewModelScope.launch {
            while (true) {
                fetchNextMockData()
                _obdState.value = ObdData(
                    waterTemp = waterTemp,
                    oilTemp = oilTemp,
                    afrBoost = afrBoost
                )
                delay(16) 
            }
        }
    }

    external fun updateData(water: Int, oil: Int, afr: Double, boost: Int)
    external fun getNativeTimestamp(): Long
    external fun fetchNextMockData()

    var waterTemp = 0
    var oilTemp = 0
    var afrBoost = "0.0, 0Vac"
}
