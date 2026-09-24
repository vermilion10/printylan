package com.printylan.usb

import android.hardware.usb.UsbManager
import com.printylan.driver.PrinterDriver
import com.printylan.model.DeviceId
import com.printylan.model.PortStatus
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

data class ProbeResult(
    val deviceId: DeviceId?,
    val portStatus: PortStatus?,
    val driver: PrinterDriver?,
)

/** Reads identity and status from a printer. The caller must hold USB permission. */
class PrinterProbe(
    private val usbManager: UsbManager,
    private val usbDispatcher: CoroutineDispatcher,
    private val driverFor: (UsbPrinter, DeviceId?) -> PrinterDriver?,
) {
    suspend fun probe(printer: UsbPrinter): ProbeResult = withContext(usbDispatcher) {
        UsbPrinterConnection.open(usbManager, printer).use { connection ->
            val deviceId = connection.readDeviceId()
            ProbeResult(deviceId, connection.readPortStatus(), driverFor(printer, deviceId))
        }
    }
}
