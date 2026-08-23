package com.henryg.obdcarplay.obd

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.util.Log
import com.hoho.android.usbserial.driver.CdcAcmSerialDriver
import com.hoho.android.usbserial.driver.FtdiSerialDriver
import com.hoho.android.usbserial.driver.ProbeTable
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.util.SerialInputOutputManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

/**
 * OBD2 Reader abstraction layer using USB-Serial for Android.
 *
 * Connects to an ELM327-compatible OBD2 adapter via USB, sends PID commands,
 * and parses responses. Supports both CDC-ACM and FTDI serial drivers.
 *
 * Usage:
 * ```
 * val reader = OBD2Reader(context)
 * reader.connect(usbDevice)
 * reader.sendCommand("01 00") // supported PIDs
 * val response = reader.readResponse()
 * reader.disconnect()
 * ```
 */
class OBD2Reader(private val context: Context) : AutoCloseable {

    companion object {
        private const val TAG = "OBD2Reader"
        private const val ELM327_PROMPT = ">"
        private const val READ_TIMEOUT_MS = 500
        private const val CONNECT_TIMEOUT_MS = 3000
        private const val BaudRate = 38400 // Default ELM327 baud

        // Standard OBD2 PID commands (Mode 01 - supported PIDs)
        const val PID_SUPPORTED_PIDS = "01 00"
        const val PID_ENGINE_RPM = "01 0C"
        const val PID_VEHICLE_SPEED = "01 0D"
        const val PID_ENGINE_COOLANT_TEMP = "01 05"
        const val PID_ENGINE_OIL_TEMP = "01 5C"
        const val PID_ENGINE_AIR_RATE = "01 04"
        const val PID_INTAKE_PRESSURE = "01 0B"
        const val PID_MAF_FLOW_RATE = "01 10"
        const val PID_THROTTLE_POSITION = "01 11"
        const val PID_AIR_FUEL_RATIO = "01 44"
        const val PID_COMMAND_2044 = "01 44" // AFR Mode 01
        const val PID_CONTROL_MODULE_VOLTAGE = "01 42"
        const val PID_TIMING_ADVANCE = "01 0E"
        const val PID_RUNNING_TIME = "01 0F"
        const val PID_ABSOLUTE_LOAD = "01 43"

        // ELM327 specific commands
        const val CMD_RESET = "AT Z"
        const val CMD_PROTOCOL_AUTO = "AT TP 0"
        const val CMD_ECHO_OFF = "ATE0"
        const val CMD_LINEFEED_OFF = "ATL0"
        const val CMD_OBD_VERSION = "AT ATV"
        const val CMD_ENGINE_RPM = "01 0C"
        const val PID_WATER_TEMP = "01 05"
        const val PID_BOOST_KPA = "01 0B" // Absolute manifold pressure
        const val PID_VACUUM_KPA = "01 0B" // Same PID, interpret based on context

        // PID decoding constants
        const val PID_AFR_STOICHIOMETRIC = 14.7

        // Supported USB vendor/product IDs for common OBD2 adapters
        val ELM327_USB_IDS = mapOf(
            "Valeo" to 0x0403,
            "OBDLink" to 0x0403,
            "ELM" to 0x0403
        )
    }

    private var usbManager: UsbManager? = null
    private var usbDevice: UsbDevice? = null
    private var usbSerialPort: UsbSerialPort? = null
    private var inputManager: SerialInputOutputManager? = null
    private var isConnecting = false
    private var isConnected = false
    private var isReading = false

    // Response buffer for incoming OBD2 data
    private var lastResponse: String? = null

    /** Name of the currently connected USB device, or null if disconnected. */
    val deviceName: String? get() = usbDevice?.deviceName

