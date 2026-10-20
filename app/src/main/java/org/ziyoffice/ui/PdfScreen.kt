package org.ziyoffice.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.io.File
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ziyoffice.export.Printer
import org.ziyoffice.model.RecentFile
import org.ziyoffice.pdf.PageRange
import org.ziyoffice.pdf.PdfDocument
import org.ziyoffice.pdf.PdfRewriter

/** Every PDF starts with this magic; an output without it is not a PDF. */
private const val PDF_HEADER_BYTES = 5

/**
 * Thrown from inside the blocking page loop when the user taps Cancel. It is a
 * dedicated type rather than [kotlinx.coroutines.CancellationException] because
 * runCatching would swallow the latter and report it as a save failure.
 */
private class ExportCancelled : Exception("export cancelled")

/** Identifies one source page of one opened document. */
private data class PageKey(val doc: Int, val source: Int)

/** One page as shown in the viewer: where it comes from and its rendered bitmap. */
private data class PdfPage(val key: PageKey, val bitmap: Bitmap?)

/**
 * PDF workspace: renders pages with the platform renderer and lets the user
 * annotate (highlight, ink, notes, signature, redaction), reorganise (reorder,
 * rotate, delete), merge in pages from another PDF and save the result.
 *
 * Saving re-renders pages and writes a fresh file, which is the trade that makes
 * redaction real: removed content is gone from the bytes, not covered by a box.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfScreen(
    file: RecentFile,
    onBack: () -> Unit,
    onOpenFile: () -> Unit,
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val pageWidthPx = remember(configuration.screenWidthDp) {
        (configuration.screenWidthDp * configuration.densityDpi / 160f * 0.94f).toInt()
    }

    var documents by remember(file.uri) { mutableStateOf<List<PdfDocument>>(emptyList()) }
    var pages by remember(file.uri) { mutableStateOf<List<PdfPage>>(emptyList()) }
    var failure by remember(file.uri) { mutableStateOf<String?>(null) }
    var loading by remember(file.uri) { mutableStateOf(true) }
    var rotations by remember(file.uri) { mutableStateOf<Map<PageKey, Int>>(emptyMap()) }
    var deleted by remember(file.uri) { mutableStateOf<Set<PageKey>>(emptySet()) }
    var selectedPage by remember(file.uri) { mutableStateOf(0) }
    var marks by remember(file.uri) { mutableStateOf<List<PdfMark>>(emptyList()) }
    var markMode by remember(file.uri) { mutableStateOf(MarkMode.HIGHLIGHT) }
    var noteTarget by remember { mutableStateOf<Triple<PageKey, Float, Float>?>(null) }
    var noteText by remember { mutableStateOf("") }
    /** Where a text stamp goes, and what it says. */
    var textTarget by remember { mutableStateOf<Triple<PageKey, Float, Float>?>(null) }
    var textValue by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var exportProgress by remember(file.uri) { mutableStateOf<Float?>(null) }
    /** null = idle; set for the lifetime of one export so it can be cancelled. */
    var exportJob by remember { mutableStateOf<Job?>(null) }
    /** Pages in the running export, for the progress label; null when idle. */
    var exportPageTotal by remember(file.uri) { mutableStateOf<Int?>(null) }
    /** Set by Cancel; the blocking page loop checks it between pages. */
    var exportCancelled by remember { mutableStateOf(false) }
    var showMarks by remember { mutableStateOf(false) }
    var showSplit by remember { mutableStateOf(false) }
    var moreMenuOpen by remember { mutableStateOf(false) }
    /** Stamps "3 / 12" on every page of a saved or printed copy. */
    var stampPageNumbers by remember(file.uri) { mutableStateOf(false) }
    var splitRange by remember { mutableStateOf("") }
    /** Pages chosen by [splitRange]; null until the user starts a split. */
    var splitPages by remember { mutableStateOf<List<Int>>(emptyList()) }

    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf"),
    ) { uri ->
        if (uri == null || documents.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            exportJob = coroutineContext.job
            exportCancelled = false
            saving = true
            exportProgress = 0f
            try {
                val order = withContext(Dispatchers.IO) {
                    pages
                        .filterNot { it.key in deleted }
                        .map { page ->
                            PdfRewriter.PageRef(
                                documentIndex = page.key.doc,
                                sourceIndex = page.key.source,
                                rotateDegrees = rotations[page.key] ?: 0,
                            )
                        }
                }
                exportPageTotal = order.size
                val destination = destinationName(context, uri)
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        val wrote = context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                            PdfDocument.exportTo(
                                out,
                                documents,
                                order,
                                marks,
                                pageNumbers = stampPageNumbers,
                                onProgress = { done, total ->
                                    // Snapshot state is thread-safe, so the blocking
                                    // page loop can report progress straight through.
                                    exportProgress = if (total == 0) 1f else done.toFloat() / total
                                    if (exportCancelled) throw ExportCancelled()
                                },
                            )
                            true
                        } ?: error("the file could not be written")
                        if (!wrote) error("nothing was written to $destination")
                        // A write that merely did not throw can still leave nothing
                        // behind — a full disk, a revoked grant or an aborted stream
                        // all end here silently — so the destination is re-opened and
                        // checked before any success is claimed.
                        val size = context.contentResolver.openFileDescriptor(uri, "r")
                            ?.use { it.statSize }
                            ?: error("the saved file could not be read back")
                        if (size <= 0L) {
                            error("the destination is empty — nothing was written to $destination")
                        }
                        val header = context.contentResolver.openInputStream(uri)?.use { input ->
                            ByteArray(PDF_HEADER_BYTES).also { buffer ->
                                if (input.read(buffer) <= 0) {
                                    error("the saved file could not be read back")
                                }
                            }
                        } ?: error("the saved file could not be read back")
                        if (!String(header, Charsets.ISO_8859_1).startsWith("%PDF")) {
                            error("$destination is not a readable PDF")
                        }
                    }
                }
                // A cancelled export writes nothing worth reporting as a success or
                // an error: the destination is left exactly as the picker made it.
                if (!isActive) return@launch
                if (result.exceptionOrNull() is ExportCancelled) {
                    snackbarHostState.showSnackbar("Export cancelled \u00B7 no file was written")
                    return@launch
                }
                result.fold(
                    onSuccess = {
                        val noteWarning = if (marks.any { it is PdfMark.Note }) {
                            " · Notes were not included — the PDF pages carry " +
                                "highlights, ink and redactions only"
                        } else {
                            ""
                        }
                        snackbarHostState.showSnackbar("Saved $destination$noteWarning")
                    },
                    onFailure = {
                        snackbarHostState.showSnackbar(
                            "Could not save: ${it.message ?: "unknown error"}",
                        )
                    },
                )
            } finally {
                saving = false
                exportProgress = null
                exportPageTotal = null
                exportJob = null
            }
        }
    }

    /**
     * Prints exactly what "Save a copy" would write: the current page order with
     * rotations applied, deleted pages gone and marks baked in. Rendering goes
     * through the same [PdfDocument.exportTo] path, so the printed pages cannot
     * disagree with the exported file.
     */
    fun printPdf() {
        if (documents.isEmpty()) return
        scope.launch {
            val order = pages
                .filterNot { it.key in deleted }
                .map { page ->
                    PdfRewriter.PageRef(
                        documentIndex = page.key.doc,
                        sourceIndex = page.key.source,
                        rotateDegrees = rotations[page.key] ?: 0,
                    )
                }
            val bytes = withContext(Dispatchers.IO) {
                runCatching {
                    val buffer = java.io.ByteArrayOutputStream()
                    PdfDocument.exportTo(
                        buffer,
                        documents,
                        order,
                        marks,
                        pageNumbers = stampPageNumbers,
                    )
                    buffer.toByteArray()
                }.getOrNull()
            }
            if (!isActive) return@launch
            if (bytes == null || bytes.isEmpty()) {
                snackbarHostState.showSnackbar("Could not prepare the PDF for printing")
                return@launch
            }
            val name = file.displayName.substringBeforeLast('.', file.displayName)
            if (!Printer.print(context, name, bytes)) {
                snackbarHostState.showSnackbar("This device has no print service")
            }
        }
    }

    /**
     * Writes the pages chosen by Split into a new file, in document order with
     * rotations applied and marks baked in — the same rendering path as Save, so
     * an extracted page cannot look different from the same page in the original.
     */
    val splitLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf"),
    ) { uri ->
        if (uri == null || splitPages.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            saving = true
            val chosen = splitPages
            val order = pages
                .filterIndexed { index, _ -> (index + 1) in chosen }
                .map { page ->
                    PdfRewriter.PageRef(
                        documentIndex = page.key.doc,
                        sourceIndex = page.key.source,
                        rotateDegrees = rotations[page.key] ?: 0,
                    )
                }
            val destination = destinationName(context, uri)
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                        PdfDocument.exportTo(out, documents, order, marks)
                    } ?: error("the file could not be written")
                    // Same honesty rule as Save: a write that did not throw can
                    // still leave nothing behind, so the file is read back.
                    val size = context.contentResolver.openFileDescriptor(uri, "r")
                        ?.use { it.statSize }
                        ?: error("the saved file could not be read back")
                    if (size <= 0L) error("nothing was written to $destination")
                    size
                }
            }
            saving = false
            if (!isActive) return@launch
            result.fold(
                onSuccess = { size ->
                    snackbarHostState.showSnackbar(
                        "Split ${order.size} page(s) to $destination \u00B7 $size bytes",
                    )
                },
                onFailure = {
                    snackbarHostState.showSnackbar(
                        "Could not split: ${it.message ?: "unknown error"}",
                    )
                },
            )
        }
    }

    val mergeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val input = context.contentResolver.openInputStream(uri)
                        ?: error("the file could not be opened")
                    val cache = File(context.cacheDir, "pdf-merge-${uri.hashCode()}.pdf")
                    input.use { PdfDocument.open(it, cache) } ?: error("this PDF could not be parsed")
                }
            }
            result.fold(
                onSuccess = { opened ->
                    val docIndex = documents.size
                    val added = withContext(Dispatchers.IO) {
                        (0 until opened.pageCount).map { index ->
                            PdfPage(PageKey(docIndex, index), opened.renderPage(index, pageWidthPx))
                        }
                    }
                    documents = documents + opened
                    pages = pages + added
                    snackbarHostState.showSnackbar(
                        "Added ${added.size} page(s) · ${pages.size} in total",
                    )
                },
                onFailure = {
                    snackbarHostState.showSnackbar("Could not add that PDF: ${it.message ?: "unreadable"}")
                },
            )
        }
    }

    LaunchedEffect(file.uri) {
        // Drop the previous file's renderers before opening a new one.
        documents.forEach { runCatching { it.close() } }
        loading = true
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val input = context.contentResolver.openInputStream(Uri.parse(file.uri))
                    ?: error("the file could not be opened")
                val cache = File(context.cacheDir, "pdf-${file.uri.hashCode()}.pdf")
                input.use { PdfDocument.open(it, cache) } ?: error("this PDF could not be parsed")
            }
        }
        result.fold(
            onSuccess = { opened ->
                val rendered = withContext(Dispatchers.IO) {
                    (0 until opened.pageCount).map { index ->
                        PdfPage(PageKey(0, index), opened.renderPage(index, pageWidthPx))
                    }
                }
                documents = listOf(opened)
                pages = rendered
                failure = null
            },
            onFailure = { error ->
                failure = error.message ?: error.javaClass.simpleName
            },
        )
        loading = false
    }

    // Hold a ref to the latest document list so it can be closed exactly once
    // when this screen leaves composition — not on every change to the list,
    // which would close renderers that merged/changed screens still need.
    val documentsRef = remember { mutableStateOf(documents) }
    documentsRef.value = documents
    DisposableEffect(Unit) {
        onDispose { documentsRef.value.forEach { runCatching { it.close() } } }
    }

    /** Moves the selected page by [delta] positions, following it with the selection. */
    fun moveSelected(delta: Int) {
        val from = selectedPage
        val to = from + delta
        if (from !in pages.indices || to !in pages.indices) return
        val updated = pages.toMutableList()
        updated.add(to, updated.removeAt(from))
        pages = updated
        selectedPage = to
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Column {
                        Text(
                            file.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            when {
                                loading -> "Opening\u2026"
                                failure != null -> "Could not open"
                                else -> "${pages.size} page(s) · ${pages.size - deleted.size} on export"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    if (documents.isNotEmpty() && !loading) {
                        // Save and Print stay visible: they are what people open a
                        // PDF to do. The rest move into one menu, because five
                        // icons plus the back arrow leave the title clipped to
                        // "Budg…" on a 360 dp phone.
                        IconButton(
                            onClick = { saveLauncher.launch(file.displayName) },
                            enabled = !saving && pages.size > deleted.size,
                        ) {
                            Icon(Icons.Filled.Save, contentDescription = "Save a copy")
                        }
                        IconButton(
                            onClick = ::printPdf,
                            enabled = !saving && pages.size > deleted.size,
                        ) {
                            Icon(Icons.Filled.Print, contentDescription = "Print")
                        }
                        Box {
                            IconButton(onClick = { moreMenuOpen = true }) {
                                Icon(
                                    Icons.Filled.MoreVert,
                                    contentDescription = "More actions",
                                )
                            }
                            DropdownMenu(
                                expanded = moreMenuOpen,
                                onDismissRequest = { moreMenuOpen = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Add pages from another PDF") },
                                    onClick = {
                                        moreMenuOpen = false
                                        mergeLauncher.launch(arrayOf("application/pdf"))
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Split pages") },
                                    onClick = {
                                        moreMenuOpen = false
                                        // Pre-fill with the selected page: the most
                                        // common split is "just this one".
                                        splitRange = (selectedPage + 1).toString()
                                        showSplit = true
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Page numbers") },
                                    leadingIcon = {
                                        if (stampPageNumbers) {
                                            Icon(Icons.Filled.Check, contentDescription = null)
                                        }
                                    },
                                    onClick = {
                                        moreMenuOpen = false
                                        stampPageNumbers = !stampPageNumbers
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Marks (${marks.size})") },
                                    enabled = marks.isNotEmpty(),
                                    onClick = {
                                        moreMenuOpen = false
                                        showMarks = true
                                    },
                                )
                            }
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when {
                loading -> Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(0.6f))
                    Text(
                        "Rendering pages\u2026",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }

                saving -> ExportProgressDialog(
                    progress = exportProgress,
                    totalPages = exportPageTotal,
                    onCancel = {
                        // The page loop runs on a blocking thread and cannot see
                        // coroutine cancellation, so it is told to stop between pages.
                        exportCancelled = true
                        exportJob?.cancel()
                    },
                )

                failure != null -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("This PDF could not be opened.", style = MaterialTheme.typography.titleMedium)
                    Text(
                        ReadFailureCopy.describe(failure),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FilledTonalButton(onClick = onOpenFile) {
                        Text("Open a different file")
                    }
                }

                else -> Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        MarkMode.entries.forEach { mode ->
                            FilterChip(
                                selected = markMode == mode,
                                onClick = { markMode = mode },
                                label = { Text(mode.label) },
                            )
                        }
                    }
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        // Lazy item keys must be Bundle-storable on Android, so the
                        // compound page key is encoded as "doc:source" (both stable ints).
                        itemsIndexed(pages, key = { _, page -> "${page.key.doc}:${page.key.source}" }) { index, page ->
                            val isDeleted = page.key in deleted
                            val isSelected = index == selectedPage
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        if (isSelected) {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.surfaceContainerLow
                                        },
                                    ),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                val pageState = buildString {
                                    append("Page ${index + 1}")
                                    if (isSelected) append(" · selected")
                                    if (isDeleted) append(" · removed on export")
                                    else if (isSelected) {
                                        val rotation = rotations[page.key] ?: 0
                                        if (rotation != 0) append(" · $rotation°")
                                    }
                                }
                                Text(
                                    pageState,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .semantics {
                                            contentDescription = pageState
                                            selected = isSelected
                                            role = Role.Button
                                        }
                                        .clickable { selectedPage = index }
                                        .padding(vertical = 4.dp),
                                )
                                if (isSelected) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = { moveSelected(-1) },
                                            enabled = index > 0,
                                            modifier = Modifier.size(48.dp),
                                        ) {
                                            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move page up")
                                        }
                                        IconButton(
                                            onClick = { moveSelected(1) },
                                            enabled = index < pages.lastIndex,
                                            modifier = Modifier.size(48.dp),
                                        ) {
                                            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move page down")
                                        }
                                        IconButton(
                                            onClick = {
                                                rotations = rotations +
                                                    (page.key to ((rotations[page.key] ?: 0) + 90) % 360)
                                                // Marks ride along with the content:
                                                // their fractions are re-expressed for
                                                // the new orientation.
                                                marks = rotateMarks(marks, page.key.doc, page.key.source, 90)
                                                val rotated = rotateBitmap(page.bitmap, 90f)
                                                pages = pages.toMutableList().also {
                                                    it[index] = page.copy(bitmap = rotated)
                                                }
                                            },
                                            modifier = Modifier.size(48.dp),
                                        ) {
                                            Icon(Icons.AutoMirrored.Filled.RotateRight, contentDescription = "Rotate page")
                                        }
                                        IconButton(
                                            onClick = {
                                                deleted = if (isDeleted) {
                                                    deleted - page.key
                                                } else {
                                                    deleted + page.key
                                                }
                                            },
                                            enabled = isDeleted || pages.size - deleted.size > 1,
                                            modifier = Modifier.size(48.dp),
                                        ) {
                                            Icon(
                                                if (isDeleted) Icons.Filled.RestoreFromTrash else Icons.Filled.Delete,
                                                contentDescription = if (isDeleted) "Keep page" else "Remove page",
                                            )
                                        }
                                    }
                                }
                                if (page.bitmap != null) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(page.bitmap.width.toFloat() / page.bitmap.height.toFloat()),
                                    ) {
                                        Image(
                                            bitmap = page.bitmap.asImageBitmap(),
                                            contentDescription = "Page ${index + 1}",
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .then(
                                                    if (isDeleted) {
                                                        Modifier.alpha(0.35f)
                                                    } else {
                                                        Modifier
                                                    },
                                                ),
                                        )
                                        MarkLayer(
                                            doc = page.key.doc,
                                            page = page.key.source,
                                            marks = marks,
                                            mode = markMode,
                                            enabled = !isDeleted && isSelected,
                                            onAdd = { marks = marks + it },
                                            onNoteAt = { x, y ->
                                                noteTarget = Triple(page.key, x, y)
                                                noteText = ""
                                            },
                                            onTextAt = { x, y ->
                                                textTarget = Triple(page.key, x, y)
                                                textValue = ""
                                            },
                                        )
                                    }
                                }
                            }
                        }
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                Text(
                                    "Tap a page to select it. Drag to ink or sign; tap to highlight. " +
                                        "Rotate, reorder or remove it from its control row.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Every mark is listed with a visible page number and a remove action. A
    // mark made by drag has no other accessible handle: TalkBack cannot drag, and
    // a highlight or redaction is otherwise impossible to find or undo.
    if (showSplit) {
        val available = pages.size
        // Parsed on every keystroke: the dialog must be able to say "no" while
        // the user types, instead of failing after they tap Split.
        val parsed = PageRange.parse(splitRange, available)
        AlertDialog(
            onDismissRequest = { showSplit = false },
            title = { Text("Split pages") },
            text = {
                Column {
                    Text(
                        "Write the pages you choose into a new PDF. " +
                            "This document has $available page(s).",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedTextField(
                        value = splitRange,
                        onValueChange = { splitRange = it },
                        label = { Text("Pages, e.g. 1-3, 7") },
                        isError = parsed == null && splitRange.isNotBlank(),
                        supportingText = {
                            Text(
                                when {
                                    parsed != null ->
                                        "${parsed.size} page(s): ${PageRange.describe(parsed)}"
                                    splitRange.isBlank() -> "Enter at least one page"
                                    else -> "Not a valid page range for this document"
                                },
                            )
                        },
                        singleLine = true,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (parsed != null) {
                            splitPages = parsed
                            showSplit = false
                            val base = file.displayName.substringBeforeLast('.', file.displayName)
                            splitLauncher.launch("$base (${PageRange.describe(parsed)}).pdf")
                        }
                    },
                    enabled = parsed != null && !saving,
                ) { Text("Split") }
            },
            dismissButton = {
                TextButton(onClick = { showSplit = false }) { Text("Cancel") }
            },
        )
    }

    if (showMarks) {
        AlertDialog(
            onDismissRequest = { showMarks = false },
            title = { Text("Marks (${marks.size})") },
            text = {
                if (marks.isEmpty()) {
                    Text("No marks on this document yet.")
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        marks.forEachIndexed { index, mark ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "${markLabel(mark)} \u00B7 page ${mark.page + 1}",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f),
                                )
                                IconButton(
                                    onClick = { marks = marks.filterIndexed { i, _ -> i != index } },
                                    modifier = Modifier.size(48.dp),
                                ) {
                                    Icon(
                                        Icons.Filled.Delete,
                                        contentDescription = "Remove ${markLabel(mark)} on page ${mark.page + 1}",
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showMarks = false }) { Text("Close") }
            },
        )
    }

    val target = noteTarget
    if (target != null) {
        AlertDialog(
            onDismissRequest = { noteTarget = null },
            title = { Text("Add a note") },
            text = {
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text("Note") },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val (key, x, y) = target
                        if (noteText.isNotBlank()) {
                            marks = marks + PdfMark.Note(key.doc, key.source, x, y, noteText)
                        }
                        noteTarget = null
                    },
                ) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { noteTarget = null }) { Text("Cancel") }
            },
        )
    }
    val stampTarget = textTarget
    if (stampTarget != null) {
        AlertDialog(
            onDismissRequest = { textTarget = null },
            title = { Text("Stamp text on the page") },
            text = {
                OutlinedTextField(
                    value = textValue,
                    onValueChange = { textValue = it },
                    label = { Text("Text") },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val (key, x, y) = stampTarget
                        if (textValue.isNotBlank()) {
                            marks = marks + PdfMark.Text(key.doc, key.source, x, y, textValue)
                        }
                        textTarget = null
                    },
                ) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { textTarget = null }) { Text("Cancel") }
            },
        )
    }
}

