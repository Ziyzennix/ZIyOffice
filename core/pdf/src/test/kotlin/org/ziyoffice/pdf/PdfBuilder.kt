package org.ziyoffice.pdf

import java.io.ByteArrayOutputStream
import java.util.Locale

/**
 * Builds PDF files for the tests.
 *
 * The files are written by hand rather than produced by a library, so a test
 * can hold a file down to the byte: a cross-reference with a wrong offset, a
 * field with no appearance, an object stream holding the catalogue.
 */
internal class PdfBuilder {

    private val bodies = mutableListOf<String>()

    /** Adds an object and returns its number. */
    fun add(body: String): Int {
        bodies += body
        return bodies.size
    }

    /** The file, with a classic cross-reference table. */
    fun build(root: Int, trailerEntries: String = "", version: String = "1.7"): ByteArray {
        val out = ByteArrayOutputStream()
        out.write("%PDF-$version\n".toByteArray(Charsets.ISO_8859_1))
        // A binary comment marks the file as binary, which is what a real
        // writer does and what a reader has to cope with.
        out.write("%\u00E2\u00E3\u00CF\u00D3\n".toByteArray(Charsets.ISO_8859_1))

        val offsets = IntArray(bodies.size + 1)
        bodies.forEachIndexed { index, body ->
            offsets[index + 1] = out.size()
            out.write("${index + 1} 0 obj\n".toByteArray(Charsets.ISO_8859_1))
            out.write(body.toByteArray(Charsets.ISO_8859_1))
            out.write("\nendobj\n".toByteArray(Charsets.ISO_8859_1))
        }

        val xrefOffset = out.size()
        out.write("xref\n".toByteArray(Charsets.ISO_8859_1))
        out.write("0 ${bodies.size + 1}\n".toByteArray(Charsets.ISO_8859_1))
        out.write("0000000000 65535 f \n".toByteArray(Charsets.ISO_8859_1))
        for (number in 1..bodies.size) {
            out.write(String.format(Locale.US, "%010d 00000 n \n", offsets[number]).toByteArray(Charsets.ISO_8859_1))
        }
        out.write("trailer\n".toByteArray(Charsets.ISO_8859_1))
        out.write(
            "<< /Size ${bodies.size + 1} /Root $root 0 R $trailerEntries >>\n"
                .toByteArray(Charsets.ISO_8859_1),
        )
        out.write("startxref\n$xrefOffset\n%%EOF\n".toByteArray(Charsets.ISO_8859_1))
        return out.toByteArray()
    }

