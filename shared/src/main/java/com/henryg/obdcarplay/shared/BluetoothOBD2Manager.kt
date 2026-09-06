package com.henryg.obdcarplay.shared

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import com.henryg.obdcarplay.obd.OBD2PidParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.seconds

/**
 * Manages Bluetooth OBD2 adapter connection lifecycle and data polling.
 *
 * This class handles:
 * - BLE device discovery
 * - Pairing flow
 * - Connection establishment
 * - PID polling loop
 * - Data state management
 *
 * It integrates with the shared data model ([ObdData]) and connection status
 * to provide a consistent interface across the app.
 *
 * Usage:
 * ```
 * val manager = BluetoothOBD2Manager(context)
 * manager.observeState(lifecycleOwner) // observeStateFlow
 * manager.startDiscovery()
 * // When a device is selected...
 * manager.connect(device)
 * manager.startPolling()
 * ```
 */
class BluetoothOBD2Manager(private val context: Context) {

    companion object {
        private const val TAG = "BluetoothOBD2Manager"
        private const val CONNECTION_TIMEOUT_MS = 15000L
        private const val POLL_INTERVAL_MS = 66 // ~15 Hz, matches USB OBD2Manager
        private const val DISCOVERY_TIMEOUT_MS = 30000
        private const val PAIRING_TIMEOUT_MS = 20000L
    }

    private val reader = BluetoothOBD2Reader(context)
    private val parser = OBD2PidParser()

    // State flow exposed for observation
    private val _connectionStatus = MutableStateFlow<BluetoothConnectionStatus>(BluetoothConnectionStatus.Disconnected)
    val connectionStatus: StateFlow<BluetoothConnectionStatus> = _connectionStatus.asStateFlow()

    private val _obdData = MutableStateFlow(ObdData.Empty)
    val obdData: StateFlow<ObdData> = _obdData.asStateFlow()

    // Whether the polling loop is active
    private val _isPolling = MutableStateFlow(false)
    val isPolling: StateFlow<Boolean> = _isPolling.asStateFlow()

    // Whether discovery is active
    private val _isDiscovering = MutableStateFlow(false)
    val isDiscovering: StateFlow<Boolean> = _isDiscovering.asStateFlow()

    // Current connected device
    private val _currentDevice = MutableStateFlow<BluetoothDevice?>(null)
    val currentDevice: StateFlow<BluetoothDevice?> = _currentDevice.asStateFlow()

    // Connection lifecycle tracking
    private val isConnected = AtomicBoolean(false)
    private var isConnecting = false
    private var pollingJob: Job? = null
    private var discoveryJob: Job? = null

    private var lastResponse: String? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var scanJob: Job? = null

    /**
     * Observe the connection status and OBD data state flows.
     *
     * @param lifecycleOwner The [LifecycleOwner] to tie lifecycle to.
     */
    fun observeState(lifecycleOwner: LifecycleOwner) {
        // Observation logic can be added here if needed
    }

    /**
     * Starts BLE scanning for OBD2 devices.
     *
     * @param timeoutMs Scan timeout in milliseconds.
     */
    suspend fun startDiscovery(timeoutMs: Int = DISCOVERY_TIMEOUT_MS) {
        if (isDiscovering.value) return

        if (!hasBluetoothPermission(context)) {
            _connectionStatus.value = BluetoothConnectionStatus.Error(
                "Bluetooth permission not granted"
            )
            return
        }

        val adapter = getBluetoothAdapter()
        if (adapter == null) {
            _connectionStatus.value = BluetoothConnectionStatus.Error(
                "Bluetooth adapter not found"
            )
            return
        }

        _isDiscovering.value = true
        _connectionStatus.value = BluetoothConnectionStatus.Discovering(timeoutMs)

        scanJob = scope.launch {
            val scanSettings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()

            val callback = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult) {
                    val device = result.device
                    @Suppress("MissingPermission")
            val name = device.name ?: device.address
                    Log.d(TAG, "Scan result: $name (address: ${device.address})")

                    // Filter for OBD2 BLE devices (can filter by UUID if known)
                    if (isOBD2Device(device)) {
                        _connectionStatus.value = BluetoothConnectionStatus.Connected(
                            deviceId = device.address,
                            deviceName = name
                        )
                    }
                }

                override fun onBatchScanResults(results: List<ScanResult>) {
                    // Batch results
                }

                override fun onScanFailed(errorCode: Int) {
                    _connectionStatus.value = BluetoothConnectionStatus.Error(
                        "Scan failed: $errorCode"
                    )
                    _isDiscovering.value = false
                }
            }

