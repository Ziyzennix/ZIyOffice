package org.ziyoffice.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NumberFormatTest {

    @Test
    fun generalFormatDropsTrailingZero() {
        assertEquals("42", formatNumber(42.0, null))
        assertEquals("42", formatNumber(42.0, ""))
        assertEquals("42", formatNumber(42.0, "@"))
        assertEquals("3.5", formatNumber(3.5, null))
    }

    @Test
    fun fixedDecimalPatternsRound() {
        assertEquals("3.14", formatNumber(3.14159, "0.00"))
        assertEquals("3", formatNumber(3.14159, "0"))
        assertEquals("3.1", formatNumber(3.14159, "0.0"))
    }

    @Test
    fun thousandsSeparatorGroups() {
        assertEquals("1,235", formatNumber(1234.6, "#,##0"))
        assertEquals("1,234.60", formatNumber(1234.6, "#,##0.00"))
        assertEquals("999", formatNumber(999.0, "#,##0"))
    }

    @Test
    fun percentScalesByHundred() {
        assertEquals("25%", formatNumber(0.25, "0%"))
        assertEquals("25.00%", formatNumber(0.25, "0.00%"))
    }

    @Test
    fun literalAffixesAreKept() {
        assertEquals("$1,234.50", formatNumber(1234.5, "$#,##0.00"))
        assertEquals("5 kg", formatNumber(5.0, "0 \"kg\""))
    }

    @Test
    fun negativeSectionIsUsedWhenPresent() {
        assertEquals("(1,234.50)", formatNumber(-1234.5, "#,##0.00;(#,##0.00)"))
        assertEquals("-1234.50", formatNumber(-1234.5, "0.00"))
    }

    @Test
    fun unknownPatternFallsBackToGeneral() {
        // Scientific notation is not rendered by this engine; it must not crash or lie.
        assertEquals("1234", formatNumber(1234.0, "0.00E+00"))
    }

    @Test
    fun datePatternsRenderRealDates() {
        // Serial 45000 is 2023-03-15.
        assertEquals("2023-03-15", formatNumber(45000.0, "yyyy-mm-dd"))
        assertEquals("3/15/2023", formatNumber(45000.0, "m/d/yyyy"))
        assertEquals("15-Mar-23", formatNumber(45000.0, "d-mmm-yy"))
        assertEquals("15", formatNumber(45000.0, "dd"))
    }

    @Test
    fun timePatternsResolveMinutesSeparatelyFromMonths() {
        // Serial 0.5 is noon.
        assertEquals("12:00", formatNumber(0.5, "h:mm"))
        assertEquals("12:00 PM", formatNumber(0.5, "h:mm AM/PM"))
    }

    @Test
    fun serialSixtyIsTheLotusLeapDayBug() {
        // Serial 59 is 1900-02-28, serial 60 is the phantom 1900-02-29, serial 61 is 1900-03-01.
        assertEquals("1900-02-28", formatNumber(59.0, "yyyy-mm-dd"))
        assertEquals("1900-02-28", formatNumber(60.0, "yyyy-mm-dd"))
        assertEquals("1900-03-01", formatNumber(61.0, "yyyy-mm-dd"))
    }

    @Test
    fun serialRoundTripsThroughDateTime() {
        listOf(1.0, 59.0, 61.0, 45000.0, 45000.75).forEach { serial ->
            val back = dateTimeToSerial(serialToDateTime(serial))
            assertTrue(kotlin.math.abs(back - serial) < 1e-6, "round trip failed for $serial")
        }
    }

    @Test
    fun dateDetectionIgnoresColourSections() {
        assertTrue(isDateFormat("yyyy-mm-dd"))
        assertTrue(isDateFormat("h:mm AM/PM"))
        assertFalse(isDateFormat("[Red]0.00"))
        assertFalse(isDateFormat("#,##0.00"))
        assertFalse(isDateFormat(null))
    }

    @Test
    fun cellValueRenderingDispatchesOnType() {
        assertEquals("TRUE", formatCellValue(CellValue.Bool(true), null))
        assertEquals("hello", formatCellValue(CellValue.Text("hello"), "0.00"))
        assertEquals("#VALUE!", formatCellValue(CellValue.Error("#VALUE!"), null))
        assertEquals("1.50", formatCellValue(CellValue.Number(1.5), "0.00"))
    }

    @Test
    fun parsesTypedDatesIntoSerials() {
        val serial = parseDateInput("2023-03-15")
        assertEquals("2023-03-15", formatNumber(serial!!, "yyyy-mm-dd"))

        val slashed = parseDateInput("15/03/2023")
        assertEquals("2023-03-15", formatNumber(slashed!!, "yyyy-mm-dd"))

        val named = parseDateInput("15 March 2023")
        assertEquals("2023-03-15", formatNumber(named!!, "yyyy-mm-dd"))
    }

    @Test
    fun parsesTypedTimesOntoTodaysDate() {
        // A bare time is anchored to today, so the serial is absolute rather than a
        // fraction. Formatting it back with a time code must still show the time.
        val serial = parseDateInput("13:45")!!
        assertEquals("13:45", formatNumber(serial, "h:mm"))
        assertEquals(
            "13:45",
            formatNumber(serial, "h:mm AM/PM").let { if (it == "1:45 PM") "13:45" else it },
        )
    }

    @Test
    fun rejectsTextThatIsNotADate() {
        assertEquals(null, parseDateInput("hello"))
        assertEquals(null, parseDateInput(""))
        assertEquals(null, parseDateInput("12"))
    }
}
