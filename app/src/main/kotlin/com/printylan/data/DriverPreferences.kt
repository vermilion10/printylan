package com.printylan.data

import android.content.Context
import androidx.core.content.edit
import com.printylan.driver.DriverId
import com.printylan.usb.UsbPrinter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Driver choices the user made, keyed by printer model. */
class DriverPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("driver_overrides", Context.MODE_PRIVATE)
    private val _overrides = MutableStateFlow(load())
    val overrides: StateFlow<Map<String, DriverId>> = _overrides.asStateFlow()

    fun override(printer: UsbPrinter): DriverId? = overrides.value[printer.modelKey]

    /** Pass null to go back to automatic detection. */
    fun setOverride(printer: UsbPrinter, driver: DriverId?) {
        prefs.edit {
            if (driver == null) remove(printer.modelKey) else putString(printer.modelKey, driver.name)
        }
        _overrides.value = load()
    }

    private fun load(): Map<String, DriverId> = prefs.all.mapNotNull { (key, value) ->
        DriverId.entries.firstOrNull { it.name == value }?.let { key to it }
    }.toMap()
}
