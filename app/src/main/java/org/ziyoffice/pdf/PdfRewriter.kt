package org.ziyoffice.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfDocument as AndroidPdfDocument
import android.graphics.pdf.PdfRenderer
import java.io.OutputStream

/**
 * Page-level PDF operations built on the platform renderer and the platform PDF
 * writer — no PDF library, no network.
 *
 * Every operation re-renders pages and writes a fresh document, which has one
 * deliberate consequence: the output is raster. That is the right trade for this
 * app because it makes redaction *real* — the removed text is gone from the file,
 * not merely covered by a black rectangle that any editor can peel back. The cost
 * is that text in an edited file is no longer selectable, so operations that do
 * not need to rewrite a page (viewing) never touch the original.
 */
object PdfRewriter {

    /** One source page to place in the output, with an optional rotation. */
    data class PageRef(
        /** Index into the renderer list the export was started with. */
        val documentIndex: Int = 0,
        val sourceIndex: Int,
        val rotateDegrees: Int = 0,
    )

    /**
     * Writes a new PDF containing [pages], in the order given, taking each page
     * from its [renderers] entry. [drawOn] runs per page after the page is
     * drawn, for signatures and redaction boxes; its canvas is in bitmap
     * coordinates.
     */
    fun export(
        renderers: List<PdfRenderer>,
        pages: List<PageRef>,
        output: OutputStream,
        onProgress: ((done: Int, total: Int) -> Unit)? = null,
        drawOn: ((page: PageRef, canvas: Canvas) -> Unit)? = null,
    ) {
        val document = AndroidPdfDocument()
        try {
            pages.forEachIndexed { outputIndex, ref ->
                val bitmap = render(renderers[ref.documentIndex], ref)
                if (drawOn != null) {
                    val canvas = Canvas(bitmap)
                    drawOn(ref, canvas)
                }

                val info = AndroidPdfDocument.PageInfo.Builder(
                    bitmap.width,
                    bitmap.height,
                    outputIndex + 1,
                ).create()
                val page = document.startPage(info)
                page.canvas.drawBitmap(bitmap, 0f, 0f, null)
                document.finishPage(page)
                bitmap.recycle()
                // Reported after each page so a long export can show real
                // progress instead of a spinner that never moves.
                onProgress?.invoke(outputIndex + 1, pages.size)
            }
            document.writeTo(output)
        } finally {
            document.close()
        }
    }

    /** Renders one source page to a bitmap at its native pixel size. */
    fun render(renderer: PdfRenderer, ref: PageRef): Bitmap {
        renderer.openPage(ref.sourceIndex).use { page ->
            val bitmap = Bitmap.createBitmap(
                page.width * RENDER_SCALE,
                page.height * RENDER_SCALE,
                Bitmap.Config.ARGB_8888,
            )
            bitmap.eraseColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            if (ref.rotateDegrees % 360 == 0) return bitmap

            val matrix = android.graphics.Matrix()
            matrix.postRotate(ref.rotateDegrees.toFloat())
            return Bitmap.createBitmap(
                bitmap,
                0,
                0,
                bitmap.width,
                bitmap.height,
                matrix,
                true,
            )
        }
    }

    /**
     * 2x render scale: enough that rasterised text stays legible when the result is
     * re-rendered on a phone, without producing multi-megabyte pages.
     */
    private const val RENDER_SCALE = 2
}
