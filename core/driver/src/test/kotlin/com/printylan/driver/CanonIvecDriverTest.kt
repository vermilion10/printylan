package com.printylan.driver

import com.printylan.model.ColorMode
import com.printylan.model.DeviceId
import com.printylan.model.DuplexMode
import com.printylan.model.JobTicket
import com.printylan.model.MediaSize
import com.printylan.model.Resolution
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.file.Files
import java.time.LocalDateTime
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CanonIvecDriverTest {
    @BeforeTest
    fun fixClock() {
        CanonIvecDriver.now = { LocalDateTime.of(2026, 9, 24, 12, 0, 0) }
        CanonIvecDriver.pageBuffer = { Files.createTempFile("canon-test", ".pwg").toFile() }
    }

    private fun canonTicket(color: ColorMode = ColorMode.COLOR, media: MediaSize = MediaSize.ISO_A4) = JobTicket(
        copies = 1,
        media = media,
        resolution = Resolution(600),
        colorMode = color,
        duplexMode = DuplexMode.NONE,
    )

    private fun print(ticket: JobTicket, vararg pages: RasterPage): String {
        val out = ByteArrayOutputStream()
        CanonIvecDriver.startJob(out, ticket, pages.size).use { sink -> pages.forEach(sink::writePage) }
        return String(out.toByteArray(), Charsets.ISO_8859_1)
    }

    /** The XML commands in the stream, with page data cut out. */
    private fun commands(stream: String): List<String> =
        Regex("<\\?xml.*?</cmd>").findAll(stream).map { it.value }.toList()

    private fun smallPage(width: Int = 16, height: Int = 4) =
        ArrayRasterPage(width, height, RasterColorSpace.RGB24)

    // Captured from Canon's libcnbpcnclapicom2 (cnijfilter2) fed the G2030's GetCapability response.
    private val canonSetConfigurationA4Color =
        "<?xml version=\"1.0\" encoding=\"utf-8\" ?><cmd xmlns:ivec=\"http://www.canon.com/ns/cmd/2008/07/common/\"><ivec:contents><ivec:operation>SetConfiguration</ivec:operation><ivec:param_set servicetype=\"print\"><ivec:jobID>00000002</ivec:jobID><ivec:papersize>iso_a4_210x297mm</ivec:papersize><ivec:papertype>stationery</ivec:papertype><ivec:borderlessprint>OFF</ivec:borderlessprint><ivec:printcolormode>color</ivec:printcolormode><ivec:duplexprint>OFF</ivec:duplexprint></ivec:param_set></ivec:contents></cmd>"
    private val canonSetJobConfiguration =
        "<?xml version=\"1.0\" encoding=\"utf-8\"?><cmd xmlns:ivec=\"http://www.canon.com/ns/cmd/2008/07/common/\"><ivec:contents><ivec:operation>SetJobConfiguration</ivec:operation><ivec:param_set servicetype=\"print\"><ivec:jobID>00000002</ivec:jobID><ivec:mismatch_mode>none</ivec:mismatch_mode><ivec:datetime>20260924120000</ivec:datetime></ivec:param_set></ivec:contents></cmd>"
    private val canonNextPageOn =
        "<?xml version=\"1.0\" encoding=\"utf-8\" ?><cmd xmlns:ivec=\"http://www.canon.com/ns/cmd/2008/07/common/\" xmlns:vcn=\"http://www.canon.com/ns/cmd/2008/07/canon/\"><ivec:contents><ivec:operation>VendorCmd</ivec:operation><ivec:param_set servicetype=\"print\"><ivec:jobID>00000002</ivec:jobID><vcn:ijoperation>SetPageConfiguration</vcn:ijoperation><vcn:nextpage>ON</vcn:nextpage></ivec:param_set></ivec:contents></cmd>"
    private val canonEndJob =
        "<?xml version=\"1.0\" encoding=\"utf-8\" ?><cmd xmlns:ivec=\"http://www.canon.com/ns/cmd/2008/07/common/\"><ivec:contents><ivec:operation>EndJob</ivec:operation><ivec:param_set servicetype=\"print\"><ivec:jobID>00000002</ivec:jobID></ivec:param_set></ivec:contents></cmd>"

    @Test
    fun selectedForIvecOnlyPrinters() {
        val g2030 = DeviceId.parse("MFG:Canon;CMD:IVEC;SOJ:CHMPu;MDL:G2030 series;CLS:PRINTER;DES:Canon G2030 series;")
        assertEquals(CanonIvecDriver, DriverSelector.select(g2030))
    }

    @Test
    fun jobCommandsMatchCanonsDriver() {
        val cmds = commands(print(canonTicket(), smallPage(), smallPage()))

        assertEquals(
            listOf("StartJob", "SetJobConfiguration", "SetConfiguration", "VendorCmd", "SendData", "VendorCmd", "SendData", "EndJob"),
            cmds.map { Regex("<ivec:operation>(\\w+)</ivec:operation>").find(it)!!.groupValues[1] },
        )
        val startJob = cmds[0].replace(Regex("CDATA\\[[0-9a-f]{32}]"), "CDATA[0123456789abcdef0123456789abcdef]")
        assertEquals(
            "<?xml version=\"1.0\" encoding=\"utf-8\" ?><cmd xmlns:ivec=\"http://www.canon.com/ns/cmd/2008/07/common/\" xmlns:vcn=\"http://www.canon.com/ns/cmd/2008/07/canon/\"><ivec:contents><ivec:operation>StartJob</ivec:operation><ivec:param_set servicetype=\"print\"><ivec:jobID>00000002</ivec:jobID><ivec:bidi>0</ivec:bidi><vcn:forcepmdetection>OFF</vcn:forcepmdetection><ivec:jobname/><ivec:username/><ivec:computername/><ivec:job_description><![CDATA[0123456789abcdef0123456789abcdef]]></ivec:job_description><vcn:host_environment>linux</vcn:host_environment></ivec:param_set></ivec:contents></cmd>",
            startJob,
        )
        assertEquals(canonSetJobConfiguration, cmds[1])
        assertEquals(canonSetConfigurationA4Color, cmds[2])
        assertEquals(canonNextPageOn, cmds[3])
        assertEquals(canonNextPageOn.replace(">ON<", ">OFF<"), cmds[5], "the last page says no page follows")
        assertEquals(canonEndJob, cmds[7])
    }

    @Test
    fun monochromeAndLetterOnlyChangeSetConfiguration() {
        val mono = commands(print(canonTicket(ColorMode.MONOCHROME, MediaSize.NA_LETTER), smallPage()))[2]
        assertEquals(
            canonSetConfigurationA4Color
                .replace("iso_a4_210x297mm", "na_letter_8.5x11in")
                .replace(">color<", ">monochrome<"),
            mono,
        )
    }

    @Test
    fun sendDataAnnouncesTheExactPwgFileThatFollows() {
        val stream = print(canonTicket(), smallPage(width = 20, height = 3))
        val sendData = Regex("<ivec:operation>SendData</ivec:operation>.*?<ivec:datasize>(\\d+)</ivec:datasize>.*?</cmd>")
            .find(stream)!!
        val size = sendData.groupValues[1].toInt()
        val payload = stream.substring(sendData.range.last + 1, sendData.range.last + 1 + size)

        assertTrue(payload.startsWith("RaS2PwgRaster"))
        // Right after the page comes the EndJob command, nothing else.
        assertTrue(stream.substring(sendData.range.last + 1 + size).startsWith("<?xml"))
        val header = ByteBuffer.wrap(payload.substring(4, 4 + 1796).toByteArray(Charsets.ISO_8859_1))
        assertEquals(600, header.getInt(276))
        assertEquals(20, header.getInt(372))
        assertEquals(3, header.getInt(376))
        assertEquals(24, header.getInt(388))
        assertEquals(19, header.getInt(400), "sRGB")
        assertEquals(1, header.getInt(452), "TotalPageCount")
        assertEquals(0xFFFFFF, header.getInt(480))
    }

    @Test
    fun printableAreaMatchesThePrintersCapability() {
        // G2030 GetCapability: printarea_border A4 4800,6826 and margin_border 300,340,500,340 (1/100 mm).
        assertEquals(PixelArea(80, 70, 4800, 6826), CanonIvecDriver.printableArea(canonTicket()))
        assertEquals(PixelArea(151, 70, 4800, 6411), CanonIvecDriver.printableArea(canonTicket(media = MediaSize.NA_LETTER)))
        // F4: printarea_border na_foolscap_8.5x13in 4800,7611.
        assertEquals(PixelArea(151, 70, 4800, 7611), CanonIvecDriver.printableArea(canonTicket(media = MediaSize.NA_FOOLSCAP)))
        assertEquals(RasterColorSpace.RGB24, CanonIvecDriver.colorSpaceFor(canonTicket(ColorMode.MONOCHROME)))
    }
}
