package org.ziyoffice.ooxml

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipInputStream
import org.ziyoffice.model.BorderStyle
import org.ziyoffice.model.Cell
import org.ziyoffice.model.CellAlignment
import org.ziyoffice.model.CellBorders
import org.ziyoffice.model.CellFormat
import org.ziyoffice.model.CellRange
import org.ziyoffice.model.NamedRange
import org.ziyoffice.model.ColumnWidth
import org.ziyoffice.model.DataValidation
import org.ziyoffice.model.MIN_SHEET_COLUMNS
import org.ziyoffice.model.MIN_SHEET_ROWS
import org.ziyoffice.model.Sheet
import org.ziyoffice.model.Workbook
import org.ziyoffice.model.columnName
import org.ziyoffice.ooxml.xml.XmlElement
import org.ziyoffice.ooxml.xml.XmlScanner
import org.ziyoffice.ooxml.xml.escapeXmlAttribute
import org.ziyoffice.ooxml.xml.escapeXmlText

/**
 * An `.xlsx` package opened for editing, plus a snapshot of the worksheet XML
 * the model came from.
 *
 * Same reasoning as [DocxSource]: rebuilding a workbook from the model throws
 * away the charts, drawings, images, pivot tables, comments, conditional
 * formatting, filters, protections, hidden rows and columns and named ranges
 * that the model cannot hold. Keeping the original package means a save can
 * leave all of that alone and change only the cells the user touched.
 */
class XlsxSource internal constructor(
    val parts: Map<String, ByteArray>,
    val workbook: Workbook,
    internal val sheets: List<XlsxSheetSource>,
    internal val styles: XlsxStyles,
    internal val workbookXml: String,
    internal val workbookRoot: XmlElement,
) {
    companion object {
        fun read(input: InputStream): XlsxSource = XlsxSourceReader().read(input)

        fun capture(bytes: ByteArray): XlsxSource = read(bytes.inputStream())
    }
}

/** One worksheet of a source workbook. */
internal class XlsxSheetSource internal constructor(
    /** The package part the worksheet lives in, e.g. `xl/worksheets/sheet1.xml`. */
    val partName: String,
    val relId: String,
    val sheetId: String,
    val name: String,
    val xml: String,
    val root: XmlElement,
    /** The style index each cell carries, keyed by row and column. */
    val styleIndexes: Map<Pair<Int, Int>, Int>,
    /** The formatting each cell resolved to, keyed by row and column. */
    val formats: Map<Pair<Int, Int>, CellFormat>,
    val sheetData: XmlElement?,
    val cols: XmlElement?,
    val mergeCells: XmlElement?,
    val dataValidations: XmlElement?,
    val sheetViews: XmlElement?,
    /** The row elements, keyed by their 0-based row index. */
    val rows: Map<Int, XmlElement>,
    val cells: Map<Pair<Int, Int>, XmlElement>,
    val formulas: Map<Pair<Int, Int>, String>,
    val cachedValues: Map<Pair<Int, Int>, String>,
    val model: Sheet,
)

/**
 * The style sheet of a workbook, kept so new formatting can be added to it
 * rather than replacing it. Named styles, theme colours, differential formats
 * and every style the workbook already declares stay exactly as they were. A
 * format the user applies to a cell is added as a new entry, and the cell
 * points at that.
 */
