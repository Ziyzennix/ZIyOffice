package org.ziyoffice.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.io.ByteArrayInputStream
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ziyoffice.data.DraftStore
import org.ziyoffice.model.BorderKind
import org.ziyoffice.model.Cell
import org.ziyoffice.model.CellFormat
import org.ziyoffice.model.CellRange
import org.ziyoffice.model.CellRangeCorner
import org.ziyoffice.model.NamedRange
import org.ziyoffice.model.RecentFile
import org.ziyoffice.model.Sheet
import org.ziyoffice.model.Workbook
import org.ziyoffice.model.evaluateFormula
import org.ziyoffice.model.withMergesClearedIn
import org.ziyoffice.model.generalNumber
import org.ziyoffice.model.isDateFormat
import org.ziyoffice.model.parseCsv
import org.ziyoffice.model.parseDateInput
import org.ziyoffice.model.withBorders
import org.ziyoffice.model.withValidation
import org.ziyoffice.model.withValidationCleared
import org.ziyoffice.model.writeCsv
import org.ziyoffice.ooxml.ContentAudit
import org.ziyoffice.ooxml.PackageKind
import org.ziyoffice.ooxml.PreservedFeature
import org.ziyoffice.ooxml.XlsxEditor
import org.ziyoffice.ooxml.XlsxReader
import org.ziyoffice.ooxml.XlsxSource
import org.ziyoffice.ooxml.XlsxWriter
import org.ziyoffice.saf.SafeWrite

private const val SHEET_UNDO_LIMIT = 50

internal data class SheetUiState(
    val workbook: Workbook? = null,
    val activeSheet: Int = 0,
    val selection: CellPos = CellPos(0, 0),
    val selectionEnd: CellPos? = null,
    val editText: String = "",
    val editingCell: Boolean = false,
    val dirty: Boolean = false,
    val saving: Boolean = false,
    val loadError: String? = null,
    val draftRecovered: Boolean = false,
    val droppedFeatures: List<PreservedFeature> = emptyList(),
    val sourcePackage: XlsxSource? = null,
    val writeVersion: Long = 0L,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val lossAccepted: Boolean = false,
)

/**
 * State and business operations for spreadsheet viewing and editing.
 */
