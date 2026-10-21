package org.ziyoffice.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument as AndroidPdfDocument
import android.graphics.pdf.PdfRenderer
import java.io.OutputStream

/**
 * Page-level PDF operations on the platform renderer and writer. No PDF library,
 * no network.
 *
 * Every operation re-renders into a fresh document, so the output is raster.
 * That makes redaction real, the removed text is gone from the file rather than
 * hidden under a peelable box. The cost is that text stops being selectable.
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
     * Writes a new PDF containing [pages], in the order given, each page coming
     * from its [renderers] entry. [drawOn] runs per page after it is drawn, for
     * signatures and redaction boxes, with its canvas in bitmap coordinates.
     */
    fun export(
        renderers: List<PdfRenderer>,
        pages: List<PageRef>,
        output: OutputStream,
        onProgress: ((done: Int, total: Int) -> Unit)? = null,
        drawOn: ((page: PageRef, canvas: Canvas) -> Unit)? = null,
        pageNumbers: Boolean = false,
    ) {
        val document = AndroidPdfDocument()
        try {
            pages.forEachIndexed { outputIndex, ref ->
                val bitmap = render(renderers[ref.documentIndex], ref)
                if (drawOn != null || pageNumbers) {
                    val canvas = Canvas(bitmap)
                    drawOn?.invoke(ref, canvas)
                    if (pageNumbers) {
                        drawPageNumber(canvas, outputIndex + 1, pages.size)
                    }
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
                // Reported per page so a long export shows real progress.
                onProgress?.invoke(outputIndex + 1, pages.size)
            }
            document.writeTo(output)
        } finally {
            document.close()
        }
    }

    /** The "3 / 12" footer, drawn after the marks so nothing covers it. */
    private fun drawPageNumber(canvas: Canvas, index: Int, total: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(0x99, 0x33, 0x33, 0x33)
            textSize = 18f
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText(
            "$index / $total",
            canvas.width - 24f,
            canvas.height - 18f,
            paint,
        )
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

    /** 2x scale: rasterised text stays legible on a phone without huge pages. */
    private const val RENDER_SCALE = 2
}
