package com.printylan.driver

import java.io.ByteArrayOutputStream

/** TIFF PackBits run length encoding, used by PCL compression mode 2. */
object PackBits {
    fun encode(src: ByteArray, length: Int, out: ByteArrayOutputStream) {
        var i = 0
        while (i < length) {
            var run = 1
            while (i + run < length && run < 128 && src[i + run] == src[i]) run++
            if (run >= 2) {
                out.write(1 - run)
                out.write(src[i].toInt())
                i += run
                continue
            }
            val start = i
            var count = 0
            while (i < length && count < 128) {
                if (i + 1 < length && src[i] == src[i + 1]) break
                i++
                count++
            }
            out.write(count - 1)
            out.write(src, start, count)
        }
    }
}
