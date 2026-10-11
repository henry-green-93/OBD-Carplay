package com.henryg.obdcarplay.shared

import kotlinx.coroutines.flow.Flow

/**
 * Interface for the Bluetooth service layer, handling low-level Bluetooth operations.
 *
 * This interface is shared across all modules needing Bluetooth functionality.
 * Implementations vary by platform:
 * - Mobile: [AndroidBleService] using standard Android Bluetooth APIs
 * - Automotive: [CarBluetoothService] using Car App HAL
 */
interface BluetoothService {

    /**
     * Retrieves a list of currently known/discovered devices.
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
     * Initiates the pairing process for a device.
     * @return True if pairing initiated successfully, false otherwise.
     */
    suspend fun pairWithDevice(device: BleDevice): Boolean

    /**
     * Disconnects from the currently active device and cleans up resources.
     */
    suspend fun disconnect()
}
