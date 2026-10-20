package org.ziyoffice.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.ziyoffice.model.DocumentKind
import org.ziyoffice.model.RecentFile

private val FILE_FILTERS = listOf("All", "Docs", "Sheets", "Slides", "PDF", "Other")

/**
 * The "Files" tab: a category tab strip (the pattern WPS uses on its document list)
 * over the same local recents list. No permission banner is needed \u2014 ZIyOffice
 * only ever sees files the user has explicitly opened.
 *
 * The heading and the empty state both say "recent": this is not a scan of device
 * storage, and the only way to reach anything else is the system picker.
 */
@Composable
fun FilesScreen(
    recents: List<RecentFile>,
    onOpenFile: () -> Unit,
    onOpenRecent: (RecentFile) -> Unit,
) {
    var filter by rememberSaveable { mutableStateOf(FILE_FILTERS.first()) }
    val filtered = recents.filter { matchesFilter(it, filter) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Recent files", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Files you have opened in ZIyOffice",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onOpenFile) { Text("Browse device") }
        }

        SecondaryScrollableTabRow(
            selectedTabIndex = FILE_FILTERS.indexOf(filter).coerceAtLeast(0),
            edgePadding = 16.dp,
        ) {
            FILE_FILTERS.forEach { name ->
                Tab(
                    selected = name == filter,
                    onClick = { filter = name },
                    text = { Text(name) },
                )
            }
        }

        if (filtered.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.InsertDriveFile,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        if (recents.isEmpty()) "No documents yet" else "Nothing in this category yet",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        if (recents.isEmpty()) {
                            "Files you open appear here. They stay in your folders \u2014 " +
                                "ZIyOffice does not copy or upload them."
                        } else {
                            "Try another category, or choose a file with \u201CBrowse device\u201D above."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            ) {
                items(filtered, key = { it.uri }) { file ->
                    RecentRow(file = file, onClick = { onOpenRecent(file) })
                }
            }
        }
    }
}

private fun matchesFilter(file: RecentFile, filter: String): Boolean = when (filter) {
    "Docs" -> file.kind == DocumentKind.DOCUMENT
    "Sheets" -> file.kind == DocumentKind.SPREADSHEET
    "Slides" -> file.kind == DocumentKind.PRESENTATION
    "PDF" -> file.kind == DocumentKind.PDF
    "Other" -> file.kind == DocumentKind.UNKNOWN
    else -> true
}
