package com.henryg.obdcarplay.vm

import android.content.Context
import android.hardware.usb.UsbDevice
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.henryg.obdcarplay.obd.OBD2Manager
import com.henryg.obdcarplay.shared.OBD2ConnectionStatus
import com.henryg.obdcarplay.shared.ObdData
import com.henryg.obdcarplay.shared.ObdViewModelBase
import com.henryg.obdcarplay.UncaughtExceptionHandler
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
 * // Connect to real OBD2 adapter
 * viewModel.connectOBD(device)
 *
 * // Or use mock data for testing
 * viewModel.useMockData()
 * ```
 */
class ObdViewModel(
    context: Context
) : ObdViewModelBase() {

    companion object {
        private const val TAG = "ObdViewModel"
    }

    internal val obdManager = OBD2Manager(context)
    private var mockMode = false

    init {
        // Observe the manager's StateFlow and emit to UI with CoroutineExceptionHandler
        // Remove try/catch so exceptions propagate to the handler
        viewModelScope.launch(UncaughtExceptionHandler.coroutineExceptionHandler) {
            obdManager.obdData.collect { data ->
                updateObdData(data)
            }
        }

        // Also observe connection status with CoroutineExceptionHandler
        // Remove try/catch so exceptions propagate to the handler
        viewModelScope.launch(UncaughtExceptionHandler.coroutineExceptionHandler) {
            obdManager.connectionStatus.collect { status ->
                isLive = status is OBD2ConnectionStatus.EcUConnected || status is OBD2ConnectionStatus.UsbConnected
                updateObdData(ObdData.live(status))
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
     * Connect to a real OBD2 adapter via USB with a timeout.
     * Falls back to mock data if the connection times out.
     */
    fun connectOBDWithTimeout(device: UsbDevice, timeoutMillis: Long) {
        mockMode = false
        isLive = true
        viewModelScope.launch {
            try {
                obdManager.connect(device, timeoutMillis)
                Log.d(TAG, "Connected to OBD2 adapter with timeout")
            } catch (e: IOException) {
                Log.e(TAG, "Connection timed out or failed: ${e.message}")
                isLive = false
                // Fall back to mock on connection failure or timeout
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
        Log.d(TAG, "Mock data polling started")
    }

    /**
     * Start mock PID polling loop.
     */
    private fun startMockPolling() {
        viewModelScope.launch(UncaughtExceptionHandler.coroutineExceptionHandler) {
            while (mockMode) {
                val timestamp = System.currentTimeMillis()
                val waterTemp = 90 + ((timestamp % 10 - 5).toInt())
                val oilTemp = 85 + ((timestamp % 7 - 3).toInt())
                val afr = 14.7 + ((timestamp % 100) / 100.0 * 0.5)
                val boostKpa = 100 + ((timestamp % 20 - 10).toInt())
                updateObdData(
                    ObdData(
                        waterTemp = waterTemp,
                        oilTemp = oilTemp,
                        afr = afr,
                        boostKpa = boostKpa
                    )
                )
                delay(100)
            }
        }
    }

    override fun startPolling() {
        if (!mockMode) {
            viewModelScope.launch(UncaughtExceptionHandler.coroutineExceptionHandler) {
                obdManager.connectionStatus.collect { status ->
                    isLive = status is OBD2ConnectionStatus.EcUConnected || status is OBD2ConnectionStatus.UsbConnected
                }
            }
        } else {
            startMockPolling()
        }
    }
}