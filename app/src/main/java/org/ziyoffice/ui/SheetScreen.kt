package org.ziyoffice.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatStrikethrough
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.ByteArrayInputStream
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ziyoffice.data.DraftStore
import org.ziyoffice.data.RecentFilesStore
import org.ziyoffice.model.BorderKind
import org.ziyoffice.model.BorderStyle
import org.ziyoffice.model.Cell
import org.ziyoffice.model.CellAlignment
import org.ziyoffice.model.CellBorders
import org.ziyoffice.model.CellFormat
import org.ziyoffice.model.CellRange
import org.ziyoffice.model.CellRangeCorner
import org.ziyoffice.model.DEFAULT_COLUMN_WIDTH_CHARS
import org.ziyoffice.model.DEFAULT_ROW_HEIGHT_POINTS
import org.ziyoffice.model.RecentFile
import org.ziyoffice.model.Sheet
import org.ziyoffice.model.SheetMatch
import org.ziyoffice.model.SheetRowBand
import org.ziyoffice.model.SheetSort
import org.ziyoffice.model.Workbook
import org.ziyoffice.model.cellPlacements
import org.ziyoffice.model.chartPointsFrom
import org.ziyoffice.model.columnName
import org.ziyoffice.model.columnWidth
import org.ziyoffice.model.deleteColumns
import org.ziyoffice.model.deleteRows
import org.ziyoffice.model.evaluateFormula
import org.ziyoffice.model.findMatches
import org.ziyoffice.model.formatCellValue
import org.ziyoffice.model.formatNumber
import org.ziyoffice.model.generalNumber
import org.ziyoffice.model.insertColumns
import org.ziyoffice.model.insertRows
import org.ziyoffice.model.isDateFormat
import org.ziyoffice.model.mergeAt
import org.ziyoffice.model.parseCsv
import org.ziyoffice.model.parseDateInput
import org.ziyoffice.model.replaceInCell
import org.ziyoffice.model.replaceText
import org.ziyoffice.model.rowBands
import org.ziyoffice.model.rowHeight
import org.ziyoffice.model.tsvOf
import org.ziyoffice.model.validationAt
import org.ziyoffice.model.withBorders
import org.ziyoffice.model.withColumnWidth
import org.ziyoffice.model.withMergedRange
import org.ziyoffice.model.withMergesClearedIn
import org.ziyoffice.model.withPastedText
import org.ziyoffice.model.withRowHeight
import org.ziyoffice.model.withValidation
import org.ziyoffice.model.withValidationCleared
import org.ziyoffice.model.writeCsv
import org.ziyoffice.export.Printer
import org.ziyoffice.export.SheetExporter
import org.ziyoffice.ooxml.ContentAudit
import org.ziyoffice.ooxml.XlsxEditor
import org.ziyoffice.ooxml.PackageKind
import org.ziyoffice.ooxml.XlsxSource
import org.ziyoffice.ooxml.PreservedFeature
import org.ziyoffice.ooxml.XlsxReader
import org.ziyoffice.ooxml.XlsxWriter
import org.ziyoffice.saf.SafeWrite
import org.ziyoffice.saf.XLSX_MIME_TYPE

/** A 0-based cell address inside the active sheet. */
private data class CellPos(val row: Int, val column: Int)

private enum class SizeTarget { COLUMN, ROW }

private enum class SheetAxis(val noun: String) {
    ROWS("row"),
    COLUMNS("column");

    fun spanOf(range: CellRange): Int = when (this) {
        ROWS -> range.rowSpan
        COLUMNS -> range.columnSpan
    }

    fun firstOf(range: CellRange): Int = when (this) {
        ROWS -> range.top
        COLUMNS -> range.left
    }

    fun lastOf(range: CellRange): Int = when (this) {
        ROWS -> range.bottom
        COLUMNS -> range.right
    }

    fun sizeOf(sheet: Sheet): Int = when (this) {
        ROWS -> sheet.rowCount
        COLUMNS -> sheet.columnCount
    }

    fun insertInto(sheet: Sheet, at: Int, count: Int): Sheet = when (this) {
        ROWS -> sheet.insertRows(at, count)
        COLUMNS -> sheet.insertColumns(at, count)
    }

    fun deleteFrom(sheet: Sheet, range: CellRange, count: Int): Sheet = when (this) {
        ROWS -> sheet.deleteRows(range.top, count)
        COLUMNS -> sheet.deleteColumns(range.left, count)
    }
}

private fun borderWidth(style: BorderStyle): Dp = when (style) {
    BorderStyle.THIN, BorderStyle.DASHED, BorderStyle.DOTTED -> 1.5.dp
    BorderStyle.MEDIUM -> 2.5.dp
    BorderStyle.THICK -> 3.5.dp
    BorderStyle.NONE -> 0.dp
}

/** Trims a unit value for the dialog: 15.0 reads as "15", 8.43 stays "8.43". */
private fun sizeText(value: Float): String =
    if (value % 1f == 0f) value.toInt().toString() else value.toString()

/** Accepts a dot or the comma key on locales whose keyboards offer one. */
private fun parseSize(text: String): Float? =
    text.trim().replace(',', '.').toFloatOrNull()?.takeIf { it.isFinite() }

/** "A1" for a cell, "A1:C3" for a rectangle, for status copy and menus. */
private fun rangeLabel(range: CellRange): String {
    val start = columnName(range.left) + (range.top + 1)
    if (range.rowSpan == 1 && range.columnSpan == 1) return start
    return "$start:${columnName(range.right)}${range.bottom + 1}"
}

/** "1 row", "3 rows", for snackbars and bottom-sheet actions. */
internal fun countLabel(count: Int, singular: String): String =
    "$count $singular" + if (count == 1) "" else "s"

private val CELL_WIDTH = 76.dp
private val CELL_HEIGHT = 40.dp
private val MIN_COLUMN_WIDTH = 24.dp
private val MAX_COLUMN_WIDTH = 480.dp
private val MIN_ROW_HEIGHT = 24.dp
private val MAX_ROW_HEIGHT = 240.dp
private val ROW_HEADER_WIDTH = 44.dp
private const val UNDO_LIMIT = 100

/**
 * Convert spreadsheet units to on-screen sizes. The package defaults (width
 * 8.43, height 15 points) map to the 76 x 40 dp cell the editor has always
 * used. The clamps are display-only: the saved value is untouched.
 */
private fun Sheet.columnWidthDp(column: Int): Dp =
    (CELL_WIDTH * (columnWidth(column) / DEFAULT_COLUMN_WIDTH_CHARS))
        .coerceIn(MIN_COLUMN_WIDTH, MAX_COLUMN_WIDTH)

private fun Sheet.rowHeightDp(row: Int): Dp =
    (CELL_HEIGHT * (rowHeight(row) / DEFAULT_ROW_HEIGHT_POINTS))
        .coerceIn(MIN_ROW_HEIGHT, MAX_ROW_HEIGHT)

private val NUMBER_FORMATS: List<Pair<String?, String>> = listOf(
    null to "General",
    "0" to "Number (0)",
    "0.00" to "Number (0.00)",
    "#,##0" to "Thousands",
    "#,##0.00" to "Thousands (2 dp)",
    "0%" to "Percent",
    "0.00%" to "Percent (2 dp)",
    "@" to "Text",
    "d-mmm-yy" to "Date (15-Mar-23)",
    "m/d/yyyy" to "Date (3/15/2023)",
    "d mmmm yyyy" to "Date (15 March 2023)",
    "h:mm" to "Time (13:45)",
    "h:mm AM/PM" to "Time (1:45 PM)",
    "d-mmm-yy h:mm" to "Date and time",
)

