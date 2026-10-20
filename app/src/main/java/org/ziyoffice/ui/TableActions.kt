package org.ziyoffice.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun TableActionBar(
    onInsertRowAbove: () -> Unit,
    onInsertRowBelow: () -> Unit,
    onDeleteRow: () -> Unit,
    onInsertColumnLeft: () -> Unit,
    onInsertColumnRight: () -> Unit,
    onDeleteColumn: () -> Unit,
    onDeleteTable: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onInsertRowAbove) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text("Row above")
            }
            TextButton(onClick = onInsertRowBelow) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text("Row below")
            }
            TextButton(onClick = onDeleteRow) {
                Icon(Icons.Filled.Delete, contentDescription = null)
                Text("Delete row")
            }
            TextButton(onClick = onInsertColumnLeft) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text("Col left")
            }
            TextButton(onClick = onInsertColumnRight) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text("Col right")
            }
            TextButton(onClick = onDeleteColumn) {
                Icon(Icons.Filled.Delete, contentDescription = null)
                Text("Delete col")
            }
            TextButton(onClick = onDeleteTable) {
                Icon(Icons.Filled.GridOn, contentDescription = null)
                Text("Delete table")
            }
        }
    }
}
