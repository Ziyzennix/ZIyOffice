package org.ziyoffice.ui

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import android.content.Intent
import android.net.Uri
import android.graphics.BitmapFactory
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ziyoffice.export.PdfExporter
import org.ziyoffice.export.Printer
import org.ziyoffice.data.DraftStore
import org.ziyoffice.data.RecentFilesStore
import org.ziyoffice.model.Block
import org.ziyoffice.model.Document
import org.ziyoffice.model.blocksIn
import org.ziyoffice.model.withParagraphRemovedAt
import org.ziyoffice.model.withParagraphAddedAfter
import org.ziyoffice.model.withPageNumberInFooter
import org.ziyoffice.model.DocumentRegion
import org.ziyoffice.model.FocusPath
import org.ziyoffice.model.mapParagraphAt
import org.ziyoffice.model.formatRange
import org.ziyoffice.model.applyText
import org.ziyoffice.model.findMatches
import org.ziyoffice.model.replaceAll
import org.ziyoffice.model.paragraphAt
import org.ziyoffice.model.textLength
import org.ziyoffice.model.withPastedParagraphs
import org.ziyoffice.model.DocumentKind
import org.ziyoffice.model.withColumnInserted
import org.ziyoffice.model.withColumnRemoved
import org.ziyoffice.model.withRowInserted
import org.ziyoffice.model.withRowRemoved
import org.ziyoffice.model.emptyTable
import org.ziyoffice.model.InlineImage
import org.ziyoffice.model.ListInfo
import org.ziyoffice.model.ListKind
import org.ziyoffice.model.Paragraph
import org.ziyoffice.model.ParagraphAlignment
import org.ziyoffice.model.RecentFile
import org.ziyoffice.model.Table
import org.ziyoffice.saf.SafeWrite
import org.ziyoffice.ooxml.PackageKind
import org.ziyoffice.ooxml.DocxEditor
import org.ziyoffice.ooxml.DocxReader
import org.ziyoffice.ooxml.DocxWriter

/**
 * The document editor: reads the file with the OOXML layer, renders styles,
 * lists, tables and images, and saves the edits back as a real .docx.
 */
@OptIn(ExperimentalMaterial3Api::class)

