package org.ziyoffice.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.platform.LocalContext
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.ByteArrayInputStream
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ziyoffice.data.DraftStore
import org.ziyoffice.data.RecentFilesStore
import org.ziyoffice.model.Cell
import org.ziyoffice.model.CellAlignment
import org.ziyoffice.model.CellFormat
import org.ziyoffice.model.CellValue
import org.ziyoffice.model.chartPointsFrom
import org.ziyoffice.model.RecentFile
import org.ziyoffice.model.Sheet
import org.ziyoffice.model.SheetSort
import org.ziyoffice.model.Workbook
import org.ziyoffice.model.columnName
import org.ziyoffice.model.evaluateFormula
import org.ziyoffice.model.formatCellValue
import org.ziyoffice.model.generalNumber
import org.ziyoffice.model.isDateFormat
import org.ziyoffice.model.parseDateInput
import org.ziyoffice.model.formatNumber
import org.ziyoffice.model.parseCsv
import org.ziyoffice.model.writeCsv
import org.ziyoffice.export.Printer
import org.ziyoffice.export.SheetExporter
import org.ziyoffice.ooxml.ContentAudit
import org.ziyoffice.ooxml.DroppedFeature
import org.ziyoffice.ooxml.XlsxReader
import org.ziyoffice.ooxml.XlsxWriter
import org.ziyoffice.saf.XLSX_MIME_TYPE

/** A 0-based cell address inside the active sheet. */
private data class CellPos(val row: Int, val column: Int)

