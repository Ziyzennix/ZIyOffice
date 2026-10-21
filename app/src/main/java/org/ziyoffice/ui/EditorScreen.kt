package org.ziyoffice.ui

import android.content.Intent
import android.net.Uri
import android.graphics.BitmapFactory
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.FormatStrikethrough
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import org.ziyoffice.model.placedCells
import org.ziyoffice.model.withParagraphRemovedAt
import org.ziyoffice.model.withParagraphAddedAfter
import org.ziyoffice.model.DocumentRegion
import org.ziyoffice.model.FocusPath
import org.ziyoffice.model.mapParagraphAt
import org.ziyoffice.model.formatRange
import org.ziyoffice.model.applyText
import org.ziyoffice.model.findMatches
import org.ziyoffice.model.replaceAll
import org.ziyoffice.model.paragraphAt
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
import org.ziyoffice.model.Run
import org.ziyoffice.model.Table
import org.ziyoffice.ooxml.ContentAudit
import org.ziyoffice.saf.SafeWrite
import org.ziyoffice.ooxml.PackageKind
import org.ziyoffice.ooxml.DocxEditor
import org.ziyoffice.ooxml.DocxSource
import org.ziyoffice.ooxml.DocxReader
import org.ziyoffice.ooxml.DocxWriter
import org.ziyoffice.ooxml.PreservedFeature

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

/**
 * A writing preset, what other office suites call a paragraph style. Applying
 * one rewrites the runs and spacing directly (fresh package, direct formatting).
 */
private data class ParagraphPreset(
    val label: String,
    val shortLabel: String,
    val sizePt: Float,
    val bold: Boolean,
    val spaceBeforeTwips: Int?,
    val spaceAfterTwips: Int,
)

private val PARAGRAPH_PRESETS = listOf(
    ParagraphPreset("Title", "Title", 24f, bold = true, spaceBeforeTwips = null, spaceAfterTwips = 240),
    ParagraphPreset("Heading 1", "H1", 18f, bold = true, spaceBeforeTwips = 240, spaceAfterTwips = 160),
    ParagraphPreset("Heading 2", "H2", 14f, bold = true, spaceBeforeTwips = 200, spaceAfterTwips = 120),
    ParagraphPreset("Heading 3", "H3", 12f, bold = true, spaceBeforeTwips = 160, spaceAfterTwips = 100),
    ParagraphPreset("Body", "Body", 11f, bold = false, spaceBeforeTwips = null, spaceAfterTwips = 160),
)

private val BODY_PRESET = PARAGRAPH_PRESETS.last()

/** Which preset a paragraph currently matches (first run's size and weight). */
private fun Paragraph.matchingPreset(): ParagraphPreset {
    val first = runs.firstOrNull() ?: return BODY_PRESET
    return PARAGRAPH_PRESETS.firstOrNull {
        it.sizePt == first.sizePt && it.bold == first.bold
    } ?: BODY_PRESET
}

@Composable
private fun FormattingBar(
    paragraph: Paragraph?,
    onPreset: (ParagraphPreset) -> Unit,
    onBold: () -> Unit,
    onItalic: () -> Unit,
    onUnderline: () -> Unit,
    onStrike: () -> Unit,
    onSize: (Float) -> Unit,
    onColour: (Int?) -> Unit,
    onAlignment: (ParagraphAlignment) -> Unit,
    onLineSpacing: (Int?) -> Unit,
    onIndent: (Int) -> Unit,
    onSpacing: (Int, Int) -> Unit,
    onToggleList: (ListKind) -> Unit,
    onTogglePageBreak: () -> Unit,
    onDeleteParagraph: () -> Unit,
) {
    val enabled = paragraph != null
    val runs = paragraph?.runs.orEmpty()
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 4.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(0.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FormatAction(
                icon = Icons.Filled.FormatBold,
                label = "Bold",
                enabled = enabled,
                selected = runs.isNotEmpty() && runs.all { it.bold },
                onClick = onBold,
            )
            FormatAction(
                icon = Icons.Filled.FormatItalic,
                label = "Italic",
                enabled = enabled,
                selected = runs.isNotEmpty() && runs.all { it.italic },
                onClick = onItalic,
            )
            FormatAction(
                icon = Icons.Filled.FormatUnderlined,
                label = "Underline",
                enabled = enabled,
                selected = runs.isNotEmpty() && runs.all { it.underline },
                onClick = onUnderline,
            )
            FormatAction(
                icon = Icons.Filled.FormatStrikethrough,
                label = "Strikethrough",
                enabled = enabled,
                selected = runs.isNotEmpty() && runs.all { it.strike },
                onClick = onStrike,
            )
            SizeMenu(
                currentSizePt = paragraph?.runs?.firstOrNull()?.sizePt,
                enabled = enabled,
                onSize = onSize,
            )
            ColourMenu(
                currentColourRgb = paragraph?.runs?.firstOrNull()?.colorRgb,
                enabled = enabled,
                onColour = onColour,
            )
            DocMoreMenu(
                paragraph = paragraph,
                enabled = enabled,
                onPreset = onPreset,
                onLineSpacing = onLineSpacing,
                onIndent = onIndent,
                onSpacing = onSpacing,
                onToggleList = onToggleList,
                onAlignment = onAlignment,
                onTogglePageBreak = onTogglePageBreak,
                onDeleteParagraph = onDeleteParagraph,
            )
        }
    }
}

