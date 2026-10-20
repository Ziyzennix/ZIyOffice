package org.ziyoffice.ooxml

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.zip.ZipInputStream

/**
 * A feature the source package contains but ZiyOffice cannot write back.
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
 * formulas, cell formatting, frozen rows, merged cells, column widths, row
 * heights, sheet order).
 */
object ContentAudit {

    /** Features a [DocxWriter] save removes, in the order the UI lists them. */
    fun docx(bytes: ByteArray): List<DroppedFeature> {
        val scan = scan(bytes) { name ->
            name == DOCUMENT_PART ||
                name == COMMENTS_PART || name == FOOTNOTES_PART || name == ENDNOTES_PART ||
                isHeaderOrFooterPart(name)
        }
        val body = scan.parts[DOCUMENT_PART]
        val features = mutableListOf<DroppedFeature>()

        fun part(name: String, label: String) {
            if (name in scan.names) features += DroppedFeature(label, 1)
        }
        // A save keeps a header only when the body references exactly one, and
        // that reference is the default variant: the writer emits a single
        // default header and footer. Counting parts alone is wrong — a document
        // with a first-page-only header has one part and still loses it.
        val headerTypes = body?.decodeToString()?.let { referenceTypes(it, "w:headerReference") }
            .orEmpty()
        val footerTypes = body?.decodeToString()?.let { referenceTypes(it, "w:footerReference") }
            .orEmpty()
        val headerParts = scan.names.count { isHeaderOrFooterPart(it) && it.contains("header") }
        val footerParts = scan.names.count { isHeaderOrFooterPart(it) && it.contains("footer") }

        fun isKept(types: List<String?>): Boolean =
            types.size == 1 && (types.single() == null || types.single() == "default")

        if (headerParts > 0 && !isKept(headerTypes)) {
            features += DroppedFeature("Headers", headerParts)
        }
        if (footerParts > 0 && !isKept(footerTypes)) {
            features += DroppedFeature("Footers", footerParts)
        }
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
        var hiddenRowsAndColumns = 0
        scan.parts.forEach { (name, part) ->
            if (!name.startsWith("xl/worksheets/") || !name.endsWith(".xml")) return@forEach
            val xml = part.decodeToString()
            listOf(
                "conditionalFormatting" to "Conditional formatting",
                "autoFilter" to "Auto filters",
                "sheetProtection" to "Sheet protection",
            ).forEach { (tag, label) ->
                val count = countTag(xml, tag)
                if (count > 0) sheetTags[label] = (sheetTags[label] ?: 0) + count
            }
            // A validation that is a plain list of values is kept by a save, so
            // only the rules the model cannot hold are worth a warning.
            val droppedValidations = countDroppedValidations(xml)
            if (droppedValidations > 0) {
                sheetTags["Data validation"] =
                    (sheetTags["Data validation"] ?: 0) + droppedValidations
            }
            // Hidden rows and columns are attributes, not elements; a save
            // rebuilds them as visible because the model has no hidden flag.
            hiddenRowsAndColumns += HIDDEN_ATTRIBUTE.findAll(xml).count()
        }
        // Stable, human order rather than map iteration order.
        listOf(
            "Conditional formatting",
            "Data validation",
            "Auto filters",
            "Sheet protection",
        ).forEach { label ->
            sheetTags[label]?.let { features += DroppedFeature(label, it) }
        }
        if (hiddenRowsAndColumns > 0) {
            features += DroppedFeature("Hidden rows and columns", hiddenRowsAndColumns)
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
     * The `w:type` of every `w:headerReference`/`w:footerReference` in [xml], in
     * document order. A reference with no type is the default variant and is
     * returned as null, matching how [DocxReader] reads it.
     */
    private fun referenceTypes(xml: String, name: String): List<String?> {
        val types = mutableListOf<String?>()
        val needle = "<$name"
        var from = 0
        while (true) {
            val at = xml.indexOf(needle, from)
            if (at < 0) return types
            val end = xml.indexOf('>', at).takeIf { it >= 0 } ?: return types
            val tag = xml.substring(at, end)
            val typeAt = tag.indexOf("w:type=\"")
            types += if (typeAt >= 0) {
                tag.substring(typeAt + 8, tag.indexOf('"', typeAt + 8))
            } else {
                null
            }
            from = end
        }
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

    /**
     * Validations a save would lose: everything that is not a plain list of
     * values, such as a rule that points at another range.
     */
    private fun countDroppedValidations(xml: String): Int {
        val total = countTag(xml, "dataValidation")
        if (total == 0) return 0
        val kept = PRESERVED_VALIDATION.findAll(xml).count()
        return (total - kept).coerceAtLeast(0)
    }

    /** `hidden="1"`/`hidden="true"` on a `<row>` or `<col>`. */
    private val HIDDEN_ATTRIBUTE =
        Regex("""\bhidden\s*=\s*["'](?:1|true)["']""", RegexOption.IGNORE_CASE)

    /** A validation that is a plain list of values, which a save keeps. */
    private val PRESERVED_VALIDATION = Regex(
        """<dataValidation\b[^>]*\btype\s*=\s*"list"[^>]*>\s*<formula1>"[^"]*"</formula1>\s*</dataValidation>""",
    )

    private const val DOCUMENT_PART = "word/document.xml"
    private const val COMMENTS_PART = "word/comments.xml"
    private const val FOOTNOTES_PART = "word/footnotes.xml"
    private const val ENDNOTES_PART = "word/endnotes.xml"
    private const val MAX_PART_BYTES = 64 * 1024 * 1024
    private const val MAX_TOTAL_BYTES = 128L * 1024 * 1024
}
