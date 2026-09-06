package com.henryg.obdcarplay.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.henryg.obdcarplay.service.BluetoothService
import com.henryg.obdcarplay.service.MockBluetoothService

/**
 * Factory for creating BluetoothViewModel instances.
 * In a real app, this would inject dependencies via Hilt/Koin.
 */
class BluetoothViewModelFactory(
    private val bluetoothService: BluetoothService = MockBluetoothService()
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(BluetoothViewModel::class.java)) {
            return BluetoothViewModel(bluetoothService) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}