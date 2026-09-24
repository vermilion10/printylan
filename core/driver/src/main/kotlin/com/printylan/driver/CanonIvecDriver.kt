package com.printylan.driver

import com.printylan.model.ColorMode
import com.printylan.model.DuplexMode
import com.printylan.model.JobTicket
import com.printylan.model.Margins
import com.printylan.model.MediaSize
import com.printylan.model.PrinterCapabilities
import com.printylan.model.Resolution
import java.io.File
import java.io.OutputStream
import java.nio.ByteBuffer
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * Canon PIXMA inkjets whose Device ID lists only `CMD:IVEC`, such as the G2030 series.
 *
 * These printers take IVEC XML commands and, inside a `SendData` command, one PWG Raster
 * file per page. The printer halftones the page itself, so pages go out as 8 bit sRGB and
 * the color mode is an XML setting. The command sequence matches what Canon's cnijfilter2
 * (`tocanonij`, protocol 2) sends: StartJob, SetJobConfiguration, SetConfiguration, then per
 * page SetPageConfiguration and SendData, then EndJob.
 */
object CanonIvecDriver : RasterDriver {
    override val id = DriverId.CANON_IVEC

    private const val DPI = 600
    private const val JOB_ID = "00000002"
    private const val HEADER_SIZE = 1796

    /**
     * A paper size as the printer names it and the size in points Canon's PPD gives it. The
     * printable area (pixels at 600 dpi) and unprintable edges (1/100 mm; top, left, bottom,
     * right) are what the G2030 reports in its GetCapability response.
     */
    private class Sheet(
        val ivecName: String,
        val ppdName: String,
        val widthPt: Int,
        val heightPt: Int,
        val areaWidth: Int,
        val areaHeight: Int,
        val top: Int,
        val left: Int,
        val bottom: Int,
        val right: Int,
    )

    private val sheets = mapOf(
        MediaSize.ISO_A4.id to Sheet("iso_a4_210x297mm", "A4", 595, 842, 4800, 6826, 300, 340, 500, 340),
        MediaSize.NA_LETTER.id to Sheet("na_letter_8.5x11in", "Letter", 612, 792, 4800, 6411, 300, 640, 500, 630),
        MediaSize.NA_LEGAL.id to Sheet("na_legal_8.5x14in", "legal", 612, 1008, 4800, 8211, 300, 640, 500, 630),
        MediaSize.ISO_A5.id to Sheet("iso_a5_148x210mm", "A5", 420, 595, 3335, 4771, 300, 340, 500, 340),
        MediaSize.NA_FOOLSCAP.id to Sheet("na_foolscap_8.5x13in", "foolscap", 612, 936, 4800, 7611, 300, 640, 500, 630),
    )

    override val capabilities = PrinterCapabilities(
        mediaSizes = StandardCapabilities.media + MediaSize.NA_FOOLSCAP,
        defaultMedia = MediaSize.ISO_A4,
        resolutions = listOf(Resolution(DPI)),
        defaultResolution = Resolution(DPI),
        colorModes = setOf(ColorMode.COLOR, ColorMode.MONOCHROME),
        defaultColorMode = ColorMode.COLOR,
        duplexModes = setOf(DuplexMode.NONE),
        defaultDuplexMode = DuplexMode.NONE,
        // Android takes one set of margins per printer, so use the widest over all sizes.
        minMargins = Margins(
            leftMils = sheets.values.maxOf { mils(it.left) },
            topMils = sheets.values.maxOf { mils(it.top) },
            rightMils = sheets.values.maxOf { mils(it.right) },
            bottomMils = sheets.values.maxOf { mils(it.bottom) },
        ),
    )

    // Every page is rendered and sent again for each copy.
    override val encodesCopies = false

    /** Clock for the job's timestamp; tests replace it. */
    internal var now: () -> LocalDateTime = LocalDateTime::now

    /** Holds one compressed page, since SendData announces the page size before the data. */
    internal var pageBuffer: () -> File = { File.createTempFile("canon-page", ".pwg") }

    // The printer converts to gray itself when the job asks for monochrome.
    override fun colorSpaceFor(ticket: JobTicket) = RasterColorSpace.RGB24

    override fun printableArea(ticket: JobTicket): PixelArea {
        val sheet = sheetFor(ticket.media)
        return PixelArea(pixels(sheet.left), pixels(sheet.top), sheet.areaWidth, sheet.areaHeight)
    }

    override fun startJob(out: OutputStream, ticket: JobTicket, pageCount: Int): PageSink {
        val sheet = sheetFor(ticket.media)
        val uuid = UUID.randomUUID().toString().replace("-", "")
        out.ascii(
            command(
                "StartJob",
                vendorNamespace = true,
                "<ivec:bidi>0</ivec:bidi><vcn:forcepmdetection>OFF</vcn:forcepmdetection><ivec:jobname/>" +
                    "<ivec:username/><ivec:computername/><ivec:job_description><![CDATA[$uuid]]></ivec:job_description>" +
                    "<vcn:host_environment>linux</vcn:host_environment>",
            ),
        )
        val dateTime = now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
        // Canon's library writes this one without the space before "?>".
        out.ascii(
            "<?xml version=\"1.0\" encoding=\"utf-8\"?><cmd xmlns:ivec=\"$IVEC_NS\"><ivec:contents>" +
                "<ivec:operation>SetJobConfiguration</ivec:operation><ivec:param_set servicetype=\"print\">" +
                "<ivec:jobID>$JOB_ID</ivec:jobID><ivec:mismatch_mode>none</ivec:mismatch_mode>" +
                "<ivec:datetime>$dateTime</ivec:datetime></ivec:param_set></ivec:contents></cmd>",
        )
        val colorMode = if (ticket.colorMode == ColorMode.COLOR) "color" else "monochrome"
        out.ascii(
            command(
                "SetConfiguration",
                vendorNamespace = false,
                "<ivec:papersize>${sheet.ivecName}</ivec:papersize><ivec:papertype>stationery</ivec:papertype>" +
                    "<ivec:borderlessprint>OFF</ivec:borderlessprint><ivec:printcolormode>$colorMode</ivec:printcolormode>" +
                    "<ivec:duplexprint>OFF</ivec:duplexprint>",
            ),
        )
        return CanonPageSink(out, sheet, pageCount)
    }

