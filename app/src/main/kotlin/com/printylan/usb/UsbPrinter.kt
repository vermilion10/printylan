package com.printylan.usb

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface

/** A USB device with a usable printer class interface. */
data class UsbPrinter(
    val device: UsbDevice,
    val printerInterface: UsbInterface,
    val bulkOut: UsbEndpoint,
    val bulkIn: UsbEndpoint?,
    /** Stable across replugs and permission changes. Used as the Android `PrinterId` local id. */
    val localId: String,
) {
    val vendorId: Int get() = device.vendorId
    val productId: Int get() = device.productId

    /** Same value for every unit of one model; keys per model settings such as the driver. */
    val modelKey: String get() = "%04x:%04x".format(vendorId, productId)

    val fallbackName: String
        get() = listOfNotNull(device.manufacturerName, device.productName)
            .joinToString(" ")
            .ifBlank { "USB printer $modelKey" }

    companion object {
        private const val SUBCLASS_PRINTER = 1
        private const val PROTOCOL_UNIDIRECTIONAL = 1
        private const val PROTOCOL_BIDIRECTIONAL = 2

        /**
         * Picks the best printer interface, or null if the device has none we can drive.
         * Protocol 3 (IEEE 1284.4) and 4 (IPP over USB) need their own transports.
         */
        fun from(device: UsbDevice, localId: String): UsbPrinter? {
            val candidates = (0 until device.interfaceCount)
                .map(device::getInterface)
                .filter {
                    it.interfaceClass == UsbConstants.USB_CLASS_PRINTER &&
                        it.interfaceSubclass == SUBCLASS_PRINTER &&
                        it.interfaceProtocol in PROTOCOL_UNIDIRECTIONAL..PROTOCOL_BIDIRECTIONAL
                }
                .sortedByDescending { it.interfaceProtocol }
            for (intf in candidates) {
                val endpoints = (0 until intf.endpointCount).map(intf::getEndpoint)
                    .filter { it.type == UsbConstants.USB_ENDPOINT_XFER_BULK }
                val out = endpoints.firstOrNull { it.direction == UsbConstants.USB_DIR_OUT } ?: continue
                val `in` = endpoints.firstOrNull { it.direction == UsbConstants.USB_DIR_IN }
                return UsbPrinter(device, intf, out, `in`, localId)
            }
            return null
        }
    }
}
