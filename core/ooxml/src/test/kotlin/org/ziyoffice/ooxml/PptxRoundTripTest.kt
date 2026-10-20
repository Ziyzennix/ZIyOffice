package org.ziyoffice.ooxml

import java.io.ByteArrayInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.ziyoffice.model.SlideDeck
import org.ziyoffice.model.withLines
import org.ziyoffice.model.withSlideAdded
import org.ziyoffice.model.withSlideMoved
import org.ziyoffice.model.withSlideRemoved

class PptxRoundTripTest {

    private val writer = PptxWriter()
    private val reader = PptxReader()

    private fun read(bytes: ByteArray) = reader.read(ByteArrayInputStream(bytes))

    @Test
    fun slideReaderPreservesParagraphSpacesAndEmptyLines() {
        val parts = zipEntries(writer.create(
            SlideDeck.blank(1).withLines(0, listOf("Title", "  leading and trailing  ", "", "After")),
        ))
        val out = read(zip(parts)).slides.single()

        assertEquals(listOf("Title", "  leading and trailing  ", "", "After"), out.lines)
    }

    @Test
    fun createdDeckIncludesNotesPartsAndReaderReturnsNotes() {
        val deck = SlideDeck.blank(1).copy(
            slides = listOf(org.ziyoffice.model.Slide(1, listOf("Title"), notes = "Presenter cue")),
        )
        val parts = zipEntries(writer.create(deck))
        assertTrue(parts.containsKey("ppt/notesSlides/notesSlide1.xml"))
        assertTrue(parts.containsKey("ppt/slides/_rels/slide1.xml.rels"))
        assertEquals("Presenter cue", read(zip(parts)).slides.single().notes)
    }

    @Test
    fun createdDeckReadsBack() {
        val deck = SlideDeck.blank(2)
        val out = read(writer.create(deck))
        assertEquals(2, out.slides.size)
        assertEquals(listOf(1, 2), out.slides.map { it.number })
        assertEquals(SlideDeck.DEFAULT_WIDTH_EMU, out.widthEmu)
        assertEquals(SlideDeck.DEFAULT_HEIGHT_EMU, out.heightEmu)
    }

    @Test
    fun textSurvivesCreateAndEdit() {
        val deck = SlideDeck.blank(1).withLines(0, listOf("Roadmap", "Ship M4", "Then M5"))
        val created = writer.create(deck)
        assertEquals(
            listOf("Roadmap", "Ship M4", "Then M5"),
            read(created).slides.single().lines,
        )

        // Fewer lines than the package has paragraphs: the tail is kept, not lost.
        val edited = deck.withLines(0, listOf("Roadmap", "Ship it all"))
        val reread = read(writer.edit(created, edited)).slides.single()
        assertEquals(listOf("Roadmap", "Ship it all", "Then M5"), reread.lines)
    }

    @Test
    fun contentTypeRewriteDropsDeletedSlideAndNotesDeclarations() {
        val original = """<Types><Override PartName="/ppt/slides/slide1.xml"/><Override PartName="/ppt/slides/slide2.xml"/><Override PartName="/ppt/notesSlides/notesSlide1.xml"/><Override PartName="/ppt/notesSlides/notesSlide2.xml"/></Types>"""
        val result = writer.rewriteContentTypesForTest(original, listOf("ppt/slides/slide1.xml"))
        assertFalse(result.contains("PartName=\"/ppt/slides/slide2.xml\""))
        assertFalse(result.contains("PartName=\"/ppt/notesSlides/notesSlide2.xml\""))
    }

    @Test
    fun removingSlideDropsItsNotesPartAndContentType() {
        val base = SlideDeck.blank(2).copy(
            slides = listOf(
                org.ziyoffice.model.Slide(1, listOf("Keep"), notes = "Keep note"),
                org.ziyoffice.model.Slide(2, listOf("Drop"), notes = "Drop note"),
            ),
        )
        val output = zipEntries(writer.edit(writer.create(base), base.withSlideRemoved(1)))

        assertFalse(output.containsKey("ppt/slides/slide2.xml"))
        assertFalse(output.containsKey("ppt/notesSlides/notesSlide2.xml"))
        assertFalse(output.getValue("[Content_Types].xml").decodeToString().contains("notesSlide2.xml"))
    }

    @Test
    fun notesStayWithTheirSlideWhenReordered() {
        val base = SlideDeck.blank(2).copy(
            slides = listOf(
                org.ziyoffice.model.Slide(1, listOf("First"), notes = "Note one"),
                org.ziyoffice.model.Slide(2, listOf("Second"), notes = "Note two"),
            ),
        )
        val output = read(writer.edit(writer.create(base), base.withSlideMoved(1, -1)))

        assertEquals(listOf("Note two", "Note one"), output.slides.map { it.notes })
    }

