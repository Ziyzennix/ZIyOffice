package org.ziyoffice.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.io.ByteArrayInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ziyoffice.data.DraftStore
import org.ziyoffice.model.Document
import org.ziyoffice.model.DocumentKind
import org.ziyoffice.model.FocusPath
import org.ziyoffice.model.Paragraph
import org.ziyoffice.model.RecentFile
import org.ziyoffice.model.mapParagraphAt
import org.ziyoffice.ooxml.ContentAudit
import org.ziyoffice.ooxml.DocxEditor
import org.ziyoffice.ooxml.DocxReader
import org.ziyoffice.ooxml.DocxWriter
import org.ziyoffice.ooxml.PackageKind
import org.ziyoffice.saf.SafeWrite


internal data class EditorUiState(
    val preview: DocxPreview = DocxPreview.Loading,
    val editing: Boolean = false,
    val dirty: Boolean = false,
    val saving: Boolean = false,
    val focusedTarget: FocusPath? = null,
    val draftRecovered: Boolean = false,
    val writeVersion: Long = 0L,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val lossAccepted: Boolean = false,
)

/**
 * State and business operations for document editing.
 */
internal class EditorViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private var undoStack = listOf<Document>()
    private var redoStack = listOf<Document>()
    private var coalesceKey: String? = null

    fun load(
        file: RecentFile,
        draftStore: DraftStore,
        context: Context,
        initialEditing: Boolean = false,
    ) {
        _uiState.update { it.copy(editing = initialEditing) }
        viewModelScope.launch {
            if (file.kind != DocumentKind.DOCUMENT) return@launch
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val stream = context.contentResolver.openInputStream(Uri.parse(file.uri))
                        ?: error("the file could not be opened")
                    val bytes = stream.use { it.readBytes() }
                    val dropped = ContentAudit.docx(bytes)
                    val source = DocxReader().readSource(ByteArrayInputStream(bytes))
                    val draft = draftStore.load(file.uri)
                    val document = if (draft != null) {
                        DocxReader().read(ByteArrayInputStream(draft))
                    } else {
                        source.document
                    }
                    val bitmaps = document.blocks
                        .flatMap(::collectImages)
                        .distinct()
                        .associateWith { image -> decodeImage(image.bytes) }
                    Loaded(document, bitmaps, dropped, source, draft != null)
                }
            }

            result.fold(
                onSuccess = { loaded ->
                    val onlyParagraph = loaded.document.blocks.singleOrNull() as? Paragraph
                    val initialTarget = if (initialEditing && onlyParagraph?.text().isNullOrEmpty()) {
                        FocusPath(0)
                    } else {
                        null
                    }
                    _uiState.update {
                        it.copy(
                            preview = DocxPreview.Ready(
                                document = loaded.document,
                                images = loaded.images,
                                dropped = loaded.dropped,
                                source = loaded.source,
                            ),
                            draftRecovered = loaded.recovered,
                            dirty = loaded.recovered,
                            focusedTarget = initialTarget,
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(preview = DocxPreview.Failed(error.message ?: error.javaClass.simpleName))
                    }
                },
            )
        }
    }

    fun setEditing(editing: Boolean) {
        _uiState.update { it.copy(editing = editing) }
    }

    fun setFocusedTarget(target: FocusPath?) {
        _uiState.update { it.copy(focusedTarget = target) }
    }

    fun acceptLoss() {
        _uiState.update { it.copy(lossAccepted = true) }
    }

    fun updateDocument(coalesce: String? = null, transform: (Document) -> Document) {
        val ready = _uiState.value.preview as? DocxPreview.Ready ?: return
        pushHistory(ready.document, coalesce)
        val nextDoc = transform(ready.document)
        _uiState.update {
            it.copy(
                preview = ready.copy(document = nextDoc),
                dirty = true,
                writeVersion = it.writeVersion + 1,
                canUndo = true,
                canRedo = false,
            )
        }
    }

    fun updateTargetParagraph(coalesce: String? = null, transform: (Paragraph) -> Paragraph) {
        val target = _uiState.value.focusedTarget ?: return
        updateDocument(coalesce) { doc -> doc.mapParagraphAt(target, transform) }
    }

    private fun pushHistory(document: Document, coalesce: String?) {
        if (coalesce != null && coalesce == coalesceKey) return
        undoStack = (undoStack + document).takeLast(UNDO_LIMIT)
        redoStack = emptyList()
        coalesceKey = coalesce
    }

    fun undo() {
        val ready = _uiState.value.preview as? DocxPreview.Ready ?: return
        val previous = undoStack.lastOrNull() ?: return
        undoStack = undoStack.dropLast(1)
        redoStack = (redoStack + ready.document).takeLast(UNDO_LIMIT)
        _uiState.update {
            it.copy(
                preview = ready.copy(document = previous),
                dirty = true,
                writeVersion = it.writeVersion + 1,
                canUndo = undoStack.isNotEmpty(),
                canRedo = true,
            )
        }
    }

    fun redo() {
        val ready = _uiState.value.preview as? DocxPreview.Ready ?: return
        val next = redoStack.lastOrNull() ?: return
        redoStack = redoStack.dropLast(1)
        undoStack = (undoStack + ready.document).takeLast(UNDO_LIMIT)
        _uiState.update {
            it.copy(
                preview = ready.copy(document = next),
                dirty = true,
                writeVersion = it.writeVersion + 1,
                canUndo = true,
                canRedo = redoStack.isNotEmpty(),
            )
        }
    }

    fun documentBytes(document: Document): ByteArray {
        val source = (_uiState.value.preview as? DocxPreview.Ready)?.source
        return if (source != null) DocxEditor(source).save(document) else DocxWriter().write(document)
    }

    fun save(
        context: Context,
        file: RecentFile,
        draftStore: DraftStore,
        onSaved: () -> Unit,
        onError: (String) -> Unit,
    ) {
        val ready = _uiState.value.preview as? DocxPreview.Ready ?: return
        val document = ready.document
        val writingVersion = _uiState.value.writeVersion
        _uiState.update { it.copy(saving = true) }

        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = documentBytes(document)
                    val uri = Uri.parse(file.uri)
                    val written = SafeWrite.write(context, uri, bytes, PackageKind.DOCUMENT)
                    if (written <= 0) error("save refused: verification failed")
                }
            }

            _uiState.update { it.copy(saving = false) }
            result.fold(
                onSuccess = {
                    if (writingVersion == _uiState.value.writeVersion) {
                        draftStore.clear(file.uri)
                        _uiState.update { it.copy(dirty = false) }
                        onSaved()
                    } else {
                        onSaved()
                    }
                },
                onFailure = { error ->
                    onError(error.message ?: error.javaClass.simpleName)
                },
            )
        }
    }

    internal fun setDocumentForTesting(document: Document) {
        _uiState.update {
            it.copy(
                preview = DocxPreview.Ready(
                    document = document,
                    images = emptyMap(),
                ),
            )
        }
    }

    private data class Loaded(
        val document: Document,
        val images: Map<org.ziyoffice.model.InlineImage, androidx.compose.ui.graphics.ImageBitmap?>,
        val dropped: List<org.ziyoffice.ooxml.PreservedFeature>,
        val source: org.ziyoffice.ooxml.DocxSource?,
        val recovered: Boolean,
    )
}
