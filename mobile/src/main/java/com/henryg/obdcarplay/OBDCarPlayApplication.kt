package com.henryg.obdcarplay

import android.app.Application

class OBDCarPlayApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        UncaughtExceptionHandler.init(this)
    }
}
