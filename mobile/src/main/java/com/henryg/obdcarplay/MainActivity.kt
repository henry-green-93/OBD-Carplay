package com.henryg.obdcarplay

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.henryg.obdcarplay.ui.screens.BluetoothScreen
import com.henryg.obdcarplay.ui.theme.OBDCarPlayTheme
import com.henryg.obdcarplay.vm.BluetoothViewModel
import com.henryg.obdcarplay.vm.BluetoothViewModelFactory
import com.henryg.obdcarplay.vm.ConnectionState

class MainActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            OBDCarPlayTheme {
                // Use ViewModel with factory
                val viewModel: BluetoothViewModel = viewModel(factory = BluetoothViewModelFactory())
                
                val uiState by viewModel.uiState.collectAsState()

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("OBD2 Bluetooth") },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            actions = {
                                // Connection status indicator
                                IconButton(
                                    onClick = { viewModel.startScan() }
                                ) {
                                    Icon(
                                        Icons.Default.Refresh,
                                        contentDescription = "Scan/Refresh",
                                        tint = when (uiState.connectionState) {
                                            is ConnectionState.Connected -> MaterialTheme.colorScheme.primary
                                            ConnectionState.Scanning -> MaterialTheme.colorScheme.primary
                                            else -> MaterialTheme.colorScheme.error
                                        }
                                    )
                                }
                            }
                        )
                    }
                ) { paddingValues ->
                    BluetoothScreen(viewModel = viewModel, paddingValues = paddingValues)
                }
            }
        }
    }
}