package com.printylan.model

/** What a printer can do, independent of the Android print framework. */
data class PrinterCapabilities(
    val mediaSizes: List<MediaSize>,
    val defaultMedia: MediaSize,
    val resolutions: List<Resolution>,
    val defaultResolution: Resolution,
    val colorModes: Set<ColorMode>,
    val defaultColorMode: ColorMode,
    val duplexModes: Set<DuplexMode>,
    val defaultDuplexMode: DuplexMode,
    /** Edges the printer cannot reach. Apps lay out their pages inside these. */
    val minMargins: Margins = Margins.NONE,
) {
    init {
        require(defaultMedia in mediaSizes) { "Default media must be in the supported list" }
        require(defaultResolution in resolutions) { "Default resolution must be in the supported list" }
        require(defaultColorMode in colorModes) { "Default color mode must be supported" }
        require(defaultDuplexMode in duplexModes) { "Default duplex mode must be supported" }
    }
}

/**
 * A paper size. [id] is a PWG 5101.1 self describing name such as `iso_a4_210x297mm`.
 * Dimensions use mils (thousandths of an inch) to match `android.print.PrintAttributes`.
 */
data class MediaSize(
    val id: String,
    val label: String,
    val widthMils: Int,
    val heightMils: Int,
) {
    val widthPoints: Int get() = widthMils * 72 / 1000
    val heightPoints: Int get() = heightMils * 72 / 1000

    fun widthPixels(dpi: Int): Int = widthMils * dpi / 1000
    fun heightPixels(dpi: Int): Int = heightMils * dpi / 1000

    companion object {
        val ISO_A4 = MediaSize("iso_a4_210x297mm", "A4", 8268, 11693)
        val ISO_A5 = MediaSize("iso_a5_148x210mm", "A5", 5827, 8268)
        val NA_LETTER = MediaSize("na_letter_8.5x11in", "Letter", 8500, 11000)
        val NA_LEGAL = MediaSize("na_legal_8.5x14in", "Legal", 8500, 14000)

        /** Sold as F4 in Indonesia and elsewhere in Asia. */
        val NA_FOOLSCAP = MediaSize("na_foolscap_8.5x13in", "F4 (8.5 x 13 in)", 8500, 13000)
    }
}

data class Resolution(val xDpi: Int, val yDpi: Int = xDpi) {
    val id: String get() = "${xDpi}x$yDpi"

    companion object {
        fun fromId(id: String): Resolution? {
            val parts = id.split('x')
            if (parts.size != 2) return null
            val x = parts[0].toIntOrNull() ?: return null
            val y = parts[1].toIntOrNull() ?: return null
            return Resolution(x, y)
        }
    }
}

/** Unprintable edges of the sheet, in mils like [MediaSize]. */
data class Margins(val leftMils: Int, val topMils: Int, val rightMils: Int, val bottomMils: Int) {
    companion object {
        val NONE = Margins(0, 0, 0, 0)
    }
}

enum class ColorMode { MONOCHROME, COLOR }

enum class DuplexMode { NONE, LONG_EDGE, SHORT_EDGE }
