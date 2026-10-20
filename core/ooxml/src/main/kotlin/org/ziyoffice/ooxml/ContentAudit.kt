package org.ziyoffice.ooxml

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.zip.ZipInputStream

/**
 * A feature the source package contains but ZIyOffice cannot write back.
 * Saving through [DocxWriter] or [XlsxWriter] removes it, so the user is told
 * before the file changes rather than after.
 */
data class DroppedFeature(
    /** What the user recognises, e.g. "Comments". */
    val label: String,
    /** How many were found; the smallest honest count, never an estimate. */
    val count: Int,
)

/**
 * Reads a package and reports what a rebuild-from-model save would destroy.
 *
 * [DocxReader] and [XlsxReader] deliberately model a documented subset and skip
 * everything else. That is the right reader behaviour — faking unsupported
 * content would be worse — but the writers emit only the model, so a skipped
 * part or tag disappears from the user's file. This audit is the missing half:
 * the editor asks it once per open and warns before the first save.
 *
 * The scan is intentionally conservative. It reports only content whose loss
 * changes what the document shows or contains, and it never reports a feature
 * the writers do reproduce (text, run formatting, lists, tables, images,
 * formulas, cell formatting, frozen rows, sheet order).
 */
object ContentAudit {

    /** Features a [DocxWriter] save removes, in the order the UI lists them. */
    fun docx(bytes: ByteArray): List<DroppedFeature> {
        val scan = scan(bytes) { name ->
            name == DOCUMENT_PART ||
                name == COMMENTS_PART || name == FOOTNOTES_PART || name == ENDNOTES_PART ||
                (name.startsWith("word/header") && name.endsWith(".xml") && !name.contains("_rels/")) ||
                (name.startsWith("word/footer") && name.endsWith(".xml") && !name.contains("_rels/"))
        }
        val body = scan.parts[DOCUMENT_PART]
        val features = mutableListOf<DroppedFeature>()

        fun part(name: String, label: String) {
            if (name in scan.names) features += DroppedFeature(label, 1)
        }
        fun headers() = scan.names.count {
            it.startsWith("word/header") && it.endsWith(".xml") && !it.contains("_rels/")
        }
        fun footers() = scan.names.count {
            it.startsWith("word/footer") && it.endsWith(".xml") && !it.contains("_rels/")
        }
        headers().takeIf { it > 0 }?.let { features += DroppedFeature("Headers", it) }
        footers().takeIf { it > 0 }?.let { features += DroppedFeature("Footers", it) }
        part(COMMENTS_PART, "Comments")
        part(FOOTNOTES_PART, "Footnotes")
        part(ENDNOTES_PART, "Endnotes")

        if (body != null) {
            val xml = body.decodeToString()
            fun tags(vararg names: String): Int = names.sumOf { countTag(xml, it) }
            tags("w:ins", "w:del").takeIf { it > 0 }?.let {
                features += DroppedFeature("Tracked changes", it)
            }
            tags("w:hyperlink").takeIf { it > 0 }?.let {
                features += DroppedFeature("Hyperlinks", it)
            }
            tags("w:vMerge").takeIf { it > 0 }?.let {
                features += DroppedFeature("Vertically merged cells", it)
            }
            tags("w:txbxContent").takeIf { it > 0 }?.let {
                features += DroppedFeature("Text boxes", it)
            }
            tags("m:oMath", "m:oMathPara").takeIf { it > 0 }?.let {
                features += DroppedFeature("Equations", it)
            }
            // The body always ends with one sectPr; more than one means real
            // section breaks (different page size, margins or column layout).
            (tags("w:sectPr") - 1).takeIf { it > 0 }?.let {
                features += DroppedFeature("Section breaks", it)
            }
            tags("w:cols").takeIf { it > 0 }?.let {
                features += DroppedFeature("Multi-column layout", it)
            }
        }
        return features
    }

