package org.ziyoffice.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import org.ziyoffice.ui.PdfMark
import org.ziyoffice.ui.bakeMarks

/**
 * Reads a PDF with the platform's [PdfRenderer], the same engine Android uses
 * for print preview, so a file renders here the way the system renders it.
 *
 * The renderer holds an open file descriptor, so [close] has to be called.
 */
class PdfDocument private constructor(
    private val descriptor: ParcelFileDescriptor,
    private val renderer: PdfRenderer,
) : AutoCloseable {

    val pageCount: Int get() = renderer.pageCount

    /** Renders page [index] at [widthPx] wide, preserving the page's aspect ratio. */
    fun renderPage(index: Int, widthPx: Int): Bitmap? {
        if (index !in 0 until pageCount) return null
        return renderer.openPage(index).use { page ->
            val heightPx = (widthPx.toFloat() * page.height / page.width).toInt().coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
            // PDF pages are transparent by default; paper is white.
            bitmap.eraseColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            bitmap
        }
    }

    /** Page size in points (1/72 inch), for layout and export. */
    fun pageSize(index: Int): Pair<Int, Int>? {
        if (index !in 0 until pageCount) return null
        return renderer.openPage(index).use { it.width to it.height }
    }

    override fun close() {
        runCatching { renderer.close() }
        runCatching { descriptor.close() }
    }

    companion object {
        /**
         * Exports pages taken from any of [documents], in [pages] order, baking
         * [marks] into the pages they belong to. Merge, reorder and page
         * deletion are built on this.
         */
        fun exportTo(
            output: OutputStream,
            documents: List<PdfDocument>,
            pages: List<PdfRewriter.PageRef>,
            marks: List<PdfMark>,
            onProgress: ((done: Int, total: Int) -> Unit)? = null,
            pageNumbers: Boolean = false,
        ) {
            PdfRewriter.export(
                renderers = documents.map { it.renderer },
                pages = pages,
                output = output,
                onProgress = onProgress,
                pageNumbers = pageNumbers,
                drawOn = { ref, canvas ->
                    bakeMarks(canvas, ref.documentIndex, ref.sourceIndex, marks)
                },
            )
        }

        /**
         * Opens a PDF from a file descriptor the caller owns. The descriptor is
         * duplicated internally, so the caller may close its own copy.
         */
        fun open(descriptor: ParcelFileDescriptor): PdfDocument? = runCatching {
            val duplicate = descriptor.dup()
            val renderer = PdfRenderer(duplicate)
            PdfDocument(duplicate, renderer)
        }.getOrNull()

        /** Copies [input] to a private cache file, because PdfRenderer needs seekable input. */
        fun open(input: InputStream, cacheFile: File): PdfDocument? {
            runCatching {
                cacheFile.parentFile?.mkdirs()
                cacheFile.outputStream().use { out -> input.copyTo(out) }
            }.getOrElse { return null }
            val descriptor = runCatching {
                ParcelFileDescriptor.open(cacheFile, ParcelFileDescriptor.MODE_READ_ONLY)
            }.getOrNull() ?: return null
            return open(descriptor) ?: run {
                runCatching { descriptor.close() }
                null
            }
        }
    }
}
