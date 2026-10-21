package org.ziyoffice.ooxml

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipInputStream
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import org.ziyoffice.model.BorderStyle
import org.ziyoffice.model.Cell
import org.ziyoffice.model.CellAlignment
import org.ziyoffice.model.CellBorders
import org.ziyoffice.model.CellFormat
import org.ziyoffice.model.CellRange
import org.ziyoffice.model.ColumnWidth
import org.ziyoffice.model.DataValidation
import org.ziyoffice.model.MAX_ROW_HEIGHT_POINTS
import org.ziyoffice.model.MIN_ROW_HEIGHT_POINTS
import org.ziyoffice.model.MIN_SHEET_COLUMNS
import org.ziyoffice.model.MIN_SHEET_ROWS
import org.ziyoffice.model.Sheet
import org.ziyoffice.model.Workbook
import org.ziyoffice.model.columnIndex
import org.ziyoffice.model.normaliseColumnWidths
import org.ziyoffice.model.normaliseMerges

/** The SpreadsheetML namespace, as defined by ECMA-376 / ISO 29500. */
const val SPREADSHEETML_NAMESPACE =
    "http://schemas.openxmlformats.org/spreadsheetml/2006/main"

/**
 * Reads an `.xlsx` package into the ZiyOffice [Workbook] model using only
 * platform APIs (`java.util.zip` + `org.xmlpull`). No Apache POI.
 *
 * Supported subset:
 *  - `workbook.xml`: sheet names, order and the active tab
 *  - `sharedStrings.xml`: the shared string table (rich runs flattened to text)
 *  - `worksheets/sheetN.xml`: cell values (numbers, strings, booleans, errors,
 *    inline strings), formulas (`<f>`), basic cell formatting (`s` -> `xf`
 *    -> `cellXfs`/`fonts`/`fills`/`numFmts`), frozen rows, merged ranges,
 *    column widths and row heights
 *
 * Unsupported content is skipped, never faked: styles beyond the basics,
 * conditional formatting, charts, pivot tables, images, comments.
 */
