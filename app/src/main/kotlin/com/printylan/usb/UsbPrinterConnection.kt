package com.printylan.usb

import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import com.printylan.model.DeviceId
import com.printylan.model.PortStatus
import java.io.Closeable
import java.io.IOException
import java.io.OutputStream

/**
 * An open, claimed printer interface. Implements the USB Printer Class 1.1 requests.
 * Every call blocks, so run it on [com.printylan.AppContainer.usbDispatcher].
 */
class UsbPrinterConnection private constructor(
    private val printer: UsbPrinter,
    private val connection: UsbDeviceConnection,
) : Closeable {

    /** GET_DEVICE_ID: a two byte big endian length (which counts itself) and then the ID string. */
    fun readDeviceId(): DeviceId? {
        val intf = printer.printerInterface
        val buffer = ByteArray(1024)
        val read = connection.controlTransfer(
            REQUEST_TYPE_CLASS_INTERFACE_IN,
            GET_DEVICE_ID,
            0, // configuration index
            (intf.id shl 8) or intf.alternateSetting,
            buffer,
            buffer.size,
            CONTROL_TIMEOUT_MS,
        )
        if (read < 2) return null
        val bigEndian = ((buffer[0].toInt() and 0xFF) shl 8) or (buffer[1].toInt() and 0xFF)
        val littleEndian = ((buffer[1].toInt() and 0xFF) shl 8) or (buffer[0].toInt() and 0xFF)
        // Some printers send the length little endian; trust whichever value fits.
        val length = listOf(bigEndian, littleEndian).firstOrNull { it in 2..read } ?: read
        return DeviceId.parse(String(buffer, 2, length - 2, Charsets.ISO_8859_1))
    }

    /** GET_PORT_STATUS. Returns null when the printer does not answer. */
    fun readPortStatus(): PortStatus? {
        val buffer = ByteArray(1)
        val read = connection.controlTransfer(
            REQUEST_TYPE_CLASS_INTERFACE_IN,
            GET_PORT_STATUS,
            0,
            printer.printerInterface.id,
            buffer,
            1,
            CONTROL_TIMEOUT_MS,
        )
        return if (read == 1) PortStatus.fromByte(buffer[0].toInt() and 0xFF) else null
    }

    /**
     * Opens the job stream. On bidirectional printers the back channel is drained for as long
     * as the stream is open and for a moment after, see [BackChannelReader].
     */
    fun openOutputStream(stallHandler: StallHandler): OutputStream {
        val backChannel = printer.bulkIn?.let { BackChannelReader(connection, it) }
        val stream = UsbBulkOutputStream(connection, printer.bulkOut, stallHandler)
        if (backChannel == null) return stream
        return object : OutputStream() {
            override fun write(b: Int) = stream.write(b)
            override fun write(b: ByteArray, off: Int, len: Int) = stream.write(b, off, len)
            override fun flush() = stream.flush()
            override fun close() {
                try {
                    stream.close()
                } finally {
                    backChannel.close(lingerMs = BACK_CHANNEL_LINGER_MS)
                }
            }
        }
    }

    override fun close() {
        connection.releaseInterface(printer.printerInterface)
        connection.close()
    }

    companion object {
        private const val REQUEST_TYPE_CLASS_INTERFACE_IN = 0xA1
        private const val GET_DEVICE_ID = 0
        private const val GET_PORT_STATUS = 1
        private const val CONTROL_TIMEOUT_MS = 5_000
        private const val BACK_CHANNEL_LINGER_MS = 3_000L

        fun open(usbManager: UsbManager, printer: UsbPrinter): UsbPrinterConnection {
            val connection = usbManager.openDevice(printer.device)
                ?: throw IOException("Cannot open USB device ${printer.device.deviceName}")
            val intf = printer.printerInterface
            if (!connection.claimInterface(intf, true)) {
                connection.close()
                throw IOException("Cannot claim printer interface ${intf.id}")
            }
            if (intf.alternateSetting != 0 && !connection.setInterface(intf)) {
                connection.releaseInterface(intf)
                connection.close()
                throw IOException("Cannot select alternate setting ${intf.alternateSetting}")
            }
            return UsbPrinterConnection(printer, connection)
        }
    }
}
