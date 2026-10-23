package org.ziyoffice.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.ziyoffice.model.Cell
import org.ziyoffice.model.CellFormat
import org.ziyoffice.model.Sheet
import org.ziyoffice.model.Workbook

class SheetViewModelTest {

    private fun testViewModelWithSheet(): SheetViewModel {
        val vm = SheetViewModel()
        val sheet = Sheet(
            name = "TestSheet",
            cells = listOf(
                Cell(0, 0, "10"),
                Cell(0, 1, "20"),
            ),
        )
        val workbook = Workbook(listOf(sheet))
        vm.setWorkbookForTesting(workbook)
        return vm
    }

    @Test
    fun selectionUpdatesStateAndEditText() {
        val vm = testViewModelWithSheet()
        vm.select(CellPos(0, 0))
        assertEquals(CellPos(0, 0), vm.uiState.value.selection)
        assertEquals("10", vm.uiState.value.editText)

        vm.select(CellPos(0, 1))
        assertEquals(CellPos(0, 1), vm.uiState.value.selection)
        assertEquals("20", vm.uiState.value.editText)
    }

    @Test
    fun writeCellUpdatesSheetAndCalculatesFormula() {
        val vm = testViewModelWithSheet()
        vm.writeCell(CellPos(1, 0), "=A1+B1")
        val cell = vm.currentSheet()?.cellAt(1, 0)
        assertNotNull(cell)
        assertEquals("=A1+B1", cell.value)
    }

    @Test
    fun sheetManagementOperationsWorkCorrectly() {
        val vm = testViewModelWithSheet()
        assertEquals(1, vm.uiState.value.workbook?.sheets?.size)

        vm.addSheet()
        assertEquals(2, vm.uiState.value.workbook?.sheets?.size)
        assertEquals(1, vm.uiState.value.activeSheet)

        vm.renameSheetAt(1, "Renamed")
        assertEquals("Renamed", vm.uiState.value.workbook?.sheets?.get(1)?.name)

        vm.duplicateSheet(1)
        assertEquals(3, vm.uiState.value.workbook?.sheets?.size)
        assertEquals("Renamed (Copy)", vm.uiState.value.workbook?.sheets?.get(2)?.name)

        vm.deleteSheet(2)
        assertEquals(2, vm.uiState.value.workbook?.sheets?.size)
    }

    @Test
    fun undoAndRedoRestoreStates() {
        val vm = testViewModelWithSheet()
        vm.writeCell(CellPos(0, 0), "Changed")
        assertEquals("Changed", vm.currentSheet()?.cellAt(0, 0)?.value)
        assertTrue(vm.uiState.value.canUndo)

        vm.undo()
        assertEquals("10", vm.currentSheet()?.cellAt(0, 0)?.value)
        assertTrue(vm.uiState.value.canRedo)

        vm.redo()
        assertEquals("Changed", vm.currentSheet()?.cellAt(0, 0)?.value)
    }

    private fun Sheet.cellAt(row: Int, column: Int): Cell? =
        cells.firstOrNull { it.row == row && it.column == column }
}
