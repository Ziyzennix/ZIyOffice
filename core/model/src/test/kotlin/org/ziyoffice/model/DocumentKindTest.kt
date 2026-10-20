package org.ziyoffice.model

import kotlin.test.Test
import kotlin.test.assertEquals

class DocumentKindTest {

    @Test
    fun resolvesCommonExtensions() {
        assertEquals(DocumentKind.DOCUMENT, DocumentKind.fromFileName("report.docx"))
        assertEquals(DocumentKind.SPREADSHEET, DocumentKind.fromFileName("budget.XLSX"))
        assertEquals(DocumentKind.PRESENTATION, DocumentKind.fromFileName("pitch.pptx"))
        assertEquals(DocumentKind.PDF, DocumentKind.fromFileName("scan.pdf"))
        assertEquals(DocumentKind.DOCUMENT, DocumentKind.fromFileName("notes.txt"))
    }

    @Test
    fun unknownForFilesWithoutKnownExtension() {
        assertEquals(DocumentKind.UNKNOWN, DocumentKind.fromFileName("README"))
        assertEquals(DocumentKind.UNKNOWN, DocumentKind.fromFileName("archive.zip"))
        assertEquals(DocumentKind.UNKNOWN, DocumentKind.fromFileName(""))
    }

    @Test
    fun handlesNamesWithMultipleDots() {
        assertEquals(DocumentKind.PRESENTATION, DocumentKind.fromFileName("2026.final.pitch.pptx"))
        assertEquals(DocumentKind.PDF, DocumentKind.fromFileName("/storage/emulated/0/Scan 001.pdf"))
    }
}
