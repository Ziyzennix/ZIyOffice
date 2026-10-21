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
 * The old writer rebuilt the workbook from the model, so a save produced a
 * package holding only the cells, the sheets and the formatting the model
 * could express. Charts, images, pivot tables, comments, conditional
 * formatting, filters, protections, hidden rows and columns and named ranges
 * were all dropped.
 *
 * This writer starts from the original package instead. It rewrites the
 * worksheet parts — the cells, the column widths, the row heights, the merged
 * ranges, the dropdowns and the frozen rows — and adds any new formatting to
 * the workbook's own style sheet. Everything else is copied through untouched,
 * so a cell edit does not disturb a chart that points at it.
 */
class XlsxEditor(private val source: XlsxSource) {

    /** Saves [workbook] into the source package and returns the new bytes. */
    fun save(workbook: Workbook): ByteArray {
        val parts = LinkedHashMap(source.parts)

        val sheets = workbook.sheets
        for (index in sheets.indices) {
            val sheetSource = source.sheets.getOrNull(index) ?: continue
            parts[sheetSource.partName] = editSheet(sheetSource, sheets[index]).encodeToByteArray()
        }

        parts[STYLES_PART] = source.styles.toXml().encodeToByteArray()
        parts[WORKBOOK_PART] = editWorkbook(workbook).encodeToByteArray()

        return zip(parts)
    }

    // ------------------------------------------------------------------ sheet

    /**
     * Rewrites one worksheet.
     *
     * The cells are written out again, but every cell keeps the style it had
     * unless the user changed the formatting, and every row keeps the
     * attributes the workbook gave it — a hidden row, an outline level — that
     * the model does not hold.
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
     * True when the sheet still says what the source sheet said.
     *
     * When it does, the worksheet part is copied through untouched, so a save
     * that changes nothing produces the same file.
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
     * The cells of a sheet, written out again.
     *
     * A cell keeps the style it had unless its formatting changed, in which
     * case the new formatting is added to the workbook's style sheet and the
     * cell points at that.
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
     * The value the workbook cached for a formula cell.
     *
     * It is only reused when the formula is still the one the workbook holds:
     * a formula the user changed has to be recalculated, and a stale result
     * would be worse than none.
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
     * The column widths.
     *
     * The workbook's own column entries are kept — a hidden column stays
     * hidden — and only the width the user set is written into them.
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

    /** The merged ranges, or an empty string when there are none. */
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
     * The dropdown lists.
     *
     * The workbook's own rules that ZiyOffice cannot hold — a rule that points
     * at another range, for instance — are kept exactly as they are; only the
     * rules the model holds are written out.
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

    /** The frozen rows. */
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

    // --------------------------------------------------------------- workbook

    /**
     * The workbook part: the sheet names, their order and the active tab.
     *
     * The relationships are not touched, so a sheet keeps the charts,
     * drawings and pivot tables that point at it.
     */
    private fun editWorkbook(workbook: Workbook): String {
        val xml = source.workbookXml
        val root = source.workbookRoot
        val edits = mutableListOf<Edit>()

        root.child("sheets")?.let { sheets ->
            val builder = StringBuilder("<sheets>")
            workbook.sheets.forEachIndexed { index, sheet ->
                val sourceSheet = source.sheets.getOrNull(index)
                val relId = sourceSheet?.relId ?: ""
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

        return applyEdits(xml, edits)
    }

    // --------------------------------------------------------------- helpers

    /**
     * Where an element belongs when the worksheet does not have one yet.
     *
     * The order of a worksheet's children is fixed by the schema, so a new
     * element goes before the first element that has to follow it.
     */
    private fun insertionPoint(root: XmlElement, before: List<String>): Int {
        val child = root.children.firstOrNull { it.localName in before }
        if (child != null) return child.start
        return root.contentEnd
    }

    /** Applies a list of edits to a string. */
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

    /** One replacement in a source string. */
    private data class Edit(val start: Int, val end: Int, val replacement: String)

    private companion object {
        const val WORKBOOK_PART = "xl/workbook.xml"
        const val STYLES_PART = "xl/styles.xml"
        const val MAX_COLUMN = 16383

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
