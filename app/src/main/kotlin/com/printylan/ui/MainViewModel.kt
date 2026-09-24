package com.printylan.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.printylan.appContainer
import com.printylan.driver.DriverId
import com.printylan.driver.DriverSelector
import com.printylan.model.PortStatus
import com.printylan.usb.ProbeResult
import com.printylan.usb.UsbPrinter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PrinterCardState(
    val localId: String,
    val name: String,
    val vendorId: Int,
    val productId: Int,
    val hasAccess: Boolean,
    val probing: Boolean,
    val portStatus: PortStatus?,
    val detectedDriver: DriverId?,
    val overrideDriver: DriverId?,
    /** Languages from the printer's Device ID, empty until it has been read. */
    val commandSet: List<String>,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val container = app.appContainer
    private val probes = MutableStateFlow<Map<String, ProbeResult>>(emptyMap())
    private val probing = MutableStateFlow<Set<String>>(emptySet())

    val printers: StateFlow<List<PrinterCardState>> = combine(
        container.scanner.printers,
        probes,
        probing,
        container.driverPreferences.overrides,
    ) { printers, probes, probing, overrides ->
        printers.map { printer ->
            val probe = probes[printer.localId]
            PrinterCardState(
                localId = printer.localId,
                name = probe?.deviceId?.displayName ?: printer.fallbackName,
                vendorId = printer.vendorId,
                productId = printer.productId,
                hasAccess = container.permissions.has(printer.device),
                probing = printer.localId in probing,
                portStatus = probe?.portStatus,
                detectedDriver = DriverSelector.select(probe?.deviceId)?.id,
                overrideDriver = overrides[printer.modelKey],
                commandSet = probe?.deviceId?.commandSet.orEmpty(),
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            container.scanner.printers.collect { printers ->
                val ids = printers.map { it.localId }.toSet()
                probes.update { current -> current.filterKeys { it in ids } }
                printers
                    .filter { container.permissions.has(it.device) && it.localId !in probes.value }
                    .forEach(::probe)
            }
        }
    }

    /** Rescans the bus and refreshes the status of every printer we can reach. */
    fun refresh() {
        container.scanner.refresh()
        container.scanner.printers.value
            .filter { container.permissions.has(it.device) }
            .forEach(::probe)
    }

    fun allowAccess(localId: String) {
        val printer = container.scanner.find(localId) ?: return
        viewModelScope.launch {
            if (container.permissions.request(printer.device)) probe(printer)
        }
    }

    fun setDriver(localId: String, driver: DriverId?) {
        val printer = container.scanner.find(localId) ?: return
        container.driverPreferences.setOverride(printer, driver)
    }

    private fun probe(printer: UsbPrinter) {
        if (printer.localId in probing.value) return
        probing.update { it + printer.localId }
        viewModelScope.launch {
            try {
                val result = container.probe.probe(printer)
                probes.update { it + (printer.localId to result) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // The card keeps its fallback name and shows no status.
            } finally {
                probing.update { it - printer.localId }
            }
        }
    }
}
