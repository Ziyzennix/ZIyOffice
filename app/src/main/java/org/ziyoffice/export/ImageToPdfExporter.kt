package org.ziyoffice.export

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import java.io.OutputStream
import kotlin.math.roundToInt
import org.ziyoffice.model.ImagePagePlacement
import org.ziyoffice.model.ImageToPdfConfig
import org.ziyoffice.model.ImageToPdfLayout

/**
 * Compiles one or more pictures directly into a standard PDF document.
 */
object ImageToPdfExporter {

    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

    fun export(
        bitmaps: List<Bitmap>,
        output: OutputStream,
        config: ImageToPdfConfig = ImageToPdfConfig(),
        onProgress: ((done: Int, total: Int) -> Unit)? = null,
    ) {
        val pdf = PdfDocument()
        try {
            val total = bitmaps.size
            bitmaps.forEachIndexed { index, bitmap ->
                val placement: ImagePagePlacement = ImageToPdfLayout.computePlacement(
                    imageWidthPx = bitmap.width,
                    imageHeightPx = bitmap.height,
                    config = config,
                )

                val pageInfo = PdfDocument.PageInfo.Builder(
                    placement.pageWidthPt.roundToInt(),
                    placement.pageHeightPt.roundToInt(),
                    index + 1,
                ).create()

                val page = pdf.startPage(pageInfo)
                val destRect = RectF(
                    placement.imageLeftPt,
                    placement.imageTopPt,
                    placement.imageLeftPt + placement.imageWidthPt,
                    placement.imageTopPt + placement.imageHeightPt,
                )
                val srcRect = Rect(0, 0, bitmap.width, bitmap.height)
                page.canvas.drawBitmap(bitmap, srcRect, destRect, paint)
                pdf.finishPage(page)

                onProgress?.invoke(index + 1, total)
            }
            pdf.writeTo(output)
        } finally {
            pdf.close()
        }
    }
}
