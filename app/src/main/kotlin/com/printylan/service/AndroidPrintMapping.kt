package com.printylan.service

import android.print.PrintAttributes
import android.print.PrintJobInfo
import android.print.PrinterCapabilitiesInfo
import android.print.PrinterId
import com.printylan.model.ColorMode
import com.printylan.model.DuplexMode
import com.printylan.model.JobTicket
import com.printylan.model.MediaSize
import com.printylan.model.PrinterCapabilities
import com.printylan.model.Resolution

/** Converts between the Android print framework types and the app's own model. */
object AndroidPrintMapping {

    fun capabilitiesInfo(printerId: PrinterId, caps: PrinterCapabilities): PrinterCapabilitiesInfo {
        val builder = PrinterCapabilitiesInfo.Builder(printerId)
        caps.mediaSizes.forEach { builder.addMediaSize(it.toAndroid(), it == caps.defaultMedia) }
        caps.resolutions.forEach {
            builder.addResolution(
                PrintAttributes.Resolution(it.id, "${it.xDpi} dpi", it.xDpi, it.yDpi),
                it == caps.defaultResolution,
            )
        }
        builder.setColorModes(caps.colorModes.fold(0) { mask, mode -> mask or mode.toAndroid() }, caps.defaultColorMode.toAndroid())
        builder.setDuplexModes(caps.duplexModes.fold(0) { mask, mode -> mask or mode.toAndroid() }, caps.defaultDuplexMode.toAndroid())
        caps.minMargins.let {
            builder.setMinMargins(PrintAttributes.Margins(it.leftMils, it.topMils, it.rightMils, it.bottomMils))
        }
        return builder.build()
    }

    /** Reads the user's choices from the job, falling back to driver defaults for anything unsupported. */
    fun ticketFrom(info: PrintJobInfo, caps: PrinterCapabilities): JobTicket {
        val attributes = info.attributes
        val media = attributes.mediaSize?.let { chosen -> caps.mediaSizes.firstOrNull { it.id == chosen.id } }
        val resolution = attributes.resolution?.let { Resolution.fromId(it.id) }?.takeIf { it in caps.resolutions }
        val color = when (attributes.colorMode) {
            PrintAttributes.COLOR_MODE_COLOR -> ColorMode.COLOR
            PrintAttributes.COLOR_MODE_MONOCHROME -> ColorMode.MONOCHROME
            else -> null
        }?.takeIf { it in caps.colorModes }
        val duplex = when (attributes.duplexMode) {
            PrintAttributes.DUPLEX_MODE_LONG_EDGE -> DuplexMode.LONG_EDGE
            PrintAttributes.DUPLEX_MODE_SHORT_EDGE -> DuplexMode.SHORT_EDGE
            PrintAttributes.DUPLEX_MODE_NONE -> DuplexMode.NONE
            else -> null
        }?.takeIf { it in caps.duplexModes }

        return JobTicket(
            copies = info.copies.coerceAtLeast(1),
            media = media ?: caps.defaultMedia,
            resolution = resolution ?: caps.defaultResolution,
            colorMode = color ?: caps.defaultColorMode,
            duplexMode = duplex ?: caps.defaultDuplexMode,
        )
    }

    private fun MediaSize.toAndroid() = PrintAttributes.MediaSize(id, label, widthMils, heightMils)

    private fun ColorMode.toAndroid() = when (this) {
        ColorMode.MONOCHROME -> PrintAttributes.COLOR_MODE_MONOCHROME
        ColorMode.COLOR -> PrintAttributes.COLOR_MODE_COLOR
    }

    private fun DuplexMode.toAndroid() = when (this) {
        DuplexMode.NONE -> PrintAttributes.DUPLEX_MODE_NONE
        DuplexMode.LONG_EDGE -> PrintAttributes.DUPLEX_MODE_LONG_EDGE
        DuplexMode.SHORT_EDGE -> PrintAttributes.DUPLEX_MODE_SHORT_EDGE
    }
}
