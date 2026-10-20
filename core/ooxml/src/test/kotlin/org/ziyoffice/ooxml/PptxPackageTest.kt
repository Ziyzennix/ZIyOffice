package org.ziyoffice.ooxml

import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import kotlin.test.Test
import kotlin.test.assertTrue
import org.ziyoffice.model.SlideDeck

class PptxPackageTest {
    @Test
    fun createdPresentationContainsThemeMasterAndLayoutParts() {
        val parts = mutableSetOf<String>()
        ZipInputStream(ByteArrayInputStream(PptxWriter().create(SlideDeck.blank()))).use { zip ->
            while (true) parts += (zip.nextEntry?.name ?: break)
        }
        assertTrue("ppt/theme/theme1.xml" in parts)
        assertTrue("ppt/slideMasters/slideMaster1.xml" in parts)
        assertTrue("ppt/slideLayouts/slideLayout1.xml" in parts)
    }
}
