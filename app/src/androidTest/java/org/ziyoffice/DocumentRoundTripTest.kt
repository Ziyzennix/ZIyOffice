package org.ziyoffice

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.ziyoffice.model.Document
import org.ziyoffice.model.Paragraph
import org.ziyoffice.model.Run
import org.ziyoffice.model.applyText
import org.ziyoffice.ooxml.DocxEditor
import org.ziyoffice.ooxml.DocxReader
import org.ziyoffice.ooxml.DocxWriter

/**
 * The document round trip, on a real phone.
 *
 * The JVM tests cover this with files in memory. This one goes through the
 * storage layer the app actually uses, because that's where the interesting
 * failures live: a save that works in memory but writes a broken file to disk
 * is still a broken save.
 */
@RunWith(AndroidJUnit4::class)
class DocumentRoundTripTest {

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun scratchFile(name: String): File =
        File(context.filesDir, name).also { it.delete() }

    @Test
    fun editingAFileOnTheDeviceKeepsTheFormattingAroundTheEdit() {
        val file = scratchFile("round-trip.docx")

        val original = Document(
            blocks = listOf(
                Paragraph(runs = listOf(Run("Hello "), Run("world", bold = true))),
            ),
        )
        file.writeBytes(DocxWriter().write(original))

        // Read it the way the editor reads it.
        val source = DocxReader().readSource(file.inputStream())
        val document = source.document
        assertEquals(
            listOf("Hello ", "world"),
            document.blocks.filterIsInstance<Paragraph>().first().runs.map { it.text },
        )

        // Type at the end, the way the editor does.
        val edited = document.copy(
            blocks = document.blocks.map { block ->
                if (block is Paragraph) block.applyText("Hello world, edited") else block
            },
        )

        // Save it, through the same path the app uses.
        file.writeBytes(DocxEditor(source).save(edited))

        // Read it back.
        val reread = DocxReader().read(file.inputStream())
        val after = reread.blocks.filterIsInstance<Paragraph>().first()
        assertEquals("Hello world, edited", after.runs.joinToString("") { it.text })
        assertTrue(
            "the bold run should still be bold, got ${after.runs}",
            after.runs.any { it.bold && it.text.contains("world") },
        )
    }

    @Test
    fun aFileSavedOnTheDeviceIsStillAValidPackage() {
        val file = scratchFile("valid-package.docx")
        val document = Document(blocks = listOf(Paragraph(runs = listOf(Run("content")))))
        file.writeBytes(DocxWriter().write(document))

        val source = DocxReader().readSource(file.inputStream())
        file.writeBytes(DocxEditor(source).save(source.document))

        // A package has to start with the ZIP local file header.
        val header = file.readBytes().take(4)
        assertEquals(listOf(0x50, 0x4B, 0x03, 0x04), header.map { it.toInt() and 0xFF })
    }
}