    @Test
    fun editKeepsOtherPartsByteForByte() {
        val deck = SlideDeck.blank(1).withLines(0, listOf("Before"))
        val created = writer.create(deck)
        val edited = writer.edit(created, deck.withLines(0, listOf("After")))

        val before = zipEntries(created)
        val after = zipEntries(edited)
        assertEquals(before.keys, after.keys)
        for (part in listOf("ppt/theme/theme1.xml", "ppt/slideMasters/slideMaster1.xml")) {
            assertTrue(before.getValue(part).contentEquals(after.getValue(part)), part)
        }
    }

    @Test
    fun xmlIsEscaped() {
        val deck = SlideDeck.blank(1).withLines(0, listOf("A & B <tag>"))
        val out = read(writer.create(deck)).slides.single()
        assertEquals(listOf("A & B <tag>"), out.lines)
    }

    @Test
    fun addedSlideAppearsInDeckOrder() {
        val base = SlideDeck.blank(1).withLines(0, listOf("One"))
        val created = writer.create(base)
        val grown = base.withSlideAdded(0).withLines(1, listOf("Two"))
        val out = read(writer.edit(created, grown))

        assertEquals(2, out.slides.size)
        assertEquals(listOf("One", "Two"), out.slides.map { it.title })
    }

    @Test
    fun removedSlideDisappears() {
        val base = SlideDeck.blank(2)
            .withLines(0, listOf("Keep"))
            .withLines(1, listOf("Drop"))
        val created = writer.create(base)
        val out = read(writer.edit(created, base.withSlideRemoved(1)))

        assertEquals(1, out.slides.size)
        assertEquals("Keep", out.slides.single().title)
        assertFalse(zipEntries(writer.edit(created, base.withSlideRemoved(1)))
            .containsKey("ppt/slides/slide2.xml"))
    }

    @Test
    fun reorderedSlidesReadInNewOrder() {
        val base = SlideDeck.blank(2)
            .withLines(0, listOf("First"))
            .withLines(1, listOf("Second"))
        val created = writer.create(base)
        val edited = writer.edit(created, base.withSlideMoved(1, -1))
        val out = read(edited)

        assertEquals(listOf("Second", "First"), out.slides.map { it.title })
    }

    @Test
    fun addingThenReorderingSlidesKeepsTheirContent() {
        val base = SlideDeck.blank(2)
            .withLines(0, listOf("First"))
            .withLines(1, listOf("Second"))
        val created = writer.create(base)
        val changed = base.withSlideAdded(1).withLines(2, listOf("Third"))
            .withSlideMoved(2, -1)

        assertEquals(listOf("First", "Third", "Second"), read(writer.edit(created, changed)).slides.map { it.title })
    }

    @Test
    fun blankDeckStillHasOneSlide() {
        val out = read(writer.create(SlideDeck(emptyList())))
        assertEquals(1, out.slides.size)
    }

    @Test
    fun extraLinesAppendRatherThanDisappear() {
        val base = SlideDeck.blank(1).withLines(0, listOf("Title", "Body"))
        val created = writer.create(base)
        val longer = base.withLines(0, listOf("Title", "Body", "Extra"))
        val out = read(writer.edit(created, longer)).slides.single()

        assertEquals(listOf("Title", "Body", "Extra"), out.lines)
    }

    @Test
    fun editedSpeakerNotesSurviveWrite() {
        val base = SlideDeck.blank(1).copy(slides = listOf(
            org.ziyoffice.model.Slide(1, listOf("Title"), notes = "Updated note"),
        ))
        val created = writer.create(base)
        val notePart = "ppt/notesSlides/notesSlide1.xml"
        val relsPart = "ppt/slides/_rels/slide1.xml.rels"
        val parts = zipEntries(created).toMutableMap()
        parts[notePart] = """
            <p:notes xmlns:p="$PRESENTATIONML_NAMESPACE" xmlns:a="$DRAWINGML_NAMESPACE">
            <p:cSld><p:spTree><p:sp><p:txBody><a:p><a:r><a:t>Old</a:t></a:r></a:p></p:txBody></p:sp></p:spTree></p:cSld>
            </p:notes>
        """.trimIndent().encodeToByteArray()
        parts[relsPart] = """
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
            <Relationship Id="rIdNotes" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/notesSlide" Target="../notesSlides/notesSlide1.xml"/>
            </Relationships>
        """.trimIndent().encodeToByteArray()

        val out = read(writer.edit(zip(parts), base)).slides.single()
        assertEquals("Updated note", out.notes)
    }

    private fun zipEntries(bytes: ByteArray): Map<String, ByteArray> {
        val entries = linkedMapOf<String, ByteArray>()
        java.util.zip.ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries[entry.name] = zip.readBytes()
                zip.closeEntry()
            }
        }
        return entries
    }

    private fun zip(parts: Map<String, ByteArray>): ByteArray =
        java.io.ByteArrayOutputStream().also { out ->
            java.util.zip.ZipOutputStream(out).use { zip ->
                parts.forEach { (name, bytes) ->
                    zip.putNextEntry(java.util.zip.ZipEntry(name))
                    zip.write(bytes)
                    zip.closeEntry()
                }
            }
        }.toByteArray()
}
