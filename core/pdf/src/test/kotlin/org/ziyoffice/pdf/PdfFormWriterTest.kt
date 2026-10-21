package org.ziyoffice.pdf

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Writing values into a form.
 */
class PdfFormWriterTest {

    /** A file with a text box and a tick box on one page. */
    private fun formFile(): ByteArray {
        val builder = PdfBuilder()
        builder.add(
            "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] " +
                "/Annots [3 0 R 4 0 R] >>",
        )
        builder.add("<< /Type /Pages /Kids [1 0 R] /Count 1 >>")
        builder.add(
            "<< /Type /Annot /Subtype /Widget /FT /Tx /T (full name) " +
                "/V (Ada) /Rect [100 700 300 720] /DA (/Helv 12 Tf 0 g) >>",
        )
        builder.add(
            "<< /Type /Annot /Subtype /Widget /FT /Btn /T (agree) " +
                "/V /Off /Rect [100 650 116 666] /AP << /N << /Yes << >> /Off << >> >> >> >>",
        )
        val catalog = builder.add("<< /Type /Catalog /Pages 2 0 R /AcroForm 6 0 R >>")
        builder.add("<< /Fields [3 0 R 4 0 R] /DA (/Helv 0 Tf 0 g) >>")
        return builder.build(catalog)
    }

    private fun written(values: Map<String, String>): Pair<ByteArray, PdfFile> {
        val original = formFile()
        val file = assertNotNull(PdfFile.open(original))
        val bytes = PdfFormWriter(file).write(values)
        val reopened = assertNotNull(PdfFile.open(bytes))
        return bytes to reopened
    }

    @Test
    fun aValueIsWrittenIntoTheField() {
        val (_, file) = written(mapOf("full name" to "Grace"))
        val form = assertNotNull(AcroForm.read(file))
        assertEquals("Grace", assertNotNull(form.field("full name")).value)
    }

    @Test
    fun aTickBoxCanBeTicked() {
        val (_, file) = written(mapOf("agree" to "Yes"))
        val form = assertNotNull(AcroForm.read(file))
        val field = assertNotNull(form.field("agree"))
        assertEquals("Yes", field.value)
        // The state of the box is what the viewer draws.
        assertEquals("Yes", assertNotNull(field.widgets.firstOrNull()).onState)
    }

    @Test
    fun theBytesThatWereThereStayThere() {
        val original = formFile()
        val file = assertNotNull(PdfFile.open(original))
        val bytes = PdfFormWriter(file).write(mapOf("full name" to "Grace"))
        // The new file starts with the old one, byte for byte.
        assertTrue(bytes.size > original.size)
        original.forEachIndexed { index, byte ->
            assertEquals(byte, bytes[index], "byte $index changed")
        }
    }

    @Test
    fun theFieldGetsAnAppearanceToDraw() {
        val (_, file) = written(mapOf("full name" to "Grace"))
        val form = assertNotNull(AcroForm.read(file))
        val field = assertNotNull(form.field("full name"))
        val appearances = assertNotNull(field.dict.dict("AP"))
        val stream = assertNotNull(file.resolve(appearances["N"]) as? PdfStream)
        val content = String(stream.decoded, Charsets.ISO_8859_1)
        assertTrue(content.contains("Grace"), "the text should be in the appearance")
        assertTrue(content.contains("BT"), "the appearance should draw text")
    }

    @Test
    fun theFormAsksTheViewerToRedrawTheFields() {
        val (_, file) = written(mapOf("full name" to "Grace"))
        val catalog = assertNotNull(file.catalog)
        val acroForm = assertNotNull(file.resolveDict(catalog["AcroForm"]))
        assertEquals(PdfBool(true), acroForm["NeedAppearances"])
    }

    @Test
    fun aFieldThatWasNotNamedKeepsItsValue() {
        val (_, file) = written(mapOf("full name" to "Grace"))
        val form = assertNotNull(AcroForm.read(file))
        assertEquals("Off", assertNotNull(form.field("agree")).value)
    }

    @Test
    fun aFileWithNothingToChangeIsLeftAlone() {
        val original = formFile()
        val file = assertNotNull(PdfFile.open(original))
        val bytes = PdfFormWriter(file).write(emptyMap())
        assertEquals(original.size, bytes.size)
    }

    @Test
    fun theSavedFileStillHasItsPages() {
        val (_, file) = written(mapOf("full name" to "Grace"))
        assertEquals(1, file.pages.size)
        assertEquals(595.0, file.pages.first().widthPoints)
    }
}