    private class CanonPageSink(
        private val out: OutputStream,
        private val sheet: Sheet,
        private val pageCount: Int,
    ) : PageSink {
        private var written = 0

        override fun writePage(page: RasterPage) {
            written++
            val nextPage = if (written < pageCount) "ON" else "OFF"
            out.ascii(
                command(
                    "VendorCmd",
                    vendorNamespace = true,
                    "<vcn:ijoperation>SetPageConfiguration</vcn:ijoperation><vcn:nextpage>$nextPage</vcn:nextpage>",
                ),
            )
            val file = pageBuffer()
            try {
                file.outputStream().buffered().use { pwg ->
                    pwg.ascii("RaS2")
                    pwg.write(pageHeader(page, sheet))
                    PwgRasterDriver.writeCompressed(page, pwg)
                }
                out.ascii(
                    command(
                        "SendData",
                        vendorNamespace = false,
                        "<ivec:format>PWGRaster</ivec:format><ivec:datasize>${file.length()}</ivec:datasize>",
                    ),
                )
                file.inputStream().use { it.copyTo(out) }
            } finally {
                file.delete()
            }
        }

        override fun close() {
            out.ascii(command("EndJob", vendorNamespace = false, ""))
            out.flush()
        }
    }

    /** The PWG header fields Canon's `tocnpwg` filter sets; everything else stays zero. */
    internal fun pageHeader(page: RasterPage, media: MediaSize): ByteArray = pageHeader(page, sheetFor(media))

    private fun pageHeader(page: RasterPage, sheet: Sheet): ByteArray {
        require(page.colorSpace == RasterColorSpace.RGB24) { "Canon IVEC pages must be RGB24" }
        val buffer = ByteBuffer.allocate(HEADER_SIZE) // big endian, zero filled
        fun string(offset: Int, value: String) {
            value.toByteArray(Charsets.US_ASCII).forEachIndexed { i, b -> buffer.put(offset + i, b) }
        }
        string(0, "PwgRaster") // MediaClass
        string(128, "plain") // MediaType
        buffer.putInt(276, DPI)
        buffer.putInt(280, DPI)
        buffer.putInt(340, 1) // NumCopies
        buffer.putInt(352, sheet.widthPt)
        buffer.putInt(356, sheet.heightPt)
        buffer.putInt(372, page.width)
        buffer.putInt(376, page.height)
        buffer.putInt(384, 8) // bits per color
        buffer.putInt(388, 24) // bits per pixel
        buffer.putInt(392, page.bytesPerRow)
        buffer.putInt(400, 19) // sRGB
        buffer.putInt(420, 3) // colors
        buffer.putInt(452, 1) // TotalPageCount, which Canon always sets to 1
        buffer.putInt(456, 1) // CrossFeedTransform
        buffer.putInt(460, 1) // FeedTransform
        // ImageBox: Canon multiplies the imaging box in points by the resolution.
        buffer.putInt(472, sheet.widthPt * DPI)
        buffer.putInt(476, sheet.heightPt * DPI)
        buffer.putInt(480, 0xFFFFFF) // AlternatePrimary: white
        string(1732, sheet.ppdName)
        return buffer.array()
    }

    private fun sheetFor(media: MediaSize) = sheets[media.id] ?: sheets.getValue(MediaSize.ISO_A4.id)

    /** 1/100 mm to pixels at 600 dpi. */
    private fun pixels(hundredthsMm: Int) = hundredthsMm * DPI / 2540

    /** 1/100 mm to mils, rounded up so Android keeps content inside the printable area. */
    private fun mils(hundredthsMm: Int) = (hundredthsMm * 1000 + 2539) / 2540

    private const val IVEC_NS = "http://www.canon.com/ns/cmd/2008/07/common/"
    private const val VCN_NS = "http://www.canon.com/ns/cmd/2008/07/canon/"

    private fun command(operation: String, vendorNamespace: Boolean, params: String): String {
        val namespaces = "xmlns:ivec=\"$IVEC_NS\"" + if (vendorNamespace) " xmlns:vcn=\"$VCN_NS\"" else ""
        return "<?xml version=\"1.0\" encoding=\"utf-8\" ?><cmd $namespaces><ivec:contents>" +
            "<ivec:operation>$operation</ivec:operation><ivec:param_set servicetype=\"print\">" +
            "<ivec:jobID>$JOB_ID</ivec:jobID>$params</ivec:param_set></ivec:contents></cmd>"
    }
}
