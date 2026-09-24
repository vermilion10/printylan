package com.printylan.service

import android.print.PrinterId
import android.print.PrinterInfo
import android.printservice.PrintService
import android.printservice.PrinterDiscoverySession
import android.util.Log
import com.printylan.AppContainer
import com.printylan.R
import com.printylan.usb.UsbPrinter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Lives while a print dialog is open. Publishes attached USB printers, and when the user
 * selects one, reads its Device ID and status to build the capabilities the dialog shows.
 */
class UsbDiscoverySession(
    private val service: PrintService,
    private val container: AppContainer,
) : PrinterDiscoverySession() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val known = mutableMapOf<PrinterId, UsbPrinter>()
    private val tracking = mutableMapOf<PrinterId, Job>()
    private var discovery: Job? = null

    override fun onStartPrinterDiscovery(priorityList: List<PrinterId>) {
        discovery?.cancel()
        container.scanner.refresh()
        discovery = scope.launch {
            container.scanner.printers.collect(::publish)
        }
    }

    override fun onStopPrinterDiscovery() {
        discovery?.cancel()
        discovery = null
    }

    override fun onValidatePrinters(printerIds: List<PrinterId>) {
        // The scanner already reports only attached printers.
    }

    override fun onStartPrinterStateTracking(printerId: PrinterId) {
        val printer = known[printerId] ?: return
        tracking[printerId]?.cancel()
        tracking[printerId] = scope.launch {
            val info = inspect(printerId, printer)
            if (known[printerId] == printer) addPrinters(listOf(info))
        }
    }

    override fun onStopPrinterStateTracking(printerId: PrinterId) {
        tracking.remove(printerId)?.cancel()
    }

    override fun onDestroy() {
        scope.cancel()
    }

    private fun publish(printers: List<UsbPrinter>) {
        val current = printers.associateBy { service.generatePrinterId(it.localId) }
        val gone = known.keys - current.keys
        if (gone.isNotEmpty()) removePrinters(gone.toList())

        // Re-adding a known printer would drop the capabilities the dialog already has.
        val added = current.filterKeys { it !in known }
        known.clear()
        known.putAll(current)
        if (added.isNotEmpty()) {
            addPrinters(added.map { (id, printer) -> basicInfo(id, printer.fallbackName, PrinterInfo.STATUS_IDLE) })
        }
    }

    private suspend fun inspect(id: PrinterId, printer: UsbPrinter): PrinterInfo {
        if (!container.permissions.request(printer.device)) {
            return basicInfo(id, printer.fallbackName, PrinterInfo.STATUS_UNAVAILABLE, R.string.printer_needs_access)
        }
        val probe = try {
            container.probe.probe(printer)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Probe failed for ${printer.localId}", e)
            return basicInfo(id, printer.fallbackName, PrinterInfo.STATUS_UNAVAILABLE)
        }
        val name = probe.deviceId?.displayName ?: printer.fallbackName
        val driver = probe.driver
            ?: return basicInfo(id, name, PrinterInfo.STATUS_UNAVAILABLE, R.string.printer_unsupported)
        val status = if (probe.portStatus?.isReady == false) PrinterInfo.STATUS_BUSY else PrinterInfo.STATUS_IDLE
        return PrinterInfo.Builder(id, name, status)
            .setDescription(service.getString(R.string.printer_description))
            .setCapabilities(AndroidPrintMapping.capabilitiesInfo(id, driver.capabilities))
            .build()
    }

    private fun basicInfo(id: PrinterId, name: String, status: Int, description: Int = R.string.printer_description) =
        PrinterInfo.Builder(id, name, status)
            .setDescription(service.getString(description))
            .build()

    private companion object {
        const val TAG = "UsbDiscoverySession"
    }
}