    /** Features an [XlsxWriter] save removes, in the order the UI lists them. */
    fun xlsx(bytes: ByteArray): List<DroppedFeature> {
        val scan = scan(bytes) { name ->
            (name.startsWith("xl/worksheets/") && name.endsWith(".xml")) ||
                name.startsWith("xl/charts/") || name.startsWith("xl/drawings/") ||
                name.startsWith("xl/media/") || name.startsWith("xl/pivotTables/") ||
                name.startsWith("xl/pivotCache/") || name.startsWith("xl/comments")
        }
        val features = mutableListOf<DroppedFeature>()

        fun parts(prefix: String, label: String) {
            val count = scan.names.count { it.startsWith(prefix) }
            if (count > 0) features += DroppedFeature(label, count)
        }
        parts("xl/charts/", "Charts")
        parts("xl/drawings/", "Drawings")
        parts("xl/media/", "Images")
        parts("xl/pivotTables/", "Pivot tables")
        parts("xl/pivotCache/", "Pivot caches")
        parts("xl/comments", "Comments")

        // Worksheet-level tags: count across every sheet part in the package.
        val sheetTags = mutableMapOf<String, Int>()
        scan.parts.forEach { (name, part) ->
            if (!name.startsWith("xl/worksheets/") || !name.endsWith(".xml")) return@forEach
            val xml = part.decodeToString()
            listOf(
                "conditionalFormatting" to "Conditional formatting",
                "dataValidation" to "Data validation",
                "mergeCell" to "Merged cells",
                "autoFilter" to "Auto filters",
                "sheetProtection" to "Sheet protection",
            ).forEach { (tag, label) ->
                val count = countTag(xml, tag)
                if (count > 0) sheetTags[label] = (sheetTags[label] ?: 0) + count
            }
        }
        // Stable, human order rather than map iteration order.
        listOf(
            "Conditional formatting",
            "Data validation",
            "Merged cells",
            "Auto filters",
            "Sheet protection",
        ).forEach { label ->
            sheetTags[label]?.let { features += DroppedFeature(label, it) }
        }
        return features
    }

    /* ------------------------------ scanning ------------------------------ */

    private class Scan(val names: List<String>, val parts: Map<String, ByteArray>)

    /**
     * Entry names plus the bytes of entries [wanted] selects. Anything else is
     * skipped without being read, so auditing a large workbook stays cheap.
     */
    private fun scan(bytes: ByteArray, wanted: (String) -> Boolean): Scan {
        val names = mutableListOf<String>()
        val parts = mutableMapOf<String, ByteArray>()
        var total = 0L
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                names += entry.name
                if (wanted(entry.name)) {
                    val part = zip.readCapped()
                    total += part.size
                    if (total > MAX_TOTAL_BYTES) {
                        throw IOException(
                            "Package exceeds the ${MAX_TOTAL_BYTES / (1024 * 1024)} MB audit limit",
                        )
                    }
                    parts[entry.name] = part
                }
            }
        }
        return Scan(names, parts)
    }

    private fun ZipInputStream.readCapped(): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            if (out.size() + read > MAX_PART_BYTES) {
                throw IOException("Part exceeds the ${MAX_PART_BYTES / (1024 * 1024)} MB audit limit")
            }
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    /**
     * Occurrences of element [name] in [xml]. Matches `<name>` and `<name ...`,
     * so `<w:delText>` does not count as a `<w:del>` and `<m:oMathPara>` is not
     * mistaken for a second `<m:oMath>` — callers pass both when both matter.
     */
    private fun countTag(xml: String, name: String): Int {
        val needle = "<$name"
        var count = 0
        var from = 0
        while (true) {
            val at = xml.indexOf(needle, from)
            if (at < 0) return count
            val next = xml.getOrNull(at + needle.length)
            if (next == '>' || next == ' ' || next == '/' || next == '\t' || next == '\n') count++
            from = at + needle.length
        }
    }

    private const val DOCUMENT_PART = "word/document.xml"
    private const val COMMENTS_PART = "word/comments.xml"
    private const val FOOTNOTES_PART = "word/footnotes.xml"
    private const val ENDNOTES_PART = "word/endnotes.xml"
    private const val MAX_PART_BYTES = 64 * 1024 * 1024
    private const val MAX_TOTAL_BYTES = 128L * 1024 * 1024
}
