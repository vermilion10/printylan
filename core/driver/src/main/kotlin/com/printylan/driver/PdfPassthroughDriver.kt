package com.printylan.driver

import com.printylan.model.ColorMode
import com.printylan.model.DuplexMode
import com.printylan.model.JobTicket
import java.io.InputStream
import java.io.OutputStream

/**
 * Sends the PDF as is. The printer decides on color and duplex, so the dialog offers
 * no duplex choice, and copies go out as repeated documents.
 */
object PdfPassthroughDriver : PassthroughDriver {
    override val id = DriverId.PDF

    override val capabilities = StandardCapabilities.build(
        colorModes = setOf(ColorMode.MONOCHROME, ColorMode.COLOR),
        duplexModes = setOf(DuplexMode.NONE),
    )

    override fun send(openDocument: () -> InputStream, out: OutputStream, ticket: JobTicket) {
        repeat(ticket.copies.coerceAtLeast(1)) {
            openDocument().use { it.copyTo(out) }
        }
        out.flush()
    }
}
