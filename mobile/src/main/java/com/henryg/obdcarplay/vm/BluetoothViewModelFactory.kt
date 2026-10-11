package com.henryg.obdcarplay.vm

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.henryg.obdcarplay.service.AndroidBleService
import com.henryg.obdcarplay.shared.BluetoothService


/**
 * Factory for creating BluetoothViewModel instances.
 *
 * @param context The application context for creating the AndroidBleService.
 */
class BluetoothViewModelFactory(
    private val context: Context
) : ViewModelProvider.Factory {

    private val bluetoothService: AndroidBleService by lazy {
        AndroidBleService(context.applicationContext)
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(BluetoothViewModel::class.java)) {
            return BluetoothViewModel(bluetoothService) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
