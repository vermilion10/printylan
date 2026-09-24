package com.printylan

import android.app.Application
import android.content.Context
import android.hardware.usb.UsbManager
import com.printylan.data.DriverPreferences
import com.printylan.driver.DriverSelector
import com.printylan.driver.PrinterDriver
import com.printylan.model.DeviceId
import com.printylan.usb.PrinterProbe
import com.printylan.usb.UsbPermissions
import com.printylan.usb.UsbPrinter
import com.printylan.usb.UsbPrinterScanner
import kotlinx.coroutines.Dispatchers

class PrintylanApp : Application() {
    val container by lazy { AppContainer(this) }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as PrintylanApp).container

/** Process wide singletons. Small enough that a DI framework would add more than it saves. */
class AppContainer(context: Context) {
    val usbManager: UsbManager = context.getSystemService(UsbManager::class.java)
    val scanner = UsbPrinterScanner(context, usbManager)
    val permissions = UsbPermissions(context, usbManager)
    val driverPreferences = DriverPreferences(context)

    /** One USB conversation at a time, so a probe never interleaves with a print job. */
    val usbDispatcher = Dispatchers.IO.limitedParallelism(1)

    val probe = PrinterProbe(usbManager, usbDispatcher, ::driverFor)

    /** The user's choice wins; otherwise pick from the printer's advertised command set. */
    fun driverFor(printer: UsbPrinter, deviceId: DeviceId?): PrinterDriver? =
        driverPreferences.override(printer)?.let(DriverSelector::byId) ?: DriverSelector.select(deviceId)
}
