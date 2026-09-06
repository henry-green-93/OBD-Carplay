package com.henryg.obdcarplay.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.henryg.obdcarplay.data.BleDevice

/**
 * A clickable item representing a single Bluetooth OBD2 device.
 *
 * @param device The BleDevice data.
 * @param onConnectClicked Callback triggered when the user taps to connect.
 * @param onPairClicked Callback triggered when the user taps to pair.
 */
@Composable
fun DeviceListItem(
    device: BleDevice,
    onConnectClicked: () -> Unit,
    onPairClicked: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onConnectClicked) // Primary action: Connect
            .padding(vertical = 12.dp, horizontal = 16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // 1. Device Name and Status Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.name.ifBlank { "Unknown OBD2 Adapter" },
                    style = MaterialTheme.typography.titleMedium
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Bluetooth,
                        contentDescription = "Bluetooth Signal",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${device.rssi} dBm",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 2. Action Buttons
            Row(
                modifier = Modifier.onePxHeight().padding(start = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Pair Button (Secondary Action)
                IconButton(
                    onClick = onPairClicked,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Link, // Using Link icon for pairing
                        contentDescription = "Pair",
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }

                // Connection Indicator (Implicitly handled by Card click, but useful for explicit icon)
                Icon(
                    imageVector = Icons.Default.ArrowForward, // Indicating 'Connect' action
                    contentDescription = "Connect",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}