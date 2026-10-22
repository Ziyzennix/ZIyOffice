package org.ziyoffice.ooxml

import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.ziyoffice.model.CellRange
import org.ziyoffice.model.NamedRange
import org.ziyoffice.model.Sheet
import org.ziyoffice.model.Workbook

/**
 * The names a formula can use, read from a file and written back into it.
 */
class XlsxNamedRangeTest {

    private fun workbookWith(names: List<NamedRange>): Workbook = Workbook(
        sheets = listOf(
            Sheet(name = "Sheet1", cells = emptyList(), rowCount = 10, columnCount = 10),
            Sheet(name = "Sheet2", cells = emptyList(), rowCount = 10, columnCount = 10),
        ),
        namedRanges = names,
    )

    private fun partOf(bytes: ByteArray, name: String): String {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name == name) return zip.readBytes().decodeToString()
            }
        }
        return ""
    }

    @Test
    fun aNameSurvivesASave() {
        val original = XlsxWriter().write(
            workbookWith(listOf(NamedRange("Sales", CellRange(0, 0, 9, 0), sheetIndex = 0))),
        )
        val read = XlsxReader().read(ByteArrayInputStream(original))
        assertEquals(1, read.namedRanges.size)
        assertEquals("Sales", read.namedRanges.first().name)
        assertEquals(CellRange(0, 0, 9, 0), read.namedRanges.first().range)
        assertEquals(0, read.namedRanges.first().sheetIndex)
    }

    @Test
    fun aNameOnTheSecondSheetKnowsWhichSheetItIsOn() {
        val original = XlsxWriter().write(
            workbookWith(listOf(NamedRange("Other", CellRange(0, 0, 0, 0), sheetIndex = 1))),
        )
        val read = XlsxReader().read(ByteArrayInputStream(original))
        assertEquals(1, read.namedRanges.first().sheetIndex)
    }

    @Test
    fun aNameIsWrittenWhereTheFormatSaysItGoes() {
        val original = XlsxWriter().write(
            workbookWith(listOf(NamedRange("Sales", CellRange(0, 0, 9, 0), sheetIndex = 0))),
        )
        val workbook = partOf(original, "xl/workbook.xml")
        assertTrue(workbook.contains("<definedNames>"), "the workbook should carry the names: $workbook")
        assertTrue(workbook.contains("name=\"Sales\""), "the name should be there: $workbook")
        assertTrue(
            workbook.contains("Sheet1!\$A\$1:\$A\$10"),
            "the range should be written out: $workbook",
        )
    }

    @Test
    fun aFileWithoutNamesHasNone() {
        val original = XlsxWriter().write(workbookWith(emptyList()))
        val read = XlsxReader().read(ByteArrayInputStream(original))
        assertTrue(read.namedRanges.isEmpty())
    }

    @Test
    fun aNameIsKeptWhenTheFileIsEdited() {
        val original = XlsxWriter().write(
            workbookWith(listOf(NamedRange("Sales", CellRange(0, 0, 9, 0), sheetIndex = 0))),
        )
        val source = XlsxSource.capture(original)
        val edited = XlsxEditor(source).save(source.workbook)
        val read = XlsxReader().read(ByteArrayInputStream(edited))
        assertEquals(1, read.namedRanges.size)
        assertEquals("Sales", read.namedRanges.first().name)
    }

    @Test
    fun aNameIsAddedToAFileThatHadNone() {
        val original = XlsxWriter().write(workbookWith(emptyList()))
        val source = XlsxSource.capture(original)
        val withName = source.workbook.copy(
            namedRanges = listOf(NamedRange("Fresh", CellRange(0, 0, 4, 0), sheetIndex = 0)),
        )
        val edited = XlsxEditor(source).save(withName)
        val read = XlsxReader().read(ByteArrayInputStream(edited))
        assertEquals(1, read.namedRanges.size)
        assertEquals("Fresh", read.namedRanges.first().name)
    }
}
