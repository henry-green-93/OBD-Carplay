package com.henryg.obdcarplay.shared

/**
 * Sealed hierarchy representing the connection state of the Bluetooth OBD2 stack.
 *
 * - [Disconnected]: No Bluetooth device selected or Bluetooth is off.
 * - [Discovering]: Actively scanning for OBD2 BLE devices.
 * - [Pairing]: Device is being paired / bonded.
 * - [Connected]: BLE connection established, MTU negotiated.
 * - [EcuConnected]: ECU is responding to OBD2 PIDs (at least one successful response).
 * - [Error]: An error occurred during connection or polling.
 */
sealed class BluetoothConnectionStatus {

    /** No Bluetooth device selected or Bluetooth is unavailable. */
    object Disconnected : BluetoothConnectionStatus()

    /** Actively scanning for OBD2 BLE devices. */
    data class Discovering(val scanTimeoutMs: Int = 30000) : BluetoothConnectionStatus()

    /** Device is being paired / bonded. */
    data class Pairing(val deviceName: String, val deviceId: String) : BluetoothConnectionStatus()

    /** BLE connection established, MTU negotiated. */
    data class Connected(val deviceId: String, val deviceName: String) : BluetoothConnectionStatus()

    /** ECU is actively responding — data is live. */
    data class EcuConnected(val deviceId: String, val deviceName: String) : BluetoothConnectionStatus()

    /** An error occurred during connection or polling. */
    data class Error(val message: String) : BluetoothConnectionStatus()

    /**
     * Helper to get a short human-readable label for display.
     */
    fun label(): String = when (this) {
        is BluetoothConnectionStatus.Disconnected -> "Disconnected"
        is BluetoothConnectionStatus.Discovering -> "Discovering..."
        is BluetoothConnectionStatus.Pairing -> "Pairing: $deviceName"
        is BluetoothConnectionStatus.Connected -> "Connected: $deviceName"
        is BluetoothConnectionStatus.EcuConnected -> "ECU Connected"
        is BluetoothConnectionStatus.Error -> "Error: $message"
    }
}
