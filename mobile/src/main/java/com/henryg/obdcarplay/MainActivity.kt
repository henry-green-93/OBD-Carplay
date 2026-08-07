package com.henryg.obdcarplay

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.app.ActivityCompat
import com.henryg.obdcarplay.ui.obdcluster.ObdNumericCluster
import com.henryg.obdcarplay.ui.theme.OBDCarPlayTheme
import com.henryg.obdcarplay.vm.ObdViewModel

class MainActivity : ComponentActivity() {
    companion object {
        private const val TAG = "MainActivity"
        private const val USB_PERMISSION_REQUEST_CODE = 42
    }

    private val viewModel: ObdViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OBDCarPlayTheme {
                val context = LocalContext.current

                // Auto-connect to OBD2 reader on startup
                LaunchedEffect(Unit) {
                    try {
                        val devices = viewModel.getAvailableDevices()
                        if (devices.isNotEmpty()) {
                            val device = devices.first()
                            if (ActivityCompat.checkSelfPermission(
                                    context,
                                    android.Manifest.permission.USB_PERMISSION
                                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                            ) {
                                viewModel.connectOBD(device)
                                Log.d(TAG, "Auto-connected to OBD2 adapter: ${device.deviceName}")
                            } else {
                                Log.d(TAG, "No OBD2 adapter, using mock data")
                                viewModel.useMockData()
                            }
                        } else {
                            Log.d(TAG, "No OBD2 adapter found, using mock data")
                            viewModel.useMockData()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error connecting to OBD2 adapter", e)
                        Toast.makeText(context, "OBD2 adapter not found — mock data", Toast.LENGTH_LONG).show()
                        viewModel.useMockData()
                    }
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
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
