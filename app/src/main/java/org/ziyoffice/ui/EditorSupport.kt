package org.ziyoffice.ui

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import android.net.Uri
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import java.io.ByteArrayInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.ziyoffice.model.Block
import org.ziyoffice.model.Document
import org.ziyoffice.model.DocumentKind
import org.ziyoffice.model.InlineImage
import org.ziyoffice.model.Paragraph
import org.ziyoffice.model.ParagraphAlignment
import org.ziyoffice.model.RecentFile
import org.ziyoffice.model.Table
import org.ziyoffice.ooxml.ContentAudit
import org.ziyoffice.ooxml.DocxSource
import org.ziyoffice.ooxml.DocxReader
import org.ziyoffice.ooxml.PreservedFeature

@Composable
internal fun AccessLine(access: FileAccess) {
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

internal sealed interface DocxPreview {
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
internal fun rememberDocxPreviewState(file: RecentFile): MutableState<DocxPreview> {
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
internal sealed interface FileAccess {
    data object Checking : FileAccess
    data class Ok(val sizeBytes: Long) : FileAccess
    data class Failed(val reason: String) : FileAccess
}

@Composable
internal fun rememberFileAccess(uriString: String): FileAccess {
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

internal fun metaLine(file: RecentFile): String =
    listOfNotNull(
        file.kind.label,
        formatSize(file.sizeBytes).ifEmpty { null },
    ).joinToString(" \u00B7 ")

internal val DocumentInk = Color(0xFF23262B)

internal fun Paragraph.text(): String = runs.joinToString("") { it.text }

internal fun nextFreeNumId(document: Document): Int {
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

internal fun Paragraph.annotatedText() = buildAnnotatedString {
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

internal fun ParagraphAlignment.toTextAlign(): TextAlign = when (this) {
    ParagraphAlignment.START -> TextAlign.Start
    ParagraphAlignment.CENTER -> TextAlign.Center
    ParagraphAlignment.END -> TextAlign.End
    ParagraphAlignment.JUSTIFY -> TextAlign.Justify
}
/** All inline images of a block, including the ones inside table cells. */
internal fun collectImages(block: Block): List<InlineImage> = when (block) {
    is Paragraph -> block.images
    is Table -> block.rows.flatMap { row ->
        row.cells.flatMap { cell -> cell.blocks.flatMap(::collectImages) }
    }
}

internal fun countParagraphs(blocks: List<Block>): Int = blocks.sumOf { block ->
    when (block) {
        is Paragraph -> 1
        is Table -> block.rows.sumOf { row -> row.cells.sumOf { cell -> countParagraphs(cell.blocks) } }
    }
}

/** Word count for the document header. Words, not paragraphs. */
internal fun countWords(blocks: List<Block>): Int = blocks.sumOf { block ->
    when (block) {
        is Paragraph -> block.text().split(' ', '\n', '\t').count { it.isNotBlank() }
        is Table -> block.rows.sumOf { row -> row.cells.sumOf { cell -> countWords(cell.blocks) } }
    }
}

/** "report.docx" -> "report.pdf" for the export dialog's suggested name. */
internal fun pdfFileName(displayName: String): String {
    return if (displayName.endsWith(".pdf", ignoreCase = true)) {
        displayName
    } else {
        val base = displayName.substringBeforeLast('.', displayName)
        "$base.pdf"
    }
}
