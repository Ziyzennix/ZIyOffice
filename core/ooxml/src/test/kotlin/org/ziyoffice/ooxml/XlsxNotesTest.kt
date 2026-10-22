package org.ziyoffice.ooxml

import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.ziyoffice.model.Cell
import org.ziyoffice.model.Sheet
import org.ziyoffice.model.Workbook

/**
 * Notes pinned to cells, read from a file and written back into it.
 */
class XlsxNotesTest {

    private fun workbookWithNotes(): Workbook = Workbook(
        sheets = listOf(
            Sheet(
                name = "Sheet1",
                cells = listOf(
                    Cell(row = 0, column = 0, value = "a", note = "the first note"),
                    Cell(row = 2, column = 1, value = "b"),
                    Cell(row = 4, column = 3, value = "c", note = "another note"),
                ),
                rowCount = 10,
                columnCount = 10,
            ),
        ),
    )

    private fun partOf(bytes: ByteArray, name: String): String? {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name == name) return zip.readBytes().decodeToString()
            }
        }
        return null
    }

    @Test
    fun aNoteSurvivesASave() {
        val original = XlsxWriter().write(workbookWithNotes())
        val source = XlsxSource.capture(original)
        val sheet = source.workbook.sheets.first()
        val notes = sheet.cells.mapNotNull { cell ->
            if (cell.note.isEmpty()) null else (cell.row to cell.column) to cell.note
        }.toMap()
        assertEquals(2, notes.size)
        assertEquals("the first note", notes[0 to 0])
        assertEquals("another note", notes[4 to 3])
    }

    @Test
    fun aCellWithoutANoteHasNone() {
        val original = XlsxWriter().write(workbookWithNotes())
        val source = XlsxSource.capture(original)
        val sheet = source.workbook.sheets.first()
        assertEquals("", sheet.cells.first { it.row == 2 && it.column == 1 }.note)
    }

    @Test
    fun theNotesLiveInAPartOfTheirOwn() {
        val original = XlsxWriter().write(workbookWithNotes())
        val comments = assertNotNull(partOf(original, "xl/comments1.xml"))
        assertTrue(comments.contains("the first note"), "the note should be in the part")
        assertTrue(comments.contains("ref=\"A1\""), "the note should name its cell")
        assertTrue(comments.contains("ref=\"D5\""), "the note should name its cell")
    }

    @Test
    fun theDrawingSaysWhereTheNotesSit() {
        val original = XlsxWriter().write(workbookWithNotes())
        val drawing = assertNotNull(partOf(original, "xl/drawings/vmlDrawing1.vml"))
        assertTrue(drawing.contains("ObjectType=\"Note\""), "the drawing should hold the notes")
        assertTrue(drawing.contains("<x:Row>0</x:Row>"), "the drawing should name the row")
        assertTrue(drawing.contains("<x:Column>0</x:Column>"), "the drawing should name the column")
    }

    @Test
    fun theSheetPointsAtTheDrawing() {
        val original = XlsxWriter().write(workbookWithNotes())
        val sheet = assertNotNull(partOf(original, "xl/worksheets/sheet1.xml"))
        assertTrue(sheet.contains("<legacyDrawing"), "the sheet should point at the drawing")
    }

    @Test
    fun aWorkbookWithoutNotesHasNoNoteParts() {
        val original = XlsxWriter().write(
            Workbook(sheets = listOf(Sheet(name = "Sheet1", cells = listOf(Cell(0, 0, "a"))))),
        )
        assertEquals(null, partOf(original, "xl/comments1.xml"))
    }

    @Test
    fun aNoteKeepsItsText() {
        val original = XlsxWriter().write(
            Workbook(
                sheets = listOf(
                    Sheet(
                        name = "Sheet1",
                        cells = listOf(Cell(0, 0, "a", note = "first <b>line</b> & second")),
                    ),
                ),
            ),
        )
        val source = XlsxSource.capture(original)
        assertEquals(
            "first <b>line</b> & second",
            source.workbook.sheets.first().cells.first().note,
        )
    }

    @Test
    fun aNoteAddedToAFileIsWrittenIntoIt() {
        val original = XlsxWriter().write(
            Workbook(sheets = listOf(Sheet(name = "Sheet1", cells = listOf(Cell(0, 0, "a"))))),
        )
        val source = XlsxSource.capture(original)
        val withNote = source.workbook.copy(
            sheets = listOf(
                source.workbook.sheets.first().copy(
                    cells = listOf(Cell(0, 0, "a", note = "a new note")),
                ),
            ),
        )
        val edited = XlsxEditor(source).save(withNote)
        val read = XlsxSource.capture(edited)
        assertEquals("a new note", read.workbook.sheets.first().cells.first().note)
    }

    @Test
    fun aFileWhoseNotesWereNotChangedKeepsItsNoteParts() {
        val original = XlsxWriter().write(workbookWithNotes())
        val source = XlsxSource.capture(original)
        val edited = XlsxEditor(source).save(source.workbook)
        val comments = assertNotNull(partOf(edited, "xl/comments1.xml"))
        assertTrue(comments.contains("the first note"), "the note should still be there")
    }
}
