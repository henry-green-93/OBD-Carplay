package com.henryg.obdcarplay.shared

/**
 * Sealed hierarchy representing the connection state of the OBD2 stack.
 *
 * - [Disconnected]: No USB device or no USB permission.
 * - [UsbConnected]: USB OBD2 adapter is connected and initialized (ELM327 responded).
 * - [EcUConnected]: ECU is responding to PIDs (at least one successful PID response).
 * - [Error]: An error occurred during connection or polling.
 */
sealed class OBD2ConnectionStatus {
    /** Not connected to any OBD2 adapter. */
    object Disconnected : OBD2ConnectionStatus()

    /** USB OBD2 adapter connected, but ECU not yet verified. */
    data class UsbConnected(val deviceName: String) : OBD2ConnectionStatus()

    /** ECU is actively responding — data is live. */
    data class EcUConnected(val deviceName: String) : OBD2ConnectionStatus()

    /** An error occurred. [message] describes the failure. */
    data class Error(val message: String) : OBD2ConnectionStatus()
}

/**
 * Helper to get a short human-readable label for display.
 */
fun OBD2ConnectionStatus.label(): String = when (this) {
    is OBD2ConnectionStatus.Disconnected -> "Disconnected"
    is OBD2ConnectionStatus.UsbConnected -> "OBD2 Reader Connected"
    is OBD2ConnectionStatus.EcUConnected -> "ECU Connected"
    is OBD2ConnectionStatus.Error -> "Error: $message"
}
