package com.henryg.obdcarplay.shared

import java.util.Random

/**
 * Data model for a discovered Bluetooth OBD2 device.
 *
 * @param name The friendly name of the device (e.g., "ELM327 Bluetooth USB")
 * @param deviceId The MAC address or unique identifier
 * @param rssi Signal strength in dBm
 * @param isConnected Whether the device is currently connected
 * @param isPairing Whether the device is in the pairing process
 */
data class BleDevice(
    val name: String,
    val deviceId: String,
    val rssi: Int = 0,
    val isConnected: Boolean = false,
    val isPairing: Boolean = false
) {
    companion object {
        /**
         * Creates a mock device from a name string, useful for demo purposes.
         */
        fun fromName(name: String): BleDevice {
            val deviceId = name.replace(" ", "").replace("-", "").replace("_", "")
            return BleDevice(
                name = name,
                deviceId = deviceId,
                rssi = (Random().nextInt(60) + 20) // Simulate varying signal strength
            )
        }

        /**
         * List of mock devices for demonstration.
         */
        val MockDevices = listOf(
            fromName("ELM327 Bluetooth USB"),
            fromName("Vgate VLinker V2.1"),
            fromName("OBDLink LX Bluetooth"),
            fromName("Xtrig BT2"),
            fromName("Thinktron T300")
        )
    }
}
