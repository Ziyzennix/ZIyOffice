package org.ziyoffice.pdf

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The file layer, against files that were not written by this code.
 *
 * The form fixture is built by a small Python script that writes the bytes
 * itself, so the reader and the fixture cannot share a mistake. The other
 * two came out of a real office suite.
 */
class PdfRealFileTest {

    private fun fixture(name: String): ByteArray =
        checkNotNull(javaClass.classLoader?.getResourceAsStream("libreoffice/$name")) {
            "missing test fixture: $name"
        }.use { it.readBytes() }

    @Test
    fun theFieldsOfARealFormAreFound() {
        val file = assertNotNull(PdfFile.open(fixture("form.pdf")))
        val form = assertNotNull(AcroForm.read(file))
        assertEquals(3, form.size)
        assertEquals(listOf("full name", "agree", "country"), form.fields.map { it.name })
    }

    @Test
    fun whatARealFormHoldsIsRead() {
        val file = assertNotNull(PdfFile.open(fixture("form.pdf")))
        val form = assertNotNull(AcroForm.read(file))
        assertEquals("Ada Lovelace", assertNotNull(form.field("full name")).value)
        assertEquals("Off", assertNotNull(form.field("agree")).value)
        assertEquals("Norway", assertNotNull(form.field("country")).value)
        assertEquals(
            listOf("Norway", "Sweden", "Denmark"),
            assertNotNull(form.field("country")).options,
        )
    }

    @Test
    fun theBoxesOfARealFormAreFound() {
        val file = assertNotNull(PdfFile.open(fixture("form.pdf")))
        val form = assertNotNull(AcroForm.read(file))
        val field = assertNotNull(form.field("full name"))
        val widget = assertNotNull(field.widgets.firstOrNull())
        assertEquals(72.0, widget.rect.left)
        assertEquals(372.0, widget.rect.right)
        assertEquals(0, widget.pageIndex)
    }

    @Test
    fun aValueIsWrittenIntoARealForm() {
        val original = fixture("form.pdf")
        val file = assertNotNull(PdfFile.open(original))
        val bytes = PdfFormWriter(file).write(mapOf("full name" to "Grace Hopper"))
        val reopened = assertNotNull(PdfFile.open(bytes))
        val form = assertNotNull(AcroForm.read(reopened))
        assertEquals("Grace Hopper", assertNotNull(form.field("full name")).value)
        // The rest of the document is untouched.
        assertEquals("Off", assertNotNull(form.field("agree")).value)
        assertEquals(1, reopened.pages.size)
    }

    @Test
    fun aRealFormWithoutAChangeIsLeftAlone() {
        val original = fixture("form.pdf")
        val file = assertNotNull(PdfFile.open(original))
        val bytes = PdfFormWriter(file).write(mapOf("full name" to "Grace Hopper"))
        // The bytes that were there are still there, in the same order.
        assertTrue(bytes.size > original.size)
        for (index in original.indices) {
            assertEquals(original[index], bytes[index], "byte $index changed")
        }
    }

    @Test
    fun thePagesOfARealDocumentAreFound() {
        val file = assertNotNull(PdfFile.open(fixture("form-plain.pdf")))
        assertEquals(3, file.pages.size)
        assertEquals(612.0, file.pages.first().widthPoints)
        assertEquals(792.0, file.pages.first().heightPoints)
    }

    @Test
    fun aDocumentFromARealOfficeSuiteOpens() {
        val file = assertNotNull(PdfFile.open(fixture("libreoffice.pdf")))
        assertTrue(file.pages.isNotEmpty(), "the document should have pages")
        assertNotNull(file.catalog)
        assertEquals("Catalog", file.catalog?.name("Type"))
    }

    @Test
    fun aDocumentFromARealOfficeSuiteHasNoForm() {
        val file = assertNotNull(PdfFile.open(fixture("libreoffice.pdf")))
        val form = AcroForm.read(file)
        assertTrue(form == null || form.isEmpty)
    }
}