    /**
     * Get available USB devices that could be OBD2 adapters.
     */
    fun getAvailableDevices(): List<UsbDevice> {
        usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        val devices = mutableListOf<UsbDevice>()

        for (device in usbManager?.deviceList.orEmpty().values) {
            // Most OBD2 adapters are CDC-ACM or FTDI devices
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
        // CDC-ACM devices are most common for ELM327
        for (i in 0 until device.interfaceCount) {
            val iface = device.getInterface(i)
            if (iface.getInterfaceClass() == 0x02 && iface.getInterfaceSubclass() == 0x02) { // CDC-Communications
                return true
            }
            if (iface.interfaceClass == 0x02) { // Communications
                return true
            }
        }
        return false
    }

    /**
     * Connect to an OBD2 adapter via USB (suspend function with timeout).
     *
     * @param device The USB device to connect to
     * @param timeoutMillis Connection timeout in milliseconds (default: 5000ms)
     * @throws IOException if connection fails or times out
     */
    @Throws(IOException::class)
    suspend fun connect(device: UsbDevice, timeoutMillis: Long = 5000) {
        Log.d(TAG, "Connecting to ${device.deviceName} (timeout: ${timeoutMillis}ms)...")
        usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        usbDevice = device

        // Check USB permission
        if (!usbManager?.hasPermission(device)!!) {
            throw IOException("No USB permission for device ${device.deviceName}")
        }

        val driver = createDriver(device) ?: throw IOException("No supported driver for device")

        val usbDeviceConnection = usbManager!!.openDevice(driver.device)
        if (usbDeviceConnection == null) {
            throw IOException("Failed to open USB device")
        }
        driver.getPorts().first().open(usbDeviceConnection)

        usbSerialPort!!.setParameters(
            BaudRate,
            8,
            UsbSerialPort.STOPBITS_1,
            UsbSerialPort.PARITY_NONE
        )

        // Initialize ELM327 with overall timeout
        try {
            withTimeout(timeoutMillis) {
                initializeAdapter()
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            Log.e(TAG, "Connection timed out after ${timeoutMillis}ms")
            throw IOException("Connection timed out after ${timeoutMillis}ms", e)
        }

        isConnected = true
        Log.d(TAG, "Connected to ${device.deviceName}")
    }

    /**
     * Connect to the first available OBD2 adapter (suspend function).
     */
    @Throws(IOException::class)
    suspend fun connectFirstAvailable(timeoutMillis: Long = 5000) {
        val devices = getAvailableDevices()
        if (devices.isEmpty()) {
            Log.d(TAG, "No OBD2 adapters found")
            throw IOException("No OBD2 adapters found")
        }
        Log.d(TAG, "Connecting to first available device: ${devices.first().deviceName}")
        connect(devices.first(), timeoutMillis)
    }

    /**
     * Create the appropriate USB serial driver for the device.
     */
    private fun createDriver(device: UsbDevice): UsbSerialDriver? {
        // Try CDC-ACM driver first (most common for ELM327)
        val cdcDriver = CdcAcmSerialDriver(device)
        if (cdcDriver.getPorts().isNotEmpty()) {
            return cdcDriver
        }

        // Try FTDI driver
        val ftdiDriver = FtdiSerialDriver(device)
        if (ftdiDriver.getPorts().isNotEmpty()) {
            return ftdiDriver
        }

        return null
    }

    /**
     * Initialize the ELM327 adapter with standard commands.
     */
    private fun initializeAdapter() {
        Log.d(TAG, "Initializing ELM327 adapter...")

        // Reset ELM327
        Log.d(TAG, "Sending reset command (AT Z)...")
        sendCommandQuietly(CMD_RESET)

        // Turn off echo and linefeed
        Log.d(TAG, "Sending echo off command (ATE0)...")
        sendCommandQuietly(CMD_ECHO_OFF)
        Log.d(TAG, "Sending linefeed off command (ATL0)...")
        sendCommandQuietly(CMD_LINEFEED_OFF)

        // Auto-detect protocol
        Log.d(TAG, "Sending auto-detect protocol command (AT TP 0)...")
        sendCommandQuietly(CMD_PROTOCOL_AUTO)

        Log.d(TAG, "ELM327 initialization complete")
    }

    /**
     * Send a command to the ELM327 adapter and read the response.
     *
     * @param command The OBD2 command (e.g., "01 0C" for engine RPM)
     * @return The raw response string
     * @throws IOException if communication fails
     */
    @Throws(IOException::class)
    fun sendCommand(command: String): String {
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

    /**
     * Send a command without waiting for response (for initialization).
     */
    private fun sendCommandQuietly(command: String) {
        try {
            usbSerialPort?.write("${command}\r\n".toByteArray(StandardCharsets.US_ASCII), 1000)
        } catch (e: IOException) {
            Log.w(TAG, "Quiet command failed: $command", e)
        }
    }

    /**
     * Get the last raw response received from the OBD2 adapter.
     */
    fun getLastResponse(): String? = lastResponse

    /**
     * Disconnect from the OBD2 adapter.
     */
    fun disconnect() {
        try {
            inputManager?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping listener", e)
        }

        usbSerialPort?.close()
        usbSerialPort = null
        isConnected = false
        Log.d(TAG, "Disconnected from OBD2 adapter")
    }

    /**
     * Check if connected to an OBD2 adapter.
     */
    fun isConnected(): Boolean = isConnected

    /**
     * Check if the OBD2 adapter is responding.
     */
    fun ping(): Boolean {
        return try {
            val response = sendCommand("AT")
            response.contains("OK", ignoreCase = true)
        } catch (e: Exception) {
            Log.w(TAG, "Ping failed", e)
            false
        }
    }

    override fun close() {
        disconnect()
    }
}
