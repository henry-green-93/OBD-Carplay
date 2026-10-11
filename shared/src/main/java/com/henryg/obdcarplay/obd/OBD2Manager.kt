package com.henryg.obdcarplay.obd

import android.content.Context
import android.hardware.usb.UsbDevice
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.henryg.obdcarplay.shared.ObdData
import com.henryg.obdcarplay.shared.OBD2ConnectionStatus
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.CompletableFuture

/**
 * Manages the OBD2 transport layer and PID polling loop.
 *
 * Supports both USB-serial and BLE transports via the [Transport] interface.
 * Polls PIDs at ~15.6Hz and emits decoded OBD data through [obdData].
 *
 * PID polling strategy:
 * - Water Temp (01 05), Oil Temp (01 5C), AFR (01 44), Boost (01 0B)
 * - Each PID is polled sequentially in a 4-slot ring buffer
 * - ~16ms between polls, ~64ms per full cycle (15.6Hz effective)
 *
 * Usage:
 * ```
 * // For USB:
 * val transport = UsbSerialTransport(context)
 * transport.setDevice(usbDevice)
 * val manager = OBD2Manager(transport)
 * manager.connect()
 *
 * // For BLE:
 * val transport = BleTransport(context, device)
 * val manager = OBD2Manager(transport)
 * manager.connect()
 * ```
 */
class OBD2Manager(private val transport: Transport) {

    companion object {
        private const val TAG = "OBD2Manager"

        // PID commands for our 4 metrics
        private val PID_COMMANDS = listOf(
            UsbSerialTransport.PIDs["waterTemp"]!! to "waterTemp",
            "01 5C" to "oilTemp",
            UsbSerialTransport.PIDs["airFuelRatio"]!! to "afr",
            UsbSerialTransport.PIDs["boostKpa"]!! to "boostKpa"
        )
    }

    private val parser = OBD2PidParser()

    // Current decoded values
    private var _currentWaterTemp: Int = 0
        private set
    private var _currentOilTemp: Int = 0
        private set
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
     * Connection status (USB adapter + ECU).
     */
    private val _connectionStatus = MutableStateFlow<OBD2ConnectionStatus>(OBD2ConnectionStatus.Disconnected)
    val connectionStatus: StateFlow<OBD2ConnectionStatus> = _connectionStatus.asStateFlow()

    /**
     * Whether the reader is actively polling.
     */
    var isPolling: Boolean = false
        private set

    /**
     * Number of successful PID responses received. Once > 0, ECU is considered connected.
     */
    private var successfulPidCount: Int = 0

    /**
     * Polling cycle (number of PIDs per cycle).
     * We poll 4 PIDs sequentially: waterTemp, oilTemp, AFR, boostKpa.
     */
    private val cycleSize = PID_COMMANDS.size

    /**
     * Connect the transport and start polling.
     */
    @Throws(IOException::class)
    suspend fun connect() = withContext(Dispatchers.IO) {
        try {
            transport.open()
            val deviceName = transport.deviceName ?: "Unknown"
            _connectionStatus.value = OBD2ConnectionStatus.UsbConnected(deviceName)
            Log.d(TAG, "Connected to OBD2 adapter: $deviceName")
            startPolling()
        } catch (e: IOException) {
            _connectionStatus.value = OBD2ConnectionStatus.Error(e.message ?: "Connection failed")
            Log.e(TAG, "Failed to connect to OBD2 adapter: ${e.message}")
            throw IOException("Failed to connect to OBD2 adapter: ${e.message}", e)
        }
    }

    /**
     * Start the PID polling loop.
     */
    private fun startPolling() {
        if (isPolling) return
        isPolling = true

        // Launch polling in a coroutine scope
        val scope = kotlinx.coroutines.CoroutineScope(Dispatchers.IO + kotlinx.coroutines.SupervisorJob())
        scope.launch {
            pollLoop()
        }
    }

    /**
     * Main polling loop. Polls PIDs sequentially.
     */
    private suspend fun pollLoop() {
        var cycleCount = 0

        while (isPolling && transport.isConnected()) {
            try {
                // Poll each PID sequentially
                for ((pid, label) in PID_COMMANDS) {
                    if (!isPolling) break

                    // Send PID command and parse response
                    val response = transport.sendCommand(pid)
                    val parsed = parser.parse(pid, response)

                    // Update the appropriate field based on label
                    parsed?.let {
                        updateMetric(label, it)
                        successfulPidCount++
                        // Once we have at least one successful PID, mark ECU as connected
                        if (successfulPidCount == 1) {
                            val deviceName = transport.deviceName ?: "Unknown"
                            _connectionStatus.value = OBD2ConnectionStatus.EcUConnected(deviceName)
                        }
                    }

                    cycleCount++

                    // ~16ms between PID polls (~60Hz)
                    kotlinx.coroutines.delay(16)
                }

                // After full cycle, emit the combined state
                emitState()

                // Small pause between full cycles (~4ms)
                kotlinx.coroutines.delay(4)

            } catch (e: IOException) {
                Log.e(TAG, "Polling error", e)
                _connectionStatus.value = OBD2ConnectionStatus.Error(e.message ?: "Polling error")
                // Reconnect on error
                kotlinx.coroutines.delay(500)
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
        transport.close()
        _connectionStatus.value = OBD2ConnectionStatus.Disconnected
        _obdData.value = createEmptyObdData()
        Log.d(TAG, "Disconnected from OBD2 adapter")
    }

    /**
     * Check if the OBD2 adapter is responding.
     */
    suspend fun ping(): Boolean = transport.ping()

    /**
     * Get the underlying transport for direct access if needed.
     */
    fun getTransport(): Transport = transport

    /**
     * Get available OBD2 devices (USB only).
     */
    fun getAvailableDevices(): List<UsbDevice> {
        return (transport as? UsbSerialTransport)?.getAvailableDevices() ?: emptyList()
    }
}

/**
 * Convenience factory that creates an [OBD2Manager] with a [UsbSerialTransport].
 *
 * Use this when connecting via USB — for BLE, construct [Transport] manually
 * and pass it to the [OBD2Manager] constructor directly.
 */
fun OBD2Manager.Companion.createUsbManager(context: Context): OBD2Manager {
    val transport = UsbSerialTransport(context)
    return OBD2Manager(transport)
}
