package com.henryg.obdcarplay.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.henryg.obdcarplay.ui.components.DeviceListItem
import com.henryg.obdcarplay.viewmodel.BluetoothViewModel
import com.henryg.obdcarplay.viewmodel.ConnectionState
import com.henryg.obdcarplay.viewmodel.BluetoothUiState
import com.henryg.obdcarplay.data.BleDevice
import com.henryg.obdcarplay.viewmodel.BluetoothViewModelFactory // Assuming a factory exists

/**
 * Main screen for managing Bluetooth connections to OBD2 adapters.
 *
 * @param viewModel The ViewModel holding the connection state and logic.
 */
@Composable
fun BluetoothScreen(
    viewModel: BluetoothViewModel = viewModel(factory = BluetoothViewModelFactory()) // Assuming this factory exists
) {
    // Observe the UI state from the ViewModel
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("OBD2 Bluetooth") })
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. Connection Status Display
            ConnectionStatusCard(state = uiState.connectionState)
            Spacer(modifier = Modifier.height(24.dp))

            // 2. Scan Button
            ScanButton(
                state = uiState.connectionState,
                isLoading = uiState.isLoading,
                onClick = viewModel::startScan
            )
            Spacer(modifier = Modifier.height(24.dp))

            // 3. Device List
            Text(text = if (uiState.availableDevices.isEmpty()) "No devices found. Tap Scan to look around." else "${uiState.availableDevices.size} devices found.",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(12.dp))

            if (uiState.availableDevices.isNotEmpty()) {
                DeviceList(
                    devices = uiState.availableDevices,
                    onDeviceSelected = viewModel::connectDevice, // Default action
                    onPairSelected = viewModel::pairDevice
                )
            }
        }
    }
}

/**
 * Displays the current connection status in a styled card.
 */
@Composable
fun ConnectionStatusCard(state: ConnectionState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val (icon, title, description) = when (state) {
                is ConnectionState.Disconnected -> (Icons.Default.SignalOff, "Disconnected", "Tap Scan to find an OBD2 adapter.")
                is ConnectionState.Scanning -> (Icons.Default.Search, "Scanning...", "Searching for nearby devices...")
                is ConnectionState.Connecting -> (Icons.Default.Build, "Connecting to ${state.deviceName}", "Attempting connection...")
                is ConnectionState.Connected -> (Icons.Default.CheckCircle, "Connected", "Successfully paired and connected!")
                is ConnectionState.Error -> (Icons.Default.Error, "Error", state.message)
            }

            Icon(icon, contentDescription = title, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            Spacer(modifier = Modifier.height(8.dp))
            Text(title, style = MaterialTheme.typography.headlineSmall)
            Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
        }
    }
}

/**
 * Provides the Scan button UI.
 */
@Composable
fun ScanButton(
    state: ConnectionState,
    isLoading: Boolean,
    onClick: () -> Unit
) {
    val buttonText = when (state) {
        ConnectionState.Disconnected -> "Scan for Devices"
        ConnectionState.Connected(_) -> "Rescan Devices"
        ConnectionState.Scanning -> "Scanning..." // Already handled by state, but good for explicit state
        is ConnectionState.Connecting -> "Wait..."
        is ConnectionState.Error -> "Try Again"
    }

    Button(
        onClick = onClick,
        enabled = !isLoading && (state !is ConnectionState.Scanning),
        modifier = Modifier.fillMaxWidth()
    ) {
        if (isLoading && state !is ConnectionState.Scanning) {
            CircularProgressIndicator(Modifier.size(24.dp))
        } else {
            Text(buttonText)
        }
    }
}

/**
 * Displays the list of discovered devices.
 */
@Composable
fun DeviceList(
    devices: List<BleDevice>,
    onDeviceSelected: (BleDevice) -> Unit,
    onPairSelected: (BleDevice) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth()
        ) {
            items(devices) { device ->
                // We will use a custom item that allows choosing between connect/pair
                DeviceListItem(
                    device = device,
                    onConnectClicked = { onDeviceSelected(device) },
                    onPairClicked = { onPairSelected(device) }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}