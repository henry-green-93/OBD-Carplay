package com.hermes.obd2.qcm.nativeui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Data model for the 3 primary OBD2 PIDs for the QCM6125 test.
 */
data class ObdData(
    val waterTemp: Int = 0,
    val oilTemp: Int = 0,
    val afrBoost: String = "0.0, 0Vac"
)

class ObdNumericViewModel : ViewModel() {
    private val _obdState = MutableStateFlow(ObdData())
    val obdState: StateFlow<ObdData> = _obdState

    init {
        // Initial mock data
        _obdState.value = ObdData(89, 70, "1.42, 9Vac")
    }

    fun updateData(water: Int, oil: Int, afr: Double, boost: Int) {
        _obdState.value = ObdData(
            waterTemp = water,
            oilTemp = oil,
            afrBoost = "%.2f, %dVac".format(afr, boost)
        )
    }
}

@Composable
fun ObdNumericCluster(viewModel: ObdNumericViewModel) {
    val data by viewModel.obdState.collectAsState()

    Surface(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Text(
                text = "OBD2 Numeric Cluster",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                NumericCard("Water Temp", "${data.waterTemp}°C", Modifier.weight(1f))
                NumericCard("Oil Temp", "${data.oilTemp}°C", Modifier.weight(1f))
            }

            NumericCard(
                "AFR + Boost",
                data.afrBoost,
                Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun NumericCard(label: String, value: String, modifier: Modifier) {
    Card(
        modifier = modifier.height(150.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
        ) {
            Text(text = label, style = MaterialTheme.typography.labelMedium)
            Text(text = value, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
        }
    }
}
