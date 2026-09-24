package com.printylan.service

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.print.PrintJobId
import android.printservice.PrintJob
import android.util.Log
import com.printylan.AppContainer
import com.printylan.R
import com.printylan.driver.PassthroughDriver
import com.printylan.driver.RasterDriver
import com.printylan.model.JobTicket
import com.printylan.model.PortStatus
import com.printylan.render.BandedPdfPage
import com.printylan.usb.StallHandler
import com.printylan.usb.UsbPrinterConnection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream

/**
 * Runs print jobs: spool the PDF, convert it with the printer's driver, and stream it over USB.
 * `PrintJob` methods must run on the main thread; conversion and USB I/O run on the USB dispatcher.
 */
class PrintJobProcessor(
    private val context: Context,
    private val container: AppContainer,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val active = mutableMapOf<PrintJobId, Job>()

    fun enqueue(printJob: PrintJob) {
        if (!printJob.isQueued) return
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                run(printJob)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Job ${printJob.id} failed", e)
                if (!printJob.isCancelled) {
                    printJob.fail((e as? PrintFailure)?.let { context.getString(it.messageRes) } ?: context.getString(R.string.job_failed))
                }
            } finally {
                active.remove(printJob.id)
            }
        }
        active[printJob.id] = job
        job.start()
    }

    fun cancel(printJob: PrintJob) {
        active.remove(printJob.id)?.cancel()
        printJob.cancel()
    }

    fun shutdown() = scope.cancel()

    private suspend fun run(printJob: PrintJob) {
        // fail() only works on started jobs, so start before anything can throw.
        printJob.start()
        // PrintJob only answers on the main thread, so read everything it holds up front.
        val info = printJob.info
        val printer = container.scanner.find(info.printerId?.localId)
            ?: throw PrintFailure(R.string.job_printer_missing)
        if (!container.permissions.request(printer.device)) throw PrintFailure(R.string.job_access_denied)

        val spool = File.createTempFile("job-", ".pdf", context.cacheDir)
        try {
            val document = printJob.document.data ?: throw PrintFailure(R.string.job_no_document)
            withContext(Dispatchers.IO) {
                ParcelFileDescriptor.AutoCloseInputStream(document).use { input ->
                    spool.outputStream().use { input.copyTo(it) }
                }
            }
            withContext(container.usbDispatcher) {
                UsbPrinterConnection.open(container.usbManager, printer).use { connection ->
                    val driver = container.driverFor(printer, connection.readDeviceId())
                        ?: throw PrintFailure(R.string.printer_unsupported)
                    val ticket = AndroidPrintMapping.ticketFrom(info, driver.capabilities)
                    val stalls = stallHandler(printJob, connection, currentCoroutineContext().job)
                    connection.openOutputStream(stalls).use { out ->
                        when (driver) {
                            is PassthroughDriver -> driver.send({ spool.inputStream() }, out, ticket)
                            is RasterDriver -> rasterize(spool, driver, ticket, out)
                        }
                    }
                }
            }
            printJob.complete()
        } finally {
            spool.delete()
        }
    }

    private suspend fun rasterize(pdf: File, driver: RasterDriver, ticket: JobTicket, out: OutputStream) {
        val colorSpace = driver.colorSpaceFor(ticket)
        val area = driver.printableArea(ticket)
        val sheetWidth = ticket.media.widthPixels(ticket.resolution.xDpi)
        val sheetHeight = ticket.media.heightPixels(ticket.resolution.yDpi)
        val copies = if (driver.encodesCopies) 1 else ticket.copies
        ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { renderer ->
                driver.startJob(out, ticket, renderer.pageCount * copies).use { sink ->
                    repeat(copies) {
                        for (index in 0 until renderer.pageCount) {
                            currentCoroutineContext().ensureActive()
                            BandedPdfPage(renderer.openPage(index), sheetWidth, sheetHeight, area, colorSpace)
                                .use(sink::writePage)
                        }
                    }
                }
            }
        }
    }

    /** Marks the job blocked while the printer refuses data, and resumes it when data flows again. */
    private fun stallHandler(printJob: PrintJob, connection: UsbPrinterConnection, job: Job) = object : StallHandler {
        override fun onStall(attempt: Int): Boolean {
            val reason = context.getString(stallReason(connection.readPortStatus()))
            scope.launch { if (printJob.isStarted) printJob.block(reason) }
            return job.isActive
        }

        override fun onResumed() {
            scope.launch { if (printJob.isBlocked) printJob.start() }
        }
    }

    private fun stallReason(status: PortStatus?): Int = when {
        status == null -> R.string.status_busy
        status.paperEmpty -> R.string.status_out_of_paper
        status.error -> R.string.status_error
        !status.selected -> R.string.status_offline
        else -> R.string.status_busy
    }

    private class PrintFailure(val messageRes: Int) : Exception()

    private companion object {
        const val TAG = "PrintJobProcessor"
    }
}
