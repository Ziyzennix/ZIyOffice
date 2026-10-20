package org.ziyoffice.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.ziyoffice.ooxml.DroppedFeature

class ContentLossCopyTest {

    private val features = listOf(
        DroppedFeature("Comments", 3),
        DroppedFeature("Tracked changes", 12),
        DroppedFeature("Headers", 1),
        DroppedFeature("Footers", 1),
    )

    @Test
    fun summaryNamesThreeAndCountsTheRest() {
        assertEquals("Comments, Tracked changes, Headers and 1 more", ContentLossCopy.summary(features))
    }

    @Test
    fun summaryOmitsTheCountWhenEverythingFits() {
        assertEquals(
            "Comments and Tracked changes",
            ContentLossCopy.summary(features.take(2)),
        )
    }

    @Test
    fun bodyListsEachItemOnItsOwnLine() {
        val body = ContentLossCopy.body(features, ".docx")
        assertTrue(body.contains(".docx"), body)
        assertTrue(body.contains("Save as copy"), body)
        // Every feature is listed, one per line, so the dialog can be scanned.
        features.forEach { feature ->
            assertTrue(body.contains("\u2022  ${feature.label}"), "missing ${feature.label}: $body")
        }
        assertEquals(4, body.lines().count { it.startsWith("\u2022") }, body)
    }

    @Test
    fun bannerStatesTheConsequenceInOneShortLine() {
        val banner = ContentLossCopy.banner(features)
        // On a 360 dp phone the old banner enumerated every feature and became a
        // wall of text; the count is what fits, and the list is one tap away.
        assertTrue(banner.contains("remove"), banner)
        assertTrue(banner.contains("4"), banner)
        assertFalse(banner.contains("Comments"), banner)
        assertTrue(banner.length <= 60, "banner too long for a phone: $banner")
    }

    @Test
    fun bannerUsesSingularForOneItem() {
        val banner = ContentLossCopy.banner(listOf(DroppedFeature("Comments", 1)))
        assertTrue(banner.contains("1 unsupported item"), banner)
        assertFalse(banner.contains("kinds"), banner)
    }


    @Test
    fun gateAsksOnceAndOnlyWhenThereIsSomethingToLose() {
        val dropped = listOf(DroppedFeature("Comments", 1))
        assertTrue(
            ContentLossCopy.requiresConfirmation(dropped, accepted = false, writesWholeFormat = true),
        )
        // Accepted in this session: later saves of the same file stay silent.
        assertFalse(
            ContentLossCopy.requiresConfirmation(dropped, accepted = true, writesWholeFormat = true),
        )
        // CSV export writes cell values only, so the package is not at risk.
        assertFalse(
            ContentLossCopy.requiresConfirmation(dropped, accepted = false, writesWholeFormat = false),
        )
        assertFalse(
            ContentLossCopy.requiresConfirmation(
                emptyList(),
                accepted = false,
                writesWholeFormat = true,
            ),
        )
    }

    @Test
    fun singleFeatureReadsNaturally() {
        assertEquals("Comments", ContentLossCopy.summary(listOf(DroppedFeature("Comments", 2))))
    }
}
