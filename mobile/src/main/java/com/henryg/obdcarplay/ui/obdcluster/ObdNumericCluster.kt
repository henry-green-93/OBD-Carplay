package com.henryg.obdcarplay.ui.obdcluster

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.henryg.obdcarplay.vm.ObdViewModel

/**
 * Compose UI rendering the OBD2 numeric cluster on mobile devices.
 *
 * Uses Material3 Cards in a bento-grid layout to display:
 * - Water Temperature
 * - Oil Temperature
 * - AFR + Boost Pressure
 */
@Composable
fun ObdNumericCluster(viewModel: ObdViewModel) {
    val data by viewModel.obdState.collectAsState()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
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
                    value = "${data.waterTemp}\u00B0C",
                    modifier = Modifier.weight(1f)
                )
                NumericCard(
                    label = "Oil Temp",
                    value = "${data.oilTemp}\u00B0C",
                    modifier = Modifier.weight(1f)
                )
            }

            // Full-width card for AFR + Boost
            NumericCard(
                label = "AFR + Boost",
                value = data.afrBoostDisplay,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

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
                fontWeight = FontWeight.Bold
            )
        }
    }
}
