package org.ziyoffice.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ziyoffice.data.RecentFilesStore
import org.ziyoffice.model.Document
import org.ziyoffice.model.DocumentKind
import org.ziyoffice.model.Paragraph
import org.ziyoffice.model.RecentFile
import org.ziyoffice.model.Sheet
import org.ziyoffice.model.SlideDeck
import org.ziyoffice.model.Workbook
import org.ziyoffice.model.documentTemplate
import org.ziyoffice.ooxml.DocxWriter
import org.ziyoffice.ooxml.PptxWriter
import org.ziyoffice.ooxml.XlsxWriter
import org.ziyoffice.saf.PDF_MIME_TYPES
import org.ziyoffice.saf.PPTX_MIME_TYPE
import org.ziyoffice.saf.XLSX_MIME_TYPE
import org.ziyoffice.saf.rememberDocumentCreator
import org.ziyoffice.saf.rememberDocumentPicker

private enum class ZiyTab(val label: String) {
    HOME("Home"),
    FILES("Files"),
    SETTINGS("Settings"),
}

/**
 * App shell: three tabs (Home / Files / Settings) and the pushed screens behind
 * them, About among them.
 */
@Composable
fun ZiyOfficeApp(
    incomingUri: Uri? = null,
    incomingMimeType: String? = null,
    onIncomingConsumed: () -> Unit = {},
) {
    val context = LocalContext.current
    val store = remember { RecentFilesStore(context) }
    val scope = rememberCoroutineScope()
    var recents by remember { mutableStateOf(store.load()) }
    var tabName by rememberSaveable { mutableStateOf(ZiyTab.HOME.name) }
    var showAbout by rememberSaveable { mutableStateOf(false) }
    var openFileUri by rememberSaveable { mutableStateOf<String?>(null) }
    var createdFileUri by rememberSaveable { mutableStateOf<String?>(null) }
    /** The file whose form is being filled in, when one is. */
    var fillFormUri by rememberSaveable { mutableStateOf<String?>(null) }
    /**
     * A file handed over by another app. Its grant usually cannot be persisted,
     * so it is not a recents entry: it lives only for this visit, and the
     * editor's "Save as copy" is the way to keep the result.
     */
    var transientFile by remember { mutableStateOf<RecentFile?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val tab = ZiyTab.entries.firstOrNull { it.name == tabName } ?: ZiyTab.HOME

    // "Open with" / "Share to": open the document straight away instead of
    // dropping the user on Home and making them find it again.
    //
    // The effect is keyed on the uri but consumes it through a local snapshot:
    // clearing [incomingUri] re-keys it and cancels whatever it was doing,
    // which silently dropped the document. So the uri is copied first, handled
    // to completion, then reported as consumed.
    LaunchedEffect(incomingUri) {
        val uri = incomingUri ?: return@LaunchedEffect
        val remembered = withContext(Dispatchers.IO) { store.remember(uri, incomingMimeType) }
        if (remembered == null) {
            statusMessage = "Couldn't read that file."
        } else {
            if (remembered.persisted) {
                recents = store.load()
            } else {
                // One-shot grant: no recents row, just this visit. Say so, since
                // edits can only be kept through "Save as copy".
                transientFile = remembered.file
                statusMessage = "Opened from another app \u00b7 use Save as copy to keep changes"
            }
            openFileUri = remembered.file.uri
        }
        onIncomingConsumed()
    }

    val openPicker = rememberDocumentPicker { uri: Uri ->
        val entry = store.remember(uri)?.file
        recents = store.load()
        if (entry != null) {
            openFileUri = entry.uri
        } else {
            statusMessage = "Couldn't read that file."
        }
    }

    // Home's PDF tile opens a PDF straight away rather than pretending the app
    // can create a blank one, so the picker is narrowed to application/pdf.
    val openPdfPicker = rememberDocumentPicker(PDF_MIME_TYPES) { uri: Uri ->
        val entry = store.remember(uri)?.file
        recents = store.load()
        if (entry != null) {
            openFileUri = entry.uri
        } else {
            statusMessage = "Couldn't read that PDF."
        }
    }

    /** Writes a fresh package into the file the user created through SAF. */
    fun createNewDocument(uri: Uri, document: Document = Document(blocks = listOf(Paragraph()))) {
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = DocxWriter().write(document)
                    context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) }
                        ?: error("the file couldn't be created")
                }
            }
            result.fold(
                onSuccess = {
                    val entry = store.remember(uri)?.file
                    recents = store.load()
                    if (entry != null) {
                        createdFileUri = entry.uri
                        openFileUri = entry.uri
                    } else {
                        statusMessage = "Couldn't read the new document."
                    }
                },
                onFailure = { error ->
                    statusMessage = "Couldn't create the document: " +
                        (error.message ?: error.javaClass.simpleName)
                },
            )
        }
    }

    /** Writes a fresh workbook into the file the user created through SAF. */
    fun createNewSpreadsheet(uri: Uri) {
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val workbook = Workbook(sheets = listOf(Sheet(name = "Sheet1", cells = emptyList())))
                    val bytes = XlsxWriter().write(workbook)
                    context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) }
                        ?: error("the file couldn't be created")
                }
            }
            result.fold(
                onSuccess = {
                    val entry = store.remember(uri)?.file
                    recents = store.load()
                    if (entry != null) {
                        createdFileUri = entry.uri
                        openFileUri = entry.uri
                    } else {
                        statusMessage = "Couldn't read the new spreadsheet."
                    }
                },
                onFailure = { error ->
                    statusMessage = "Couldn't create the spreadsheet: " +
                        (error.message ?: error.javaClass.simpleName)
                },
            )
        }
    }

    /** Writes a fresh one-slide deck into the file the user created through SAF. */
    fun createNewPresentation(uri: Uri) {
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = PptxWriter().create(SlideDeck.blank())
                    context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) }
                        ?: error("the file couldn't be created")
                }
            }
            result.fold(
                onSuccess = {
                    val entry = store.remember(uri)?.file
                    recents = store.load()
                    if (entry != null) {
                        createdFileUri = entry.uri
                        openFileUri = entry.uri
                    } else {
                        statusMessage = "Couldn't read the new presentation."
                    }
                },
                onFailure = { error ->
                    statusMessage = "Couldn't create the presentation: " +
                        (error.message ?: error.javaClass.simpleName)
                },
            )
        }
    }

    val createDocument = rememberDocumentCreator(onCreated = { uri -> createNewDocument(uri) })
    val createSpreadsheet = rememberDocumentCreator(
        mimeType = XLSX_MIME_TYPE,
        onCreated = ::createNewSpreadsheet,
    )
    val createPresentation = rememberDocumentCreator(
        mimeType = PPTX_MIME_TYPE,
        onCreated = ::createNewPresentation,
    )

    /** Template id chosen before the SAF picker returns; consumed once. */
    var pendingTemplateId by remember { mutableStateOf<String?>(null) }
    val createFromTemplate = rememberDocumentCreator(
        onCreated = { uri ->
            val template = pendingTemplateId?.let(::documentTemplate)
            pendingTemplateId = null
            createNewDocument(uri, template?.document ?: Document(blocks = listOf(Paragraph())))
        },
    )

    LaunchedEffect(statusMessage) {
        val message = statusMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        statusMessage = null
    }

    if (showAbout) {
        BackHandler { showAbout = false }
        AboutScreen(onBack = { showAbout = false })
        return
    }

    val openFile = openFileUri?.let { uri ->
        recents.firstOrNull { it.uri == uri }
            ?: transientFile?.takeIf { it.uri == uri }
    }

    /** Leaves the editor; a transient (share-intent) file is dropped with it. */
    fun closeFile() {
        openFileUri = null
        createdFileUri = null
        transientFile = null
    }

    // Filling a form is a screen of its own: the pages are drawn with the
    // fields on them, which the workspace above does not show.
    val formFile = fillFormUri?.let { uri ->
        recents.firstOrNull { it.uri == uri } ?: transientFile?.takeIf { it.uri == uri }
    }
    if (formFile != null) {
        PdfFormScreen(
            file = formFile,
            onBack = { fillFormUri = null },
        )
        return
    }

    if (openFile != null) {
        when (openFile.kind) {
            DocumentKind.SPREADSHEET -> SheetScreen(
                file = openFile,
                initialEditing = openFile.uri == createdFileUri,
                onBack = { closeFile() },
                onSaveAs = { entry ->
                    recents = store.load()
                    openFileUri = entry.uri
                    createdFileUri = null
                    // The copy is a real, reopenable recents entry now.
                    transientFile = null
                },
            )
            DocumentKind.DOCUMENT -> EditorScreen(
                file = openFile,
                initialEditing = openFile.uri == createdFileUri,
                onBack = { closeFile() },
                onSaveAs = { entry ->
                    recents = store.load()
                    openFileUri = entry.uri
                    createdFileUri = null
                    // The copy is a real, reopenable recents entry now.
                    transientFile = null
                },
            )
            DocumentKind.PDF -> PdfScreen(
                file = openFile,
                onBack = { closeFile() },
                onOpenFile = openPicker,
                onFillForm = { fillFormUri = openFile.uri },
            )
            DocumentKind.PRESENTATION -> PresentationScreen(
                file = openFile,
                onBack = { closeFile() },
            )
            else -> UnsupportedScreen(
                file = openFile,
                onBack = { closeFile() },
                onOpenFile = openPicker,
            )
        }
        return
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar {
                ZiyTab.entries.forEach { entry ->
                    NavigationBarItem(
                        selected = entry == tab,
                        onClick = { tabName = entry.name },
                        icon = { Icon(tabIcon(entry), contentDescription = entry.label) },
                        label = { Text(entry.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when (tab) {
                ZiyTab.HOME -> HomeScreen(
                    recents = recents,
                    onOpenFile = openPicker,
                    onOpenPdf = openPdfPicker,
                    onOpenRecent = { file -> openFileUri = file.uri },
                    onCreateDocument = { createDocument("Document.docx") },
                    onCreateSpreadsheet = { createSpreadsheet("Spreadsheet.xlsx") },
                    onCreatePresentation = { createPresentation("Presentation.pptx") },
                    onCreateTemplate = { templateId ->
                        pendingTemplateId = templateId
                        createFromTemplate(documentTemplate(templateId).name + ".docx")
                    },
                    onShowAllFiles = { tabName = ZiyTab.FILES.name },
                )
                ZiyTab.FILES -> FilesScreen(
                    recents = recents,
                    onOpenFile = openPicker,
                    onOpenRecent = { file -> openFileUri = file.uri },
                )
                ZiyTab.SETTINGS -> SettingsScreen(onOpenAbout = { showAbout = true })
            }
        }
    }
}

private fun tabIcon(tab: ZiyTab): ImageVector = when (tab) {
    ZiyTab.HOME -> Icons.Filled.Home
    ZiyTab.FILES -> Icons.Filled.Folder
    ZiyTab.SETTINGS -> Icons.Filled.Settings
}
