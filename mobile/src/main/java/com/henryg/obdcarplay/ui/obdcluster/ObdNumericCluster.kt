package com.henryg.obdcarplay.ui.obdcluster

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.henryg.obdcarplay.shared.OBD2ConnectionStatus
import com.henryg.obdcarplay.shared.label
import com.henryg.obdcarplay.shared.ObdData
import com.henryg.obdcarplay.vm.ObdViewModel

/**
 * Compose UI rendering the OBD2 numeric cluster on mobile devices.
 *
 * Shows real-time OBD2 data from a connected ELM327 adapter or mock data.
 * Includes connection status indicator and connect/disconnect buttons.
 */
@Composable
fun ObdNumericCluster(viewModel: ObdViewModel, modifier: Modifier = Modifier) {
    val data by viewModel.obdState.collectAsState()
    val isConnected = viewModel.isLive
    val connectionStatus by viewModel.connectionStatus.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Connection status banner
        ConnectionBanner(isConnected, connectionStatus)

        // Main data cluster
        Text(
            text = "OBD2 Numeric Cluster",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Row: Water Temp + Oil Temp side by side
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            NumericCard(
                label = "Water Temp",
                value = "${data.waterTemp}°C",
                modifier = Modifier.weight(1f)
            )
            NumericCard(
                label = "Oil Temp",
                value = "${data.oilTemp}°C",
                modifier = Modifier.weight(1f)
            )
        }

        // Full-width card for AFR + Boost
        NumericCard(
            label = "AFR + Boost",
            value = data.afrBoostDisplay,
            modifier = Modifier.fillMaxWidth()
        )

        // Spacer for bottom of screen
        Spacer(modifier = Modifier.weight(1f))

        // Action buttons row at bottom
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Button(
                onClick = { viewModel.connectFirstAvailable() },
                modifier = Modifier.weight(1f)
            ) {
                Text("Connect OBD2")
            }
            Button(
                onClick = { viewModel.useMockData() },
                modifier = Modifier.weight(1f)
            ) {
                Text("Use Mock Data")
            }
            if (isConnected) {
                Button(
                    onClick = { viewModel.obdManager.disconnect() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Disconnect")
                }
            }
        }
    }
}

/**
 * Banner showing connection status.
 */
@Composable
private fun ConnectionBanner(isConnected: Boolean, status: OBD2ConnectionStatus) {
    var icon = "🔴"
    var message = "Disconnected"
    var containerColor = MaterialTheme.colorScheme.errorContainer
    var contentColor = MaterialTheme.colorScheme.onErrorContainer

    when (status) {
        is OBD2ConnectionStatus.Disconnected -> {
            icon = "🔴"
            message = "Disconnected — tap Connect OBD2"
            containerColor = MaterialTheme.colorScheme.errorContainer
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        }
        is OBD2ConnectionStatus.UsbConnected -> {
            icon = "🟡"
            message = "OBD2 Reader Connected — waiting for ECU"
            containerColor = MaterialTheme.colorScheme.secondaryContainer
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        }
        is OBD2ConnectionStatus.EcUConnected -> {
            icon = "🟢"
            message = "ECU Connected"
            containerColor = MaterialTheme.colorScheme.primaryContainer
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        }
        is OBD2ConnectionStatus.Error -> {
            icon = "⚠️"
            message = "Error: ${status.message}"
            containerColor = MaterialTheme.colorScheme.errorContainer
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = containerColor
    ) {
        Text(
            text = "$icon $message",
            style = MaterialTheme.typography.labelLarge,
            color = contentColor,
            modifier = Modifier.padding(8.dp)
        )
    }
}

/**
 * Single data card for displaying a metric value.
 */
@Composable
fun NumericCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.height(150.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium
            )
            Text(
                text = value,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp
            )
        }
    }
}
