package com.henryg.obdcarplay.shared

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Base ViewModel that provides OBD data polling for all platforms.
 *
 * Subclasses override [nativeFetchNextMockData] to supply platform-specific
 * data sources (JNI on mobile, AIDL/binder on automotive, etc.).
 */
abstract class ObdViewModelBase : ViewModel() {

    private val _obdState = MutableStateFlow(ObdData())
    val obdState: StateFlow<ObdData> = _obdState

    init {
        viewModelScope.launch {
            while (true) {
                nativeFetchNextMockData()
                _obdState.value = ObdData(
                    waterTemp = currentWaterTemp,
                    oilTemp = currentOilTemp,
                    afr = currentAfr,
                    boostKpa = currentBoostKpa
                )
                delay(16) // ~60Hz polling
            }
        }
    }

    /** Mutable state fields set by [nativeFetchNextMockData]. */
    protected open var currentWaterTemp: Int = 0
        protected set
    protected open var currentOilTemp: Int = 0
        protected set
    protected open var currentAfr: Double = 0.0
        protected set
    protected open var currentBoostKpa: Int = 0
        protected set

    /**
     * Called every ~16ms by the polling coroutine.
     * Subclasses implement this to fetch from native code,
     * a service, or an emulator.
     */
    protected abstract fun nativeFetchNextMockData()
}
