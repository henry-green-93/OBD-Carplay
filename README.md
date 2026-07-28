# OBD-Carplay Dashboard

A high-frequency Android application designed to read and visualize OBD2 information from a car's ECU in real-time. Optimized for the **QCM6125 SOC**, aiming for a data refresh rate as close to **60Hz** as possible.

## 🚀 Project Overview
This project establishes a bridge between native C++ processing (for high-frequency USB/HIDL burst data) and a modern **Jetpack Compose** UI using **Material3**.

## 🏗️ Current Progress
- [x] **Project Scaffolding**: Set up `build.gradle` with Compose and Material3 dependencies.
- [x] **Native Bridge**: Initialized CMake and JNI stubs (`Natives.h`, `NativeHelper.cpp`) to handle low-level data processing.
- [x] **UI Layer**: Created a Numeric Cluster UI (`ObdNumericCluster`) to display key metrics without the overhead of complex needles.
- [x] **State Management**: Implemented `ObdViewModel` using `StateFlow` to provide a reactive stream of data to the UI.

## 📦 Components & Documentation

### 📱 UI Layer
- **`ObdNumericCluster.kt`**: The main UI component. It renders a BentoGrid-style cluster of numeric values.
    - *Status*: Finished (Numeric-only).
    - *Design*: Material3 Surface + Cards.

### 🧠 Logic Layer
- **`ObdViewModel.kt`**: Handles the business logic and state.
    - `obdState`: A `StateFlow<ObdData>` that the UI observes.
    - `updateData(water, oil, afr, boost)`: Updates the current state.

### ⚙️ Native Layer (C++)
- **`Natives.h`**: Header file for JNI declarations.
- **`NativeHelper.cpp`**: The core C++ logic. Designed to eventually handle the **9.5ms/iteration** cycle for 60Hz data bursts.
- **`NativeHelper_Mock.cpp`**: A dedicated mock file to simulate data flow during emulator testing.

## 🧪 Development Environment
- **Target SOC**: QCM6125
- **Refresh Target**: 60Hz (~9.5ms per iteration)
- **Emulator Config**: `.hermes/config-android-emulator-env.sh` provides the checklist and mock HAL stubs.

## 🛠️ How to Run
1. Pull the `ui-numeric-cluster` branch.
2. Run the build using the Android Emulator.
3. Check the numeric values for Water Temp, Oil Temp, and AFR+Boost KPA.
