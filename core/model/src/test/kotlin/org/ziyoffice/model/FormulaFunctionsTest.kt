package org.ziyoffice.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FormulaFunctionsTest {

    private fun sheet(): Sheet = Sheet(
        name = "Sheet1",
        cells = listOf(
            Cell(row = 0, column = 0, value = "Paper"),
            Cell(row = 0, column = 1, value = "10"),
            Cell(row = 0, column = 2, value = "20"),
            Cell(row = 1, column = 0, value = "Ink"),
            Cell(row = 1, column = 1, value = "5"),
            Cell(row = 1, column = 2, value = "7"),
            Cell(row = 2, column = 0, value = "Paper"),
            Cell(row = 2, column = 1, value = "3"),
            Cell(row = 2, column = 2, value = "4"),
            Cell(row = 3, column = 0, value = "Service"),
            Cell(row = 3, column = 1, value = "8"),
            Cell(row = 3, column = 2, value = "2"),
        ),
        rowCount = 20,
        columnCount = 10,
    )

    private fun value(formula: String): CellValue =
        evaluateFormula(sheet(), formula).value

    private fun number(formula: String): Double =
        (value(formula) as CellValue.Number).number

    private fun text(formula: String): String =
        (value(formula) as CellValue.Text).text

    @Test
    fun mathsFunctions() {
        assertEquals(3.0, number("=ABS(-3)"))
        assertEquals(2.0, number("=INT(2.9)"))
        assertEquals(4.0, number("=SQRT(16)"))
        assertEquals(CellValue.Error("#NUM!"), value("=SQRT(-1)"))
        assertEquals(3.14, number("=ROUND(3.14159,2)"), )
        assertEquals(-3.0, number("=ROUND(-2.5)"))
        assertEquals(3.2, number("=ROUNDUP(3.11,1)"))
        assertEquals(3.1, number("=ROUNDDOWN(3.19,1)"))
        assertEquals(1.0, number("=MOD(-3,2)"))
        assertEquals(-1.0, number("=SIGN(-7)"))
        assertEquals(3.0, number("=CEILING(2.1,1)"))
        assertEquals(2.0, number("=FLOOR(2.9,1)"))
        assertEquals(8.0, number("=POWER(2,3)"))
        assertEquals(263.0, number("=SUMPRODUCT(B1:B4,C1:C4)"))
    }

    @Test
    fun textFunctions() {
        assertEquals(5.0, number("=LEN(\"Hello\")"))
        assertEquals("He", text("=LEFT(\"Hello\",2)"))
        assertEquals("lo", text("=RIGHT(\"Hello\",2)"))
        assertEquals("ell", text("=MID(\"Hello\",2,3)"))
        assertEquals("a b", text("=TRIM(\"  a   b  \")"))
        assertEquals("AB", text("=UPPER(\"ab\")"))
        assertEquals("ab", text("=LOWER(\"AB\")"))
        assertEquals("Hello World", text("=PROPER(\"hello world\")"))
        assertEquals("abc", text("=CONCATENATE(\"a\",\"b\",\"c\")"))
        assertEquals("ababab", text("=REPT(\"ab\",3)"))
        assertEquals("a+b+c", text("=SUBSTITUTE(\"a-b-c\",\"-\",\"+\")"))
        assertEquals("a-b+c", text("=SUBSTITUTE(\"a-b-c\",\"-\",\"+\",2)"))
        assertEquals("aXef", text("=REPLACE(\"abcdef\",2,3,\"X\")"))
        assertEquals(2.0, number("=FIND(\"b\",\"abc\")"))
        assertEquals(2.0, number("=SEARCH(\"B\",\"abc\")"))
        assertEquals(CellValue.Error("#VALUE!"), value("=FIND(\"z\",\"abc\")"))
        assertEquals(CellValue.Bool(false), value("=EXACT(\"a\",\"A\")"))
        assertEquals("1234.50", text("=TEXT(1234.5,\"0.00\")"))
        assertEquals(42.0, number("=VALUE(\"42\")"))
        assertEquals(CellValue.Error("#VALUE!"), value("=VALUE(\"abc\")"))
    }

    @Test
    fun logicalFunctions() {
        assertEquals(CellValue.Number(5.0), value("=IFERROR(5,9)"))
        assertEquals(0.0, number("=IFERROR(1/0,0)"))
        assertEquals(CellValue.Bool(true), value("=AND(TRUE,1)"))
        assertEquals(CellValue.Bool(false), value("=AND(TRUE,FALSE)"))
        assertEquals(CellValue.Bool(true), value("=OR(0,1)"))
        assertEquals(CellValue.Bool(true), value("=NOT(0)"))
        assertEquals(CellValue.Error("#N/A"), value("=NA()"))
    }

    @Test
    fun conditionalCountingFunctions() {
        assertEquals(3.0, number("=COUNTIF(B1:B4,\">4\")"))
        assertEquals(2.0, number("=COUNTIF(A1:A4,\"Paper\")"))
        assertEquals(13.0, number("=SUMIF(A1:A4,\"Paper\",B1:B4)"))
        assertEquals(23.0, number("=SUMIF(B1:B4,\">4\")"))
        assertEquals(23.0 / 3.0, number("=AVERAGEIF(B1:B4,\">4\")"))
        assertEquals(1.0, number("=COUNTBLANK(A1:A5)"))
        assertEquals(4.0, number("=COUNTA(A1:A5)"))
    }

    @Test
    fun lookupFunctions() {
        assertEquals(7.0, number("=INDEX(B1:C4,2,2)"))
        assertEquals(20.0, number("=INDEX(B1:C1,2)"))
        assertEquals(5.0, number("=INDEX(B1:B4,2)"))
        assertEquals(2.0, number("=MATCH(7,C1:C4,0)"))
        assertEquals(CellValue.Error("#N/A"), value("=MATCH(100,C1:C4,0)"))
        assertEquals("b", text("=CHOOSE(2,\"a\",\"b\")"))
    }

    @Test
    fun statisticsFunctions() {
        assertEquals(2.5, number("=MEDIAN(1,2,3,4)"))
        assertEquals(8.0, number("=LARGE(B1:B4,2)"))
        assertEquals(3.0, number("=SMALL(B1:B4,1)"))
        assertEquals(CellValue.Error("#NUM!"), value("=LARGE(B1:B4,9)"))
    }

    @Test
    fun dateFunctions() {
        assertEquals(2024.0, number("=YEAR(DATE(2024,3,15))"))
        assertEquals(3.0, number("=MONTH(DATE(2024,3,15))"))
        assertEquals(15.0, number("=DAY(DATE(2024,3,15))"))
        assertEquals(12.0, number("=HOUR(0.5)"))
        assertEquals(15.0, number("=MINUTE(0.510416666666667)"))
        assertTrue(number("=TODAY()") > 0.0)
    }

    @Test
    fun informationFunctions() {
        assertEquals(CellValue.Bool(true), value("=ISBLANK(A5)"))
        assertEquals(CellValue.Bool(true), value("=ISNUMBER(B1)"))
        assertEquals(CellValue.Bool(true), value("=ISTEXT(A1)"))
        assertEquals(CellValue.Bool(true), value("=ISERROR(1/0)"))
        assertEquals(CellValue.Bool(false), value("=ISERROR(1)"))
        assertEquals(CellValue.Bool(true), value("=ISLOGICAL(TRUE)"))
    }
}
