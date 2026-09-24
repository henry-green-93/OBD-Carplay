package com.henryg.obdcarplay.ui.screens

import android.content.Context
import android.graphics.Paint
import android.graphics.Paint.Align
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.henryg.obdcarplay.data.BleDevice
import com.henryg.obdcarplay.data.BleDeviceCard
import com.henryg.obdcarplay.shared.OBD2ConnectionStatus
import com.henryg.obdcarplay.shared.ObdData
import com.henryg.obdcarplay.ui.obdcluster.NumericCard
import com.henryg.obdcarplay.vm.ObdViewModel
import com.henryg.obdcarplay.vm.ObdViewModelFactory
import java.util.Locale
import kotlinx.coroutines.delay

/**
 * Unified Dashboard that displays OBD2 data regardless of source.
 *
 * Shows real-time metrics when connected to a real OBD2 adapter,
 * simulated data when in mock mode, and connection options when disconnected.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Dashboard(
    viewModel: ObdViewModel = viewModel(factory = ObdViewModelFactory(LocalContext.current)),
) {
    val context = LocalContext.current
    val data by viewModel.obdState.collectAsState()
    val connectionStatus by viewModel.connectionStatus.collectAsState()
    val isConnected = viewModel.isLive

    // USB setup
    val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager

    // Bluetooth tab selection
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = USB, 1 = Bluetooth

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("OBD CarPlay") },
                actions = {
                    // Mock data toggle
                    IconButton(onClick = { viewModel.useMockData() }) {
                        Icon(
                            Icons.Default.AutoFixHigh,
                            contentDescription = "Switch to Mock Data",
                            tint = if (viewModel.isMockMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    // Disconnect button (when connected)
                    if (isConnected) {
                        IconButton(onClick = { viewModel.obdManager.disconnect() }) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Disconnect"
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        bottomBar = {
            // Bottom action buttons
            Surface(
                tonalElevation = 4.dp,
                shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .windowInsetsPadding(WindowInsets.navigationBars)
                ) {
                    if (!isConnected && connectionStatus !is OBD2ConnectionStatus.EcUConnected) {
                        // Connection options when disconnected
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { viewModel.connectFirstAvailable() },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.ConnectWithoutContact, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Auto Connect")
                            }
                            Button(
                                onClick = { viewModel.useMockData() },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Mock Data")
                            }
                        }
                    } else {
                        // When connected, show connect/switch options
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { viewModel.connectFirstAvailable() },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Reconnect")
                            }
                            OutlinedButton(
                                onClick = { viewModel.useMockData() },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Mock Data")
                            }
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .padding(bottom = paddingValues.calculateBottomPadding())
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Connection status banner
            ConnectionBanner(connectionStatus)

            Spacer(modifier = Modifier.height(16.dp))

            // Data content or connection options
            when {
                isConnected -> {
                    // Live metrics grid
                    LiveMetricsGrid(data)
                }
                viewModel.isMockMode -> {
                    // Mock data display
                    MockMetricsGrid()
                }
                else -> {
                    // Disconnected state with connection options
                    DisconnectedView(
                        usbManager = usbManager,
                        selectedTab = selectedTab,
                        onTabSelected = { selectedTab = it },
                        onDeviceSelected = { device ->
                            viewModel.connectOBD(device)
                        }
                    )
                }
            }
        }
    }
}

/**
 * Connection status banner (shared logic from ActualDashboard).
 */