    /**
     * The file, with the catalogue and the page tree inside an object stream
     * and a cross-reference stream pointing at it.
     *
     * This is the shape a modern writer uses. The two bodies go into the
     * stream, so their object numbers are not the caller's to choose; ask
     * for them with [packedCatalogNumber] and [packedPagesNumber].
     */
    fun buildWithObjectStream(catalogBody: String, pagesBody: String): ByteArray {
        val out = ByteArrayOutputStream()
        out.write("%PDF-1.5\n".toByteArray(Charsets.ISO_8859_1))

        // The objects that go inside the object stream.
        val packed = mutableListOf<Pair<Int, String>>()
        packed += packedCatalogNumber to catalogBody
        packed += packedPagesNumber to pagesBody

        val header = StringBuilder()
        val body = StringBuilder()
        packed.forEach { (number, content) ->
            header.append("$number ${body.length} ")
            body.append(content).append(' ')
        }
        val payload = header.toString() + body.toString()
        val compressed = deflate(payload.toByteArray(Charsets.ISO_8859_1))

        val objStmNumber = bodies.size + 1
        val offsets = IntArray(bodies.size + 2)
        bodies.forEachIndexed { index, content ->
            offsets[index + 1] = out.size()
            out.write("${index + 1} 0 obj\n".toByteArray(Charsets.ISO_8859_1))
            out.write(content.toByteArray(Charsets.ISO_8859_1))
            out.write("\nendobj\n".toByteArray(Charsets.ISO_8859_1))
        }
        val objStmOffset = out.size()
        out.write("$objStmNumber 0 obj\n".toByteArray(Charsets.ISO_8859_1))
        out.write(
            (
                "<< /Type /ObjStm /N ${packed.size} /First ${header.length} " +
                    "/Filter /FlateDecode /Length ${compressed.size} >>\n"
                ).toByteArray(Charsets.ISO_8859_1),
        )
        out.write("stream\n".toByteArray(Charsets.ISO_8859_1))
        out.write(compressed)
        out.write("\nendstream\nendobj\n".toByteArray(Charsets.ISO_8859_1))

        // The cross-reference stream. Its own offset is known before it is
        // written, because it starts where the object stream ended.
        val xrefNumber = bodies.size + 2
        val xrefOffset = out.size()
        val rows = ArrayList<ByteArray>()
        fun freeRow() = byteArrayOf(0, 0, 0, 0, 0, 0, 0xFF.toByte(), 0xFF.toByte())
        fun inFileRow(offset: Int) = byteArrayOf(
            1,
            (offset shr 24).toByte(), (offset shr 16).toByte(),
            (offset shr 8).toByte(), offset.toByte(),
            0, 0,
            0, 0,
        )
        fun inStreamRow(number: Int) = byteArrayOf(
            2,
            0, 0, 0, 0,
            (number shr 8).toByte(), number.toByte(),
            0, 0,
        )
        rows += freeRow()
        for (number in 1..bodies.size) rows += inFileRow(offsets[number])
        rows += inFileRow(objStmOffset)
        rows += inFileRow(xrefOffset)
        rows += inStreamRow(packedCatalogNumber)
        rows += inStreamRow(packedPagesNumber)

        val table = ByteArrayOutputStream()
        rows.forEach { table.write(it) }
        val compressedTable = deflate(table.toByteArray())
        out.write("$xrefNumber 0 obj\n".toByteArray(Charsets.ISO_8859_1))
        out.write(
            (
                "<< /Type /XRef /Size ${rows.size} /W [1 4 2] /Root $packedCatalogNumber 0 R " +
                    "/Filter /FlateDecode /Length ${compressedTable.size} >>\n"
                ).toByteArray(Charsets.ISO_8859_1),
        )
        out.write("stream\n".toByteArray(Charsets.ISO_8859_1))
        out.write(compressedTable)
        out.write("\nendstream\nendobj\n".toByteArray(Charsets.ISO_8859_1))
        out.write("startxref\n$xrefOffset\n%%EOF\n".toByteArray(Charsets.ISO_8859_1))
        return out.toByteArray()
    }

    /** The number the catalogue gets when it is packed into an object stream. */
    val packedCatalogNumber: Int get() = bodies.size + 3

    /** The number the page tree gets when it is packed into an object stream. */
    val packedPagesNumber: Int get() = bodies.size + 4

    /** The file with its cross-reference offsets broken. */
    fun buildWithBrokenXref(root: Int): ByteArray {
        val bytes = build(root)
        val text = String(bytes, Charsets.ISO_8859_1)
        // Every offset in the table points at the header instead of an object.
        val broken = text.replace(Regex("""\n\d{10} 00000 n """), "\n0000000000 00000 n ")
        return broken.toByteArray(Charsets.ISO_8859_1)
    }
}

/** Deflates [data], the way a PDF stream with a Flate filter holds it. */
internal fun deflate(data: ByteArray): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val deflater = java.util.zip.Deflater()
    try {
        deflater.setInput(data)
        deflater.finish()
        val buffer = ByteArray(16 * 1024)
        while (!deflater.finished()) {
            val count = deflater.deflate(buffer)
            out.write(buffer, 0, count)
        }
    } finally {
        deflater.end()
    }
    return out.toByteArray()
}
