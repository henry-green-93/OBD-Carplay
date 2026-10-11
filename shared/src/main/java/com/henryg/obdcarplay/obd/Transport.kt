package com.henryg.obdcarplay.obd

/**
 * Abstract transport for sending/receiving data to/from the OBD2 adapter.
 *
 * Implemented by USB-serial and BLE backends to provide a unified interface
 * for [OBD2Manager] to poll PIDs regardless of the underlying connection type.
 */
interface Transport : AutoCloseable {

    /**
     * Opens the transport connection. Must be called before sending commands.
     */
    suspend fun open()

    /**
     * Sends an OBD2 command (e.g., "01 0C" for engine RPM) and returns the raw response.
     *
     * @param command The OBD2 command string.
     * @return The raw response string from the OBD2 adapter.
     * @throws Exception if communication fails or the adapter is not connected.
     */
    suspend fun sendCommand(command: String): String

    /**
     * Sends a command without waiting for a response (used for initialization).
     *
     * @param command The command string to send.
     */
    suspend fun sendCommandQuietly(command: String)

    /**
     * Checks if the transport is currently connected and ready for commands.
     */
    fun isConnected(): Boolean

    /**
     * Checks if the OBD2 adapter is responding with an OK ping.
     */
    suspend fun ping(): Boolean

    /**
     * The human-readable name of the connected device, or null if disconnected.
     */
    val deviceName: String?
}
