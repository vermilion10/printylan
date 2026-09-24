package com.printylan.driver

/**
 * Converts 8 bit gray rows to 1 bit rows with Floyd-Steinberg error diffusion.
 * Output bits are packed MSB first and a set bit means black, as PCL expects.
 * Feed rows in order; the ditherer carries error from one row into the next.
 */
class FloydSteinbergDitherer(private val width: Int) {
    private var current = IntArray(width + 2)
    private var next = IntArray(width + 2)

    val bytesPerRow: Int get() = (width + 7) / 8

    fun ditherRow(gray: ByteArray, out: ByteArray) {
        out.fill(0, 0, bytesPerRow)
        for (x in 0 until width) {
            val value = (gray[x].toInt() and 0xFF) + current[x + 1]
            val black = value < 128
            if (black) {
                out[x shr 3] = (out[x shr 3].toInt() or (0x80 ushr (x and 7))).toByte()
            }
            val error = if (black) value else value - 255
            current[x + 2] += error * 7 / 16
            next[x] += error * 3 / 16
            next[x + 1] += error * 5 / 16
            next[x + 2] += error / 16
        }
        val done = current
        current = next
        next = done
        next.fill(0)
    }
}
