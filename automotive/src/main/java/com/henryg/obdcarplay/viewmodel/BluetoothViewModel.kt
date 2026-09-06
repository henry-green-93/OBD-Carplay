package com.henryg.obdcarplay.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.henryg.obdcarplay.data.BleDevice
import com.henryg.obdcarplay.service.BluetoothService
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * Represents the possible connection states for the Bluetooth adapter.
 */
sealed class ConnectionState {
    object Disconnected : ConnectionState()
    object Scanning : ConnectionState()
    data class Connecting(val deviceName: String) : ConnectionState()
    data class Connected(val device: BleDevice) : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}

/**
 * Data class holding the current state exposed to the UI.
 */
data class BluetoothUiState(
    val connectionState: ConnectionState = ConnectionState.Disconnected,
    val availableDevices: List<BleDevice> = emptyList(),
    val isLoading: Boolean = false,
)

/**
 * ViewModel responsible for managing the Bluetooth connection lifecycle.
 *
 * @param service The service layer responsible for actual Bluetooth operations.
 */
class BluetoothViewModel(
    private val bluetoothService: BluetoothService
) : ViewModel() {

    private val _uiState = MutableStateFlow(BluetoothUiState())
    val uiState: StateFlow<BluetoothUiState> = _uiState.asStateFlow()

    init {
        // Load initial state or start a passive scan on initialization
        _uiState.update { it.copy(isLoading = true) }
        // Assume service has a function to retrieve initial/cached devices
        viewModelScope.launch {
            _uiState.update { it.copy(availableDevices = bluetoothService.getAvailableDevices()) }
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    /**
     * Starts the process of scanning for nearby OBD2 Bluetooth devices.
     */
    fun startScan() {
        if (_uiState.value.connectionState == ConnectionState.Scanning || _uiState.value.isLoading) return

        _uiState.update { it.copy(connectionState = ConnectionState.Scanning, isLoading = true) }
        viewModelScope.launch {
            // Listen to device updates from the service
            bluetoothService.observeDevices().collect { devices ->
                _uiState.update { it.copy(availableDevices = devices) }
            }
            // Update loading state when scan completes (or service signals completion)
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    /**
     * Attempts to establish a connection to a specified device.
     * @param device The device to connect to.
     */
    fun connectDevice(device: BleDevice) {
        if (_uiState.value.connectionState is ConnectionState.Connecting || _uiState.value.connectionState is ConnectionState.Connected) return

        _uiState.update { it.copy(connectionState = ConnectionState.Connecting(device.name), isLoading = true) }

        viewModelScope.launch {
            try {
                val success = bluetoothService.connectToDevice(device)
                if (success) {
                    _uiState.update { it.copy(connectionState = ConnectionState.Connected(device), isLoading = false) }
                } else {
                    // Connection failed, revert state
                    _uiState.update { it.copy(connectionState = ConnectionState.Disconnected, isLoading = false) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(connectionState = ConnectionState.Error(e.localizedMessage ?: "Connection failed"), isLoading = false) }
            }
        }
    }

    /**
     * Initiates the pairing process for a specified device.
     * @param device The device to pair with.
     */
    fun pairDevice(device: BleDevice) {
        if (_uiState.value.connectionState is ConnectionState.Connecting) return // Already trying to connect

        _uiState.update { it.copy(connectionState = ConnectionState.Connecting(device.name), isLoading = true) }

        viewModelScope.launch {
            try {
                val success = bluetoothService.pairWithDevice(device)
                if (success) {
                    // If pairing is successful, often we connect immediately afterward
                    _uiState.update { it.copy(connectionState = ConnectionState.Connected(device), isLoading = false) }
                } else {
                    // Pairing failed, revert state
                    _uiState.update { it.copy(connectionState = ConnectionState.Disconnected, isLoading = false) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(connectionState = ConnectionState.Error(e.localizedMessage ?: "Pairing failed"), isLoading = false) }
            }
        }
    }

    /**
     * Disconnects the currently connected device and resets the state.
     */
    fun disconnectDevice() {
        viewModelScope.launch {
            bluetoothService.disconnect()
            _uiState.update {
                when (it.connectionState) {
                    is ConnectionState.Connected -> it.copy(connectionState = ConnectionState.Disconnected, isLoading = false)
                    // If not connected, just ensure we are in Disconnected state
                    else -> it.copy(connectionState = ConnectionState.Disconnected, isLoading = false)
                }
            }
        }
    }
}

// --- MOCK SERVICE INTERFACE AND DATA CLASSES (to make the file self-contained) ---
// In a real project, these would be in separate files.

/**
 * Interface for the Bluetooth service layer.
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
     * Disconnects from the currently active device.
     */
    suspend fun disconnect()
}

/**
 * Data model representing a Bluetooth Low Energy (BLE) device.
 */
data class BleDevice(
    val address: String,
    val name: String,
    val rssi: Int // Received Signal Strength Indicator (dBm)
)

// NOTE: A concrete implementation of BluetoothService must be provided (e.g., in a companion object or separate file)
// and injected into the ViewModel.
// For this implementation, we assume a basic mock implementation exists or will be provided.