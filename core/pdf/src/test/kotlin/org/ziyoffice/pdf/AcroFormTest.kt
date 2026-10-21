package org.ziyoffice.pdf

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Reading the fields of a form.
 */
class AcroFormTest {

    /**
     * A file with one page and three fields on it: a text box, a tick box
     * and a list to pick from.
     */
    private fun formFile(): ByteArray {
        val builder = PdfBuilder()
        builder.add(
            "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] " +
                "/Annots [3 0 R 4 0 R 5 0 R] >>",
        )
        builder.add("<< /Type /Pages /Kids [1 0 R] /Count 1 >>")
        builder.add(
            "<< /Type /Annot /Subtype /Widget /FT /Tx /T (full name) " +
                "/V (Ada) /Rect [100 700 300 720] /DA (/Helv 10 Tf 0 g) >>",
        )
        builder.add(
            "<< /Type /Annot /Subtype /Widget /FT /Btn /T (agree) " +
                "/V /Off /Rect [100 650 116 666] /AP << /N << /Yes << >> /Off << >> >> >> >>",
        )
        builder.add(
            "<< /Type /Annot /Subtype /Widget /FT /Ch /T (country) /Ff 131072 " +
                "/V (Norway) /Opt [[(NO) (Norway)] [(SE) (Sweden)] [(DK) (Denmark)]] " +
                "/Rect [100 600 300 620] >>",
        )
        val catalog = builder.add(
            "<< /Type /Catalog /Pages 2 0 R /AcroForm 7 0 R >>",
        )
        builder.add("<< /Fields [3 0 R 4 0 R 5 0 R] /DA (/Helv 0 Tf 0 g) >>")
        return builder.build(catalog)
    }

    private fun readForm(): PdfForm {
        val file = assertNotNull(PdfFile.open(formFile()))
        return assertNotNull(AcroForm.read(file))
    }

    @Test
    fun theFieldsOfAFormAreFound() {
        val form = readForm()
        assertEquals(3, form.size)
        assertEquals(listOf("full name", "agree", "country"), form.fields.map { it.name })
    }

    @Test
    fun aTextFieldIsATextField() {
        val field = readForm().field("full name")
        assertNotNull(field)
        assertEquals(PdfFieldKind.TEXT, field.kind)
        assertEquals("Ada", field.value)
        assertEquals("/Helv 10 Tf 0 g", field.defaultAppearance)
    }

    @Test
    fun aTickBoxIsATickBox() {
        val field = readForm().field("agree")
        assertNotNull(field)
        assertEquals(PdfFieldKind.CHECKBOX, field.kind)
        assertEquals("Off", field.value)
    }

    @Test
    fun aListIsAList() {
        val field = readForm().field("country")
        assertNotNull(field)
        assertEquals(PdfFieldKind.DROPDOWN, field.kind)
        assertEquals(listOf("Norway", "Sweden", "Denmark"), field.options)
    }

    @Test
    fun aFieldKnowsWhichPageItIsOn() {
        val form = readForm()
        assertTrue(form.fields.all { it.pageIndex == 0 })
        assertEquals(3, form.fieldsOn(0).size)
    }

    @Test
    fun aBoxKnowsWhereItSits() {
        val field = readForm().field("full name")
        val widget = assertNotNull(field?.widgets?.firstOrNull())
        assertEquals(100.0, widget.rect.left)
        assertEquals(720.0, widget.rect.top)
        assertEquals(300.0, widget.rect.right)
    }

    @Test
    fun theStateThatMeansOnIsFound() {
        val field = readForm().field("agree")
        val widget = assertNotNull(field?.widgets?.firstOrNull())
        assertEquals("Yes", widget.onState)
    }

    @Test
    fun aFieldIsPlacedOverThePageAsItIsDrawn() {
        val file = assertNotNull(PdfFile.open(formFile()))
        val page = file.pages.first()
        val field = assertNotNull(AcroForm.read(file)?.field("full name"))
        val widget = assertNotNull(field.widgets.firstOrNull())
        val fractions = widget.rect.fractionsOn(page)
        // The box is 200 points wide on a 595 point page.
        assertEquals(100.0 / 595.0, fractions.left, 0.0001)
        assertEquals(200.0 / 595.0, fractions.width, 0.0001)
        // It sits near the top of the page, so the top is a small number.
        assertEquals((842.0 - 720.0) / 842.0, fractions.top, 0.0001)
    }

    @Test
    fun aTurnedPageTurnsTheBoxWithIt() {
        val builder = PdfBuilder()
        builder.add(
            "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Rotate 90 " +
                "/Annots [3 0 R] >>",
        )
        builder.add("<< /Type /Pages /Kids [1 0 R] /Count 1 >>")
        builder.add(
            "<< /Type /Annot /Subtype /Widget /FT /Tx /T (name) " +
                "/Rect [0 0 595 842] >>",
        )
        val catalog = builder.add("<< /Type /Catalog /Pages 2 0 R /AcroForm 5 0 R >>")
        builder.add("<< /Fields [3 0 R] >>")
        val file = assertNotNull(PdfFile.open(builder.build(catalog)))
        val field = assertNotNull(AcroForm.read(file)?.field("name"))
        val fractions = assertNotNull(field.widgets.firstOrNull()).rect.fractionsOn(file.pages.first())
        // A box covering the whole page still covers the whole page.
        assertEquals(0.0, fractions.left, 0.0001)
        assertEquals(1.0, fractions.width, 0.0001)
        assertEquals(1.0, fractions.height, 0.0001)
    }

    @Test
    fun aFileWithNoFormHasNoFields() {
        val builder = PdfBuilder()
        builder.add("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] >>")
        builder.add("<< /Type /Pages /Kids [1 0 R] /Count 1 >>")
        val catalog = builder.add("<< /Type /Catalog /Pages 2 0 R >>")
        val file = assertNotNull(PdfFile.open(builder.build(catalog)))
        val form = AcroForm.read(file)
        assertTrue(form == null || form.isEmpty)
    }

    @Test
    fun anEncryptedFileHasNoReadableForm() {
        val builder = PdfBuilder()
        builder.add("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] >>")
        builder.add("<< /Type /Pages /Kids [1 0 R] /Count 1 >>")
        builder.add("<< /Type /Annot /Subtype /Widget /FT /Tx /T (name) /Rect [0 0 10 10] >>")
        val catalog = builder.add("<< /Type /Catalog /Pages 2 0 R /AcroForm 5 0 R >>")
        builder.add("<< /Fields [3 0 R] >>")
        builder.add("<< /Filter /Standard /V 2 /R 3 /Length 128 >>")
        // The encryption dictionary belongs in the trailer, not the catalogue.
        val file = assertNotNull(PdfFile.open(builder.build(catalog, "/Encrypt 6 0 R")))
        assertTrue(file.encrypted)
        assertNull(AcroForm.read(file))
    }
}
