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
├── mobile/           # Phones/tablets: Compose UI + native C++ JNI bridge (cmake)
└── automotive/       # Android Automotive: Car App Template-based OBD display
```

### Data Flow

1. **Native C++ layer** (`mobile/src/main/cpp/`) - USB/HIDL ISO-TP polling, sin/cos mock data
2. **Kotlin ViewModel** (`mobile/vm/ObdViewModel.kt`) - extends `ObdViewModelBase`, calls JNI
3. **Shared model** (`shared/ObdData.kt`) - `ObdData(waterTemp, oilTemp, afr, boostKpa)`
4. **UI layers** - Compose (mobile) / Templates (automotive) consume `StateFlow<ObdData>`

### Build System & Dependencies

The project uses [Version Catalogs](https://docs.gradle.org/current/userguide/platforms.html) via `gradle/libs.versions.toml`.

| Library | Version | Purpose |
|---|---|---|
| AGP | 9.3.1 | Android Gradle Plugin |
| Kotlin | 2.2.10 | Kotlin language |
| Coroutines | 1.10.1 | Async polling (60Hz) |
| Lifecycle | 2.6.1 | ViewModel, LiveData support |
| Compose BOM | 2026.02.01 | Jetpack Compose |

## 🏗️ Current Progress

- [x] **Project Scaffolding**: `:mobile`, `:automotive`, `:shared` modules with Car App support
- [x] **Native Bridge**: CMake + JNI stubs (`Natives.h`, `NativeHelper.cpp`) for low-level data
- [x] **Mobile UI**: `ObdNumericCluster` Compose screen with Material3 NumericCard widgets
- [x] **Automotive UI**: `ObdCarScreen` using Car App `MessageTemplate` with `CarText` (migrated from `Metric` in Car App 1.7.0)
- [x] **Shared Layer**: `ObdData` data class + `ObdViewModelBase` with 60Hz polling loop

## 📦 Component Details

### Mobile (`:mobile`)
- **`MainActivity.kt`** - Entry point, wires `ObdViewModel` into Compose `ObdNumericCluster`
- **`ObdViewModel.kt`** (`vm/`) - JNI bindings + sin/cos mock polling at 16ms intervals
- **`ObdNumericCluster.kt`** (`ui/obdcluster/`) - Compose UI: Material3 Cards layout
- **Native C++** (`src/main/cpp/`) - JNI declarations, mock data generation

### Automotive (`:automotive`)
- **`ObdCarScreen.kt`** (`ui/obdcluster/`) - Car App `Screen` with `MessageTemplate` + `CarText` (Car App 1.7.0+)
- Depends on `CarAppService`, `CarAppSession`, `CarAppActivity`

### Shared (`:shared`)
- **`ObdData.kt`** - `data class` with waterTemp, oilTemp, afr, boostKpa + `afrBoostDisplay`
- **`ObdViewModelBase.kt`** - Abstract base class with `CoroutineScope`-backed `StateFlow<ObdData>` polling loop. Subclasses implement `nativeFetchNextMockData()` for platform-specific data sources.
- **`MyCarAppService.kt`** - `CarAppService` host validator + session factory
- **`MyCarAppSession.kt`** - `Session` creating `ObdCarScreen`
- **`automotive_app_desc.xml`** - declares template usage for Car App

## 🧪 Development

- **Target SOC**: QCM6125
- **Refresh Target**: 60Hz (~16ms per iteration)
- **Min SDK**: 34 (Android 14)
- **Compile SDK**: 36

## 🛠️ How to Run

1. **Mobile**: Run the `:mobile` module on a phone/tablet AVD (Android 14+) or device
2. **Automotive**: Run the `:automotive` module on an automotive AVD with templates host
3. **Android Auto**: The `:mobile` module also registers as a projected Android Auto app

> [!TIP]
> If you get an `AndroidLocationsException` during build, run with `unset ANDROID_PREFS_ROOT` before invoking Gradle.

## 📝 Recent Changes

### Build System & Dependencies
- Added `lifecycle-viewmodel-ktx` and `kotlinx-coroutines-android` to the version catalog
- Added `android-library` and `kotlin-android` plugins to version catalog
- `:shared` module now depends on `app-automotive:1.7.0` (for `CarAppService`/`Session`/`Screen` classes)
- `:mobile` module excludes `app` from `app-projected` to prevent manifest conflicts with `app-automotive`
- Added `CarAppMetadataHolderService` override in mobile `AndroidManifest.xml` with `tools:node="replace"` to resolve `CAR_HARDWARE_MANAGER` conflict

### Native Layer
- Fixed `NativeHelper.cpp`: removed orphaned `extern "C"` declaration that caused `expected unqualified-id` compile error

### Shared Module
- `ObdViewModelBase.kt`: made class `abstract`, added `CoroutineScope` for polling (replaced deprecated `viewModelScope` from lifecycle 2.6.x), removed `android.R.attr.delay` import conflict
- `MyCarAppSession.kt`: creates anonymous subclass of `ObdViewModelBase` with stub `nativeFetchNextMockData()` implementation

### Automotive UI
- `ObdCarScreen.kt`: migrated from Car App 1.7.0 `Metric.Builder()` → `CarText.create()` API
- `MessageTemplate.Builder.setPrimaryText()` → `setTitle()` (deprecated in 1.7.0)

### Mobile UI
- `ObdNumericCluster.kt`: added `modifier: Modifier` parameter for proper `Scaffold` integration