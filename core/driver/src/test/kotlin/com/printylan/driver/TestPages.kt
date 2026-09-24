package com.printylan.driver

import com.printylan.model.ColorMode
import com.printylan.model.DuplexMode
import com.printylan.model.JobTicket
import com.printylan.model.MediaSize
import com.printylan.model.Resolution

class ArrayRasterPage(
    override val width: Int,
    override val height: Int,
    override val colorSpace: RasterColorSpace,
    val pixels: ByteArray = ByteArray(width * height * colorSpace.bytesPerPixel) { 0xFF.toByte() },
) : RasterPage {
    override fun readRow(y: Int, dst: ByteArray) {
        System.arraycopy(pixels, y * bytesPerRow, dst, 0, bytesPerRow)
    }
}

fun ticket(
    colorMode: ColorMode = ColorMode.MONOCHROME,
    duplexMode: DuplexMode = DuplexMode.NONE,
    copies: Int = 1,
) = JobTicket(
    copies = copies,
    media = MediaSize.ISO_A4,
    resolution = Resolution(300),
    colorMode = colorMode,
    duplexMode = duplexMode,
)
