package org.ziyoffice.export

import android.content.Context
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import java.io.ByteArrayOutputStream

/**
 * Prints any ZiyOffice document through Android's own print service.
 *
 * No permission is involved: the pages are rendered into a PDF in memory and
 * the bytes go to [PrintManager], so the system dialog shows a real preview and
 * the user picks a printer or "Save as PDF". Same offline path as export, so
 * what prints is what export produces.
 */
object Printer {

    /**
     * Opens the system print dialog under [jobName] with [pdfBytes]. False when
     * the platform has no print service (some stripped-down builds omit it), so
     * the caller can say so instead of doing nothing.
     */
    fun print(context: Context, jobName: String, pdfBytes: ByteArray): Boolean {
        val manager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
            ?: return false
        val adapter = BytesPrintAdapter(context, jobName, pdfBytes)
        manager.print(jobName, adapter, PrintAttributes.Builder().build())
        return true
    }

    /** Serves already-rendered PDF bytes to the print framework. */
    private class BytesPrintAdapter(
        private val context: Context,
        private val jobName: String,
        private val pdfBytes: ByteArray,
    ) : PrintDocumentAdapter() {

        override fun onLayout(
            oldAttributes: PrintAttributes?,
            newAttributes: PrintAttributes?,
            cancellationSignal: CancellationSignal?,
            callback: LayoutResultCallback?,
            extras: Bundle?,
        ) {
            if (cancellationSignal?.isCanceled == true) {
                callback?.onLayoutCancelled()
                return
            }
            // The exporters already chose page size and count, so the framework
            // is told the document is ready rather than re-laid out.
            val info = PrintDocumentInfo.Builder(jobName)
                .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                .build()
            callback?.onLayoutFinished(info, true)
        }

        override fun onWrite(
            pages: Array<out PageRange>?,
            destination: ParcelFileDescriptor?,
            cancellationSignal: CancellationSignal?,
            callback: WriteResultCallback?,
        ) {
            if (destination == null) {
                callback?.onWriteFailed("The print destination was not available")
                return
            }
            try {
                // The bytes are a complete, paginated PDF, so all pages are always
                // written. Writing a subset would mean re-rendering.
                ParcelFileDescriptor.AutoCloseOutputStream(destination).use { out ->
                    out.write(pdfBytes)
                    out.flush()
                }
                if (cancellationSignal?.isCanceled == true) {
                    callback?.onWriteCancelled()
                } else {
                    callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                }
            } catch (error: Exception) {
                callback?.onWriteFailed(error.message ?: error.javaClass.simpleName)
            }
        }
    }

    /** Renders a document to PDF bytes for printing, without touching the file. */
    fun documentBytes(document: org.ziyoffice.model.Document): ByteArray {
        val buffer = ByteArrayOutputStream()
        PdfExporter.export(document, buffer)
        return buffer.toByteArray()
    }

    /** Renders a deck to PDF bytes for printing. */
    fun deckBytes(deck: org.ziyoffice.model.SlideDeck): ByteArray {
        val buffer = ByteArrayOutputStream()
        DeckExporter.export(deck, buffer)
        return buffer.toByteArray()
    }

    /** Renders a workbook to PDF bytes for printing. */
    fun workbookBytes(workbook: org.ziyoffice.model.Workbook): ByteArray {
        val buffer = ByteArrayOutputStream()
        SheetExporter.export(workbook, buffer)
        return buffer.toByteArray()
    }
}
