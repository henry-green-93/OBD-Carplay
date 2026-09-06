package com.henryg.obdcarplay.service

import com.henryg.obdcarplay.data.BleDevice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.ConcurrentHashMap

/**
 * Interface for the Bluetooth service layer, handling low-level Bluetooth operations.
 * This interface is shared/expected by all modules needing Bluetooth functionality.
 */
interface BluetoothService {

    /**
     * Retrieves a list of currently known/discovered devices.
     * In a real scenario, this would query the Android BluetoothAdapter.
     */
    fun getAvailableDevices(): List<BleDevice>

    /**
     * Observes changes to the list of available devices (e.g., when scanning).
     * @return A Flow that emits the current list of devices.
     */
    fun observeDevices(): Flow<List<BleDevice>>

    /**
     * Initiates the connection handshake with a device.
     * @return True if connection started successfully, false otherwise.
     */
    suspend fun connectToDevice(device: BleDevice): Boolean

    /**
     * Initiates the pairing process for a specified device.
     * @return True if pairing initiated successfully, false otherwise.
     */
    suspend fun pairWithDevice(device: BleDevice): Boolean

    /**
     * Disconnects from the currently active device and cleans up resources.
     */
    suspend fun disconnect()
}

/**
 * Mock implementation of BluetoothService for demonstration and testing.
 * In a real app, this would implement the Android BluetoothGatt/BluetoothAdapter logic.
 */
class MockBluetoothService : BluetoothService {
    private val _availableDevices = MutableStateFlow<List<BleDevice>>(emptyList())
    override fun observeDevices(): Flow<List<BleDevice>> = _availableDevices.asStateFlow()

    private val knownDevices = mutableListOf(
        BleDevice("00:1A:7D:DA:71:13", "OBD2 Adapter PRO", -65),
        BleDevice("A1:B2:C3:D4:E5:F6", "OBD2 Scanner Mini", -70),
        BleDevice("11:22:33:44:55:66", "Generic Dongle", -55)
    )

    override fun getAvailableDevices(): List<BleDevice> {
        return knownDevices.toList()
    }

    override suspend fun connectToDevice(device: BleDevice): Boolean {
        println("MOCK: Attempting connection to ${device.name}...")
        // Simulate a successful connection attempt delay
        return true
    }

    override suspend fun pairWithDevice(device: BleDevice): Boolean {
        println("MOCK: Attempting pairing with ${device.name}...")
        // Simulate a successful pairing attempt
        return true
    }

    override suspend fun disconnect() {
        println("MOCK: Disconnecting Bluetooth service...")
        // Simulate disconnection cleanup
        kotlinx.coroutines.delay(500) // Needs import
    }
}