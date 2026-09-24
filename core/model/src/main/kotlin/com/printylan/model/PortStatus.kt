package com.printylan.model

/**
 * Printer state from the USB printer class GET_PORT_STATUS request
 * (USB Printer Class 1.1, section 4.2.2). Only bits 3, 4 and 5 carry meaning.
 */
data class PortStatus(
    val paperEmpty: Boolean,
    val selected: Boolean,
    val error: Boolean,
) {
    val isReady: Boolean get() = selected && !error && !paperEmpty

    companion object {
        fun fromByte(value: Int): PortStatus = PortStatus(
            paperEmpty = value and 0x20 != 0,
            selected = value and 0x10 != 0,
            // Bit 3 is "Not Error": 1 means no error.
            error = value and 0x08 == 0,
        )
    }
}
