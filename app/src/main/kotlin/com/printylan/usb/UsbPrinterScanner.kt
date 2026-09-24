package com.printylan.usb

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Keeps a live list of attached USB printers. */
class UsbPrinterScanner(context: Context, private val usbManager: UsbManager) {
    private val _printers = MutableStateFlow(scan())
    val printers: StateFlow<List<UsbPrinter>> = _printers.asStateFlow()

    init {
        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = refresh()
        }
        ContextCompat.registerReceiver(
            context.applicationContext,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    fun refresh() {
        _printers.value = scan()
    }

    fun find(localId: String?): UsbPrinter? = printers.value.firstOrNull { it.localId == localId }

    private fun scan(): List<UsbPrinter> {
        val seen = mutableMapOf<String, Int>()
        return usbManager.deviceList.values
            .sortedBy { it.deviceName }
            .mapNotNull { device ->
                // Serial numbers need USB permission on Android 10+, so ids use vendor and
                // product, plus an index when several identical printers are attached.
                val base = "usb-%04x-%04x".format(device.vendorId, device.productId)
                val index = seen.merge(base, 1, Int::plus)!!
                UsbPrinter.from(device, if (index == 1) base else "$base-$index")
            }
    }
}
