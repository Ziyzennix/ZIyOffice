package org.ziyoffice.ooxml

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.ziyoffice.model.Cell
import org.ziyoffice.model.CellFormat
import org.ziyoffice.model.CellValue
import org.ziyoffice.model.ColumnWidth
import org.ziyoffice.model.DataValidation
import org.ziyoffice.model.Sheet
import org.ziyoffice.model.Workbook
import org.ziyoffice.model.columnName
import org.ziyoffice.ooxml.xml.XmlElement
import org.ziyoffice.ooxml.xml.XmlScanner
import org.ziyoffice.ooxml.xml.escapeXmlAttribute
import org.ziyoffice.ooxml.xml.escapeXmlText

/**
 * Writes an edited workbook back into the package it came from.
 *
 * The old writer rebuilt the workbook from the model, so a save dropped the
 * charts, images, pivot tables, comments, conditional formatting, filters,
 * protections, hidden rows and columns and named ranges.
 *
 * This writer starts from the original package instead. It rewrites the
 * worksheet parts (the cells, the column widths, the row heights, the merged
 * ranges, the dropdowns and the frozen rows) and adds any new formatting to
 * the workbook's own style sheet. Everything else is copied through
 * untouched, so a cell edit does not disturb a chart that points at it.
 */
class XlsxEditor(private val source: XlsxSource) {

    fun save(workbook: Workbook): ByteArray {
        val parts = LinkedHashMap(source.parts)

        // A sheet the user added has no part of its own yet: it needs one, a
        // relationship and a content type, or the workbook would point at
        // nothing.
        val relIds = mutableListOf<String>()
        var relationships = parts[WORKBOOK_RELS_PART]?.decodeToString()
        var contentTypes = parts[CONTENT_TYPES_PART]?.decodeToString()
        var nextRel = nextRelationshipNumber(relationships)
        var nextSheet = nextSheetNumber(parts)

        workbook.sheets.forEachIndexed { index, sheet ->
            val sheetSource = source.sheets.getOrNull(index)
            if (sheetSource != null) {
                var sheetXml = editSheet(sheetSource, sheet)
                // The notes of this sheet. A sheet whose notes were not
                // changed keeps the part it had, byte for byte.
                val notes = XlsxNotes.notesOf(sheet)
                val notesBefore = XlsxNotes.notesOf(sheetSource.model)
                if (notes != notesBefore) {
                    val number = nextCommentsNumber(parts)
                    parts["xl/comments$number.xml"] =
                        XlsxNotes.commentsXml(notes).encodeToByteArray()
                    parts["xl/drawings/vmlDrawing$number.vml"] =
                        XlsxNotes.vmlDrawingXml(notes).encodeToByteArray()
                    val relsName = XlsxNotes.relsNameOf(sheetSource.partName)
                    val rels = parts[relsName]?.decodeToString() ?: emptyRelationships()
                    val first = nextRelationshipNumber(rels)
                    parts[relsName] = addTypedRelationship(
                        addTypedRelationship(
                            rels,
                            "rId$first",
                            XlsxNotes.VML_REL,
                            "../drawings/vmlDrawing$number.vml",
                        ),
                        "rId${first + 1}",
                        XlsxNotes.COMMENTS_REL,
                        "../comments$number.xml",
                    )!!.encodeToByteArray()
                    sheetXml = addLegacyDrawing(sheetXml, "rId$first")
                    contentTypes = addContentType(
                        contentTypes,
                        "/xl/comments$number.xml",
                        XlsxNotes.COMMENTS_CONTENT_TYPE,
                    )
                }
                parts[sheetSource.partName] = sheetXml.encodeToByteArray()
                relIds += sheetSource.relId
            } else {
                val partName = "xl/worksheets/sheet$nextSheet.xml"
                val relId = "rId$nextRel"
                parts[partName] = renderNewSheet(sheet).encodeToByteArray()
                relationships = addRelationship(
                    relationships,
                    relId,
                    "worksheet",
                    "worksheets/sheet$nextSheet.xml",
                )
                contentTypes = addContentType(
                    contentTypes,
                    "/$partName",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml",
                )
                relIds += relId
                nextRel++
                nextSheet++
            }
        }
        relationships?.let { parts[WORKBOOK_RELS_PART] = it.encodeToByteArray() }
        contentTypes?.let { parts[CONTENT_TYPES_PART] = it.encodeToByteArray() }

        parts[STYLES_PART] = source.styles.toXml().encodeToByteArray()
        parts[WORKBOOK_PART] = editWorkbook(workbook, relIds).encodeToByteArray()

        return zip(parts)
    }

