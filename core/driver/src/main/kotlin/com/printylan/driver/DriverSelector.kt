package com.printylan.driver

import com.printylan.model.DeviceId

object DriverSelector {
    private val pwgTokens = setOf("PWGRASTER", "PWG", "PWG-RASTER")
    private val pclTokens = setOf("PCL", "PCL3", "PCL3GUI", "PCL5", "PCL5E", "PCL5C")

    val all: List<PrinterDriver> = listOf(PdfPassthroughDriver, PwgRasterDriver, PclRasterDriver, CanonIvecDriver)

    fun byId(id: DriverId): PrinterDriver = all.first { it.id == id }

    /**
     * Picks a driver from the command set in the printer's IEEE 1284 Device ID.
     * Returns null for printers that only speak a proprietary host based language.
     */
    fun select(deviceId: DeviceId?): PrinterDriver? {
        val commands = deviceId?.commandSet ?: return null
        return when {
            "PDF" in commands -> PdfPassthroughDriver
            commands.any { it in pwgTokens } -> PwgRasterDriver
            commands.any { it in pclTokens } -> PclRasterDriver
            // Canon inkjets that only list IVEC take BJ raster after an IVEC mode switch.
            "IVEC" in commands -> CanonIvecDriver
            else -> null
        }
    }
}
