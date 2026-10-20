package org.ziyoffice.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

// panels the sheet editor opens over the grid. the screen keeps the state and
// the actions, the panels here just draw them.
internal enum class StructureAction {
    INSERT_ROWS_ABOVE,
    INSERT_ROWS_BELOW,
    DELETE_ROWS,
    INSERT_COLUMNS_LEFT,
    INSERT_COLUMNS_RIGHT,
    DELETE_COLUMNS,
}

// what the find panel can ask for
internal enum class FindAction { NEXT, REPLACE, REPLACE_ALL }

@Composable
internal fun SheetListDialog(
    choices: String,
    rangeLabel: String,
    onChoicesChange: (String) -> Unit,
    onApply: () -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Dropdown choices") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextField(
                    value = choices,
                    onValueChange = onChoicesChange,
                    singleLine = true,
                    label = { Text("Choices") },
                )
                Text(
                    "Applies to $rangeLabel. The cell offers these values when it is edited.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = onApply) { Text("Apply") } },
        dismissButton = {
            Row {
                TextButton(onClick = onClear) { Text("Clear") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SheetFindPanel(
    query: String,
    replacement: String,
    matchCase: Boolean,
    matchCount: Int,
    onQueryChange: (String) -> Unit,
    onReplacementChange: (String) -> Unit,
    onMatchCaseToggle: () -> Unit,
    onAction: (FindAction) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Find and replace", style = MaterialTheme.typography.titleLarge)
            TextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                label = { Text("Find") },
            )
            TextField(
                value = replacement,
                onValueChange = onReplacementChange,
                singleLine = true,
                label = { Text("Replace with") },
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterChip(
                    selected = matchCase,
                    onClick = onMatchCaseToggle,
                    label = { Text("Match case") },
                )
                Text(
                    when (matchCount) {
                        0 -> "No matches"
                        1 -> "1 match"
                        else -> "$matchCount matches"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                TextButton(onClick = { onAction(FindAction.NEXT) }) { Text("Find next") }
                TextButton(
                    onClick = { onAction(FindAction.REPLACE) },
                    enabled = matchCount > 0,
                ) { Text("Replace") }
                TextButton(
                    onClick = { onAction(FindAction.REPLACE_ALL) },
                    enabled = matchCount > 0,
                ) { Text("Replace all") }
            }
            Text(
                "A formula is searched through the text it quotes, so references and " +
                    "function names are never rewritten.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SheetStructurePanel(
    rangeLabel: String,
    rowCount: Int,
    columnCount: Int,
    canDeleteRows: Boolean,
    canDeleteColumns: Boolean,
    onAction: (StructureAction) -> Unit,
    onDismiss: () -> Unit,
) {
    val rows = countLabel(rowCount, "row")
    val columns = countLabel(columnCount, "column")
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
        ) {
            Text(
                "Rows and columns",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            Text(
                "$rangeLabel \u00B7 $rows \u00D7 $columns",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            Text(
                "Rows",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            StructureAction("Insert $rows above") {
                onAction(StructureAction.INSERT_ROWS_ABOVE)
            }
            StructureAction("Insert $rows below") {
                onAction(StructureAction.INSERT_ROWS_BELOW)
            }
            StructureAction(
                label = "Delete $rows",
                destructive = true,
                enabled = canDeleteRows,
                onClick = { onAction(StructureAction.DELETE_ROWS) },
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
            Text(
                "Columns",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            StructureAction("Insert $columns to the left") {
                onAction(StructureAction.INSERT_COLUMNS_LEFT)
            }
            StructureAction("Insert $columns to the right") {
                onAction(StructureAction.INSERT_COLUMNS_RIGHT)
            }
            StructureAction(
                label = "Delete $columns",
                destructive = true,
                enabled = canDeleteColumns,
                onClick = { onAction(StructureAction.DELETE_COLUMNS) },
            )
            Text(
                "Cells move with the edit. Merged ranges, sizes and formula references adjust; " +
                    "a deleted reference becomes #REF!.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

@Composable
private fun StructureAction(
    label: String,
    destructive: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodyLarge,
        color = when {
            !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
            destructive -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.onSurface
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(vertical = 14.dp),
    )
}
