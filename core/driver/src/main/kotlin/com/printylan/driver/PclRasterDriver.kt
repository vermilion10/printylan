package com.printylan.driver

import com.printylan.model.ColorMode
import com.printylan.model.DuplexMode
import com.printylan.model.JobTicket
import com.printylan.model.MediaSize
import java.io.ByteArrayOutputStream
import java.io.OutputStream

/**
 * Monochrome PCL 5 raster output with PackBits (mode 2) row compression.
 * Most HP compatible lasers and many office inkjets accept it.
 */
object PclRasterDriver : RasterDriver {
    private const val ESC = "\u001B"
    private const val UEL = "$ESC%-12345X"

    override val id = DriverId.PCL

    override val capabilities = StandardCapabilities.build(
        colorModes = setOf(ColorMode.MONOCHROME),
        duplexModes = setOf(DuplexMode.NONE, DuplexMode.LONG_EDGE, DuplexMode.SHORT_EDGE),
    )

    override fun colorSpaceFor(ticket: JobTicket) = RasterColorSpace.GRAY8

    override fun startJob(out: OutputStream, ticket: JobTicket, pageCount: Int): PageSink {
        out.ascii(UEL)
        out.ascii("@PJL JOB NAME=\"Printylan\"\r\n@PJL ENTER LANGUAGE=PCL\r\n")
        out.ascii("${ESC}E")
        out.ascii("$ESC&l${ticket.copies.coerceIn(1, 999)}X")
        out.ascii("$ESC&l${pageSizeCode(ticket.media)}A")
        out.ascii("$ESC&l0O")
        out.ascii("$ESC&l${duplexCode(ticket.duplexMode)}S")
        out.ascii("$ESC*t${ticket.resolution.xDpi}R")
        return PclPageSink(out)
    }

    private class PclPageSink(private val out: OutputStream) : PageSink {
        override fun writePage(page: RasterPage) {
            require(page.colorSpace == RasterColorSpace.GRAY8) { "PCL driver needs GRAY8 pages" }
            val ditherer = FloydSteinbergDitherer(page.width)
            val gray = ByteArray(page.bytesPerRow)
            val bits = ByteArray(ditherer.bytesPerRow)
            val packed = ByteArrayOutputStream(bits.size + bits.size / 64 + 2)
            var pendingBlankRows = 0

            out.ascii("$ESC*p0x0Y")
            out.ascii("$ESC*r${page.width}S")
            out.ascii("$ESC*b2M")
            out.ascii("$ESC*r1A")
            for (y in 0 until page.height) {
                page.readRow(y, gray)
                ditherer.ditherRow(gray, bits)
                val used = bits.indexOfLast { it != 0.toByte() } + 1
                if (used == 0) {
                    pendingBlankRows++
                    continue
                }
                if (pendingBlankRows > 0) {
                    out.ascii("$ESC*b${pendingBlankRows}Y")
                    pendingBlankRows = 0
                }
                packed.reset()
                PackBits.encode(bits, used, packed)
                out.ascii("$ESC*b${packed.size()}W")
                packed.writeTo(out)
            }
            out.ascii("$ESC*rC")
            out.write(0x0C)
        }

        override fun close() {
            out.ascii("${ESC}E")
            out.ascii("$UEL@PJL EOJ\r\n$UEL")
            out.flush()
        }
    }

    internal fun pageSizeCode(media: MediaSize): Int = when (media.id) {
        MediaSize.NA_LETTER.id -> 2
        MediaSize.NA_LEGAL.id -> 3
        MediaSize.ISO_A5.id -> 25
        else -> 26 // A4
    }

    private fun duplexCode(mode: DuplexMode): Int = when (mode) {
        DuplexMode.NONE -> 0
        DuplexMode.LONG_EDGE -> 1
        DuplexMode.SHORT_EDGE -> 2
    }
}

internal fun OutputStream.ascii(text: String) = write(text.toByteArray(Charsets.US_ASCII))
