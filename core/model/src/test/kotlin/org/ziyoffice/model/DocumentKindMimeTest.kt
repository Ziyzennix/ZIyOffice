package org.ziyoffice.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The MIME type an intent carries decides how a shared file opens, and it is the
 * only reliable signal: content providers sometimes report a display name with
 * no extension at all ("document:1000142160"), which reads as [DocumentKind.UNKNOWN]
 * and sends the user to the unsupported-file screen.
 */
class DocumentKindMimeTest {

    @Test
    fun mapsTheFormatsTheAppOpens() {
        assertEquals(
            DocumentKind.DOCUMENT,
            DocumentKind.fromMimeType(
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            ),
        )
        assertEquals(
            DocumentKind.SPREADSHEET,
            DocumentKind.fromMimeType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            ),
        )
        assertEquals(
            DocumentKind.PRESENTATION,
            DocumentKind.fromMimeType(
                "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            ),
        )
        assertEquals(DocumentKind.PDF, DocumentKind.fromMimeType("application/pdf"))
        assertEquals(DocumentKind.SPREADSHEET, DocumentKind.fromMimeType("text/csv"))
    }

    @Test
    fun isCaseInsensitiveBecauseSendersAreInconsistent() {
        assertEquals(DocumentKind.PDF, DocumentKind.fromMimeType("Application/PDF"))
    }

    @Test
    fun unknownOrMissingTypeIsNullRatherThanAGuess() {
        assertNull(DocumentKind.fromMimeType(null))
        assertNull(DocumentKind.fromMimeType(""))
        assertNull(DocumentKind.fromMimeType("image/png"))
    }

    @Test
    fun everyMappedTypeHasAnExtensionAndTheExtensionReadsBackAsThatKind() {
        val types = listOf(
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "text/csv",
            "text/plain",
            "application/rtf",
            "application/vnd.oasis.opendocument.text",
            "application/vnd.oasis.opendocument.spreadsheet",
            "application/vnd.oasis.opendocument.presentation",
        )
        types.forEach { type ->
            val kind = DocumentKind.fromMimeType(type)
            val extension = DocumentKind.extensionFor(type)
            assertEquals(
                kind,
                extension?.let { DocumentKind.fromFileName("file.$it") },
                "round trip failed for $type",
            )
        }
    }
}
