# OBD-Carplay Dashboard

A high-frequency Android application designed to read and visualize OBD2 information from a car's ECU in real-time. Optimized for the **QCM6125 SOC**, aiming for a data refresh rate as close to **60Hz** as possible.

> [!NOTE]
> **Build Configuration**: Requires `ANDROID_PREFS_ROOT` to be unset (or pointing to the same path as `ANDROID_USER_HOME`) when building with AGP 9.3.1 to avoid a `AndroidLocationsException` during Gradle configuration.

## Project Structure

This project uses a multi-module Gradle setup targeting three Android platforms:

| Module | Target Platform | UI Technology |
|---|---|---|
| `:mobile` | Phones & Tablets (Android 14+) + Android Auto (Projected) | Jetpack Compose |
| `:automotive` | Android Automotive OS (standalone) | Car App Library (Template API) |
| `:shared` | Shared library (data model, base ViewModel, Car App service/session) | N/A |

## Core Architecture

```
OBD-Carplay/
├── shared/           # ObdData model + ObdViewModelBase + Car App Service/Session
├── mobile/           # Phones/tablets: Compose UI + OBD2 USB reader + native C++ bridge
└── automotive/       # Android Automotive: Car App Template-based OBD display
```

### Data Flow

1. **USB OBD2 Reader** (`mobile/obd/OBD2Reader`) - USB-Serial ELM327 adapter, CDC-ACM/FTDI
2. **PID Parser** (`mobile/obd/OBD2PidParser`) - ELM327 response parsing (RPM, temp, AFR, boost)
3. **OBD2 Manager** (`mobile/obd/OBD2Manager`) - Polling loop, connects/disconnects, ~60Hz
4. **ViewModel** (`mobile/vm/ObdViewModel`) - Bridges OBD2Manager → UI StateFlow, mock fallback
5. **Shared Model** (`shared/ObdData`) - `ObdData(waterTemp, oilTemp, afr, boostKpa)`
6. **UI layers** - Compose (mobile) / Templates (automotive) consume `StateFlow<ObdData>`

### Build System & Dependencies

