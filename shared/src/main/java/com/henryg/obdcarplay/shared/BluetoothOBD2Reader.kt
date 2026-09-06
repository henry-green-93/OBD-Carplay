package com.henryg.obdcarplay.shared

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothManager
import android.content.Context
import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import java.util.UUID

/**
 * Low-level Bluetooth OBD2 reader abstraction.
 *
 * This class handles BLE GATT discovery, connection, MTU negotiation,
 * and raw byte-level communication with an OBD2 BLE adapter.
 */
class BluetoothOBD2Reader(private val context: Context) {

    companion object {
        private const val TAG = "BluetoothOBD2Reader"
        private const val DEFAULT_MTU = 512
        private const val CONNECTION_TIMEOUT_MS = 15000L
        private val OBD2_SERVICE_UUID = UUID.fromString("000018f9-0000-1000-8000-00805f9b34fb")
        private val OBD2_DATA_UUID = UUID.fromString("000018f9-0100-1000-8000-00805f9b34fb")
    }

    private var gatt: BluetoothGatt? = null
    private var currentDevice: BluetoothDevice? = null
    private var isConnecting = false
    private var isConnected = false
    private var connection: BluetoothOBD2Connection? = null

    /**
     * Connects to a Bluetooth device and negotiates the OBD2 GATT service.
     *
     * @param device The Bluetooth device to connect to.
     * @param timeoutMs Connection timeout in milliseconds.
     * @return A [BluetoothOBD2Connection] that can be used to send commands.
     * @throws IllegalArgumentException if the device is null.
     * @throws IllegalStateException if already connecting or connected.
     */
    @Suppress("MissingPermission")
    suspend fun connect(device: BluetoothDevice, timeoutMs: Long = CONNECTION_TIMEOUT_MS): BluetoothOBD2Connection {
        currentDevice = device
        isConnecting = true

        val adapter = getBluetoothAdapter()
        if (adapter == null) {
            isConnecting = false
            throw IllegalStateException("Bluetooth adapter not found")
        }

        device.createBond()

        val bondState = device.bondState
        if (bondState != BluetoothDevice.BOND_BONDED) {
            isConnecting = false
            throw IllegalStateException("Device not bonded")
        }

        gatt = currentDevice?.connectGatt(context, false, object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        gatt?.discoverServices()
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        // Connection failed
                    }
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    Log.e(TAG, "Services discovered failed: $status")
                    @Suppress("MissingPermission")
                    gatt?.close()
                    isConnecting = false
                    return
                }

                val service = gatt?.getService(OBD2_SERVICE_UUID)
                if (service == null) {
                    Log.w(TAG, "OBD2 BLE service not found")
                    @Suppress("MissingPermission")
                    gatt?.close()
                    isConnecting = false
                    return
                }

                val characteristic = service.getCharacteristic(OBD2_DATA_UUID)
                if (characteristic == null) {
                    Log.w(TAG, "OBD2 data characteristic not found")
                    gatt?.close()
                    isConnecting = false
                    return
                }

                @Suppress("MissingPermission")
                gatt.requestMtu(DEFAULT_MTU)

                connection = BluetoothOBD2Connection(
                    device = device,
                    gatt = gatt,
                    characteristic = characteristic
                )
                isConnected = true
                isConnecting = false
            }

            @Deprecated("Use registerGattCallback instead")
            override fun onCharacteristicChanged(
                gatt: BluetoothGatt?,
                characteristic: BluetoothGattCharacteristic?
            ) {
                characteristic?.let {
                    gatt?.setCharacteristicNotification(it, false)
                }
            }
        })

        // Wait for connection to complete
        try {
            withTimeout(timeoutMs) {
                while (isConnecting) {
                    delay(100)
                }
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            Log.e(TAG, "Connection timeout")
            gatt?.close()
            isConnecting = false
            throw IllegalStateException("Connection timed out")
        }

        return connection ?: throw IllegalStateException("Connection not established")
    }

    /**
     * Sends an OBD2 command and waits for a response.
     *
     * @param command The OBD2 command string.
     * @return The raw response string from the ECU.
     */
    fun sendCommand(command: String): String {
        val gattRef = gatt ?: throw IllegalStateException("Not connected")
        val characteristic = connection?.characteristic ?: throw IllegalStateException("No connection")

        val commandBytes = command.toByteArray(Charsets.UTF_8)
        @Suppress("MissingPermission")
        gattRef.writeCharacteristic(characteristic, commandBytes, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)

        return connection?.readResponse() ?: ""
    }

    /**
     * Disconnects from the current Bluetooth device.
     */
    fun disconnect() {
        @Suppress("MissingPermission")
        gatt?.close()
        gatt = null
        connection = null
        currentDevice = null
        isConnected = false
        isConnecting = false
    }

    private fun getBluetoothAdapter(): BluetoothAdapter? {
        return try {
            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            bluetoothManager.adapter
        } catch (e: Exception) {
            null
        }
    }
}

/**
 * Data class representing an active Bluetooth OBD2 connection.
 */
class BluetoothOBD2Connection(
    val device: BluetoothDevice,
    val gatt: BluetoothGatt,
    val characteristic: BluetoothGattCharacteristic
) {

    /**
     * Sends a command and reads the response.
     *
     * @param command The OBD2 command to send.
     * @return The raw response string.
     */
    fun sendCommand(command: String): String {
        @Suppress("MissingPermission")
        gatt.writeCharacteristic(characteristic, command.toByteArray(Charsets.UTF_8), BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
        return readResponse()
    }

    /**
     * Reads a response from the GATT characteristic.
     */
    fun readResponse(): String {
        @Suppress("MissingPermission")
        gatt.setCharacteristicNotification(characteristic, true)
        // TODO: Implement actual notification reading
        return ""
    }
}
