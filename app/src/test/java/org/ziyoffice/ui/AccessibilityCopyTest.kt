package org.ziyoffice.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for the spoken descriptions the accessibility pass added. These are pure
 * functions precisely so the wording can be asserted without a device.
 */
class AccessibilityCopyTest {

    @Test
    fun recentRowDescribesNameTypeSizeAndWhenItWasOpened() {
        val file = org.ziyoffice.model.RecentFile(
            uri = "content://test/report.docx",
            displayName = "Report.docx",
            sizeBytes = 24 * 1024,
            lastOpenedAtEpochMs = System.currentTimeMillis(),
        )
        val description = recentRowDescription(file, openedLabel = "2 hours ago")
        assertTrue(description.startsWith("Report.docx"), description)
        assertTrue(description.contains("24 KB"), description)
        assertTrue(description.endsWith("opened 2 hours ago"), description)
    }

    @Test
    fun recentRowOmitsAnUnknownSizeInsteadOfSayingZero() {
        val file = org.ziyoffice.model.RecentFile(
            uri = "content://test/x.pdf",
            displayName = "x.pdf",
            sizeBytes = -1L,
            lastOpenedAtEpochMs = System.currentTimeMillis(),
        )
        val description = recentRowDescription(file, openedLabel = "")
        // No dangling separator, no "0 B" for a size the provider did not give,
        // and no bare "opened" when the platform returned no label.
        assertFalse(description.contains("0 B"), description)
        assertFalse(description.contains(", ,"), description)
        assertFalse(description.contains("opened"), description)
    }

    @Test
    fun everyFillSwatchHasADistinctSpokenName() {
        // A swatch carries its meaning in colour alone, so the names are what a
        // screen-reader user actually picks from, and they must not collide.
        val names = listOf<Int?>(
            null, 0xFFF2CC, 0xD8F3E4, 0xDCE7FF, 0xFBD9D9, 0xEFE4FF, 0xEDEDED,
        ).map { fillColourName(it) }
        assertEquals(names.size, names.toSet().size, "duplicate swatch names: $names")
        assertTrue(names.none { it.isBlank() }, names.toString())
    }

    @Test
    fun everyFontColourOptionHasADistinctSpokenName() {
        // Same rule for the font-colour menu: the name beside the swatch is the
        // only non-colour label, so it must identify the colour on its own.
        val names = COLOUR_OPTIONS.filterNotNull().map { colourName(it) }
        assertEquals(names.size, names.toSet().size, "duplicate colour names: $names")
        assertTrue(names.none { it == "Colour" }, "a colour fell through to the fallback: $names")
    }
}
