package org.ziyoffice.pdf

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The file layer: finding the pages of a document.
 */
class PdfFileTest {

    /** A PDF with two pages, the second one rotated. */
    private fun twoPageFile(): ByteArray {
        val builder = PdfBuilder()
        builder.add("<< /Type /Page /Parent 3 0 R /MediaBox [0 0 595 842] >>")
        builder.add("<< /Type /Page /Parent 3 0 R /MediaBox [0 0 595 842] /Rotate 90 >>")
        builder.add("<< /Type /Pages /Kids [1 0 R 2 0 R] /Count 2 >>")
        val catalog = builder.add("<< /Type /Catalog /Pages 3 0 R >>")
        return builder.build(catalog)
    }

    @Test
    fun thePagesOfAFileAreFound() {
        val file = assertNotNull(PdfFile.open(twoPageFile()))
        assertEquals(2, file.pages.size)
    }

    @Test
    fun aPageKnowsHowBigItIs() {
        val file = assertNotNull(PdfFile.open(twoPageFile()))
        val page = file.pages.first()
        assertEquals(595.0, page.widthPoints)
        assertEquals(842.0, page.heightPoints)
    }

    @Test
    fun aRotatedPageIsTurnedSideways() {
        val file = assertNotNull(PdfFile.open(twoPageFile()))
        val rotated = file.pages[1]
        assertEquals(90, rotated.rotate)
        // A page turned on its side is as wide as it was tall.
        assertEquals(842.0, rotated.widthPoints)
        assertEquals(595.0, rotated.heightPoints)
    }

    @Test
    fun aPageInheritsTheMediaBoxOfTheTree() {
        val builder = PdfBuilder()
        builder.add("<< /Type /Page /Parent 2 0 R >>")
        builder.add("<< /Type /Pages /Kids [1 0 R] /Count 1 /MediaBox [0 0 200 100] >>")
        val catalog = builder.add("<< /Type /Catalog /Pages 2 0 R >>")
        val file = assertNotNull(PdfFile.open(builder.build(catalog)))
        assertEquals(200.0, file.pages.first().widthPoints)
        assertEquals(100.0, file.pages.first().heightPoints)
    }

    @Test
    fun anObjectStreamIsUnpacked() {
        val builder = PdfBuilder()
        builder.add("<< /Type /Page /Parent ${builder.packedPagesNumber} 0 R /MediaBox [0 0 595 842] >>")
        val catalogBody = "<< /Type /Catalog /Pages ${builder.packedPagesNumber} 0 R >>"
        val pagesBody = "<< /Type /Pages /Kids [1 0 R] /Count 1 >>"
        val file = assertNotNull(PdfFile.open(builder.buildWithObjectStream(catalogBody, pagesBody)))
        assertEquals(1, file.pages.size)
        assertEquals("Catalog", file.catalog?.name("Type"))
    }

    @Test
    fun aFileWithABrokenCrossReferenceStillOpens() {
        val builder = PdfBuilder()
        builder.add("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] >>")
        builder.add("<< /Type /Pages /Kids [1 0 R] /Count 1 >>")
        val catalog = builder.add("<< /Type /Catalog /Pages 2 0 R >>")
        val file = assertNotNull(PdfFile.open(builder.buildWithBrokenXref(catalog)))
        assertEquals(1, file.pages.size)
    }

    @Test
    fun somethingThatIsNotAPdfIsRefused() {
        assertNull(PdfFile.open("hello".toByteArray()))
        assertNull(PdfFile.open(ByteArray(0)))
    }

    @Test
    fun aFileWithoutAHeaderIsStillRecognised() {
        val bytes = twoPageFile()
        // A file with something in front of the header is unusual but legal.
        val padded = ByteArray(8) + bytes
        assertTrue(PdfFile.looksLikePdf(padded))
        assertNotNull(PdfFile.open(padded))
    }

    @Test
    fun theCatalogueIsFound() {
        val file = assertNotNull(PdfFile.open(twoPageFile()))
        assertEquals("Catalog", file.catalog?.name("Type"))
    }
}
