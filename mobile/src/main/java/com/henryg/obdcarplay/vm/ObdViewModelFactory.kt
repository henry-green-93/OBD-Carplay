package com.henryg.obdcarplay.vm

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

/**
 * Factory for creating ObdViewModel instances.
 * Requires a Context to initialize the OBD2Manager.
 */
class ObdViewModelFactory(
    private val context: Context
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ObdViewModel::class.java)) {
            return ObdViewModel(context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
