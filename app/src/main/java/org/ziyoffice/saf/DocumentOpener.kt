package org.ziyoffice.saf

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable

/**
 * MIME types ZiyOffice offers in the system file picker.
 *
 * The picker is a Storage Access Framework flow: the user picks a real file in
 * a real folder and the app gets a revocable grant. No broad storage
 * permission, no access to anything the user did not pick.
 */
private val SUPPORTED_MIME_TYPES = arrayOf(
    "application/pdf",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document", // .docx
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", // .xlsx
    PPTX_MIME_TYPE, // .pptx
    "application/vnd.oasis.opendocument.text", // .odt
    "application/vnd.oasis.opendocument.spreadsheet", // .ods
    "application/vnd.oasis.opendocument.presentation", // .odp
    "text/csv",
    "text/plain",
    "application/rtf",
)

/**
 * Returns a function that launches the document picker; [onPicked] gets the uri.
 * Pass [mimeTypes] to narrow it to one kind of file (Home's PDF tile does this).
 */
@Composable
fun rememberDocumentPicker(
    mimeTypes: Array<String> = SUPPORTED_MIME_TYPES,
    onPicked: (Uri) -> Unit,
): () -> Unit {
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) onPicked(uri)
    }
    return { launcher.launch(mimeTypes) }
}

/** The picker narrowed to PDFs, used by Home's "Open PDF" tile. */
val PDF_MIME_TYPES = arrayOf("application/pdf")

const val DOCX_MIME_TYPE =
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document"

const val XLSX_MIME_TYPE =
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

const val PPTX_MIME_TYPE =
    "application/vnd.openxmlformats-officedocument.presentationml.presentation"

/**
 * Returns a function that launches the system "create document" flow for a new
 * file of [mimeType]; [onCreated] receives the uri of the empty file the user
 * named. The app then writes a fresh package into it, so the new document lives
 * where the user chose, owned by them, not in an app-private sandbox.
 */
@Composable
fun rememberDocumentCreator(
    mimeType: String = DOCX_MIME_TYPE,
    onCreated: (Uri) -> Unit,
): (String) -> Unit {
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(mimeType)
    ) { uri ->
        if (uri != null) onCreated(uri)
    }
    return { suggestedName -> launcher.launch(suggestedName) }
}
