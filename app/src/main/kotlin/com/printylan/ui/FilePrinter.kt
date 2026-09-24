package com.printylan.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.provider.OpenableColumns
import androidx.print.PrintHelper
import java.io.FileOutputStream
import java.io.IOException
import kotlin.concurrent.thread

/**
 * Hands a file the user picked to the system print dialog, where they choose the printer and
 * settings. PDFs pass through unchanged; images are scaled to fit the page.
 */
object FilePrinter {
    /** MIME types the picker offers. */
    val mimeTypes = arrayOf("application/pdf", "image/*")

    /** Returns false when the file cannot be opened. */
    fun print(context: Context, uri: Uri): Boolean {
        val name = displayName(context, uri) ?: "Document"
        val type = context.contentResolver.getType(uri).orEmpty()
        return try {
            if (type.startsWith("image/")) {
                PrintHelper(context).apply {
                    scaleMode = PrintHelper.SCALE_MODE_FIT
                    // PrintHelper defaults to landscape, which shrinks portrait photos.
                    orientation = if (isLandscape(context, uri)) {
                        PrintHelper.ORIENTATION_LANDSCAPE
                    } else {
                        PrintHelper.ORIENTATION_PORTRAIT
                    }
                }.printBitmap(name, uri)
            } else {
                // Fail now rather than inside the print dialog if the file is gone.
                context.contentResolver.openFileDescriptor(uri, "r")?.close() ?: return false
                context.getSystemService(PrintManager::class.java)
                    .print(name, PdfAdapter(context.applicationContext, uri, name), null)
            }
            true
        } catch (_: IOException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }

    /** Reads only the image header, so this is cheap even for large photos. */
    private fun isLandscape(context: Context, uri: Uri): Boolean {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        return bounds.outWidth > bounds.outHeight
    }

    private fun displayName(context: Context, uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }

    /** Streams the PDF to the print framework as is; the framework counts its pages. */
    private class PdfAdapter(
        private val context: Context,
        private val uri: Uri,
        private val name: String,
    ) : PrintDocumentAdapter() {
        override fun onLayout(
            oldAttributes: PrintAttributes?,
            newAttributes: PrintAttributes,
            cancellationSignal: CancellationSignal,
            callback: LayoutResultCallback,
            extras: Bundle?,
        ) {
            if (cancellationSignal.isCanceled) {
                callback.onLayoutCancelled()
                return
            }
            val info = PrintDocumentInfo.Builder(name)
                .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                .build()
            callback.onLayoutFinished(info, oldAttributes == null)
        }

        override fun onWrite(
            pages: Array<out PageRange>,
            destination: ParcelFileDescriptor,
            cancellationSignal: CancellationSignal,
            callback: WriteResultCallback,
        ) {
            // onWrite runs on the main thread; copy a large PDF off it.
            thread(name = "print-copy") {
                try {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(destination.fileDescriptor).use { output ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                if (cancellationSignal.isCanceled) {
                                    callback.onWriteCancelled()
                                    return@thread
                                }
                                val read = input.read(buffer)
                                if (read < 0) break
                                output.write(buffer, 0, read)
                            }
                        }
                    } ?: throw IOException("Cannot open $uri")
                    callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                } catch (e: IOException) {
                    callback.onWriteFailed(e.message)
                }
            }
        }
    }
}
