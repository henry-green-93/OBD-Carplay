package com.henryg.obdcarplay

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.CoroutineExceptionHandler
import java.io.PrintWriter
import java.io.StringWriter

class UncaughtExceptionHandler(
    private val defaultHandler: Thread.UncaughtExceptionHandler?
) : Thread.UncaughtExceptionHandler {

    companion object {
        private const val TAG = "UncaughtExceptionHandler"

        // Store crash information
        @Volatile
        var lastCrashInfo: String? = null
            @JvmStatic
            set

        @Volatile
        var crashTimestamp: Long = 0L
            @JvmStatic
            set

        private lateinit var appContext: Context
        private var _coroutineHandler: CoroutineExceptionHandler? = null

        val coroutineExceptionHandler: CoroutineExceptionHandler
            get() = _coroutineHandler ?: throw IllegalStateException("UncaughtExceptionHandler.init() must be called first")

        fun init(context: Context) {
            appContext = context.applicationContext
            _coroutineHandler = CoroutineExceptionHandler { _, ex ->
                // Capture the exception info
                val sw = StringWriter()
                val pw = PrintWriter(sw)
                ex.printStackTrace(pw)
                val stacktrace = sw.toString()

                lastCrashInfo = "Exception: ${ex::class.simpleName}\nMessage: ${ex.message}\n\nStacktrace:\n$stacktrace"
                crashTimestamp = System.currentTimeMillis()

                Log.e(TAG, "Coroutine uncaught exception:\n$stacktrace")

                // Launch crash screen
                Handler(Looper.getMainLooper()).post {
                    try {
                        val intent = android.content.Intent(appContext, CrashScreenActivity::class.java).apply {
                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        }
                        appContext.startActivity(intent)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to launch CrashScreenActivity", e)
                    }
                }
            }
            val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler(
                UncaughtExceptionHandler(defaultHandler)
            )
        }
    }

    override fun uncaughtException(
        thread: Thread,
        ex: Throwable
    ) {
        // Capture the exception info
        val sw = StringWriter()
        val pw = PrintWriter(sw)
        ex.printStackTrace(pw)
        val stacktrace = sw.toString()

        lastCrashInfo = "Exception: ${ex::class.simpleName}\nMessage: ${ex.message}\n\nStacktrace:\n$stacktrace"
        crashTimestamp = System.currentTimeMillis()

        Log.e(TAG, "Uncaught exception:\n$stacktrace")

        // Launch crash screen
        Handler(Looper.getMainLooper()).post {
            try {
                val intent = android.content.Intent(appContext, CrashScreenActivity::class.java).apply {
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
                }
                appContext.startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to launch CrashScreenActivity", e)
            }
        }

        // Give the UI time to show, then let the system handle it
        Handler(Looper.getMainLooper()).postDelayed({
            defaultHandler?.uncaughtException(thread, ex)
        }, 3000)
    }
}