/** A short human name for a mark, used in the mark list and its remove action. */
private fun markLabel(mark: PdfMark): String = when (mark) {
    is PdfMark.Highlight -> "Highlight"
    is PdfMark.Ink -> if (mark.isSignature) "Signature" else "Ink stroke"
    is PdfMark.Note -> "Note \u201C${mark.text.take(40)}\u201D"
    is PdfMark.Text -> "Text \u201C${mark.text.take(40)}\u201D"
    is PdfMark.Redaction -> "Redaction"
}

/**
 * A display name for a destination the user just picked in the file picker. The
 * source file's name is wrong here — the user chose a new file — so it is read
 * from the uri itself, falling back to the last path segment when the provider
 * will not say.
 */
private fun destinationName(context: Context, uri: Uri): String {
    val fromProvider = runCatching {
        context.contentResolver.query(
            uri,
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            val column = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            if (column >= 0 && cursor.moveToFirst()) cursor.getString(column) else null
        }
    }.getOrNull()
    return fromProvider ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotEmpty() }
        ?: "the chosen location"
}

/**
 * Blocks the screen while a copy is written and lets the user stop it. [progress]
 * is 0..1 when pages are reported, or null for the indeterminate case where the
 * export cannot say how far it has got.
 */
@Composable
private fun ExportProgressDialog(
    progress: Float?,
    totalPages: Int?,
    onCancel: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f)),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            tonalElevation = 6.dp,
            modifier = Modifier.padding(32.dp),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("Saving a copy\u2026", style = MaterialTheme.typography.titleMedium)
                Text(
                    when {
                        progress != null -> "${(progress * 100).toInt()}% of $totalPages page(s)"
                        else -> "Saving ${totalPages ?: 0} page(s)\u2026"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (progress != null) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth(0.7f),
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(0.7f))
                }
                TextButton(onClick = onCancel) { Text("Cancel") }
            }
        }
    }
}

/** Rotates a rendered page bitmap clockwise, for on-screen feedback while editing. */
private fun rotateBitmap(bitmap: Bitmap?, degrees: Float): Bitmap? {
    if (bitmap == null) return null
    val matrix = Matrix().apply { postRotate(degrees) }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}
