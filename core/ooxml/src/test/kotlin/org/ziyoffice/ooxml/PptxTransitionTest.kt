package org.ziyoffice.ooxml

import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.ziyoffice.model.Slide
import org.ziyoffice.model.SlideDeck
import org.ziyoffice.model.SlideTransition

/**
 * The transition of a slide: read from a file, written back into it.
 */
class PptxTransitionTest {

    private fun deckOf(transition: SlideTransition): SlideDeck = SlideDeck(
        slides = listOf(
            Slide(number = 1, lines = listOf("First"), transition = transition),
            Slide(number = 2, lines = listOf("Second"), transition = transition),
        ),
    )

    /** One part of a package, as text. */
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
    fun aTransitionSurvivesASave() {
        val original = PptxWriter().create(deckOf(SlideTransition.FADE))
        val read = PptxReader().read(original.inputStream())
        assertEquals(SlideTransition.FADE, read.slides.first().transition)
    }

    @Test
    fun everyTransitionSurvivesASave() {
        SlideTransition.entries.forEach { transition ->
            val original = PptxWriter().create(deckOf(transition))
            val read = PptxReader().read(original.inputStream())
            assertEquals(
                transition,
                read.slides.first().transition,
                "the $transition transition should survive",
            )
        }
    }

    @Test
    fun aTransitionIsWrittenIntoTheSlide() {
        val original = PptxWriter().create(deckOf(SlideTransition.PUSH))
        val slide = partOf(original, "ppt/slides/slide1.xml")
        assertTrue(slide.contains("<p:transition"), "the slide should carry a transition")
        assertTrue(slide.contains("<p:push"), "the transition should be a push")
    }

    @Test
    fun theTransitionSitsWhereTheFormatSaysItDoes() {
        val original = PptxWriter().create(deckOf(SlideTransition.FADE))
        val slide = partOf(original, "ppt/slides/slide1.xml")
        val slideStart = slide.indexOf("<p:sld ")
        val transitionAt = slide.indexOf("<p:transition")
        val closeAt = slide.indexOf("</p:sld>")
        assertTrue(slideStart in 0 until transitionAt, "the transition comes after the slide opens")
        assertTrue(transitionAt in 0 until closeAt, "the transition comes before the slide closes")
    }

    @Test
    fun aTransitionIsReplacedRatherThanAdded() {
        val original = PptxWriter().create(deckOf(SlideTransition.FADE))
        val changed = PptxWriter().edit(original, deckOf(SlideTransition.ZOOM))
        val slide = partOf(changed, "ppt/slides/slide1.xml")
        assertEquals(1, Regex("<p:transition").findAll(slide).count(), "one transition per slide")
        assertTrue(slide.contains("<p:zoom"))
        assertTrue(!slide.contains("<p:fade/>"))
    }

    @Test
    fun aSlideWithNoTransitionHasNone() {
        val original = PptxWriter().create(deckOf(SlideTransition.NONE))
        val read = PptxReader().read(original.inputStream())
        assertEquals(SlideTransition.NONE, read.slides.first().transition)
    }

    @Test
    fun theTextOfTheSlideIsNotLost() {
        val original = PptxWriter().create(deckOf(SlideTransition.FADE))
        val read = PptxReader().read(original.inputStream())
        assertEquals("First", read.slides.first().title)
        assertEquals("Second", read.slides[1].title)
    }
}
