package com.henryg.obdcarplay.data

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.henryg.obdcarplay.shared.BleDevice

// Re-export BleDevice from shared module
public typealias BleDevice = com.henryg.obdcarplay.shared.BleDevice

/**
 * Displays a single Bluetooth OBD2 device as a clickable card.
 */
@Composable
fun BleDeviceCard(
    device: BleDevice,
    onClick: () -> Unit,
    onConnect: () -> Unit,
    onPair: () -> Unit
) {
    val isConnected = device.isConnected
    val isPairing = device.isPairing

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isConnected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Device info
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (isConnected) Icons.Default.BluetoothConnected else Icons.Default.Bluetooth,
                        contentDescription = if (isConnected) "Connected" else "Bluetooth",
                        tint = if (isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.padding(8.dp))
                    Text(
                        text = device.name.ifBlank { "Unknown OBD2 Adapter" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(modifier = Modifier.padding(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Signal: ${device.rssi} dBm",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.padding(12.dp))
                    if (isConnected) {
                        Text(
                            text = "Connected",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else if (isPairing) {
                        Text(
                            text = "Pairing...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Text(
                            text = "Tap to connect",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Right: Action buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!isConnected) {
                    FilledTonalButton(
                        onClick = onConnect,
                        enabled = !isPairing
                    ) {
                        Text("Connect")
                    }
                } else {
                    FilledTonalButton(
                        onClick = onConnect
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Disconnect"
                        )
                    }
                }
            }
        }
    }
}
