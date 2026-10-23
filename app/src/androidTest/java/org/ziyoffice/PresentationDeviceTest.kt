package org.ziyoffice

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.ziyoffice.model.Slide
import org.ziyoffice.model.SlideDeck
import org.ziyoffice.model.SlideTransition
import org.ziyoffice.ooxml.PptxReader
import org.ziyoffice.ooxml.PptxWriter

@RunWith(AndroidJUnit4::class)
class PresentationDeviceTest {

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun scratchFile(name: String): File =
        File(context.filesDir, name).also { it.delete() }

    @Test
    fun presentationRoundTripOnAndroidStoragePreservesDeck() {
        val file = scratchFile("test-presentation.pptx")

        val deck = SlideDeck(
            slides = listOf(
                Slide(
                    number = 1,
                    lines = listOf("Quarterly Roadmap", "Key initiatives for mobile office suite"),
                    transition = SlideTransition.FADE,
                ),
                Slide(
                    number = 2,
                    lines = listOf("Architecture", "Clean module boundaries and offline privacy"),
                    transition = SlideTransition.PUSH,
                ),
            ),
        )
        val written = PptxWriter().create(deck)
        file.writeBytes(written)

        // Verify ZIP header
        val header = file.readBytes().take(4)
        assertEquals(listOf(0x50, 0x4B, 0x03, 0x04), header.map { it.toInt() and 0xFF })

        // Read back from Android filesystem
        val read = PptxReader().read(file.inputStream())
        assertEquals(2, read.slides.size)
        assertEquals("Quarterly Roadmap", read.slides[0].lines[0])
        assertEquals("Architecture", read.slides[1].lines[0])
        assertEquals(SlideTransition.FADE, read.slides[0].transition)
        assertEquals(SlideTransition.PUSH, read.slides[1].transition)
    }
}
