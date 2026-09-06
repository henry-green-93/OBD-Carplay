# Bluetooth Pairing and Connection UI - Walkthrough

## Changes Made

### Mobile Module (`:mobile`)

#### [NEW] data/BleDevice.kt
- Data model representing a Bluetooth OBD2 device
- Contains: `address`, `name`, `rssi`

#### [NEW] service/BluetoothService.kt
- Interface defining Bluetooth operations: `getAvailableDevices()`, `observeDevices()`, `connectToDevice()`, `pairWithDevice()`, `disconnect()`
- Includes `MockBluetoothService` implementation for testing/demo

#### [NEW] vm/BluetoothViewModel.kt
- Manages Bluetooth connection lifecycle
- Defines:
  - `ConnectionState` sealed class (Disconnected, Scanning, Connecting, Connected, Error)
  - `BluetoothUiState` data class for UI state
- Methods: `startScan()`, `connectDevice()`, `pairDevice()`, `disconnectDevice()`

#### [NEW] vm/BluetoothViewModelFactory.kt
- ViewModelProvider.Factory for creating BluetoothViewModel instances
- Injects MockBluetoothService by default

#### [NEW] ui/screens/BluetoothScreen.kt
- Main composable for Bluetooth UI
- Components:
  - `ConnectionStatusCard()` - Shows current connection state with icon and description
  - `ScanButton()` - Scan/refresh button with loading state
  - `DeviceList()` - Lists discovered devices
  - `DeviceListItem()` (delegates to component)

#### [NEW] ui/components/DeviceListItem.kt
- Reusable card for each discovered Bluetooth device
- Shows device name, signal strength (dBm)
- Provides "Connect" (tap card) and "Pair" (icon button) actions

#### [MODIFY] MainActivity.kt
- Replaced ObdViewModel with BluetoothViewModel
- Uses BluetoothViewModelFactory for DI
- Connects BluetoothScreen with padding support

#### [MODIFY] gradle/libs.versions.toml
- Added `lifecycleViewmodelCompose` version and library reference

#### [MODIFY] mobile/build.gradle.kts
- Added `libs.androidx.lifecycle.viewmodel.compose` dependency

### Automotive Module (`:automotive`)

#### [NEW] viewmodel/BluetoothViewModel.kt
- Standalone Bluetooth ViewModel implementation (for automotive use)
- Includes same ConnectionState and BluetoothUiState classes
- Includes mock BluetoothService interface

## Verification

✅ Build successful (`mobile:compileDebugKotlin`)
✅ Build successful (`mobile:assembleDebug`)
- All errors resolved
- Only warnings remain (trailing commas, deprecated icons, etc.)

## File Structure

```
mobile/src/main/java/com/henryg/obdcarplay/
├── data/
│   └── BleDevice.kt                    # Device data model
├── service/
│   └── BluetoothService.kt             # Bluetooth interface + mock
├── ui/
│   ├── components/
│   │   └── DeviceListItem.kt           # Device list card
│   ├── screens/
│   │   └── BluetoothScreen.kt          # Main Bluetooth UI
│   └── theme/
│       └── (existing theme files)
├── vm/
│   ├── BluetoothViewModel.kt           # Connection state management
│   ├── BluetoothViewModelFactory.kt    # ViewModel factory
│   └── ObdViewModel.kt                 # Existing OBD ViewModel
├── MainActivity.kt                      # Updated to use Bluetooth
└── (other existing files)
```

## Next Steps (Future Enhancements)

1. **Replace MockBluetoothService** with real Android BluetoothAdapter/BluetoothGatt implementation
2. **Add Bluetooth permissions** to AndroidManifest.xml
3. **Add Bluetooth permission request** runtime handling for Android 12+
4. **Integrate with automotive module** if Bluetooth is shared across modules
5. **Add unit tests** for BluetoothViewModel
6. **Add instrumented tests** for BluetoothScreen UI
7. **Add navigation** to Bluetooth screen from main app
