package com.printylan.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeviceIdTest {
    @Test
    fun parsesShortAndLongKeys() {
        val id = DeviceId.parse("MANUFACTURER:Brother;MODEL:HL-L2350DW series;COMMAND SET:PJL, PCL,PCLXL;CLS:PRINTER;")
        assertEquals("Brother", id.manufacturer)
        assertEquals("HL-L2350DW series", id.model)
        assertEquals(listOf("PJL", "PCL", "PCLXL"), id.commandSet)
        assertEquals("Brother HL-L2350DW series", id.displayName)
    }

    @Test
    fun avoidsRepeatingManufacturerInDisplayName() {
        assertEquals("HP LaserJet P1102", DeviceId.parse("MFG:HP;MDL:HP LaserJet P1102;").displayName)
    }

    @Test
    fun decodesPortStatusBits() {
        val ready = PortStatus.fromByte(0x18)
        assertTrue(ready.isReady)
        val outOfPaper = PortStatus.fromByte(0x38)
        assertTrue(outOfPaper.paperEmpty)
        assertFalse(outOfPaper.isReady)
        assertTrue(PortStatus.fromByte(0x10).error)
    }
}
