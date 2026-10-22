package org.ziyoffice.ui

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
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
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.ziyoffice.model.Block
import org.ziyoffice.model.placedCells
import org.ziyoffice.model.DocumentRegion
import org.ziyoffice.model.FocusPath
import org.ziyoffice.model.InlineImage
import org.ziyoffice.model.Paragraph
import org.ziyoffice.model.RecentFile
import org.ziyoffice.model.Table

@Composable
internal fun DocxPage(
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
                    var showStatsDialog by remember { mutableStateOf(false) }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier
                            .clickable { showStatsDialog = true }
                            .semantics { role = Role.Button },
                    ) {
                        Text(
                            "${countWords(preview.document.blocks)} words \u00B7 " +
                                "${countParagraphs(preview.document.blocks)} paragraphs",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                    if (showStatsDialog) {
                        DocumentStatisticsDialog(
                            stats = computeDocumentStats(preview.document.blocks),
                            onDismiss = { showStatsDialog = false },
                        )
                    }
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
internal fun EditableParagraph(
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
/**
 * One rendered paragraph: list marker, start padding, spacing and styled
 * text. Markers get concrete labels, so a document renders the same way twice.
 */
internal class DisplayLine(
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

internal sealed interface DisplayItem {
    data class Line(val line: DisplayLine) : DisplayItem
    data class Grid(val table: Table) : DisplayItem
}

internal fun List<Block>.toDisplayItems(
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

internal fun Paragraph.toDisplayLine(
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
@Composable
internal fun ParagraphLine(line: DisplayLine, onClick: (() -> Unit)? = null) {
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
internal fun PageBreakDivider() {
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
internal fun AddParagraphButton(onClick: () -> Unit) {
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
internal fun InlineImages(images: List<ImageBitmap?>) {
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

internal val DocumentTextStyle: TextStyle
    @Composable get() = MaterialTheme.typography.bodyMedium.copy(lineHeight = 16.sp)

/**
 * The document text style with the paragraph's line spacing applied: base
 * line height scaled by the percentage (null means the base).
 */
@Composable
internal fun lineTextStyle(line: DisplayLine): TextStyle {
    val base = DocumentTextStyle
    val percent = line.lineSpacingPercent ?: return base
    return base.copy(lineHeight = (base.lineHeight.value * percent / 100f).sp)
}

internal val GridLineColor = Color(0xFFC9CDC8)

@Composable
internal fun TableBlock(
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
internal data class CellRect(val left: Int, val top: Int, val right: Int, val bottom: Int)

/** The rectangles the last layout produced, for the grid to draw. */
internal class CellGeometry {
    var rects: List<CellRect> = emptyList()
}

/**
 * Draws the table grid from the rectangles the layout produced, instead of
 * giving every cell its own border. A spanning cell contributes only its real
 * edges, so no rule runs through its middle and neighbours share one line.
 */
internal fun DrawScope.drawGridLines(rects: List<CellRect>, color: Color, thickness: Float) {
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

internal val CELL_HORIZONTAL = 8.dp
internal val CELL_VERTICAL = 6.dp

/**
 * A header or footer shown as a labelled band above/below the body. Read-only
 * on purpose: Word keeps them in separate package parts with their own
 * section rules, so editing them from the body flow would write text into
 * the wrong part. The band makes them visible instead of dropping them on
 * the next save, which is what used to happen.
 */
@Composable
internal fun HeaderFooterBand(
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

internal const val TWIPS_PER_DP = 15

/** One indent step, a quarter of an inch. */
internal const val INDENT_STEP_TWIPS = 360

/** The space above and below a paragraph, in twips. */
internal val PARAGRAPH_SPACINGS: List<Triple<String, Int, Int>> = listOf(
    Triple("Tight", 0, 0),
    Triple("Normal", 120, 120),
    Triple("Relaxed", 240, 240),
)

/** A typical list indent (0.5 inch) for lists that do not set their own. */
internal const val DEFAULT_LIST_INDENT_TWIPS = 720

/** Space after a paragraph when the document does not specify one (8 pt). */
internal const val DEFAULT_PARAGRAPH_SPACING_TWIPS = 160

/** Depth of the in-memory undo history, in document snapshots. */
internal const val UNDO_LIMIT = 50

@Composable
internal fun InsertTableDialog(
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
internal fun StepperRow(
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
