package com.henryg.obdcarplay

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.util.Log

/**
 * Broadcast receiver for USB device permission callbacks.
 *
 * When the system grants/denies USB permission for an OBD2 adapter,
 * this receiver sends a sticky Intent that OBD2Reader can pick up.
 */
class OBDUsbPermissionReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "OBDUsbPermission"
        const val KEY_PERMISSION_GRANTED = "permission_granted"
        const val ACTION_USB_PERMISSION_RESULT = "com.henryg.obdcarplay.USB_PERMISSION_RESULT"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE) ?: return

        val granted = usbManager.hasPermission(device)
        Log.d(TAG, "USB permission ${if (granted) "granted" else "denied"} for ${device.deviceName}")

        // Send a sticky result intent back to OBD2Reader
        val resultIntent = Intent(ACTION_USB_PERMISSION_RESULT)
        resultIntent.putExtra(UsbManager.EXTRA_DEVICE, device)
        resultIntent.putExtra(KEY_PERMISSION_GRANTED, granted)
        setJobIntent(context, resultIntent)
    }

    /**
     * Send a job intent (works on API 31+ without FLAG_RECEIVER_EXPORTED).
     */
    private fun setJobIntent(context: Context, intent: Intent) {
        val flags = if (android.os.Build.VERSION.SDK_INT >= 31) {
            Intent.FLAG_ACTIVITY_NEW_TASK or PendingIntent.FLAG_IMMUTABLE
        } else {
            0
        }
        @Suppress("DEPRECATION")
        PendingIntent.getBroadcast(context, 0, intent, flags)
    }
}
