package org.ziyoffice.ui

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/**
 * Decodes document images, downsampled to a sane display size so a 12-megapixel
 * photo never allocates its full bitmap in memory. Called from a background
 * dispatcher; returns null for formats the platform cannot decode (EMF, WMF, SVG).
 */
fun decodeImage(bytes: ByteArray, maxDimension: Int = 2048): ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (bounds.outWidth / sample > maxDimension || bounds.outHeight / sample > maxDimension) {
        sample *= 2
    }
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null
    return bitmap.asImageBitmap()
}