internal class SheetViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(SheetUiState())
    val uiState: StateFlow<SheetUiState> = _uiState.asStateFlow()

    private var undoStack = listOf<Workbook>()
    private var redoStack = listOf<Workbook>()
    internal fun setWorkbookForTesting(workbook: Workbook) {
        _uiState.update {
            it.copy(
                workbook = workbook,
                activeSheet = workbook.activeSheet.coerceIn(0, (workbook.sheets.size - 1).coerceAtLeast(0)),
            )
        }
    }


    fun currentSheet(): Sheet? {
        val state = _uiState.value
        return state.workbook?.sheets?.getOrNull(state.activeSheet)
    }

    fun namesForSheet(): List<NamedRange> {
        val state = _uiState.value
        return state.workbook?.namedRanges?.filter { it.appliesTo(state.activeSheet) }.orEmpty()
    }

    fun selectedRange(): CellRange {
        val state = _uiState.value
        return CellRange.of(
            CellRangeCorner(state.selection.row, state.selection.column),
            CellRangeCorner(
                state.selectionEnd?.row ?: state.selection.row,
                state.selectionEnd?.column ?: state.selection.column,
            ),
        )
    }

    fun rawValueAt(pos: CellPos): String =
        currentSheet()?.cells?.firstOrNull { it.row == pos.row && it.column == pos.column }?.value.orEmpty()

    fun formatAt(pos: CellPos): CellFormat =
        currentSheet()?.cells?.firstOrNull { it.row == pos.row && it.column == pos.column }?.format
            ?: CellFormat()

    fun load(file: RecentFile, draftStore: DraftStore, context: Context) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val draft = draftStore.load(file.uri)
                    if (isCsv(file.displayName)) {
                        val text = context.contentResolver.openInputStream(Uri.parse(file.uri))
                            ?.bufferedReader()?.use { it.readText() }
                            ?: error("the file could not be opened")
                        val fromFile = Workbook(
                            listOf(parseCsv(text, file.displayName.substringBeforeLast('.'))),
                        )
                        if (draft != null) {
                            val recovered = XlsxReader().read(ByteArrayInputStream(draft))
                            Loaded(recovered, true, emptyList(), null)
                        } else {
                            Loaded(fromFile, false, emptyList(), null)
                        }
                    } else {
                        val stream = context.contentResolver.openInputStream(Uri.parse(file.uri))
                            ?: error("the file could not be opened")
                        val bytes = stream.use { it.readBytes() }
                        val dropped = ContentAudit.xlsx(bytes)
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

            result.fold(
                onSuccess = { loaded ->
                    val initialSheet = loaded.workbook.activeSheet.coerceIn(
                        0,
                        (loaded.workbook.sheets.size - 1).coerceAtLeast(0),
                    )
                    _uiState.update {
                        it.copy(
                            workbook = loaded.workbook,
                            activeSheet = initialSheet,
                            draftRecovered = loaded.recovered,
                            droppedFeatures = loaded.dropped,
                            sourcePackage = loaded.source,
                            loadError = null,
                            dirty = loaded.recovered,
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(loadError = error.message ?: error.javaClass.simpleName)
                    }
                },
            )
        }
    }

    fun select(pos: CellPos) {
        val sheet = currentSheet() ?: return
        val clamped = anchorAt(sheet, clampToSheet(sheet, pos))
        if (_uiState.value.editingCell && clamped != _uiState.value.selection) {
            commitEdit()
        }
        _uiState.update {
            it.copy(
                selection = clamped,
                selectionEnd = null,
                editText = rawValueAt(clamped),
            )
        }
    }

    fun extendSelectionTo(pos: CellPos) {
        val sheet = currentSheet() ?: return
        if (_uiState.value.editingCell) commitEdit()
        val clamped = clampToSheet(sheet, pos)
        _uiState.update { it.copy(selectionEnd = clamped) }
    }

    fun extendSelection(rowDelta: Int, columnDelta: Int) {
        val sheet = currentSheet() ?: return
        if (_uiState.value.editingCell) commitEdit()
        val state = _uiState.value
        val from = state.selectionEnd ?: state.selection
        val target = clampToSheet(sheet, CellPos(from.row + rowDelta, from.column + columnDelta))
        _uiState.update { it.copy(selectionEnd = target) }
    }

    fun startTyping(character: Char) {
        _uiState.update {
            it.copy(
                selectionEnd = null,
                editingCell = true,
                editText = character.toString(),
            )
        }
    }

    fun updateEditText(text: String) {
        _uiState.update { it.copy(editText = text) }
    }

    fun startEditing() {
        val text = rawValueAt(_uiState.value.selection)
        _uiState.update {
            it.copy(editingCell = true, editText = text)
        }
    }

    fun cancelEdit() {
        val currentText = rawValueAt(_uiState.value.selection)
        _uiState.update {
            it.copy(editingCell = false, editText = currentText)
        }
    }

    fun commitEdit() {
        val text = _uiState.value.editText
        _uiState.update { it.copy(editingCell = false) }
        val pos = _uiState.value.selection
        if (text != rawValueAt(pos)) {
            writeCell(pos, normaliseInput(text, formatAt(pos)))
        }
    }

    fun writeCell(pos: CellPos, raw: String, format: CellFormat? = null) {
        mutate { target ->
            val existing = target.cells.firstOrNull { it.row == pos.row && it.column == pos.column }
            val effectiveFormat = format ?: existing?.format ?: CellFormat()
            val cells = target.cells
                .filterNot { it.row == pos.row && it.column == pos.column }
                .toMutableList()
            val nextSheet = target.copy(
                rowCount = maxOf(target.rowCount, pos.row + 1),
                columnCount = maxOf(target.columnCount, pos.column + 1),
            )
            if (raw.isNotEmpty()) {
                val cached = if (raw.startsWith("=")) {
                    evaluateFormula(nextSheet, raw, namesForSheet()).value
                } else {
                    null
                }
                cells += Cell(pos.row, pos.column, raw, effectiveFormat, cached)
            } else if (effectiveFormat != CellFormat()) {
                cells += Cell(pos.row, pos.column, "", effectiveFormat, null)
            }
            nextSheet.copy(cells = cells.sortedWith(compareBy({ it.row }, { it.column })))
        }
    }

    fun normaliseInput(text: String, format: CellFormat): String {
        if (text.isEmpty() || text.startsWith("=")) return text
        if (text.toDoubleOrNull() != null) return text
        if (!isDateFormat(format.numberFormat)) return text
        val serial = parseDateInput(text) ?: return text
        return generalNumber(serial)
    }

    fun mutate(transform: (Sheet) -> Sheet) {
        val current = _uiState.value.workbook ?: return
        val currentSheetValue = current.sheets.getOrNull(_uiState.value.activeSheet) ?: return
        val updated = transform(currentSheetValue)
        if (updated == currentSheetValue) return

        undoStack = (undoStack + current).takeLast(SHEET_UNDO_LIMIT)
        redoStack = emptyList()

        val sheets = current.sheets.toMutableList()
        sheets[_uiState.value.activeSheet] = updated
        val next = current.copy(sheets = sheets)

        _uiState.update {
            it.copy(
                workbook = next,
                dirty = true,
                writeVersion = it.writeVersion + 1,
                canUndo = true,
                canRedo = false,
            )
        }
    }

    fun applyFormat(transform: (CellFormat) -> CellFormat) {
        if (_uiState.value.editingCell) commitEdit()
        val range = selectedRange()
        val anchor = _uiState.value.selection
        mutate { sheet ->
            val byPosition = sheet.cells.associateBy { it.row to it.column }.toMutableMap()
            for (row in range.top..range.bottom) {
                for (column in range.left..range.right) {
                    val existing = byPosition[row to column]
                    val format = transform(existing?.format ?: CellFormat())
                    val value = existing?.value.orEmpty()
                    if (value.isNotEmpty() || format != CellFormat()) {
                        byPosition[row to column] = Cell(row, column, value, format, existing?.cached)
                    } else {
                        byPosition.remove(row to column)
                    }
                }
            }
            sheet.copy(cells = byPosition.values.toList())
        }
        val current = rawValueAt(anchor)
        _uiState.update { it.copy(editText = current) }
    }
    fun applyBorders(kind: BorderKind) {
        if (_uiState.value.editingCell) commitEdit()
        val range = selectedRange()
        val anchor = _uiState.value.selection
        mutate { sheet -> sheet.withBorders(range, kind) }
        val current = rawValueAt(anchor)
        _uiState.update { it.copy(editText = current) }
    }

    fun setNote(note: String) {
        if (_uiState.value.editingCell) commitEdit()
        val position = _uiState.value.selection
        mutate { sheet ->
            val existing = sheet.cells.firstOrNull { it.row == position.row && it.column == position.column }
            val cells = sheet.cells.filterNot { it.row == position.row && it.column == position.column }.toMutableList()
            if (note.isNotEmpty() || existing?.value?.isNotEmpty() == true || existing?.format != CellFormat()) {
                cells += (existing ?: Cell(position.row, position.column)).copy(note = note)
            }
            sheet.copy(cells = cells)
        }
    }

    fun clearSelectedCell() {
        if (_uiState.value.editingCell) commitEdit()
        val range = selectedRange()
        val target = currentSheet() ?: return
        val hasContent = target.cells.any {
            range.contains(it.row, it.column) &&
                (it.value.isNotEmpty() || it.format != CellFormat())
        }
        if (!hasContent) return
        mutate { sheet ->
            val remaining = sheet.cells.mapNotNull { cell ->
                if (!range.contains(cell.row, cell.column)) cell
                else when {
                    cell.format != CellFormat() -> cell.copy(value = "", cached = null)
                    else -> null
                }
            }
            sheet.copy(cells = remaining)
        }
        _uiState.update { it.copy(editText = "") }
    }

    fun mergeSelection() {
        if (_uiState.value.editingCell) commitEdit()
        val range = selectedRange()
        if (range.rowSpan <= 1 && range.columnSpan <= 1) return
        val target = currentSheet() ?: return
        if (target.mergedRanges.any { it.intersects(range) }) return
        val anchor = CellPos(range.top, range.left)
        mutate { sheet ->
            val cells = sheet.cells.mapNotNull { cell ->
                if (!range.contains(cell.row, cell.column)) cell
                else if (cell.row == anchor.row && cell.column == anchor.column) cell
                else null
            }
            sheet.copy(
                cells = cells,
                mergedRanges = sheet.mergedRanges + range,
            )
        }
        select(anchor)
    }

    fun unmergeSelection() {
        if (_uiState.value.editingCell) commitEdit()
        val range = selectedRange()
        val target = currentSheet() ?: return
        if (!target.mergedRanges.any { it.intersects(range) }) return
        mutate { it.withMergesClearedIn(range) }
        select(_uiState.value.selection)
    }

    fun toggleFrozenRows() {
        if (_uiState.value.editingCell) commitEdit()
        val current = currentSheet() ?: return
        mutate {
            it.copy(frozenRows = if (current.frozenRows > 0) 0 else _uiState.value.selection.row + 1)
        }
    }

    fun runStructureAction(action: StructureAction) {
        when (action) {
            StructureAction.INSERT_ROWS_ABOVE -> insertSelected(SheetAxis.ROWS, before = true)
            StructureAction.INSERT_ROWS_BELOW -> insertSelected(SheetAxis.ROWS, before = false)
            StructureAction.INSERT_COLUMNS_LEFT -> insertSelected(SheetAxis.COLUMNS, before = true)
            StructureAction.INSERT_COLUMNS_RIGHT -> insertSelected(SheetAxis.COLUMNS, before = false)
            StructureAction.DELETE_ROWS -> deleteSelected(SheetAxis.ROWS)
            StructureAction.DELETE_COLUMNS -> deleteSelected(SheetAxis.COLUMNS)
        }
    }

    private fun insertSelected(axis: SheetAxis, before: Boolean) {
        if (_uiState.value.editingCell) commitEdit()
        val sheet = currentSheet() ?: return
        val range = selectedRange()
        val at = if (before) axis.firstOf(range) else axis.lastOf(range) + 1
        val count = axis.spanOf(range)
        mutate { axis.insertInto(it, at, count) }
        currentSheet()?.let { updated ->
            val pos = if (before) {
                when (axis) {
                    SheetAxis.ROWS -> CellPos(at + count, range.left)
                    SheetAxis.COLUMNS -> CellPos(range.top, at + count)
                }
            } else {
                CellPos(range.top, range.left)
            }
            settleAfterStructure(updated, pos)
        }
    }

    private fun deleteSelected(axis: SheetAxis) {
        if (_uiState.value.editingCell) commitEdit()
        val sheet = currentSheet() ?: return
        val range = selectedRange()
        val count = axis.spanOf(range)
        if (count >= axis.sizeOf(sheet)) return
        mutate { axis.deleteFrom(it, range, count) }
        currentSheet()?.let { updated ->
            val pos = CellPos(
                range.top.coerceAtMost(updated.rowCount - 1),
                range.left.coerceAtMost(updated.columnCount - 1),
            )
            settleAfterStructure(updated, pos)
        }
    }

    private fun settleAfterStructure(sheet: Sheet, pos: CellPos) {
        val clamped = anchorAt(sheet, clampToSheet(sheet, pos))
        _uiState.update {
            it.copy(
                selection = clamped,
                selectionEnd = null,
                editText = rawValueAt(clamped),
            )
        }
    }

    fun undo() {
        if (_uiState.value.editingCell) commitEdit()
        val previous = undoStack.lastOrNull() ?: return
        val current = _uiState.value.workbook ?: return
        undoStack = undoStack.dropLast(1)
        redoStack = (redoStack + current).takeLast(SHEET_UNDO_LIMIT)
        val active = previous.activeSheet.coerceIn(0, (previous.sheets.size - 1).coerceAtLeast(0))
        val restored = previous.sheets.getOrNull(active)
        _uiState.update {
            it.copy(
                workbook = previous,
                activeSheet = active,
                selection = if (restored != null) anchorAt(restored, clampToSheet(restored, it.selection)) else it.selection,
                selectionEnd = null,
                dirty = true,
                writeVersion = it.writeVersion + 1,
                canUndo = undoStack.isNotEmpty(),
                canRedo = true,
            )
        }
    }

    fun redo() {
        if (_uiState.value.editingCell) commitEdit()
        val next = redoStack.lastOrNull() ?: return
        val current = _uiState.value.workbook ?: return
        redoStack = redoStack.dropLast(1)
        undoStack = (undoStack + current).takeLast(SHEET_UNDO_LIMIT)
        val active = next.activeSheet.coerceIn(0, (next.sheets.size - 1).coerceAtLeast(0))
        val restored = next.sheets.getOrNull(active)
        _uiState.update {
            it.copy(
                workbook = next,
                activeSheet = active,
                selection = if (restored != null) anchorAt(restored, clampToSheet(restored, it.selection)) else it.selection,
                selectionEnd = null,
                dirty = true,
                writeVersion = it.writeVersion + 1,
                canUndo = true,
                canRedo = redoStack.isNotEmpty(),
            )
        }
    }

    fun addSheet() {
        if (_uiState.value.editingCell) commitEdit()
        val current = _uiState.value.workbook ?: return
        undoStack = (undoStack + current).takeLast(SHEET_UNDO_LIMIT)
        redoStack = emptyList()
        val name = "Sheet ${current.sheets.size + 1}"
        val sheets = current.sheets + Sheet(name = name, cells = emptyList())
        _uiState.update {
            it.copy(
                workbook = current.copy(sheets = sheets),
                activeSheet = sheets.lastIndex,
                selection = CellPos(0, 0),
                selectionEnd = null,
                editText = "",
                dirty = true,
                writeVersion = it.writeVersion + 1,
                canUndo = true,
            )
        }
    }

    fun renameSheetAt(index: Int, newName: String) {
        if (_uiState.value.editingCell) commitEdit()
        val current = _uiState.value.workbook ?: return
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return
        val sheets = current.sheets.toMutableList()
        if (index !in sheets.indices) return
        sheets[index] = sheets[index].copy(name = trimmed)
        undoStack = (undoStack + current).takeLast(SHEET_UNDO_LIMIT)
        redoStack = emptyList()
        _uiState.update {
            it.copy(
                workbook = current.copy(sheets = sheets),
                dirty = true,
                writeVersion = it.writeVersion + 1,
                canUndo = true,
            )
        }
    }

    fun duplicateSheet(index: Int) {
        if (_uiState.value.editingCell) commitEdit()
        val current = _uiState.value.workbook ?: return
        val sourceSheet = current.sheets.getOrNull(index) ?: return
        undoStack = (undoStack + current).takeLast(SHEET_UNDO_LIMIT)
        redoStack = emptyList()
        val copyName = "${sourceSheet.name} (Copy)"
        val duplicated = sourceSheet.copy(name = copyName)
        val sheets = current.sheets.toMutableList()
        val newIndex = index + 1
        sheets.add(newIndex, duplicated)
        _uiState.update {
            it.copy(
                workbook = current.copy(sheets = sheets),
                activeSheet = newIndex,
                selection = CellPos(0, 0),
                selectionEnd = null,
                dirty = true,
                writeVersion = it.writeVersion + 1,
                canUndo = true,
            )
        }
    }

    fun deleteSheet(index: Int) {
        if (_uiState.value.editingCell) commitEdit()
        val current = _uiState.value.workbook ?: return
        if (current.sheets.size <= 1) return
        undoStack = (undoStack + current).takeLast(SHEET_UNDO_LIMIT)
        redoStack = emptyList()
        val sheets = current.sheets.toMutableList()
        if (index !in sheets.indices) return
        sheets.removeAt(index)
        val nextActive = _uiState.value.activeSheet.coerceIn(0, sheets.lastIndex)
        _uiState.update {
            it.copy(
                workbook = current.copy(sheets = sheets),
                activeSheet = nextActive,
                selection = CellPos(0, 0),
                selectionEnd = null,
                dirty = true,
                writeVersion = it.writeVersion + 1,
                canUndo = true,
            )
        }
    }

    fun switchSheet(index: Int) {
        if (_uiState.value.editingCell) commitEdit()
        _uiState.update {
            it.copy(
                activeSheet = index,
                selection = CellPos(0, 0),
                selectionEnd = null,
                editText = "",
            )
        }
    }

    fun acceptLoss() {
        _uiState.update { it.copy(lossAccepted = true) }
    }

    fun workbookBytes(current: Workbook, displayName: String): ByteArray {
        val state = _uiState.value
        if (isCsv(displayName)) {
            return writeCsv(current.sheets[state.activeSheet]).encodeToByteArray()
        }
        val target = current.copy(activeSheet = state.activeSheet)
        val source = state.sourcePackage
        return if (source != null) XlsxEditor(source).save(target) else XlsxWriter().write(target)
    }

    fun draftBytes(current: Workbook): ByteArray =
        XlsxWriter().write(current.copy(activeSheet = _uiState.value.activeSheet))

    fun save(
        context: Context,
        file: RecentFile,
        draftStore: DraftStore,
        onSaved: () -> Unit,
        onError: (String) -> Unit,
    ) {
        val current = _uiState.value.workbook ?: return
        val writingVersion = _uiState.value.writeVersion
        _uiState.update { it.copy(saving = true) }

        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = workbookBytes(current, file.displayName)
                    val uri = Uri.parse(file.uri)
                    val isCsv = isCsv(file.displayName)
                    val written = if (isCsv) {
                        SafeWrite.writePlain(context, uri, bytes)
                    } else {
                        SafeWrite.write(context, uri, bytes, PackageKind.SPREADSHEET)
                    }
                    if (written <= 0) error("save refused: verification failed")
                }
            }

            _uiState.update { it.copy(saving = false) }
            result.fold(
                onSuccess = {
                    if (writingVersion == _uiState.value.writeVersion) {
                        draftStore.clear(file.uri)
                        _uiState.update { it.copy(dirty = false) }
                        onSaved()
                    } else {
                        onSaved()
                    }
                },
                onFailure = { error ->
                    onError(error.message ?: error.javaClass.simpleName)
                },
            )
        }
    }

    private data class Loaded(
        val workbook: Workbook,
        val recovered: Boolean,
        val dropped: List<PreservedFeature>,
        val source: XlsxSource?,
    )
}

internal fun anchorAt(sheet: Sheet, pos: CellPos): CellPos {
    val merge = sheet.mergedRanges.firstOrNull { it.contains(pos.row, pos.column) } ?: return pos
    return CellPos(merge.top, merge.left)
}

internal fun clampToSheet(sheet: Sheet, pos: CellPos): CellPos = CellPos(
    row = pos.row.coerceIn(0, (sheet.rowCount - 1).coerceAtLeast(0)),
    column = pos.column.coerceIn(0, (sheet.columnCount - 1).coerceAtLeast(0)),
)

