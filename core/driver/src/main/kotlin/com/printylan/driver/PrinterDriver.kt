package com.printylan.driver

import com.printylan.model.JobTicket
import com.printylan.model.PrinterCapabilities
import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream

enum class DriverId(val displayName: String) {
    PDF("PDF (direct)"),
    PWG_RASTER("PWG Raster"),
    PCL("PCL raster (monochrome)"),
    CANON_IVEC("Canon inkjet (IVEC)"),
}

/** Converts a print job into the byte stream a printer understands. */
sealed interface PrinterDriver {
    val id: DriverId
    val capabilities: PrinterCapabilities
}

/** For printers that accept the PDF from the print framework without conversion. */
interface PassthroughDriver : PrinterDriver {
    /** Writes the job to [out]. [openDocument] returns a fresh stream each call, one per copy. */
    fun send(openDocument: () -> InputStream, out: OutputStream, ticket: JobTicket)
}

/** A rectangle on the sheet, in pixels at the job's resolution. */
data class PixelArea(val left: Int, val top: Int, val width: Int, val height: Int)

/** For printers that need each page rasterized first. */
interface RasterDriver : PrinterDriver {
    /** The color space pages must be rendered in for [ticket]. */
    fun colorSpaceFor(ticket: JobTicket): RasterColorSpace

    /**
     * The part of the sheet the driver sends. Pages passed to the sink have this size, and
     * show the document cropped to it. The default is the whole sheet.
     */
    fun printableArea(ticket: JobTicket): PixelArea = PixelArea(
        left = 0,
        top = 0,
        width = ticket.media.widthPixels(ticket.resolution.xDpi),
        height = ticket.media.heightPixels(ticket.resolution.yDpi),
    )

    /** False when the printer language has no copy count, so the caller must send each copy. */
    val encodesCopies: Boolean get() = true

    /** Starts a job. Write every page to the returned sink, then close it to finish the job. */
    fun startJob(out: OutputStream, ticket: JobTicket, pageCount: Int): PageSink
}

interface PageSink : Closeable {
    fun writePage(page: RasterPage)
}