/** Overflow for the format bar: list style, alignment and paragraph actions in one sheet. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun DocMoreMenu(
    paragraph: Paragraph?,
    enabled: Boolean,
    onPreset: (ParagraphPreset) -> Unit,
    onLineSpacing: (Int?) -> Unit,
    onIndent: (Int) -> Unit,
    onSpacing: (Int, Int) -> Unit,
    onToggleList: (ListKind) -> Unit,
    onAlignment: (ParagraphAlignment) -> Unit,
    onTogglePageBreak: () -> Unit,
    onDeleteParagraph: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }, enabled = enabled) {
        Icon(
            imageVector = Icons.Filled.MoreVert,
            contentDescription = "More formatting",
            tint = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
        )
    }
    if (open) {
        ModalBottomSheet(onDismissRequest = { open = false }) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Paragraph style", style = MaterialTheme.typography.titleSmall)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val current = paragraph?.matchingPreset() ?: BODY_PRESET
                        PARAGRAPH_PRESETS.forEach { preset ->
                            FilterChip(
                                selected = preset == current,
                                onClick = {
                                    open = false
                                    onPreset(preset)
                                },
                                label = { Text(preset.shortLabel) },
                            )
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Line spacing", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            100 to "Single",
                            115 to "1.15",
                            150 to "1.5",
                            200 to "Double",
                        ).forEach { (percent, label) ->
                            FilterChip(
                                selected = paragraph?.lineSpacingPercent == percent,
                                onClick = {
                                    open = false
                                    onLineSpacing(percent)
                                },
                                label = { Text(label) },
                            )
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Indent", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = false,
                            onClick = {
                                open = false
                                onIndent(-INDENT_STEP_TWIPS)
                            },
                            label = { Text("Decrease indent") },
                        )
                        FilterChip(
                            selected = false,
                            onClick = {
                                open = false
                                onIndent(INDENT_STEP_TWIPS)
                            },
                            label = { Text("Increase indent") },
                        )
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Paragraph spacing", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PARAGRAPH_SPACINGS.forEach { (label, before, after) ->
                            FilterChip(
                                selected = paragraph?.spacingBeforeTwips == before &&
                                    paragraph?.spacingAfterTwips == after,
                                onClick = {
                                    open = false
                                    onSpacing(before, after)
                                },
                                label = { Text(label) },
                            )
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("List style", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            ListKind.BULLET to "Bulleted",
                            ListKind.NUMBER to "Numbered",
                        ).forEach { (kind, label) ->
                            FilterChip(
                                selected = paragraph?.list?.kind == kind,
                                onClick = {
                                    open = false
                                    onToggleList(kind)
                                },
                                label = { Text(label) },
                            )
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Alignment", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            ParagraphAlignment.START to "Left",
                            ParagraphAlignment.CENTER to "Centre",
                            ParagraphAlignment.END to "Right",
                            ParagraphAlignment.JUSTIFY to "Justify",
                        ).forEach { (alignment, label) ->
                            FilterChip(
                                selected = paragraph?.alignment == alignment,
                                onClick = {
                                    open = false
                                    onAlignment(alignment)
                                },
                                label = { Text(label) },
                            )
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Paragraph", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                open = false
                                onTogglePageBreak()
                            },
                        ) { Text("Page break before") }
                        OutlinedButton(
                            onClick = {
                                open = false
                                onDeleteParagraph()
                            },
                        ) { Text("Delete") }
                    }
                }
            }
        }
    }
}

@Composable
private fun FormatAction(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val isSelected = selected
    IconButton(
        onClick = onClick,
        enabled = enabled,
        // Bold/Italic/Underline/Strikethrough showed their state as a tint only. It is spoken now.
        modifier = Modifier.semantics {
            contentDescription = label
            stateDescription = if (isSelected) "On" else "Off"
        },
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = when {
                !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                isSelected -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
private fun AccessLine(access: FileAccess) {
    val text = when (access) {
        FileAccess.Checking -> "Checking file access\u2026"
        is FileAccess.Ok -> "File access verified \u2014 ${formatSize(access.sizeBytes)} readable on-device."
        is FileAccess.Failed -> ReadFailureCopy.describe(access.reason)
    }
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private sealed interface DocxPreview {
    data object Loading : DocxPreview
    data class Ready(
        val document: Document,
        val images: Map<InlineImage, ImageBitmap?>,
        /**
         * Content a rebuild-from-model save drops, kept so a restored draft
         * still warns about the source file rather than the draft.
         */
        val dropped: List<PreservedFeature> = emptyList(),
        /**
         * The package the document was opened from. Saving writes the edited
         * document back into it, so what the editor cannot edit (comments,
         * footnotes, tracked changes, hyperlinks, styles, section breaks)
         * survives the save.
         */
        val source: DocxSource? = null,
    ) : DocxPreview
    data class Failed(val reason: String) : DocxPreview
}

