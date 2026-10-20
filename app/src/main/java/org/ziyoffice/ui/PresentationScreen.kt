package org.ziyoffice.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ziyoffice.data.DraftStore
import org.ziyoffice.export.DeckExporter
import org.ziyoffice.export.Printer
import org.ziyoffice.model.RecentFile
import org.ziyoffice.model.SlideDeck
import org.ziyoffice.model.withLines
import org.ziyoffice.model.withSlideAdded
import org.ziyoffice.model.withSlideMoved
import org.ziyoffice.model.withSlideRemoved
import org.ziyoffice.ooxml.PptxReader
import org.ziyoffice.ooxml.PptxWriter

/**
 * Presentation editor: a horizontal slide strip, a text editor for the selected
 * slide (title, body lines, speaker notes) and a full-screen present mode.
 *
 * The model edits text only, because the writer patches text in the original
 * package: layouts, images, themes and animation survive an edit untouched but
 * are not editable here. The strip is honest about that — it shows the text a
 * slide carries, not a broken imitation of its artwork.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PresentationScreen(file: RecentFile, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val drafts = remember { DraftStore(context) }
    val snackbar = remember { SnackbarHostState() }
    var original by remember(file.uri) { mutableStateOf<ByteArray?>(null) }
    var deck by remember(file.uri) { mutableStateOf<SlideDeck?>(null) }
    var dirty by remember(file.uri) { mutableStateOf(false) }
    // Bumped by every deck edit, so an in-flight write can tell whether newer
    // edits appeared while it was running.
    var writeVersion by remember(file.uri) { mutableStateOf(0L) }
    var saving by remember { mutableStateOf(false) }
    var presenting by remember(file.uri) { mutableStateOf(false) }
    var slideIndex by remember(file.uri) { mutableStateOf(0) }
    var showExitDialog by remember(file.uri) { mutableStateOf(false) }
    var loadError by remember(file.uri) { mutableStateOf<String?>(null) }

    /**
     * The name the SAF provider reports for [uri], so export messages can name
     * the file the user actually picked. Falls back to the last path segment.
     */
    fun pdfName(uri: Uri): String {
        val queried = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && !cursor.isNull(index)) cursor.getString(index) else null
            } else {
                null
            }
        }
        return queried?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
            ?: "the PDF"
    }

    /** Reads and parses the package; called on entry and again from "Try again". */
    suspend fun loadDeck() {
        loadError = null
        runCatching {
            withContext(Dispatchers.IO) {
                val bytes = drafts.load(file.uri) ?: context.contentResolver
                    .openInputStream(Uri.parse(file.uri))?.use { it.readBytes() }
                    ?: error("The presentation could not be opened")
                bytes to PptxReader().read(ByteArrayInputStream(bytes))
            }
        }.onSuccess { (bytes, parsed) ->
            original = bytes
            deck = parsed
            dirty = drafts.load(file.uri) != null
        }.onFailure {
            loadError = it.message ?: "invalid PPTX"
            snackbar.showSnackbar("Could not open presentation: ${it.message ?: "invalid PPTX"}")
        }
    }

    LaunchedEffect(file.uri) { loadDeck() }

    // Recovery silence is the bug: a failed draft write used to vanish into
    // runCatching, so crash protection stopped working without saying so.
    var draftAutosaveHealthy by remember(file.uri) { mutableStateOf(true) }
    LaunchedEffect(deck, dirty) {
        val current = deck ?: return@LaunchedEffect
        if (!dirty) return@LaunchedEffect
        val source = original ?: return@LaunchedEffect
        delay(1500L)
        val saved = withContext(Dispatchers.IO) {
            runCatching { drafts.save(file.uri, PptxWriter().edit(source, current)) }.isSuccess
        }
        if (!saved && draftAutosaveHealthy) {
            draftAutosaveHealthy = false
            snackbar.showSnackbar("Could not save your draft — your work may not be recoverable")
        } else if (saved) {
            draftAutosaveHealthy = true
        }
    }

    // Same last-chance flush as the other editors: an edit made between the
    // draft write above and leaving composition exists only in this process.
    val flushTarget = remember(file.uri) { mutableStateOf<SlideDeck?>(null) }
    // Set when the user explicitly discards: the last-chance flush must not
    // resurrect the draft that Discard just deleted.
    val discardDraft = remember(file.uri) { mutableStateOf(false) }
    SideEffect { flushTarget.value = deck?.takeIf { dirty } }
    DisposableEffect(file.uri) {
        onDispose {
            if (discardDraft.value) return@onDispose
            val current = flushTarget.value ?: return@onDispose
            val source = original ?: return@onDispose
            val uri = file.uri
            val store = drafts
            // A composition scope is cancelled before onDispose runs, so a plain
            // scope.launch would never land. A daemon thread always finishes,
            // and this is a small file write (a few milliseconds).
            Thread {
                runCatching { store.save(uri, PptxWriter().edit(source, current)) }
            }.apply { name = "draft-flush" }.start()
        }
    }

    fun save(onSaved: () -> Unit = {}) {
        val bytes = original ?: return
        val current = deck ?: return
        val writingVersion = writeVersion
        saving = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val output = PptxWriter().edit(bytes, current)
                    context.contentResolver.openOutputStream(Uri.parse(file.uri), "wt")
                        ?.use { it.write(output) } ?: error("The file could not be opened for writing")
                    output
                }
            }
            saving = false
            result.onSuccess {
                if (writeSettlesDirty(writingVersion, writeVersion)) {
                    original = it
                    drafts.clear(file.uri)
                    dirty = false
                    snackbar.showSnackbar("Saved \u00B7 ${file.displayName}")
                    onSaved()
                } else {
                    // Edits landed while the write was running: the newer work
                    // stays dirty and keeps its draft recovery copy.
                    snackbar.showSnackbar("Saved \u00B7 newer edits kept")
                }
            }.onFailure {
                snackbar.showSnackbar("Could not save: ${it.message ?: it.javaClass.simpleName}")
            }
        }
    }

    /**
     * The single exit path: leave present mode, leave outright when clean, or
     * ask what to do about unsaved edits. Both the system back gesture and the
     * toolbar arrow use it.
     */
    fun tryExit() {
        if (presenting) {
            presenting = false
            return
        }
        when (exitAction(dirty = dirty, saving = saving, needsConfirmation = true)) {
            ExitAction.LEAVE -> onBack()
            ExitAction.BLOCK -> scope.launch {
                snackbar.showSnackbar("Saving \u00B7 wait a moment")
            }
            ExitAction.ASK -> showExitDialog = true
            // Slides always ask, so this branch is unreachable.
            ExitAction.SAVE_AND_LEAVE -> showExitDialog = true
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf"),
    ) { uri ->
        val current = deck ?: return@rememberLauncherForActivityResult
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    // Render to memory first, so success can be claimed only
                    // once real bytes exist.
                    val buffer = ByteArrayOutputStream()
                    DeckExporter.export(current, buffer)
                    val bytes = buffer.toByteArray()
                    check(bytes.isNotEmpty()) { "the PDF came back empty" }
                    context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                        out.write(bytes)
                    } ?: error("the file could not be created")
                    // A write that did not throw can still leave nothing behind:
                    // a full disk, a revoked grant or an aborted stream all end
                    // silently. Read the destination back before claiming success.
                    val size = context.contentResolver.openFileDescriptor(uri, "r")
                        ?.use { it.statSize }
                        ?: error("the saved file could not be read back")
                    if (size <= 0L) error("nothing was written to ${pdfName(uri)}")
                    bytes.size
                }
            }
            if (!isActive) return@launch
            result.onFailure {
                snackbar.showSnackbar("Could not export PDF: ${it.message ?: "unknown error"}")
            }.onSuccess {
                // The user picked the name in the picker; report that, not the
                // deck it came from. The PDF carries text only.
                snackbar.showSnackbar(
                    "PDF exported to ${pdfName(uri)} \u00B7 text only \u2014 " +
                        "images, shapes and transitions are not included",
                )
            }
        }
    }

    /** Opens the system print dialog for the deck, rendered in memory first. */
    fun printDeck() {
        val current = deck ?: return
        scope.launch {
            val bytes = withContext(Dispatchers.IO) {
                runCatching { Printer.deckBytes(current) }.getOrNull()
            }
            if (!isActive) return@launch
            if (bytes == null) {
                snackbar.showSnackbar("Could not prepare the presentation for printing")
                return@launch
            }
            val name = file.displayName.substringBeforeLast('.', file.displayName)
            if (!Printer.print(context, name, bytes)) {
                snackbar.showSnackbar("This device has no print service")
            }
        }
    }

    BackHandler { tryExit() }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("Save changes?") },
            text = { Text("Unsaved edits to ${file.displayName} will be lost if you leave now.") },
            confirmButton = {
                // Three actions in the confirm row: Cancel keeps the user in the
                // editor, Discard leaves, Save writes then leaves only on success.
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = { showExitDialog = false }) { Text("Cancel") }
                    TextButton(
                        onClick = {
                            showExitDialog = false
                            // Discard must also drop the recovery draft, or the
                            // next open would silently restore what was thrown away.
                            discardDraft.value = true
                            dirty = false
                            val store = drafts
                            val uri = file.uri
                            Thread { runCatching { store.discard(uri) } }
                                .apply { name = "draft-discard" }
                                .start()
                            onBack()
                        },
                    ) { Text("Discard") }
                    TextButton(
                        onClick = {
                            showExitDialog = false
                            save(onSaved = { onBack() })
                        },
                    ) { Text("Save") }
                }
            },
        )
    }

    val current = deck
    if (presenting && current != null) {
        PresentingOverlay(
            deck = current,
            index = slideIndex.coerceIn(current.slides.indices),
            onIndexChange = { slideIndex = it },
            onExit = { presenting = false },
        )
        return
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = ::tryExit) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Text(file.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                actions = {
                    IconButton(
                        onClick = { slideIndex = 0; presenting = true },
                        enabled = current?.slides?.isNotEmpty() == true,
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Present slides")
                    }
                    IconButton(
                        onClick = {
                            deck = deck?.withSlideAdded(slideIndex)
                            slideIndex = (slideIndex + 1).coerceAtMost(deck?.slides?.lastIndex ?: 0)
                            dirty = true
                            writeVersion++
                        },
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add slide")
                    }
                    IconButton(
                        onClick = {
                            val name = file.displayName.substringBeforeLast('.', file.displayName)
                            exportLauncher.launch("$name.pdf")
                        },
                        enabled = current?.slides?.isNotEmpty() == true,
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = "Export as PDF")
                    }
                    IconButton(
                        onClick = ::printDeck,
                        enabled = current?.slides?.isNotEmpty() == true,
                    ) {
                        Icon(Icons.Default.Print, contentDescription = "Print")
                    }
                    IconButton(onClick = ::save, enabled = dirty && !saving) {
                        Icon(Icons.Default.Save, contentDescription = "Save")
                    }
                },
            )
        },
    ) { padding ->
        if (loadError != null) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "${file.displayName} could not be opened",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    loadError.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                TextButton(
                    onClick = { scope.launch { loadDeck() } },
                    modifier = Modifier.padding(top = 16.dp),
                ) { Text("Try again") }
                TextButton(onClick = onBack) { Text("Back") }
            }
        } else if (current == null) {
            Text("Loading presentation…", Modifier.padding(padding).padding(24.dp))
        } else {
            val safeIndex = slideIndex.coerceIn(current.slides.indices)
            Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SlideStrip(
                    deck = current,
                    selected = safeIndex,
                    onSelect = { slideIndex = it },
                )
                SlideEditor(
                    deck = current,
                    index = safeIndex,
                    onChange = { updated ->
                        deck = updated
                        dirty = true
                        writeVersion++
                    },
                )
            }
        }
    }
}

