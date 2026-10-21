package org.ziyoffice.pdf

import java.io.ByteArrayOutputStream
import java.util.zip.DataFormatException
import java.util.zip.Inflater

/**
 * Undoes the filters a stream's bytes are behind.
 *
 * A PDF may compress its content, its images and even its font files, and it
 * says which way in the stream's `/Filter` entry. The filters here are the
 * ones that turn up in the files an office app writes and reads: Flate, the
 * two text encodings, and the run length code.
 *
 * A filter that is not here is not an error. The bytes come back as they
 * are, and the caller finds out it cannot use them.
 */
object PdfFilters {

    /**
     * The content of a stream, with its filters undone.
     *
     * The filters are applied in order, which is the order the `/Filter`
     * array lists them in.
     */
    fun decode(dict: PdfDict, raw: ByteArray): ByteArray {
        var data = raw
        val filters = filterNames(dict)
        filters.forEachIndexed { index, filter ->
            data = applyFilter(filter, data, decodeParms(dict, index))
        }
        return data
    }

    /** The names of the filters a stream is behind, in order. */
    fun filterNames(dict: PdfDict): List<String> = when (val filter = dict["Filter"]) {
        is PdfName -> listOf(filter.value)
        is PdfArray -> filter.items.filterIsInstance<PdfName>().map { it.value }
        else -> emptyList()
    }

    /** The parameters of the filter at [index], when there are any. */
    private fun decodeParms(dict: PdfDict, index: Int): PdfDict? {
        val parms = when (val value = dict["DecodeParms"]) {
            is PdfDict -> listOf(value)
            is PdfArray -> value.items.filterIsInstance<PdfDict>()
            else -> emptyList()
        }
        return parms.getOrNull(index)
    }

    /** One filter applied to [data]. */
    private fun applyFilter(name: String, data: ByteArray, parms: PdfDict?): ByteArray = when (name) {
        "FlateDecode", "Fl" -> flate(data, parms)
        "ASCIIHexDecode", "AHx" -> asciiHex(data)
        "ASCII85Decode", "A85" -> ascii85(data)
        "RunLengthDecode", "RL" -> runLength(data)
        // DCTDecode (JPEG), JPXDecode, CCITTFaxDecode, JBIG2Decode and
        // Crypt are not undone here: they hold images, and the platform
        // decodes those itself.
        else -> data
    }

    /**
     * The Flate filter.
     *
     * Some writers leave the two-byte zlib header off, so a plain inflate
     * that fails is tried again as raw deflate.
     */
    private fun flate(data: ByteArray, parms: PdfDict?): ByteArray {
        val inflated = inflate(data, false) ?: inflate(data, true) ?: return data
        val predictor = parms?.integer("Predictor")?.toInt() ?: 1
        if (predictor <= 1) return inflated
        val colors = parms?.integer("Colors")?.toInt() ?: 1
        val bpc = parms?.integer("BitsPerComponent")?.toInt() ?: 8
        val columns = parms?.integer("Columns")?.toInt() ?: 1
        return applyPredictor(inflated, predictor, colors, bpc, columns)
    }

    private fun inflate(data: ByteArray, raw: Boolean): ByteArray? {
        if (data.isEmpty()) return ByteArray(0)
        val inflater = Inflater(raw)
        inflater.setInput(data)
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        try {
            while (!inflater.finished()) {
                val count = inflater.inflate(buffer)
                if (count == 0) {
                    if (inflater.needsInput() || inflater.needsDictionary()) break
                }
                out.write(buffer, 0, count)
            }
        } catch (_: DataFormatException) {
            return null
        } finally {
            inflater.end()
        }
        return out.toByteArray()
    }

    /**
     * Undoes a predictor.
     *
     * The PNG predictors are the ones a filter chain uses before Flate, and
     * they exist so that a row of similar bytes compresses well. TIFF
     * predictor 2 is a running difference.
     */
    private fun applyPredictor(
        data: ByteArray,
        predictor: Int,
        colors: Int,
        bpc: Int,
        columns: Int,
    ): ByteArray {
        val bpp = ((colors * bpc + 7) / 8).coerceAtLeast(1)
        val rowLength = ((colors * bpc * columns) + 7) / 8
        if (rowLength <= 0) return data
        return when (predictor) {
            2 -> tiffPredictor(data, colors, bpc, columns)
            else -> pngPredictor(data, bpp, rowLength)
        }
    }