@Composable
private fun ConnectionBanner(status: OBD2ConnectionStatus) {
    var icon: ImageVector = Icons.Default.Error
    var title = "Disconnected"
    var subtitle = "No OBD2 adapter connected"
    var containerColor = MaterialTheme.colorScheme.errorContainer
    var contentColor = MaterialTheme.colorScheme.onErrorContainer

    when (status) {
        is OBD2ConnectionStatus.Disconnected -> {
            icon = Icons.Default.Error
            title = "Disconnected"
            subtitle = "No OBD2 adapter connected"
            containerColor = MaterialTheme.colorScheme.errorContainer
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        }
        is OBD2ConnectionStatus.UsbConnected -> {
            icon = Icons.Default.Sync
            title = "OBD2 Reader Connected"
            subtitle = "Waiting for ECU data..."
            containerColor = MaterialTheme.colorScheme.secondaryContainer
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        }
        is OBD2ConnectionStatus.EcUConnected -> {
            icon = Icons.Default.CheckCircle
            title = "ECU Connected"
            subtitle = status.deviceName
            containerColor = MaterialTheme.colorScheme.primaryContainer
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        }
        is OBD2ConnectionStatus.Error -> {
            icon = Icons.Default.Error
            title = "Error"
            subtitle = status.message
            containerColor = MaterialTheme.colorScheme.errorContainer
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = containerColor
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(24.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = contentColor
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.7f)
                )
            }
        }
    }
}

/**
 * Grid of real-time metrics from live OBD2 data.
 */