private val CELL_WIDTH = 76.dp
private val CELL_HEIGHT = 40.dp
private val ROW_HEADER_WIDTH = 44.dp
private const val UNDO_LIMIT = 100

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
 * The spreadsheet editor: a scrollable grid with frozen row/column headers,
 * a formula bar, full keyboard navigation and cell formatting.
 *
 * Everything is computed against the [Sheet] model, so a formula shows its value
 * while the formula bar keeps the formula — the same split every spreadsheet user
 * expects. Saving writes a real `.xlsx` through [XlsxWriter]; unsaved work is kept
 * in a local draft so a process death never costs the user their typing.
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
    // and navigates in the same frame, which would cancel a composition scope.
    val writeScope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate) }
    val snackbarHostState = remember { SnackbarHostState() }
    val draftStore = remember { DraftStore(context) }

    var workbook by remember(file.uri) { mutableStateOf<Workbook?>(null) }
    var loadError by remember(file.uri) { mutableStateOf<String?>(null) }
    var activeSheet by remember(file.uri) { mutableStateOf(0) }
    var selection by remember(file.uri) { mutableStateOf(CellPos(0, 0)) }
    var editText by remember(file.uri) { mutableStateOf("") }
    var editingCell by remember(file.uri) { mutableStateOf(false) }
    var dirty by remember(file.uri) { mutableStateOf(false) }
    // Bumped by every mutation, so an in-flight write can tell whether newer
    // edits appeared while it was running.
    var writeVersion by remember(file.uri) { mutableStateOf(0L) }
    var saving by remember(file.uri) { mutableStateOf(false) }
    var undoStack by remember(file.uri) { mutableStateOf(emptyList<Workbook>()) }
    var redoStack by remember(file.uri) { mutableStateOf(emptyList<Workbook>()) }
    var menuExpanded by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var showChart by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }
    // Content-loss gate, same contract as the Docs editor: the writer rebuilds
    // the workbook from the model, so parts it cannot reproduce are announced
    // before the first save rather than discovered afterwards.
    var droppedFeatures by remember(file.uri) { mutableStateOf(emptyList<DroppedFeature>()) }
    var showLossWarning by remember(file.uri) { mutableStateOf(false) }
    var lossAccepted by remember(file.uri) { mutableStateOf(false) }
    var pendingAfterLoss by remember(file.uri) { mutableStateOf<(() -> Unit)?>(null) }

    val gridFocus = remember { FocusRequester() }
    val formulaFocus = remember { FocusRequester() }

    val sheet = workbook?.sheets?.getOrNull(activeSheet)
    val cellsByPos = remember(sheet) {
        sheet?.cells?.associateBy { it.row to it.column }.orEmpty()
    }

    LaunchedEffect(file.uri) {
        val loaded = withContext(Dispatchers.IO) {
            runCatching {
                val draft = draftStore.load(file.uri)
                if (draft != null) {
                    // The draft holds the user's unsaved cells; the source file
                    // still decides what a save would drop, so audit the source
                    // and keep warning while the recovery copy is in use.
                    val dropped = if (isCsv(file.displayName)) {
                        emptyList()
                    } else {
                        runCatching {
                            context.contentResolver.openInputStream(Uri.parse(file.uri))
                                ?.use { stream -> ContentAudit.xlsx(stream.readBytes()) }
                                .orEmpty()
                        }.getOrDefault(emptyList())
                    }
                    Triple(XlsxReader().read(ByteArrayInputStream(draft)), true, dropped)
                } else if (isCsv(file.displayName)) {
                    val text = context.contentResolver.openInputStream(Uri.parse(file.uri))
                        ?.bufferedReader()?.use { it.readText() }
                        ?: error("the file could not be opened")
                    Triple(
                        Workbook(listOf(parseCsv(text, file.displayName.substringBeforeLast('.')))),
                        false,
                        emptyList(),
                    )
                } else {
                    val stream = context.contentResolver.openInputStream(Uri.parse(file.uri))
                        ?: error("the file could not be opened")
                    // One read serves both the model and the audit, so the warning
                    // describes exactly this file.
                    val bytes = stream.use { it.readBytes() }
                    val dropped = runCatching { ContentAudit.xlsx(bytes) }.getOrDefault(emptyList())
                    Triple(XlsxReader().read(ByteArrayInputStream(bytes)), false, dropped)
                }
            }
        }
        loaded.fold(
            onSuccess = { (loadedWorkbook, recovered, dropped) ->
                workbook = loadedWorkbook
                droppedFeatures = dropped
                activeSheet = loadedWorkbook.activeSheet.coerceIn(
                    0,
                    (loadedWorkbook.sheets.size - 1).coerceAtLeast(0),
                )
                dirty = recovered
                if (recovered) {
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
                draftStore.save(file.uri, XlsxWriter().write(target))
            }.isSuccess
        }
        if (!saved) {
            snackbarHostState.showSnackbar("Could not save your draft")
        }
    }

    LaunchedEffect(initialEditing, workbook) {
        if (initialEditing && workbook != null) runCatching { gridFocus.requestFocus() }
    }

    // A keystroke in the last 1.5 s of a session exists only in this process:
    // the debounced autosave above has not run yet and composable state is not
    // rememberSaveable. Leaving composition is the last chance, so the draft is
    // flushed on a thread that outlives the cancelled composition scope — the
    // serialization happens once, in onDispose, not on every recomposition.
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
                // A composition scope is cancelled before onDispose runs, so a
                // plain scope.launch would never land. A daemon thread always
                // finishes, and this is a small file write (a few milliseconds).
                Thread {
                    runCatching {
                        store.save(uri, XlsxWriter().write(target.copy(activeSheet = sheetIndex)))
                    }
                }.apply { name = "draft-flush" }.start()
            }
        }
    }

    fun currentSheet(): Sheet? = workbook?.sheets?.getOrNull(activeSheet)

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
        // then left by tapping another cell would never be saved or drafted.
        if (text != rawValueAt(selection)) writeCell(selection, normaliseInput(text, formatAt(selection)))
    }


    fun select(pos: CellPos) {
        val target = currentSheet() ?: return
        val clamped = CellPos(
            row = pos.row.coerceIn(0, (target.rowCount - 1).coerceAtLeast(0)),
            column = pos.column.coerceIn(0, (target.columnCount - 1).coerceAtLeast(0)),
        )
        if (editingCell && clamped != selection) commitEdit()
        selection = clamped
        editText = rawValueAt(clamped)
    }

    fun clearSelectedCell() {
        val pos = selection
        if (editingCell) commitEdit()
        if (rawValueAt(pos).isEmpty()) return
        mutate { target ->
            target.copy(
                cells = target.cells.filterNot { it.row == pos.row && it.column == pos.column },
            )
        }
        editText = ""
    }

    /** Freezes the rows above the cursor, or clears the freeze when one is set. */
    fun toggleFrozenRows() {
        if (editingCell) commitEdit()
        val current = currentSheet() ?: return
        mutate {
            it.copy(frozenRows = if (it.frozenRows > 0) 0 else selection.row)
        }
    }

    /**
     * Sorts every used row of the active sheet by the column the cursor is in.
     * The whole used block moves, so rows keep their cells together.
     */
    fun sortBySelection(ascending: Boolean) {
        val current = currentSheet() ?: return
        val lastRow = current.cells.maxOfOrNull { it.row } ?: return
        if (lastRow < 1) return
        if (editingCell) commitEdit()

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
        val pos = selection
        writeCell(pos, rawValueAt(pos), transform(formatAt(pos)))
        editText = rawValueAt(pos)
    }

    fun moveSelection(rowDelta: Int, columnDelta: Int) {
        if (editingCell) commitEdit()
        select(CellPos(selection.row + rowDelta, selection.column + columnDelta))
    }

    fun startTyping(character: Char) {
        editingCell = true
        editText = character.toString()
        runCatching { formulaFocus.requestFocus() }
    }

    fun handleKey(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        if (event.isCtrlPressed) return false
        return when (event.key) {
            Key.DirectionLeft -> { moveSelection(0, -1); true }
            Key.DirectionRight -> { moveSelection(0, 1); true }
            Key.DirectionUp -> { moveSelection(-1, 0); true }
            Key.DirectionDown -> { moveSelection(1, 0); true }
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
        editingCell = false
        editText = rawValueAt(selection)
        dirty = true
        writeVersion++
    }

    /** Writes [current] to the source file, reporting the real outcome. */
    fun writeWorkbook(current: Workbook, onSaved: () -> Unit) {
        val writingVersion = writeVersion
        saving = true
        writeScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = if (isCsv(file.displayName)) {
                        writeCsv(current.sheets[activeSheet]).encodeToByteArray()
                    } else {
                        XlsxWriter().write(current.copy(activeSheet = activeSheet))
                    }
                    val stream = context.contentResolver.openOutputStream(Uri.parse(file.uri), "wt")
                        ?: error("the file could not be opened for writing")
                    stream.use { it.write(bytes) }
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
                        // Edits landed while the write was running: the newer
                        // work stays dirty and keeps its draft recovery copy.
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
     * Confirms once, before the first write, when saving would remove workbook
     * content the writer cannot reproduce.
     */
    fun save(onSaved: () -> Unit = {}) {
        val current = workbook ?: return
        if (
            ContentLossCopy.requiresConfirmation(
                dropped = droppedFeatures,
                accepted = lossAccepted,
                writesWholeFormat = !isCsv(file.displayName),
            )
        ) {
            pendingAfterLoss = onSaved
            showLossWarning = true
            return
        }
        writeWorkbook(current, onSaved)
    }

    /**
     * The single exit path: save-and-leave when work is dirty, leave immediately
     * when it is not. Both the system back gesture and the toolbar arrow use it.
     */
    fun tryExit() {
        if (editingCell) commitEdit()
        when (exitAction(dirty = dirty, saving = saving, needsConfirmation = false)) {
            ExitAction.LEAVE -> onBack()
            ExitAction.BLOCK ->
                // A second write racing the first could land out of order; the
                // in-flight one already reports its own outcome.
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
                    val bytes = XlsxWriter().write(current.copy(activeSheet = activeSheet))
                    context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) }
                        ?: error("could not write")
                }
            }
            result.fold(
                onSuccess = {
                    val entry = RecentFilesStore(context).remember(uri)
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
     * memory first, so a failure to render is reported here rather than inside
     * the print framework.
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
                    val entry = RecentFilesStore(context).remember(uri)
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
                    val entry = RecentFilesStore(context).remember(uri)
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
        // Captured for this dialog: a pending action means the warning gates a
        // save; none means the user opened it from the banner to read the list.
        val gatingSave = pendingAfterLoss != null
        androidx.compose.material3.AlertDialog(
            onDismissRequest = {
                showLossWarning = false
                pendingAfterLoss = null
            },
            title = { Text("This save removes content") },
            text = {
                Text(
                    ContentLossCopy.body(droppedFeatures, ".xlsx") +
                        "\n\nUnsaved edits stay in this session if you cancel.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLossWarning = false
                        val next = pendingAfterLoss
                        pendingAfterLoss = null
                        if (next != null) {
                            // Accepted for this session: later saves of the same
                            // workbook are not re-asked.
                            lossAccepted = true
                            save(next)
                        }
                    },
                ) { Text(if (gatingSave) "Save anyway" else "Understood") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showLossWarning = false
                        pendingAfterLoss = null
                    },
                ) { Text("Cancel") }
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

    if (showChart) {
        // Commit first, in the event handler that opened the sheet — not here.
        // The body below runs on every recomposition while the sheet is open, and
        // mutating editor state during composition would re-trigger it.
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
                )
                SheetTabs(
                    sheets = workbook?.sheets.orEmpty(),
                    active = activeSheet,
                    onSelect = { index ->
                        if (editingCell) commitEdit()
                        activeSheet = index
                        selection = CellPos(0, 0)
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
                            ContentLossCopy.banner(droppedFeatures),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        TextButton(
                            onClick = {
                                pendingAfterLoss = null
                                showLossWarning = true
                            },
                            modifier = Modifier.padding(top = 4.dp),
                        ) {
                            Text("What will be removed")
                        }
                    }
                }
            }
            FormulaBar(
                selection = selection,
                text = editText,
                enabled = sheet != null,
                focusRequester = formulaFocus,
                onTextChange = {
                    editingCell = true
                    editText = it
                },
                onCommit = {
                    commitEdit()
                    runCatching { gridFocus.requestFocus() }
                },

            )

            when {
                loadError != null -> CenteredNotice(
                    title = "This spreadsheet could not be opened",
                    detail = loadError.orEmpty(),
                )
                sheet == null -> CenteredNotice(title = "Opening\u2026", detail = null)
                else -> SheetGrid(
                    sheet = sheet,
                    cells = cellsByPos,
                    selection = selection,
                    editing = editingCell,
                    editText = editText,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(gridFocus)
                        .focusable()
                        .onPreviewKeyEvent(::handleKey),
                    onSelect = { pos ->
                        select(pos)
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

/* ------------------------------- formula bar ------------------------------- */

@Composable
private fun FormulaBar(
    selection: CellPos,
    text: String,
    enabled: Boolean,
    focusRequester: FocusRequester,
    onTextChange: (String) -> Unit,
    onCommit: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = columnName(selection.column) + (selection.row + 1),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .width(56.dp)
                    // Spoken on the address itself rather than on the field: a
                    // contentDescription on an editable node would replace the
                    // text TalkBack reads back as the user types.
                    .semantics { contentDescription = "Cell ${columnName(selection.column)}${selection.row + 1}" },
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

/* ---------------------------------- grid ---------------------------------- */

@Composable
private fun SheetGrid(
    sheet: Sheet,
    cells: Map<Pair<Int, Int>, Cell>,
    selection: CellPos,
    editing: Boolean,
    editText: String,
    modifier: Modifier = Modifier,
    onSelect: (CellPos) -> Unit,
    onStartEdit: (CellPos) -> Unit,
) {
    // One scroll state shared by the header and every row keeps the frozen row
    // headers aligned with the grid while columns scroll.
    val horizontal = rememberScrollState()
    val rowCount = sheet.rowCount.coerceAtLeast(1)
    val columnCount = sheet.columnCount.coerceAtLeast(1)

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
                                .width(CELL_WIDTH)
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

        // Rows above frozenRows render outside the list, so they never scroll away.
        val frozen = sheet.frozenRows.coerceIn(0, rowCount - 1)
        repeat(frozen) { row ->
            GridRow(
                row = row,
                columnCount = columnCount,
                cells = cells,
                sheet = sheet,
                selection = selection,
                editing = editing,
                editText = editText,
                horizontal = horizontal,
                onSelect = onSelect,
                onStartEdit = onStartEdit,
            )
        }

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(rowCount - frozen) { index ->
                GridRow(
                    row = frozen + index,
                    columnCount = columnCount,
                    cells = cells,
                    sheet = sheet,
                    selection = selection,
                    editing = editing,
                    editText = editText,
                    horizontal = horizontal,
                    onSelect = onSelect,
                    onStartEdit = onStartEdit,
                )
            }
        }
    }
}

@Composable
private fun GridRow(
    row: Int,
    columnCount: Int,
    cells: Map<Pair<Int, Int>, Cell>,
    sheet: Sheet,
    selection: CellPos,
    editing: Boolean,
    editText: String,
    horizontal: androidx.compose.foundation.ScrollState,
    onSelect: (CellPos) -> Unit,
    onStartEdit: (CellPos) -> Unit,
) {
    Row(modifier = Modifier.height(CELL_HEIGHT)) {
        Box(
            modifier = Modifier
                .width(ROW_HEADER_WIDTH)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "${row + 1}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(horizontal),
        ) {
            Row {
                repeat(columnCount) { col ->
                    SheetCell(
                        row = row,
                        column = col,
                        cell = cells[row to col],
                        sheet = sheet,
                        selected = selection.row == row && selection.column == col,
                        editing = editing && selection.row == row && selection.column == col,
                        editText = editText,
                        onClick = { onSelect(CellPos(row, col)) },
                        onDoubleClick = { onStartEdit(CellPos(row, col)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SheetCell(
    row: Int,
    column: Int,
    cell: Cell?,
    sheet: Sheet,
    selected: Boolean,
    editing: Boolean,
    editText: String,
    onClick: () -> Unit,
    onDoubleClick: () -> Unit,
) {
    val format = cell?.format ?: CellFormat()
    val text = if (editing) editText else displayValue(cell, sheet)
    val background = when {
        selected -> MaterialTheme.colorScheme.primaryContainer
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

    // Hoisted so the semantics block reads like the rest of the file: plain
    // values, not assignments captured from receiver-scope properties.
    val isSelected = selected
    val label = buildString {
        append(columnName(column))
        append(row + 1)
        val value = if (editing) editText else text.ifEmpty { "empty" }
        append(", ")
        append(value)
    }

    Box(
        modifier = Modifier
            .width(CELL_WIDTH)
            .fillMaxHeight()
            .background(background)
            .border(
                width = if (selected) 2.dp else 0.5.dp,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                },
            )
            .combinedClickable(onClick = onClick, onDoubleClick = onDoubleClick)
            .semantics {
                // Coordinate + value, so a screen-reader user hears "B3, 42"
                // rather than a bare number with no address.
                this.contentDescription = label
                this.selected = isSelected
                role = Role.Button
            }
            .padding(horizontal = 4.dp),
        contentAlignment = when (format.alignment) {
            CellAlignment.LEFT -> Alignment.CenterStart
            CellAlignment.CENTER -> Alignment.Center
            CellAlignment.RIGHT -> Alignment.CenterEnd
            CellAlignment.GENERAL -> Alignment.CenterStart
        },
    ) {
        Text(
            text = text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(
                // sp follows the system font scale; the 13 pt fallback is the
                // cell's own size, not a screen-density guess.
                fontSize = (format.sizePt ?: 13f).sp,
                fontWeight = if (format.bold) FontWeight.Bold else FontWeight.Normal,
                fontStyle = if (format.italic) FontStyle.Italic else FontStyle.Normal,
                textDecoration = decoration,
                color = format.colorRgb?.let { Color(0xFF000000.toInt() or it) }
                    ?: MaterialTheme.colorScheme.onSurface,
                textAlign = when (format.alignment) {
                    CellAlignment.CENTER -> TextAlign.Center
                    CellAlignment.RIGHT -> TextAlign.End
                    else -> TextAlign.Start
                },
            ),
        )
    }
}

/* ------------------------------ formatting bar ----------------------------- */

@Composable
private fun SheetFormatBar(
    format: CellFormat,
    onFormat: ((CellFormat) -> CellFormat) -> Unit,
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
                    onDismiss = { moreOpen = false },
                )
            }
        }
    }
}

/**
 * Overflow for the sheet format bar: one compact panel with fill swatches, alignment and
 * number format — three rows of controls instead of nineteen stacked menu items.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun SheetMoreSheet(
    format: CellFormat,
    onFormat: ((CellFormat) -> CellFormat) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
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
        // Bold/Italic/etc. showed their on/off state only by tint. The state is
        // now a semantic property, so TalkBack says "Bold, on" / "Bold, off".
        modifier = Modifier.semantics {
            this.contentDescription = label
            stateDescription = if (isSelected) "On" else "Off"
        },
    ) {
        Icon(
            imageVector = icon,
            // The button node carries the name and state; a second description
            // on the icon would be announced twice.
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

private val FILL_OPTIONS: List<Int?> = listOf(
    null, 0xFFF2CC, 0xD8F3E4, 0xDCE7FF, 0xFBD9D9, 0xEFE4FF, 0xEDEDED,
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

/* ---------------------------------- tabs ---------------------------------- */

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
                        // A tab's active state was colour-only; TalkBack now hears
                        // the name plus whether it is the current sheet, and the
                        // selected state is exposed as a semantic property too.
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

/* -------------------------------- formatting ------------------------------- */

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
