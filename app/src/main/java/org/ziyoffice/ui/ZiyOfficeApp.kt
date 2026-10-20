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
 * Top-level app shell: a three-tab bottom navigation (Home / Files / Settings),
 * the layout users already know from commercial office apps — minus the accounts,
 * the cloud promos, the ads and the premium nags.
 *
 * About is still a pushed screen with a back button.
 */
@Composable
fun ZiyOfficeApp() {
    val context = LocalContext.current
    val store = remember { RecentFilesStore(context) }
    val scope = rememberCoroutineScope()
    var recents by remember { mutableStateOf(store.load()) }
    var tabName by rememberSaveable { mutableStateOf(ZiyTab.HOME.name) }
    var showAbout by rememberSaveable { mutableStateOf(false) }
    var openFileUri by rememberSaveable { mutableStateOf<String?>(null) }
    var createdFileUri by rememberSaveable { mutableStateOf<String?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val tab = runCatching { ZiyTab.valueOf(tabName) }.getOrDefault(ZiyTab.HOME)

    val openPicker = rememberDocumentPicker { uri: Uri ->
        val entry = store.remember(uri)
        recents = store.load()
        if (entry != null) {
            openFileUri = entry.uri
        } else {
            statusMessage = "Could not read that file."
        }
    }

    // Home's PDF tile opens a PDF straight away rather than pretending the app
    // can create a blank one: the picker is narrowed to application/pdf.
    val openPdfPicker = rememberDocumentPicker(PDF_MIME_TYPES) { uri: Uri ->
        val entry = store.remember(uri)
        recents = store.load()
        if (entry != null) {
            openFileUri = entry.uri
        } else {
            statusMessage = "Could not read that PDF."
        }
    }

    /** Writes a fresh package into the file the user created through SAF. */
    fun createNewDocument(uri: Uri, document: Document = Document(blocks = listOf(Paragraph()))) {
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = DocxWriter().write(document)
                    context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) }
                        ?: error("the file could not be created")
                }
            }
            result.fold(
                onSuccess = {
                    val entry = store.remember(uri)
                    recents = store.load()
                    if (entry != null) {
                        createdFileUri = entry.uri
                        openFileUri = entry.uri
                    } else {
                        statusMessage = "Could not read the new document."
                    }
                },
                onFailure = { error ->
                    statusMessage = "Could not create the document: " +
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
                        ?: error("the file could not be created")
                }
            }
            result.fold(
                onSuccess = {
                    val entry = store.remember(uri)
                    recents = store.load()
                    if (entry != null) {
                        createdFileUri = entry.uri
                        openFileUri = entry.uri
                    } else {
                        statusMessage = "Could not read the new spreadsheet."
                    }
                },
                onFailure = { error ->
                    statusMessage = "Could not create the spreadsheet: " +
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
                        ?: error("the file could not be created")
                }
            }
            result.fold(
                onSuccess = {
                    val entry = store.remember(uri)
                    recents = store.load()
                    if (entry != null) {
                        createdFileUri = entry.uri
                        openFileUri = entry.uri
                    } else {
                        statusMessage = "Could not read the new presentation."
                    }
                },
                onFailure = { error ->
                    statusMessage = "Could not create the presentation: " +
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

    val openFile = openFileUri?.let { uri -> recents.firstOrNull { it.uri == uri } }
    if (openFile != null) {
        when (openFile.kind) {
            DocumentKind.SPREADSHEET -> SheetScreen(
                file = openFile,
                initialEditing = openFile.uri == createdFileUri,
                onBack = {
                    openFileUri = null
                    createdFileUri = null
                },
                onSaveAs = { entry ->
                    recents = store.load()
                    openFileUri = entry.uri
                    createdFileUri = null
                },
            )
            DocumentKind.DOCUMENT -> EditorScreen(
                file = openFile,
                initialEditing = openFile.uri == createdFileUri,
                onBack = {
                    openFileUri = null
                    createdFileUri = null
                },
                onSaveAs = { entry ->
                    recents = store.load()
                    openFileUri = entry.uri
                    createdFileUri = null
                },
            )
            DocumentKind.PDF -> PdfScreen(
                file = openFile,
                onBack = {
                    openFileUri = null
                    createdFileUri = null
                },
                onOpenFile = openPicker,
            )
            DocumentKind.PRESENTATION -> PresentationScreen(
                file = openFile,
                onBack = {
                    openFileUri = null
                    createdFileUri = null
                },
            )
            else -> UnsupportedScreen(
                file = openFile,
                onBack = {
                    openFileUri = null
                    createdFileUri = null
                },
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
