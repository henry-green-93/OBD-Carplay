package com.henryg.obdcarplay.shared

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Shared base ViewModel for all platforms.
 *
 * Manages the OBD data StateFlow and provides lifecycle-safe access
 * to the current OBD readings. Subclasses override [startPolling] to
 * supply real or mock data.
 *
 * The base class does NOT start polling automatically — that is controlled
 * by subclasses via [startPolling] and [stopPolling].
 */
abstract class ObdViewModelBase : ViewModel() {

    private val _obdState = MutableStateFlow(ObdData.Empty)
    val obdState: StateFlow<ObdData> = _obdState

    /** Whether the data source is currently connected and active. */
    var isLive: Boolean = false
        protected set

    /**
     * Called by subclasses to push a new OBD data snapshot.
     */
    protected fun updateObdData(data: ObdData) {
        _obdState.value = data
    }

    /**
     * Start the data source (USB polling, service connection, etc.).
     * Subclasses implement this to start their platform-specific source.
     */
    abstract fun startPolling()

    /**
     * Stop the data source and emit empty data.
     */
    fun stopPolling() {
        isLive = false
        _obdState.value = ObdData.Empty
    }
}