@Composable
private fun rememberDocxPreviewState(file: RecentFile): MutableState<DocxPreview> {
    val context = LocalContext.current
    val state = remember(file.uri) { mutableStateOf<DocxPreview>(DocxPreview.Loading) }
    LaunchedEffect(file.uri) {
        if (file.kind != DocumentKind.DOCUMENT) return@LaunchedEffect
        state.value = withContext(Dispatchers.IO) {
            runCatching {
                val stream = context.contentResolver.openInputStream(Uri.parse(file.uri))
                    ?: error("the file could not be opened")
                val bytes = stream.use { it.readBytes() }
                // The audit reads the same bytes the reader just consumed, so the
                // warning describes exactly this file and the provider is read once.
                val dropped = ContentAudit.docx(bytes)
                val source = DocxReader().readSource(ByteArrayInputStream(bytes))
                val document = source.document
                val bitmaps = document.blocks
                    .flatMap(::collectImages)
                    .distinct()
                    .associateWith { image -> decodeImage(image.bytes) }
                DocxPreview.Ready(document, bitmaps, dropped, source)
            }.fold(
                onSuccess = { it },
                onFailure = { DocxPreview.Failed(it.message ?: it.javaClass.simpleName) },
            )
        }
    }
    return state
}

@Composable
private fun DocxPage(
    file: RecentFile,
    preview: DocxPreview,
    editing: Boolean,
    focusedTarget: FocusPath?,
    onFocusTarget: (FocusPath) -> Unit,
    onStartEditingParagraph: (FocusPath) -> Unit,
    onSelectionChange: (TextRange) -> Unit,
    onTextChange: (FocusPath, String) -> Unit,
    onSplitParagraph: (FocusPath, String, String) -> Unit,
    onAddParagraphAtEnd: () -> Unit,
    findQuery: String = "",
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFFFAFAF7),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                file.displayName,
                style = MaterialTheme.typography.titleMedium,
                color = Color(0xFF1A1C1B),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            when (preview) {
                DocxPreview.Loading -> Text(
                    "Reading the document\u2026",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF5A605C),
                )
                is DocxPreview.Failed -> {
                    Text(
                        "Could not read this document.",
                        style = MaterialTheme.typography.titleSmall,
                        color = Color(0xFF8A3F3F),
                    )
                    Text(
                        ReadFailureCopy.describe(preview.reason),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF5A605C),
                    )
                }
                is DocxPreview.Ready -> {
                    Text(
                        "${countWords(preview.document.blocks)} words \u00B7 " +
                            "${countParagraphs(preview.document.blocks)} paragraphs",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF8A8F8B),
                    )
                    preview.document.header?.takeIf { it.isNotEmpty() }?.let { header ->
                        HeaderFooterBand(
                            label = "Header",
                            region = DocumentRegion.HEADER,
                            blocks = header,
                            bitmaps = preview.images,
                            editing = editing,
                            focusedTarget = focusedTarget,
                            onFocusTarget = onFocusTarget,
                            onTextChange = onTextChange,
                            onSelectionChange = onSelectionChange,
                            onSplitParagraph = onSplitParagraph,
                            findQuery = findQuery,
                        )
                    }
                    if (preview.document.blocks.isEmpty()) {
                        Text(
                            "This document has no text paragraphs yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF5A605C),
                        )
                    } else {
                        val items = remember(preview.document.blocks, preview.images, findQuery) {
                            preview.document.blocks.toDisplayItems(preview.images, searchQuery = findQuery)
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                            items.forEachIndexed { index, item ->
                                when (item) {
                                    is DisplayItem.Line -> {
                                        if (item.line.pageBreakBefore) PageBreakDivider()
                                        val paragraph = preview.document.blocks
                                            .getOrNull(index) as? Paragraph
                                        if (editing && paragraph != null) {
                                            val path = FocusPath(index)
                                            EditableParagraph(
                                                paragraph = paragraph,
                                                line = item.line,
                                                isFocused = focusedTarget == path,
                                                onFocus = { onFocusTarget(path) },
                                                onTextChange = { text ->
                                                    onTextChange(path, text)
                                                },
                                                onSelectionChange = onSelectionChange,
                                                onSplitParagraph = { before, after ->
                                                    onSplitParagraph(path, before, after)
                                                },
                                                findQuery = findQuery,
                                            )
                                        } else {
                                            // Tapping text starts writing right there,
                                            // no mode hunting and no menu (like WPS).
                                            ParagraphLine(
                                                line = item.line,
                                                onClick = { onStartEditingParagraph(FocusPath(index)) },
                                            )
                                        }
                                    }
                                    is DisplayItem.Grid ->
                                        TableBlock(
                                            table = item.table,
                                            tableBlockIndex = index,
                                            bitmaps = preview.images,
                                            editing = editing,
                                            focusedTarget = focusedTarget,
                                            onFocusTarget = onFocusTarget,
                                            onTextChange = onTextChange,
                                            onSelectionChange = onSelectionChange,
                                            onSplitParagraph = onSplitParagraph,
                                            findQuery = findQuery,
                                        )
                                }
                            }
                            if (editing) {
                                AddParagraphButton(onClick = onAddParagraphAtEnd)
                            }
                        }
                    }
                    preview.document.footer?.takeIf { it.isNotEmpty() }?.let { footer ->
                        HeaderFooterBand(
                            label = "Footer",
                            region = DocumentRegion.FOOTER,
                            blocks = footer,
                            bitmaps = preview.images,
                            editing = editing,
                            focusedTarget = focusedTarget,
                            onFocusTarget = onFocusTarget,
                            onTextChange = onTextChange,
                            onSelectionChange = onSelectionChange,
                            onSplitParagraph = onSplitParagraph,
                            findQuery = findQuery,
                        )
                    }
                }
            }
        }
    }
}

