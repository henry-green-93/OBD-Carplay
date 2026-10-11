package com.henryg.obdcarplay.service

import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.util.Log
import com.henryg.obdcarplay.shared.BluetoothService
import com.henryg.obdcarplay.shared.BleDevice
import com.henryg.obdcarplay.shared.BluetoothOBD2Manager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Automotive Bluetooth service that uses standard BLE for device discovery
 * and connection.
 *
 * On Android Automotive OS, this falls back to the BluetoothAdapter since
 * the Car App HAL BLE APIs may not be fully available in all emulator versions.
 */
class CarBluetoothService(
    private val carContext: androidx.car.app.CarContext
) : BluetoothService {

    companion object {
        private const val TAG = "CarBluetoothService"
    }

    private val context = carContext.getApplicationContext() as Context
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter = bluetoothManager.adapter

    // State flows
    private val _availableDevices = MutableStateFlow<List<BleDevice>>(emptyList())

    override fun observeDevices(): Flow<List<BleDevice>> = _availableDevices.asStateFlow()

    override fun getAvailableDevices(): List<BleDevice> {
        return _availableDevices.value
    }

    override suspend fun connectToDevice(device: BleDevice): Boolean {
        Log.d(TAG, "Connecting to device: ${device.name} (${device.deviceId})")

        val bluetoothDevice = bluetoothAdapter.getRemoteDevice(device.deviceId)
        return try {
            // Use BluetoothOBD2Manager for BLE connection
            val obdManager = BluetoothOBD2Manager(context)
            obdManager.connect(bluetoothDevice)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Connection failed: ${e.message}")
            false
        }
    }

    override suspend fun pairWithDevice(device: BleDevice): Boolean {
        Log.d(TAG, "Pairing with device: ${device.name}")
        val bluetoothDevice = bluetoothAdapter.getRemoteDevice(device.deviceId)
        return try {
            @Suppress("MissingPermission")
            val result = bluetoothDevice.createBond()
            Log.d(TAG, "Bond result: $result")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Pairing failed: ${e.message}")
            false
        }
    }

    override suspend fun disconnect() {
        Log.d(TAG, "Disconnecting Bluetooth service")
        _availableDevices.value = emptyList()
    }

    /**
     * Start scanning for BLE OBD2 devices.
     */
    @Suppress("MissingPermission")
    fun startScan() {
        Log.d(TAG, "Starting BLE scan")

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device
                val name = device.name ?: device.address
                val rssi = result.rssi

                Log.d(TAG, "Scan result: $name (RSSI: $rssi)")

                val existing = _availableDevices.value.find { it.deviceId == device.address }
                if (existing == null) {
                    _availableDevices.value = _availableDevices.value + BleDevice(
                        name = name,
                        deviceId = device.address,
                        rssi = rssi
                    )
                } else {
                    _availableDevices.value = _availableDevices.value.map {
                        if (it.deviceId == device.address) it.copy(rssi = rssi) else it
                    }
                }
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e(TAG, "Scan failed: $errorCode")
            }
        }

        bluetoothAdapter.bluetoothLeScanner.startScan(
            null,
            ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),
            callback
        )
    }

    /**
     * Get the BluetoothAdapter for direct access.
     */
    fun getBluetoothAdapter(): android.bluetooth.BluetoothAdapter? = bluetoothAdapter
}
