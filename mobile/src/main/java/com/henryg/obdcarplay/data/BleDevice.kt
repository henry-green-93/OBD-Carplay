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