The project uses [Version Catalogs](https://docs.gradle.org/current/userguide/platforms.html) via `gradle/libs.versions.toml`.

| Library | Version | Purpose |
|---|---|---|
| AGP | 9.3.1 | Android Gradle Plugin |
| Kotlin | 2.2.10 | Kotlin language |
| Coroutines | 1.10.1 | Async polling (60Hz) |
| Lifecycle | 2.6.1 | ViewModel, LiveData support |
| Compose BOM | 2026.02.01 | Jetpack Compose |
| USB-Serial-for-Android | 3.7.1 | ELM327 OBD2 adapter communication |

## 🏗️ Current Progress

- [x] **Project Scaffolding**: `:mobile`, `:automotive`, `:shared` modules with Car App support
- [x] **Native Bridge**: CMake + JNI stubs (`Natives.h`, `NativeHelper.cpp`) for low-level data
- [x] **Mobile UI**: `ObdNumericCluster` Compose screen with Material3 NumericCard widgets
- [x] **Automotive UI**: `ObdCarScreen` using Car App `MessageTemplate` with `CarText`
- [x] **Shared Layer**: `ObdData` data class + `ObdViewModelBase` polling lifecycle
- [x] **OBD2 USB Reader**: `OBD2Reader` + `OBD2PidParser` + `OBD2Manager` for real ECU data
- [x] **Connection UI**: Connection status banner, connect/disconnect buttons, mock toggle

## 📦 Component Details

### Mobile (`:mobile`)
- **`MainActivity.kt`** - Entry point, auto-connects OBD2 reader, wires ViewModel → UI
- **`ObdViewModel.kt`** (`vm/`) - Bridges OBD2Manager ↔ UI StateFlow, mock fallback
- **`ObdNumericCluster.kt`** (`ui/obdcluster/`) - Compose UI: Material3 Cards, connection banner
- **`OBD2Reader.kt`** (`obd/`) - USB-serial ELM327 adapter connection (CDC-ACM + FTDI)
- **`OBD2PidParser.kt`** (`obd/`) - ELM327 response parsing (RPM, temp, AFR, boost)
- **`OBD2Manager.kt`** (`obd/`) - PID polling loop (~60Hz), connect/disconnect lifecycle
- **`device_filter.xml`** - USB device filter for ELM327 adapters
- **Native C++** (`src/main/cpp/`) - JNI declarations, mock data generation (placeholder)

### Automotive (`:automotive`)
- **`ObdCarScreen.kt`** (`ui/obdcluster/`) - Car App `Screen` with `MessageTemplate` + `CarText`
- Depends on `CarAppService`, `CarAppSession`, `CarAppActivity`

### Shared (`:shared`)
- **`ObdData.kt`** - `data class` with waterTemp, oilTemp, afr, boostKpa + `afrBoostDisplay`
- **`ObdViewModelBase.kt`** - Abstract base class with `StateFlow<ObdData>`, `startPolling()`/`stopPolling()`
- **`MyCarAppService.kt`** - `CarAppService` host validator + session factory
- **`MyCarAppSession.kt`** - `Session` creating `ObdCarScreen` with mock automotive polling
- **`automotive_app_desc.xml`** - declares template usage for Car App

## 🧪 Development

- **Target SOC**: QCM6125
- **Refresh Target**: 60Hz (~16ms per iteration)
- **Min SDK**: 34 (Android 14)
- **Compile SDK**: 36

## 🛠️ How to Run

1. **Mobile**: Run the `:mobile` module on a phone/tablet AVD (Android 14+) or device with OBD2 adapter
   - Auto-connects to first available ELM327 adapter on startup
   - Falls back to mock sin/cos data if no adapter found
2. **Automotive**: Run the `:automotive` module on an automotive AVD with templates host
3. **Android Auto**: The `:mobile` module also registers as a projected Android Auto app

> [!TIP]
> If you get an `AndroidLocationsException` during build, run with `unset ANDROID_PREFS_ROOT` before invoking Gradle.

## 📝 Recent Changes

### OBD2 USB Reader Connection
- Added `OBD2Reader` — USB-serial ELM327 adapter layer (CDC-ACM + FTDI, usb-serial-for-android 3.7.1)
- Added `OBD2PidParser` — ELM327 response decoder (RPM: (256A+B)/4, temp: A-40, AFR: 200A/B, boost: A-40)
- Added `OBD2Manager` — PID polling loop with ~60Hz sequential polling (waterTemp, oilTemp, AFR, boost)
- Added USB device filter (`device_filter.xml`) for ELM327 adapters
- Added USB host feature and permission to `AndroidManifest.xml`
- Updated `ObdViewModel` — bridges OBD2Manager ↔ UI, auto-connect, mock fallback on failure
- Updated `ObdNumericCluster` — connection status banner, connect/disconnect/mock buttons
- Updated `MainActivity` — auto-connects OBD2 adapter on startup
- Updated `ObdViewModelBase` — lifecycle-safe with `startPolling()`/`stopPolling()` instead of auto-polling
- Updated `MyCarAppSession` — automotive-specific mock polling in Session

### Build System
- Added `usb-serial-for-android` dependency to mobile module
- Added `lifecycle-viewmodel-ktx` and `kotlinx-coroutines-android` to shared module
- Added `android-library` and `kotlin-android` plugins to version catalog
- `:shared` module depends on `app-automotive:1.7.0` (for `CarAppService`/`Session`/`Screen` classes)
- `:mobile` module excludes `app` from `app-projected` to prevent manifest conflicts
- Added `CarAppMetadataHolderService` override in mobile `AndroidManifest.xml`

### Native Layer
- Fixed `NativeHelper.cpp`: removed orphaned `extern "C"` declaration

### Shared Module
- `ObdViewModelBase.kt`: made class `abstract`, added `CoroutineScope` for polling
- `MyCarAppSession.kt`: creates anonymous subclass of `ObdViewModelBase` with stub `nativeFetchNextMockData()`

### Automotive UI
- `ObdCarScreen.kt`: migrated from Car App 1.7.0 `Metric.Builder()` → `CarText.create()` API
- `MessageTemplate.Builder.setPrimaryText()` → `setTitle()` (deprecated in 1.7.0)

### Mobile UI
- `ObdNumericCluster.kt`: added `modifier: Modifier` parameter, connection status banner
