package com.printylan.usb

import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import java.io.IOException
import java.io.OutputStream

/** Called when the printer stops accepting data, most often because it ran out of paper. */
interface StallHandler {
    /** Returns true to keep waiting and retry, false to give up. [attempt] starts at 1. */
    fun onStall(attempt: Int): Boolean

    /** The printer accepted data again after a stall. */
    fun onResumed()
}

/** Buffers writes into bulk transfers of at most [CHUNK_SIZE] bytes. */
internal class UsbBulkOutputStream(
    private val connection: UsbDeviceConnection,
    private val endpoint: UsbEndpoint,
    private val stallHandler: StallHandler,
) : OutputStream() {
    private val buffer = ByteArray(CHUNK_SIZE)
    private var count = 0
    private var closed = false

    override fun write(b: Int) {
        if (count == buffer.size) drain()
        buffer[count++] = b.toByte()
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        var offset = off
        var remaining = len
        while (remaining > 0) {
            if (count == buffer.size) drain()
            val n = minOf(remaining, buffer.size - count)
            System.arraycopy(b, offset, buffer, count, n)
            count += n
            offset += n
            remaining -= n
        }
    }

    override fun flush() = drain()

    override fun close() {
        if (closed) return
        drain()
        closed = true
    }

    private fun drain() {
        check(!closed) { "Stream is closed" }
        var sent = 0
        var stalls = 0
        while (sent < count) {
            val n = connection.bulkTransfer(endpoint, buffer, sent, count - sent, WRITE_TIMEOUT_MS)
            if (n > 0) {
                sent += n
                if (stalls > 0) {
                    stalls = 0
                    stallHandler.onResumed()
                }
            } else if (!stallHandler.onStall(++stalls)) {
                throw IOException("Printer stopped accepting data")
            }
        }
        count = 0
    }

    private companion object {
        // Android before 9 caps a single bulk transfer at 16 KiB.
        const val CHUNK_SIZE = 16 * 1024
        const val WRITE_TIMEOUT_MS = 10_000
    }
}