    /**
     * A worksheet for a sheet the user added. The cells carry the formatting
     * the model holds, added to the workbook's own style sheet, so a new
     * sheet looks like the others.
     */
    private fun renderNewSheet(sheet: Sheet): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n")
        append("<worksheet xmlns=\"$SPREADSHEETML_NAMESPACE\">")
        append("<dimension ref=\"A1:").append(columnName(maxOf(sheet.columnCount - 1, 0)))
            .append(maxOf(sheet.rowCount, 1)).append("\"/>")
        if (sheet.frozenRows > 0) {
            append("<sheetViews><sheetView workbookViewId=\"0\">")
            append("<pane ySplit=\"").append(sheet.frozenRows)
                .append("\" topLeftCell=\"A").append(sheet.frozenRows + 1)
                .append("\" activePane=\"bottomLeft\" state=\"frozen\"/>")
            append("<selection pane=\"bottomLeft\"/>")
            append("</sheetView></sheetViews>")
        }
        val widths = sheet.columnWidths
        if (widths.isNotEmpty()) {
            append("<cols>")
            widths.forEach { column ->
                append("<col min=\"").append(column.firstColumn + 1)
                    .append("\" max=\"").append(minOf(column.lastColumn, MAX_COLUMN) + 1)
                    .append("\" width=\"").append(formatFloat(column.width))
                    .append("\" customWidth=\"1\"/>")
            }
            append("</cols>")
        }
        append("<sheetData>")
        val byRow = sheet.cells.groupBy { it.row }.toSortedMap()
        byRow.forEach { (row, cells) ->
            append("<row r=\"").append(row + 1).append("\">")
            cells.sortedBy { it.column }.forEach { cell ->
                val styleIndex = source.styles.indexFor(cell.format)
                append("<c r=\"").append(columnName(cell.column)).append(row + 1).append('"')
                if (styleIndex > 0) append(" s=\"").append(styleIndex).append('"')
                val value = cell.value
                when {
                    value.startsWith("=") -> {
                        append("><f>").append(escapeXmlText(value.substring(1))).append("</f></c>")
                    }
                    value == "TRUE" || value == "FALSE" -> {
                        append(" t=\"b\"><v>").append(if (value == "TRUE") "1" else "0")
                            .append("</v></c>")
                    }
                    value.toDoubleOrNull() != null -> append("><v>").append(value).append("</v></c>")
                    value.isEmpty() -> append("/>")
                    else -> {
                        append(" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
                            .append(escapeXmlText(value)).append("</t></is></c>")
                    }
                }
            }
            append("</row>")
        }
        append("</sheetData>")
        val merges = sheet.mergedRanges
        if (merges.isNotEmpty()) {
            append("<mergeCells count=\"").append(merges.size).append("\">")
            merges.forEach { range ->
                append("<mergeCell ref=\"").append(columnName(range.left)).append(range.top + 1)
                    .append(':').append(columnName(range.right)).append(range.bottom + 1)
                    .append("\"/>")
            }
            append("</mergeCells>")
        }
        val validations = sheet.validations.filter { it.values.isNotEmpty() }
        if (validations.isNotEmpty()) {
            append("<dataValidations count=\"").append(validations.size).append("\">")
            validations.forEach { validation ->
                append("<dataValidation type=\"list\" allowBlank=\"1\" showInputMessage=\"0\"")
                append(" showErrorMessage=\"1\" sqref=\"")
                    .append(columnName(validation.range.left)).append(validation.range.top + 1)
                    .append(':').append(columnName(validation.range.right))
                    .append(validation.range.bottom + 1)
                    .append("\"><formula1>\"")
                    .append(escapeXmlAttribute(validation.values.joinToString(",")))
                    .append("\"</formula1></dataValidation>")
            }
            append("</dataValidations>")
        }
        append("</worksheet>")
    }

