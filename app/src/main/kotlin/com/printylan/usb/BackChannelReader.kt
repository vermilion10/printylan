package com.printylan.usb

import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import java.io.Closeable
import kotlin.concurrent.thread

/**
 * Keeps reading the bulk IN endpoint of a bidirectional printer while a job runs.
 *
 * Some printers queue status messages there and stop printing when nobody reads them: a
 * Canon G2030 fed every job after the first through blank until its queue was drained.
 * Canon's own backend reads continuously, so Printylan does too and discards the data.
 */
internal class BackChannelReader(
    connection: UsbDeviceConnection,
    endpoint: UsbEndpoint,
) : Closeable {
    @Volatile private var running = true

    private val reader = thread(name = "usb-back-channel", isDaemon = true) {
        val buffer = ByteArray(endpoint.maxPacketSize.coerceAtLeast(512) * 8)
        while (running) {
            // A short timeout so close() never waits long for this thread.
            connection.bulkTransfer(endpoint, buffer, buffer.size, READ_TIMEOUT_MS)
        }
    }

    /** Stops after [lingerMs], giving the printer time to answer the end of the job. */
    fun close(lingerMs: Long) {
        if (lingerMs > 0) Thread.sleep(lingerMs)
        close()
    }

    override fun close() {
        running = false
        reader.join(READ_TIMEOUT_MS * 2L)
    }

    private companion object {
        const val READ_TIMEOUT_MS = 250
    }
}
