# Bluetooth Pairing and Connection UI Implementation

## Goal Description

The objective is to create the user interface components within the `:automotive` module that allow the user to discover, select, pair, and connect to a Bluetooth OBD2 adapter. This UI will drive the logic in the Bluetooth service (which is assumed to be built out) and display the connection status and adapter details.

## User Review Required

*   **UI Flow/Navigation:** The primary flow should be:
    1.  Initial Screen (e.g., `MainActivity` or a dedicated `BluetoothScreen`) shows current status (Disconnected/Connecting/Connected).
    2.  If Disconnected, a "Pair" or "Scan" button is prominent.
    3.  Tapping "Scan" initiates a device search.
    4.  A list/RecyclerView appears showing discoverable OBD2 devices.
    5.  Tapping a device initiates connection/pairing.
    6.  The status updates (e.g., "Pairing...", "Connected to [DeviceName]").
*   **Design System Adherence:** We will use the established Compose Material 3 design language for consistency with the rest of the app.

## Open Questions

*   **Target Screen:** Should this UI be integrated directly into the main `MainActivity`'s content, or should we create a dedicated composable/screen (e.g., `BluetoothScreen`) and navigate to it? (Assumption: A dedicated screen is cleaner.)
*   **Connection State Management:** Which existing ViewModel (or should we create a new one, e.g., `BluetoothViewModel`) will hold the connection state (e.g., `ConnectionState.Disconnected`, `ConnectionState.Scanning`, `ConnectionState.Connected(Device)`), and how will it be exposed to the UI? (Assumption: A new `BluetoothViewModel` is best practice.)

## Proposed Changes

We will focus the changes primarily within the `:automotive` module.

### :automotive (App Module)

#### [NEW] ui/screens/BluetoothScreen.kt
This will contain the main Composable function for the Bluetooth UI.
*   **Function:** `BluetoothScreen(viewModel: BluetoothViewModel)`
*   **Contents:** State observation, UI layout (status, scan button, list of devices, connection controls).

#### [NEW] ui/components/DeviceListItem.kt
A reusable Composable for each device displayed in the list.
*   **Contents:** Device name, signal strength, connection status icon, and tap handler.

#### [NEW] viewmodel/BluetoothViewModel.kt
The ViewModel responsible for managing Bluetooth state and interacting with the underlying Bluetooth service.
*   **Contents:** `StateFlow` for connection status, `StateFlow` for list of devices, functions like `startScan()`, `connectDevice(device: BleDevice)`, `pairDevice(device: BleDevice)`.

#### [MODIFY] MainActivity.kt
Update the main activity to host the `BluetoothScreen`.
*   **Change:** Replace existing placeholder/initial screen content with a `NavHost` or direct composition of `BluetoothScreen()`.

#### [MODIFY] AutomotiveApplication.kt (Optional, if using Hilt/Koin)
If necessary, update dependency injection configuration to provide `BluetoothViewModel` to `MainActivity`. (Assuming standard Hilt setup for now.)

## Verification Plan

### Automated Tests
- **Unit Tests:** Write unit tests for `BluetoothViewModel.kt` to verify state transitions (e.g., `scan()` -> `devices` list populated -> `connect()` -> `Connected` state).
- **Integration Tests:** Write an instrumented test for `BluetoothScreen.kt` to verify that button clicks (Scan/Connect) correctly trigger the expected state changes in the ViewModel.

### Manual Verification
- **Device Test:** Run the app on a physical device (or emulator with Bluetooth enabled).
- **Test Flow:**
    1.  Verify the initial screen shows the correct default state (e.g., "Disconnected").
    2.  Trigger the Scan and verify a list of available OBD2 devices appears.
    3.  Select a known OBD2 device and verify the UI changes to "Pairing..." and then "Connected to [Device Name]".
    4.  Trigger a disconnect/reconnect action and verify the UI transitions correctly.