    private fun nextRelationshipNumber(relationships: String?): Int {
        val highest = relationships?.let { text ->
            Regex("Id=\"rId(\\d+)\"").findAll(text).mapNotNull { it.groupValues[1].toIntOrNull() }
                .maxOrNull()
        } ?: 0
        return highest + 1
    }

    private fun nextSheetNumber(parts: Map<String, ByteArray>): Int {
        val highest = parts.keys.mapNotNull {
            Regex("xl/worksheets/sheet(\\d+)\\.xml").find(it)?.groupValues?.get(1)?.toIntOrNull()
        }.maxOrNull() ?: 0
        return highest + 1
    }

    /** A number no other part of the notes is using. */
    private fun nextCommentsNumber(parts: Map<String, ByteArray>): Int {
        val highest = parts.keys.mapNotNull {
            Regex("xl/comments(\\d+)\\.xml").find(it)?.groupValues?.get(1)?.toIntOrNull()
        }.maxOrNull() ?: 0
        return highest + 1
    }

    /** A relationships part with nothing in it. */
    private fun emptyRelationships(): String =
        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
            "<Relationships xmlns=\"$DOCUMENT_RELATIONSHIPS_NAMESPACE\"></Relationships>"

    /**
     * Points a worksheet at the drawing that says where its notes sit.
     *
     * The element belongs at the end of the worksheet, and a worksheet that
     * already has one keeps it.
     */
    private fun addLegacyDrawing(xml: String, relId: String): String {
        if (xml.contains("<legacyDrawing")) return xml
        val closeAt = xml.lastIndexOf("</worksheet>")
        if (closeAt < 0) return xml
        val element = "<legacyDrawing r:id=\"$relId\"/>"
        val withNamespace = if (xml.contains("xmlns:r=")) {
            xml
        } else {
            xml.replace(
                "<worksheet ",
                "<worksheet xmlns:r=\"$DOCUMENT_RELATIONSHIPS_NAMESPACE\" ",
            )
        }
        val at = withNamespace.lastIndexOf("</worksheet>")
        if (at < 0) return xml
        return withNamespace.substring(0, at) + element + withNamespace.substring(at)
    }

    private fun addRelationship(
        relationships: String?,
        relId: String,
        kind: String,
        target: String,
    ): String? {
        val text = relationships ?: return null
        val relationship = "<Relationship Id=\"$relId\" " +
            "Type=\"$DOCUMENT_RELATIONSHIPS_NAMESPACE/$kind\" Target=\"$target\"/>"
        return DocxXml.insertIntoContainer(text, "Relationships", relationship)
    }

    /** A relationship whose type is written out in full. */
    private fun addTypedRelationship(
        relationships: String?,
        relId: String,
        type: String,
        target: String,
    ): String? {
        val text = relationships ?: return null
        val relationship = "<Relationship Id=\"$relId\" Type=\"$type\" Target=\"$target\"/>"
        return DocxXml.insertIntoContainer(text, "Relationships", relationship)
    }

    private fun addContentType(contentTypes: String?, partName: String, type: String): String? {
        val text = contentTypes ?: return null
        if (text.contains("PartName=\"$partName\"")) return text
        val declaration = "<Override PartName=\"$partName\" ContentType=\"$type\"/>"
        return DocxXml.insertIntoContainer(text, "Types", declaration)
    }