            @Suppress("MissingPermission")
        adapter.bluetoothLeScanner.startScan(null, scanSettings, callback)

            delay(timeoutMs.toLong())

            // Cancel scan
            @Suppress("MissingPermission")
            adapter.bluetoothLeScanner.stopScan(callback)
            _isDiscovering.value = false
        }
    }

    /**
     * Connects to a specific Bluetooth device.
     *
     * @param device The Bluetooth device to connect to.
     * @param timeoutMs Connection timeout in milliseconds.
     */
    suspend fun connect(device: BluetoothDevice, timeoutMs: Long = CONNECTION_TIMEOUT_MS) {
        if (isConnected.get()) {
            // Already connected, do nothing
            return
        }

        if (isConnecting) {
            throw IllegalStateException("Already connecting")
        }

        _currentDevice.value = device
        _connectionStatus.value = BluetoothConnectionStatus.Discovering()

        // Start scanning if not already scanning
        if (!_isDiscovering.value) {
            startDiscovery(CONNECTION_TIMEOUT_MS.toInt())
        }

        // Wait for device to be found
        try {
            withTimeout(timeoutMs) {
                while (!(_connectionStatus.value is BluetoothConnectionStatus.Connected)) {
                    delay(100)
                }
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            _connectionStatus.value = BluetoothConnectionStatus.Error(
                "Connection timeout: Could not connect to $device"
            )
            _currentDevice.value = null
            throw IllegalStateException("Connection timed out")
        }

        @Suppress("MissingPermission")
        _connectionStatus.value = BluetoothConnectionStatus.Connected(
            deviceId = device.address,
            deviceName = (device.name ?: device.address)
        )
    }

    /**
     * Pairs a Bluetooth device.
     *
     * @param device The Bluetooth device to pair with.
     * @param timeoutMs Pairing timeout in milliseconds.
     */
    suspend fun pair(device: BluetoothDevice, timeoutMs: Long = PAIRING_TIMEOUT_MS) {
        if (!isConnected.get()) {
            // Pairing requires a connection
            connect(device)
        }

        val adapter = getBluetoothAdapter()
        if (adapter == null) {
            _connectionStatus.value = BluetoothConnectionStatus.Error(
                "Bluetooth adapter not found"
            )
            return
        }

        @Suppress("MissingPermission")
        _connectionStatus.value = BluetoothConnectionStatus.Pairing(
            deviceName = device.name ?: device.address,
            deviceId = device.address
        )

        try {
            withTimeout(timeoutMs) {
                @Suppress("MissingPermission")
        val bondState = device.bondState
                if ((bondState == BluetoothDevice.BOND_BONDED) || (bondState == BluetoothDevice.BOND_BONDING)) {
                    // Already bonded
                    null
                } else {
                    @Suppress("MissingPermission")
                    val result = withContext(Dispatchers.IO) {
                        device.createBond()
                        null
                    }

                    if (result != null) {
                        Log.i(TAG, "Bond result: $result")
                    }
                }
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            @Suppress("MissingPermission")
            _connectionStatus.value = BluetoothConnectionStatus.Error(
                "Pairing timed out for device: ${(device.name ?: device.address)}"
            )
            throw IllegalStateException("Pairing timed out")
        }

        @Suppress("MissingPermission")
        _connectionStatus.value = BluetoothConnectionStatus.Connected(
            deviceId = device.address,
            deviceName = device.name ?: device.address
        )
    }

    /**
     * Starts the PID polling loop.
     *
     * This method sets up a coroutine that repeatedly:
     * 1. Sends each PID command in sequence
     * 2. Parses the response
     * 3. Updates [obdData] state
     *
     * The loop runs until [stopPolling] is called or the connection is lost.
     */
    suspend fun startPolling() {
        if (!_isPolling.value) {
            if (!(_connectionStatus.value is BluetoothConnectionStatus.Connected)) {
                throw IllegalStateException(
                    "Cannot start polling: not connected. Call connect() or pair() first."
                )
            }
        }

        if (pollingJob?.isActive == true) {
            throw IllegalStateException("Already polling")
        }

        _isPolling.value = true
        _connectionStatus.value = if (_connectionStatus.value is BluetoothConnectionStatus.Connected) {
            @Suppress("MissingPermission")
            BluetoothConnectionStatus.EcuConnected(
                deviceId = (_currentDevice.value?.address ?: ""),
                deviceName = (_currentDevice.value?.name ?: "")
            )
        } else {
            _connectionStatus.value
        }

        pollingJob = scope.launch {
            while (isConnected.get()) {
                try {
                    // Send each PID and update state
                    pollPids()
                } catch (_: Exception) {
                    // Continue polling on error
                    delay(100)
                }
            }
        }
    }

    /**
     * Stops the PID polling loop.
     */
    suspend fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
        _isPolling.value = false
    }

    /**
     * Disconnects from the current Bluetooth device.
     */
    fun disconnect() {
        reader.disconnect()
        _connectionStatus.value = BluetoothConnectionStatus.Disconnected
        _currentDevice.value = null
        _isPolling.value = false
        pollingJob?.cancel()
        pollingJob = null
        _isPolling.value = false
    }

    /**
     * Polls all configured PIDs and updates the data state.
     *
     * This method sends each PID command sequentially and collects responses.
     * The [ObdData] model is updated with the latest values.
     */
    private suspend fun pollPids() {
        // Send each PID and collect responses
        val responses = mutableMapOf<String, String>()

        // Water Temp (01 05)
        try {
            val response = reader.sendCommand("01 05")
            responses["waterTemp"] = response
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read water temp: ${e.message}")
        }

        // Oil Temp (01 5C)
        try {
            val response = reader.sendCommand("01 5C")
            responses["oilTemp"] = response
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read oil temp: ${e.message}")
        }

        // AFR (01 44)
        try {
            val response = reader.sendCommand("01 44")
            responses["afr"] = response
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read AFR: ${e.message}")
        }

        // Boost/Vacuum (01 0B)
        try {
            val response = reader.sendCommand("01 0B")
            responses["boostKpa"] = response
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read boost: ${e.message}")
        }

        // Update ObdData with latest values
        val waterTemp = parseWaterTemp(responses["waterTemp"])
        val oilTemp = parseOilTemp(responses["oilTemp"])
        val afr = parseAfr(responses["afr"])
        val boostKpa = parseBoostKpa(responses["boostKpa"])

        lastResponse = responses["afr"]

        _obdData.value = ObdData(
            waterTemp = waterTemp,
            oilTemp = oilTemp,
            afr = afr,
            boostKpa = boostKpa,
            connectionStatus = @Suppress("MissingPermission")
                OBD2ConnectionStatus.EcUConnected(
                    deviceName = (_currentDevice.value?.name ?: "Unknown")
                )
        )

        Log.d(TAG, "Poll cycle complete: ${_obdData.value}")
    }

    /**
     * Parses the water temperature from an ELM327 response.
     * Formula: A - 40 (where A is the first data byte)
     * Valid range: -40°C to 215°C
     */
    private fun parseWaterTemp(response: String?): Int {
        if (response.isNullOrBlank()) return 0
        val clean = response.replace(">", "").trim()
        if (clean.isEmpty()) return 0
        val values = clean.split(" ").map { it.toIntOrNull(16) ?: 0 }
        if (values.isEmpty()) return 0
        val a = values[0]
        return (a - 40).coerceIn(-40, 215)
    }

    /**
     * Parses the oil temperature from an ELM327 response.
     * Formula: A - 40 (where A is the first data byte)
     */
    private fun parseOilTemp(response: String?): Int {
        if (response.isNullOrBlank()) return 0
        val clean = response.replace(">", "").trim()
        if (clean.isEmpty()) return 0
        val values = clean.split(" ").map { it.toIntOrNull(16) ?: 0 }
        if (values.isEmpty()) return 0
        val a = values[0]
        return (a - 40).coerceIn(-40, 215)
    }

    /**
     * Parses the AFR from an ELM327 response.
     * Formula: (100 / 200) * (A / B) where A is the first data byte and B is the second.
     * Result is scaled to a 0-100 range.
     */
    private fun parseAfr(response: String?): Double {
        if (response.isNullOrBlank()) return 0.0
        val clean = response.replace(">", "").trim()
        if (clean.isEmpty()) return 0.0
        val values = clean.split(" ").map { it.toIntOrNull(16) ?: 0 }
        if (values.size < 2) return 0.0
        val a = values[0].toLong()
        val b = values[1].toLong()
        if (b == 0L) return 0.0
        val ratio = (100.0 / 200.0) * (a / b.toDouble())
        return ratio.coerceIn(0.0, 100.0)
    }

    /**
     * Parses the boost/vacuum from an ELM327 response.
     * Formula: A - 40 (where A is the first data byte).
     * Result is in kPa, negative values indicate vacuum.
     */
    private fun parseBoostKpa(response: String?): Int {
        if (response.isNullOrBlank()) return 0
        val clean = response.replace(">", "").trim()
        if (clean.isEmpty()) return 0
        val values = clean.split(" ").map { it.toIntOrNull(16) ?: 0 }
        if (values.isEmpty()) return 0
        val a = values[0]
        return (a - 40).coerceIn(-100, 300) // Reasonable boost/vacuum range
    }

    /**
     * Checks if a Bluetooth device appears to be an OBD2 BLE adapter.
     *
     * Currently checks for known OBD2 BLE device addresses or UUIDs.
     * In production, this could be expanded to check for the OBD2 BLE service UUID.
     */
    private fun isOBD2Device(device: BluetoothDevice): Boolean {
        // TODO: Add known OBD2 BLE device addresses/UUIDs
        // For now, accept any device as potentially OBD2
        return true
    }

    /**
     * Checks if Bluetooth is available on the device.
     */
    private fun hasBluetoothPermission(context: Context): Boolean {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                context.checkSelfPermission(
                    android.Manifest.permission.BLUETOOTH_CONNECT
                ) == PackageManager.PERMISSION_GRANTED
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                context.checkSelfPermission(
                    android.Manifest.permission.BLUETOOTH_SCAN
                ) == PackageManager.PERMISSION_GRANTED &&
                        context.checkSelfPermission(
                            android.Manifest.permission.BLUETOOTH_CONNECT
                        ) == PackageManager.PERMISSION_GRANTED
            }
            else -> {
                context.checkSelfPermission(
                    android.Manifest.permission.BLUETOOTH
                ) == PackageManager.PERMISSION_GRANTED &&
                        context.checkSelfPermission(
                            android.Manifest.permission.BLUETOOTH_ADMIN
                        ) == PackageManager.PERMISSION_GRANTED
            }
        }
    }

    /**
     * Checks if Bluetooth is available on the device.
     */
    private fun isBluetoothAvailable(context: Context): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)
    }

    /**
     * Gets the default Bluetooth adapter.
     */
    private fun getBluetoothAdapter(): android.bluetooth.BluetoothAdapter? {
        return try {
            val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager
            manager?.adapter
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Cancels any active discovery or polling operations.
     */
    fun cancel() {
        scanJob?.cancel()
        scanJob = null
        discoveryJob?.cancel()
        discoveryJob = null
        _isDiscovering.value = false
        _isPolling.value = false
        pollingJob?.cancel()
        pollingJob = null
        reader.disconnect()
        _connectionStatus.value = BluetoothConnectionStatus.Disconnected
        _currentDevice.value = null
        _isPolling.value = false
    }
}