/**
 * The spreadsheet editor: a scrollable grid with frozen row/column headers, a
 * formula bar, keyboard navigation and cell formatting. Everything is computed
 * against the [Sheet] model, so a formula shows its value while the formula bar
 * keeps the formula. Saving writes a real `.xlsx`; unsaved work goes to a local
 * draft, so a process death never costs the user their typing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SheetScreen(
    file: RecentFile,
    initialEditing: Boolean = false,
    onBack: () -> Unit,
    onSaveAs: (RecentFile) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Saving must survive the screen leaving composition: the back handler saves
    // and navigates in one frame, which would cancel a composition scope.
    val writeScope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate) }
    val snackbarHostState = remember { SnackbarHostState() }
    val draftStore = remember { DraftStore(context) }

    var workbook by remember(file.uri) { mutableStateOf<Workbook?>(null) }
    var loadError by remember(file.uri) { mutableStateOf<String?>(null) }
    var activeSheet by remember(file.uri) { mutableStateOf(0) }
    var selection by remember(file.uri) { mutableStateOf(CellPos(0, 0)) }
    // The far corner of a rectangular selection. Null means one active cell.
    var selectionEnd by remember(file.uri) { mutableStateOf<CellPos?>(null) }
    var editText by remember(file.uri) { mutableStateOf("") }
    var editingCell by remember(file.uri) { mutableStateOf(false) }
    var dirty by remember(file.uri) { mutableStateOf(false) }
    // Bumped by every mutation, so an in-flight write can spot newer edits.
    var writeVersion by remember(file.uri) { mutableStateOf(0L) }
    var saving by remember(file.uri) { mutableStateOf(false) }
    var undoStack by remember(file.uri) { mutableStateOf(emptyList<Workbook>()) }
    var redoStack by remember(file.uri) { mutableStateOf(emptyList<Workbook>()) }
    var menuExpanded by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var showChart by remember { mutableStateOf(false) }
    var showStructure by remember { mutableStateOf(false) }
    var showFind by remember { mutableStateOf(false) }
    var findQuery by remember { mutableStateOf("") }
    var findReplacement by remember { mutableStateOf("") }
    var findMatchCase by remember { mutableStateOf(false) }
    var findCursor by remember { mutableStateOf(0) }
    var showListDialog by remember { mutableStateOf(false) }
    var listChoicesText by remember { mutableStateOf("") }
    // The row the grid should bring into view, set when a match is selected.
    var scrollTargetRow by remember { mutableStateOf<Int?>(null) }
    var renameText by remember { mutableStateOf("") }
    var sizeTarget by remember { mutableStateOf<SizeTarget?>(null) }
    var sizeValueText by remember { mutableStateOf("") }
    // Content-loss gate, same contract as the Docs editor: parts the writer
    // cannot reproduce are announced before the first save, not after it.
    var droppedFeatures by remember(file.uri) { mutableStateOf(emptyList<PreservedFeature>()) }
    /**
     * The package the workbook was opened from. Saving writes the edited
     * workbook back into it, so the charts, images, comments, filters and
     * protections it carries survive the save.
     */
    var sourcePackage by remember(file.uri) { mutableStateOf<XlsxSource?>(null) }

    /**
     * The bytes to write for [current]. A workbook from a package goes back into
     * that package, so what the editor cannot edit survives. One ZiyOffice
     * created has no package and is written from scratch.
     */
    fun workbookBytes(current: Workbook): ByteArray {
        if (isCsv(file.displayName)) {
            return writeCsv(current.sheets[activeSheet]).encodeToByteArray()
        }
        val target = current.copy(activeSheet = activeSheet)
        val source = sourcePackage
        return if (source != null) XlsxEditor(source).save(target) else XlsxWriter().write(target)
    }

    // A draft is always an XLSX package, even for a CSV file. Restoring one is
    // then the same operation whichever file it came from.
    fun draftBytes(current: Workbook): ByteArray =
        XlsxWriter().write(current.copy(activeSheet = activeSheet))

    var showLossWarning by remember(file.uri) { mutableStateOf(false) }
    var lossAccepted by remember(file.uri) { mutableStateOf(false) }

    val gridFocus = remember { FocusRequester() }
    val formulaFocus = remember { FocusRequester() }

    val sheet = workbook?.sheets?.getOrNull(activeSheet)
    val cellsByPos = remember(sheet) {
        sheet?.cells?.associateBy { it.row to it.column }.orEmpty()
    }
    val findHits = remember(sheet, findQuery, findMatchCase) {
        if (findQuery.isEmpty() || sheet == null) {
            emptyList()
        } else {
            sheet.findMatches(findQuery, ignoreCase = !findMatchCase)
        }
    }

    data class Loaded(
        val workbook: Workbook,
        val recovered: Boolean,
        val dropped: List<PreservedFeature>,
        val source: XlsxSource?,
    )

    LaunchedEffect(file.uri) {
        val loaded = withContext(Dispatchers.IO) {
            runCatching {
                val draft = draftStore.load(file.uri)
                if (isCsv(file.displayName)) {
                    // CSV has no package to write back into, so there is no
                    // source and nothing to preserve.
                    val text = context.contentResolver.openInputStream(Uri.parse(file.uri))
                        ?.bufferedReader()?.use { it.readText() }
                        ?: error("the file could not be opened")
                    val fromFile = Workbook(
                        listOf(parseCsv(text, file.displayName.substringBeforeLast('.'))),
                    )
                    if (draft != null) {
                        // The draft holds the user's unsaved cells.
                        Loaded(XlsxSource.capture(draft).workbook, true, emptyList(), null)
                    } else {
                        Loaded(fromFile, false, emptyList(), null)
                    }
                } else {
                    val stream = context.contentResolver.openInputStream(Uri.parse(file.uri))
                        ?: error("the file could not be opened")
                    // One read serves both the model and the audit, so the warning
                    // describes this file.
                    val bytes = stream.use { it.readBytes() }
                    val dropped = ContentAudit.xlsx(bytes)
                    // The source package always comes from the file on disk. A
                    // recovered draft only carries the user's cells, and saving
                    // from it would drop whatever the file had that a save keeps.
                    val fileSource = XlsxSource.capture(bytes)
                    if (draft != null) {
                        val draftWorkbook = XlsxSource.capture(draft).workbook
                        Loaded(draftWorkbook, true, dropped, fileSource)
                    } else {
                        Loaded(fileSource.workbook, false, dropped, fileSource)
                    }
                }
            }
        }
        loaded.fold(
            onSuccess = { loaded ->
                val loadedWorkbook = loaded.workbook
                workbook = loadedWorkbook
                droppedFeatures = loaded.dropped
                sourcePackage = loaded.source
                activeSheet = loadedWorkbook.activeSheet.coerceIn(
                    0,
                    (loadedWorkbook.sheets.size - 1).coerceAtLeast(0),
                )
                dirty = loaded.recovered
                if (loaded.recovered) {
                    snackbarHostState.showSnackbar("Recovered unsaved changes")
                }
            },
            onFailure = { error ->
                loadError = error.message ?: error.javaClass.simpleName
            },
        )
    }

    LaunchedEffect(workbook, dirty, editingCell) {
        val current = workbook ?: return@LaunchedEffect
        if (!dirty) return@LaunchedEffect
        delay(1500L)
        val saved = withContext(Dispatchers.IO) {
            runCatching {
                val target = current.copy(activeSheet = activeSheet)
                draftStore.save(file.uri, draftBytes(target))
            }.isSuccess
        }
        if (!saved) {
            snackbarHostState.showSnackbar("Could not save your draft")
        }
    }

    LaunchedEffect(initialEditing, workbook) {
        if (initialEditing && workbook != null) runCatching { gridFocus.requestFocus() }
    }

    // A keystroke in the last 1.5 s exists only in this process: the debounced
    // autosave has not run yet and state is not rememberSaveable. Leaving
    // composition is the last chance, so the draft goes out on a daemon thread.
    // The serialization happens once, in onDispose, not on every recomposition.
    var flushTarget by remember(file.uri) { mutableStateOf<Workbook?>(null) }
    var flushIsDirty by remember(file.uri) { mutableStateOf(false) }
    var flushSheet by remember(file.uri) { mutableStateOf(0) }
    SideEffect {
        flushTarget = workbook
        flushIsDirty = dirty
        flushSheet = activeSheet
    }
    DisposableEffect(file.uri) {
        onDispose {
            val target = flushTarget
            if (target != null && flushIsDirty) {
                val uri = file.uri
                val store = draftStore
                val sheetIndex = flushSheet
                // A composition scope is cancelled before onDispose runs, so a plain
                // scope.launch would never land. A daemon thread always finishes.
                Thread {
                    runCatching {
                        store.save(uri, XlsxWriter().write(target.copy(activeSheet = sheetIndex)))
                    }
                }.apply { name = "draft-flush" }.start()
            }
        }
    }

    fun currentSheet(): Sheet? = workbook?.sheets?.getOrNull(activeSheet)

    /** The selected rectangle, always including the active cell. */
    fun selectedRange(): CellRange = CellRange.of(
        CellRangeCorner(selection.row, selection.column),
        CellRangeCorner(
            selectionEnd?.row ?: selection.row,
            selectionEnd?.column ?: selection.column,
        ),
    )

    fun anchorAt(sheet: Sheet, pos: CellPos): CellPos {
        val merge = sheet.mergeAt(pos.row, pos.column) ?: return pos
        return CellPos(merge.top, merge.left)
    }

    fun clampToSheet(sheet: Sheet, pos: CellPos): CellPos = CellPos(
        row = pos.row.coerceIn(0, (sheet.rowCount - 1).coerceAtLeast(0)),
        column = pos.column.coerceIn(0, (sheet.columnCount - 1).coerceAtLeast(0)),
    )

    fun rawValueAt(pos: CellPos): String =
        currentSheet()?.cells?.firstOrNull { it.row == pos.row && it.column == pos.column }?.value.orEmpty()

    fun formatAt(pos: CellPos): CellFormat =
        currentSheet()?.cells?.firstOrNull { it.row == pos.row && it.column == pos.column }?.format
            ?: CellFormat()

    /** Applies [transform] to the active sheet, recording one undo step. */
    fun mutate(transform: (Sheet) -> Sheet) {
        val current = workbook ?: return
        val currentSheetValue = current.sheets.getOrNull(activeSheet) ?: return
        val updated = transform(currentSheetValue)
        if (updated == currentSheetValue) return
        val sheets = current.sheets.toMutableList()
        sheets[activeSheet] = updated
        val next = current.copy(sheets = sheets)
        undoStack = (undoStack + current).takeLast(UNDO_LIMIT)
        redoStack = emptyList()
        workbook = next
        dirty = true
        writeVersion++
    }

    fun writeCell(pos: CellPos, raw: String, format: CellFormat? = null) {
        mutate { target ->
            val existing = target.cells.firstOrNull { it.row == pos.row && it.column == pos.column }
            val effectiveFormat = format ?: existing?.format ?: CellFormat()
            val cells = target.cells
                .filterNot { it.row == pos.row && it.column == pos.column }
                .toMutableList()
            val nextSheet = target.copy(cells = cells)
            if (raw.isNotEmpty()) {
                // Cache the computed value so the saved package shows results
                // before Excel recalculates.
                val cached = if (raw.startsWith("=")) evaluateFormula(nextSheet, raw).value else null
                cells += Cell(pos.row, pos.column, raw, effectiveFormat, cached)
            } else if (effectiveFormat != CellFormat()) {
                cells += Cell(pos.row, pos.column, "", effectiveFormat, null)
            }
            nextSheet.copy(
                cells = cells.sortedWith(compareBy({ it.row }, { it.column })),
            )
        }
    }

    /**
     * Typed dates and times become serial numbers so the date format has something
     * to render. Numbers and formulas pass through untouched.
     */
    fun normaliseInput(text: String, format: CellFormat): String {
        if (text.isEmpty() || text.startsWith("=")) return text
        if (text.toDoubleOrNull() != null) return text
        if (!isDateFormat(format.numberFormat)) return text
        val serial = parseDateInput(text) ?: return text
        return generalNumber(serial)
    }

    fun commitEdit() {
        val text = editText
        editingCell = false
        // Committing is a real edit: it must set [dirty], or a cell typed and
        // then left by tapping another cell would never be saved.
        if (text != rawValueAt(selection)) writeCell(selection, normaliseInput(text, formatAt(selection)))
    }

    fun select(pos: CellPos) {
        val target = currentSheet() ?: return
        val clamped = anchorAt(target, clampToSheet(target, pos))
        if (editingCell && clamped != selection) commitEdit()
        selection = clamped
        selectionEnd = null
        editText = rawValueAt(clamped)
    }

    /**
     * Grows the selection from its active cell to [pos]. A long press is the
     * one-thumb path, Shift+arrow the same from a hardware keyboard.
     */
    fun extendSelectionTo(pos: CellPos) {
        val target = currentSheet() ?: return
        if (editingCell) commitEdit()
        selectionEnd = clampToSheet(target, pos)
        editText = rawValueAt(selection)
    }

    /** Moves the far corner, skipping over any merged block it starts inside. */
    fun extendSelection(rowDelta: Int, columnDelta: Int) {
        val target = currentSheet() ?: return
        if (editingCell) commitEdit()
        val from = selectionEnd ?: selection
        val merge = target.mergeAt(from.row, from.column)
        val next = CellPos(
            row = when {
                rowDelta < 0 -> (merge?.top ?: from.row) - 1
                rowDelta > 0 -> (merge?.bottom ?: from.row) + 1
                else -> from.row
            },
            column = when {
                columnDelta < 0 -> (merge?.left ?: from.column) - 1
                columnDelta > 0 -> (merge?.right ?: from.column) + 1
                else -> from.column
            },
        )
        selectionEnd = clampToSheet(target, next)
    }

    fun clearSelectedCell() {
        if (editingCell) commitEdit()
        val range = selectedRange()
        val target = currentSheet() ?: return
        val hasContent = target.cells.any {
            range.contains(it.row, it.column) &&
                (it.value.isNotEmpty() || it.format != CellFormat())
        }
        if (!hasContent) return
        mutate { sheet ->
            // Clearing removes the value but keeps deliberate formatting: a
            // bordered table stays a table when its contents are deleted.
            val cells = sheet.cells.mapNotNull { cell ->
                if (!range.contains(cell.row, cell.column)) return@mapNotNull cell
                when {
                    cell.format != CellFormat() -> cell.copy(value = "", cached = null)
                    else -> null
                }
            }
            sheet.copy(cells = cells)
        }
        editText = ""
    }

    /** Merges the selected rectangle; Excel-style, only the top-left value stays. */
    fun mergeSelection() {
        if (editingCell) commitEdit()
        val range = selectedRange()
        if (range.rowSpan <= 1 && range.columnSpan <= 1) {
            scope.launch { snackbarHostState.showSnackbar("Select two or more cells first") }
            return
        }
        val hiddenValues = currentSheet()?.cells?.count { cell ->
            range.contains(cell.row, cell.column) &&
                (cell.row != range.top || cell.column != range.left) &&
                cell.value.isNotEmpty()
        } ?: 0
        mutate { it.withMergedRange(range) }
        selection = CellPos(range.top, range.left)
        selectionEnd = null
        editText = rawValueAt(selection)
        val suffix = if (hiddenValues > 0) " \u00B7 other values cleared" else ""
        scope.launch {
            snackbarHostState.showSnackbar("Merged ${rangeLabel(range)}$suffix")
        }
    }

    fun unmergeSelection() {
        if (editingCell) commitEdit()
        val range = selectedRange()
        val target = currentSheet() ?: return
        val merges = target.mergedRanges.filter { it.intersects(range) }
        if (merges.isEmpty()) return
        mutate { it.withMergesClearedIn(range) }
        editText = rawValueAt(selection)
        val label = merges.joinToString { rangeLabel(it) }
        scope.launch {
            snackbarHostState.showSnackbar(
                if (merges.size == 1) "Unmerged $label" else "Unmerged ${merges.size} blocks",
            )
        }
    }

    fun toggleFrozenRows() {
        if (editingCell) commitEdit()
        val current = currentSheet() ?: return
        mutate {
            it.copy(frozenRows = if (it.frozenRows > 0) 0 else selection.row)
        }
    }

    fun openSizeDialog(target: SizeTarget) {
        if (editingCell) commitEdit()
        val current = currentSheet() ?: return
        val range = selectedRange()
        sizeValueText = sizeText(
            when (target) {
                SizeTarget.COLUMN -> current.columnWidth(range.left)
                SizeTarget.ROW -> current.rowHeight(range.top)
            },
        )
        sizeTarget = target
    }

    fun applySizeDialog() {
        val target = sizeTarget ?: return
        val value = parseSize(sizeValueText) ?: return
        val range = selectedRange()
        mutate { sheet ->
            when (target) {
                SizeTarget.COLUMN -> (range.left..range.right).fold(sheet) { next, column ->
                    next.withColumnWidth(column, value)
                }
                SizeTarget.ROW -> (range.top..range.bottom).fold(sheet) { next, row ->
                    next.withRowHeight(row, value)
                }
            }
        }
        sizeTarget = null
        editText = rawValueAt(selection)
    }

    /** Moves the selection after a structural edit and keeps it on a real cell. */
    fun settleAfterStructure(sheet: Sheet, pos: CellPos) {
        selection = anchorAt(sheet, clampToSheet(sheet, pos))
        selectionEnd = null
        editText = rawValueAt(selection)
    }

    /**
     * Inserts rows or columns at the edge of the selection. [before] puts them
     * above the first selected row, or left of the first selected column.
     */
    fun insertSelected(axis: SheetAxis, before: Boolean) {
        if (editingCell) commitEdit()
        val sheet = currentSheet() ?: return
        val range = selectedRange()
        val count = axis.spanOf(range)
        val at = if (before) axis.firstOf(range) else axis.lastOf(range) + 1
        val after = axis.insertInto(sheet, at, count)
        if (after == sheet) {
            showStructure = false
            scope.launch {
                snackbarHostState.showSnackbar("There is no room to insert more ${axis.noun}s")
            }
            return
        }
        mutate { axis.insertInto(it, at, count) }
        val target = when (axis) {
            SheetAxis.ROWS -> CellPos(at, range.left)
            SheetAxis.COLUMNS -> CellPos(range.top, at)
        }
        settleAfterStructure(after, target)
        showStructure = false
        val inserted = axis.sizeOf(after) - axis.sizeOf(sheet)
        scope.launch {
            val where = when (axis) {
                SheetAxis.ROWS ->
                    if (before) "above row ${at + 1}" else "below row ${at}"
                SheetAxis.COLUMNS ->
                    if (before) "to the left of ${columnName(at)}" else "to the right of ${columnName(at - 1)}"
            }
            snackbarHostState.showSnackbar("Inserted ${countLabel(inserted, axis.noun)} $where")
        }
    }

    fun deleteSelected(axis: SheetAxis) {
        if (editingCell) commitEdit()
        val sheet = currentSheet() ?: return
        val range = selectedRange()
        val count = axis.spanOf(range)
        if (count >= axis.sizeOf(sheet)) {
            showStructure = false
            scope.launch {
                snackbarHostState.showSnackbar(
                    "A sheet keeps at least one ${axis.noun}; clear the cells instead",
                )
            }
            return
        }
        val after = axis.deleteFrom(sheet, range, count)
        if (after == sheet) return
        mutate { axis.deleteFrom(it, range, count) }
        settleAfterStructure(after, CellPos(range.top, range.left))
        showStructure = false
        scope.launch {
            snackbarHostState.showSnackbar("Deleted ${countLabel(count, axis.noun)}")
        }
    }

    fun runStructureAction(action: StructureAction) {
        when (action) {
            StructureAction.INSERT_ROWS_ABOVE -> insertSelected(SheetAxis.ROWS, before = true)
            StructureAction.INSERT_ROWS_BELOW -> insertSelected(SheetAxis.ROWS, before = false)
            StructureAction.DELETE_ROWS -> deleteSelected(SheetAxis.ROWS)
            StructureAction.INSERT_COLUMNS_LEFT -> insertSelected(SheetAxis.COLUMNS, before = true)
            StructureAction.INSERT_COLUMNS_RIGHT -> insertSelected(SheetAxis.COLUMNS, before = false)
            StructureAction.DELETE_COLUMNS -> deleteSelected(SheetAxis.COLUMNS)
        }
    }

    fun selectHit(hit: SheetMatch) {
        findCursor = findHits.indexOfFirst { it.row == hit.row && it.column == hit.column }
        selection = CellPos(hit.row, hit.column)
        selectionEnd = null
        scrollTargetRow = hit.row
        editText = rawValueAt(selection)
    }

    /** The first match after [position], wrapping around to the start. */
    fun firstHitAfter(hits: List<SheetMatch>, position: CellPos): SheetMatch? {
        val index = hits.indexOfFirst {
            it.row > position.row || (it.row == position.row && it.column > position.column)
        }
        return if (index < 0) hits.firstOrNull() else hits[index]
    }

    fun findNext() {
        val hits = findHits
        if (hits.isEmpty()) {
            scope.launch { snackbarHostState.showSnackbar("No cell holds \u201C$findQuery\u201D") }
            return
        }
        val current = hits.indexOfFirst { it.row == selection.row && it.column == selection.column }
        val next = if (current < 0) {
            firstHitAfter(hits, selection) ?: hits.first()
        } else {
            hits[(current + 1) % hits.size]
        }
        selectHit(next)
    }

    fun replaceCurrent() {
        val hit = findHits.getOrNull(findCursor)
        if (hit == null) {
            findNext()
            return
        }
        val before = currentSheet() ?: return
        val (updated, count) = before.replaceInCell(
            row = hit.row,
            column = hit.column,
            query = findQuery,
            replacement = findReplacement,
            ignoreCase = !findMatchCase,
        )
        if (count == 0) {
            findNext()
            return
        }
        mutate { updated }
        // The match list changed, so the next hit is looked up in the new sheet.
        val hits = updated.findMatches(findQuery, ignoreCase = !findMatchCase)
        val next = firstHitAfter(hits, CellPos(hit.row, hit.column))
        if (next != null) selectHit(next) else editText = rawValueAt(selection)
    }

    fun replaceAll() {
        val before = currentSheet() ?: return
        val result = before.replaceText(
            query = findQuery,
            replacement = findReplacement,
            ignoreCase = !findMatchCase,
        )
        if (result.occurrences == 0) {
            scope.launch { snackbarHostState.showSnackbar("Nothing to replace") }
            return
        }
        mutate { result.sheet }
        // The cell under the cursor may be one of the ones that changed.
        editText = rawValueAt(selection)
        scope.launch {
            snackbarHostState.showSnackbar(
                "Replaced ${result.occurrences} " +
                    (if (result.occurrences == 1) "occurrence" else "occurrences") +
                    " in ${result.cells} " +
                    (if (result.cells == 1) "cell" else "cells"),
            )
        }
    }

    fun runFindAction(action: FindAction) {
        when (action) {
            FindAction.NEXT -> findNext()
            FindAction.REPLACE -> replaceCurrent()
            FindAction.REPLACE_ALL -> replaceAll()
        }
    }

    /** Draws borders around the selected cells. "Box" is the outline, "all" every edge. */
    fun applyBorders(kind: BorderKind) {
        if (editingCell) commitEdit()
        val range = selectedRange()
        val anchor = selection
        mutate { it.withBorders(range, kind) }
        editText = rawValueAt(anchor)
    }

    /** Copies the selected range as tab-separated text. */
    fun copySelection() {
        val current = currentSheet() ?: return
        val range = selectedRange()
        val text = current.tsvOf(range)
        if (text.isEmpty()) return
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        clipboard?.setPrimaryClip(ClipData.newPlainText("ZiyOffice cells", text))
        scope.launch { snackbarHostState.showSnackbar("Copied ${rangeLabel(range)}") }
    }

    fun pasteClipboard() {
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        val text = clipboard?.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
        if (text.isEmpty()) {
            scope.launch { snackbarHostState.showSnackbar("The clipboard holds no text") }
            return
        }
        val at = selection
        mutate { it.withPastedText(text, at.row, at.column) }
        editText = rawValueAt(selection)
        scope.launch { snackbarHostState.showSnackbar("Pasted into ${rangeLabel(selectedRange())}") }
    }

    fun openListDialog() {
        val current = currentSheet() ?: return
        val range = selectedRange()
        val existing = current.validationAt(range.top, range.left)
        listChoicesText = existing?.values?.joinToString(", ").orEmpty()
        showListDialog = true
    }

    fun applyListDialog() {
        val range = selectedRange()
        val values = listChoicesText.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        mutate { it.withValidation(range, values) }
        showListDialog = false
        editText = rawValueAt(selection)
    }

    fun clearListDialog() {
        val range = selectedRange()
        mutate { it.withValidationCleared(range) }
        showListDialog = false
    }

    /** Sorts every used row by the cursor's column. The whole block moves, rows keep their cells. */
    fun sortBySelection(ascending: Boolean) {
        if (editingCell) commitEdit()
        val current = currentSheet() ?: return
        val lastRow = current.cells.maxOfOrNull { it.row } ?: return
        if (lastRow < 1) return
        val firstRow = current.cells.minOfOrNull { it.row } ?: 0
        val lastColumn = current.cells.maxOfOrNull { it.column } ?: 0
        val sortTouchesMerge = current.mergedRanges.any {
            it.top <= lastRow && it.bottom >= firstRow && it.left <= lastColumn
        }
        if (sortTouchesMerge) {
            // Reordering rows under a vertical merge would move the value away
            // from the block that owns it. Refuse instead of corrupting the sheet.
            scope.launch { snackbarHostState.showSnackbar("Unmerge cells before sorting") }
            return
        }

        val sorted = SheetSort.sort(
            sheet = current,
            topRow = 0,
            bottomRow = lastRow,
            key = SheetSort.Key(column = selection.column, ascending = ascending),
        )
        mutate { sorted }
        editText = rawValueAt(selection)
    }

    fun applyFormat(transform: (CellFormat) -> CellFormat) {
        if (editingCell) commitEdit()
        val range = selectedRange()
        val anchor = selection
        mutate { sheet ->
            val byPosition = sheet.cells.associateBy { it.row to it.column }
            // Formatting a visible merged cell must reach the cells it hides,
            // or unmerging shows an unstyled block.
            val positions = buildSet {
                for (row in range.top..range.bottom) {
                    for (column in range.left..range.right) add(row to column)
                }
                sheet.mergedRanges.filter { it.intersects(range) }.forEach { merge ->
                    for (row in merge.top..merge.bottom) {
                        for (column in merge.left..merge.right) add(row to column)
                    }
                }
            }
            val outside = sheet.cells.filterNot { (it.row to it.column) in positions }
            val formatted = positions.mapNotNull { (row, column) ->
                val existing = byPosition[row to column]
                val format = transform(existing?.format ?: CellFormat())
                val value = existing?.value.orEmpty()
                if (value.isNotEmpty() || format != CellFormat()) {
                    Cell(row, column, value, format, existing?.cached)
                } else {
                    null
                }
            }
            sheet.copy(
                cells = (outside + formatted).sortedWith(compareBy({ it.row }, { it.column })),
            )
        }
        editText = rawValueAt(anchor)
    }

    fun moveSelection(rowDelta: Int, columnDelta: Int) {
        if (editingCell) commitEdit()
        val sheet = currentSheet() ?: return
        val merge = sheet.mergeAt(selection.row, selection.column)
        val next = CellPos(
            row = when {
                rowDelta < 0 -> (merge?.top ?: selection.row) - 1
                rowDelta > 0 -> (merge?.bottom ?: selection.row) + 1
                else -> selection.row
            },
            column = when {
                columnDelta < 0 -> (merge?.left ?: selection.column) - 1
                columnDelta > 0 -> (merge?.right ?: selection.column) + 1
                else -> selection.column
            },
        )
        select(next)
    }

    fun startTyping(character: Char) {
        selectionEnd = null
        editingCell = true
        editText = character.toString()
        runCatching { formulaFocus.requestFocus() }
    }

    fun handleKey(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        if (event.isCtrlPressed) return false
        return when (event.key) {
            Key.DirectionLeft ->
                if (event.isShiftPressed) { extendSelection(0, -1); true } else { moveSelection(0, -1); true }
            Key.DirectionRight ->
                if (event.isShiftPressed) { extendSelection(0, 1); true } else { moveSelection(0, 1); true }
            Key.DirectionUp ->
                if (event.isShiftPressed) { extendSelection(-1, 0); true } else { moveSelection(-1, 0); true }
            Key.DirectionDown ->
                if (event.isShiftPressed) { extendSelection(1, 0); true } else { moveSelection(1, 0); true }
            Key.Enter -> { moveSelection(1, 0); true }
            Key.Tab -> { moveSelection(0, 1); true }
            Key.Delete -> { clearSelectedCell(); true }
            Key.Backspace -> { clearSelectedCell(); true }
            else -> {
                val codePoint = event.utf16CodePoint
                if (codePoint >= 32) {
                    startTyping(codePoint.toChar())
                    true
                } else {
                    false
                }
            }
        }
    }

    fun undo() {
        if (editingCell) commitEdit()
        val previous = undoStack.lastOrNull() ?: return
        val current = workbook ?: return
        undoStack = undoStack.dropLast(1)
        redoStack = (redoStack + current).takeLast(UNDO_LIMIT)
        workbook = previous
        activeSheet = previous.activeSheet.coerceIn(0, (previous.sheets.size - 1).coerceAtLeast(0))
        val restored = previous.sheets.getOrNull(activeSheet)
        if (restored != null) selection = anchorAt(restored, clampToSheet(restored, selection))
        selectionEnd = null
        editingCell = false
        editText = rawValueAt(selection)
        dirty = true
        writeVersion++
    }

    fun redo() {
        if (editingCell) commitEdit()
        val next = redoStack.lastOrNull() ?: return
        val current = workbook ?: return
        redoStack = redoStack.dropLast(1)
        undoStack = (undoStack + current).takeLast(UNDO_LIMIT)
        workbook = next
        activeSheet = next.activeSheet.coerceIn(0, (next.sheets.size - 1).coerceAtLeast(0))
        val restored = next.sheets.getOrNull(activeSheet)
        if (restored != null) selection = anchorAt(restored, clampToSheet(restored, selection))
        selectionEnd = null
        editingCell = false
        editText = rawValueAt(selection)
        dirty = true
        writeVersion++
    }

    fun writeWorkbook(current: Workbook, onSaved: () -> Unit) {
        val writingVersion = writeVersion
        saving = true
        writeScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = workbookBytes(current)
                    if (isCsv(file.displayName)) {
                        SafeWrite.writePlain(context, Uri.parse(file.uri), bytes)
                    } else {
                        val written = SafeWrite.write(
                            context,
                            Uri.parse(file.uri),
                            bytes,
                            PackageKind.SPREADSHEET,
                        )
                        check(
                            SafeWrite.verifyWritten(
                                context,
                                Uri.parse(file.uri),
                                written,
                                PackageKind.SPREADSHEET,
                            ),
                        ) { "the file could not be read back after saving" }
                    }
                }
            }
            saving = false
            result.fold(
                onSuccess = {
                    if (writeSettlesDirty(writingVersion, writeVersion)) {
                        draftStore.clear(file.uri)
                        dirty = false
                        snackbarHostState.showSnackbar("Saved \u00B7 ${file.displayName}")
                        onSaved()
                    } else {
                        // Edits landed while the write ran: the newer work stays
                        // dirty and keeps its draft.
                        snackbarHostState.showSnackbar("Saved \u00B7 newer edits kept")
                    }
                },
                onFailure = { error ->
                    snackbarHostState.showSnackbar(
                        "Could not save: ${error.message ?: error.javaClass.simpleName}. " +
                            "Re-open the file to grant write access.",
                    )
                },
            )
        }
    }

    /**
     * Confirms once, before the first write, when a save would remove workbook
     * content the writer cannot reproduce.
     */
    fun save(onSaved: () -> Unit = {}) {
        val current = workbook ?: return
        // Nothing is lost by saving: the edited parts go back into the original package.
        writeWorkbook(current, onSaved)
    }

    /**
     * The single exit path: save-and-leave when dirty, leave when not. The
     * system back gesture and the toolbar arrow both use it.
     */
    fun tryExit() {
        if (editingCell) commitEdit()
        when (exitAction(dirty = dirty, saving = saving, needsConfirmation = false)) {
            ExitAction.LEAVE -> onBack()
            ExitAction.BLOCK ->
                // A second write racing the first could land out of order; the
                // in-flight one reports its own outcome.
                scope.launch { snackbarHostState.showSnackbar("Saving \u00B7 wait a moment") }
            ExitAction.SAVE_AND_LEAVE -> save(onSaved = { onBack() })
            // Sheets save rather than ask; this branch is unreachable.
            ExitAction.ASK -> save(onSaved = { onBack() })
        }
    }

    fun share() {
        runCatching {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = XLSX_MIME_TYPE
                putExtra(Intent.EXTRA_STREAM, Uri.parse(file.uri))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share ${file.displayName}"))
        }.onFailure {
            scope.launch { snackbarHostState.showSnackbar("No installed app can share this file.") }
        }
    }

    fun addSheet() {
        if (editingCell) commitEdit()
        val current = workbook ?: return
        undoStack = (undoStack + current).takeLast(UNDO_LIMIT)
        redoStack = emptyList()
        val name = "Sheet ${current.sheets.size + 1}"
        val sheets = current.sheets + Sheet(name = name, cells = emptyList())
        workbook = current.copy(sheets = sheets)
        activeSheet = sheets.lastIndex
        selection = CellPos(0, 0)
        selectionEnd = null
        editText = ""
        dirty = true
        writeVersion++
    }

    fun renameSheet(newName: String) {
        if (editingCell) commitEdit()
        val current = workbook ?: return
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return
        val sheets = current.sheets.toMutableList()
        sheets[activeSheet] = sheets[activeSheet].copy(name = trimmed)
        undoStack = (undoStack + current).takeLast(UNDO_LIMIT)
        redoStack = emptyList()
        workbook = current.copy(sheets = sheets)
        dirty = true
        writeVersion++
    }

    val saveAsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(XLSX_MIME_TYPE),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val current = workbook ?: return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = workbookBytes(current)
                    if (isCsv(file.displayName)) {
                        SafeWrite.writePlain(context, uri, bytes)
                    } else {
                        SafeWrite.write(context, uri, bytes, PackageKind.SPREADSHEET)
                    }
                }
            }
            result.fold(
                onSuccess = {
                    val entry = RecentFilesStore(context).remember(uri)?.file
                    if (entry != null) {
                        snackbarHostState.showSnackbar("Saved copy \u00B7 ${entry.displayName}")
                        onSaveAs(entry)
                    }
                },
                onFailure = { error ->
                    snackbarHostState.showSnackbar("Could not save: ${error.message}")
                },
            )
        }
    }

    /**
     * Opens the system print dialog for the workbook. The PDF is rendered in
     * memory first, so a render failure is reported here.
     */
    fun printWorkbook() {
        if (editingCell) commitEdit()
        val current = workbook ?: return
        val target = current.copy(activeSheet = activeSheet)
        scope.launch {
            val bytes = withContext(Dispatchers.IO) {
                runCatching { Printer.workbookBytes(target) }.getOrNull()
            }
            if (!isActive) return@launch
            if (bytes == null) {
                snackbarHostState.showSnackbar("Could not prepare the spreadsheet for printing")
                return@launch
            }
            val name = file.displayName.substringBeforeLast('.', file.displayName)
            if (!Printer.print(context, name, bytes)) {
                snackbarHostState.showSnackbar("This device has no print service")
            }
        }
    }

    val exportPdfLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val current = workbook ?: return@rememberLauncherForActivityResult
        val target = current.copy(activeSheet = activeSheet)
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val buffer = java.io.ByteArrayOutputStream()
                    SheetExporter.export(target, buffer)
                    val bytes = buffer.toByteArray()
                    check(bytes.isNotEmpty()) { "the PDF came back empty" }
                    val stream = context.contentResolver.openOutputStream(uri, "wt")
                        ?: error("the file could not be created")
                    stream.use { it.write(bytes) }
                    bytes.size
                }
            }
            result.fold(
                onSuccess = { size ->
                    val entry = RecentFilesStore(context).remember(uri)?.file
                    val name = entry?.displayName ?: "the chosen file"
                    snackbarHostState.showSnackbar("PDF exported to $name \u00B7 $size bytes")
                },
                onFailure = { error ->
                    snackbarHostState.showSnackbar("Could not export PDF: ${error.message}")
                },
            )
        }
    }

    val saveCsvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val current = workbook ?: return@rememberLauncherForActivityResult
        writeScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = writeCsv(current.sheets[activeSheet]).encodeToByteArray()
                    context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) }
                        ?: error("could not write")
                }
            }
            result.fold(
                onSuccess = {
                    val entry = RecentFilesStore(context).remember(uri)?.file
                    if (entry != null) {
                        snackbarHostState.showSnackbar("Saved copy \u00B7 ${entry.displayName}")
                        onSaveAs(entry)
                    }
                },
                onFailure = { error ->
                    snackbarHostState.showSnackbar("Could not save: ${error.message}")
                },
            )
        }
    }

    BackHandler { tryExit() }

    if (showLossWarning) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showLossWarning = false },
            title = { Text("Content ZiyOffice can't edit") },
            text = {
                Text(
                    PreservedCopy.body(droppedFeatures, ".xlsx"),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLossWarning = false
                        // Accepted for this session: the banner does not come back.
                        lossAccepted = true
                    },
                ) { Text("Understood") }
            },
        )
    }

    if (showRename) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showRename = false },
            title = { Text("Rename sheet") },
            text = {
                TextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    label = { Text("Sheet name") },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        renameSheet(renameText)
                        showRename = false
                    },
                ) { Text("Rename") }
            },
            dismissButton = {
                TextButton(onClick = { showRename = false }) { Text("Cancel") }
            },
        )
    }

    sizeTarget?.let { target ->
        val range = selectedRange()
        val title = when (target) {
            SizeTarget.COLUMN ->
                if (range.columnSpan == 1) {
                    "Column ${columnName(range.left)} width"
                } else {
                    "Columns ${columnName(range.left)}\u2013${columnName(range.right)} width"
                }
            SizeTarget.ROW ->
                if (range.rowSpan == 1) {
                    "Row ${range.top + 1} height"
                } else {
                    "Rows ${range.top + 1}\u2013${range.bottom + 1} height"
                }
        }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { sizeTarget = null },
            title = { Text(title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextField(
                        value = sizeValueText,
                        onValueChange = { sizeValueText = it },
                        singleLine = true,
                        label = {
                            Text(if (target == SizeTarget.COLUMN) "Width" else "Height")
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (parseSize(sizeValueText) != null) applySizeDialog()
                            },
                        ),
                    )
                    Text(
                        if (target == SizeTarget.COLUMN) {
                            "Excel character units, 1\u2013255. The default is 8.43."
                        } else {
                            "Points, 8\u2013409. The default is 15."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val count = when (target) {
                        SizeTarget.COLUMN -> range.columnSpan
                        SizeTarget.ROW -> range.rowSpan
                    }
                    if (count > 1) {
                        Text(
                            "Applies to all $count selected " +
                                if (target == SizeTarget.COLUMN) "columns." else "rows.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = ::applySizeDialog,
                    enabled = parseSize(sizeValueText) != null,
                ) { Text("Apply") }
            },
            dismissButton = {
                TextButton(onClick = { sizeTarget = null }) { Text("Cancel") }
            },
        )
    }

    if (showListDialog) {
        SheetListDialog(
            choices = listChoicesText,
            rangeLabel = rangeLabel(selectedRange()),
            onChoicesChange = { listChoicesText = it },
            onApply = ::applyListDialog,
            onClear = ::clearListDialog,
            onDismiss = { showListDialog = false },
        )
    }

    if (showFind) {
        SheetFindPanel(
            query = findQuery,
            replacement = findReplacement,
            matchCase = findMatchCase,
            matchCount = findHits.size,
            onQueryChange = {
                findQuery = it
                findCursor = 0
            },
            onReplacementChange = { findReplacement = it },
            onMatchCaseToggle = { findMatchCase = !findMatchCase },
            onAction = ::runFindAction,
            onDismiss = { showFind = false },
        )
    }

    if (showStructure) {
        val structureSheet = currentSheet()
        if (structureSheet != null) {
            val range = selectedRange()
            SheetStructurePanel(
                rangeLabel = rangeLabel(range),
                rowCount = range.rowSpan,
                columnCount = range.columnSpan,
                canDeleteRows = range.rowSpan < structureSheet.rowCount,
                canDeleteColumns = range.columnSpan < structureSheet.columnCount,
                onAction = ::runStructureAction,
                onDismiss = { showStructure = false },
            )
        }
    }

    if (showChart) {
        // Commit first, in the event handler that opened the sheet, not here.
        // The body below runs on every recomposition, and mutating editor
        // state would re-trigger it.
        val chartSheet = currentSheet()
        if (chartSheet != null) {
            androidx.compose.material3.ModalBottomSheet(onDismissRequest = { showChart = false }) {
                Column(modifier = Modifier.padding(bottom = 24.dp)) {
                    Text(
                        "Chart of column ${columnName(selection.column)}",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    BarChart(
                        points = chartPointsFrom(chartSheet, column = selection.column),
                        numberFormat = null,
                    )
                }
            }
        }
    }

    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = ::tryExit) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Column {
                        Text(
                            file.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            (sheet?.name ?: file.displayName) +
                                if (dirty) " \u00B7 UNSAVED EDITS" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = ::undo, enabled = undoStack.isNotEmpty()) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
                    }
                    IconButton(onClick = ::redo, enabled = redoStack.isNotEmpty()) {
                        Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo")
                    }
                    IconButton(onClick = ::save, enabled = dirty && !saving) {
                        Icon(Icons.Filled.Save, contentDescription = "Save")
                    }
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More actions")
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Copy") },
                                onClick = {
                                    menuExpanded = false
                                    copySelection()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Paste") },
                                onClick = {
                                    menuExpanded = false
                                    pasteClipboard()
                                },
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Share as .xlsx") },
                                onClick = {
                                    menuExpanded = false
                                    share()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Save as copy\u2026") },
                                onClick = {
                                    menuExpanded = false
                                    saveAsLauncher.launch(file.displayName)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Export as PDF\u2026") },
                                onClick = {
                                    menuExpanded = false
                                    if (editingCell) commitEdit()
                                    exportPdfLauncher.launch(
                                        file.displayName.substringBeforeLast('.') + ".pdf",
                                    )
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Print\u2026") },
                                onClick = {
                                    menuExpanded = false
                                    printWorkbook()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Save as CSV\u2026") },
                                onClick = {
                                    menuExpanded = false
                                    saveCsvLauncher.launch(
                                        file.displayName.substringBeforeLast('.') + ".csv",
                                    )
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Rename sheet\u2026") },
                                onClick = {
                                    menuExpanded = false
                                    renameText = currentSheet()?.name.orEmpty()
                                    showRename = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Find and replace\u2026") },
                                onClick = {
                                    menuExpanded = false
                                    if (editingCell) commitEdit()
                                    findCursor = 0
                                    showFind = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Rows and columns\u2026") },
                                onClick = {
                                    menuExpanded = false
                                    if (editingCell) commitEdit()
                                    showStructure = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Dropdown choices\u2026") },
                                onClick = {
                                    menuExpanded = false
                                    openListDialog()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Column width\u2026") },
                                onClick = {
                                    menuExpanded = false
                                    openSizeDialog(SizeTarget.COLUMN)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Row height\u2026") },
                                onClick = {
                                    menuExpanded = false
                                    openSizeDialog(SizeTarget.ROW)
                                },
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if ((sheet?.frozenRows ?: 0) > 0) {
                                            "Unfreeze rows"
                                        } else {
                                            "Freeze rows above row ${selection.row + 1}"
                                        },
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    toggleFrozenRows()
                                },
                            )
                            val mergeRange = selectedRange()
                            val canMerge = mergeRange.rowSpan > 1 || mergeRange.columnSpan > 1
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text("Merge cells")
                                        Text(
                                            if (canMerge) {
                                                rangeLabel(mergeRange)
                                            } else {
                                                "Long-press a second cell to select a range"
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                },
                                enabled = canMerge,
                                onClick = {
                                    menuExpanded = false
                                    mergeSelection()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Unmerge cells") },
                                enabled = sheet?.mergedRanges?.any {
                                    it.intersects(selectedRange())
                                } == true,
                                onClick = {
                                    menuExpanded = false
                                    unmergeSelection()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Chart selected column") },
                                onClick = {
                                    menuExpanded = false
                                    if (editingCell) commitEdit()
                                    showChart = true
                                },
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Sort column A \u2192 Z") },
                                onClick = {
                                    menuExpanded = false
                                    sortBySelection(ascending = true)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Sort column Z \u2192 A") },
                                onClick = {
                                    menuExpanded = false
                                    sortBySelection(ascending = false)
                                },
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            Column {
                SheetFormatBar(
                    format = formatAt(selection),
                    onFormat = ::applyFormat,
                    onBorders = ::applyBorders,
                )
                SheetTabs(
                    sheets = workbook?.sheets.orEmpty(),
                    active = activeSheet,
                    onSelect = { index ->
                        if (editingCell) commitEdit()
                        activeSheet = index
                        selection = CellPos(0, 0)
                        selectionEnd = null
                        editText = ""
                    },
                    onAdd = ::addSheet,
                )
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (droppedFeatures.isNotEmpty() && !lossAccepted) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            PreservedCopy.banner(droppedFeatures),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        TextButton(
                            onClick = { showLossWarning = true },
                            modifier = Modifier.padding(top = 4.dp),
                        ) {
                            Text("What cannot be edited")
                        }
                    }
                }
            }
            FormulaBar(
                selection = selection,
                range = selectedRange(),
                text = editText,
                enabled = sheet != null,
                focusRequester = formulaFocus,
                onTextChange = {
                    editingCell = true
                    selectionEnd = null
                    editText = it
                },
                onCommit = {
                    commitEdit()
                    runCatching { gridFocus.requestFocus() }
                },

            )

            // A cell with a dropdown offers its choices right under the formula bar.
            val choices = sheet?.validationAt(selection.row, selection.column)?.values.orEmpty()
            if (choices.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    choices.forEach { choice ->
                        FilterChip(
                            selected = editText == choice,
                            onClick = {
                                if (editingCell) {
                                    editText = choice
                                    commitEdit()
                                } else {
                                    writeCell(selection, choice)
                                }
                                runCatching { gridFocus.requestFocus() }
                            },
                            label = { Text(choice) },
                        )
                    }
                }
            }

            when {
                loadError != null -> CenteredNotice(
                    title = "This spreadsheet could not be opened",
                    detail = ReadFailureCopy.describe(loadError),
                )
                sheet == null -> CenteredNotice(title = "Opening\u2026", detail = null)
                else -> SheetGrid(
                    sheet = sheet,
                    cells = cellsByPos,
                    selection = selection,
                    selectionRange = selectedRange().takeIf {
                        it.rowSpan > 1 || it.columnSpan > 1
                    },
                    editing = editingCell,
                    editText = editText,
                    scrollTargetRow = scrollTargetRow,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(gridFocus)
                        .focusable()
                        .onPreviewKeyEvent(::handleKey),
                    onSelect = { pos ->
                        select(pos)
                        runCatching { gridFocus.requestFocus() }
                    },
                    onExtendTo = { pos ->
                        extendSelectionTo(pos)
                        runCatching { gridFocus.requestFocus() }
                    },
                    onStartEdit = { pos ->
                        select(pos)
                        editingCell = true
                        editText = rawValueAt(pos)
                        runCatching { formulaFocus.requestFocus() }
                    },
                )
            }
        }
    }
}

@Composable
private fun FormulaBar(
    selection: CellPos,
    range: CellRange,
    text: String,
    enabled: Boolean,
    focusRequester: FocusRequester,
    onTextChange: (String) -> Unit,
    onCommit: () -> Unit,
) {
    val isRange = range.rowSpan > 1 || range.columnSpan > 1
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = rangeLabel(range),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .width(if (isRange) 82.dp else 56.dp)
                    // Spoken on the address itself, not on the field: a contentDescription
                    // on an editable node would replace the text TalkBack reads back.
                    .semantics {
                        contentDescription = if (isRange) {
                            "Selected range ${rangeLabel(range)}, active cell " +
                                "${columnName(selection.column)}${selection.row + 1}"
                        } else {
                            "Cell ${columnName(selection.column)}${selection.row + 1}"
                        }
                    },
                maxLines = 1,
            )
            Text(
                text = "fx",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 8.dp),
            )
            TextField(
                value = text,
                onValueChange = onTextChange,
                enabled = enabled,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onCommit() }),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
            )
            if (text.isNotEmpty()) {
                IconButton(onClick = onCommit) {
                    Icon(Icons.Filled.Check, contentDescription = "Apply and finish editing")
                }
            }
        }
    }
}

@Composable
private fun SheetGrid(
    sheet: Sheet,
    cells: Map<Pair<Int, Int>, Cell>,
    selection: CellPos,
    selectionRange: CellRange?,
    editing: Boolean,
    editText: String,
    modifier: Modifier = Modifier,
    scrollTargetRow: Int? = null,
    onSelect: (CellPos) -> Unit,
    onExtendTo: (CellPos) -> Unit,
    onStartEdit: (CellPos) -> Unit,
) {
    // One scroll state shared by the header and every row keeps the frozen headers aligned.
    val horizontal = rememberScrollState()
    val vertical = rememberLazyListState()
    val density = LocalDensity.current
    val columnCount = sheet.columnCount.coerceAtLeast(1)
    val rowCount = sheet.rowCount.coerceAtLeast(1)
    val columnWidths = remember(sheet) { List(columnCount) { sheet.columnWidthDp(it) } }
    val rowHeights = remember(sheet) { List(rowCount) { sheet.rowHeightDp(it) } }
    val columnWidthsPx = remember(columnWidths, density) {
        columnWidths.map { with(density) { it.roundToPx() } }
    }
    val rowHeightsPx = remember(rowHeights, density) {
        rowHeights.map { with(density) { it.roundToPx() } }
    }
    val bands = remember(sheet) { sheet.rowBands() }
    val active = remember(sheet, selection) {
        val merge = sheet.mergeAt(selection.row, selection.column)
        if (merge == null) selection else CellPos(merge.top, merge.left)
    }

    // A band that starts above the freeze line is frozen as a whole. A freeze
    // through a vertical merge would clip its text or leave half a cell behind.
    val frozenBands = remember(bands, sheet.frozenRows) {
        bands.count { it.startRow < sheet.frozenRows }
    }

    // Find & replace moves the cursor to a match, so the grid follows it.
    LaunchedEffect(scrollTargetRow, bands) {
        val row = scrollTargetRow ?: return@LaunchedEffect
        val index = bands.indexOfFirst { row in it.startRow..it.endRow }
        if (index >= frozenBands) {
            vertical.animateScrollToItem(index - frozenBands)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(modifier = Modifier.height(CELL_HEIGHT)) {
            Box(
                modifier = Modifier
                    .width(ROW_HEADER_WIDTH)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(horizontal),
            ) {
                Row {
                    repeat(columnCount) { column ->
                        Box(
                            modifier = Modifier
                                .width(with(density) { columnWidthsPx[column].toDp() })
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = columnName(column),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        // Frozen bands render outside the list, so they never scroll away.
        bands.take(frozenBands).forEach { band ->
            GridBand(
                band = band,
                cells = cells,
                sheet = sheet,
                active = active,
                selectionRange = selectionRange,
                editing = editing,
                editText = editText,
                horizontal = horizontal,
                columnWidthsPx = columnWidthsPx,
                rowHeightsPx = rowHeightsPx,
                onSelect = onSelect,
                onExtendTo = onExtendTo,
                onStartEdit = onStartEdit,
            )
        }

        LazyColumn(modifier = Modifier.weight(1f), state = vertical) {
            items(bands.drop(frozenBands), key = { it.startRow }) { band ->
                GridBand(
                    band = band,
                    cells = cells,
                    sheet = sheet,
                    active = active,
                    selectionRange = selectionRange,
                    editing = editing,
                    editText = editText,
                    horizontal = horizontal,
                    columnWidthsPx = columnWidthsPx,
                    rowHeightsPx = rowHeightsPx,
                    onSelect = onSelect,
                    onExtendTo = onExtendTo,
                    onStartEdit = onStartEdit,
                )
            }
        }
    }
}

/**
 * One band of model rows, laid out as an absolute grid rather than a Row. Rows
 * have to be bands because a vertically merged cell is taller than one row and
 * must be measured once, at full height. Every placement is measured at its
 * final size and placed at its offset, so merged anchors line up with the rest.
 */
@Composable
private fun GridBand(
    band: SheetRowBand,
    cells: Map<Pair<Int, Int>, Cell>,
    sheet: Sheet,
    active: CellPos,
    selectionRange: CellRange?,
    editing: Boolean,
    editText: String,
    horizontal: androidx.compose.foundation.ScrollState,
    columnWidthsPx: List<Int>,
    rowHeightsPx: List<Int>,
    onSelect: (CellPos) -> Unit,
    onExtendTo: (CellPos) -> Unit,
    onStartEdit: (CellPos) -> Unit,
) {
    val placements = remember(sheet, band) { sheet.cellPlacements(band) }
    val density = LocalDensity.current
    val bandRowHeights = remember(rowHeightsPx, band) {
        rowHeightsPx.subList(band.startRow, band.endRow + 1)
    }
    val columnOffsets = remember(columnWidthsPx) { prefixSums(columnWidthsPx) }
    val rowOffsets = remember(bandRowHeights) { prefixSums(bandRowHeights) }

    Row {
        Column(
            modifier = Modifier
                .width(ROW_HEADER_WIDTH)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            bandRowHeights.forEachIndexed { offset, heightPx ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(with(density) { heightPx.toDp() })
                        .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "${band.startRow + offset + 1}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(horizontal),
        ) {
            Layout(
                content = {
                    placements.forEach { placement ->
                        val rowOffset = placement.row - band.startRow
                        val widthPx = columnOffsets[placement.column + placement.columnSpan] -
                            columnOffsets[placement.column]
                        val heightPx = rowOffsets[rowOffset + placement.rowSpan] -
                            rowOffsets[rowOffset]
                        val selected = selectionRange?.let { range ->
                            range.intersects(
                                CellRange(
                                    top = placement.row,
                                    left = placement.column,
                                    bottom = placement.row + placement.rowSpan - 1,
                                    right = placement.column + placement.columnSpan - 1,
                                ),
                            )
                        } ?: false
                        SheetCell(
                            row = placement.row,
                            column = placement.column,
                            rowSpan = placement.rowSpan,
                            columnSpan = placement.columnSpan,
                            cell = cells[placement.row to placement.column],
                            sheet = sheet,
                            selected = selected,
                            active = active.row == placement.row &&
                                active.column == placement.column,
                            editing = editing && active.row == placement.row &&
                                active.column == placement.column,
                            editText = editText,
                            width = with(density) { widthPx.toDp() },
                            height = with(density) { heightPx.toDp() },
                            onClick = { onSelect(CellPos(placement.row, placement.column)) },
                            onLongClick = {
                                onExtendTo(CellPos(placement.row, placement.column))
                            },
                            onDoubleClick = {
                                onStartEdit(CellPos(placement.row, placement.column))
                            },
                        )
                    }
                },
            ) { measurables, _ ->
                val measured = measurables.mapIndexed { index, measurable ->
                    val placement = placements[index]
                    val rowOffset = placement.row - band.startRow
                    val widthPx = columnOffsets[placement.column + placement.columnSpan] -
                        columnOffsets[placement.column]
                    val heightPx = rowOffsets[rowOffset + placement.rowSpan] -
                        rowOffsets[rowOffset]
                    measurable.measure(
                        androidx.compose.ui.unit.Constraints.fixed(widthPx, heightPx),
                    )
                }
                layout(columnOffsets.last(), rowOffsets.last()) {
                    placements.forEachIndexed { index, placement ->
                        measured[index].placeRelative(
                            x = columnOffsets[placement.column],
                            y = rowOffsets[placement.row - band.startRow],
                        )
                    }
                }
            }
        }
    }
}

/** Prefix sums for the pixel offsets inside one band. */
private fun prefixSums(values: List<Int>): IntArray {
    val sums = IntArray(values.size + 1)
    values.forEachIndexed { index, value -> sums[index + 1] = sums[index] + value }
    return sums
}

/**
 * The colour a cell's text is drawn in. A light fill needs dark text: the
 * dark theme's on-surface is near-white, so a pale fill swallows the text.
 * An explicit font colour always wins, as in the PDF export.
 */
@Composable
private fun cellTextColour(format: CellFormat): Color {
    format.colorRgb?.let { return Color(0xFF000000.toInt() or it) }
    val fill = format.fillRgb ?: return MaterialTheme.colorScheme.onSurface
    return if (isLightColour(fill)) Color(0xFF191C1B.toInt()) else MaterialTheme.colorScheme.onSurface
}

/** True when a colour is light enough that dark text reads better on it. */
internal fun isLightColour(rgb: Int): Boolean {
    val red = (rgb shr 16) and 0xFF
    val green = (rgb shr 8) and 0xFF
    val blue = rgb and 0xFF
    return (0.299 * red + 0.587 * green + 0.114 * blue) > 140
}

@Composable
private fun SheetCell(
    row: Int,
    column: Int,
    rowSpan: Int,
    columnSpan: Int,
    cell: Cell?,
    sheet: Sheet,
    selected: Boolean,
    active: Boolean,
    editing: Boolean,
    editText: String,
    width: Dp,
    height: Dp,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDoubleClick: () -> Unit,
) {
    val format = cell?.format ?: CellFormat()
    val text = if (editing) editText else displayValue(cell, sheet)
    val background = when {
        active -> MaterialTheme.colorScheme.primaryContainer
        selected -> MaterialTheme.colorScheme.secondaryContainer
        format.fillRgb != null -> Color(0xFF000000.toInt() or format.fillRgb!!)
        else -> MaterialTheme.colorScheme.surface
    }
    val decoration = when {
        format.underline && format.strike -> TextDecoration.combine(
            listOf(TextDecoration.Underline, TextDecoration.LineThrough),
        )
        format.underline -> TextDecoration.Underline
        format.strike -> TextDecoration.LineThrough
        else -> null
    }

    // Hoisted so the semantics block reads like the rest of the file: plain values.
    val isSelected = selected || active
    val label = buildString {
        append(columnName(column))
        append(row + 1)
        if (rowSpan > 1 || columnSpan > 1) {
            append(':')
            append(columnName(column + columnSpan - 1))
            append(row + rowSpan)
        }
        val value = if (editing) editText else text.ifEmpty { "empty" }
        append(", ")
        append(value)
    }

    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .background(background)
            .border(
                width = if (active) 2.dp else 0.5.dp,
                color = if (active) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                },
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
                onDoubleClick = onDoubleClick,
            )
            .semantics {
                // Coordinate + value, so a screen-reader user hears "B3, 42", not a bare number.
                this.contentDescription = label
                this.selected = isSelected
                role = Role.Button
            },
        contentAlignment = when (format.alignment) {
            CellAlignment.LEFT -> Alignment.CenterStart
            CellAlignment.CENTER -> Alignment.Center
            CellAlignment.RIGHT -> Alignment.CenterEnd
            CellAlignment.GENERAL -> Alignment.CenterStart
        },
    ) {
        // The cell's own borders, drawn over the grid lines
        val borders = format.borders
        if (!borders.isEmpty) {
            val lineColor = MaterialTheme.colorScheme.onSurface
            Box(modifier = Modifier.fillMaxSize()) {
                if (borders.top != BorderStyle.NONE) {
                    Box(
                        Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .height(borderWidth(borders.top))
                            .background(lineColor),
                    )
                }
                if (borders.bottom != BorderStyle.NONE) {
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(borderWidth(borders.bottom))
                            .background(lineColor),
                    )
                }
                if (borders.left != BorderStyle.NONE) {
                    Box(
                        Modifier
                            .align(Alignment.CenterStart)
                            .fillMaxHeight()
                            .width(borderWidth(borders.left))
                            .background(lineColor),
                    )
                }
                if (borders.right != BorderStyle.NONE) {
                    Box(
                        Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                            .width(borderWidth(borders.right))
                            .background(lineColor),
                    )
                }
            }
        }
        Text(
            text = text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp),
            style = TextStyle(
                // sp follows the system font scale; the 13 pt fallback is the cell's own size.
                fontSize = (format.sizePt ?: 13f).sp,
                fontWeight = if (format.bold) FontWeight.Bold else FontWeight.Normal,
                fontStyle = if (format.italic) FontStyle.Italic else FontStyle.Normal,
                textDecoration = decoration,
                color = cellTextColour(format),
                textAlign = when (format.alignment) {
                    CellAlignment.CENTER -> TextAlign.Center
                    CellAlignment.RIGHT -> TextAlign.End
                    else -> TextAlign.Start
                },
            ),
        )
    }
}

@Composable
private fun SheetFormatBar(
    format: CellFormat,
    onFormat: ((CellFormat) -> CellFormat) -> Unit,
    onBorders: (BorderKind) -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SheetAction(
                icon = Icons.Filled.FormatBold,
                label = "Bold",
                enabled = true,
                selected = format.bold,
                onClick = { onFormat { it.copy(bold = !it.bold) } },
            )
            SheetAction(
                icon = Icons.Filled.FormatItalic,
                label = "Italic",
                enabled = true,
                selected = format.italic,
                onClick = { onFormat { it.copy(italic = !it.italic) } },
            )
            SheetAction(
                icon = Icons.Filled.FormatUnderlined,
                label = "Underline",
                enabled = true,
                selected = format.underline,
                onClick = { onFormat { it.copy(underline = !it.underline) } },
            )
            SheetAction(
                icon = Icons.Filled.FormatStrikethrough,
                label = "Strikethrough",
                enabled = true,
                selected = format.strike,
                onClick = { onFormat { it.copy(strike = !it.strike) } },
            )
            SheetDivider()
            SizeMenu(
                currentSizePt = format.sizePt,
                onSize = { size -> onFormat { it.copy(sizePt = size) } },
            )
            ColourMenu(
                currentColourRgb = format.colorRgb,
                onColour = { rgb -> onFormat { it.copy(colorRgb = rgb) } },
            )
            SheetDivider()
            var moreOpen by remember { mutableStateOf(false) }
            IconButton(onClick = { moreOpen = true }) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = "More formatting",
                )
            }
            if (moreOpen) {
                SheetMoreSheet(
                    format = format,
                    onFormat = onFormat,
                    onBorders = onBorders,
                    onDismiss = { moreOpen = false },
                )
            }
        }
    }
}

/**
 * Overflow for the sheet format bar: fill swatches, alignment and number
 * format, three rows of controls instead of nineteen stacked menu items.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun SheetMoreSheet(
    format: CellFormat,
    onFormat: ((CellFormat) -> CellFormat) -> Unit,
    onBorders: (BorderKind) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Fill colour", style = MaterialTheme.typography.titleSmall)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    FILL_OPTIONS.forEach { rgb ->
                        val selected = format.fillRgb == rgb
                        val swatchName = fillColourName(rgb)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                // A colour swatch carries its choice in colour
                                // alone, so it needs a spoken name and a state.
                                .semantics {
                                    contentDescription = swatchName
                                    this.selected = selected
                                    role = Role.RadioButton
                                }
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    rgb?.let { Color(0xFF000000.toInt() or it) }
                                        ?: MaterialTheme.colorScheme.surfaceContainerHigh,
                                )
                                .border(
                                    width = if (selected) 2.dp else 1.dp,
                                    color = if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant,
                                    shape = RoundedCornerShape(8.dp),
                                )
                                .clickable { onFormat { it.copy(fillRgb = rgb) } },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (rgb == null) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else if (selected) {
                                // A second, non-colour cue for the active swatch.
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Alignment", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        CellAlignment.GENERAL to "General",
                        CellAlignment.LEFT to "Left",
                        CellAlignment.CENTER to "Centre",
                        CellAlignment.RIGHT to "Right",
                    ).forEach { (value, label) ->
                        FilterChip(
                            selected = format.alignment == value,
                            onClick = { onFormat { it.copy(alignment = value) } },
                            label = { Text(label) },
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Number format", style = MaterialTheme.typography.titleSmall)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    NUMBER_FORMATS.forEach { (code, label) ->
                        FilterChip(
                            selected = format.numberFormat == code,
                            onClick = { onFormat { it.copy(numberFormat = code) } },
                            label = { Text(label) },
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Borders", style = MaterialTheme.typography.titleSmall)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    BORDER_ACTIONS.forEach { (kind, label) ->
                        FilterChip(
                            selected = false,
                            onClick = { onBorders(kind) },
                            label = { Text(label) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val isSelected = selected
    IconButton(
        onClick = onClick,
        enabled = enabled,
        // Bold/Italic/etc. showed their state by tint only. It is a semantic
        // property now, so TalkBack says "Bold, on" / "Bold, off".
        modifier = Modifier.semantics {
            this.contentDescription = label
            stateDescription = if (isSelected) "On" else "Off"
        },
    ) {
        Icon(
            imageVector = icon,
            // The button node carries the name and state; a second description
            // would be announced twice.
            contentDescription = null,
            tint = when {
                !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                isSelected -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
private fun SheetDivider() {
    Spacer(
        modifier = Modifier
            .padding(horizontal = 4.dp)
            .width(1.dp)
            .height(24.dp)
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
}

/** The border actions offered in the formatting sheet. */
private val BORDER_ACTIONS: List<Pair<BorderKind, String>> = listOf(
    BorderKind.NONE to "None",
    BorderKind.TOP to "Top",
    BorderKind.BOTTOM to "Bottom",
    BorderKind.LEFT to "Left",
    BorderKind.RIGHT to "Right",
    BorderKind.ALL to "All",
    BorderKind.BOX to "Box",
)

private val FILL_OPTIONS: List<Int?> = listOf(    null, 0xFFF2CC, 0xD8F3E4, 0xDCE7FF, 0xFBD9D9, 0xEFE4FF, 0xEDEDED,
)

/** The spoken name of a fill swatch; the colour alone is not enough for TalkBack. */
internal fun fillColourName(rgb: Int?): String = when (rgb) {
    null -> "No fill"
    0xFFF2CC -> "Yellow fill"
    0xD8F3E4 -> "Green fill"
    0xDCE7FF -> "Blue fill"
    0xFBD9D9 -> "Red fill"
    0xEFE4FF -> "Purple fill"
    0xEDEDED -> "Grey fill"
    else -> "Fill colour"
}

@Composable
private fun SheetTabs(
    sheets: List<Sheet>,
    active: Int,
    onSelect: (Int) -> Unit,
    onAdd: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            sheets.forEachIndexed { index, sheet ->
                val selected = index == active
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        // A tab's active state was colour-only. TalkBack now hears
                        // the name plus whether it is the current sheet, and the
                        // selected state is a semantic property too.
                        .semantics(mergeDescendants = true) {
                            contentDescription = "Sheet ${sheet.name}"
                            this.selected = selected
                            role = Role.Tab
                        }
                        .clickable { onSelect(index) },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = sheet.name,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (selected) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            maxLines = 1,
                        )
                    }
                }
            }
            IconButton(onClick = onAdd, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Filled.Add, contentDescription = "Add sheet")
            }
        }
    }
}

@Composable
private fun CenteredNotice(title: String, detail: String?) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (detail != null) {
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

/** What the grid shows for a cell: computed formula results, never the formula itself. */
private fun displayValue(cell: Cell?, sheet: Sheet): String {
    if (cell == null || cell.value.isEmpty()) return ""
    val raw = cell.value
    if (raw.startsWith("=")) {
        return formatCellValue(evaluateFormula(sheet, raw).value, cell.format.numberFormat)
    }
    val number = raw.toDoubleOrNull()
    return if (number != null) {
        formatNumber(number, cell.format.numberFormat)
    } else {
        raw
    }
}

private fun isCsv(displayName: String): Boolean =
    displayName.substringAfterLast('.', "").equals("csv", ignoreCase = true)