    /**
     * Rewrites one worksheet. Every cell keeps the style it had unless the
     * user changed the formatting, and every row keeps the attributes the
     * workbook gave it (a hidden row, an outline level) that the model does
     * not hold.
     */
    private fun editSheet(sheetSource: XlsxSheetSource, sheet: Sheet): String {
        val xml = sheetSource.xml
        val edits = mutableListOf<Edit>()

        if (sameSheet(sheetSource.model, sheet)) return xml

        val sheetData = renderSheetData(sheetSource, sheet)
        sheetSource.sheetData?.let { data ->
            edits += Edit(data.start, data.end, sheetData)
        } ?: run {
            val at = insertionPoint(sheetSource.root, listOf("sheetData"))
            edits += Edit(at, at, sheetData)
        }

        sheetSource.cols?.let { cols ->
            edits += Edit(cols.start, cols.end, renderCols(sheetSource, sheet))
        } ?: run {
            val widths = sheet.columnWidths
            if (widths.isNotEmpty()) {
                val at = insertionPoint(sheetSource.root, listOf("sheetData"))
                edits += Edit(at, at, renderCols(sheetSource, sheet))
            }
        }

        val merges = renderMerges(sheet)
        if (merges.isNotEmpty()) {
            sheetSource.mergeCells?.let { element ->
                edits += Edit(element.start, element.end, merges)
            } ?: run {
                val at = insertionPoint(
                    sheetSource.root,
                    listOf("phoneticPr", "conditionalFormatting", "dataValidations", "hyperlinks"),
                )
                edits += Edit(at, at, merges)
            }
        } else if (sheetSource.mergeCells != null) {
            edits += Edit(sheetSource.mergeCells.start, sheetSource.mergeCells.end, "")
        }

        val validations = renderValidations(sheetSource, sheet)
        if (validations.isNotEmpty()) {
            sheetSource.dataValidations?.let { element ->
                edits += Edit(element.start, element.end, validations)
            } ?: run {
                val at = insertionPoint(
                    sheetSource.root,
                    listOf("hyperlinks", "printOptions", "pageMargins", "pageSetup"),
                )
                edits += Edit(at, at, validations)
            }
        } else if (sheetSource.dataValidations != null) {
            edits += Edit(sheetSource.dataValidations.start, sheetSource.dataValidations.end, "")
        }

        if (sheet.frozenRows != (sheetSource.sheetViews?.let { view ->
                view.child("sheetView")?.child("pane")?.let { pane ->
                    if (pane.attr("state") == "frozen") pane.attr("ySplit")?.toIntOrNull() ?: 0
                    else 0
                } ?: 0
            } ?: 0)
        ) {
            val views = renderSheetViews(sheet)
            sheetSource.sheetViews?.let { element ->
                edits += Edit(element.start, element.end, views)
            } ?: run {
                val at = insertionPoint(sheetSource.root, listOf("sheetFormatPr", "cols", "sheetData"))
                edits += Edit(at, at, views)
            }
        }

        return applyEdits(xml, edits)
    }

    /**
     * True when the sheet still says what the source sheet said. When it
     * does, the worksheet part is copied through untouched, so a save that
     * changes nothing produces the same file.
     */
    private fun sameSheet(original: Sheet, sheet: Sheet): Boolean {
        if (sheet.frozenRows != original.frozenRows) return false
        if (sheet.mergedRanges != original.mergedRanges) return false
        if (sheet.columnWidths != original.columnWidths) return false
        if (sheet.rowHeights != original.rowHeights) return false
        if (sheet.validations != original.validations) return false
        if (sheet.cells.size != original.cells.size) return false
        val byPosition = original.cells.associateBy { it.row to it.column }
        sheet.cells.forEach { cell ->
            val source = byPosition[cell.row to cell.column] ?: return false
            if (source.value != cell.value) return false
            if (source.format != cell.format) return false
        }
        return true
    }

