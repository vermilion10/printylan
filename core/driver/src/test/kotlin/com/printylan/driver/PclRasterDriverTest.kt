package com.printylan.driver

import com.printylan.model.DuplexMode
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PclRasterDriverTest {
    @Test
    fun writesJobSetupAndOneFormFeedPerPage() {
        val black = ArrayRasterPage(16, 3, RasterColorSpace.GRAY8, ByteArray(48))
        val white = ArrayRasterPage(16, 3, RasterColorSpace.GRAY8)
        val out = ByteArrayOutputStream()
        PclRasterDriver.startJob(out, ticket(duplexMode = DuplexMode.LONG_EDGE, copies = 3), 2).use {
            it.writePage(black)
            it.writePage(white)
        }
        val text = String(out.toByteArray(), Charsets.ISO_8859_1)

        assertTrue(text.startsWith("\u001B%-12345X@PJL"))
        assertTrue("\u001B&l3X" in text, "copies")
        assertTrue("\u001B&l26A" in text, "A4 page size")
        assertTrue("\u001B&l1S" in text, "long edge duplex")
        assertTrue("\u001B*t300R" in text, "resolution")
        // A 16 pixel black row packs to two 0xFF bytes: one run control byte plus the value.
        assertEquals(3, Regex("\u001B\\*b2Wÿÿ").findAll(text).count())
        assertTrue("\u001B*b3Y" !in text, "blank rows at the end of a page are not sent")
        assertEquals(2, text.count { it == '\u000C' })
        assertTrue(text.endsWith("@PJL EOJ\r\n\u001B%-12345X"))
    }
}
