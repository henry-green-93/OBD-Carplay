package com.henryg.obdcarplay.vm

import android.content.Context
import android.hardware.usb.UsbDevice
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.henryg.obdcarplay.obd.OBD2Manager
import com.henryg.obdcarplay.shared.ObdData
import com.henryg.obdcarplay.shared.ObdViewModelBase
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.IOException

/**
 * Mobile ViewModel that bridges the OBD2 hardware layer to the UI.
 *
 * Connects to a real ELM327 OBD2 adapter via USB and polls PIDs at ~60Hz.
 * Falls back to mock sin/cos data if no device is connected or connection fails.
 *
 * Usage:
 * ```
 * // Connect to real OBD2 reader
 * viewModel.connectOBD(device)
 *
 * // Or use mock data for testing
 * viewModel.useMockData()
 * ```
 */
class ObdViewModel(
    private val context: Context
) : ObdViewModelBase() {

    companion object {
        private const val TAG = "ObdViewModel"
    }

    private val obdManager = OBD2Manager(context)
    private var mockMode = false

    init {
        // Observe the manager's StateFlow and emit to UI
        viewModelScope.launch {
            obdManager.obdData.collect { data ->
                isLive = obdManager.connected.value
                updateObdData(data)
            }
        }

        // Also observe connection status
        viewModelScope.launch {
            obdManager.connected.collect { connected ->
                isLive = connected
            }
        }
    }

    /**
     * Connect to a real OBD2 adapter via USB.
     */
    fun connectOBD(device: UsbDevice) {
        mockMode = false
        isLive = true
        viewModelScope.launch {
            try {
                obdManager.connect(device)
                Log.d(TAG, "Connected to OBD2 adapter")
            } catch (e: IOException) {
                Log.e(TAG, "Failed to connect to OBD2 adapter: ${e.message}")
                isLive = false
                // Fall back to mock on connection failure
                startMockPolling()
            }
        }
    }

    /**
     * Connect to the first available OBD2 adapter.
     */
    fun connectFirstAvailable() {
        mockMode = false
        isLive = true
        viewModelScope.launch {
            try {
                obdManager.connectFirstAvailable()
                Log.d(TAG, "Connected to first available OBD2 adapter")
            } catch (e: IOException) {
                Log.e(TAG, "No OBD2 adapter found, using mock data")
                isLive = false
                startMockPolling()
            }
        }
    }

    /**
     * Switch to mock/simulated OBD data for testing.
     */
    fun useMockData() {
        mockMode = true
        isLive = false
        obdManager.disconnect()
        startMockPolling()
    }

    /**
     * Disconnect from OBD2 adapter.
     */
    fun disconnectOBD() {
        obdManager.disconnect()
        if (!mockMode) {
            startMockPolling() // Fall back to mock
        }
    }

    /**
     * Get available OBD2 devices.
     */
    fun getAvailableDevices(): List<UsbDevice> {
        return obdManager.getAvailableDevices()
    }

    /**
     * Check if connected to a live OBD2 adapter.
     */
    fun isOBDConnected(): Boolean = obdManager.connected.value

    /**
     * Manually ping the OBD2 adapter (useful for diagnostics).
     */
    fun pingAdapter(): Boolean = obdManager.ping()

    /**
     * Start mock sin/cos polling loop as fallback.
     */
    private fun startMockPolling() {
        viewModelScope.launch {
            while (mockMode && !obdManager.connected.value) {
                // Simulate 60Hz sin/cos oscillation:
                val waterTimer = (System.nanoTime().toDouble() / 1e7) % (2 * Math.PI)
                val oilTimer = (waterTimer * 1.5) % (2 * Math.PI)

                val mockData = ObdData(
                    waterTemp = 90 + (10 * Math.sin(waterTimer)).toInt(),
                    oilTemp = 70 + (10 * Math.sin(oilTimer)).toInt(),
                    afr = 1.4 + 0.2 * Math.cos(waterTimer),
                    boostKpa = 10 + (5 * Math.sin(oilTimer)).toInt()
                )
                updateObdData(mockData)
                delay(16) // ~60Hz polling
            }
        }
    }

    override fun startPolling() {
        // Real USB polling is driven by OBD2Manager
        // This method exists for interface compliance
    }

    override fun onCleared() {
        super.onCleared()
        // Stop polling and clean up resources
        mockMode = false
        obdManager.disconnect()
    }
}
