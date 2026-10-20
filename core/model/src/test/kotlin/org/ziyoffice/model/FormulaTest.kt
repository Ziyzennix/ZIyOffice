package org.ziyoffice.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FormulaTest {

    private fun sheet(vararg cells: Pair<String, String>): Sheet {
        return Sheet(
            name = "Sheet1",
            cells = cells.map { (ref, value) ->
                val col = columnIndex(ref.takeWhile { it.isLetter() })
                val row = ref.dropWhile { it.isLetter() }.toInt() - 1
                Cell(row = row, column = col, value = value)
            },
            rowCount = 20,
            columnCount = 10,
        )
    }

    private fun value(sheet: Sheet, formula: String): CellValue =
        evaluateFormula(sheet, formula).value

    private fun number(sheet: Sheet, formula: String): Double =
        (value(sheet, formula) as CellValue.Number).number

    @Test
    fun arithmeticRespectsPrecedence() {
        val s = sheet()
        assertEquals(14.0, number(s, "=2+3*4"))
        assertEquals(20.0, number(s, "=(2+3)*4"))
        assertEquals(8.0, number(s, "=2^3"))
        assertEquals(-6.0, number(s, "=-2*3"))
    }

    @Test
    fun readsCellReferencesAndDollarAnchors() {
        val s = sheet("A1" to "10", "B2" to "4")
        assertEquals(14.0, number(s, "=A1+B2"))
        assertEquals(14.0, number(s, "=\$A\$1+\$B2"))
    }

    @Test
    fun sumAcceptsRanges() {
        val s = sheet("A1" to "1", "A2" to "2", "A3" to "3.5", "B1" to "not a number")
        assertEquals(6.5, number(s, "=SUM(A1:A3)"))
        assertEquals(6.5, number(s, "=SUM(A1:A3, B1)"))
        assertEquals(6.5, number(s, "=SUM(A1:A3)"))
    }

    @Test
    fun averageMinMaxCount() {
        val s = sheet("A1" to "2", "A2" to "4", "A3" to "6")
        assertEquals(4.0, number(s, "=AVERAGE(A1:A3)"))
        assertEquals(2.0, number(s, "=MIN(A1:A3)"))
        assertEquals(6.0, number(s, "=MAX(A1:A3)"))
        assertEquals(3.0, number(s, "=COUNT(A1:A3)"))
    }

    @Test
    fun comparisonsProduceBooleans() {
        val s = sheet("A1" to "5")
        assertEquals(CellValue.Bool(true), value(s, "=A1>3"))
        assertEquals(CellValue.Bool(false), value(s, "=A1<3"))
        assertEquals(CellValue.Bool(true), value(s, "=A1=5"))
        assertEquals(CellValue.Bool(true), value(s, "=A1<>3"))
        assertEquals(CellValue.Bool(true), value(s, "=A1>=5"))
    }

    @Test
    fun ifChoosesBranch() {
        val s = sheet("A1" to "7")
        assertEquals(CellValue.Text("big"), value(s, "=IF(A1>5,\"big\",\"small\")"))
        assertEquals(CellValue.Text("small"), value(s, "=IF(A1<5,\"big\",\"small\")"))
    }

    @Test
    fun vlookupFindsExactAndApproximateMatches() {
        val s = sheet(
            "A1" to "1", "B1" to "one",
            "A2" to "2", "B2" to "two",
            "A3" to "3", "B3" to "three",
        )
        assertEquals(CellValue.Text("two"), value(s, "=VLOOKUP(2,A1:B3,2,FALSE)"))
        assertEquals(CellValue.Text("three"), value(s, "=VLOOKUP(3,A1:B3,2,FALSE)"))
        assertEquals(CellValue.Error("#N/A"), value(s, "=VLOOKUP(9,A1:B3,2,FALSE)"))
        assertEquals(CellValue.Text("two"), value(s, "=VLOOKUP(2.5,A1:B3,2,TRUE)"))
        assertEquals(CellValue.Error("#REF!"), value(s, "=VLOOKUP(2,A1:B3,5,FALSE)"))
    }

    @Test
    fun textConcatenationWithAmpersand() {
        val s = sheet("A1" to "Hello", "B1" to "World")
        assertEquals(CellValue.Text("Hello World"), value(s, "=A1&\" \"&B1"))
    }

    @Test
    fun divisionByZeroIsAnError() {
        val s = sheet("A1" to "0")
        assertEquals(CellValue.Error("#DIV/0!"), value(s, "=5/A1"))
    }

    @Test
    fun circularReferencesAreReported() {
        val s = sheet("A1" to "=B1", "B1" to "=A1")
        val result = evaluateFormula(s, "=A1")
        assertTrue(result.errors.isNotEmpty(), "expected a circular-reference error")
    }

    @Test
    fun nestedFormulasEvaluate() {
        val s = sheet("A1" to "2", "A2" to "=A1*3", "A3" to "=A2+1")
        assertEquals(7.0, number(s, "=A3"))
        assertEquals(15.0, number(s, "=A1+A2+A3"))
    }

    @Test
    fun unknownFunctionIsNameError() {
        assertEquals(CellValue.Error("#NAME?"), value(sheet(), "=NOPE(1)"))
    }

    @Test
    fun nonFormulaTextPassesThrough() {
        assertEquals(CellValue.Text("plain"), value(sheet(), "plain"))
    }

    @Test
    fun emptyCellsCountAsZeroInArithmetic() {
        // Excel and LibreOffice treat a blank cell as 0, so =A1+1 is 1 when A1 is blank.
        val s = sheet()
        assertEquals(1.0, number(s, "=A1+1"))
        assertEquals(0.0, number(s, "=A1*2"))
    }

    @Test
    fun nonNumericTextIsStillAValueError() {
        // The empty-cell rule must not turn ordinary text into a number.
        val s = sheet("A1" to "abc")
        assertEquals(CellValue.Error("#VALUE!"), value(s, "=A1+1"))
    }

    @Test
    fun longAcyclicChainReportsDepthInsteadOfCrashing() {
        // Each cell references the next; cycle detection never fires, so only the
        // depth bound keeps this from exhausting the stack.
        val cells = (1..300).map { row -> "A$row" to "=A${row + 1}" }
        val s = sheet(*cells.toTypedArray())
        val result = evaluateFormula(s, "=A1")
        assertTrue(
            result.value is CellValue.Error,
            "expected an error value for a 300-deep chain, got ${result.value}",
        )
    }

    @Test
    fun doubledQuotesInsideStringsAreLiteralQuotes() {
        assertEquals(
            CellValue.Text("he said \"hi\""),
            value(sheet(), "=\"he said \"\"hi\"\"\""),
        )
    }

    @Test
    fun percentIsAValidLiteral() {
        val s = sheet()
        assertEquals(0.5, number(s, "=50%"))
        assertEquals(1.5, number(s, "=50%+1"))
        assertEquals(25.0, number(s, "=100*25%"))
    }

    @Test
    fun scientificNotationIsAValidLiteral() {
        val s = sheet()
        assertEquals(1000.0, number(s, "=1E3"))
        assertEquals(0.015, number(s, "=1.5E-2"))
        assertEquals(20000000000.0, number(s, "=2e+10"))
    }

    @Test
    fun columnELettersAreStillCellReferences() {
        // Scientific notation must not swallow E1/E10 as exponents.
        val s = sheet("E1" to "5", "E2" to "6", "E3" to "7")
        assertEquals(5.0, number(s, "=E1"))
        assertEquals(18.0, number(s, "=SUM(E1:E3)"))
    }
}
