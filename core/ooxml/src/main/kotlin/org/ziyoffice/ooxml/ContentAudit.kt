package org.ziyoffice.ooxml

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.zip.ZipInputStream

/**
 * A feature the source package contains that ZiyOffice keeps but cannot edit.
 *
 * Saving a document writes the edited parts back into the original package,
 * so nothing here is lost. It is reported because the user should know what
 * they will not be able to change, and how much of the document is out of
 * reach — not to warn about damage.
 */
data class PreservedFeature(
    /** What the user recognises, e.g. "Comments". */
    val label: String,
    /** How many were found; the smallest honest count, never an estimate. */
    val count: Int,
)

/**
 * Reads a package and reports what it holds that ZiyOffice cannot edit.
 *
 * The readers model a documented subset and skip everything else. That is the
 * right reader behaviour — faking unsupported content would be worse — but it
 * means the editor cannot place the caret in some of it. The writers keep
 * those parts untouched, so the file stays complete; this audit says which
 * parts of it are read-only.
 *
 * The scan is intentionally conservative. It reports only content the user
 * would expect to be able to change, and it never reports a feature the
 * editor does handle.
 */
object ContentAudit {

    /** Features a document keeps but cannot edit, in the order the UI lists them. */
    fun docx(bytes: ByteArray): List<PreservedFeature> {
        val scan = scan(bytes) { name ->
            name == DOCUMENT_PART ||
                name == COMMENTS_PART || name == FOOTNOTES_PART || name == ENDNOTES_PART ||
                isHeaderOrFooterPart(name)
        }
        val body = scan.parts[DOCUMENT_PART]
        val features = mutableListOf<PreservedFeature>()

        fun part(name: String, label: String) {
            if (name in scan.names) features += PreservedFeature(label, 1)
        }

        // The default header and footer are editable; the first-page and
        // even-page variants are kept but cannot be reached.
        val headerTypes = body?.decodeToString()?.let { referenceTypes(it, "w:headerReference") }
            .orEmpty()
        val footerTypes = body?.decodeToString()?.let { referenceTypes(it, "w:footerReference") }
            .orEmpty()
        val headerVariants = headerTypes.count { it != null && it != "default" }
        val footerVariants = footerTypes.count { it != null && it != "default" }
        if (headerVariants > 0) features += PreservedFeature("Extra header pages", headerVariants)
        if (footerVariants > 0) features += PreservedFeature("Extra footer pages", footerVariants)

        part(COMMENTS_PART, "Comments")
        part(FOOTNOTES_PART, "Footnotes")
        part(ENDNOTES_PART, "Endnotes")

        if (body != null) {
            val xml = body.decodeToString()
            fun tags(vararg names: String): Int = names.sumOf { countTag(xml, it) }
            tags("w:ins", "w:del").takeIf { it > 0 }?.let {
                features += PreservedFeature("Tracked changes", it)
            }
            tags("w:hyperlink").takeIf { it > 0 }?.let {
                features += PreservedFeature("Hyperlinks", it)
            }
            tags("w:txbxContent").takeIf { it > 0 }?.let {
                features += PreservedFeature("Text boxes", it)
            }
            tags("m:oMath", "m:oMathPara").takeIf { it > 0 }?.let {
                features += PreservedFeature("Equations", it)
            }
            // The body always ends with one sectPr; more than one means real
            // section breaks (different page size, margins or column layout).
            (tags("w:sectPr") - 1).takeIf { it > 0 }?.let {
                features += PreservedFeature("Section breaks", it)
            }
            tags("w:cols").takeIf { it > 0 }?.let {
                features += PreservedFeature("Multi-column layout", it)
            }
        }
        return features
    }

    /** Features a workbook keeps but cannot edit, in the order the UI lists them. */
    fun xlsx(bytes: ByteArray): List<PreservedFeature> {
        val scan = scan(bytes) { name ->
            (name.startsWith("xl/worksheets/") && name.endsWith(".xml")) ||
                name.startsWith("xl/charts/") || name.startsWith("xl/drawings/") ||
                name.startsWith("xl/media/") || name.startsWith("xl/pivotTables/") ||
                name.startsWith("xl/pivotCache/") || name.startsWith("xl/comments")
        }
        val features = mutableListOf<PreservedFeature>()

        fun parts(prefix: String, label: String) {
            val count = scan.names.count { it.startsWith(prefix) }
            if (count > 0) features += PreservedFeature(label, count)
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
            // A validation that is a plain list of values can be edited; a rule
            // that points at another range cannot.
            val droppedValidations = countDroppedValidations(xml)
            if (droppedValidations > 0) {
                sheetTags["Data validation"] =
                    (sheetTags["Data validation"] ?: 0) + droppedValidations
            }
            hiddenRowsAndColumns += HIDDEN_ATTRIBUTE.findAll(xml).count()
        }
        // Stable, human order rather than map iteration order.
        listOf(
            "Conditional formatting",
            "Data validation",
            "Auto filters",
            "Sheet protection",
        ).forEach { label ->
            sheetTags[label]?.let { features += PreservedFeature(label, it) }
        }
        if (hiddenRowsAndColumns > 0) {
            features += PreservedFeature("Hidden rows and columns", hiddenRowsAndColumns)
        }
        return features
    }

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
