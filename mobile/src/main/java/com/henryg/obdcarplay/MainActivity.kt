package com.henryg.obdcarplay

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import com.henryg.obdcarplay.ui.obdcluster.ObdNumericCluster
import com.henryg.obdcarplay.ui.theme.OBDCarPlayTheme
import com.henryg.obdcarplay.vm.ObdViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    companion object {
        private const val TAG = "MainActivity"
        private const val USB_PERMISSION_REQUEST_CODE = 42
    }

    private lateinit var viewModel: ObdViewModel

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ObdViewModel(baseContext)
        enableEdgeToEdge()
        setContent {
            OBDCarPlayTheme {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("OBD2 Live Monitor") },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            actions = {
                                // Connection status indicator
                                IconButton(
                                    onClick = { viewModel.useMockData() }
                                ) {
                                    Icon(
                                        Icons.Default.Refresh,
                                        contentDescription = "Switch to mock data",
                                        tint = if (viewModel.isLive) 
                                            MaterialTheme.colorScheme.primary 
                                        else 
                                            MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        )
                    }
                ) { innerPadding ->
                    val context = LocalContext.current

                    // Auto-connect to OBD2 reader on startup (with timeout)
                    LaunchedEffect(Unit) {
                        Log.d(TAG, "Starting OBD2 auto-connect sequence...")
                        val devices = viewModel.obdManager.getAvailableDevices()
                        Log.d(TAG, "Found ${devices.size} USB device(s)")
                        
                        if (devices.isNotEmpty()) {
                            val device = devices.first()
                            try {
                                // Try to connect with a 3-second timeout
                                viewModel.connectOBDWithTimeout(device, 3000)
                                Log.d(TAG, "Auto-connected to OBD2 adapter: ${device.deviceName}")
                            } catch (e: Exception) {
                                Log.e(TAG, "Connection failed, falling back to mock data: ${e.message}")
                                viewModel.useMockData()
                            }
                        } else {
                            Log.d(TAG, "No OBD2 adapter found, starting mock data")
                            viewModel.useMockData()
                        }
                    }

                    ObdNumericCluster(
                        viewModel = viewModel,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ObdClusterPreview() {
    val mockViewModel = ObdViewModel(LocalContext.current)
    OBDCarPlayTheme {
        ObdNumericCluster(mockViewModel)
    }
}