/**
 * An editable paragraph: the text field keeps its own value and cursor state
 * and pushes changes into the model. Formatting past the first run is simple:
 * the paragraph is re-styled from its first run, and the toolbar restyles it.
 */
@Composable
private fun EditableParagraph(
    paragraph: Paragraph,
    line: DisplayLine,
    isFocused: Boolean,
    onFocus: () -> Unit,
    onTextChange: (String) -> Unit,
    onSelectionChange: (TextRange) -> Unit,
    onSplitParagraph: (before: String, after: String) -> Unit,
    findQuery: String = "",
) {
    val reference = paragraph.runs.firstOrNull()
    val textStyle = lineTextStyle(line).copy(
        fontWeight = if (reference?.bold == true) FontWeight.Bold else null,
        fontStyle = if (reference?.italic == true) FontStyle.Italic else null,
        textDecoration = listOfNotNull(
            if (reference?.underline == true) TextDecoration.Underline else null,
            if (reference?.strike == true) TextDecoration.LineThrough else null,
        ).takeIf { it.isNotEmpty() }?.reduce { a, b -> a + b },
        fontSize = reference?.sizePt?.sp ?: 11.sp,
        color = reference?.colorRgb?.let { Color(0xFF000000.toInt() or it) } ?: DocumentInk,
        textAlign = line.alignment,
    )
    val currentText = paragraph.text()
    var field by remember { mutableStateOf(TextFieldValue(currentText)) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(currentText) {
        if (field.text != currentText) {
            field = TextFieldValue(currentText, selection = TextRange(0))
        }
    }
    LaunchedEffect(isFocused) {
        // Covers programmatic focus (a paragraph created by pressing Enter).
        if (isFocused) focusRequester.requestFocus()
    }
    val visualTransformation = remember(findQuery) {
        if (findQuery.isNotEmpty()) {
            VisualTransformation { text ->
                val annotated = buildAnnotatedString {
                    append(text.text)
                    var idx = text.text.indexOf(findQuery, ignoreCase = true)
                    while (idx >= 0) {
                        addStyle(
                            SpanStyle(
                                background = Color(0xFFFFF176),
                                color = Color(0xFF1A1C1B),
                            ),
                            idx,
                            idx + findQuery.length,
                        )
                        idx = text.text.indexOf(findQuery, idx + findQuery.length, ignoreCase = true)
                    }
                }
                TransformedText(annotated, OffsetMapping.Identity)
            }
        } else {
            VisualTransformation.None
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = line.indentDp.dp,
                top = line.spacingBeforeDp.dp,
                bottom = line.spacingAfterDp.dp,
            ),
    ) {
        if (line.marker != null) {
            Text(
                text = line.marker,
                style = DocumentTextStyle,
                color = DocumentInk,
                // A minimum, not a fixed width: list numbers must not clip at a large font scale.
                modifier = Modifier.widthIn(min = 22.dp).padding(end = 2.dp),
            )
        }
        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
            BasicTextField(
                value = field,
                onValueChange = { value ->
                    if (value.text.contains('\n')) {
                        // Enter starts a new paragraph; the split happens at the
                        // newline, like every editor.
                        val before = value.text.substringBefore('\n')
                        val after = value.text.substringAfter('\n')
                        field = TextFieldValue(before, selection = TextRange(before.length))
                        onSplitParagraph(before, after)
                    } else {
                        field = value
                        onTextChange(value.text)
                        onSelectionChange(value.selection)
                    }
                },
                textStyle = textStyle,
                visualTransformation = visualTransformation,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .focusRequester(focusRequester)
                    .fillMaxWidth()
                    .background(
                        color = if (isFocused) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)
                        } else {
                            Color.Transparent
                        },
                        shape = RoundedCornerShape(4.dp),
                    )
                    .padding(2.dp)
                    .onFocusChanged { focusState ->
                        if (focusState.isFocused) onFocus()
                    },
            )
            InlineImages(line.images)
        }
    }
}

private sealed interface FileAccess {
    data object Checking : FileAccess
    data class Ok(val sizeBytes: Long) : FileAccess
    data class Failed(val reason: String) : FileAccess
}

