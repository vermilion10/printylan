package com.printylan.driver

import com.printylan.model.ColorMode
import com.printylan.model.DuplexMode
import com.printylan.model.JobTicket
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.nio.ByteBuffer

/**
 * PWG Raster (PWG 5102.4) output, the raster format IPP Everywhere printers accept.
 * Pages go out as 8 bit sGray or sRGB with the format's run length compression.
 */
object PwgRasterDriver : RasterDriver {
    const val HEADER_SIZE = 1796
    private const val COLOR_SPACE_SGRAY = 18
    private const val COLOR_SPACE_SRGB = 19
    private const val MAX_RUN = 128
    private const val MAX_LINE_REPEAT = 256

    override val id = DriverId.PWG_RASTER

    override val capabilities = StandardCapabilities.build(
        colorModes = setOf(ColorMode.MONOCHROME, ColorMode.COLOR),
        duplexModes = setOf(DuplexMode.NONE, DuplexMode.LONG_EDGE, DuplexMode.SHORT_EDGE),
    )

    override fun colorSpaceFor(ticket: JobTicket) =
        if (ticket.colorMode == ColorMode.COLOR) RasterColorSpace.RGB24 else RasterColorSpace.GRAY8

    override fun startJob(out: OutputStream, ticket: JobTicket, pageCount: Int): PageSink {
        out.ascii("RaS2")
        return PwgPageSink(out, ticket, pageCount)
    }

    private class PwgPageSink(
        private val out: OutputStream,
        private val ticket: JobTicket,
        private val pageCount: Int,
    ) : PageSink {
        override fun writePage(page: RasterPage) {
            out.write(header(page, ticket, pageCount))
            writeCompressed(page, out)
        }

        override fun close() = out.flush()
    }

    internal fun header(page: RasterPage, ticket: JobTicket, pageCount: Int): ByteArray {
        val buffer = ByteBuffer.allocate(HEADER_SIZE) // big endian, zero filled
        fun string(offset: Int, value: String) {
            val bytes = value.toByteArray(Charsets.US_ASCII)
            for (i in 0 until minOf(bytes.size, 63)) buffer.put(offset + i, bytes[i])
        }
        val bytesPerPixel = page.colorSpace.bytesPerPixel
        string(0, "PwgRaster")
        buffer.putInt(272, if (ticket.duplexMode == DuplexMode.NONE) 0 else 1)
        buffer.putInt(276, ticket.resolution.xDpi)
        buffer.putInt(280, ticket.resolution.yDpi)
        buffer.putInt(340, ticket.copies.coerceAtLeast(1))
        buffer.putInt(352, ticket.media.widthPoints)
        buffer.putInt(356, ticket.media.heightPoints)
        buffer.putInt(368, if (ticket.duplexMode == DuplexMode.SHORT_EDGE) 1 else 0)
        buffer.putInt(372, page.width)
        buffer.putInt(376, page.height)
        buffer.putInt(384, 8)
        buffer.putInt(388, 8 * bytesPerPixel)
        buffer.putInt(392, page.bytesPerRow)
        buffer.putInt(400, if (page.colorSpace == RasterColorSpace.RGB24) COLOR_SPACE_SRGB else COLOR_SPACE_SGRAY)
        buffer.putInt(420, bytesPerPixel)
        buffer.putInt(452, pageCount)
        buffer.putInt(456, 1)
        buffer.putInt(460, 1)
        string(1732, ticket.media.id)
        return buffer.array()
    }

    /**
     * Each line group starts with a repeat count (lines minus one), followed by pixel runs:
     * 0..127 repeats the next pixel n+1 times, 129..255 copies 257-n literal pixels.
     */
    internal fun writeCompressed(page: RasterPage, out: OutputStream) {
        if (page.height == 0) return
        val bpp = page.colorSpace.bytesPerPixel
        var current = ByteArray(page.bytesPerRow)
        var next = ByteArray(page.bytesPerRow)
        val encoded = ByteArrayOutputStream(page.bytesPerRow + page.bytesPerRow / MAX_RUN + 2)
        page.readRow(0, current)
        var repeat = 1
        for (y in 1..page.height) {
            if (y < page.height) {
                page.readRow(y, next)
                if (repeat < MAX_LINE_REPEAT && next.contentEquals(current)) {
                    repeat++
                    continue
                }
            }
            out.write(repeat - 1)
            encoded.reset()
            encodeLine(current, page.width, bpp, encoded)
            encoded.writeTo(out)
            val swap = current
            current = next
            next = swap
            repeat = 1
        }
    }

    private fun encodeLine(row: ByteArray, width: Int, bpp: Int, out: ByteArrayOutputStream) {
        fun same(a: Int, b: Int): Boolean {
            for (k in 0 until bpp) if (row[a * bpp + k] != row[b * bpp + k]) return false
            return true
        }
        var x = 0
        while (x < width) {
            var run = 1
            while (x + run < width && run < MAX_RUN && same(x, x + run)) run++
            if (run >= 2) {
                out.write(run - 1)
                out.write(row, x * bpp, bpp)
                x += run
                continue
            }
            val start = x
            var count = 0
            while (x < width && count < MAX_RUN) {
                if (x + 1 < width && same(x, x + 1)) break
                x++
                count++
            }
            // A single pixel literal has no encoding of its own; send it as a run of one.
            out.write(if (count == 1) 0 else 257 - count)
            out.write(row, start * bpp, count * bpp)
        }
    }
}