    /**
     * The cells of a sheet, written out again. A cell keeps the style it had
     * unless its formatting changed, in which case the new formatting is
     * added to the workbook's style sheet and the cell points at that.
     */
    private fun renderSheetData(sheetSource: XlsxSheetSource, sheet: Sheet): String {
        val builder = StringBuilder("<sheetData>")
        val byRow = sheet.cells.groupBy { it.row }.toSortedMap()
        val rows = (byRow.keys + sheet.rowHeights.keys).filter { it >= 0 }.distinct().sorted()
        rows.forEach { row ->
            val sourceRow = sheetSource.rows[row]
            val cells = byRow[row].orEmpty().sortedBy { it.column }
            val height = sheet.rowHeights[row]
            builder.append("<row r=\"").append(row + 1).append('"')
            if (height != null && height.isFinite() && height > 0f) {
                builder.append(" ht=\"").append(formatFloat(height)).append("\" customHeight=\"1\"")
            }
            sourceRow?.attributes?.forEach { attribute ->
                if (attribute.localName in PRESERVED_ROW_ATTRIBUTES) {
                    builder.append(' ').append(attribute.qName).append("=\"")
                        .append(escapeXmlAttribute(attribute.value)).append('"')
                }
            }
            builder.append('>')
            cells.forEach { cell ->
                val text = renderCell(sheetSource, cell)
                if (text != null) builder.append(text)
            }
            builder.append("</row>")
        }
        builder.append("</sheetData>")
        return builder.toString()
    }

