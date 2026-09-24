package com.printylan.render

import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import androidx.core.graphics.createBitmap
import com.printylan.driver.PixelArea
import com.printylan.driver.RasterColorSpace
import com.printylan.driver.RasterPage
import java.io.Closeable

/**
 * Renders a PDF page in horizontal bands. A full A4 page at 600 dpi needs about 140 MB as an
 * ARGB bitmap; a 128 row band needs about 2.5 MB.
 *
 * The page is scaled to fit a [sheetWidth] x [sheetHeight] sheet and centered, then cropped to
 * [area]. A landscape PDF page on portrait paper is rotated 90 degrees, because the printer
 * always feeds paper in portrait.
 */
class BandedPdfPage(
    private val page: PdfRenderer.Page,
    private val sheetWidth: Int,
    private val sheetHeight: Int,
    private val area: PixelArea,
    override val colorSpace: RasterColorSpace,
    private val bandHeight: Int = 128,
) : RasterPage, Closeable {
    override val width: Int get() = area.width
    override val height: Int get() = area.height

    private val band = createBitmap(width, bandHeight)
    private val pixels = IntArray(width)
    private val matrix = Matrix()
    private var bandTop = -1

    override fun readRow(y: Int, dst: ByteArray) {
        if (bandTop < 0 || y < bandTop || y >= bandTop + bandHeight) renderBand(y)
        band.getPixels(pixels, 0, width, 0, y - bandTop, width, 1)
        when (colorSpace) {
            RasterColorSpace.GRAY8 -> for (x in 0 until width) {
                val c = pixels[x]
                dst[x] = ((Color.red(c) * 299 + Color.green(c) * 587 + Color.blue(c) * 114) / 1000).toByte()
            }
            RasterColorSpace.RGB24 -> for (x in 0 until width) {
                val c = pixels[x]
                dst[x * 3] = Color.red(c).toByte()
                dst[x * 3 + 1] = Color.green(c).toByte()
                dst[x * 3 + 2] = Color.blue(c).toByte()
            }
        }
    }

    private fun renderBand(top: Int) {
        val srcW = page.width.toFloat()
        val srcH = page.height.toFloat()
        val rotate = (srcW > srcH) != (sheetWidth > sheetHeight)
        val fitW = if (rotate) srcH else srcW
        val fitH = if (rotate) srcW else srcH
        val scale = minOf(sheetWidth / fitW, sheetHeight / fitH)

        matrix.reset()
        matrix.postScale(scale, scale)
        if (rotate) {
            matrix.postRotate(90f)
            matrix.postTranslate(srcH * scale, 0f)
        }
        matrix.postTranslate(
            (sheetWidth - fitW * scale) / 2f - area.left,
            (sheetHeight - fitH * scale) / 2f - area.top - top,
        )

        // PdfRenderer only draws page content, so paint the paper white first.
        band.eraseColor(Color.WHITE)
        page.render(band, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
        bandTop = top
    }

    override fun close() {
        band.recycle()
        page.close()
    }
}
