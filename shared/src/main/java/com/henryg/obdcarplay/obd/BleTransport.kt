package com.henryg.obdcarplay.obd

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/**
 * BLE GATT implementation of [Transport].
 *
 * Connects to an OBD2 BLE adapter via GATT, discovers the standard OBD2 service
 * (UUID `00001101-0000-1000-8000-00805f9b34fb`), and uses the write (0x1102)
 * and notify (0x1103) characteristics for command/response communication.
 *
 * This transport is used by the [OBD2Manager] for BLE-based OBD2 connections.
 */
class BleTransport(
    private val context: Context,
    private val bluetoothDevice: BluetoothDevice,
    private val serviceUuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb")
) : Transport {

    companion object {
        private const val TAG = "BleTransport"
        private const val DEFAULT_MTU = 512
        private const val WRITE_CHARACTERISTIC_UUID = "00001102-0000-1000-8000-00805f9b34fb"
        private const val NOTIFY_CHARACTERISTIC_UUID = "00001103-0000-1000-8000-00805f9b34fb"
        private const val CONNECT_TIMEOUT_MS = 15_000L
        private const val READ_TIMEOUT_MS = 500L
    }

    private val writeCharacteristicUuid = UUID.fromString(WRITE_CHARACTERISTIC_UUID)
    private val notifyCharacteristicUuid = UUID.fromString(NOTIFY_CHARACTERISTIC_UUID)

    private var gattRef: BluetoothGatt? = null
    private var writeChar: BluetoothGattCharacteristic? = null
    private var notifyChar: BluetoothGattCharacteristic? = null
    private var isConnecting = false
    private var isConnected = false
    private val responseMutex = Mutex()

    /** The Bluetooth device name, or null if disconnected. */
    @Suppress("MissingPermission")
    override val deviceName: String? get() = bluetoothDevice.name

    @Suppress("MissingPermission")
    override suspend fun open() {
        if (isConnected) return

        isConnecting = true
        Log.d(TAG, "Connecting to BLE device: ${bluetoothDevice.address}")

        gattRef = bluetoothDevice.connectGatt(context, false, object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        Log.d(TAG, "BLE connected, discovering services...")
                        gattRef?.discoverServices()
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        Log.w(TAG, "BLE disconnected")
                        isConnected = false
                        isConnecting = false
                        gatt?.close()
                    }
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    Log.e(TAG, "Services discovery failed: $status")
                    gatt?.close()
                    isConnecting = false
                    isConnected = false
                    return
                }

                var service = gatt?.getService(serviceUuid)
                if (service == null) {
                    // Fallback: try 000018f9 service
                    service = gatt?.getService(UUID.fromString("000018f9-0000-1000-8000-00805f9b34fb"))
                    if (service != null) {
                        Log.d(TAG, "Found fallback OBD2 service (000018f9)")
                    }
                }

                if (service != null) {
                    writeChar = findCharacteristic(service, writeCharacteristicUuid)
                    notifyChar = findCharacteristic(service, notifyCharacteristicUuid)

                    // If not found in standard service, try fallback characteristic UUIDs
                    if (writeChar == null || notifyChar == null) {
                        writeChar = findCharacteristic(service, UUID.fromString("000018f9-0100-1000-8000-00805f9b34fb"))
                            ?: writeChar
                        notifyChar = findCharacteristic(service, notifyCharacteristicUuid) ?: notifyChar
                    }
                }

                if (writeChar == null || notifyChar == null) {
                    Log.e(TAG, "Write or notify characteristic not found in any service")
                    gatt?.close()
                    isConnecting = false
                    isConnected = false
                    return
                }

                // Setup notification
                gatt?.setCharacteristicNotification(notifyChar, true)

                // Request MTU
                gatt?.requestMtu(DEFAULT_MTU)

                isConnected = true
                isConnecting = false
                Log.d(TAG, "BLE transport connected successfully")
            }

            override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Log.d(TAG, "MTU negotiated: $mtu")
                }
            }

            override fun onCharacteristicChanged(
                gatt: BluetoothGatt?,
                characteristic: BluetoothGattCharacteristic
            ) {
                if (characteristic == notifyChar) {
                    val data = String(characteristic.value ?: byteArrayOf())
                    Log.d(TAG, "Notification received: $data")
                    responseMutex.tryLock()
                    try {
                        pendingResponse = data.trim()
                    } finally {
                        responseMutex.unlock()
                    }
                }
            }
        })

        // Wait for connection to complete
        val startTime = System.currentTimeMillis()
        while (isConnecting && (System.currentTimeMillis() - startTime) < CONNECT_TIMEOUT_MS) {
            delay(100)
        }

        if (isConnecting) {
            gattRef?.close()
            throw java.io.InterruptedIOException("BLE connection timed out")
        }
    }

    private var pendingResponse: String? = null

    override suspend fun sendCommand(command: String): String {
        if (!isConnected) {
            throw java.io.IOException("BLE transport not connected")
        }

        val char = writeChar ?: throw java.io.IOException("Write characteristic not set")
        val gatt = gattRef ?: throw java.io.IOException("GATT not connected")

        // Clear any pending response
        responseMutex.tryLock()
        try {
            pendingResponse = null
        } finally {
            responseMutex.unlock()
        }

        // Send command
        char.value = "${command}\r\n".toByteArray(Charsets.US_ASCII)
        @Suppress("MissingPermission")
        gatt.writeCharacteristic(char)

        // Wait for response with timeout
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < READ_TIMEOUT_MS) {
            responseMutex.tryLock()
            try {
                val response = pendingResponse
                if (response != null && (response.endsWith(">") || response.contains("NO DATA"))) {
                    pendingResponse = null
                    return response
                }
            } finally {
                responseMutex.unlock()
            }
            delay(50)
        }

        // Timeout - return whatever we got
        responseMutex.tryLock()
        try {
            val response = pendingResponse ?: "TIMEOUT"
            pendingResponse = null
            return response
        } finally {
            responseMutex.unlock()
        }
    }

    override suspend fun sendCommandQuietly(command: String) {
        if (!isConnected) return
        val char = writeChar ?: return
        val gatt = gattRef ?: return

        char.value = "${command}\r\n".toByteArray(Charsets.US_ASCII)
        @Suppress("MissingPermission")
        gatt.writeCharacteristic(char)
        delay(50)
    }

    override fun isConnected(): Boolean = isConnected

    override suspend fun ping(): Boolean {
        return try {
            val response = sendCommand("AT")
            response.contains("OK", ignoreCase = true)
        } catch (e: Exception) {
            Log.w(TAG, "BLE ping failed", e)
            false
        }
    }

    override fun close() {
        @Suppress("MissingPermission")
        gattRef?.close()
        gattRef = null
        writeChar = null
        notifyChar = null
        isConnected = false
        Log.d(TAG, "BLE transport closed")
    }

    /**
     * Find a characteristic by UUID in a service.
     */
    private fun findCharacteristic(service: BluetoothGattService, uuid: UUID): BluetoothGattCharacteristic? {
        for (characteristic in service.characteristics) {
            if (characteristic.uuid == uuid) return characteristic
        }
        return null
    }
}

/**
 * Data class representing a BLE OBD2 device discovered during scanning.
 */
data class BleObdDevice(
    val bluetoothDevice: BluetoothDevice,
    val name: String,
    val macAddress: String,
    val rssi: Int = 0
)