    /** One cell, or null when the cell holds nothing worth writing. */
    private fun renderCell(sheetSource: XlsxSheetSource, cell: Cell): String? {
        val sourceCell = sheetSource.cells[cell.row to cell.column]
        val sourceFormat = sheetSource.formats[cell.row to cell.column]
        val sourceStyle = sheetSource.styleIndexes[cell.row to cell.column]

        val styleIndex = if (sourceFormat != null && sourceFormat == cell.format) {
            sourceStyle ?: 0
        } else {
            source.styles.indexFor(cell.format)
        }

        val value = cell.value
        if (value.isEmpty() && cell.format == CellFormat()) return null

        val builder = StringBuilder()
        builder.append("<c r=\"").append(columnName(cell.column)).append(cell.row + 1).append('"')
        if (styleIndex > 0) builder.append(" s=\"").append(styleIndex).append('"')
        sourceCell?.attributes?.forEach { attribute ->
            if (attribute.localName in PRESERVED_CELL_ATTRIBUTES) {
                builder.append(' ').append(attribute.qName).append("=\"")
                    .append(escapeXmlAttribute(attribute.value)).append('"')
            }
        }

        if (value.startsWith("=")) {
            val formula = value.substring(1)
            val cached = cell.cached ?: cachedValueOf(sheetSource, cell, formula)
            when (cached) {
                is CellValue.Bool -> builder.append(" t=\"b\"")
                is CellValue.Text -> builder.append(" t=\"str\"")
                is CellValue.Error -> builder.append(" t=\"e\"")
                else -> Unit
            }
            builder.append('>')
            builder.append("<f>").append(escapeXmlText(formula)).append("</f>")
            when (cached) {
                is CellValue.Number -> builder.append("<v>").append(formatNumber(cached.number)).append("</v>")
                is CellValue.Text -> builder.append("<v>").append(escapeXmlText(cached.text)).append("</v>")
                is CellValue.Bool -> builder.append("<v>").append(if (cached.value) "1" else "0").append("</v>")
                is CellValue.Error -> builder.append("<v>").append(escapeXmlText(cached.message)).append("</v>")
                null -> Unit
            }
            builder.append("</c>")
            return builder.toString()
        }

        when {
            value == "TRUE" || value == "FALSE" -> {
                builder.append(" t=\"b\"><v>").append(if (value == "TRUE") "1" else "0")
                    .append("</v></c>")
            }
            value.toDoubleOrNull() != null -> {
                builder.append("><v>").append(value).append("</v></c>")
            }
            value.isEmpty() -> builder.append("/>")
            else -> {
                builder.append(" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
                    .append(escapeXmlText(value))
                    .append("</t></is></c>")
            }
        }
        return builder.toString()
    }

    /**
     * The value the workbook cached for a formula cell. It is only reused
     * when the formula is still the one the workbook holds: a formula the
     * user changed has to be recalculated, and a stale result would be worse
     * than none.
     */
    private fun cachedValueOf(
        sheetSource: XlsxSheetSource,
        cell: Cell,
        formula: String,
    ): CellValue? {
        val position = cell.row to cell.column
        if (sheetSource.formulas[position] != formula) return null
        val raw = sheetSource.cachedValues[position] ?: return null
        val type = sheetSource.cells[position]?.attr("t")
        return when (type) {
            "b" -> CellValue.Bool(raw == "1")
            "str" -> CellValue.Text(raw)
            "e" -> CellValue.Error(raw)
            else -> raw.toDoubleOrNull()?.let { CellValue.Number(it) }
        }
    }

    /**
     * The column widths. The workbook's own column entries are kept (a
     * hidden column stays hidden) and only the width the user set is written
     * into them.
     */
    private fun renderCols(sheetSource: XlsxSheetSource, sheet: Sheet): String {
        val widths = sheet.columnWidths
        val builder = StringBuilder("<cols>")
        sheetSource.cols?.childrenNamed("col")?.forEach { col ->
            val first = col.attr("min")?.toIntOrNull()?.minus(1) ?: return@forEach
            val last = col.attr("max")?.toIntOrNull()?.minus(1) ?: return@forEach
            val width = widths.firstOrNull { it.firstColumn == first && it.lastColumn == last }
            builder.append("<col min=\"").append(first + 1).append("\" max=\"").append(last + 1)
                .append('"')
            if (width != null) {
                builder.append(" width=\"").append(formatFloat(width.width)).append("\" customWidth=\"1\"")
            }
            col.attributes.forEach { attribute ->
                if (attribute.localName in PRESERVED_COL_ATTRIBUTES) {
                    builder.append(' ').append(attribute.qName).append("=\"")
                        .append(escapeXmlAttribute(attribute.value)).append('"')
                }
            }
            builder.append("/>")
        }
        // Widths the workbook did not have yet.
        val covered = sheetSource.cols?.childrenNamed("col")
            ?.mapNotNull { col ->
                val first = col.attr("min")?.toIntOrNull()?.minus(1)
                val last = col.attr("max")?.toIntOrNull()?.minus(1)
                if (first == null || last == null) null else first to last
            }.orEmpty()
        widths.filter { width -> covered.none { it == (width.firstColumn to width.lastColumn) } }
            .forEach { width ->
                builder.append("<col min=\"").append(width.firstColumn + 1)
                    .append("\" max=\"").append(minOf(width.lastColumn, MAX_COLUMN) + 1)
                    .append("\" width=\"").append(width.width).append("\" customWidth=\"1\"/>")
            }
        builder.append("</cols>")
        return builder.toString()
    }

    private fun renderMerges(sheet: Sheet): String {
        val merges = sheet.mergedRanges
        if (merges.isEmpty()) return ""
        val builder = StringBuilder()
        builder.append("<mergeCells count=\"").append(merges.size).append("\">")
        merges.forEach { range ->
            builder.append("<mergeCell ref=\"")
                .append(columnName(range.left)).append(range.top + 1).append(':')
                .append(columnName(range.right)).append(range.bottom + 1)
                .append("\"/>")
        }
        builder.append("</mergeCells>")
        return builder.toString()
    }

    /**
     * The dropdown lists. Rules the workbook has that ZiyOffice cannot hold
     * (a rule pointing at another range, for instance) are kept exactly as
     * they are; only the rules the model holds are written out.
     */
    private fun renderValidations(sheetSource: XlsxSheetSource, sheet: Sheet): String {
        val validations = sheet.validations.filter { it.values.isNotEmpty() }
        val modelRanges = validations.map { it.range }.toSet()
        val kept = sheetSource.dataValidations?.childrenNamed("dataValidation").orEmpty()
            .filter { element ->
                val range = element.attr("sqref")?.let(::parseCellRange) ?: return@filter false
                range !in modelRanges
            }
        if (validations.isEmpty() && kept.isEmpty()) return ""
        val builder = StringBuilder()
        builder.append("<dataValidations count=\"").append(validations.size + kept.size).append("\">")
        kept.forEach { element -> builder.append(element.source(sheetSource.xml)) }
        validations.forEach { validation ->
            builder.append("<dataValidation type=\"list\" allowBlank=\"1\" showInputMessage=\"0\"")
            builder.append(" showErrorMessage=\"1\" sqref=\"")
                .append(columnName(validation.range.left)).append(validation.range.top + 1)
            if (validation.range.rowSpan > 1 || validation.range.columnSpan > 1) {
                builder.append(':')
                    .append(columnName(validation.range.right)).append(validation.range.bottom + 1)
            }
            builder.append("\"><formula1>\"")
                .append(escapeXmlAttribute(validation.values.joinToString(",")))
                .append("\"</formula1></dataValidation>")
        }
        builder.append("</dataValidations>")
        return builder.toString()
    }

    private fun renderSheetViews(sheet: Sheet): String {
        val builder = StringBuilder("<sheetViews><sheetView workbookViewId=\"0\">")
        if (sheet.frozenRows > 0) {
            builder.append("<pane ySplit=\"").append(sheet.frozenRows)
                .append("\" topLeftCell=\"A").append(sheet.frozenRows + 1)
                .append("\" activePane=\"bottomLeft\" state=\"frozen\"/>")
            builder.append("<selection pane=\"bottomLeft\"/>")
        }
        builder.append("</sheetView></sheetViews>")
        return builder.toString()
    }

    /**
     * The workbook part: the sheet names, their order and the active tab. The
     * relationships are not touched, so a sheet keeps the charts, drawings
     * and pivot tables that point at it.
     */
    private fun editWorkbook(workbook: Workbook, relIds: List<String>): String {
        val xml = source.workbookXml
        val root = source.workbookRoot
        val edits = mutableListOf<Edit>()

        root.child("sheets")?.let { sheets ->
            val builder = StringBuilder("<sheets>")
            workbook.sheets.forEachIndexed { index, sheet ->
                val sourceSheet = source.sheets.getOrNull(index)
                val relId = relIds.getOrNull(index) ?: sourceSheet?.relId.orEmpty()
                val sheetId = sourceSheet?.sheetId ?: (index + 1).toString()
                builder.append("<sheet name=\"")
                    .append(escapeXmlAttribute(sheet.name.ifEmpty { "Sheet${index + 1}" }))
                    .append("\" sheetId=\"").append(sheetId)
                    .append("\" r:id=\"").append(relId).append("\"/>")
            }
            builder.append("</sheets>")
            edits += Edit(sheets.start, sheets.end, builder.toString())
        }

        root.child("bookViews")?.child("workbookView")?.let { view ->
            val active = workbook.activeSheet.coerceIn(0, (workbook.sheets.size - 1).coerceAtLeast(0))
            if (view.attr("activeTab")?.toIntOrNull() != active) {
                val attribute = view.attributes.firstOrNull { it.localName == "activeTab" }
                val replacement = if (attribute != null && attribute.start >= 0) {
                    xml.substring(0, attribute.start) + "activeTab=\"$active\"" +
                        xml.substring(attribute.end)
                } else {
                    val at = view.contentStart
                    xml.substring(0, at) + " activeTab=\"$active\"" + xml.substring(at)
                }
                edits += Edit(view.start, view.end, replacement)
            }
        }

        // The names a formula can use. A file whose names were not changed
        // keeps the bytes it had, and one whose names were is rewritten.
        if (workbook.namedRanges != source.workbook.namedRanges) {
            val names = root.child("definedNames")
            if (names != null) {
                edits += Edit(names.start, names.end, definedNamesXml(workbook))
            } else {
                val sheets = root.child("sheets")
                if (sheets != null) {
                    val at = sheets.end
                    edits += Edit(at, at, definedNamesXml(workbook))
                }
            }
        }

        return applyEdits(xml, edits)
    }

    /** The names a formula can use, as the workbook part holds them. */
    private fun definedNamesXml(workbook: Workbook): String = buildString {
        append("<definedNames>")
        workbook.namedRanges.forEach { named ->
            append("<definedName name=\"")
            append(escapeXmlAttribute(named.name))
            append("\">")
            val sheetName = if (named.sheetIndex in workbook.sheets.indices) {
                escapeXmlAttribute(workbook.sheets[named.sheetIndex].name)
            } else {
                ""
            }
            val range = named.range
            val cells = "\$${columnName(range.left)}\$${range.top + 1}:" +
                "\$${columnName(range.right)}\$${range.bottom + 1}"
            append(if (sheetName.isEmpty()) cells else "$sheetName!$cells")
            append("</definedName>")
        }
        append("</definedNames>")
    }

    /**
     * Where an element belongs when the worksheet does not have one yet. The
     * order of a worksheet's children is fixed by the schema, so a new
     * element goes before the first element that has to follow it.
     */
    private fun insertionPoint(root: XmlElement, before: List<String>): Int {
        val child = root.children.firstOrNull { it.localName in before }
        if (child != null) return child.start
        return root.contentEnd
    }

    private fun applyEdits(xml: String, edits: List<Edit>): String {
        if (edits.isEmpty()) return xml
        val sorted = edits.sortedBy { it.start }
        val builder = StringBuilder(xml.length + 64)
        var cursor = 0
        sorted.forEach { edit ->
            if (edit.start < cursor) return@forEach
            builder.append(xml, cursor, edit.start)
            builder.append(edit.replacement)
            cursor = edit.end
        }
        builder.append(xml, cursor, xml.length)
        return builder.toString()
    }

    /** A float the way a spreadsheet writes it: whole numbers without a tail. */
    private fun formatFloat(value: Float): String {
        if (value == value.toInt().toFloat()) return value.toInt().toString()
        return value.toString()
    }

    private fun formatNumber(number: Double): String {
        if (number.isFinite() && number == Math.floor(number) && Math.abs(number) < 1e15) {
            return number.toLong().toString()
        }
        return number.toString()
    }

    private fun zip(parts: Map<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            parts.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private data class Edit(val start: Int, val end: Int, val replacement: String)

    private companion object {
        const val WORKBOOK_PART = "xl/workbook.xml"
        const val WORKBOOK_RELS_PART = "xl/_rels/workbook.xml.rels"
        const val CONTENT_TYPES_PART = "[Content_Types].xml"
        const val STYLES_PART = "xl/styles.xml"
        const val MAX_COLUMN = 16383
        const val SPREADSHEETML_NAMESPACE =
            "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
        const val DOCUMENT_RELATIONSHIPS_NAMESPACE =
            "http://schemas.openxmlformats.org/officeDocument/2006/relationships"

        /** Row attributes the model does not hold but the workbook may. */
        val PRESERVED_ROW_ATTRIBUTES = setOf(
            "hidden", "outlineLevel", "collapsed", "thickBot", "thickTop", "customFormat",
        )

        /** Cell attributes the model does not hold but the workbook may. */
        val PRESERVED_CELL_ATTRIBUTES = setOf("cm", "vm", "ph")

        /** Column attributes the model does not hold but the workbook may. */
        val PRESERVED_COL_ATTRIBUTES = setOf("hidden", "outlineLevel", "collapsed", "bestFit")
    }
}
