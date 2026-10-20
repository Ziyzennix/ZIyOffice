package org.ziyoffice.ooxml

import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.ziyoffice.model.SlideDeck

class PptxWriterTest {
    @Test
    fun newPresentationIsAValidTextRoundTrip() {
        val deck = SlideDeck.blank().copy(
            slides = listOf(org.ziyoffice.model.Slide(1, listOf("New title", "New body"))),
        )
        val roundTrip = PptxReader().read(ByteArrayInputStream(PptxWriter().create(deck)))
        assertEquals(listOf("New title", "New body"), roundTrip.slides.single().lines)
    }

    @Test
    fun createdPackageContainsRequiredThemeMasterAndLayoutParts() {
        val names = mutableSetOf<String>()
        ZipInputStream(ByteArrayInputStream(PptxWriter().create(SlideDeck.blank()))).use { zip ->
            while (true) names += (zip.nextEntry?.name ?: break)
        }
        assertTrue("ppt/theme/theme1.xml" in names)
        assertTrue("ppt/slideMasters/slideMaster1.xml" in names)
        assertTrue("ppt/slideLayouts/slideLayout1.xml" in names)
    }
}
