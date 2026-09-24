package com.printylan.driver

import com.printylan.model.ColorMode
import com.printylan.model.DuplexMode
import com.printylan.model.MediaSize
import com.printylan.model.PrinterCapabilities
import com.printylan.model.Resolution

internal object StandardCapabilities {
    val media = listOf(MediaSize.ISO_A4, MediaSize.NA_LETTER, MediaSize.NA_LEGAL, MediaSize.ISO_A5)

    fun build(
        colorModes: Set<ColorMode>,
        duplexModes: Set<DuplexMode>,
        resolutions: List<Resolution> = listOf(Resolution(300), Resolution(600)),
    ) = PrinterCapabilities(
        mediaSizes = media,
        defaultMedia = MediaSize.ISO_A4,
        resolutions = resolutions,
        defaultResolution = resolutions.first(),
        colorModes = colorModes,
        defaultColorMode = ColorMode.MONOCHROME.takeIf { it in colorModes } ?: colorModes.first(),
        duplexModes = duplexModes,
        defaultDuplexMode = DuplexMode.NONE,
    )
}
