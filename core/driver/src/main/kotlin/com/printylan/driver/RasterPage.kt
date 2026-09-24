package com.printylan.driver

enum class RasterColorSpace(val bytesPerPixel: Int) {
    /** One byte per pixel, 0 is black and 255 is white. */
    GRAY8(1),

    /** Three bytes per pixel in R, G, B order. */
    RGB24(3),
}

/**
 * One rendered page. Drivers read rows top to bottom exactly once, so an implementation
 * can render the page in bands instead of holding a full page bitmap in memory.
 */
interface RasterPage {
    val width: Int
    val height: Int
    val colorSpace: RasterColorSpace

    val bytesPerRow: Int get() = width * colorSpace.bytesPerPixel

    /** Copies row [y] into [dst], which holds at least [bytesPerRow] bytes. */
    fun readRow(y: Int, dst: ByteArray)
}
