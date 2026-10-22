package org.ziyoffice.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reading the words out of a picture.
 *
 * The engine and its English model travel inside the app, so this works with
 * the radio off and never asks anyone for anything. That is the whole point:
 * the sentence "a scanned document becomes text" should not come with a
 * condition about the network.
 *
 * The engine reads its model from a file path, so the model is copied out of
 * the assets the first time it is used. It is 4 MB, and it stays in the app's
 * own storage.
 */
object PictureText {

    /** The language the model reads. */
    private const val LANGUAGE = "eng"

    /** The folder under the app's files that holds the model. */
    private const val FOLDER = "ocr"

    /** The name of the model in the assets. */
    private const val MODEL = "tessdata/$LANGUAGE.traineddata"

    /**
     * The text in [bitmap].
     *
     * Lines come back the way the engine found them. An empty string means it
     * found no words, which happens on a photograph of a wall.
     */
    suspend fun read(context: Context, bitmap: Bitmap): String = withContext(Dispatchers.Default) {
        val path = prepare(context) ?: return@withContext ""
        val engine = TessBaseAPI()
        try {
            if (!engine.init(path, LANGUAGE)) return@withContext ""
            // A page of text, or a sign, or a receipt: let the engine work out
            // how the text is laid out rather than assuming one block.
            engine.setPageSegMode(TessBaseAPI.PageSegMode.PSM_AUTO)
            engine.setImage(upright(bitmap))
            engine.getUTF8Text().orEmpty().trim()
        } finally {
            engine.end()
        }
    }

    /**
     * Reads the picture's bytes into a bitmap.
     *
     * The engine wants a bitmap it can read pixel by pixel, and a photograph
     * straight off a phone is 12 megapixels of it. Downsampling first keeps
     * the read inside a sane amount of memory.
     */
    fun bitmapOf(bytes: ByteArray): Bitmap? = with(BitmapFactory.Options()) {
        inJustDecodeBounds = true
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, this)
        if (outWidth <= 0 || outHeight <= 0) return null
        var sample = 1
        while (outWidth / sample > 2600 || outHeight / sample > 2600) sample *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }

    /**
     * The picture, made easier to read.
     *
     * Small text is the usual reason a page comes back empty, so a picture
     * narrower than a thousand pixels is enlarged. The engine works on one
     * pixel format, so anything else is converted.
     */
    private fun upright(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) return bitmap
        val scale = if (width < MIN_WIDTH) MIN_WIDTH.toFloat() / width else 1f
        val target = if (scale > 1f) {
            Bitmap.createScaledBitmap(
                bitmap,
                (width * scale).toInt(),
                (height * scale).toInt(),
                true,
            )
        } else {
            bitmap
        }
        if (target.config == Bitmap.Config.ARGB_8888) return target
        val converted = target.copy(Bitmap.Config.ARGB_8888, false)
        return converted ?: target
    }

    /** The folder the engine reads its model from, copied out of the assets once. */
    private fun prepare(context: Context): String? {
        val root = File(context.filesDir, FOLDER)
        val model = File(root, MODEL)
        val wanted = runCatching {
            context.assets.open(MODEL).use { it.available().toLong() }
        }.getOrNull() ?: return null
        if (model.isFile && model.length() == wanted) return root.absolutePath
        model.parentFile?.mkdirs()
        runCatching {
            context.assets.open(MODEL).use { input ->
                model.outputStream().use { output -> input.copyTo(output) }
            }
        }.onFailure { return null }
        return root.absolutePath
    }

    /** Below this width the text is too small for the engine to find. */
    private const val MIN_WIDTH = 1200
}