@Composable
private fun rememberFileAccess(uriString: String): FileAccess {
    val context = LocalContext.current
    var access by remember(uriString) { mutableStateOf<FileAccess>(FileAccess.Checking) }
    LaunchedEffect(uriString) {
        access = withContext(Dispatchers.IO) {
            runCatching {
                val uri = Uri.parse(uriString)
                val header = ByteArray(8)
                val bytesRead = context.contentResolver.openInputStream(uri)?.use { it.read(header) } ?: -1
                val size = context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: -1L
                if (bytesRead > 0) FileAccess.Ok(size) else FileAccess.Failed("empty or unavailable")
            }.getOrElse { error ->
                FileAccess.Failed(error.message ?: error.javaClass.simpleName)
            }
        }
    }
    return access
}

private fun metaLine(file: RecentFile): String =
    listOfNotNull(
        file.kind.label,
        formatSize(file.sizeBytes).ifEmpty { null },
    ).joinToString(" \u00B7 ")

private val DocumentInk = Color(0xFF23262B)

private fun Paragraph.text(): String = runs.joinToString("") { it.text }

private fun nextFreeNumId(document: Document): Int {
    fun visit(blocks: List<Block>): List<Int> = blocks.flatMap { block ->
        when (block) {
            is Paragraph -> listOfNotNull(block.list?.numId)
            is Table -> block.rows.flatMap { row ->
                row.cells.flatMap { cell -> visit(cell.blocks) }
            }
        }
    }
    return (visit(document.blocks).maxOrNull() ?: 0) + 1
}

private fun Paragraph.annotatedText() = buildAnnotatedString {
    if (runs.isEmpty()) {
        append("\u00A0")
        return@buildAnnotatedString
    }
    runs.forEach { run ->
        withStyle(
            SpanStyle(
                color = run.colorRgb?.let { Color(0xFF000000.toInt() or it) } ?: Color.Unspecified,
                fontSize = run.sizePt?.sp ?: TextUnit.Unspecified,
                fontWeight = if (run.bold) FontWeight.Bold else null,
                fontStyle = if (run.italic) FontStyle.Italic else null,
                textDecoration = listOfNotNull(
                    if (run.underline) TextDecoration.Underline else null,
                    if (run.strike) TextDecoration.LineThrough else null,
                ).takeIf { it.isNotEmpty() }?.reduce { a, b -> a + b },
            ),
        ) {
            append(run.text)
        }
    }
}

private fun ParagraphAlignment.toTextAlign(): TextAlign = when (this) {
    ParagraphAlignment.START -> TextAlign.Start
    ParagraphAlignment.CENTER -> TextAlign.Center
    ParagraphAlignment.END -> TextAlign.End
    ParagraphAlignment.JUSTIFY -> TextAlign.Justify
}

/**
 * One rendered paragraph: list marker, start padding, spacing and styled
 * text. Markers get concrete labels, so a document renders the same way twice.
 */
private class DisplayLine(
    val marker: String?,
    val indentDp: Int,
    val text: AnnotatedString,
    val alignment: TextAlign,
    /** Decoded images, null when the format is not supported. */
    val images: List<ImageBitmap?> = emptyList(),
    val spacingBeforeDp: Int = 0,
    val spacingAfterDp: Int = 0,
    /** Line height inside the paragraph, as a percentage (100 = single). */
    val lineSpacingPercent: Int? = null,
    val pageBreakBefore: Boolean = false,
)

private sealed interface DisplayItem {
    data class Line(val line: DisplayLine) : DisplayItem
    data class Grid(val table: Table) : DisplayItem
}

private fun List<Block>.toDisplayItems(
    bitmaps: Map<InlineImage, ImageBitmap?>,
    searchQuery: String = "",
): List<DisplayItem> {
    val counters = mutableMapOf<Pair<Int, Int>, Int>()
    return map { block ->
        when (block) {
            is Paragraph -> DisplayItem.Line(block.toDisplayLine(counters, bitmaps, searchQuery = searchQuery))
            is Table -> DisplayItem.Grid(block)
        }
    }
}

private fun Paragraph.toDisplayLine(
    counters: MutableMap<Pair<Int, Int>, Int>,
    bitmaps: Map<InlineImage, ImageBitmap?> = emptyMap(),
    defaultSpacingTwips: Int = DEFAULT_PARAGRAPH_SPACING_TWIPS,
    searchQuery: String = "",
): DisplayLine {
    val listInfo = list
    val indentTwips = indentTwips
        ?: listInfo?.let { DEFAULT_LIST_INDENT_TWIPS * (it.level + 1) }
        ?: 0
    val baseText = annotatedText()
    val highlightedText = if (searchQuery.isNotEmpty()) {
        buildAnnotatedString {
            append(baseText)
            val raw = baseText.text
            var idx = raw.indexOf(searchQuery, ignoreCase = true)
            while (idx >= 0) {
                addStyle(
                    SpanStyle(
                        background = Color(0xFFFFF176),
                        color = Color(0xFF1A1C1B),
                    ),
                    idx,
                    idx + searchQuery.length,
                )
                idx = raw.indexOf(searchQuery, idx + searchQuery.length, ignoreCase = true)
            }
        }
    } else {
        baseText
    }
    return DisplayLine(
        marker = listInfo?.let { listMarker(it, counters) },
        indentDp = indentTwips / TWIPS_PER_DP,
        text = highlightedText,
        alignment = alignment.toTextAlign(),
        images = images.map { bitmaps[it] },
        spacingBeforeDp = (spacingBeforeTwips ?: 0) / TWIPS_PER_DP,
        spacingAfterDp = (spacingAfterTwips ?: defaultSpacingTwips) / TWIPS_PER_DP,
        lineSpacingPercent = lineSpacingPercent,
        pageBreakBefore = pageBreakBefore,
    )
}

