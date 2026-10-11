package com.henryg.obdcarplay.shared

import android.content.Intent
import androidx.car.app.Screen
import androidx.car.app.Session
import com.henryg.obdcarplay.obd.OBD2Manager
import com.henryg.obdcarplay.obd.UsbSerialTransport
import com.henryg.obdcarplay.ui.obdcluster.ObdCarScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Automotive Car App Session.
 *
 * Creates an automotive-specific ObdViewModelBase with real OBD2Manager
 * that connects to a USB OBD2 adapter and polls PIDs from the ECU.
 */
class MyCarAppSession : Session() {
    override fun onCreateScreen(intent: Intent): Screen {
        val oemContext = carContext

        // Create USB transport and OBD2 manager
        val usbTransport = UsbSerialTransport(oemContext)
        val manager = OBD2Manager(usbTransport)

        val viewModel = object : ObdViewModelBase() {
            // Scope for the polling coroutine (bound to session lifecycle)
            private val scope = CoroutineScope(Dispatchers.Default + Job())

            override fun startPolling() {
                // Collect OBD data and update ViewModel
                scope.launch {
                    manager.obdData.collectLatest { data ->
                        updateObdData(data)
                    }
                }

                // Collect connection status and update ViewModel
                scope.launch {
                    manager.connectionStatus.collectLatest { status ->
                        updateConnectionStatus(status)
                        // Update obdState with connection status
                        updateObdData(ObdData.live(status))
                    }
                }

                // Attempt to connect to first available OBD2 adapter
                scope.launch(Dispatchers.IO) {
                    try {
                        // Connect to first available USB device
                        val devices = usbTransport.getAvailableDevices()
                        if (devices.isNotEmpty()) {
                            usbTransport.setDevice(devices.first())
                            manager.connect()
                        } else {
                            // No USB device, try BLE discovery
                            updateConnectionStatus(OBD2ConnectionStatus.Disconnected)
                        }
                    } catch (_: Exception) {
                        // Connection failed — status already updated by manager
                    }
                }
            }
        }
        viewModel.startPolling()
        return ObdCarScreen(oemContext, viewModel)
    }
}
