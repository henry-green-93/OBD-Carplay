package com.henryg.obdcarplay.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.henryg.obdcarplay.shared.BleDevice
import com.henryg.obdcarplay.shared.BluetoothConnectionStatus
import com.henryg.obdcarplay.shared.BluetoothOBD2Manager
import com.henryg.obdcarplay.shared.ObdData
import com.henryg.obdcarplay.service.CarBluetoothService
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
 * @param bluetoothService The Car App HAL Bluetooth service.
 */
class BluetoothViewModel(
    private val bluetoothService: CarBluetoothService
) : ViewModel() {

    private val _uiState = MutableStateFlow(BluetoothUiState())
    val uiState: StateFlow<BluetoothUiState> = _uiState.asStateFlow()

    init {
        // Load initial state or start a passive scan on initialization
        _uiState.update { it.copy(isLoading = true) }
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
            bluetoothService.startScan()
            bluetoothService.observeDevices().collect { devices ->
                _uiState.update { it.copy(availableDevices = devices) }
            }
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
        if (_uiState.value.connectionState is ConnectionState.Connecting) return

        _uiState.update { it.copy(connectionState = ConnectionState.Connecting(device.name), isLoading = true) }

        viewModelScope.launch {
            try {
                val success = bluetoothService.pairWithDevice(device)
                if (success) {
                    _uiState.update { it.copy(connectionState = ConnectionState.Connected(device), isLoading = false) }
                } else {
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
                    else -> it.copy(connectionState = ConnectionState.Disconnected, isLoading = false)
                }
            }
        }
    }
}

/**
 * Factory for creating BluetoothViewModel instances with CarBluetoothService.
 */
class BluetoothViewModelFactory(
    private val bluetoothService: CarBluetoothService
) : androidx.lifecycle.ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(BluetoothViewModel::class.java)) {
            return BluetoothViewModel(bluetoothService) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