@Composable
fun EditorScreen(
    file: RecentFile,
    initialEditing: Boolean = false,
    onBack: () -> Unit,
    onSaveAs: (RecentFile) -> Unit = {},
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val access = rememberFileAccess(file.uri)
    val previewState = rememberDocxPreviewState(file)
    val preview by previewState

    var editing by remember(file.uri) { mutableStateOf(initialEditing) }
    var dirty by remember(file.uri) { mutableStateOf(false) }
    var saving by remember(file.uri) { mutableStateOf(false) }
    // Bumped by every edit, so an in-flight save can spot newer work.
    var writeVersion by remember(file.uri) { mutableStateOf(0L) }
    var showExitDialog by remember(file.uri) { mutableStateOf(false) }
    // Content-loss gate: shown once per open, before the first write that
    // would drop parts of the file we cannot reproduce.
    var showLossWarning by remember(file.uri) { mutableStateOf(false) }
    var lossAccepted by remember(file.uri) { mutableStateOf(false) }
    var focusedTarget by remember(file.uri) { mutableStateOf<FocusPath?>(null) }
    // A selection belongs to the paragraph it was made in. Tapping into
    // another paragraph must not leave the old offsets behind, or Bold would
    // land on the wrong words.
    val selections = remember(file.uri) { mutableStateMapOf<FocusPath, TextRange>() }
    var findOpen by remember { mutableStateOf(false) }
    var findQuery by remember { mutableStateOf("") }
    var findCursor by remember { mutableStateOf(0) }
    var replaceText by remember { mutableStateOf("") }

    val findMatches = remember(preview, findQuery) {
        if (findQuery.isEmpty()) emptyList()
        else (preview as? DocxPreview.Ready)?.document?.findMatches(findQuery) ?: emptyList()
    }

    /** A blank document means the user came here to write, so put the caret in. */
    fun focusBlankDocumentIfEmpty() {
        val ready = previewState.value as? DocxPreview.Ready ?: return
        val onlyParagraph = ready.document.blocks.singleOrNull() as? Paragraph ?: return
        if (onlyParagraph.text().isEmpty()) focusedTarget = FocusPath(0)
    }

    // A document created a moment ago opens writing, so the caret goes in as soon as it loads.
    var focusedBlankOnce by remember(file.uri) { mutableStateOf(false) }
    val readyArrived = preview is DocxPreview.Ready
    LaunchedEffect(file.uri, readyArrived) {
        if (initialEditing && readyArrived && !focusedBlankOnce) {
            focusedBlankOnce = true
            focusBlankDocumentIfEmpty()
        }
    }

    // Undo/redo is free: the model is immutable, so history is a stack of references.
    // Rapid typing is coalesced with [coalesce], so undo steps back by thought, not keystroke.
    var undoStack by remember(file.uri) { mutableStateOf(emptyList<Document>()) }
    var redoStack by remember(file.uri) { mutableStateOf(emptyList<Document>()) }
    var coalesceKey by remember(file.uri) { mutableStateOf<String?>(null) }

    val draftStore = remember { DraftStore(context) }
    var draftRecovery by remember(file.uri) { mutableStateOf(false) }
    LaunchedEffect(file.uri) {
        val bytes = withContext(Dispatchers.IO) { draftStore.load(file.uri) }
        if (bytes != null) draftRecovery = true
    }

    val currentDoc = (preview as? DocxPreview.Ready)?.document

    /**
     * The bytes to write for [document]. A document from a package is written
     * back into that package, so everything the editor cannot edit survives.
     * One ZiyOffice created has no package and is written from scratch.
     */
    fun documentBytes(document: Document): ByteArray {
        val source = (previewState.value as? DocxPreview.Ready)?.source
        return if (source != null) DocxEditor(source).save(document) else DocxWriter().write(document)
    }

    // A failed autosave used to vanish into runCatching, so crash protection
    // silently stopped working. Report the failure once, then stay quiet.
    var draftAutosaveHealthy by remember(file.uri) { mutableStateOf(true) }
    LaunchedEffect(currentDoc, editing, dirty) {
        if (!dirty || !editing || currentDoc == null) return@LaunchedEffect
        delay(1500L)
        val saved = withContext(Dispatchers.IO) {
            runCatching { draftStore.save(file.uri, documentBytes(currentDoc)) }.isSuccess
        }
        if (!saved && draftAutosaveHealthy) {
            draftAutosaveHealthy = false
            snackbarHostState.showSnackbar(
                "Could not save your draft — your work may not be recoverable",
            )
        } else if (saved) {
            draftAutosaveHealthy = true
        }
    }

    // A keystroke in the last 1.5 s exists only in this process: the debounced
    // autosave has not run yet and state is not rememberSaveable. Leaving
    // composition is the last chance, so the draft goes out on a daemon thread.
    val flushTarget = remember(file.uri) { mutableStateOf<Document?>(null) }
    // Set when the user discards on purpose: the last-chance flush must not resurrect that draft.
    val discardDraft = remember(file.uri) { mutableStateOf(false) }
    SideEffect { flushTarget.value = currentDoc?.takeIf { dirty && editing } }
    DisposableEffect(file.uri) {
        onDispose {
            if (discardDraft.value) return@onDispose
            val document = flushTarget.value ?: return@onDispose
            val uri = file.uri
            val store = draftStore
            // A composition scope is cancelled before onDispose runs, so a plain
            // scope.launch would never land. A daemon thread always finishes.
            Thread {
                runCatching { store.save(uri, documentBytes(document)) }
            }.apply { name = "draft-flush" }.start()
        }
    }
    fun pushHistory(document: Document, coalesce: String?) {
        if (coalesce != null && coalesce == coalesceKey) return
        undoStack = (undoStack + document).takeLast(UNDO_LIMIT)
        redoStack = emptyList()
        coalesceKey = coalesce
    }

    fun updateDocument(coalesce: String? = null, transform: (Document) -> Document) {
        val ready = previewState.value as? DocxPreview.Ready ?: return
        pushHistory(ready.document, coalesce)
        previewState.value = ready.copy(document = transform(ready.document))
        dirty = true
        writeVersion++
    }

    fun updateTargetParagraph(
        coalesce: String? = null,
        transform: (Paragraph) -> Paragraph,
    ) {
        val target = focusedTarget ?: return
        updateDocument(coalesce) { doc -> doc.mapParagraphAt(target, transform) }
    }

    /**
     * Puts the selected words on the clipboard.
     *
     * With nothing selected, the whole paragraph goes, which is what a
     * reader expects from a tap on a line.
     */
    fun copySelection() {
        val target = focusedTarget ?: return
        val ready = previewState.value as? DocxPreview.Ready ?: return
        val paragraph = ready.document.paragraphAt(target) ?: return
        val sel = selections[target] ?: TextRange.Zero
        val start = if (sel.collapsed) 0 else sel.min
        val end = if (sel.collapsed) paragraph.textLength() else sel.max
        EditorClipboard.copy(context, paragraph, start, end)
        scope.launch {
            snackbarHostState.showSnackbar("Copied")
        }
    }

    /**
     * Puts what is on the clipboard into the document.
     *
     * The first thing pasted takes the place of the paragraph the caret is
     * in, and the rest are added after it.
     */
    fun pasteClipboard() {
        val target = focusedTarget ?: return
        val pasted = EditorClipboard.read(context)
        if (pasted == null || pasted.isEmpty()) {
            scope.launch { snackbarHostState.showSnackbar("The clipboard holds nothing to paste") }
            return
        }
        updateDocument { doc -> doc.withPastedParagraphs(target, pasted) }
        editing = true
    }

    fun applyPreset(preset: ParagraphPreset) {
        val target = focusedTarget ?: return
        updateTargetParagraph { paragraph ->
            paragraph.copy(
                runs = paragraph.runs.map { run ->
                    run.copy(bold = preset.bold, sizePt = preset.sizePt)
                },
                spacingBeforeTwips = preset.spaceBeforeTwips,
                spacingAfterTwips = preset.spaceAfterTwips,
            )
        }
    }

    /** Hands the .docx to another app the user picks, no permissions needed. */
    fun shareDocument() {
        runCatching {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                putExtra(Intent.EXTRA_STREAM, Uri.parse(file.uri))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(
                Intent.createChooser(intent, "Share ${file.displayName}"),
            )
        }.onFailure {
            scope.launch {
                snackbarHostState.showSnackbar("No installed app can share this file.")
            }
        }
    }

    /** The name the SAF provider reports for [uri], or the last path segment. */
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

    fun exportPdfTo(uri: Uri) {
        val ready = previewState.value as? DocxPreview.Ready ?: return
        val document = ready.document
        // Snackbars outlive the screen, so a cancelled export stays quiet instead
        // of claiming a PDF the user never got. Leaving the editor cancels `scope`.
        scope.launch {
            val result: Result<Pair<String, Int>> = withContext(Dispatchers.IO) {
                runCatching {
                    // Render to memory first, so success is only claimed once there are real bytes.
                    val buffer = ByteArrayOutputStream()
                    PdfExporter.export(document, buffer)
                    val bytes = buffer.toByteArray()
                    check(bytes.isNotEmpty()) { "the PDF came back empty" }
                    val stream = context.contentResolver.openOutputStream(uri, "wt")
                        ?: error("the file could not be created")
                    stream.use { it.write(bytes) }
                    Pair(pdfName(uri), bytes.size)
                }
            }
            // The composition scope is cancelled before this runs, so a screen
            // that left mid-export reports nothing rather than a false success.
            if (!isActive) return@launch
            result.fold(
                onSuccess = { (name, bytes) ->
                    snackbarHostState.showSnackbar("PDF exported to $name \u00B7 $bytes bytes")
                },
                onFailure = { error ->
                    snackbarHostState.showSnackbar(
                        "Could not export PDF: ${error.message ?: "unknown error"}",
                    )
                },
            )
        }
    }

    /** Opens the system print dialog. Rendering runs off the main thread. */
    fun printDocument() {
        val ready = previewState.value as? DocxPreview.Ready ?: return
        scope.launch {
            val bytes = withContext(Dispatchers.IO) {
                runCatching { Printer.documentBytes(ready.document) }.getOrNull()
            }
            if (!isActive) return@launch
            if (bytes == null) {
                snackbarHostState.showSnackbar("Could not prepare the document for printing")
                return@launch
            }
            val name = file.displayName.substringBeforeLast('.', file.displayName)
            if (!Printer.print(context, name, bytes)) {
                snackbarHostState.showSnackbar("This device has no print service")
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf"),
    ) { uri -> if (uri != null) exportPdfTo(uri) }
    val saveAsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        ),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val ready = previewState.value as? DocxPreview.Ready ?: return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = documentBytes(ready.document)
                    SafeWrite.write(context, uri, bytes, PackageKind.DOCUMENT)
                }
            }
            result.fold(
                onSuccess = {
                    // The bytes are already written, so say so even when the recents index fails.
                    val entry = RecentFilesStore(context).remember(uri)?.file
                    if (entry != null) {
                        draftStore.clear(file.uri)
                        dirty = false
                        snackbarHostState.showSnackbar("Saved copy \u00B7 ${entry.displayName}")
                        onSaveAs(entry)
                    } else {
                        snackbarHostState.showSnackbar(
                            "Saved, but the file could not be indexed — find it in your files",
                        )
                    }
                },
                onFailure = { e ->
                    snackbarHostState.showSnackbar(
                        "Could not save to ${e.message ?: "the destination"}.",
                    )
                },
            )
        }
    }
    val insertImageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val bytes = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                }.getOrNull()
            }
            if (bytes == null) {
                snackbarHostState.showSnackbar("Could not load image")
                return@launch
            }
            if (bytes.size > 8 * 1024 * 1024) {
                snackbarHostState.showSnackbar("Image is larger than 8 MB")
                return@launch
            }
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            val outWidth = options.outWidth
            val outHeight = options.outHeight
            if (outWidth <= 0 || outHeight <= 0) {
                snackbarHostState.showSnackbar("Could not decode image")
                return@launch
            }
            val scale = (320f / outWidth).coerceAtMost(1f)
            val image = InlineImage(bytes, outWidth * scale, outHeight * scale)
            val decoded = withContext(Dispatchers.IO) { decodeImage(bytes) }

            val ready = previewState.value as? DocxPreview.Ready
            val totalBlocks = ready?.document?.blocks?.size ?: 0
            val insertIndex = ((focusedTarget?.blockIndex ?: (totalBlocks - 1)) + 1).coerceIn(0, totalBlocks)
            updateDocument { doc ->
                val newBlocks = doc.blocks.toMutableList()
                newBlocks.add(insertIndex, Paragraph(images = listOf(image)))
                doc.copy(blocks = newBlocks)
            }
            val currentReady = previewState.value as? DocxPreview.Ready
            if (currentReady != null) {
                previewState.value = currentReady.copy(
                    images = currentReady.images + (image to decoded),
                )
            }
            focusedTarget = FocusPath(insertIndex)
        }
    }

    var showInsertTable by remember { mutableStateOf(false) }

    if (showInsertTable) {
        InsertTableDialog(
            onDismiss = { showInsertTable = false },
            onInsert = { rows, cols ->
                showInsertTable = false
                val table = emptyTable(rows, cols)
                val ready = previewState.value as? DocxPreview.Ready
                val totalBlocks = ready?.document?.blocks?.size ?: 0
                val insertIndex = ((focusedTarget?.blockIndex ?: (totalBlocks - 1)) + 1).coerceIn(0, totalBlocks)
                updateDocument { doc ->
                    val newBlocks = doc.blocks.toMutableList()
                    newBlocks.add(insertIndex, table)
                    doc.copy(blocks = newBlocks)
                }
                focusedTarget = FocusPath(insertIndex, 0, 0, 0)
            },
        )
    }

    var menuExpanded by remember { mutableStateOf(false) }

    fun undo() {
        val ready = previewState.value as? DocxPreview.Ready ?: return
        val previous = undoStack.lastOrNull() ?: return
        undoStack = undoStack.dropLast(1)
        redoStack = (redoStack + ready.document).takeLast(UNDO_LIMIT)
        previewState.value = ready.copy(document = previous)
        coalesceKey = null
        focusedTarget = null
        dirty = true
        writeVersion++
    }

    fun redo() {
        val ready = previewState.value as? DocxPreview.Ready ?: return
        val next = redoStack.lastOrNull() ?: return
        redoStack = redoStack.dropLast(1)
        undoStack = (undoStack + ready.document).takeLast(UNDO_LIMIT)
        previewState.value = ready.copy(document = next)
        coalesceKey = null
        focusedTarget = null
        dirty = true
        writeVersion++
    }

    fun toggleBold() {
        val target = focusedTarget ?: return
        val sel = selections[target] ?: TextRange.Zero
        val range = if (!sel.collapsed) sel.min..sel.max else null
        updateTargetParagraph { p ->
            if (range != null) {
                val turnOn = p.runs.formatRange(range) { it }.any { !it.bold }
                p.copy(runs = p.runs.formatRange(range) { it.copy(bold = turnOn) })
            } else {
                val turnOn = p.runs.any { !it.bold }
                p.copy(runs = p.runs.map { it.copy(bold = turnOn) })
            }
        }
    }

    fun toggleItalic() {
        val target = focusedTarget ?: return
        val sel = selections[target] ?: TextRange.Zero
        val range = if (!sel.collapsed) sel.min..sel.max else null
        updateTargetParagraph { p ->
            if (range != null) {
                val turnOn = p.runs.formatRange(range) { it }.any { !it.italic }
                p.copy(runs = p.runs.formatRange(range) { it.copy(italic = turnOn) })
            } else {
                val turnOn = p.runs.any { !it.italic }
                p.copy(runs = p.runs.map { it.copy(italic = turnOn) })
            }
        }
    }

    fun toggleUnderline() {
        val target = focusedTarget ?: return
        val sel = selections[target] ?: TextRange.Zero
        val range = if (!sel.collapsed) sel.min..sel.max else null
        updateTargetParagraph { p ->
            if (range != null) {
                val turnOn = p.runs.formatRange(range) { it }.any { !it.underline }
                p.copy(runs = p.runs.formatRange(range) { it.copy(underline = turnOn) })
            } else {
                val turnOn = p.runs.any { !it.underline }
                p.copy(runs = p.runs.map { it.copy(underline = turnOn) })
            }
        }
    }

    fun toggleStrike() {
        val target = focusedTarget ?: return
        val sel = selections[target] ?: TextRange.Zero
        val range = if (!sel.collapsed) sel.min..sel.max else null
        updateTargetParagraph { p ->
            if (range != null) {
                val turnOn = p.runs.formatRange(range) { it }.any { !it.strike }
                p.copy(runs = p.runs.formatRange(range) { it.copy(strike = turnOn) })
            } else {
                val turnOn = p.runs.any { !it.strike }
                p.copy(runs = p.runs.map { it.copy(strike = turnOn) })
            }
        }
    }

    fun setFontSize(size: Float) {
        updateTargetParagraph {
            val sel = focusedTarget?.let { selections[it] } ?: TextRange.Zero
            val range = if (!sel.collapsed) sel.min..sel.max else null
            if (range != null) it.copy(runs = it.runs.formatRange(range) { r -> r.copy(sizePt = size) })
            else it.copy(runs = it.runs.map { r -> r.copy(sizePt = size) })
        }
    }

    fun setColour(rgb: Int?) {
        updateTargetParagraph {
            val sel = focusedTarget?.let { selections[it] } ?: TextRange.Zero
            val range = if (!sel.collapsed) sel.min..sel.max else null
            if (range != null) it.copy(runs = it.runs.formatRange(range) { r -> r.copy(colorRgb = rgb) })
            else it.copy(runs = it.runs.map { r -> r.copy(colorRgb = rgb) })
        }
    }

    fun setAlignment(alignment: ParagraphAlignment) {
        val target = focusedTarget ?: return
        updateTargetParagraph { paragraph -> paragraph.copy(alignment = alignment) }
    }

    fun setLineSpacing(percent: Int?) {
        val target = focusedTarget ?: return
        updateTargetParagraph { paragraph -> paragraph.copy(lineSpacingPercent = percent) }
    }

    /** Moves the paragraph in or out one step. One with no indent of its own starts at zero. */
    fun setIndent(deltaTwips: Int) {
        val target = focusedTarget ?: return
        updateTargetParagraph { paragraph ->
            val current = paragraph.indentTwips ?: 0
            paragraph.copy(indentTwips = (current + deltaTwips).coerceAtLeast(0))
        }
    }

    fun setParagraphSpacing(beforeTwips: Int, afterTwips: Int) {
        val target = focusedTarget ?: return
        updateTargetParagraph { paragraph ->
            paragraph.copy(
                spacingBeforeTwips = beforeTwips,
                spacingAfterTwips = afterTwips,
            )
        }
    }

    fun toggleList(kind: ListKind) {
        val target = focusedTarget ?: return
        val ready = previewState.value as? DocxPreview.Ready ?: return
        val current = ready.document.paragraphAt(target)?.list
        val next = when {
            current == null ->
                ListInfo(numId = nextFreeNumId(ready.document), level = 0, kind = kind)
            current.kind == kind -> null
            else -> current.copy(kind = kind)
        }
        updateTargetParagraph { paragraph -> paragraph.copy(list = next) }
    }

    /**
     * Splits paragraph [index] in two at Enter. The new one inherits alignment,
     * list membership and run formatting, so a list keeps its numbering.
     */
    fun splitParagraph(path: FocusPath, before: String, after: String) {
        val ready = previewState.value as? DocxPreview.Ready ?: return
        val paragraph = ready.document.paragraphAt(path) ?: return
        updateDocument { document ->
            document
                .mapParagraphAt(path) { it.applyText(before) }
                .withParagraphAddedAfter(
                    path,
                    paragraph.applyText(after).copy(images = emptyList()),
                )
        }
        // The caret follows the new paragraph, wherever it was created.
        focusedTarget = path.copy(
            blockIndex = if (path.cellRow == null) path.blockIndex + 1 else path.blockIndex,
            cellParagraph = if (path.cellRow == null) path.cellParagraph else path.cellParagraph + 1,
        )
    }

    /**
     * Removes the focused paragraph. A body, header or footer keeps one
     * paragraph at least, so deleting the last leaves an empty one behind.
     */
    fun deleteParagraph(path: FocusPath) {
        val ready = previewState.value as? DocxPreview.Ready ?: return
        if (ready.document.paragraphAt(path) == null) return
        updateDocument { document -> document.withParagraphRemovedAt(path) }
        focusedTarget = null
    }

    fun insertRowAbove() {
        val target = focusedTarget ?: return
        val row = target.cellRow ?: return
        updateDocument { doc ->
            val table = doc.blocks.getOrNull(target.blockIndex) as? Table ?: return@updateDocument doc
            doc.copy(blocks = doc.blocks.mapIndexed { i, b ->
                if (i == target.blockIndex) table.withRowInserted(row) else b
            })
        }
    }

    fun insertRowBelow() {
        val target = focusedTarget ?: return
        val row = target.cellRow ?: return
        updateDocument { doc ->
            val table = doc.blocks.getOrNull(target.blockIndex) as? Table ?: return@updateDocument doc
            doc.copy(blocks = doc.blocks.mapIndexed { i, b ->
                if (i == target.blockIndex) table.withRowInserted(row + 1) else b
            })
        }
        focusedTarget = target.copy(cellRow = row + 1)
    }

    fun deleteRow() {
        val target = focusedTarget ?: return
        val row = target.cellRow ?: return
        updateDocument { doc ->
            val table = doc.blocks.getOrNull(target.blockIndex) as? Table ?: return@updateDocument doc
            doc.copy(blocks = doc.blocks.mapIndexed { i, b ->
                if (i == target.blockIndex) table.withRowRemoved(row) else b
            })
        }
        val ready = previewState.value as? DocxPreview.Ready
        val table = ready?.document?.blocks?.getOrNull(target.blockIndex) as? Table
        val newRowsCount = table?.rows?.size ?: 1
        val newRow = row.coerceAtMost(newRowsCount - 1)
        focusedTarget = target.copy(cellRow = newRow)
    }

    fun insertColumnLeft() {
        val target = focusedTarget ?: return
        val col = target.cellColumn ?: return
        updateDocument { doc ->
            val table = doc.blocks.getOrNull(target.blockIndex) as? Table ?: return@updateDocument doc
            doc.copy(blocks = doc.blocks.mapIndexed { i, b ->
                if (i == target.blockIndex) table.withColumnInserted(col) else b
            })
        }
    }

    fun insertColumnRight() {
        val target = focusedTarget ?: return
        val col = target.cellColumn ?: return
        updateDocument { doc ->
            val table = doc.blocks.getOrNull(target.blockIndex) as? Table ?: return@updateDocument doc
            doc.copy(blocks = doc.blocks.mapIndexed { i, b ->
                if (i == target.blockIndex) table.withColumnInserted(col + 1) else b
            })
        }
        focusedTarget = target.copy(cellColumn = col + 1)
    }

    fun deleteColumn() {
        val target = focusedTarget ?: return
        val col = target.cellColumn ?: return
        updateDocument { doc ->
            val table = doc.blocks.getOrNull(target.blockIndex) as? Table ?: return@updateDocument doc
            doc.copy(blocks = doc.blocks.mapIndexed { i, b ->
                if (i == target.blockIndex) table.withColumnRemoved(col) else b
            })
        }
        val ready = previewState.value as? DocxPreview.Ready
        val table = ready?.document?.blocks?.getOrNull(target.blockIndex) as? Table
        val newColsCount = table?.columnCount ?: 1
        val newCol = col.coerceAtMost(newColsCount - 1)
        focusedTarget = target.copy(cellColumn = newCol)
    }

    fun deleteTable() {
        val target = focusedTarget ?: return
        updateDocument { doc ->
            val blocks = doc.blocks.toMutableList()
            if (target.blockIndex in blocks.indices) {
                blocks.removeAt(target.blockIndex)
            }
            if (blocks.isEmpty()) blocks += Paragraph()
            doc.copy(blocks = blocks)
        }
        focusedTarget = null
    }

    fun appendParagraph() {
        val ready = previewState.value as? DocxPreview.Ready ?: return
        val newIndex = ready.document.blocks.size
        updateDocument { document ->
            document.copy(blocks = document.blocks + Paragraph())
        }
        focusedTarget = FocusPath(newIndex)
    }

    /**
     * Gives the document a header (or footer) with an empty paragraph, the
     * minimum Word accepts per part, and puts the caret in it.
     */
    fun addHeaderFooter(region: DocumentRegion) {
        val ready = previewState.value as? DocxPreview.Ready ?: return
        if (ready.document.blocksIn(region) != null) return
        updateDocument { document ->
            val blocks = listOf<Block>(Paragraph())
            when (region) {
                DocumentRegion.BODY -> document.copy(blocks = blocks)
                DocumentRegion.HEADER -> document.copy(header = blocks)
                DocumentRegion.FOOTER -> document.copy(footer = blocks)
            }
        }
        editing = true
        focusedTarget = FocusPath(0, region = region)
    }

    fun togglePageBreak() {
        val target = focusedTarget ?: return
        updateTargetParagraph { paragraph ->
            paragraph.copy(pageBreakBefore = !paragraph.pageBreakBefore)
        }
    }

    fun writeDocument(document: Document, onSaved: () -> Unit) {
        val writingVersion = writeVersion
        saving = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = documentBytes(document)
                    val written = SafeWrite.write(
                        context,
                        Uri.parse(file.uri),
                        bytes,
                        PackageKind.DOCUMENT,
                    )
                    check(
                        SafeWrite.verifyWritten(context, Uri.parse(file.uri), written, PackageKind.DOCUMENT),
                    ) { "the file could not be read back after saving" }
                }
            }
            saving = false
            result.fold(
                onSuccess = {
                    if (writeSettlesDirty(writingVersion, writeVersion)) {
                        draftStore.clear(file.uri)
                        dirty = false
                        snackbarHostState.showSnackbar("Saved \u00B7 ${file.displayName}")
                        onSaved()
                    } else {
                        // Edits landed while the write ran: the newer work stays
                        // dirty and keeps its draft.
                        snackbarHostState.showSnackbar("Saved \u00B7 newer edits kept")
                    }
                },
                onFailure = { error ->
                    snackbarHostState.showSnackbar(
                        "Could not save: ${error.message ?: error.javaClass.simpleName}. " +
                            "Re-open the file to grant write access.",
                    )
                },
            )
        }
    }

    /**
     * Writes the document to the file. Nothing is lost by saving: the edited
     * parts go back into the original package.
     */
    fun save(onSaved: () -> Unit = {}) {
        val ready = previewState.value as? DocxPreview.Ready ?: return
        writeDocument(ready.document, onSaved)
    }

    /** The single exit path, for the toolbar arrow and the back gesture. */
    fun tryExit() {
        when (exitAction(dirty = dirty, saving = saving, needsConfirmation = true)) {
            ExitAction.LEAVE -> onBack()
            ExitAction.BLOCK -> scope.launch {
                snackbarHostState.showSnackbar("Saving \u00B7 wait a moment")
            }
            ExitAction.ASK -> showExitDialog = true
            // Docs always ask, so this branch is unreachable; handled for exhaustiveness.
            ExitAction.SAVE_AND_LEAVE -> showExitDialog = true
        }
    }

    BackHandler { tryExit() }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("Save changes?") },
            text = {
                Text(
                    "Unsaved edits to ${file.displayName} will be lost if you leave now. " +
                        "A recovery copy stays on this device until the next save.",
                )
            },
            confirmButton = {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = { showExitDialog = false }) { Text("Stay") }
                    TextButton(
                        onClick = {
                            showExitDialog = false
                            // Leaving is the point of Discard: the recovery draft
                            // goes too, or reopening would restore thrown-away work.
                            discardDraft.value = true
                            dirty = false
                            val store = draftStore
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

    val droppedFeatures = (preview as? DocxPreview.Ready)?.dropped.orEmpty()
    if (showLossWarning) {
        AlertDialog(
            onDismissRequest = { showLossWarning = false },
            title = { Text("Content ZiyOffice can't edit") },
            text = {
                Text(
                    PreservedCopy.body(droppedFeatures, ".docx"),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLossWarning = false
                        // Read for this session: the banner does not come back.
                        lossAccepted = true
                    },
                ) { Text("Understood") }
            },
        )
    }

    Scaffold(
        // Lift the whole shell (including the formatting rail) above the keyboard.
        modifier = Modifier.imePadding(),
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = ::tryExit) {
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
                            metaLine(file) + when {
                                saving -> " \u00B7 SAVING\u2026"
                                dirty -> " \u00B7 UNSAVED EDITS"
                                else -> ""
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                },
                actions = {
                    if (file.kind == DocumentKind.DOCUMENT && preview is DocxPreview.Ready) {
                        IconButton(onClick = ::undo, enabled = undoStack.isNotEmpty()) {
                            Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
                        }
                        IconButton(onClick = ::redo, enabled = redoStack.isNotEmpty()) {
                            Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo")
                        }
                        // Entering edit mode is the primary action, so it keeps its
                        // icon; leaving it lives in the overflow. At 360 dp five
                        // icons clipped the file name to "Head…" and hid the save
                        // state, the one thing the user needs.
                        if (editing) {
                            IconButton(onClick = ::save, enabled = dirty && !saving) {
                                Icon(Icons.Filled.Save, contentDescription = "Save")
                            }
                        } else {
                            IconButton(onClick = {
                                editing = true
                                focusBlankDocumentIfEmpty()
                            }) {
                                Icon(Icons.Filled.Edit, contentDescription = "Edit")
                            }
                        }
                        Box {
                            IconButton(onClick = { menuExpanded = true }) {
                                Icon(
                                    Icons.Filled.MoreVert,
                                    contentDescription = "More actions",
                                )
                            }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false },
                            ) {
                                if (editing) {
                                    DropdownMenuItem(
                                        text = { Text("Finish editing") },
                                        onClick = {
                                            menuExpanded = false
                                            editing = false
                                        },
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("Find and replace") },
                                    onClick = {
                                        menuExpanded = false
                                        findOpen = true
                                        if (findMatches.isNotEmpty()) {
                                            focusedTarget = findMatches.first().path
                                        }
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Share as .docx") },
                                    onClick = {
                                        menuExpanded = false
                                        shareDocument()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Export as PDF") },
                                    onClick = {
                                        menuExpanded = false
                                        exportLauncher.launch(pdfFileName(file.displayName))
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Print…") },
                                    onClick = {
                                        menuExpanded = false
                                        printDocument()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Save as copy\u2026") },
                                    onClick = {
                                        menuExpanded = false
                                        saveAsLauncher.launch(file.displayName)
                                    },
                                )
                                if (editing) {
                                    DropdownMenuItem(
                                        text = { Text("Copy") },
                                        onClick = {
                                            menuExpanded = false
                                            copySelection()
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Paste") },
                                        onClick = {
                                            menuExpanded = false
                                            pasteClipboard()
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Insert picture\u2026") },
                                        onClick = {
                                            menuExpanded = false
                                            insertImageLauncher.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                            )
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Insert table\u2026") },
                                        onClick = {
                                            menuExpanded = false
                                            showInsertTable = true
                                        },
                                    )
                                    // A document with no header has no band to tap,
                                    // so this is the only way in.
                                    if ((preview as? DocxPreview.Ready)?.document?.header.isNullOrEmpty()) {
                                        DropdownMenuItem(
                                            text = { Text("Add header") },
                                            onClick = {
                                                menuExpanded = false
                                                addHeaderFooter(DocumentRegion.HEADER)
                                            },
                                        )
                                    }
                                    if ((preview as? DocxPreview.Ready)?.document?.footer.isNullOrEmpty()) {
                                        DropdownMenuItem(
                                            text = { Text("Add footer") },
                                            onClick = {
                                                menuExpanded = false
                                                addHeaderFooter(DocumentRegion.FOOTER)
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (editing) {
                Column {
                    if (focusedTarget?.cellRow != null) {
                        TableActionBar(
                            onInsertRowAbove = ::insertRowAbove,
                            onInsertRowBelow = ::insertRowBelow,
                            onDeleteRow = ::deleteRow,
                            onInsertColumnLeft = ::insertColumnLeft,
                            onInsertColumnRight = ::insertColumnRight,
                            onDeleteColumn = ::deleteColumn,
                            onDeleteTable = ::deleteTable,
                        )
                    }
                    val ready = preview as? DocxPreview.Ready
                    val focusedDocumentParagraph = focusedTarget?.let { target ->
                        ready?.document?.paragraphAt(target)
                    }
                    FormattingBar(
                        paragraph = focusedDocumentParagraph,
                        onPreset = ::applyPreset,
                        onBold = ::toggleBold,
                        onItalic = ::toggleItalic,
                        onUnderline = ::toggleUnderline,
                        onStrike = ::toggleStrike,
                        onSize = ::setFontSize,
                        onColour = ::setColour,
                        onAlignment = ::setAlignment,
                        onLineSpacing = ::setLineSpacing,
                        onIndent = ::setIndent,
                        onSpacing = ::setParagraphSpacing,
                        onToggleList = ::toggleList,
                        onTogglePageBreak = ::togglePageBreak,
                        onDeleteParagraph = { focusedTarget?.let(::deleteParagraph) },
                    )
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (findOpen) {
                FindReplaceBar(
                    query = findQuery,
                    onQueryChange = {
                        findQuery = it
                        findCursor = 0
                    },
                    matchCount = findMatches.size,
                    currentMatch = if (findMatches.isEmpty()) -1 else findCursor.coerceIn(0, findMatches.size - 1),
                    onNext = {
                        if (findMatches.isNotEmpty()) {
                            findCursor = (findCursor + 1) % findMatches.size
                            focusedTarget = findMatches[findCursor].path
                        }
                    },
                    onPrevious = {
                        if (findMatches.isNotEmpty()) {
                            findCursor = (findCursor - 1 + findMatches.size) % findMatches.size
                            focusedTarget = findMatches[findCursor].path
                        }
                    },
                    replaceText = replaceText,
                    onReplaceTextChange = { replaceText = it },
                    onReplace = {
                        val match = findMatches.getOrNull(findCursor)
                        if (match != null) {
                            updateDocument { doc ->
                                doc.mapParagraphAt(match.path) { p ->
                                    var replacedOnce = false
                                    val newRuns = p.runs.formatRange(match.start..match.end) { r ->
                                        if (!replacedOnce) {
                                            replacedOnce = true
                                            r.copy(text = replaceText)
                                        } else {
                                            r.copy(text = "")
                                        }
                                    }
                                    p.copy(runs = newRuns)
                                }
                            }
                        }
                    },
                    onReplaceAll = {
                        val ready = previewState.value as? DocxPreview.Ready
                        if (ready != null) {
                            var totalCount = 0
                            updateDocument { doc ->
                                val (newDoc, count) = doc.replaceAll(findQuery, replaceText)
                                totalCount = count
                                newDoc
                            }
                            scope.launch {
                                snackbarHostState.showSnackbar("Replaced $totalCount occurrences")
                            }
                        }
                    },
                    editing = editing,
                    onClose = { findOpen = false },
                )
            }
            // The page already explains a read failure, so repeating it doubles the noise.
            if (preview is DocxPreview.Ready) AccessLine(access)
            if (droppedFeatures.isNotEmpty() && !lossAccepted) {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            PreservedCopy.banner(droppedFeatures),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        TextButton(
                            onClick = { showLossWarning = true },
                            modifier = Modifier.padding(top = 4.dp),
                        ) {
                            Text("What cannot be edited")
                        }
                    }
                }
            }
            if (draftRecovery) {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 0.dp, vertical = 4.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Unsaved changes from a previous session.",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        TextButton(onClick = {
                            scope.launch {
                                val bytes = withContext(Dispatchers.IO) { draftStore.load(file.uri) }
                                if (bytes != null) {
                                    val draftSource = withContext(Dispatchers.IO) {
                                        DocxReader().readSource(java.io.ByteArrayInputStream(bytes))
                                    }
                                    val doc = draftSource.document
                                    val bitmaps = withContext(Dispatchers.IO) {
                                        doc.blocks
                                            .flatMap(::collectImages)
                                            .distinct()
                                            .associateWith { image -> decodeImage(image.bytes) }
                                    }
                                    // The draft is the user's unsaved work, not the source
                                    // file: keep the source audit so the warning still
                                    // describes what a save removes.
                                    val previous = (previewState.value as? DocxPreview.Ready)
                                    previewState.value = DocxPreview.Ready(
                                        document = doc,
                                        images = bitmaps,
                                        dropped = previous?.dropped.orEmpty(),
                                        source = draftSource,
                                    )
                                    dirty = true
                                }
                                draftRecovery = false
                            }
                        }) {
                            Text("Restore")
                        }
                        TextButton(onClick = {
                            scope.launch {
                                withContext(Dispatchers.IO) { draftStore.clear(file.uri) }
                                draftRecovery = false
                            }
                        }) {
                            Text("Discard")
                        }
                    }
                }
            }
            DocxPage(
                file = file,
                preview = preview,
                editing = editing,
                focusedTarget = focusedTarget,
                onFocusTarget = { target -> focusedTarget = target },
                onStartEditingParagraph = { target ->
                    editing = true
                    focusedTarget = target
                },
                onSelectionChange = { sel ->
                    focusedTarget?.let { selections[it] = sel }
                },
                onAddParagraphAtEnd = ::appendParagraph,
                onTextChange = { path, text ->
                    val readyState = previewState.value as? DocxPreview.Ready
                    val current = readyState?.document?.paragraphAt(path)?.text()
                    if (current != text) {
                        val coalesceKey = "text:${path.blockIndex}:${path.cellRow}:${path.cellColumn}:${path.cellParagraph}"
                        updateDocument(coalesce = coalesceKey) { doc ->
                            doc.mapParagraphAt(path) { p -> p.applyText(text) }
                        }
                    }
                },
                onSplitParagraph = ::splitParagraph,
                findQuery = if (findOpen) findQuery else "",
            )
        }
    }
}
