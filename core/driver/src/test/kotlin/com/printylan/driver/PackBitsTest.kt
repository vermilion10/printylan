package com.printylan.driver

import java.io.ByteArrayOutputStream
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals

class PackBitsTest {
    @Test
    fun roundTripsMixedData() {
        val random = Random(42)
        val inputs = listOf(
            ByteArray(0),
            byteArrayOf(7),
            ByteArray(300) { 0 },
            ByteArray(300) { random.nextInt().toByte() },
            ByteArray(500) { i -> if (i % 50 < 20) 0 else (i % 7).toByte() },
        )
        for (input in inputs) {
            val out = ByteArrayOutputStream()
            PackBits.encode(input, input.size, out)
            assertContentEquals(input, decode(out.toByteArray()))
        }
    }

    private fun decode(data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        var i = 0
        while (i < data.size) {
            val n = data[i++].toInt()
            when {
                n >= 0 -> { out.write(data, i, n + 1); i += n + 1 }
                n != -128 -> { repeat(1 - n) { out.write(data[i].toInt()) }; i++ }
            }
        }
        return out.toByteArray()
    }
}
