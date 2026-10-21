package org.ziyoffice.ooxml

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A save is only worth reporting as successful when the file that was
 * written can be opened again.
 */
class PackageIntegrityTest {

    private fun zipOf(parts: Map<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            parts.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    @Test
    fun aCompletePackageVerifies() {
        val bytes = zipOf(mapOf("word/document.xml" to "<w:document/>"))
        assertTrue(PackageIntegrity.verify(bytes, PackageKind.DOCUMENT))
    }

    @Test
    fun aPackageWithoutThePartItNeedsIsRejected() {
        val bytes = zipOf(mapOf("word/styles.xml" to "<w:styles/>"))
        assertFalse(PackageIntegrity.verify(bytes, PackageKind.DOCUMENT))
    }

    @Test
    fun anEmptyFileIsRejected() {
        assertFalse(PackageIntegrity.verify(ByteArray(0), PackageKind.DOCUMENT))
    }

    @Test
    fun bytesThatAreNotAZipAreRejected() {
        assertFalse(PackageIntegrity.verify("not a zip".toByteArray(), PackageKind.DOCUMENT))
    }

    @Test
    fun aTruncatedPackageIsRejected() {
        val bytes = zipOf(mapOf("word/document.xml" to "<w:document/>"))
        val truncated = bytes.copyOf(bytes.size / 2)
        assertFalse(PackageIntegrity.verify(truncated, PackageKind.DOCUMENT))
    }

    @Test
    fun everyFormatHasItsOwnPart() {
        assertTrue(
            PackageIntegrity.verify(
                zipOf(mapOf("xl/workbook.xml" to "<workbook/>")),
                PackageKind.SPREADSHEET,
            ),
        )
        assertTrue(
            PackageIntegrity.verify(
                zipOf(mapOf("ppt/presentation.xml" to "<p:presentation/>")),
                PackageKind.PRESENTATION,
            ),
        )
    }

    @Test
    fun aDocumentZiyOfficeWroteVerifies() {
        val bytes = DocxWriter().write(
            org.ziyoffice.model.Document(listOf(org.ziyoffice.model.Paragraph())),
        )
        assertTrue(PackageIntegrity.verify(bytes, PackageKind.DOCUMENT))
    }
}
