package org.ziyoffice.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class RotateMarksTest {
    private val highlight = PdfMark.Highlight(doc = 0, page = 0, left = 0.1f, top = 0.2f, right = 0.4f, bottom = 0.3f)

    @Test
    fun rotatingNinetyDegreesMovesTheTopLeftCornerToTheTopRight() {
        val rotated = rotateMarks(listOf(highlight), 0, 0, 90).single() as PdfMark.Highlight
        // (0.1, 0.2) -> (0.8, 0.1); (0.4, 0.3) -> (0.7, 0.4) — so the new box is
        // x 0.7..0.8, y 0.1..0.4.
        assertEquals(0.7f, rotated.left, 0.0001f)
        assertEquals(0.1f, rotated.top, 0.0001f)
        assertEquals(0.8f, rotated.right, 0.0001f)
        assertEquals(0.4f, rotated.bottom, 0.0001f)
    }

    @Test
    fun fourNinetyDegreeTurnsRestoreTheOriginalBox() {
        var marks = listOf<PdfMark>(highlight)
        repeat(4) { marks = rotateMarks(marks, 0, 0, 90) }
        val back = marks.single() as PdfMark.Highlight
        assertEquals(highlight.left, back.left, 0.0001f)
        assertEquals(highlight.top, back.top, 0.0001f)
        assertEquals(highlight.right, back.right, 0.0001f)
        assertEquals(highlight.bottom, back.bottom, 0.0001f)
    }

    @Test
    fun marksOnOtherPagesDoNotMove() {
        val other = PdfMark.Ink(doc = 1, page = 3, points = listOf(0.25f to 0.75f))
        val rotated = rotateMarks(listOf(other), 0, 0, 90).single() as PdfMark.Ink
        assertEquals(other.points, rotated.points)
    }

    @Test
    fun notesAndInkRotateTheirPoints() {
        val note = PdfMark.Note(doc = 0, page = 0, x = 0.2f, y = 0.1f, text = "hi")
        val ink = PdfMark.Ink(doc = 0, page = 0, points = listOf(0.2f to 0.1f, 0.5f to 0.5f))
        val rotated = rotateMarks(listOf(note, ink), 0, 0, 90)
        val rn = rotated[0] as PdfMark.Note
        assertEquals(0.9f, rn.x, 0.0001f)
        assertEquals(0.2f, rn.y, 0.0001f)
        val ri = rotated[1] as PdfMark.Ink
        assertEquals(listOf(0.9f to 0.2f, 0.5f to 0.5f), ri.points)
    }

    @Test
    fun zeroDegreesIsANoOp() {
        assertEquals(listOf<PdfMark>(highlight), rotateMarks(listOf(highlight), 0, 0, 0))
    }
}