@Composable
private fun LiveMetricsGrid(data: ObdData) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Row: Water Temp + Oil Temp
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
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

        Spacer(modifier = Modifier.height(16.dp))

        // AFR card
        NumericCard(
            label = "AFR",
            value = String.format(Locale.US, "%.2f", data.afr),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Boost/Vacuum card
        NumericCard(
            label = "Boost/Vac",
            value = "${data.boostKpa} kPa",
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Grid of mock/simulated metrics when no real adapter is connected.
 */
@Composable
private fun MockMetricsGrid() {
    var refreshCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            refreshCount++
        }
    }

    val mockRPM = 2000 + (refreshCount % 500)
    val mockSpeed = 60 + (refreshCount % 20)
    val mockTemp = 90 + (refreshCount % 10)
    val mockBoost = -5 + (refreshCount % 10)
    val mockAFR = 14.7f + (refreshCount % 10) * 0.01f

    Column(modifier = Modifier.fillMaxWidth()) {
        // Header
        Text(
            text = "Mock Dashboard",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "Simulated data — tap 'Auto Connect' to use real OBD2 data",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        Spacer(modifier = Modifier.height(24.dp))

        // RPM Gauge visualization
        MockGaugeChart(rpm = mockRPM)

        Spacer(modifier = Modifier.height(16.dp))

        // Row: RPM + Speed
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            MetricCardSimple(
                label = "RPM",
                value = mockRPM.toString(),
                modifier = Modifier.weight(1f),
                color = Color(0xFF4CAF50)
            )
            MetricCardSimple(
                label = "Speed",
                value = mockSpeed.toString(),
                unit = "km/h",
                modifier = Modifier.weight(1f),
                color = Color(0xFF2196F3)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Row: Engine Temp + Boost
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            MetricCardSimple(
                label = "Engine Temp",
                value = mockTemp.toString(),
                unit = "°C",
                modifier = Modifier.weight(1f),
                color = Color(0xFFFF9800)
            )
            MetricCardSimple(
                label = "Boost/Vac",
                value = mockBoost.toString(),
                unit = "kPa",
                modifier = Modifier.weight(1f),
                color = Color(0xFF9C27B0)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // AFR card - full width
        MetricCardSimple(
            label = "AFR",
            value = String.format(Locale.US, "%.2f", mockAFR),
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFF00BCD4)
        )
    }
}

/**
 * Simple metric card for mock mode display.
 */
@Composable
private fun MetricCardSimple(
    label: String,
    value: String,
    unit: String = "",
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary
) {
    Card(
        modifier = modifier.height(120.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = color
            )
            if (unit.isNotBlank()) {
                Text(
                    text = unit,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * RPM gauge visualization (from old MockDashboard).
 */
@Composable
private fun MockGaugeChart(rpm: Int) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val centerX = size.width / 2
                val centerY = size.height / 2
                

                // Background arc (270 degrees)
                drawArc(
                    color = Color.LightGray.copy(alpha = 0.3f),
                    startAngle = 135f,
                    sweepAngle = 270f,
                    useCenter = false,
                    style = Stroke(width = 20.dp.toPx())
                )

                // RPM arc
                val rpmAngle = ((rpm % 8000) / 8000f) * 270f
                val rpmColor = if (rpm > 6000) Color.Red else Color.Green
                drawArc(
                    color = rpmColor,
                    startAngle = 135f,
                    sweepAngle = rpmAngle.coerceAtMost(270f),
                    useCenter = false,
                    style = Stroke(width = 20.dp.toPx())
                )
            }
            Text(
                text = "$rpm RPM",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/**
 * Disconnected view with inline connection options (USB + Bluetooth tabs).
 */
@Composable
private fun DisconnectedView(
    usbManager: UsbManager,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    onDeviceSelected: (UsbDevice) -> Unit,
) {
    // Tab selector
    @OptIn(ExperimentalMaterial3Api::class)
TabRow(
        selectedTabIndex = selectedTab,
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        indicator = { tabPositions ->
            TabRowDefaults.SecondaryIndicator(
                modifier = Modifier
                    .offset(x = tabPositions[selectedTab].left)
                    .size(
                        width = tabPositions[selectedTab].right - tabPositions[selectedTab].left,
                        height = 2.dp
                    ),
                color = MaterialTheme.colorScheme.primary
            )
        }
    ) {
        Tab(
            selected = selectedTab == 0,
            onClick = { onTabSelected(0) },
            text = { Text("USB") }
        )
        Tab(
            selected = selectedTab == 1,
            onClick = { onTabSelected(1) },
            text = { Text("Bluetooth") }
        )
    }

    Spacer(modifier = Modifier.height(16.dp))

    when (selectedTab) {
        0 -> UsbConnectionPanel(
            usbManager = usbManager,
            onDeviceSelected = onDeviceSelected
        )
        1 -> BluetoothConnectionPanel()
    }
}

/**
 * USB connection panel showing detected devices.
 */
@Composable
private fun UsbConnectionPanel(
    usbManager: UsbManager,
    onDeviceSelected: (UsbDevice) -> Unit,
) {
    val usbDevices = usbManager.deviceList.values.toList()

    if (usbDevices.isEmpty()) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Icon(
                    Icons.Default.Usb,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(32.dp).align(Alignment.CenterHorizontally)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "No USB OBD2 adapters detected",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Plug in your OBD2 adapter to get started.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        usbDevices.forEach { device ->
            val deviceName = device.deviceName
            Spacer(modifier = Modifier.height(8.dp))
            UsbDeviceCard(
                deviceName = deviceName ?: "Unknown",
                vendorId = device.vendorId,
                productId = device.productId,
                onConnect = { onDeviceSelected(device) }
            )
        }
    }
}

/**
 * Single USB device card.
 */
@Composable
private fun UsbDeviceCard(
    deviceName: String,
    vendorId: Int,
    productId: Int,
    onConnect: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = deviceName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Vendor: $vendorId | Product: $productId",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            FilledTonalButton(onClick = onConnect) {
                Icon(Icons.Default.ConnectWithoutContact, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Connect")
            }
        }
    }
}

/**
 * Bluetooth connection panel with stubbed device list.
 */
@Composable
private fun BluetoothConnectionPanel() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Icon(
                Icons.Default.Bluetooth,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(32.dp).align(Alignment.CenterHorizontally)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Bluetooth OBD2 Adapters",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Real Bluetooth scanning coming soon. For now, use USB or Mock data.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Show mock devices as placeholder
            BleDevice.MockDevices.forEach { device ->
                Spacer(modifier = Modifier.height(8.dp))
                BleDeviceCard(
                    device = device,
                    onClick = {},
                    onConnect = {},
                    onPair = {}
                )
            }
        }
    }
}
