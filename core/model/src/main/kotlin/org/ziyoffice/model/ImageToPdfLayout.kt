package org.ziyoffice.model

import kotlin.math.max
import kotlin.math.min

/** Standard page sizes in points (72 points per inch). */
enum class PdfPageSize(val widthPt: Float, val heightPt: Float) {
    A4(595f, 842f),
    LETTER(612f, 792f),
    FIT_IMAGE(0f, 0f),
}

/** Orientation choices for placing an image onto a PDF page. */
enum class PdfPageOrientation {
    AUTO,
    PORTRAIT,
    LANDSCAPE,
}

/** Configuration for converting images into PDF pages. */
data class ImageToPdfConfig(
    val pageSize: PdfPageSize = PdfPageSize.A4,
    val orientation: PdfPageOrientation = PdfPageOrientation.AUTO,
    val marginPt: Float = 36f,
)

/** The calculated position and dimensions for one image on a PDF page. */
data class ImagePagePlacement(
    val pageWidthPt: Float,
    val pageHeightPt: Float,
    val imageLeftPt: Float,
    val imageTopPt: Float,
    val imageWidthPt: Float,
    val imageHeightPt: Float,
)

/**
 * Calculates page dimensions and image bounds for compiling pictures into a PDF.
 */
object ImageToPdfLayout {

    fun computePlacement(
        imageWidthPx: Int,
        imageHeightPx: Int,
        config: ImageToPdfConfig = ImageToPdfConfig(),
    ): ImagePagePlacement {
        if (imageWidthPx <= 0 || imageHeightPx <= 0) {
            val w = min(config.pageSize.widthPt, config.pageSize.heightPt).coerceAtLeast(595f)
            val h = max(config.pageSize.widthPt, config.pageSize.heightPt).coerceAtLeast(842f)
            return ImagePagePlacement(w, h, 0f, 0f, 0f, 0f)
        }

        if (config.pageSize == PdfPageSize.FIT_IMAGE) {
            val w = imageWidthPx.toFloat()
            val h = imageHeightPx.toFloat()
            return ImagePagePlacement(w, h, 0f, 0f, w, h)
        }

        val baseWidth = min(config.pageSize.widthPt, config.pageSize.heightPt)
        val baseHeight = max(config.pageSize.widthPt, config.pageSize.heightPt)

        val isLandscape = when (config.orientation) {
            PdfPageOrientation.AUTO -> imageWidthPx > imageHeightPx
            PdfPageOrientation.LANDSCAPE -> true
            PdfPageOrientation.PORTRAIT -> false
        }

        val pageWidth = if (isLandscape) baseHeight else baseWidth
        val pageHeight = if (isLandscape) baseWidth else baseHeight

        val margin = max(0f, config.marginPt)
        val usableWidth = max(1f, pageWidth - 2 * margin)
        val usableHeight = max(1f, pageHeight - 2 * margin)

        val scale = min(usableWidth / imageWidthPx, usableHeight / imageHeightPx)
        val targetWidth = imageWidthPx * scale
        val targetHeight = imageHeightPx * scale

        val left = margin + (usableWidth - targetWidth) / 2f
        val top = margin + (usableHeight - targetHeight) / 2f

        return ImagePagePlacement(
            pageWidthPt = pageWidth,
            pageHeightPt = pageHeight,
            imageLeftPt = left,
            imageTopPt = top,
            imageWidthPt = targetWidth,
            imageHeightPt = targetHeight,
        )
    }
}