/** The horizontally scrolling film strip: one numbered card per slide. */
@Composable
private fun SlideStrip(
    deck: SlideDeck,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(deck.slides, key = { _, slide -> slide.number }) { index, slide ->
            val isSelected = index == selected
            val spokenTitle = slide.title.ifBlank { "untitled" }
            Column(
                modifier = Modifier
                    .width(132.dp)
                    .semantics(mergeDescendants = true) {
                        // Position, title and selection, so TalkBack users can
                        // tell which slide they are on and activate it.
                        contentDescription =
                            "Slide ${index + 1} of ${deck.slides.size}, $spokenTitle"
                        this.selected = isSelected
                        role = Role.Button
                    }
                    .clickable { onSelect(index) },
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(deck.aspectRatio)
                        .then(
                            if (isSelected) {
                                Modifier.border(
                                    width = 2.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = RoundedCornerShape(10.dp),
                                )
                            } else {
                                Modifier
                            },
                        ),
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            slide.title.ifBlank { "Slide ${index + 1}" },
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        slide.body.firstOrNull()?.takeIf { it.isNotBlank() }?.let { line ->
                            Text(
                                line,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                Text(
                    "${index + 1}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }
        }
    }
}

/**
 * Text editor for one slide: title and body lines as separate fields (the model
 * is line-based), plus speaker notes. Line add/remove is explicit so nothing
 * changes silently while typing.
 */
@Composable
private fun SlideEditor(
    deck: SlideDeck,
    index: Int,
    onChange: (SlideDeck) -> Unit,
) {
    val slide = deck.slides.getOrNull(index) ?: return
    val lines = slide.lines.ifEmpty { listOf("") }

    fun replaceLine(lineIndex: Int, text: String) {
        val updated = lines.toMutableList()
        // The synthetic single empty field of an empty slide still writes lines[0].
        if (lineIndex < lines.size) updated[lineIndex] = text else updated += text
        onChange(deck.withLines(index, updated))
    }

    fun removeLine(lineIndex: Int) {
        onChange(deck.withLines(index, lines.filterIndexed { i, _ -> i != lineIndex }))
    }

    fun addLine() {
        onChange(deck.withLines(index, lines + ""))
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            bottom = 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Slide ${index + 1} of ${deck.slides.size}", style = MaterialTheme.typography.titleMedium)
                Row {
                    IconButton(
                        onClick = { onChange(deck.withSlideMoved(index, -1)) },
                        enabled = index > 0,
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Move slide left")
                    }
                    IconButton(
                        onClick = { onChange(deck.withSlideMoved(index, 1)) },
                        enabled = index < deck.slides.lastIndex,
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Move slide right")
                    }
                    IconButton(
                        onClick = { onChange(deck.withSlideRemoved(index)) },
                        enabled = deck.slides.size > 1,
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Remove slide")
                    }
                }
            }
        }

        itemsIndexed(lines) { lineIndex, line ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                TextField(
                    value = line,
                    onValueChange = { replaceLine(lineIndex, it) },
                    modifier = Modifier.weight(1f),
                    label = { Text(if (lineIndex == 0) "Title" else "Line ${lineIndex + 1}") },
                    minLines = if (lineIndex == 0) 1 else 2,
                )
                if (lines.size > 1) {
                    IconButton(onClick = { removeLine(lineIndex) }) {
                        Icon(Icons.Default.Close, contentDescription = "Remove line ${lineIndex + 1}")
                    }
                }
            }
        }

        item {
            OutlinedButton(onClick = ::addLine, modifier = Modifier.fillMaxWidth()) {
                Text("Add a line")
            }
        }

        item {
            TextField(
                value = slide.notes,
                onValueChange = { notes ->
                    val updated = deck.slides.toMutableList()
                    updated[index] = slide.copy(notes = notes)
                    onChange(deck.copy(slides = updated))
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Speaker notes") },
                minLines = 3,
            )
        }
    }
}

/**
 * Full-screen present mode. Tap to advance, tap past the last slide to leave;
 * the bottom bar carries page controls, a speaker-notes toggle and an exit button.
 */
@Composable
private fun PresentingOverlay(
    deck: SlideDeck,
    index: Int,
    onIndexChange: (Int) -> Unit,
    onExit: () -> Unit,
) {
    var showNotes by remember { mutableStateOf(false) }
    val slide = deck.slides.getOrNull(index)

    fun advance() {
        if (index < deck.slides.lastIndex) onIndexChange(index + 1) else onExit()
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .clickable { advance() }
            .semantics { contentDescription = "Slide ${index + 1} of ${deck.slides.size}. Tap to advance." },
        color = MaterialTheme.colorScheme.surface,
    ) {
        Box(Modifier.fillMaxSize().padding(32.dp)) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                slide?.lines.orEmpty().forEachIndexed { lineIndex, line ->
                    if (line.isEmpty()) return@forEachIndexed
                    Text(
                        line,
                        style = if (lineIndex == 0) {
                            MaterialTheme.typography.displaySmall
                        } else {
                            MaterialTheme.typography.headlineSmall
                        },
                    )
                }
            }

            if (showNotes) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(bottom = 64.dp),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Speaker notes", style = MaterialTheme.typography.labelMedium)
                        Text(
                            slide?.notes?.takeIf { it.isNotBlank() } ?: "No notes for this slide.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.align(Alignment.BottomCenter),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { onIndexChange((index - 1).coerceAtLeast(0)) }, enabled = index > 0) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous slide")
                }
                Text("${index + 1} / ${deck.slides.size}", Modifier.padding(horizontal = 12.dp))
                IconButton(onClick = { advance() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next slide")
                }
                IconButton(onClick = { showNotes = !showNotes }) {
                    Icon(Icons.AutoMirrored.Filled.Notes, contentDescription = "Toggle speaker notes")
                }
                IconButton(onClick = onExit) {
                    Icon(Icons.Default.Close, contentDescription = "End presentation")
                }
            }
        }
    }
}
