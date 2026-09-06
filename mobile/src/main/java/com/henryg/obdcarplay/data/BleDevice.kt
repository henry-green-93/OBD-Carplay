package com.henryg.obdcarplay.data

/**
 * Data model representing a Bluetooth Low Energy (BLE) device.
 */
data class BleDevice(
    val address: String,
    val name: String,
    val rssi: Int // Received Signal Strength Indicator (dBm)
)