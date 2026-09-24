package com.printylan.service

import android.printservice.PrintJob
import android.printservice.PrintService
import android.printservice.PrinterDiscoverySession
import com.printylan.appContainer

/**
 * Entry point for the Android print framework. The system binds to this service while the
 * print dialog is open or jobs are active, and calls every method on the main thread.
 */
class PrintylanPrintService : PrintService() {
    private lateinit var jobs: PrintJobProcessor

    override fun onCreate() {
        super.onCreate()
        jobs = PrintJobProcessor(this, appContainer)
    }

    override fun onCreatePrinterDiscoverySession(): PrinterDiscoverySession =
        UsbDiscoverySession(this, appContainer)

    override fun onPrintJobQueued(printJob: PrintJob) = jobs.enqueue(printJob)

    override fun onRequestCancelPrintJob(printJob: PrintJob) = jobs.cancel(printJob)

    override fun onDestroy() {
        jobs.shutdown()
        super.onDestroy()
    }
}
