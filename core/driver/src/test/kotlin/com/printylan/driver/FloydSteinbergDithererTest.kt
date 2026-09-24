package com.printylan.driver

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class FloydSteinbergDithererTest {
    @Test
    fun solidColorsMapToSolidBits() {
        val ditherer = FloydSteinbergDitherer(10)
        val out = ByteArray(ditherer.bytesPerRow)
        ditherer.ditherRow(ByteArray(10) { 0xFF.toByte() }, out)
        assertContentEquals(byteArrayOf(0, 0), out)
        ditherer.ditherRow(ByteArray(10), out)
        assertContentEquals(byteArrayOf(0xFF.toByte(), 0xC0.toByte()), out)
    }

    @Test
    fun midGrayProducesRoughlyHalfBlack() {
        val width = 200
        val ditherer = FloydSteinbergDitherer(width)
        val out = ByteArray(ditherer.bytesPerRow)
        var black = 0
        repeat(50) {
            ditherer.ditherRow(ByteArray(width) { 128.toByte() }, out)
            black += out.sumOf { Integer.bitCount(it.toInt() and 0xFF) }
        }
        assertEquals(0.5, black / (width * 50.0), 0.05)
    }
}