/** All inline images of a block, including the ones inside table cells. */
private fun collectImages(block: Block): List<InlineImage> = when (block) {
    is Paragraph -> block.images
    is Table -> block.rows.flatMap { row ->
        row.cells.flatMap { cell -> cell.blocks.flatMap(::collectImages) }
    }
}

private fun countParagraphs(blocks: List<Block>): Int = blocks.sumOf { block ->
    when (block) {
        is Paragraph -> 1
        is Table -> block.rows.sumOf { row -> row.cells.sumOf { cell -> countParagraphs(cell.blocks) } }
    }
}

/** Word count for the document header. Words, not paragraphs. */
private fun countWords(blocks: List<Block>): Int = blocks.sumOf { block ->
    when (block) {
        is Paragraph -> block.text().split(' ', '\n', '\t').count { it.isNotBlank() }
        is Table -> block.rows.sumOf { row -> row.cells.sumOf { cell -> countWords(cell.blocks) } }
    }
}

/** "report.docx" -> "report.pdf" for the export dialog's suggested name. */
private fun pdfFileName(displayName: String): String {
    return if (displayName.endsWith(".pdf", ignoreCase = true)) {
        displayName
    } else {
        val base = displayName.substringBeforeLast('.', displayName)
        "$base.pdf"
    }
}

@Composable
private fun ParagraphLine(line: DisplayLine, onClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(
                start = line.indentDp.dp,
                top = line.spacingBeforeDp.dp,
                bottom = line.spacingAfterDp.dp,
            ),
    ) {
        if (line.marker != null) {
            Text(
                text = line.marker,
                style = DocumentTextStyle,
                color = DocumentInk,
                // A minimum, not a fixed width: list numbers must not clip at a large font scale.
                modifier = Modifier.widthIn(min = 22.dp).padding(end = 2.dp),
            )
        }
        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
            Text(
                text = line.text,
                style = lineTextStyle(line),
                color = DocumentInk,
                textAlign = line.alignment,
                modifier = Modifier.fillMaxWidth(),
            )
            InlineImages(line.images)
        }
    }
}

@Composable
private fun PageBreakDivider() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f), color = GridLineColor)
        Text(
            "  NEW PAGE  ",
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF8A8F8B),
        )
        HorizontalDivider(modifier = Modifier.weight(1f), color = GridLineColor)
    }
}

@Composable
private fun AddParagraphButton(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .border(BorderStroke(1.dp, GridLineColor), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Add,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "Add paragraph",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun InlineImages(images: List<ImageBitmap?>) {
    images.forEach { image ->
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .padding(top = 6.dp)
                    .widthIn(max = 320.dp)
                    .heightIn(max = 400.dp),
            )
        } else {
            Box(
                modifier = Modifier
                    .padding(top = 6.dp)
                    .border(BorderStroke(1.dp, GridLineColor), RectangleShape)
                    .padding(8.dp),
            ) {
                Text(
                    "This image format is not supported yet",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF8A8F8B),
                )
            }
        }
    }
}

private val DocumentTextStyle: TextStyle
    @Composable get() = MaterialTheme.typography.bodyMedium.copy(lineHeight = 16.sp)

/**
 * The document text style with the paragraph's line spacing applied: base
 * line height scaled by the percentage (null means the base).
 */
@Composable
private fun lineTextStyle(line: DisplayLine): TextStyle {
    val base = DocumentTextStyle
    val percent = line.lineSpacingPercent ?: return base
    return base.copy(lineHeight = (base.lineHeight.value * percent / 100f).sp)
}

private val GridLineColor = Color(0xFFC9CDC8)

