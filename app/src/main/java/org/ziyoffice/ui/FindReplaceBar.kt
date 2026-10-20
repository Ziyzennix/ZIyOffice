package org.ziyoffice.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun FindReplaceBar(
    query: String,
    onQueryChange: (String) -> Unit,
    matchCount: Int,
    currentMatch: Int, // 0-based, -1 if none
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    replaceText: String,
    onReplaceTextChange: (String) -> Unit,
    onReplace: () -> Unit,
    onReplaceAll: () -> Unit,
    editing: Boolean, // show replace row only when editing
    onClose: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    placeholder = { Text("Find\u2026") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = when {
                        query.isEmpty() -> ""
                        matchCount == 0 -> "No matches"
                        else -> "${currentMatch + 1} of $matchCount"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
                IconButton(
                    onClick = onPrevious,
                    enabled = matchCount > 0,
                ) {
                    Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Previous match")
                }
                IconButton(
                    onClick = onNext,
                    enabled = matchCount > 0,
                ) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Next match")
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = "Close search")
                }
            }
            if (editing) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    OutlinedTextField(
                        value = replaceText,
                        onReplaceTextChange,
                        placeholder = { Text("Replace\u2026") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = onReplace,
                        enabled = matchCount > 0 && currentMatch >= 0,
                    ) {
                        Text("Replace")
                    }
                    TextButton(
                        onClick = onReplaceAll,
                        enabled = matchCount > 0,
                    ) {
                        Text("Replace all")
                    }
                }
            }
        }
    }
}
