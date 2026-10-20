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
 * No permission is involved: the app renders the pages into a PDF in memory and
 * hands the bytes to [PrintManager], so the system print dialog shows a real
 * preview and the user chooses printer or "Save as PDF". This is the same
 * offline path as export — nothing leaves the device unless the user picks a
 * printer that is not the local PDF writer.
 *
 * The PDF is produced by the existing exporters, so what prints is exactly what
 * export produces: the document's own layout, the deck's text pages, or the
 * sheet's computed values.
 */
object Printer {

    /**
     * Renders [pdfBytes] and opens the system print dialog under [jobName].
     * Returns false when the platform has no print service (rare, but some
     * stripped-down builds omit it), so the caller can say so instead of
     * silently doing nothing.
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
                // The bytes are a complete, paginated PDF: all pages are always
                // written, which is what a document produced by our exporters
                // means. Writing a subset would require re-rendering.
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
