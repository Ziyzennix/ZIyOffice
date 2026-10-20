package org.ziyoffice.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class SlidesTest {
    @Test
    fun newSlidesHaveUniqueIncreasingPartNumbersAndKeepTextOnReorder() {
        val deck = SlideDeck.blank(2)
            .withLines(0, listOf("First"))
            .withLines(1, listOf("Second"))
            .withSlideAdded(0)
            .withLines(1, listOf("New"))
            .withSlideMoved(1, 1)

        assertEquals(listOf("First", "Second", "New"), deck.slides.map { it.title })
        assertEquals(listOf(1, 2, 3), deck.slides.map { it.number })
        assertEquals(3, deck.slides.map { it.number }.toSet().size)
    }

    @Test
    fun invalidSlideOperationsReturnSameDeck() {
        val deck = SlideDeck.blank(1)
        assertSame(deck, deck.withSlideMoved(0, 1))
        assertSame(deck, deck.withSlideRemoved(0))
        assertSame(deck, deck.withLines(4, listOf("ignored")))
    }
}
