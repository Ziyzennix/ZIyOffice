package org.ziyoffice.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.ziyoffice.ooxml.PreservedFeature

/**
 * The notice about content the app keeps but can't edit has to be accurate.
 *
 * A save no longer removes anything, so the wording must not claim that it
 * does: the user should learn which parts of their document are out of reach,
 * not be frightened about damage that will not happen.
 */
class PreservedCopyTest {

    private val features = listOf(
        PreservedFeature("Comments", 2),
        PreservedFeature("Footnotes", 1),
        PreservedFeature("Tracked changes", 1),
    )

    @Test
    fun theSummaryNamesTheFirstFewAndCountsTheRest() {
        assertEquals(
            "Comments, Footnotes and Tracked changes",
            PreservedCopy.summary(features),
        )
        assertEquals(
            "Comments, Footnotes, Tracked changes and 1 more",
            PreservedCopy.summary(features + PreservedFeature("Equations", 1)),
        )
    }

    @Test
    fun theSummaryHandlesShortLists() {
        assertEquals("", PreservedCopy.summary(emptyList()))
        assertEquals("Comments", PreservedCopy.summary(listOf(features[0])))
        assertEquals("Comments and Footnotes", PreservedCopy.summary(features.take(2)))
    }

    @Test
    fun theBodySaysWhatIsKeptAndNotThatAnythingIsLost() {
        val body = PreservedCopy.body(features, ".docx")

        assertTrue(body.contains("keeps but can't edit"))
        assertTrue(body.contains("Comments"))
        assertTrue(body.contains("Footnotes"))
        assertTrue(body.contains("Tracked changes"))
        assertFalse(body.contains("removes"), "a save no longer removes anything")
        assertFalse(body.contains("cannot write back"))
    }

    @Test
    fun theBannerCountsTheKindsWithoutListingThem() {
        val banner = PreservedCopy.banner(features)

        assertTrue(banner.contains("3"))
        assertFalse(banner.contains("Comments"), "the banner stays to one line")
        assertFalse(banner.contains("remove"))
    }

    @Test
    fun theBannerHandlesASingleKind() {
        assertTrue(PreservedCopy.banner(listOf(features[0])).contains("1 kind"))
    }
}