@Composable
private fun TableBlock(
    table: Table,
    tableBlockIndex: Int,
    bitmaps: Map<InlineImage, ImageBitmap?>,
    editing: Boolean,
    focusedTarget: FocusPath?,
    onFocusTarget: (FocusPath) -> Unit,
    onTextChange: (FocusPath, String) -> Unit,
    onSelectionChange: (TextRange) -> Unit,
    onSplitParagraph: (FocusPath, String, String) -> Unit,
    findQuery: String = "",
) {
    val placed = remember(table) { table.placedCells() }
    // Written during layout, read by the draw phase that always runs after.
    // Plain fields, not state: a state change inside measure schedules a
    // second composition for every table layout.
    val geometry = remember(table) { CellGeometry() }
    val lineColor = GridLineColor
    val lineWidth = with(LocalDensity.current) { 1.dp.toPx() }

    Layout(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .drawBehind {
                if (table.borders) drawGridLines(geometry.rects, lineColor, lineWidth)
            },
        content = {
            placed.forEach { item ->
                Box(modifier = Modifier.padding(CELL_HORIZONTAL, CELL_VERTICAL)) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        item.cell.blocks.forEachIndexed { blockIndex, block ->
                            when (block) {
                                is Paragraph -> {
                                    // The path keeps the model's own row and cell indexes
                                    // rather than the grid ones, so an edit lands in its
                                    // own cell even when merges shift the columns.
                                    val path = FocusPath(
                                        blockIndex = tableBlockIndex,
                                        cellRow = item.rowIndex,
                                        cellColumn = item.cellIndex,
                                        cellParagraph = blockIndex,
                                    )
                                    val displayLine = block.toDisplayLine(
                                        counters = mutableMapOf(),
                                        bitmaps = bitmaps,
                                        defaultSpacingTwips = 0,
                                        searchQuery = findQuery,
                                    )
                                    if (editing) {
                                        EditableParagraph(
                                            paragraph = block,
                                            line = displayLine,
                                            isFocused = focusedTarget == path,
                                            onFocus = { onFocusTarget(path) },
                                            onTextChange = { text -> onTextChange(path, text) },
                                            onSelectionChange = onSelectionChange,
                                            onSplitParagraph = { before, after ->
                                                onSplitParagraph(path, before, after)
                                            },
                                            findQuery = findQuery,
                                        )
                                    } else {
                                        ParagraphLine(
                                            line = displayLine,
                                            onClick = { onFocusTarget(path) },
                                        )
                                    }
                                }
                                is Table -> TableBlock(
                                    table = block,
                                    tableBlockIndex = -1,
                                    bitmaps = bitmaps,
                                    editing = false,
                                    focusedTarget = null,
                                    onFocusTarget = {},
                                    onTextChange = { _, _ -> },
                                    onSelectionChange = {},
                                    onSplitParagraph = { _, _, _ -> },
                                )
                            }
                        }
                    }
                }
            }
        },
    ) { measurables, constraints ->
        val columns = table.columnCount.coerceAtLeast(1)
        val width = constraints.maxWidth

        // Equal shares per grid column, with the remainder given to the last
        // column so the widths always sum to the available width exactly.
        val base = width / columns
        val columnWidths = IntArray(columns) { base }
        columnWidths[columns - 1] = width - base * (columns - 1)

        val columnLefts = IntArray(columns)
        var running = 0
        columnWidths.forEachIndexed { index, w ->
            columnLefts[index] = running
            running += w
        }

        val cellWidths = placed.map { item ->
            val firstColumn = item.column.coerceIn(0, columns - 1)
            val lastColumn = (item.column + item.columnSpan.coerceAtLeast(1) - 1)
                .coerceIn(0, columns - 1)
            columnLefts[lastColumn] + columnWidths[lastColumn] - columnLefts[firstColumn]
        }

        // A cell is measured exactly once, at its final width, or the text wraps differently.
        val measured = placed.mapIndexed { index, _ ->
            val cellWidth = cellWidths[index].coerceAtLeast(1)
            measurables[index].measure(
                Constraints(minWidth = cellWidth, maxWidth = cellWidth),
            )
        }

        val rowHeights = IntArray(table.rows.size)
        measured.forEachIndexed { index, placeable ->
            val item = placed[index]
            // The measured box carries its own padding, so its height is the row's whole height.
            val needed = placeable.height.coerceAtLeast(1)
            val last = (item.rowIndex + item.rowSpan).coerceAtMost(rowHeights.size)
            if (last <= item.rowIndex) return@forEachIndexed
            if (item.rowSpan <= 1) {
                rowHeights[item.rowIndex] = maxOf(rowHeights[item.rowIndex], needed)
            } else {
                // A vertically merged cell must fit the rows it covers, so spread
                // its height over them.
                val rowsCovered = last - item.rowIndex
                val shared = (needed + rowsCovered - 1) / rowsCovered
                for (r in item.rowIndex until last) {
                    rowHeights[r] = maxOf(rowHeights[r], shared)
                }
            }
        }

        val rowTops = IntArray(rowHeights.size)
        var y = 0
        rowHeights.forEachIndexed { index, h ->
            rowTops[index] = y
            y += h
        }
        val totalHeight = maxOf(y, 1)

        val rects = placed.map { item ->
            val firstColumn = item.column.coerceIn(0, columns - 1)
            val lastColumn = (item.column + item.columnSpan - 1).coerceIn(0, columns - 1)
            val firstRow = item.rowIndex.coerceIn(0, rowHeights.size - 1)
            val lastRow = (item.rowIndex + item.rowSpan - 1).coerceIn(0, rowHeights.size - 1)
            CellRect(
                left = columnLefts[firstColumn],
                top = rowTops[firstRow],
                right = columnLefts[lastColumn] + columnWidths[lastColumn],
                bottom = rowTops[lastRow] + rowHeights[lastRow],
            )
        }
        geometry.rects = rects

        layout(width, totalHeight) {
            measured.forEachIndexed { index, placeable ->
                val rect = rects[index]
                placeable.placeRelative(rect.left, rect.top)
            }
        }
    }
}

/** A cell's rectangle inside the rendered grid, in pixels. */
private data class CellRect(val left: Int, val top: Int, val right: Int, val bottom: Int)

