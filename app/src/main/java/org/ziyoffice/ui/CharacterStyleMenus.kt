package org.ziyoffice.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp

internal val SIZE_OPTIONS = listOf(9f, 10f, 11f, 12f, 14f, 16f, 18f, 24f, 36f)
internal val COLOUR_OPTIONS: List<Int?> = listOf(
    null, 0x1A1C1B, 0x2F6FED, 0x0E5A4E, 0xBA1A1A, 0xB25E00, 0x6B3FA0, 0x6F7975,
)

@Composable
internal fun SizeMenu(
    currentSizePt: Float?,
    enabled: Boolean = true,
    onSize: (Float) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(
            onClick = { expanded = true },
            enabled = enabled,
        ) {
            Icon(
                imageVector = Icons.Filled.FormatSize,
                contentDescription = "Font size",
                tint = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SIZE_OPTIONS.forEach { size ->
                val isCurrent = currentSizePt == size
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${size.toInt()} pt", modifier = Modifier.weight(1f))
                            if (isCurrent) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    // The item's state is announced below, so a
                                    // description here would say it twice.
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    },
                    onClick = {
                        expanded = false
                        onSize(size)
                    },
                    modifier = Modifier.semantics {
                        stateDescription = if (isCurrent) "Selected" else "Not selected"
                    },
                )
            }
        }
    }
}

@Composable
internal fun ColourMenu(
    currentColourRgb: Int?,
    enabled: Boolean = true,
    onColour: (Int?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(
            onClick = { expanded = true },
            enabled = enabled,
        ) {
            Icon(
                imageVector = Icons.Filled.FormatColorFill,
                contentDescription = "Font colour",
                tint = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            COLOUR_OPTIONS.forEach { rgb ->
                val isCurrent = currentColourRgb == rgb
                DropdownMenuItem(
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 4.dp),
                        ) {
                            if (rgb == null) {
                                Text("Auto", modifier = Modifier.weight(1f))
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        // The swatch is decorative: the name beside
                                        // it is the label, so TalkBack never has
                                        // to describe a colour.
                                        .background(
                                            color = Color(0xFF000000.toInt() or rgb),
                                            shape = CircleShape,
                                        ),
                                )
                                Text(
                                    text = " " + colourName(rgb),
                                    modifier = Modifier.weight(1f).padding(start = 8.dp),
                                )
                            }
                            if (isCurrent) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    },
                    onClick = {
                        expanded = false
                        onColour(rgb)
                    },
                    // The checkmark is visual only, so the chosen colour is also
                    // announced as the item's state.
                    modifier = Modifier.semantics {
                        stateDescription = if (isCurrent) "Selected" else "Not selected"
                    },
                )
            }
        }
    }
}

/** The name shown (and spoken) next to a font-colour swatch. */
internal fun colourName(rgb: Int): String = when (rgb) {
    0x1A1C1B -> "Black"
    0x2F6FED -> "Blue"
    0x0E5A4E -> "Green"
    0xBA1A1A -> "Red"
    0xB25E00 -> "Orange"
    0x6B3FA0 -> "Purple"
    0x6F7975 -> "Grey"
    else -> "Colour"
}
