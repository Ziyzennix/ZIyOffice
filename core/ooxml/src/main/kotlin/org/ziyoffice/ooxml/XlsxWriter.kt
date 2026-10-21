package org.ziyoffice.ooxml

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.ziyoffice.model.BorderStyle
import org.ziyoffice.model.Cell
import org.ziyoffice.model.CellAlignment
import org.ziyoffice.model.CellBorders
import org.ziyoffice.model.CellFormat
import org.ziyoffice.model.MAX_SHEET_COLUMNS
import org.ziyoffice.model.Sheet
import org.ziyoffice.model.Workbook
import org.ziyoffice.model.columnName
import org.ziyoffice.model.normaliseMerges

/**
 * Writes the [Workbook] model back into an .xlsx package with platform APIs
 * only. The counterpart of [XlsxReader].
 *
 * The package is minimal but standards-valid: content types, the package and
 * workbook relationships, xl/workbook.xml, xl/styles.xml and one worksheet
 * part per sheet. Values are written inline where possible: numbers as <v>,
 * text as inline strings, formulas as <f> with a cached <v>.
 *
 * Cell formatting is flattened into direct cellXfs entries, so bold, italic,
 * underline, strike, size, colour, fill, borders, alignment and number
 * formats all survive a round trip.
 */
class XlsxWriter {