class XlsxStyles internal constructor(
    val xml: String,
    internal val root: XmlElement,
) {
    private val numFmts = mutableMapOf<Int, String>()
    private val fonts = mutableListOf<FontEntry>()
    private val fills = mutableListOf<FillEntry>()
    private val borders = mutableListOf<BorderEntry>()
    private val cellXfs = mutableListOf<XfEntry>()
    private var nextNumFmtId = 164
    private val addedNumFmts = mutableListOf<String>()
    private val addedFonts = mutableListOf<String>()
    private val addedFills = mutableListOf<String>()
    private val addedBorders = mutableListOf<String>()
    private val addedXfs = mutableListOf<String>()

    init {
        root.child("numFmts")?.childrenNamed("numFmt")?.forEach { element ->
            val id = element.attr("numFmtId")?.toIntOrNull() ?: return@forEach
            val code = element.attr("formatCode") ?: return@forEach
            numFmts[id] = code
            if (id >= nextNumFmtId) nextNumFmtId = id + 1
        }
        root.child("fonts")?.childrenNamed("font")?.forEach { fonts += readFont(it) }
        root.child("fills")?.childrenNamed("fill")?.forEach { fills += readFill(it) }
        root.child("borders")?.childrenNamed("border")?.forEach { borders += readBorder(it) }
        root.child("cellXfs")?.childrenNamed("xf")?.forEach { element ->
            cellXfs += XfEntry(
                fontId = element.attr("fontId")?.toIntOrNull() ?: 0,
                fillId = element.attr("fillId")?.toIntOrNull() ?: 0,
                borderId = element.attr("borderId")?.toIntOrNull() ?: 0,
                numFmtId = element.attr("numFmtId")?.toIntOrNull() ?: 0,
                alignment = readAlignment(element),
            )
        }
    }

    fun formatAt(index: Int?): CellFormat {
        if (index == null) return CellFormat()
        val xf = cellXfs.getOrNull(index) ?: return CellFormat()
        val font = fonts.getOrNull(xf.fontId) ?: FontEntry(CellFormat())
        val fill = fills.getOrNull(xf.fillId) ?: FillEntry(null)
        val border = borders.getOrNull(xf.borderId) ?: BorderEntry(CellBorders())
        val numberFormat = numFmts[xf.numFmtId] ?: builtinNumberFormatCode(xf.numFmtId)
        return CellFormat(
            bold = font.bold,
            italic = font.italic,
            underline = font.underline,
            strike = font.strike,
            sizePt = font.sizePt,
            colorRgb = font.colorRgb,
            fillRgb = fill.rgb,
            alignment = xf.alignment,
            numberFormat = numberFormat,
            borders = border.borders,
        )
    }

    private fun readFont(element: XmlElement): FontEntry {
        var bold = false
        var italic = false
        var underline = false
        var strike = false
        var sizePt: Float? = null
        var colorRgb: Int? = null
        element.children.forEach { child ->
            when (child.localName) {
                "b" -> bold = onOff(child)
                "i" -> italic = onOff(child)
                "u" -> underline = true
                "strike" -> strike = onOff(child)
                "sz" -> sizePt = child.attr("val")?.toFloatOrNull()
                "color" -> colorRgb = child.attr("rgb")?.let(::parseRgb)
            }
        }
        return FontEntry(bold, italic, underline, strike, sizePt, colorRgb)
    }

    private fun readFill(element: XmlElement): FillEntry {
        val pattern = element.child("patternFill") ?: return FillEntry(null)
        val rgb = pattern.child("fgColor")?.attr("rgb")?.let(::parseRgb)
        return FillEntry(rgb)
    }

    private fun readBorder(element: XmlElement): BorderEntry = BorderEntry(
        CellBorders(
            top = borderStyle(element.child("top")?.attr("style")),
            bottom = borderStyle(element.child("bottom")?.attr("style")),
            left = borderStyle(element.child("left")?.attr("style")),
            right = borderStyle(element.child("right")?.attr("style")),
        ),
    )

    private fun readAlignment(element: XmlElement): CellAlignment {
        val horizontal = element.child("alignment")?.attr("horizontal")
            ?: element.attr("horizontal")
            ?: return CellAlignment.GENERAL
        return when (horizontal) {
            "left" -> CellAlignment.LEFT
            "center", "centerContinuous" -> CellAlignment.CENTER
            "right" -> CellAlignment.RIGHT
            else -> CellAlignment.GENERAL
        }
    }

    /** The index of the cell format, adding it to the sheet when it is new. */
    fun indexFor(format: CellFormat): Int {
        val fontId = fontId(format)
        val fillId = fillId(format)
        val borderId = borderId(format)
        val numFmtId = numFmtId(format.numberFormat)
        val xf = XfEntry(fontId, fillId, borderId, numFmtId, format.alignment)
        val existing = cellXfs.indexOfFirst { it.sameAs(xf) }
        if (existing >= 0) return existing
        val index = cellXfs.size
        cellXfs += xf
        addedXfs += xf.toXml()
        return index
    }

    fun toXml(): String {
        var text = xml
        text = insertBefore(text, "</fonts>", addedFonts, "fonts")
        text = insertBefore(text, "</fills>", addedFills, "fills")
        text = insertBefore(text, "</borders>", addedBorders, "borders")
        text = insertBefore(text, "</cellXfs>", addedXfs, "cellXfs")
        if (addedNumFmts.isNotEmpty()) {
            val block = addedNumFmts.joinToString("")
            if (text.contains("</numFmts>")) {
                text = insertBefore(text, "</numFmts>", addedNumFmts, "numFmts")
            } else {
                val index = text.indexOf("<fonts")
                val at = if (index >= 0) index else text.indexOf("<cellXfs")
                if (at >= 0) {
                    text = text.substring(0, at) +
                        "<numFmts count=\"${addedNumFmts.size}\">$block</numFmts>" +
                        text.substring(at)
                }
            }
        }
        return text
    }

    /**
     * Puts [block] in front of [marker] and updates the count the parent
     * element declares, so the style sheet stays self-describing.
     */
    private fun insertBefore(
        text: String,
        marker: String,
        block: List<String>,
        parent: String,
    ): String {
        if (block.isEmpty()) return text
        val index = text.lastIndexOf(marker)
        if (index < 0) return text
        val withBlock = text.substring(0, index) + block.joinToString("") + text.substring(index)
        return updateCount(withBlock, parent, block.size)
    }

    private fun updateCount(text: String, name: String, added: Int): String {
        val pattern = Regex("<$name\\b[^>]*\\bcount=\"(\\d+)\"")
        val match = pattern.find(text) ?: return text
        val current = match.groupValues[1].toIntOrNull() ?: return text
        val replacement = "<$name"
        val start = match.range.first
        val openTag = text.substring(start, match.range.last + 1)
        val updated = openTag.replace(
            "count=\"$current\"",
            "count=\"${current + added}\"",
        )
        return text.substring(0, start) + updated + text.substring(match.range.last + 1)
    }

    private fun fontId(format: CellFormat): Int {
        val entry = FontEntry(format)
        val existing = fonts.indexOfFirst { it.sameAs(entry) }
        if (existing >= 0) return existing
        val id = fonts.size
        fonts += entry
        addedFonts += entry.toXml()
        return id
    }

    private fun fillId(format: CellFormat): Int {
        val entry = FillEntry(format.fillRgb)
        val existing = fills.indexOfFirst { it.sameAs(entry) }
        if (existing >= 0) return existing
        val id = fills.size
        fills += entry
        addedFills += entry.toXml()
        return id
    }

    private fun borderId(format: CellFormat): Int {
        val entry = BorderEntry(format.borders)
        val existing = borders.indexOfFirst { it.sameAs(entry) }
        if (existing >= 0) return existing
        val id = borders.size
        borders += entry
        addedBorders += entry.toXml()
        return id
    }

    private fun numFmtId(code: String?): Int {
        if (code == null) return 0
        numFmts.forEach { (id, existing) -> if (existing == code) return id }
        val builtin = builtinNumberFormatId(code)
        if (builtin != 0) return builtin
        val id = nextNumFmtId++
        numFmts[id] = code
        addedNumFmts += "<numFmt numFmtId=\"$id\" formatCode=\"${escapeXmlAttribute(code)}\"/>"
        return id
    }

    private data class FontEntry(
        val bold: Boolean,
        val italic: Boolean,
        val underline: Boolean,
        val strike: Boolean,
        val sizePt: Float?,
        val colorRgb: Int?,
    ) {
        constructor(format: CellFormat) : this(
            format.bold,
            format.italic,
            format.underline,
            format.strike,
            format.sizePt,
            format.colorRgb,
        )

        fun sameAs(other: FontEntry): Boolean =
            bold == other.bold &&
                italic == other.italic &&
                underline == other.underline &&
                strike == other.strike &&
                sizePt == other.sizePt &&
                colorRgb == other.colorRgb

        fun toXml(): String = buildString {
            append("<font>")
            if (bold) append("<b/>")
            if (italic) append("<i/>")
            if (underline) append("<u/>")
            if (strike) append("<strike/>")
            sizePt?.let { append("<sz val=\"$it\"/>") }
            colorRgb?.let { append("<color rgb=\"FF${hex(it)}\"/>") }
            append("</font>")
        }
    }

    private data class FillEntry(val rgb: Int?) {
        fun sameAs(other: FillEntry): Boolean = rgb == other.rgb

        fun toXml(): String = buildString {
            append("<fill><patternFill patternType=\"solid\">")
            rgb?.let { append("<fgColor rgb=\"FF${hex(it)}\"/>") }
            append("<bgColor indexed=\"64\"/></patternFill></fill>")
        }
    }

    private data class BorderEntry(val borders: CellBorders) {
        fun sameAs(other: BorderEntry): Boolean = borders == other.borders

        fun toXml(): String = buildString {
            append("<border>")
            append("<left${edge(borders.left)}/>")
            append("<right${edge(borders.right)}/>")
            append("<top${edge(borders.top)}/>")
            append("<bottom${edge(borders.bottom)}/>")
            append("<diagonal/>")
            append("</border>")
        }

        private fun edge(style: BorderStyle): String = when (style) {
            BorderStyle.NONE -> ""
            BorderStyle.THIN -> " style=\"thin\""
            BorderStyle.MEDIUM -> " style=\"medium\""
            BorderStyle.THICK -> " style=\"thick\""
            BorderStyle.DASHED -> " style=\"dashed\""
            BorderStyle.DOTTED -> " style=\"dotted\""
        }
    }

    private data class XfEntry(
        val fontId: Int,
        val fillId: Int,
        val borderId: Int,
        val numFmtId: Int,
        val alignment: CellAlignment,
    ) {
        fun sameAs(other: XfEntry): Boolean =
            fontId == other.fontId &&
                fillId == other.fillId &&
                borderId == other.borderId &&
                numFmtId == other.numFmtId &&
                alignment == other.alignment

        fun toXml(): String = buildString {
            append("<xf numFmtId=\"$numFmtId\" fontId=\"$fontId\" fillId=\"$fillId\" ")
            append("borderId=\"$borderId\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\"")
            if (borderId != 0) append(" applyBorder=\"1\"")
            if (numFmtId != 0) append(" applyNumberFormat=\"1\"")
            if (alignment != CellAlignment.GENERAL) {
                append(" applyAlignment=\"1\"><alignment horizontal=\"")
                append(
                    when (alignment) {
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
    }

    private companion object {
        fun hex(rgb: Int): String = (rgb and 0xFFFFFF).toString(16).uppercase().padStart(6, '0')

        fun onOff(element: XmlElement): Boolean {
            val value = element.attr("val")
            return value == null || value == "1" || value.equals("true", ignoreCase = true)
        }

        fun parseRgb(value: String): Int? {
            val hex = value.removePrefix("#")
            if (hex.length == 8) return hex.substring(2).toIntOrNull(16)
            if (hex.length == 6) return hex.toIntOrNull(16)
            return null
        }

        fun borderStyle(style: String?): BorderStyle = when (style?.lowercase()) {
            "thin" -> BorderStyle.THIN
            "medium" -> BorderStyle.MEDIUM
            "thick" -> BorderStyle.THICK
            "dashed" -> BorderStyle.DASHED
            "dotted" -> BorderStyle.DOTTED
            else -> BorderStyle.NONE
        }

        fun builtinNumberFormatCode(id: Int): String? = when (id) {
            1 -> "0"
            2 -> "0.00"
            3 -> "#,##0"
            4 -> "#,##0.00"
            9 -> "0%"
            10 -> "0.00%"
            11 -> "0.00E+00"
            14 -> "m/d/yyyy"
            20 -> "h:mm"
            21 -> "h:mm:ss"
            49 -> "@"
            else -> null
        }

        fun builtinNumberFormatId(code: String): Int = when (code) {
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
    }
}

internal class XlsxSourceReader {

    fun read(input: InputStream): XlsxSource {
        val parts = readParts(input)
        val workbookBytes = parts[WORKBOOK_PART]
            ?: throw IOException("$WORKBOOK_PART not found in the package")
        val workbookXml = workbookBytes.decodeToString()
        val workbookRoot = XmlScanner.parse(workbookXml).root
        val stylesXml = parts[STYLES_PART]?.decodeToString() ?: "<styleSheet/>"
        val styles = XlsxStyles(stylesXml, XmlScanner.parse(stylesXml).root)
        val rels = parts[WORKBOOK_RELS_PART]?.let { parseRelationships(it) } ?: emptyMap()
        val sharedStrings = parts[SHARED_STRINGS_PART]?.let { parseSharedStrings(it) } ?: emptyList()

        val sheetEntries = parseSheetEntries(workbookRoot)
        val sheets = sheetEntries.map { entry ->
            val partName = rels[entry.relId]?.let { resolveWorkbookTarget(it.target) }
                ?: "$SHEET_PREFIX${entry.sheetId}.xml"
            val bytes = parts[partName] ?: ByteArray(0)
            parseSheet(entry, partName, bytes.decodeToString(), sharedStrings, styles)
        }
        val active = parseActiveTab(workbookRoot).coerceIn(0, (sheets.size - 1).coerceAtLeast(0))
        val namedRanges = parseDefinedNames(workbookRoot, sheetEntries, workbookXml)
        return XlsxSource(
            parts = parts,
            workbook = Workbook(
                sheets = sheets.map { it.model },
                activeSheet = active,
                namedRanges = namedRanges,
            ),
            sheets = sheets,
            styles = styles,
            workbookXml = workbookXml,
            workbookRoot = workbookRoot,
        )
    }

    /**
     * The names a formula can use.
     *
     * A name points at a range on a sheet: "Sheet1!$A$1:$B$40". A name
     * that points somewhere else, or at nothing, is left out: a formula that
     * used it would fail, and a name the file itself does not understand is
     * not ZiyOffice's to guess at.
     */
    private fun parseDefinedNames(
        root: XmlElement,
        sheetEntries: List<SheetEntry>,
        workbookXml: String,
    ): List<NamedRange> {
        val names = root.child("definedNames") ?: return emptyList()
        val found = mutableListOf<NamedRange>()
        names.childrenNamed("definedName").forEach { element ->
            val name = element.attr("name")?.trim().orEmpty()
            val refersTo = element.innerSource(workbookXml).trim()
            if (name.isEmpty() || refersTo.isEmpty()) return@forEach
            val sheetIndex = sheetIndexOf(refersTo, sheetEntries)
            val range = rangeOf(refersTo) ?: return@forEach
            found += NamedRange(name = name, range = range, sheetIndex = sheetIndex)
        }
        return found
    }

    /** The sheet a reference names, or -1 when it names none. */
    private fun sheetIndexOf(
        reference: String,
        sheetEntries: List<SheetEntry>,
    ): Int {
        val bang = reference.indexOf('!')
        if (bang < 0) return -1
        val sheetName = reference.substring(0, bang).trim().removeSurrounding("'")
        return sheetEntries.indexOfFirst { it.name == sheetName }
    }

    /** The range a reference points at. */
    private fun rangeOf(reference: String): CellRange? {
        val bang = reference.indexOf('!')
        val cells = (if (bang < 0) reference else reference.substring(bang + 1)).replace("$", "")
        val parts = cells.split(':')
        val first = CellRange.parse("${parts[0]}:${parts[0]}") ?: return null
        if (parts.size == 1) return first
        val second = CellRange.parse("${parts[1]}:${parts[1]}") ?: return null
        return CellRange(
            top = minOf(first.top, second.top),
            left = minOf(first.left, second.left),
            bottom = maxOf(first.bottom, second.bottom),
            right = maxOf(first.right, second.right),
        )
    }

    private fun readParts(input: InputStream): Map<String, ByteArray> {
        val parts = linkedMapOf<String, ByteArray>()
        var total = 0L
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
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
        return parts
    }

    private fun ZipInputStream.readEntryCapped(): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val read = read(buffer)
            if (read <= 0) break
            if (out.size() + read > MAX_ENTRY_BYTES) throw IOException("Part exceeds the entry size limit")
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    private data class SheetEntry(val name: String, val sheetId: String, val relId: String)

    private fun parseSheetEntries(root: XmlElement): List<SheetEntry> {
        val entries = mutableListOf<SheetEntry>()
        root.child("sheets")?.childrenNamed("sheet")?.forEach { element ->
            val name = element.attr("name") ?: "Sheet${entries.size + 1}"
            val sheetId = element.attr("sheetId") ?: "${entries.size + 1}"
            val relId = element.attr(RELATIONSHIPS_NAMESPACE, "id")
                ?: element.attr(null, "id")
                ?: ""
            entries += SheetEntry(name, sheetId, relId)
        }
        return entries
    }

    private fun parseActiveTab(root: XmlElement): Int =
        root.child("bookViews")?.child("workbookView")?.attr("activeTab")?.toIntOrNull() ?: 0

    private fun parseSharedStrings(bytes: ByteArray): List<String> {
        val root = XmlScanner.parse(bytes.decodeToString()).root
        return root.childrenNamed("si").map { it.text }
    }

    private fun parseSheet(
        entry: SheetEntry,
        partName: String,
        xml: String,
        sharedStrings: List<String>,
        styles: XlsxStyles,
    ): XlsxSheetSource {
        val root = XmlScanner.parse(xml).root
        val sheetData = root.child("sheetData")
        val styleIndexes = mutableMapOf<Pair<Int, Int>, Int>()
        val formats = mutableMapOf<Pair<Int, Int>, CellFormat>()
        val rows = mutableMapOf<Int, XmlElement>()
        val cells = mutableMapOf<Pair<Int, Int>, XmlElement>()
        val formulas = mutableMapOf<Pair<Int, Int>, String>()
        val cachedValues = mutableMapOf<Pair<Int, Int>, String>()
        val rowHeights = mutableMapOf<Int, Float>()

        sheetData?.childrenNamed("row")?.forEach { row ->
            val rowIndex = row.attr("r")?.toIntOrNull()?.minus(1) ?: return@forEach
            rows[rowIndex] = row
            row.attr("ht")?.toFloatOrNull()?.let { height ->
                if (height > 0f) rowHeights[rowIndex] = height
            }
            row.childrenNamed("c").forEach { cell ->
                val ref = cell.attr("r") ?: return@forEach
                val position = parseCellRef(ref)
                cells[position] = cell
                cell.attr("s")?.toIntOrNull()?.let { styleIndexes[position] = it }
                formats[position] = styles.formatAt(cell.attr("s")?.toIntOrNull())
                cell.child("f")?.let { formulas[position] = it.text }
                cell.child("v")?.let { cachedValues[position] = it.text }
            }
        }

        var frozenRows = 0
        root.child("sheetViews")?.child("sheetView")?.child("pane")?.let { pane ->
            if (pane.attr("state") == "frozen") {
                frozenRows = pane.attr("ySplit")?.toIntOrNull() ?: 0
            }
        }
        val mergedRanges = root.child("mergeCells")?.childrenNamed("mergeCell")
            ?.mapNotNull { it.attr("ref")?.let(CellRange::parse) }
            .orEmpty()
        val columnWidths = root.child("cols")?.childrenNamed("col")?.mapNotNull { col ->
            val first = col.attr("min")?.toIntOrNull()?.minus(1) ?: return@mapNotNull null
            val last = col.attr("max")?.toIntOrNull()?.minus(1) ?: return@mapNotNull null
            val width = col.attr("width")?.toFloatOrNull() ?: return@mapNotNull null
            ColumnWidth(first, last, width)
        }.orEmpty()
        val validations = root.child("dataValidations")?.childrenNamed("dataValidation")
            ?.mapNotNull { validation ->
                val range = validation.attr("sqref")?.split(" ")?.firstOrNull()
                    ?.let(::parseRange) ?: return@mapNotNull null
                val values = validation.child("formula1")?.text?.trim('"')?.split(",")
                    ?.map { it.trim() }?.filter { it.isNotEmpty() } ?: return@mapNotNull null
                if (values.isEmpty()) return@mapNotNull null
                DataValidation(range, values)
            }.orEmpty()

        val model = Sheet(
            name = entry.name,
            cells = cells.mapNotNull { (position, cell) ->
                val raw = cellValue(cell, sharedStrings)
                val format = formats[position] ?: CellFormat()
                if (raw.isEmpty() && format == CellFormat()) null
                else Cell(row = position.first, column = position.second, value = raw, format = format)
            },
            rowCount = maxOf((rows.keys.maxOrNull() ?: 0) + 1, MIN_SHEET_ROWS),
            columnCount = maxOf((cells.keys.maxOfOrNull { it.second } ?: 0) + 1, MIN_SHEET_COLUMNS),
            frozenRows = frozenRows,
            mergedRanges = mergedRanges,
            columnWidths = columnWidths,
            rowHeights = rowHeights,
            validations = validations,
        )
        return XlsxSheetSource(
            partName = partName,
            relId = entry.relId,
            sheetId = entry.sheetId,
            name = entry.name,
            xml = xml,
            root = root,
            styleIndexes = styleIndexes,
            formats = formats,
            sheetData = sheetData,
            cols = root.child("cols"),
            mergeCells = root.child("mergeCells"),
            dataValidations = root.child("dataValidations"),
            sheetViews = root.child("sheetViews"),
            rows = rows,
            cells = cells,
            formulas = formulas,
            cachedValues = cachedValues,
            model = model,
        )
    }

    private fun cellValue(cell: XmlElement, sharedStrings: List<String>): String {
        val formula = cell.child("f")?.text
        if (formula != null) return "=$formula"
        return when (cell.attr("t")) {
            "s" -> cell.child("v")?.text?.toIntOrNull()
                ?.let { sharedStrings.getOrNull(it) }.orEmpty()
            "inlineStr" -> cell.child("is")?.text.orEmpty()
            "b" -> if (cell.child("v")?.text == "1") "TRUE" else "FALSE"
            else -> cell.child("v")?.text.orEmpty()
        }
    }

    private fun parseRange(reference: String): CellRange? = parseCellRange(reference)

    private fun parseCellRef(ref: String): Pair<Int, Int> {
        val letters = ref.takeWhile { it.isLetter() }
        val digits = ref.dropWhile { it.isLetter() }
        val column = org.ziyoffice.model.columnIndex(letters).coerceAtLeast(0)
        val row = (digits.toIntOrNull() ?: 1) - 1
        return row.coerceAtLeast(0) to column
    }

    internal companion object {
        const val WORKBOOK_PART = "xl/workbook.xml"
        const val WORKBOOK_RELS_PART = "xl/_rels/workbook.xml.rels"
        const val SHARED_STRINGS_PART = "xl/sharedStrings.xml"
        const val STYLES_PART = "xl/styles.xml"
        const val SHEET_PREFIX = "xl/worksheets/sheet"
        const val MAX_ENTRY_BYTES = 64L * 1024 * 1024
        const val MAX_TOTAL_BYTES = 256L * 1024 * 1024
    }
}

/** A range reference, which may name a single cell rather than a range. */
internal fun parseCellRange(reference: String): CellRange? {
    val trimmed = reference.trim()
    if (trimmed.contains(':')) return CellRange.parse(trimmed)
    val letters = trimmed.takeWhile { it.isLetter() }
    val digits = trimmed.dropWhile { it.isLetter() }
    if (letters.isEmpty() || digits.isEmpty()) return null
    val column = org.ziyoffice.model.columnIndex(letters).coerceAtLeast(0)
    val row = (digits.toIntOrNull() ?: 1) - 1
    return CellRange(row.coerceAtLeast(0), column, row.coerceAtLeast(0), column)
}

internal fun parseWorkbookRelationships(bytes: ByteArray): Map<String, String> {
    val root = XmlScanner.parse(bytes.decodeToString()).root
    val targets = mutableMapOf<String, String>()
    root.childrenNamed("Relationship").forEach { element ->
        val id = element.attr("Id") ?: return@forEach
        val target = element.attr("Target") ?: return@forEach
        if (element.attr("TargetMode") == "External") return@forEach
        targets[id] = resolveWorkbookTarget(target)
    }
    return targets
}

internal fun resolveWorkbookTarget(target: String): String {
    if (target.startsWith("/")) return target.removePrefix("/")
    if (target.startsWith("xl/")) return target
    return "xl/$target"
}
