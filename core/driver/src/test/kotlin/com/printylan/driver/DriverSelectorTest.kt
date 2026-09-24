package com.printylan.driver

import com.printylan.model.DeviceId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DriverSelectorTest {
    @Test
    fun prefersPdfThenPwgThenPcl() {
        assertEquals(DriverId.PDF, select("MFG:X;MDL:Y;CMD:PCL,PDF,PWGRaster;").id)
        assertEquals(DriverId.PWG_RASTER, select("MFG:X;MDL:Y;CMD:PCL,PWGRaster;").id)
        assertEquals(DriverId.PCL, select("MFG:HP;MDL:LaserJet;COMMAND SET:PJL,MLC,PCL5E;").id)
    }

    @Test
    fun returnsNullForHostBasedPrinters() {
        assertNull(DriverSelector.select(DeviceId.parse("MFG:HP;MDL:LaserJet 1020;CMD:ACL;")))
        assertNull(DriverSelector.select(null))
    }

    private fun select(raw: String) = DriverSelector.select(DeviceId.parse(raw))!!
}