    fun write(workbook: Workbook): ByteArray {
        val sheets = workbook.sheets.ifEmpty { listOf(Sheet("Sheet1", emptyList())) }
        val styles = StylePlan.from(sheets)

        val parts = linkedMapOf<String, ByteArray>()
        parts["[Content_Types].xml"] = contentTypes(sheets.size).encodeToByteArray()
        parts["_rels/.rels"] = ROOT_RELS.encodeToByteArray()
        parts["xl/workbook.xml"] = workbookXml(sheets, workbook.activeSheet).encodeToByteArray()
        parts["xl/_rels/workbook.xml.rels"] = workbookRels(sheets.size).encodeToByteArray()
        parts["xl/styles.xml"] = styles.toXml().encodeToByteArray()
        sheets.forEachIndexed { index, sheet ->
            parts["xl/worksheets/sheet${index + 1}.xml"] =
                sheetXml(sheet, styles).encodeToByteArray()
        }

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

    private fun workbookXml(sheets: List<Sheet>, activeSheet: Int): String = buildString {
        append(XML_HEADER)
        append("<workbook xmlns=\"$SPREADSHEETML_NAMESPACE\"")
        append(" xmlns:r=\"$DOCUMENT_RELATIONSHIPS_NAMESPACE\">")
        append("<bookViews><workbookView activeTab=\"")
        append(activeSheet.coerceIn(0, sheets.size - 1))
        append("\"/></bookViews><sheets>")
        sheets.forEachIndexed { index, sheet ->
            append("<sheet name=\"")
            append(escapeAttribute(sheet.name.ifEmpty { "Sheet${index + 1}" }))
            append("\" sheetId=\"").append(index + 1)
            append("\" r:id=\"rId").append(index + 1).append("\"/>")
        }
        append("</sheets></workbook>")
    }

    private fun workbookRels(sheetCount: Int): String = buildString {
        append(XML_HEADER)
        append("<Relationships xmlns=\"$PACKAGE_RELS_NAMESPACE\">")
        repeat(sheetCount) { index ->
            append("<Relationship Id=\"rId").append(index + 1)
            append("\" Type=\"$DOCUMENT_RELATIONSHIPS_NAMESPACE/worksheet")
            append("\" Target=\"worksheets/sheet").append(index + 1).append(".xml\"/>")
        }
        append("<Relationship Id=\"rId").append(sheetCount + 1)
        append("\" Type=\"$DOCUMENT_RELATIONSHIPS_NAMESPACE/styles")
        append("\" Target=\"styles.xml\"/>")
        append("</Relationships>")
    }

    private fun contentTypes(sheetCount: Int): String = buildString {
        append(XML_HEADER)
        append("<Types xmlns=\"$CONTENT_TYPES_NAMESPACE\">")
        append("<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>")
        append("<Default Extension=\"xml\" ContentType=\"application/xml\"/>")
        append("<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>")
        append("<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>")
        repeat(sheetCount) { index ->
            append("<Override PartName=\"/xl/worksheets/sheet").append(index + 1)
            append(".xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>")
        }
        append("</Types>")
    }

    private fun sheetXml(sheet: Sheet, styles: StylePlan): String = buildString {
        val lastRow = maxOf(
            sheet.rowCount,
            sheet.mergedRanges.maxOfOrNull { it.bottom + 1 } ?: 0,
            (sheet.rowHeights.keys.maxOrNull() ?: -1) + 1,
        ).coerceAtLeast(1)
        val lastColumn = maxOf(
            sheet.columnCount,
            sheet.mergedRanges.maxOfOrNull { it.right + 1 } ?: 0,
        ).coerceAtLeast(1)
        append(XML_HEADER)
        append("<worksheet xmlns=\"$SPREADSHEETML_NAMESPACE\">")
        append("<dimension ref=\"A1:").append(columnName(lastColumn - 1))
            .append(lastRow).append("\"/>")
        if (sheet.frozenRows > 0) {
            // pane + selection is the minimum Excel needs to reopen with rows locked.
            append("<sheetViews><sheetView workbookViewId=\"0\">")
            append("<pane ySplit=\"").append(sheet.frozenRows)
            append("\" topLeftCell=\"A").append(sheet.frozenRows + 1)
            append("\" activePane=\"bottomLeft\" state=\"frozen\"/>")
            append("<selection pane=\"bottomLeft\"/>")
            append("</sheetView></sheetViews>")
        }

        // Column widths come before sheetData in the schema. A run is written
        // as one <col> so a default width covering every column does not
        // become 16k tags.
        val widths = sheet.columnWidths
            .filter {
                it.firstColumn in 0 until MAX_SHEET_COLUMNS &&
                    it.lastColumn >= it.firstColumn &&
                    it.width > 0f && it.width.isFinite()
            }
            .sortedWith(compareBy({ it.firstColumn }, { it.lastColumn }))
        if (widths.isNotEmpty()) {
            append("<cols>")
            widths.forEach { column ->
                append("<col min=\"").append(column.firstColumn + 1)
                append("\" max=\"")
                    .append(minOf(column.lastColumn, MAX_SHEET_COLUMNS - 1) + 1)
                append("\" width=\"").append(column.width)
                append("\" customWidth=\"1\"/>")
            }
            append("</cols>")
        }

        append("<sheetData>")

        val byRow = sheet.cells
            // A formatted-but-empty cell still carries user intent (pre-formatted
            // column), so it is written; the reader keeps it on the way back.
            .filter { it.value.isNotEmpty() || it.format != CellFormat() }
            .groupBy { it.row }
            .toSortedMap()

        // Rows can carry a height without carrying cells; keep those too.
        val rows = (byRow.keys + sheet.rowHeights.keys)
            .filter { it >= 0 }
            .distinct()
            .sorted()
        rows.forEach { row ->
            append("<row r=\"").append(row + 1).append('"')
            sheet.rowHeights[row]?.takeIf { it.isFinite() && it > 0f }?.let { height ->
                append(" ht=\"").append(height).append("\" customHeight=\"1\"")
            }
            append('>')
            byRow[row]?.sortedBy { it.column }?.forEach { cell -> appendCell(cell, styles) }
            append("</row>")
        }

        append("</sheetData>")

        // Merges sit after sheetData in the schema. Their anchors keep their
        // value; the covered cells keep whatever the file held, so a save never
        // invents or destroys data behind a merge.
        val merges = sheet.mergedRanges.normaliseMerges()
        if (merges.isNotEmpty()) {
            append("<mergeCells count=\"").append(merges.size).append("\">")
            merges.forEach { range ->
                append("<mergeCell ref=\"")
                append(columnName(range.left)).append(range.top + 1).append(':')
                append(columnName(range.right)).append(range.bottom + 1)
                append("\"/>")
            }
            append("</mergeCells>")
        }
        // Validations come after the merges in the schema. Only a plain list of
        // values is written, which is what the model holds.
        val validations = sheet.validations.filter { it.values.isNotEmpty() }
        if (validations.isNotEmpty()) {
            append("<dataValidations count=\"").append(validations.size).append("\">")
            validations.forEach { validation ->
                append("<dataValidation type=\"list\" allowBlank=\"1\" showInputMessage=\"0\"")
                append(" showErrorMessage=\"1\" sqref=\"")
                append(columnName(validation.range.left)).append(validation.range.top + 1)
                append(':')
                append(columnName(validation.range.right)).append(validation.range.bottom + 1)
                append("\"><formula1>\"")
                append(escapeAttribute(validation.values.joinToString(",")))
                append("\"</formula1></dataValidation>")
            }
            append("</dataValidations>")
        }
        append("</worksheet>")
    }

    private fun StringBuilder.appendCell(cell: Cell, styles: StylePlan) {
        val styleIndex = styles.indexOf(cell.format)
        val raw = cell.value
        val cached = cell.cached

        val typeAttribute = when {
            raw.startsWith("=") ->
                if (cached is org.ziyoffice.model.CellValue.Bool) " t=\"b\"" else ""
            raw == "TRUE" || raw == "FALSE" -> " t=\"b\""
            raw.toDoubleOrNull() != null -> ""
            else -> " t=\"inlineStr\""
        }

        append("<c r=\"").append(columnName(cell.column)).append(cell.row + 1).append('"')
        if (styleIndex > 0) append(" s=\"").append(styleIndex).append('"')
        append(typeAttribute)
        append('>')

        when {
            raw.startsWith("=") -> {
                append("<f>").append(escapeText(raw.substring(1))).append("</f>")
                when (cached) {
                    is org.ziyoffice.model.CellValue.Number ->
                        append("<v>").append(formatNumber(cached.number)).append("</v>")
                    is org.ziyoffice.model.CellValue.Text ->
                        append("<v>").append(escapeText(cached.text)).append("</v>")
                    is org.ziyoffice.model.CellValue.Bool ->
                        append("<v>").append(if (cached.value) "1" else "0").append("</v>")
                    is org.ziyoffice.model.CellValue.Error ->
                        append("<v>").append(escapeText(cached.message)).append("</v>")
                    null -> Unit
                }
            }
            raw.toDoubleOrNull() != null -> append("<v>").append(raw).append("</v>")
            raw == "TRUE" || raw == "FALSE" ->
                append("<v>").append(if (raw == "TRUE") "1" else "0").append("</v>")
            else -> {
                append("<is><t xml:space=\"preserve\">")
                append(escapeText(raw))
                append("</t></is>")
            }
        }
        append("</c>")
    }

    private class StylePlan(
        private val formats: List<CellFormat>,
        private val index: Map<CellFormat, Int>,
        private val borders: List<CellBorders>,
        private val borderIndex: Map<CellBorders, Int>,
    ) {
        fun indexOf(format: CellFormat): Int {
            val normalized = format.copy(sizePt = format.sizePt, numberFormat = format.numberFormat)
            return index[normalized] ?: 0
        }

        fun toXml(): String = buildString {
            append(XML_HEADER)
            append("<styleSheet xmlns=\"$SPREADSHEETML_NAMESPACE\">")

            // Fonts
            append("<fonts count=\"").append(formats.size).append("\">")
            formats.forEach { format ->
                append("<font>")
                if (format.bold) append("<b/>")
                if (format.italic) append("<i/>")
                if (format.underline) append("<u/>")
                if (format.strike) append("<strike/>")
                format.sizePt?.let { append("<sz val=\"").append(it).append("\"/>") }
                format.colorRgb?.let {
                    append("<color rgb=\"").append(argb(it)).append("\"/>")
                }
                append("</font>")
            }
            append("</fonts>")

            // Fills (index 0 and 1 are reserved by the spec)
            append("<fills count=\"").append(formats.size + 2).append("\">")
            append("<fill><patternFill patternType=\"none\"/></fill>")
            append("<fill><patternFill patternType=\"gray125\"/></fill>")
            formats.forEach { format ->
                append("<fill><patternFill patternType=\"solid\">")
                format.fillRgb?.let {
                    append("<fgColor rgb=\"").append(argb(it)).append("\"/>")
                }
                append("<bgColor indexed=\"64\"/></patternFill></fill>")
            }
            append("</fills>")

            // Borders: index 0 is the empty border every sheet starts with.
            append("<borders count=\"").append(borders.size).append("\">")
            borders.forEach { border ->
                append("<border>")
                append("<left").append(borderStyle(border.left)).append("/>")
                append("<right").append(borderStyle(border.right)).append("/>")
                append("<top").append(borderStyle(border.top)).append("/>")
                append("<bottom").append(borderStyle(border.bottom)).append("/>")
                append("<diagonal/>")
                append("</border>")
            }
            append("</borders>")

            // Number formats: custom ids start at 164.
            val customFormats = formats.mapNotNull { it.numberFormat }.distinct()
            if (customFormats.isNotEmpty()) {
                append("<numFmts count=\"").append(customFormats.size).append("\">")
                customFormats.forEachIndexed { i, code ->
                    append("<numFmt numFmtId=\"").append(164 + i)
                    append("\" formatCode=\"").append(escapeAttribute(code)).append("\"/>")
                }
                append("</numFmts>")
            }

            append("<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>")
            append("<cellXfs count=\"").append(formats.size).append("\">")
            formats.forEachIndexed { i, format ->
                append("<xf numFmtId=\"").append(numberFormatId(format, customFormats))
                append("\" fontId=\"").append(i)
                append("\" fillId=\"").append(i + 2)
                append("\" borderId=\"").append(borderIndex[format.borders] ?: 0).append("\" xfId=\"0\"")
                append(" applyFont=\"1\" applyFill=\"1\"")
                if (!format.borders.isEmpty) append(" applyBorder=\"1\"")
                if (format.numberFormat != null) append(" applyNumberFormat=\"1\"")
                if (format.alignment != CellAlignment.GENERAL) {
                    append(" applyAlignment=\"1\"><alignment horizontal=\"")
                    append(
                        when (format.alignment) {
                            CellAlignment.LEFT -> "left"
                            CellAlignment.CENTER -> "center"
                            CellAlignment.RIGHT -> "right"
                            CellAlignment.GENERAL -> "general"
                        },
                    )
                    append("\"/></xf>")
                } else {
                    append("/>")
                }
            }
            append("</cellXfs>")
            append("<cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles>")
            append("</styleSheet>")
        }

        private fun numberFormatId(format: CellFormat, custom: List<String>): Int {
            val code = format.numberFormat ?: return 0
            val customIndex = custom.indexOf(code)
            if (customIndex >= 0) return 164 + customIndex
            return builtinNumberFormatId(code)
        }

        /** The XML for one edge: a style attribute, or nothing for a plain edge. */
        private fun borderStyle(style: BorderStyle): String = when (style) {
            BorderStyle.NONE -> ""
            BorderStyle.THIN -> " style=\"thin\""
            BorderStyle.MEDIUM -> " style=\"medium\""
            BorderStyle.THICK -> " style=\"thick\""
            BorderStyle.DASHED -> " style=\"dashed\""
            BorderStyle.DOTTED -> " style=\"dotted\""
        }

        private fun builtinNumberFormatId(code: String): Int = when (code) {
            "0" -> 1
            "0.00" -> 2
            "#,##0" -> 3
            "#,##0.00" -> 4
            "0%" -> 9
            "0.00%" -> 10
            "0.00E+00" -> 11
            "m/d/yyyy" -> 14
            "h:mm" -> 20
            "h:mm:ss" -> 21
            "@" -> 49
            else -> 0
        }

        companion object {
            fun from(sheets: List<Sheet>): StylePlan {
                // Index 0 is always the default format: a cell with s="0" and a
                // cell without s both mean "no direct formatting".
                val distinct = linkedSetOf(CellFormat())
                val distinctBorders = linkedSetOf(CellBorders())
                sheets.forEach { sheet ->
                    sheet.cells.forEach { cell ->
                        distinct += cell.format
                        distinctBorders += cell.format.borders
                    }
                }
                val formats = distinct.toList()
                val borderList = distinctBorders.toList()
                return StylePlan(
                    formats = formats,
                    index = formats.withIndex().associate { (i, f) -> f to i },
                    borders = borderList,
                    borderIndex = borderList.withIndex().associate { (i, b) -> b to i },
                )
            }
        }
    }

    private companion object {
        const val XML_HEADER = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
        const val PACKAGE_RELS_NAMESPACE =
            "http://schemas.openxmlformats.org/package/2006/relationships"
        const val DOCUMENT_RELATIONSHIPS_NAMESPACE =
            "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
        const val CONTENT_TYPES_NAMESPACE =
            "http://schemas.openxmlformats.org/package/2006/content-types"

        const val ROOT_RELS = XML_HEADER +
            "<Relationships xmlns=\"$PACKAGE_RELS_NAMESPACE\">" +
            "<Relationship Id=\"rId1\" Type=\"$DOCUMENT_RELATIONSHIPS_NAMESPACE/officeDocument\" " +
            "Target=\"xl/workbook.xml\"/></Relationships>"

        fun argb(rgb: Int): String =
            "FF" + (rgb and 0xFFFFFF).toString(16).uppercase().padStart(6, '0')

        fun formatNumber(number: Double): String {
            if (number.isFinite() && number == Math.floor(number) && Math.abs(number) < 1e15) {
                return number.toLong().toString()
            }
            return number.toString()
        }

        fun escapeText(text: String): String = text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")

        fun escapeAttribute(text: String): String = escapeText(text)
            .replace("\"", "&quot;")
            .replace("\n", "&#10;")
    }
}
