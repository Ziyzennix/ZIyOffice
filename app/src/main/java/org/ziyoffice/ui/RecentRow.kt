package org.ziyoffice.ui

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.ziyoffice.design.ZiyTypeColors
import org.ziyoffice.model.DocumentKind
import org.ziyoffice.model.RecentFile

/**
 * A file row with a colour-coded type badge, shared by Home and Files.
 *
 * The row's text children already spell out the name and the type/size/date
 * line, but TalkBack would read them as two nodes. One labelled node with
 * Role.Button gives a single sentence.
 */
@Composable
internal fun RecentRow(file: RecentFile, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = recentRowDescription(file, openedLabel(file.lastOpenedAtEpochMs))
                role = Role.Button
            }
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KindBadge(file)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                file.displayName,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                listOfNotNull(
                    file.kind.label,
                    formatSize(file.sizeBytes).takeIf { it.isNotEmpty() },
                    openedLabel(file.lastOpenedAtEpochMs).takeIf { it.isNotBlank() },
                ).joinToString(" \u00B7 "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun KindBadge(file: RecentFile, size: Dp = 48.dp) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = kindColor(file.kind),
        modifier = Modifier.size(size),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = badgeText(file),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                maxLines = 1,
            )
        }
    }
}

internal fun kindColor(kind: DocumentKind): Color = when (kind) {
    DocumentKind.DOCUMENT -> ZiyTypeColors.Docs
    DocumentKind.SPREADSHEET -> ZiyTypeColors.Sheets
    DocumentKind.PRESENTATION -> ZiyTypeColors.Slides
    DocumentKind.PDF -> ZiyTypeColors.Pdf
    DocumentKind.UNKNOWN -> ZiyTypeColors.File
}

private fun badgeText(file: RecentFile): String =
    file.displayName.substringAfterLast('.', "").uppercase().take(4).ifEmpty { "FILE" }

internal fun formatSize(bytes: Long): String = when {
    bytes <= 0 -> ""
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
}

/**
 * The spoken name of a recent-file row: "Report.docx, Word document, 24 KB,
 * opened 2 hours ago". Type, size and date are all optional, so the sentence
 * degrades gracefully instead of leaving dangling separators.
 *
 * [openedLabel] is passed in rather than read from [DateUtils] here, so the
 * wording can be asserted in a JVM test.
 */
internal fun recentRowDescription(file: RecentFile, openedLabel: String): String {
    val size = formatSize(file.sizeBytes).takeIf { it.isNotEmpty() }
    return listOfNotNull(
        file.displayName,
        file.kind.label,
        size,
        openedLabel.takeIf { it.isNotBlank() }?.let { "opened $it" },
    ).joinToString(", ")
}

/** The Android relative-time label for a row, e.g. "2 hours ago". */
internal fun openedLabel(lastOpenedAtEpochMs: Long): String =
    DateUtils.getRelativeTimeSpanString(lastOpenedAtEpochMs)?.toString().orEmpty()