    private fun pngPredictor(data: ByteArray, bpp: Int, rowLength: Int): ByteArray {
        val rows = data.size / (rowLength + 1)
        if (rows == 0) return data
        val out = ByteArray(rows * rowLength)
        var previous = ByteArray(rowLength)
        var from = 0
        var to = 0
        repeat(rows) {
            val type = data[from].toInt().and(0xFF)
            from++
            val row = data.copyOfRange(from, from + rowLength)
            from += rowLength
            val decoded = ByteArray(rowLength)
            for (i in 0 until rowLength) {
                val raw = row[i].toInt().and(0xFF)
                val left = if (i >= bpp) decoded[i - bpp].toInt().and(0xFF) else 0
                val up = previous[i].toInt().and(0xFF)
                val upLeft = if (i >= bpp) previous[i - bpp].toInt().and(0xFF) else 0
                val value = when (type) {
                    0 -> raw
                    1 -> raw + left
                    2 -> raw + up
                    3 -> raw + ((left + up) / 2)
                    4 -> raw + paeth(left, up, upLeft)
                    else -> raw
                }
                decoded[i] = value.toByte()
            }
            decoded.copyInto(out, to)
            to += rowLength
            previous = decoded
        }
        return out
    }

    private fun paeth(left: Int, up: Int, upLeft: Int): Int {
        val p = left + up - upLeft
        val dLeft = kotlin.math.abs(p - left)
        val dUp = kotlin.math.abs(p - up)
        val dUpLeft = kotlin.math.abs(p - upLeft)
        return when {
            dLeft <= dUp && dLeft <= dUpLeft -> left
            dUp <= dUpLeft -> up
            else -> upLeft
        }
    }

    private fun tiffPredictor(
        data: ByteArray,
        colors: Int,
        bpc: Int,
        columns: Int,
    ): ByteArray {
        if (bpc != 8) return data
        val rowLength = colors * columns
        if (rowLength <= 0) return data
        val out = ByteArray(data.size)
        var from = 0
        var to = 0
        while (from + rowLength <= data.size) {
            for (i in 0 until rowLength) {
                val raw = data[from + i].toInt().and(0xFF)
                val left = if (i > 0) out[to + i - 1].toInt().and(0xFF) else 0
                out[to + i] = (raw + left).toByte()
            }
            from += rowLength
            to += rowLength
        }
        return out
    }

    /** The ASCII hex filter: two hex digits per byte, `>` ends it. */
    private fun asciiHex(data: ByteArray): ByteArray {
        val digits = StringBuilder()
        for (byte in data) {
            val c = byte.toInt().and(0xFF)
            if (c == '>'.code) break
            if (c in '0'.code..'9'.code || c in 'a'.code..'f'.code || c in 'A'.code..'F'.code) {
                digits.append(c.toChar())
            }
        }
        val text = digits.toString()
        val padded = if (text.length % 2 == 1) text + "0" else text
        val out = ByteArray(padded.length / 2)
        for (i in out.indices) {
            out[i] = ((Character.digit(padded[i * 2], 16) shl 4) or
                Character.digit(padded[i * 2 + 1], 16)).toByte()
        }
        return out
    }

    /** The ASCII85 filter, as PostScript wrote it. */
    private fun ascii85(data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        var group = IntArray(5)
        var count = 0
        var index = 0
        while (index < data.size) {
            val c = data[index].toInt().and(0xFF)
            index++
            if (c == '~'.code) break
            if (c == 32 || c == 10 || c == 13 || c == 9 || c == 12) continue
            if (c == 'z'.code && count == 0) {
                out.write(0); out.write(0); out.write(0); out.write(0)
                continue
            }
            if (c < '!'.code || c > 'u'.code) continue
            group[count++] = c - '!'.code
            if (count == 5) {
                var value = 0L
                for (i in 0 until 5) value = value * 85 + group[i]
                out.write((value shr 24).toInt())
                out.write((value shr 16).toInt())
                out.write((value shr 8).toInt())
                out.write(value.toInt())
                count = 0
            }
        }
        if (count > 0) {
            for (i in count until 5) group[i] = 84
            var value = 0L
            for (i in 0 until 5) value = value * 85 + group[i]
            for (i in 0 until count - 1) out.write((value shr (24 - 8 * i)).toInt())
        }
        return out.toByteArray()
    }

    /** The run length filter: a length byte, then that many bytes, or a repeat. */
    private fun runLength(data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        var index = 0
        while (index < data.size) {
            val length = data[index].toInt().and(0xFF)
            index++
            when {
                length == 128 -> return out.toByteArray()
                length < 128 -> {
                    val count = length + 1
                    for (i in 0 until count) {
                        if (index >= data.size) break
                        out.write(data[index].toInt())
                        index++
                    }
                }
                else -> {
                    if (index >= data.size) break
                    val byte = data[index]
                    index++
                    repeat(257 - length) { out.write(byte.toInt()) }
                }
            }
        }
        return out.toByteArray()
    }
}
