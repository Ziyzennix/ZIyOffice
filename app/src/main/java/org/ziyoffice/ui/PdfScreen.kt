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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
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

/** Every PDF starts with this header, so an output without it is not a PDF. */
private const val PDF_HEADER_BYTES = 5

/**
 * Thrown from the blocking page loop when the user taps Cancel. Its own type,
 * because runCatching would swallow a CancellationException.
 */
private class ExportCancelled : Exception("export cancelled")

private data class PageKey(val doc: Int, val source: Int)

private data class PdfPage(
    val key: PageKey,
    val widthPt: Int = 595,
    val heightPt: Int = 842,
)

private class PageBitmapCache(private val maxEntries: Int = 6) {
    private val cache = object : LinkedHashMap<Pair<PageKey, Int>, Bitmap>(maxEntries, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Pair<PageKey, Int>, Bitmap>?): Boolean {
            if (size > maxEntries) {
                eldest?.value?.recycle()
                return true
            }
            return false
        }
    }

    @Synchronized
    fun get(key: PageKey, rotation: Int): Bitmap? = cache[key to rotation]?.takeUnless { it.isRecycled }

    @Synchronized
    fun put(key: PageKey, rotation: Int, bitmap: Bitmap) {
        val old = cache.put(key to rotation, bitmap)
        if (old != null && old != bitmap) {
            old.recycle()
        }
    }

    @Synchronized
    fun clear() {
        cache.values.forEach { if (!it.isRecycled) it.recycle() }
        cache.clear()
    }
}
/**
 * PDF workspace: renders pages with the platform renderer and lets the user
 * annotate, reorganise, merge pages in and save. Saving re-renders and writes a
 * fresh file, which is what makes redaction real: removed content is gone from
 * the bytes, not covered by a box.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfScreen(
    file: RecentFile,
    onBack: () -> Unit,
    onOpenFile: () -> Unit,
    onFillForm: () -> Unit = {},
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
    var textTarget by remember { mutableStateOf<Triple<PageKey, Float, Float>?>(null) }
    var textValue by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var exportProgress by remember(file.uri) { mutableStateOf<Float?>(null) }
    var exportPageTotal by remember(file.uri) { mutableStateOf<Int?>(null) }
    /** Set by Cancel; the blocking page loop checks it between pages. */
    var exportCancelled by remember { mutableStateOf(false) }
    var showMarks by remember { mutableStateOf(false) }
    var showSplit by remember { mutableStateOf(false) }
    var moreMenuOpen by remember { mutableStateOf(false) }
    /** Stamps "3 / 12" on every page of a saved or printed copy. */
    var stampPageNumbers by remember(file.uri) { mutableStateOf(false) }
    var splitRange by remember { mutableStateOf("") }
    var splitPages by remember { mutableStateOf<List<Int>>(emptyList()) }
    /** True when the file has fields to fill in. */
    var hasForm by remember(file.uri) { mutableStateOf(false) }

    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf"),
    ) { uri ->
        if (uri == null || documents.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
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
                                    // Snapshot state is thread-safe, so the blocking page
                                    // loop can report progress straight through.
                                    exportProgress = if (total == 0) 1f else done.toFloat() / total
                                    if (exportCancelled) throw ExportCancelled()
                                },
                            )
                            true
                        } ?: error("the file couldn't be written")
                        if (!wrote) error("nothing was written to $destination")
                        // A write that did not throw can still leave nothing
                        // behind (a full disk, a revoked grant, an aborted
                        // stream), so the destination is read back before success
                        // is claimed.
                        val size = context.contentResolver.openFileDescriptor(uri, "r")
                            ?.use { it.statSize }
                            ?: error("the saved file couldn't be read back")
                        if (size <= 0L) {
                            error("$destination is empty, nothing was written")
                        }
                        val header = context.contentResolver.openInputStream(uri)?.use { input ->
                            ByteArray(PDF_HEADER_BYTES).also { buffer ->
                                if (input.read(buffer) <= 0) {
                                    error("the saved file couldn't be read back")
                                }
                            }
                        } ?: error("the saved file couldn't be read back")
                        if (!String(header, Charsets.ISO_8859_1).startsWith("%PDF")) {
                            error("$destination isn't a readable PDF")
                        }
                    }
                }
                // A cancelled export writes nothing worth reporting, so the
                // destination is left exactly as the picker made it.
                if (!isActive) return@launch
                if (result.exceptionOrNull() is ExportCancelled) {
                    snackbarHostState.showSnackbar("Export cancelled, nothing was written")
                    return@launch
                }
                result.fold(
                    onSuccess = {
                        val noteWarning = if (marks.any { it is PdfMark.Note }) {
                            " · notes weren't included, the pages carry " +
                                "highlights, ink and redactions only"
                        } else {
                            ""
                        }
                        snackbarHostState.showSnackbar("Saved $destination$noteWarning")
                    },
                    onFailure = {
                        snackbarHostState.showSnackbar(
                            "Couldn't save that: ${it.message ?: "unknown error"}",
                        )
                    },
                )
            } finally {
                saving = false
                exportProgress = null
                exportPageTotal = null
            }
        }
    }

    /**
     * Prints exactly what "Save a copy" would write: the current page order with
     * rotations applied, deleted pages gone and marks baked in. Same
     * [PdfDocument.exportTo] path, so printed and exported pages cannot differ.
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
                snackbarHostState.showSnackbar("Couldn't prepare the PDF for printing")
                return@launch
            }
            val name = file.displayName.substringBeforeLast('.', file.displayName)
            if (!Printer.print(context, name, bytes)) {
                snackbarHostState.showSnackbar("No print service on this device")
            }
        }
    }

    /**
     * Writes the pages chosen by Split into a new file, in document order with
     * rotations applied and marks baked in. Same path as Save, so an extracted
     * page looks the same as the one it came from.
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
                        PdfDocument.exportTo(out, documents, order, marks, pageNumbers = stampPageNumbers)
                    } ?: error("the file couldn't be written")
                    // Same rule as Save: a write that did not throw can still
                    // leave nothing behind, so the file is read back.
                    val size = context.contentResolver.openFileDescriptor(uri, "r")
                        ?.use { it.statSize }
                        ?: error("the saved file couldn't be read back")
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
                        "Couldn't split that: ${it.message ?: "unknown error"}",
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
                        ?: error("the file couldn't be opened")
                    val cache = File(context.cacheDir, "pdf-merge-${uri.hashCode()}.pdf")
                    input.use { PdfDocument.open(it, cache) } ?: error("this PDF couldn't be parsed")
                }
            }
            result.fold(
                onSuccess = { opened ->
                    val docIndex = documents.size
                    val added = (0 until opened.pageCount).map { index ->
                        val size = opened.pageSize(index) ?: (595 to 842)
                        PdfPage(PageKey(docIndex, index), size.first, size.second)
                    }
                    documents = documents + opened
                    pages = pages + added
                    snackbarHostState.showSnackbar(
                        "Added ${added.size} page(s) · ${pages.size} in total",
                    )
                },
                onFailure = {
                    snackbarHostState.showSnackbar("Couldn't add that PDF: ${it.message ?: "unreadable"}")
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
                    ?: error("the file couldn't be opened")
                val bytes = input.use { it.readBytes() }
                // A file with fields to fill in gets a button for it.
                val parsed = org.ziyoffice.pdf.PdfFile.open(bytes)
                val form = parsed?.let { org.ziyoffice.pdf.AcroForm.read(it) }
                hasForm = form != null && !form.isEmpty
                val cache = File(context.cacheDir, "pdf-${file.uri.hashCode()}.pdf")
                PdfDocument.open(
                    java.io.ByteArrayInputStream(bytes),
                    cache,
                ) ?: error("this PDF couldn't be parsed")
            }
        }
        result.fold(
            onSuccess = { opened ->
                val rendered = (0 until opened.pageCount).map { index ->
                    val size = opened.pageSize(index) ?: (595 to 842)
                    PdfPage(PageKey(0, index), size.first, size.second)
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

    // Hold a ref to the latest document list so it is closed exactly once when
    // this screen leaves composition, not on every change to the list (which
    // would close renderers other screens still need).
    val documentsRef = remember { mutableStateOf(documents) }
    documentsRef.value = documents
    val bitmapCache = remember(file.uri) { PageBitmapCache(maxEntries = 6) }
    DisposableEffect(file.uri) {
        onDispose {
            documentsRef.value.forEach { runCatching { it.close() } }
            bitmapCache.clear()
        }
    }

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
                                failure != null -> "Couldn't open it"
                                else -> "${pages.size} page(s) · ${pages.size - deleted.size} on export"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    if (documents.isNotEmpty() && !loading) {
                        // Save and Print stay visible: they are what people
                        // open a PDF to do. The rest move into one menu, because
                        // five icons plus the back arrow clip the title.
                        if (hasForm) {
                            IconButton(onClick = onFillForm) {
                                Icon(
                                    Icons.Filled.EditNote,
                                    contentDescription = "Fill the form",
                                )
                            }
                        }
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
                                        // Pre-fill with the selected page, the most
                                        // common split being "just this one".
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
                        // coroutine cancellation, so it is told to stop between
                        // pages. Cancelling the job as well would kill the
                        // coroutine before it could say anything about it.
                        exportCancelled = true
                    },
                )

                failure != null -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Couldn't open this PDF.", style = MaterialTheme.typography.titleMedium)
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
                        // compound page key is encoded as "doc:source".
                        itemsIndexed(pages, key = { _, page -> "${page.key.doc}:${page.key.source}" }) { index, page ->
                            val isDeleted = page.key in deleted
                            val isSelected = index == selectedPage
                            val rotation = rotations[page.key] ?: 0
                            val isSideways = rotation % 180 != 0
                            val effWidth = if (isSideways) page.heightPt else page.widthPt
                            val effHeight = if (isSideways) page.widthPt else page.heightPt
                            val pageHeightPx = (pageWidthPx.toFloat() * effHeight / effWidth).toInt().coerceAtLeast(100)

                            var pageBitmap by remember(page.key, rotation, pageWidthPx) {
                                mutableStateOf(bitmapCache.get(page.key, rotation))
                            }

                            LaunchedEffect(page.key, rotation, pageWidthPx) {
                                if (pageBitmap == null) {
                                    val doc = documents.getOrNull(page.key.doc)
                                    val rendered = withContext(Dispatchers.IO) {
                                        doc?.renderPage(page.key.source, pageWidthPx)?.let { raw ->
                                            if (rotation != 0) {
                                                val rotated = rotateBitmap(raw, rotation.toFloat())
                                                if (rotated != raw) raw.recycle()
                                                rotated
                                            } else {
                                                raw
                                            }
                                        }
                                    }
                                    if (rendered != null) {
                                        bitmapCache.put(page.key, rotation, rendered)
                                        pageBitmap = rendered
                                    }
                                }
                            }

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
                                                val nextRotation = ((rotations[page.key] ?: 0) + 90) % 360
                                                rotations = rotations + (page.key to nextRotation)
                                                marks = rotateMarks(marks, page.key.doc, page.key.source, 90)
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
                                val currentBitmap = pageBitmap
                                if (currentBitmap != null) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(currentBitmap.width.toFloat() / currentBitmap.height.toFloat()),
                                    ) {
                                        Image(
                                            bitmap = currentBitmap.asImageBitmap(),
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
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(with(LocalDensity.current) { pageHeightPx.toDp() })
                                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            "Page ${index + 1}",
                                             style = MaterialTheme.typography.bodySmall,
                                             color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                                    "Tap a page to select it. Drag to ink or sign, tap to highlight. " +
                                        "Rotate, reorder or remove it from the row below it.",
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

    if (showSplit) {
        val available = pages.size
        // Parsed on every keystroke, so the dialog can say "no" while the user types.
        val parsed = PageRange.parse(splitRange, available)
        AlertDialog(
            onDismissRequest = { showSplit = false },
            title = { Text("Split pages") },
            text = {
                Column {
                    Text(
                        "Write the pages you want into a new PDF. " +
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
                                    else -> "Not a page range in this document"
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

    // Every mark is listed with a visible page number and a remove action. A
    // mark made by drag has no other accessible handle: TalkBack cannot drag,
    // and a highlight or redaction is otherwise impossible to find or undo.
    if (showMarks) {
        AlertDialog(
            onDismissRequest = { showMarks = false },
            title = { Text("Marks (${marks.size})") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "A saved PDF is drawn page by page, so the copy you export looks " +
                            "the same but its text is no longer selectable. A redaction " +
                            "is different: it removes what is underneath, so that content " +
                            "cannot be recovered from the saved file.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                if (marks.isEmpty()) {
                    Text("No marks on this document yet")
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp)
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

private fun markLabel(mark: PdfMark): String = when (mark) {
    is PdfMark.Highlight -> "Highlight"
    is PdfMark.Ink -> if (mark.isSignature) "Signature" else "Ink stroke"
    is PdfMark.Note -> "Note \u201C${mark.text.take(40)}\u201D"
    is PdfMark.Text -> "Text \u201C${mark.text.take(40)}\u201D"
    is PdfMark.Redaction -> "Redaction"
}

/**
 * A display name for a destination the user just picked. The source file's name
 * is wrong here (the user chose a new file), so it comes from the uri itself.
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
 * Blocks the screen while a copy is written and lets the user stop it.
 * [progress] is 0..1, or null when the export cannot say how far it got.
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