/** The rectangles the last layout produced, for the grid to draw. */
private class CellGeometry {
    var rects: List<CellRect> = emptyList()
}

/**
 * Draws the table grid from the rectangles the layout produced, instead of
 * giving every cell its own border. A spanning cell contributes only its real
 * edges, so no rule runs through its middle and neighbours share one line.
 */
private fun DrawScope.drawGridLines(rects: List<CellRect>, color: Color, thickness: Float) {
    if (rects.isEmpty()) return
    // Inset by half the stroke so the outer rules stay inside the table's bounds.
    val inset = thickness / 2
    rects.forEach { rect ->
        val left = rect.left.toFloat() + inset
        val right = rect.right.toFloat() - inset
        val top = rect.top.toFloat() + inset
        val bottom = rect.bottom.toFloat() - inset
        if (right >= left) {
            drawLine(color, Offset(left, top), Offset(right, top), thickness)
            drawLine(color, Offset(left, bottom), Offset(right, bottom), thickness)
        }
        if (bottom >= top) {
            drawLine(color, Offset(left, top), Offset(left, bottom), thickness)
            drawLine(color, Offset(right, top), Offset(right, bottom), thickness)
        }
    }
}

private val CELL_HORIZONTAL = 8.dp
private val CELL_VERTICAL = 6.dp

/**
 * A header or footer shown as a labelled band above/below the body. Read-only
 * on purpose: Word keeps them in separate package parts with their own
 * section rules, so editing them from the body flow would write text into
 * the wrong part. The band makes them visible instead of dropping them on
 * the next save, which is what used to happen.
 */
@Composable
private fun HeaderFooterBand(
    label: String,
    region: DocumentRegion,
    blocks: List<Block>,
    bitmaps: Map<InlineImage, ImageBitmap?>,
    editing: Boolean,
    focusedTarget: FocusPath?,
    onFocusTarget: (FocusPath) -> Unit,
    onTextChange: (FocusPath, String) -> Unit,
    onSelectionChange: (TextRange) -> Unit,
    onSplitParagraph: (FocusPath, String, String) -> Unit,
    findQuery: String = "",
) {
    val counters = remember(blocks) { mutableMapOf<Pair<Int, Int>, Int>() }
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFFF1F1EC),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(
                label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF8A8F8B),
            )
            blocks.forEachIndexed { index, block ->
                when (block) {
                    is Paragraph -> {
                        val path = FocusPath(index, region = region)
                        val line = block.toDisplayLine(
                            counters = counters,
                            bitmaps = bitmaps,
                            defaultSpacingTwips = 0,
                            searchQuery = findQuery,
                        )
                        if (editing) {
                            EditableParagraph(
                                paragraph = block,
                                line = line,
                                isFocused = focusedTarget == path,
                                onFocus = { onFocusTarget(path) },
                                onTextChange = { text -> onTextChange(path, text) },
                                onSelectionChange = onSelectionChange,
                                onSplitParagraph = { before, after ->
                                    onSplitParagraph(path, before, after)
                                },
                                findQuery = findQuery,
                            )
                        } else {
                            ParagraphLine(line = line, onClick = { onFocusTarget(path) })
                        }
                    }
                    is Table -> Text(
                        "Table in $label \u2014 not shown here",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF5A605C),
                    )
                }
            }
        }
    }
}

private const val TWIPS_PER_DP = 15

/** One indent step, a quarter of an inch. */
private const val INDENT_STEP_TWIPS = 360

/** The space above and below a paragraph, in twips. */
private val PARAGRAPH_SPACINGS: List<Triple<String, Int, Int>> = listOf(
    Triple("Tight", 0, 0),
    Triple("Normal", 120, 120),
    Triple("Relaxed", 240, 240),
)

/** A typical list indent (0.5 inch) for lists that do not set their own. */
private const val DEFAULT_LIST_INDENT_TWIPS = 720

/** Space after a paragraph when the document does not specify one (8 pt). */
private const val DEFAULT_PARAGRAPH_SPACING_TWIPS = 160

/** Depth of the in-memory undo history, in document snapshots. */
private const val UNDO_LIMIT = 50

@Composable
private fun InsertTableDialog(
    onDismiss: () -> Unit,
    onInsert: (rows: Int, columns: Int) -> Unit,
) {
    var rows by remember { mutableStateOf(3) }
    var columns by remember { mutableStateOf(3) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Insert table") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                StepperRow(
                    label = "Rows",
                    value = rows,
                    onValueChange = { rows = it.coerceIn(1, 8) },
                )
                StepperRow(
                    label = "Columns",
                    value = columns,
                    onValueChange = { columns = it.coerceIn(1, 8) },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onInsert(rows, columns) }) {
                Text("Insert")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun StepperRow(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = { onValueChange(value - 1) },
                enabled = value > 1,
            ) {
                Icon(Icons.Filled.Remove, contentDescription = "Decrease $label")
            }
            Text(
                "$value",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            IconButton(
                onClick = { onValueChange(value + 1) },
                enabled = value < 8,
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Increase $label")
            }
        }
    }
}
