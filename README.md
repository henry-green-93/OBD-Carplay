# OBD-Carplay Dashboard

A high-frequency Android application designed to read and visualize OBD2 information from a car's ECU in real-time. Optimized for the **QCM6125 SOC**, aiming for a data refresh rate as close to **60Hz** as possible.

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

## 🏗️ Current Progress

- [x] **Project Scaffolding**: `:mobile`, `:automotive`, `:shared` modules with Car App support
- [x] **Native Bridge**: CMake + JNI stubs (`Natives.h`, `NativeHelper.cpp`) for low-level data
- [x] **Mobile UI**: `ObdNumericCluster` Compose screen with Material3 NumericCard widgets
- [x] **Automotive UI**: `ObdCarScreen` using Car App `MessageTemplate` with `Metric` objects
- [x] **Shared Layer**: `ObdData` data class + `ObdViewModelBase` with 60Hz polling loop

## 📦 Component Details

### Mobile (`:mobile`)
- **`MainActivity.kt`** - Entry point, wires `ObdViewModel` into Compose `ObdNumericCluster`
- **`ObdViewModel.kt`** (`vm/`) - JNI bindings + sin/cos mock polling at 16ms intervals
- **`ObdNumericCluster.kt`** (`ui/obdcluster/`) - Compose UI: Material3 Cards layout
- **Native C++** (`src/main/cpp/`) - JNI declarations, mock data generation

### Automotive (`:automotive`)
- **`ObdCarScreen.kt`** (`ui/obdcluster/`) - Car App `Screen` with `MessageTemplate` + `Metric`
- Depends on `CarAppService`, `CarAppSession`, `CarAppActivity`

### Shared (`:shared`)
- **`ObdData.kt`** - `data class` with waterTemp, oilTemp, afr, boostKpa + `afrBoostDisplay`
- **`ObdViewModelBase.kt`** - Abstract base with `StateFlow<ObdData>` polling loop
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