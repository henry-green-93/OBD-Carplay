package com.henryg.obdcarplay.obd

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.util.Log
import com.hoho.android.usbserial.driver.CdcAcmSerialDriver
import com.hoho.android.usbserial.driver.Ch34xSerialDriver
import com.hoho.android.usbserial.driver.Cp21xxSerialDriver
import com.hoho.android.usbserial.driver.FtdiSerialDriver
import com.hoho.android.usbserial.driver.ProlificSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.util.SerialInputOutputManager
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.nio.charset.StandardCharsets

/**
 * USB-serial implementation of [Transport].
 *
 * Wraps the usb-serial-for-android library to connect to ELM327-compatible
 * OBD2 adapters via USB CDC-ACM or vendor-specific drivers (FTDI, CP210x, CH34x, PL2303).
 *
 * Initializes the ELM327 adapter by sending AT commands (reset, echo off, linefeed off, auto protocol).
 */
class UsbSerialTransport(
    private val context: Context
) : Transport {

    companion object {
        private const val TAG = "UsbSerialTransport"
        private const val READ_TIMEOUT_MS = 500
        private const val CONNECT_TIMEOUT_MS = 3000
        private const val BaudRate = 38400 // Default ELM327 baud
        private const val ELM327_PROMPT = ">"
        private const val CMD_RESET = "AT Z"
        private const val CMD_PROTOCOL_AUTO = "AT TP 0"
        private const val CMD_ECHO_OFF = "ATE0"
        private const val CMD_LINEFEED_OFF = "ATL0"

        // USB permission broadcast constants
        const val ACTION_USB_PERMISSION_RESULT = "com.henryg.obdcarplay.USB_PERMISSION_RESULT"
        const val KEY_PERMISSION_GRANTED = "permission_granted"

        /**
         * Standard OBD2 PID commands (Mode 01 - supported PIDs).
         */
        val PIDs = mapOf(
            "supported" to "01 00",
            "engineRpm" to "01 0C",
            "vehicleSpeed" to "01 0D",
            "engineCoolantTemp" to "01 05",
            "engineOilTemp" to "01 5C",
            "engineAirRate" to "01 04",
            "intakePressure" to "01 0B",
            "mafFlowRate" to "01 10",
            "throttlePosition" to "01 11",
            "airFuelRatio" to "01 44",
            "command2044" to "01 44", // AFR Mode 01
            "controlModuleVoltage" to "01 42",
            "timingAdvance" to "01 0E",
            "runningTime" to "01 0F",
            "absoluteLoad" to "01 43",
            "waterTemp" to "01 05",
            "boostKpa" to "01 0B", // Absolute manifold pressure
            "vacuumKpa" to "01 0B" // Same PID, interpret based on context
        )
    }

    private var usbManager: UsbManager? = null
    private var usbDevice: UsbDevice? = null
    private var usbSerialPort: UsbSerialPort? = null
    private var inputManager: SerialInputOutputManager? = null
    private var isConnecting = false
    private var isConnected = false

    // Used to wait for USB permission callback from OBDUsbPermissionReceiver
    private var usbPermissionFuture: java.util.concurrent.CompletableFuture<UsbDevice?>? = null
    private var usbPermissionReceiver: BroadcastReceiver? = null

    // Response buffer for incoming OBD2 data
    private var lastResponse: String? = null

    /** Name of the currently connected USB device, or null if disconnected. */
    override val deviceName: String? get() = usbDevice?.deviceName

    /**
     * Set the CompletableFuture that will be completed when USB permission is granted/denied.
     * Call this before [open] when no permission exists for the device.
     */
    fun setUsbPermissionFuture(future: java.util.concurrent.CompletableFuture<UsbDevice?>?) {
        this.usbPermissionFuture = future
    }

    /**
     * Get available USB devices that could be OBD2 adapters.
     */
    fun getAvailableDevices(): List<UsbDevice> {
        usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        val devices = mutableListOf<UsbDevice>()

        for (device in usbManager?.deviceList.orEmpty().values) {
            if (isLikelyOBD2Device(device)) {
                devices.add(device)
            }
        }
        return devices
    }

    /**
     * Check if a USB device is likely an OBD2 adapter.
     */
    private fun isLikelyOBD2Device(device: UsbDevice): Boolean {
        for (i in 0 until device.interfaceCount) {
            val iface = device.getInterface(i)
            // CDC-ACM (class 0x02) - standard USB serial
            if (iface.getInterfaceClass() == 0x02 && iface.getInterfaceSubclass() == 0x02) {
                return true
            }
            if (iface.interfaceClass == 0x02) {
                return true
            }
            // Vendor Specific (class 0xFF) - CP2102, CH34x, PL2303, most ELM327 clones
            if (iface.interfaceClass == 0xFF) {
                return true
            }
        }
        return false
    }

    override suspend fun open() {
        Log.d(TAG, "Opening USB transport...")
        usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager

        // Check USB permission - request if not granted
        val device = usbDevice
        if (device != null && !usbManager!!.hasPermission(device)) {
            Log.d(TAG, "No USB permission, requesting...")
            val future = usbPermissionFuture
            if (future != null) {
                usbManager!!.requestPermission(device, null)
                try {
                    val resultDevice = future.get(10_000, java.util.concurrent.TimeUnit.MILLISECONDS)
                    if (resultDevice == null || !usbManager!!.hasPermission(resultDevice)) {
                        throw IOException("USB permission denied for device ${device.deviceName}")
                    }
                } catch (e: java.util.concurrent.TimeoutException) {
                    throw IOException("USB permission timeout for device ${device.deviceName}")
                }
            } else {
                throw IOException("No USB permission for device ${device.deviceName}")
            }
        }

        if (device == null) {
            throw IOException("No USB device set")
        }

        val driver = createDriver(device) ?: throw IOException("No supported driver for device ${device.deviceName}")

        val usbDeviceConnection = usbManager!!.openDevice(driver.device)
        if (usbDeviceConnection == null) {
            throw IOException("Failed to open USB device ${device.deviceName}")
        }
        driver.getPorts().first().open(usbDeviceConnection)

        usbSerialPort!!.setParameters(
            BaudRate,
            8,
            UsbSerialPort.STOPBITS_1,
            UsbSerialPort.PARITY_NONE
        )

        // Register broadcast receiver for USB permission result
        registerPermissionReceiver()

        // Initialize ELM327 with overall timeout
        try {
            withTimeout(CONNECT_TIMEOUT_MS.toLong()) {
                initializeAdapter()
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            Log.e(TAG, "Connection timed out after ${CONNECT_TIMEOUT_MS}ms")
            throw IOException("Connection timed out after ${CONNECT_TIMEOUT_MS}ms", e)
        }

        isConnected = true
        Log.d(TAG, "Connected to ${device.deviceName}")
    }

    override suspend fun sendCommand(command: String): String {
        if (!isConnected) {
            throw IOException("Not connected to OBD2 adapter")
        }

        val port = usbSerialPort ?: throw IOException("No USB serial port")

        // Send command
        port.write("${command}\r\n".toByteArray(StandardCharsets.US_ASCII), 1000)

        // Read response with timeout
        var response = ""
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < READ_TIMEOUT_MS) {
            val buffer = ByteArray(1024)
            val bytesRead = port.read(buffer, READ_TIMEOUT_MS)
            if (bytesRead > 0) {
                val text = String(buffer, 0, bytesRead, StandardCharsets.US_ASCII)
                response += text
                if (response.endsWith(ELM327_PROMPT) || response.contains("NO DATA")) {
                    break
                }
            }
        }

        lastResponse = response
        Log.d(TAG, "Command: $command → Response: $response")
        return response.trim()
    }

    override suspend fun sendCommandQuietly(command: String) {
        try {
            usbSerialPort?.write("${command}\r\n".toByteArray(StandardCharsets.US_ASCII), 1000)
        } catch (e: IOException) {
            Log.w(TAG, "Quiet command failed: $command", e)
        }
    }

    override fun isConnected(): Boolean = isConnected

    override fun close() {
        disconnect()
    }

    /**
     * Get the last raw response received from the OBD2 adapter.
     */
    fun getLastResponse(): String? = lastResponse

    /**
     * Check if the OBD2 adapter is responding.
     */
    override suspend fun ping(): Boolean {
        return try {
            val response = sendCommand("AT")
            response.contains("OK", ignoreCase = true)
        } catch (e: Exception) {
            Log.w(TAG, "Ping failed", e)
            false
        }
    }

    /**
     * Initialize the ELM327 adapter with standard commands.
     */
    private suspend fun initializeAdapter() {
        Log.d(TAG, "Initializing ELM327 adapter...")
        sendCommandQuietly(CMD_RESET)
        sendCommandQuietly(CMD_ECHO_OFF)
        sendCommandQuietly(CMD_LINEFEED_OFF)
        sendCommandQuietly(CMD_PROTOCOL_AUTO)
        Log.d(TAG, "ELM327 initialization complete")
    }

    /**
     * Create the appropriate USB serial driver for the device.
     */
    private fun createDriver(device: UsbDevice): UsbSerialDriver? {
        // Try CDC-ACM driver first (most common for ELM327)
        val cdcDriver = CdcAcmSerialDriver(device)
        if (cdcDriver.getPorts().isNotEmpty()) {
            Log.d(TAG, "Detected CDC-ACM driver")
            return cdcDriver
        }

        // Try FTDI driver (FT232R, FT232H, FT231XS)
        val ftdiDriver = FtdiSerialDriver(device)
        if (ftdiDriver.getPorts().isNotEmpty()) {
            Log.d(TAG, "Detected FTDI driver")
            return ftdiDriver
        }

        // Try Silicon Labs CP210x driver (CP2102, CP2102N - OBDLink SX/CX3)
        val cp21xxDriver = Cp21xxSerialDriver(device)
        if (cp21xxDriver.getPorts().isNotEmpty()) {
            Log.d(TAG, "Detected CP21xx driver")
            return cp21xxDriver
        }

        // Try CH34x driver (CH340, CH341 - common ELM327 clones)
        val ch34xDriver = Ch34xSerialDriver(device)
        if (ch34xDriver.getPorts().isNotEmpty()) {
            Log.d(TAG, "Detected CH34x driver")
            return ch34xDriver
        }

        // Try Prolific PL2303 driver
        val prolificDriver = ProlificSerialDriver(device)
        if (prolificDriver.getPorts().isNotEmpty()) {
            Log.d(TAG, "Detected Prolific driver")
            return prolificDriver
        }

        Log.d(TAG, "No supported driver found for device ${device.deviceName}")
        return null
    }

    /**
     * Register a broadcast receiver for USB permission result callbacks.
     */
    private fun registerPermissionReceiver() {
        val filter = IntentFilter(ACTION_USB_PERMISSION_RESULT)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent) {
                val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                val granted = intent.getBooleanExtra(KEY_PERMISSION_GRANTED, false)
                Log.d(TAG, "USB permission broadcast: device=${device?.deviceName}, granted=$granted")

                usbPermissionFuture?.let { future ->
                    if (granted && device != null) {
                        future.complete(device)
                    } else {
                        future.complete(null)
                    }
                }
                // Unregister after receiving the callback
                try {
                    context?.unregisterReceiver(this)
                } catch (e: Exception) {
                    Log.w(TAG, "Receiver already unregistered", e)
                }
            }
        }
        context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        usbPermissionReceiver = receiver
    }

    /**
     * Unregister the USB permission broadcast receiver.
     */
    private fun unregisterPermissionReceiver() {
        usbPermissionReceiver?.let { receiver ->
            try {
                context.unregisterReceiver(receiver)
            } catch (e: Exception) {
                // Receiver may already be unregistered
            }
            usbPermissionReceiver = null
        }
    }

    /**
     * Disconnect from the OBD2 adapter.
     */
    private fun disconnect() {
        try {
            inputManager?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping listener", e)
        }

        usbSerialPort?.close()
        usbSerialPort = null
        isConnected = false
        unregisterPermissionReceiver()
        Log.d(TAG, "Disconnected from OBD2 adapter")
    }

    /**
     * Set the USB device to connect to. Must be called before [open].
     */
    fun setDevice(device: UsbDevice) {
        this.usbDevice = device
    }

    /**
     * Connect to the first available OBD2 adapter.
     */
    suspend fun connectFirstAvailable() {
        val devices = getAvailableDevices()
        if (devices.isEmpty()) {
            Log.d(TAG, "No OBD2 adapters found")
            throw IOException("No OBD2 adapters found")
        }
        Log.d(TAG, "Connecting to first available device: ${devices.first().deviceName}")
        setDevice(devices.first())
        open()
    }
}
