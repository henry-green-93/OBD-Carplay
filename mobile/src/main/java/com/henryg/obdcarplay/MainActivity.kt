package com.henryg.obdcarplay

import android.content.Context
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.util.Log
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
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    companion object {
        private const val TAG = "MainActivity"
        private const val USB_PERMISSION_REQUEST_CODE = 42
    }

    private lateinit var viewModel: ObdViewModel
    private var usbPermissionReceiver: OBDUsbPermissionReceiver? = null
    private var usbPermissionFuture: CompletableFuture<UsbDevice?>? = null

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ObdViewModel(baseContext)
        enableEdgeToEdge()

        // Set up USB permission receiver
        usbPermissionReceiver = OBDUsbPermissionReceiver()
        viewModel.obdManager.setUsbPermissionFuture(usbPermissionFuture)

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
                                // Register permission receiver and request permission
                                usbPermissionFuture = CompletableFuture()
                                val filter = IntentFilter(OBDUsbPermissionReceiver.ACTION_USB_PERMISSION_RESULT)
                                @Suppress("DEPRECATION", "UnspecifiedRegisterReceiverFlag")
                                registerReceiver(usbPermissionReceiver, filter, "0x00000000" /*RECEIVER_NOT_EXPORTED*/, null)

                                val usbManager = getSystemService(Context.USB_SERVICE) as UsbManager
                                if (!usbManager.hasPermission(device)) {
                                    Log.d(TAG, "Requesting USB permission for ${device.deviceName}")
                                    usbManager.requestPermission(device, null)
                                }



                                // Wait up to 5 seconds for permission, then try connecting
                                val permissionGranted = usbPermissionFuture?.get(5, TimeUnit.SECONDS) != null
                                Log.d(TAG, "USB permission granted: $permissionGranted")

                                if (permissionGranted) {
                                    try {
                                        viewModel.connectOBD(device)
                                        Log.d(TAG, "Auto-connected to OBD2 adapter: ${device.deviceName}")
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Connection failed: ${e.message}")
                                        viewModel.useMockData()
                                    }
                                } else {
                                    Log.d(TAG, "Permission timeout, starting mock data")
                                    viewModel.useMockData()
                                }
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