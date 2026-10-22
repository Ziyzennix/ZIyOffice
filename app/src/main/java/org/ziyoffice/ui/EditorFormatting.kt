package org.ziyoffice.ui

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.FormatStrikethrough
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import org.ziyoffice.model.ListKind
import org.ziyoffice.model.Paragraph
import org.ziyoffice.model.ParagraphAlignment

/**
 * A writing preset, what other office suites call a paragraph style. Applying
 * one rewrites the runs and spacing directly (fresh package, direct formatting).
 */
internal data class ParagraphPreset(
    val label: String,
    val shortLabel: String,
    val sizePt: Float,
    val bold: Boolean,
    val spaceBeforeTwips: Int?,
    val spaceAfterTwips: Int,
)

internal val PARAGRAPH_PRESETS = listOf(
    ParagraphPreset("Title", "Title", 24f, bold = true, spaceBeforeTwips = null, spaceAfterTwips = 240),
    ParagraphPreset("Heading 1", "H1", 18f, bold = true, spaceBeforeTwips = 240, spaceAfterTwips = 160),
    ParagraphPreset("Heading 2", "H2", 14f, bold = true, spaceBeforeTwips = 200, spaceAfterTwips = 120),
    ParagraphPreset("Heading 3", "H3", 12f, bold = true, spaceBeforeTwips = 160, spaceAfterTwips = 100),
    ParagraphPreset("Body", "Body", 11f, bold = false, spaceBeforeTwips = null, spaceAfterTwips = 160),
)

internal val BODY_PRESET = PARAGRAPH_PRESETS.last()

/** Which preset a paragraph currently matches (first run's size and weight). */
internal fun Paragraph.matchingPreset(): ParagraphPreset {
    val first = runs.firstOrNull() ?: return BODY_PRESET
    return PARAGRAPH_PRESETS.firstOrNull {
        it.sizePt == first.sizePt && it.bold == first.bold
    } ?: BODY_PRESET
}

@Composable
internal fun FormattingBar(
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
internal fun DocMoreMenu(
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
                            val matches = paragraph != null &&
                                paragraph.spacingBeforeTwips == before &&
                                paragraph.spacingAfterTwips == after
                            FilterChip(
                                selected = matches,
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
internal fun FormatAction(
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
