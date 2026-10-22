package org.ziyoffice.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.ziyoffice.design.ZiyTypeColors
import org.ziyoffice.model.RecentFile

/** How many recents Home lists before deferring to the Files tab. */
private const val RECENT_LIMIT = 5

/**
 * Home dashboard: search, create tiles and the recent files list, the same
 * visual rhythm as the office apps users know, without the cloud rows.
 */
@Composable
fun HomeScreen(
    recents: List<RecentFile>,
    onOpenFile: () -> Unit,
    onOpenPdf: () -> Unit,
    onOpenRecent: (RecentFile) -> Unit,
    onCreateDocument: () -> Unit,
    onCreateSpreadsheet: () -> Unit,
    onCreatePresentation: () -> Unit,
    onCreateTemplate: (String) -> Unit,
    onReadText: () -> Unit,
    onImagesToPdf: () -> Unit,
    onToggleStar: (RecentFile) -> Unit = {},
    onShowAllFiles: () -> Unit,
) {
    var showTemplates by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = if (query.isBlank()) {
        recents
    } else {
        recents.filter { it.displayName.contains(query.trim(), ignoreCase = true) }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("ZiyOffice", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Your files, your device. No account, no cloud, no ads.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                label = { Text("Search recent files") },
                placeholder = { Text("File name") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                shape = RoundedCornerShape(28.dp),
                // No contentDescription here on purpose: it would replace the
                // node's editable text, so TalkBack would stop reading back
                // what the user types. The label names the field instead.
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item {
            FilledTonalButton(onClick = onOpenFile, modifier = Modifier.fillMaxWidth()) {
                Text("Open a file")
            }
        }

        item {
            TextButton(onClick = { showTemplates = !showTemplates }) {
                Text(if (showTemplates) "Hide templates" else "Start from a template")
            }
        }

        if (showTemplates) {
            item {
                SectionCard {
                    Text("Templates", style = MaterialTheme.typography.titleMedium)
                    org.ziyoffice.model.DOCUMENT_TEMPLATES.forEach { template ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .semantics(mergeDescendants = true) {
                                    // announce a real action, not the raw list item
                                    contentDescription = "${template.name}, ${template.description}"
                                    role = Role.Button
                                }
                                .clickable {
                                    showTemplates = false
                                    onCreateTemplate(template.id)
                                }
                                .padding(vertical = 6.dp),
                        ) {
                            Text(template.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                template.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        item {
            SectionCard {
                Text("Create", style = MaterialTheme.typography.titleMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CreateTile(
                        label = "Docs",
                        color = ZiyTypeColors.Docs,
                        icon = Icons.Filled.Description,
                        modifier = Modifier.weight(1f),
                    ) { onCreateDocument() }
                    CreateTile(
                        label = "Slides",
                        color = ZiyTypeColors.Slides,
                        icon = Icons.Filled.Slideshow,
                        modifier = Modifier.weight(1f),
                    ) { onCreatePresentation() }
                    CreateTile(
                        label = "Sheets",
                        color = ZiyTypeColors.Sheets,
                        icon = Icons.Filled.TableChart,
                        modifier = Modifier.weight(1f),
                    ) { onCreateSpreadsheet() }
                    CreateTile(
                        label = "PDF",
                        spokenAction = "Open",
                        color = ZiyTypeColors.Pdf,
                        icon = Icons.Filled.PictureAsPdf,
                        modifier = Modifier.weight(1f),
                    ) { onOpenPdf() }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CreateTile(
                        label = "Text",
                        description = "Read text from a picture",
                        color = ZiyTypeColors.Image,
                        icon = Icons.Filled.DocumentScanner,
                        modifier = Modifier.weight(1f),
                    ) { onReadText() }
                    CreateTile(
                        label = "Photos to PDF",
                        description = "Compile pictures into a PDF",
                        color = ZiyTypeColors.Pdf,
                        icon = Icons.Filled.Collections,
                        modifier = Modifier.weight(1f),
                    ) { onImagesToPdf() }
                }
            }
        }

        item {
            Text("Recent files", style = MaterialTheme.typography.titleMedium)
        }

        if (filtered.isEmpty()) {
            item {
                Text(
                    if (recents.isEmpty()) {
                        "Nothing yet. Open a .docx, .xlsx, .pptx or .pdf from your folders, " +
                            "everything stays on this device."
                    } else {
                        "No recent file matches \u201C${query.trim()}\u201D."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            val shown = filtered.take(RECENT_LIMIT)
            items(shown, key = { it.uri }) { file ->
                RecentRow(file = file, onClick = { onOpenRecent(file) }, onToggleStar = { onToggleStar(file) })
            }
            if (filtered.size > RECENT_LIMIT) {
                item {
                    TextButton(onClick = onShowAllFiles) {
                        Text("All ${filtered.size} files")
                    }
                }
            }
        }

        item {
            Text(
                "Offline by design. ZiyOffice has no network permission, no ads and no accounts.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SectionCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@Composable
private fun CreateTile(
    label: String,
    color: Color,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    spokenAction: String = "New",
    description: String? = null,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .semantics(mergeDescendants = true) {
                // "New Docs" for creation, "Open PDF" for the PDF tile: a PDF
                // cannot be created from scratch, so the tile must not imply it.
                contentDescription = description ?: "$spokenAction $label"
                role = Role.Button
            }
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = color,
            modifier = Modifier.size(48.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}
