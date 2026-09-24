package com.printylan.usb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume

class UsbPermissions(context: Context, private val usbManager: UsbManager) {
    private val context = context.applicationContext
    private val action = "${context.packageName}.USB_PERMISSION"
    private val mutex = Mutex()

    fun has(device: UsbDevice): Boolean = usbManager.hasPermission(device)

    /** Shows the system USB permission dialog if needed. Returns true once access is granted. */
    suspend fun request(device: UsbDevice): Boolean = mutex.withLock {
        if (has(device)) return true
        suspendCancellableCoroutine { cont ->
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    val target = IntentCompat.getParcelableExtra(intent, UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                    if (target?.deviceName != device.deviceName) return
                    context.unregisterReceiver(this)
                    if (cont.isActive) {
                        cont.resume(intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false))
                    }
                }
            }
            ContextCompat.registerReceiver(context, receiver, IntentFilter(action), ContextCompat.RECEIVER_NOT_EXPORTED)
            cont.invokeOnCancellation { runCatching { context.unregisterReceiver(receiver) } }

            // The system fills in the grant extras, so the intent must be mutable on Android 12+.
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            val intent = Intent(action).setPackage(context.packageName)
            usbManager.requestPermission(device, PendingIntent.getBroadcast(context, device.deviceId, intent, flags))
        }
    }
}