class XlsxReader(
    private val newParser: () -> XmlPullParser = {
        XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }.newPullParser()
    },
) {

    fun read(input: InputStream): Workbook {
        val parts = readParts(input)
        val workbookBytes = parts[WORKBOOK_PART]
            ?: throw IOException("$WORKBOOK_PART not found in the package")

        val sheets = parseWorkbookSheets(workbookBytes)
        val targets = parts[WORKBOOK_RELS_PART]?.let { parseWorkbookTargets(it) } ?: emptyMap()
        val sharedStrings = parts[SHARED_STRINGS_PART]?.let { parseSharedStrings(it) } ?: emptyList()
        val styles = parts[STYLES_PART]?.let { parseStyles(it) } ?: StyleTable.EMPTY

        val resolved = sheets.map { entry ->
            val path = targets[entry.relId] ?: entry.fallbackPath()
            val bytes = parts[path] ?: ByteArray(0)
            parseSheet(entry.name, bytes, sharedStrings, styles)
        }
        val active = parseActiveTab(workbookBytes).coerceIn(0, (resolved.size - 1).coerceAtLeast(0))
        return Workbook(sheets = resolved, activeSheet = active)
    }

    private fun parseWorkbookTargets(bytes: ByteArray): Map<String, String> {
        val parser = newParser()
        parser.setInput(ByteArrayInputStream(bytes), null)
        val targets = mutableMapOf<String, String>()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "Relationship") {
                val id = attribute(parser, "Id")
                val target = attribute(parser, "Target")
                if (id != null && target != null && target.startsWith("worksheets/")) {
                    targets[id] = "xl/$target"
                }
            }
            event = parser.next()
        }
        return targets
    }

    private fun readParts(input: InputStream): Map<String, ByteArray> {
        val parts = mutableMapOf<String, ByteArray>()
        var total = 0L
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name in WANTED_PARTS || entry.name.startsWith(SHEET_PREFIX)) {
                    val bytes = zip.readEntryCapped()
                    total += bytes.size
                    if (total > MAX_TOTAL_BYTES) {
                        throw IOException(
                            "Package exceeds the ${MAX_TOTAL_BYTES / (1024 * 1024)} MB safety limit",
                        )
                    }
                    parts[entry.name] = bytes
                }
            }
        }
        return parts
    }

    private fun ZipInputStream.readEntryCapped(): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var read = 0L
        while (true) {
            val n = read(buffer)
            if (n <= 0) break
            read += n
            if (read > MAX_ENTRY_BYTES) throw IOException("Part exceeds the entry size limit")
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }

    private data class SheetEntry(val name: String, val sheetId: String, val relId: String) {
        /** Last-resort part name when workbook.xml.rels is missing. */
        fun fallbackPath(): String = "$SHEET_PREFIX$sheetId.xml"
    }

    private fun parseWorkbookSheets(bytes: ByteArray): List<SheetEntry> {
        val parser = newParser()
        parser.setInput(ByteArrayInputStream(bytes), null)
        val sheets = mutableListOf<SheetEntry>()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.namespace == SPREADSHEETML_NAMESPACE &&
                parser.name == "sheet"
            ) {
                val name = attribute(parser, "name") ?: "Sheet${sheets.size + 1}"
                val sheetId = attribute(parser, "sheetId") ?: "${sheets.size + 1}"
                val relId = attribute(parser, "id", RELATIONSHIPS_NAMESPACE) ?: ""
                sheets += SheetEntry(name, sheetId, relId)
            }
            event = parser.next()
        }
        return sheets
    }

    private fun parseActiveTab(bytes: ByteArray): Int {
        val parser = newParser()
        parser.setInput(ByteArrayInputStream(bytes), null)
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.namespace == SPREADSHEETML_NAMESPACE &&
                parser.name == "workbookView"
            ) {
                return attribute(parser, "activeTab")?.toIntOrNull() ?: 0
            }
            event = parser.next()
        }
        return 0
    }

    private fun parseSharedStrings(bytes: ByteArray): List<String> {
        val parser = newParser()
        parser.setInput(ByteArrayInputStream(bytes), null)
        val strings = mutableListOf<String>()
        val current = StringBuilder()
        var inString = false
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when {
                event == XmlPullParser.START_TAG && parser.namespace == SPREADSHEETML_NAMESPACE &&
                    parser.name == "si" -> {
                    inString = true
                    current.setLength(0)
                }
                event == XmlPullParser.END_TAG && parser.namespace == SPREADSHEETML_NAMESPACE &&
                    parser.name == "si" -> {
                    strings += current.toString()
                    inString = false
                }
                event == XmlPullParser.TEXT && inString -> current.append(parser.text)
            }
            event = parser.next()
        }
        return strings
    }

    /** Indexed cell formats, resolved from `cellXfs` -> `fonts` / `fills` / `numFmts`. */
    private class StyleTable(val formats: List<CellFormat>) {
        fun formatAt(index: Int?): CellFormat =
            index?.let { formats.getOrNull(it) } ?: CellFormat()

        companion object {
            val EMPTY = StyleTable(emptyList())
        }
    }

    private fun parseStyles(bytes: ByteArray): StyleTable {
        val parser = newParser()
        parser.setInput(ByteArrayInputStream(bytes), null)

        val numberFormats = mutableMapOf<Int, String>()
        val fonts = mutableListOf<CellFormat>()
        val fills = mutableListOf<Int?>()
        val borders = mutableListOf<CellBorders>()
        val xfFontIds = mutableListOf<Int>()
        val xfFillIds = mutableListOf<Int>()
        val xfNumFmtIds = mutableListOf<Int>()
        val xfBorderIds = mutableListOf<Int>()
        val xfAlignments = mutableListOf<CellAlignment>()

        var section = ""
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (parser.namespace == SPREADSHEETML_NAMESPACE) {
                when {
                    // styleSheet (depth 1) holds numFmts/fonts/fills/borders/cellXfs (depth 2).
                    event == XmlPullParser.START_TAG && parser.depth == 2 -> section = parser.name
                    event == XmlPullParser.START_TAG -> when (section) {
                        "numFmts" -> if (parser.name == "numFmt") {
                            val id = attribute(parser, "numFmtId")?.toIntOrNull()
                            val code = attribute(parser, "formatCode")
                            if (id != null && code != null) numberFormats[id] = code
                        }
                        "fonts" -> if (parser.name == "font") fonts += parseFont(parser)
                        "fills" -> if (parser.name == "fill") fills += parseFillColor(parser)
                        "borders" -> if (parser.name == "border") borders += parseBorder(parser)
                        "cellXfs" -> if (parser.name == "xf") {
                            xfFontIds += attribute(parser, "fontId")?.toIntOrNull() ?: 0
                            xfFillIds += attribute(parser, "fillId")?.toIntOrNull() ?: 0
                            xfNumFmtIds += attribute(parser, "numFmtId")?.toIntOrNull() ?: 0
                            xfBorderIds += attribute(parser, "borderId")?.toIntOrNull() ?: 0
                            // Alignment lives in the child <alignment> element.
                            xfAlignments += parseXfAlignment(parser)
                        }
                    }
                }
            }
            event = parser.next()
        }

        val formats = xfFontIds.indices.map { i ->
            val font = fonts.getOrNull(xfFontIds.getOrElse(i) { 0 }) ?: CellFormat()
            val fill = fills.getOrNull(xfFillIds.getOrElse(i) { 0 })
            val numFmtId = xfNumFmtIds.getOrElse(i) { 0 }
            font.copy(
                fillRgb = fill,
                alignment = xfAlignments.getOrElse(i) { CellAlignment.GENERAL },
                numberFormat = builtinOrCustomFormat(numFmtId, numberFormats),
                borders = borders.getOrElse(xfBorderIds.getOrElse(i) { 0 }) { CellBorders() },
            )
        }
        return StyleTable(formats)
    }

    private fun parseFont(parser: XmlPullParser): CellFormat {
        var bold = false
        var italic = false
        var underline = false
        var strike = false
        var sizePt: Float? = null
        var colorRgb: Int? = null

        val depth = parser.depth
        var event = parser.next()
        while (!(event == XmlPullParser.END_TAG && parser.depth == depth)) {
            if (event == XmlPullParser.START_TAG && parser.namespace == SPREADSHEETML_NAMESPACE) {
                when (parser.name) {
                    "b" -> bold = onOff(parser)
                    "i" -> italic = onOff(parser)
                    "u" -> underline = true
                    "strike" -> strike = onOff(parser)
                    "sz" -> sizePt = attribute(parser, "val")?.toFloatOrNull()
                    "color" -> colorRgb = attribute(parser, "rgb")?.let(::parseRgb)
                }
            }
            event = parser.next()
        }
        return CellFormat(
            bold = bold,
            italic = italic,
            underline = underline,
            strike = strike,
            sizePt = sizePt,
            colorRgb = colorRgb,
        )
    }

    private fun parseBorder(parser: XmlPullParser): CellBorders {
        var top = BorderStyle.NONE
        var bottom = BorderStyle.NONE
        var left = BorderStyle.NONE
        var right = BorderStyle.NONE
        val depth = parser.depth
        var event = parser.next()
        while (!(event == XmlPullParser.END_TAG && parser.depth == depth)) {
            if (event == XmlPullParser.START_TAG && parser.namespace == SPREADSHEETML_NAMESPACE) {
                val style = borderStyleOf(attribute(parser, "style"))
                when (parser.name) {
                    "top" -> top = style
                    "bottom" -> bottom = style
                    "left" -> left = style
                    "right" -> right = style
                }
            }
            event = parser.next()
        }
        return CellBorders(top = top, bottom = bottom, left = left, right = right)
    }

    private fun borderStyleOf(style: String?): BorderStyle = when (style?.lowercase()) {
        "thin" -> BorderStyle.THIN
        "medium" -> BorderStyle.MEDIUM
        "thick" -> BorderStyle.THICK
        "dashed" -> BorderStyle.DASHED
        "dotted" -> BorderStyle.DOTTED
        else -> BorderStyle.NONE
    }

    private fun parseFillColor(parser: XmlPullParser): Int? {
        val depth = parser.depth
        var event = parser.next()
        var inPattern = false
        while (!(event == XmlPullParser.END_TAG && parser.depth == depth)) {
            if (event == XmlPullParser.START_TAG && parser.namespace == SPREADSHEETML_NAMESPACE) {
                when (parser.name) {
                    "patternFill" -> inPattern = true
                    "fgColor" -> if (inPattern) {
                        val rgb = attribute(parser, "rgb")?.let(::parseRgb)
                        if (rgb != null) return rgb
                    }
                }
            }
            event = parser.next()
        }
        return null
    }

    private fun parseXfAlignment(parser: XmlPullParser): CellAlignment {
        val depth = parser.depth
        var horizontal = attribute(parser, "horizontal")
        var event = parser.next()
        while (!(event == XmlPullParser.END_TAG && parser.depth == depth)) {
            if (event == XmlPullParser.START_TAG && parser.namespace == SPREADSHEETML_NAMESPACE &&
                parser.name == "alignment"
            ) {
                horizontal = attribute(parser, "horizontal") ?: horizontal
            }
            event = parser.next()
        }
        horizontal ?: return CellAlignment.GENERAL
        return when (horizontal) {
            "left" -> CellAlignment.LEFT
            "center", "centerContinuous" -> CellAlignment.CENTER
            "right" -> CellAlignment.RIGHT
            else -> CellAlignment.GENERAL
        }
    }

    private fun builtinOrCustomFormat(id: Int, custom: Map<Int, String>): String? {
        custom[id]?.let { return it }
        return when (id) {
            0 -> null
            1 -> "0"
            2 -> "0.00"
            3 -> "#,##0"
            4 -> "#,##0.00"
            9 -> "0%"
            10 -> "0.00%"
            11 -> "0.00E+00"
            14 -> "m/d/yyyy"
            15 -> "d-mmm-yy"
            16 -> "d-mmm"
            17 -> "mmm-yy"
            18 -> "h:mm AM/PM"
            19 -> "h:mm:ss AM/PM"
            20 -> "h:mm"
            21 -> "h:mm:ss"
            22 -> "m/d/yyyy h:mm"
            37 -> "#,##0 ;(#,##0)"
            38 -> "#,##0 ;[Red](#,##0)"
            39 -> "#,##0.00;(#,##0.00)"
            40 -> "#,##0.00;[Red](#,##0.00)"
            45 -> "mm:ss"
            46 -> "[h]:mm:ss"
            47 -> "mmss.0"
            48 -> "##0.0E+0"
            49 -> "@"
            else -> null
        }
    }

    private fun parseRgb(value: String): Int? {
        val hex = value.removePrefix("#")
        if (hex.length == 8) {
            // ARGB; drop alpha.
            return hex.substring(2).toIntOrNull(16)
        }
        if (hex.length == 6) return hex.toIntOrNull(16)
        return null
    }

    private fun onOff(parser: XmlPullParser): Boolean {
        val value = attribute(parser, "val")
        return value == null || value == "1" || value.equals("true", ignoreCase = true)
    }

    private fun parseSheet(
        name: String,
        bytes: ByteArray,
        sharedStrings: List<String>,
        styles: StyleTable,
    ): Sheet {
        val cells = mutableListOf<Cell>()
        val mergedRanges = mutableListOf<CellRange>()
        val columnWidths = mutableListOf<ColumnWidth>()
        val rowHeights = mutableMapOf<Int, Float>()
        val validations = mutableListOf<DataValidation>()
        var rowCount = 0
        var columnCount = 0
        var frozenRows = 0
        if (bytes.isEmpty()) {
            return Sheet(name = name, cells = emptyList(), rowCount = MIN_SHEET_ROWS, columnCount = MIN_SHEET_COLUMNS)
        }

        val parser = newParser()
        parser.setInput(ByteArrayInputStream(bytes), null)

        var cellRef: String? = null
        var cellType: String? = null
        var styleIndex: Int? = null
        var value = StringBuilder()
        var formula: StringBuilder? = null
        var inValue = false
        var inFormula = false
        var validationRef: String? = null
        var validationType: String? = null
        var validationFormula: StringBuilder? = null
        var inValidationFormula = false

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            // Text events carry no namespace, so they are handled before the guard.
            if (event == XmlPullParser.TEXT && inValue) {
                value.append(parser.text)
                event = parser.next()
                continue
            }
            if (event == XmlPullParser.TEXT && inFormula) {
                formula?.append(parser.text)
                event = parser.next()
                continue
            }
            if (event == XmlPullParser.TEXT && inValidationFormula) {
                validationFormula?.append(parser.text)
                event = parser.next()
                continue
            }
            if (parser.namespace == SPREADSHEETML_NAMESPACE) {
                when {
                    event == XmlPullParser.START_TAG && parser.name == "pane" -> {
                        // Only a frozen top row block is meaningful here; a split pane
                        // that is not "frozen" is a scroll position, not a freeze.
                        if (attribute(parser, "state") == "frozen") {
                            frozenRows = attribute(parser, "ySplit")?.toIntOrNull() ?: 0
                        }
                    }
                    event == XmlPullParser.START_TAG && parser.name == "col" -> {
                        val first = attribute(parser, "min")?.toIntOrNull()?.minus(1)
                        val last = attribute(parser, "max")?.toIntOrNull()?.minus(1)
                        val width = attribute(parser, "width")?.toFloatOrNull()
                        if (first != null && last != null && width != null) {
                            columnWidths += ColumnWidth(first, last, width)
                        }
                    }
                    event == XmlPullParser.START_TAG && parser.name == "row" -> {
                        val rowIndex = attribute(parser, "r")?.toIntOrNull()
                        if (rowIndex != null) {
                            rowCount = maxOf(rowCount, rowIndex)
                            attribute(parser, "ht")?.toFloatOrNull()?.let { height ->
                                if (rowIndex > 0 && height > 0f) {
                                    rowHeights[rowIndex - 1] = height.coerceIn(
                                        MIN_ROW_HEIGHT_POINTS,
                                        MAX_ROW_HEIGHT_POINTS,
                                    )
                                }
                            }
                        }
                    }
                    event == XmlPullParser.START_TAG && parser.name == "dataValidation" -> {
                        validationRef = attribute(parser, "sqref")
                        validationType = attribute(parser, "type")
                        validationFormula = null
                    }
                    event == XmlPullParser.START_TAG && parser.name == "formula1" -> {
                        inValidationFormula = true
                        validationFormula = StringBuilder()
                    }
                    event == XmlPullParser.END_TAG && parser.name == "formula1" ->
                        inValidationFormula = false
                    event == XmlPullParser.END_TAG && parser.name == "dataValidation" -> {
                        val range = validationRef
                            ?.split(" ")
                            ?.firstOrNull()
                            ?.let(CellRange::parse)
                        val text = validationFormula?.toString().orEmpty()
                        // Only a plain list of values survives; a rule that points
                        // at another range is read but not kept.
                        if (
                            validationType == "list" &&
                            range != null &&
                            text.length >= 2 &&
                            text.startsWith("\"") &&
                            text.endsWith("\"")
                        ) {
                            val values = text.substring(1, text.length - 1)
                                .split(",")
                                .map { it.trim() }
                                .filter { it.isNotEmpty() }
                            if (values.isNotEmpty()) {
                                validations += DataValidation(range, values)
                                rowCount = maxOf(rowCount, range.bottom + 1)
                                columnCount = maxOf(columnCount, range.right + 1)
                            }
                        }
                        validationRef = null
                        validationType = null
                        validationFormula = null
                    }
                    event == XmlPullParser.START_TAG && parser.name == "mergeCell" -> {
                        val range = attribute(parser, "ref")?.let(CellRange::parse)
                        if (range != null) {
                            mergedRanges += range
                            rowCount = maxOf(rowCount, range.bottom + 1)
                            columnCount = maxOf(columnCount, range.right + 1)
                        }
                    }
                    event == XmlPullParser.START_TAG && parser.name == "c" -> {
                        cellRef = attribute(parser, "r")
                        cellType = attribute(parser, "t")
                        styleIndex = attribute(parser, "s")?.toIntOrNull()
                        value.setLength(0)
                        formula = null
                    }
                    event == XmlPullParser.START_TAG && parser.name == "v" -> inValue = true
                    // Inline strings store their text in <is><t> rather than <v>.
                    event == XmlPullParser.START_TAG && parser.name == "t" && cellType == "inlineStr" ->
                        inValue = true
                    event == XmlPullParser.START_TAG && parser.name == "f" -> {
                        inFormula = true
                        formula = StringBuilder()
                    }
                    event == XmlPullParser.END_TAG && parser.name == "v" -> inValue = false
                    event == XmlPullParser.END_TAG && parser.name == "t" && cellType == "inlineStr" ->
                        inValue = false
                    event == XmlPullParser.END_TAG && parser.name == "f" -> inFormula = false
                    event == XmlPullParser.END_TAG && parser.name == "c" -> {
                        val ref = cellRef
                        if (ref != null) {
                            val (row, column) = parseCellRef(ref)
                            val raw = buildRawValue(cellType, value.toString(), sharedStrings, formula?.toString())
                            val format = styles.formatAt(styleIndex)
                            if (raw.isNotEmpty() || format != CellFormat()) {
                                cells += Cell(
                                    row = row,
                                    column = column,
                                    value = raw,
                                    format = format,
                                )
                                rowCount = maxOf(rowCount, row + 1)
                                columnCount = maxOf(columnCount, column + 1)
                            }
                        }
                        cellRef = null
                        cellType = null
                        styleIndex = null
                    }
                }
            }
            event = parser.next()
        }

        return Sheet(
            name = name,
            cells = cells,
            rowCount = maxOf(rowCount, MIN_SHEET_ROWS),
            columnCount = maxOf(columnCount, MIN_SHEET_COLUMNS),
            frozenRows = frozenRows.coerceAtMost(rowCount),
            mergedRanges = mergedRanges.normaliseMerges(),
            columnWidths = columnWidths.normaliseColumnWidths(),
            rowHeights = rowHeights,
            validations = validations,
        )
    }

    private fun buildRawValue(
        type: String?,
        value: String,
        sharedStrings: List<String>,
        formula: String?,
    ): String {
        if (formula != null) return "=$formula"
        return when (type) {
            "s" -> value.toIntOrNull()?.let { sharedStrings.getOrNull(it) } ?: ""
            "inlineStr" -> value
            "b" -> if (value == "1") "TRUE" else "FALSE"
            "str" -> value
            "e" -> value
            else -> value
        }
    }

    /** "B12" -> (11, 1), 0-based. */
    private fun parseCellRef(ref: String): Pair<Int, Int> {
        val letters = ref.takeWhile { it.isLetter() }
        val digits = ref.dropWhile { it.isLetter() }
        val column = columnIndex(letters).coerceAtLeast(0)
        val row = (digits.toIntOrNull() ?: 1) - 1
        return row.coerceAtLeast(0) to column
    }

    private fun attribute(parser: XmlPullParser, name: String, namespace: String? = null): String? =
        if (namespace == null) parser.getAttributeValue(null, name)
        else parser.getAttributeValue(namespace, name)

    private companion object {
        const val WORKBOOK_PART = "xl/workbook.xml"
        const val WORKBOOK_RELS_PART = "xl/_rels/workbook.xml.rels"
        const val SHARED_STRINGS_PART = "xl/sharedStrings.xml"
        const val STYLES_PART = "xl/styles.xml"
        const val SHEET_PREFIX = "xl/worksheets/sheet"
        const val RELATIONSHIPS_NAMESPACE =
            "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
        const val MAX_ENTRY_BYTES = 64L * 1024 * 1024
        const val MAX_TOTAL_BYTES = 256L * 1024 * 1024
        val WANTED_PARTS = setOf(
            WORKBOOK_PART,
            WORKBOOK_RELS_PART,
            SHARED_STRINGS_PART,
            STYLES_PART,
        )
    }
}
