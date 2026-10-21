package org.ziyoffice.pdf

import java.io.ByteArrayOutputStream
import java.math.BigDecimal

/**
 * Writes objects back out as the bytes a file holds them in.
 *
 * The writing side of the format: the same vocabulary the reader reads, in
 * the same order, with the same spellings. A file that is read and written
 * without being changed should come out the same size and mean the same
 * thing.
 */
object PdfSerializer {

    /** The bytes of [value] as a file would write them. */
    fun write(value: PdfObject): ByteArray {
        val out = ByteArrayOutputStream()
        append(out, value)
        return out.toByteArray()
    }

    private fun append(out: ByteArrayOutputStream, value: PdfObject) {
        when (value) {
            is PdfNull -> out.write("null".toByteArray(Charsets.ISO_8859_1))
            is PdfBool -> out.write(value.value.toString().toByteArray(Charsets.ISO_8859_1))
            is PdfInteger -> out.write(value.value.toString().toByteArray(Charsets.ISO_8859_1))
            is PdfReal -> out.write(formatReal(value.value).toByteArray(Charsets.ISO_8859_1))
            is PdfName -> out.write(value.toPdfName().toByteArray(Charsets.ISO_8859_1))
            is PdfString -> appendString(out, value)
            is PdfRef -> out.write(
                "${value.number} ${value.generation} R".toByteArray(Charsets.ISO_8859_1),
            )
            is PdfArray -> {
                out.write('['.code)
                value.items.forEachIndexed { index, item ->
                    if (index > 0) out.write(' '.code)
                    append(out, item)
                }
                out.write(']'.code)
            }
            is PdfDict -> {
                out.write("<<".toByteArray(Charsets.ISO_8859_1))
                value.entries.forEach { (key, entry) ->
                    out.write(' '.code)
                    out.write(PdfName(key).toPdfName().toByteArray(Charsets.ISO_8859_1))
                    out.write(' '.code)
                    append(out, entry)
                }
                out.write(">>".toByteArray(Charsets.ISO_8859_1))
            }
            is PdfStream -> {
                append(out, value.dict)
                out.write("\nstream\n".toByteArray(Charsets.ISO_8859_1))
                out.write(value.raw)
                out.write("\nendstream".toByteArray(Charsets.ISO_8859_1))
            }
        }
    }

    /**
     * A string, as a file would write it.
     *
     * A string whose bytes are not plain characters is written as a hex
     * string, because a literal string cannot hold them cleanly.
     */
    private fun appendString(out: ByteArrayOutputStream, value: PdfString) {
        val bytes = value.bytes
        val needsHex = bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()
        if (needsHex) {
            out.write('<'.code)
            bytes.forEach { byte ->
                val code = byte.toInt() and 0xFF
                out.write(HEX[code shr 4].code)
                out.write(HEX[code and 0x0F].code)
            }
            out.write('>'.code)
            return
        }
        out.write('('.code)
        bytes.forEach { byte ->
            val code = byte.toInt() and 0xFF
            when {
                code == '\\'.code -> out.write("\\\\".toByteArray(Charsets.ISO_8859_1))
                code == '('.code -> out.write("\\(".toByteArray(Charsets.ISO_8859_1))
                code == ')'.code -> out.write("\\)".toByteArray(Charsets.ISO_8859_1))
                code == '\r'.code -> out.write("\\r".toByteArray(Charsets.ISO_8859_1))
                code == '\n'.code -> out.write("\\n".toByteArray(Charsets.ISO_8859_1))
                code == '\t'.code -> out.write("\\t".toByteArray(Charsets.ISO_8859_1))
                code < 0x20 || code > 0x7E -> {
                    out.write('\\'.code)
                    out.write(HEX[code shr 4].code)
                    out.write(HEX[code and 0x0F].code)
                }
                else -> out.write(code)
            }
        }
        out.write(')'.code)
    }

    /**
     * A real number, in the way a file writes one.
     *
     * A file has no way to write an exponent, so a number that would need
     * one is written out in full.
     */
    fun formatReal(value: Double): String {
        if (value == value.toLong().toDouble() && kotlin.math.abs(value) < 1e15) {
            return value.toLong().toString()
        }
        val text = value.toString()
        if (text.contains('E') || text.contains('e')) {
            return BigDecimal(value).stripTrailingZeros().toPlainString()
        }
        return text
    }

    private val HEX = "0123456789ABCDEF".toCharArray()
}
