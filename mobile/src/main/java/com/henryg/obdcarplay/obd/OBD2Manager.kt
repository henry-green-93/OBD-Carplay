package com.henryg.obdcarplay.obd

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Manages the OBD2 reader lifecycle and PID polling loop.
 *
 * Connects to an ELM327 adapter via USB, polls PIDs at ~60Hz,
 * and updates the StateFlow with decoded OBD data.
 *
 * PID polling strategy:
 * - Water Temp (01 05), Oil Temp (01 5C), AFR (01 44), Boost (01 0B)
 * - Each PID is polled sequentially in a 4-slot ring buffer
 * - ~16ms between polls, ~64ms per full cycle (15.6Hz effective)
 *
 * Usage:
 * ```
 * val manager = OBD2Manager(context)
 * manager.connect(device)
 * val data = manager.obdData.collectAsState() // ObdData with real values
 * manager.disconnect()
 * ```
 */
class OBD2Manager(private val context: Context) {

    companion object {
        private const val TAG = "OBD2Manager"

        // PID commands for our 4 metrics
        private val PID_COMMANDS = listOf(
            OBD2Reader.PID_WATER_TEMP to "waterTemp",
            "01 5C" to "oilTemp",
            OBD2Reader.PID_AIR_FUEL_RATIO to "afr",
            OBD2Reader.PID_BOOST_KPA to "boostKpa"
        )
    }

    private val reader = OBD2Reader(context)
    private val parser = OBD2PidParser()

    // Current decoded values
    private var _currentWaterTemp: Int = 0
        private set
    private var _currentOilTemp: Int = 0
        private field
    private var _currentAfr: Double = 0.0
        private set
    private var _currentBoostKpa: Int = 0
        private set

    /**
     * Current OBD data state. Updated every polling cycle.
     */
    private val _obdData = MutableStateFlow(createEmptyObdData())
    val obdData: StateFlow<ObdData> = _obdData.asStateFlow()

    /**
     * Connection status.
     */
    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    /**
     * Whether the reader is actively polling.
     */
    var isPolling: Boolean = false
        private set

    /**
     * Polling cycle (number of PIDs per cycle).
     * We poll 4 PIDs sequentially: waterTemp, oilTemp, AFR, boostKpa.
     */
    private val cycleSize = PID_COMMANDS.size

    /**
     * Connect to an OBD2 adapter.
     *
     * @param device The USB device to connect to
     * @throws IOException if connection fails
     */
    @Throws(IOException::class)
    suspend fun connect(device: UsbDevice) = withContext(Dispatchers.IO) {
        try {
            reader.connect(device)
            _connected.value = true
            Log.d(TAG, "Connected to OBD2 adapter: ${device.deviceName}")
            startPolling()
        } catch (e: IOException) {
            _connected.value = false
            throw IOException("Failed to connect to OBD2 adapter: ${e.message}", e)
        }
    }

    /**
     * Connect to the first available OBD2 adapter.
     */
    @Throws(IOException::class)
    suspend fun connectFirstAvailable() = withContext(Dispatchers.IO) {
        val available = reader.getAvailableDevices()
        if (available.isEmpty()) {
            throw IOException("No OBD2 adapters found")
        }
        connect(available.first())
    }

    /**
     * Start the PID polling loop.
     */
    private fun startPolling() {
        if (isPolling) return
        isPolling = true

        // Launch polling coroutine
        Thread {
            pollLoop()
        }.start()
    }

    /**
     * Main polling loop. Polls PIDs sequentially.
     */
    private fun pollLoop() {
        var cycleCount = 0

        while (isPolling && reader.isConnected()) {
            try {
                // Poll each PID sequentially
                for ((pid, label) in PID_COMMANDS) {
                    if (!isPolling) break

                    // Send PID command and parse response
                    val response = reader.sendCommand(pid)
                    val parsed = parser.parse(pid, response)

                    // Update the appropriate field based on label
                    parsed?.let {
                        updateMetric(label, it)
                    }

                    cycleCount++

                    // ~16ms between PID polls (~60Hz)
                    Thread.sleep(16)
                }

                // After full cycle, emit the combined state
                emitState()

                // Small pause between full cycles (~4ms)
                Thread.sleep(4)

            } catch (e: IOException) {
                Log.e(TAG, "Polling error", e)
                // Reconnect on error
                Thread.sleep(500)
            }
        }
    }

    /**
     * Update a single metric from a parsed response string.
     */
    private fun updateMetric(label: String, value: String) {
        when (label) {
            "waterTemp" -> {
                // Parse "90°C" → 90
                val temp = value.replace("°C", "").trim().toIntOrNull()
                if (temp != null) {
                    _currentWaterTemp = temp
                }
            }
            "oilTemp" -> {
                val temp = value.replace("°C", "").trim().toIntOrNull()
                if (temp != null) {
                    _currentOilTemp = temp
                }
            }
            "afr" -> {
                val afr = value.toDoubleOrNull()
                if (afr != null) {
                    _currentAfr = afr
                }
            }
            "boostKpa" -> {
                // Parse "10 kPa (boost)" or "-10 kPa (vacuum)" → 10 or -10
                val kpa = value.split(" ").firstOrNull { it.contains("kPa") }
                    ?.replace("kPa", "")
                    ?.replace("(", "")
                    ?.replace(")", "")
                    ?.trim()
                    ?.toIntOrNull()
                if (kpa != null) {
                    _currentBoostKpa = kpa
                }
            }
        }
    }

    /**
     * Emit combined OBD data state.
     */
    private fun emitState() {
        _obdData.value = ObdData(
            waterTemp = _currentWaterTemp,
            oilTemp = _currentOilTemp,
            afr = _currentAfr,
            boostKpa = _currentBoostKpa
        )
    }

    /**
     * Create empty ObdData with defaults.
     */
    private fun createEmptyObdData(): ObdData {
        return ObdData(
            waterTemp = 0,
            oilTemp = 0,
            afr = 0.0,
            boostKpa = 0
        )
    }

    /**
     * Disconnect and stop polling.
     */
    fun disconnect() {
        isPolling = false
        reader.disconnect()
        _connected.value = false
        _obdData.value = createEmptyObdData()
        Log.d(TAG, "Disconnected from OBD2 adapter")
    }

    /**
     * Check if the OBD2 adapter is responding.
     */
    fun ping(): Boolean {
        return reader.ping()
    }

    /**
     * Get the OBD2 reader for direct access if needed.
     */
    fun getReader(): OBD2Reader = reader

    /**
     * Get available OBD2 devices.
     */
    fun getAvailableDevices(): List<UsbDevice> {
        return reader.getAvailableDevices()
    }
}
