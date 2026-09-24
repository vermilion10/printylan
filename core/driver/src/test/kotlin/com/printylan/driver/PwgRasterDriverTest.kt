package com.printylan.driver

import com.printylan.model.ColorMode
import com.printylan.model.DuplexMode
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.nio.ByteBuffer
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class PwgRasterDriverTest {
    @Test
    fun writesSyncWordAndHeaderFields() {
        val page = ArrayRasterPage(10, 4, RasterColorSpace.RGB24)
        val out = ByteArrayOutputStream()
        PwgRasterDriver.startJob(out, ticket(ColorMode.COLOR, DuplexMode.SHORT_EDGE, copies = 2), 1)
            .use { it.writePage(page) }
        val bytes = out.toByteArray()
        assertEquals("RaS2", String(bytes, 0, 4, Charsets.US_ASCII))

        val header = ByteBuffer.wrap(bytes, 4, PwgRasterDriver.HEADER_SIZE).slice()
        assertEquals("PwgRaster", String(bytes, 4, 9, Charsets.US_ASCII))
        assertEquals(1, header.getInt(272)) // Duplex
        assertEquals(300, header.getInt(276))
        assertEquals(2, header.getInt(340)) // NumCopies
        assertEquals(1, header.getInt(368)) // Tumble
        assertEquals(10, header.getInt(372))
        assertEquals(4, header.getInt(376))
        assertEquals(24, header.getInt(388))
        assertEquals(30, header.getInt(392))
        assertEquals(19, header.getInt(400)) // sRGB
    }

    @Test
    fun compressedDataDecodesToOriginalPixels() {
        val random = Random(7)
        for (colorSpace in RasterColorSpace.entries) {
            val width = 37
            val height = 300
            val bpp = colorSpace.bytesPerPixel
            val pixels = ByteArray(width * height * bpp)
            for (y in 0 until height) {
                // Blocks of identical rows, runs, and noise.
                val seed = y / 5
                for (x in 0 until width) {
                    val value = if (seed % 3 == 0) random.nextInt() else (x / 4 + seed)
                    for (k in 0 until bpp) pixels[(y * width + x) * bpp + k] = (value + k).toByte()
                }
                if (seed % 3 == 0 && y % 5 != 0) {
                    System.arraycopy(pixels, (y - 1) * width * bpp, pixels, y * width * bpp, width * bpp)
                }
            }
            val page = ArrayRasterPage(width, height, colorSpace, pixels)
            val out = ByteArrayOutputStream()
            PwgRasterDriver.writeCompressed(page, out)
            assertContentEquals(pixels, decode(out.toByteArray(), width, height, bpp))
        }
    }

    private fun decode(data: ByteArray, width: Int, height: Int, bpp: Int): ByteArray {
        val input = DataInputStream(ByteArrayInputStream(data))
        val result = ByteArrayOutputStream()
        var rows = 0
        while (rows < height) {
            val repeat = input.readUnsignedByte() + 1
            val line = ByteArrayOutputStream()
            var x = 0
            while (x < width) {
                val n = input.readUnsignedByte()
                if (n <= 127) {
                    val pixel = ByteArray(bpp).also { input.readFully(it) }
                    repeat(n + 1) { line.write(pixel) }
                    x += n + 1
                } else {
                    val count = 257 - n
                    val literal = ByteArray(count * bpp).also { input.readFully(it) }
                    line.write(literal)
                    x += count
                }
            }
            repeat(repeat) { line.writeTo(result) }
            rows += repeat
        }
        assertEquals(-1, input.read(), "trailing data after last row")
        return result.toByteArray()
    }
}
