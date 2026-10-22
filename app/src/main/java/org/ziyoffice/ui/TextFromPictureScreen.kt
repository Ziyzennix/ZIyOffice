package org.ziyoffice.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A picture in, its words out.
 *
 * A scanned page is a picture, so nothing on it can be searched, copied or
 * edited until somebody reads the letters off it. That is what this screen
 * does, on the device, with the radio off.
 *
 * The text is shown in a field rather than a label because no reader is
 * perfect: the point is to be able to fix the one word it got wrong before
 * the text becomes a document.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextFromPictureScreen(
    imageUri: Uri,
    onBack: () -> Unit,
    onCreateDocument: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var text by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(imageUri) {
        busy = true
        message = null
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val bytes = context.contentResolver.openInputStream(imageUri)
                    ?.use { it.readBytes() }
                    ?: error("the picture couldn't be read")
                val bitmap = PictureText.bitmapOf(bytes)
                    ?: error("that file isn't a picture ZiyOffice can read")
                PictureText.read(context, bitmap)
            }
        }
        busy = false
        result.fold(
            onSuccess = { found ->
                text = found
                if (found.isEmpty()) {
                    message = "No words were found in that picture. It may be too small, " +
                        "too dark, or turned on its side."
                }
            },
            onFailure = { message = it.message ?: "the picture couldn't be read" },
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text("Text from a picture") },
            )
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                busy -> Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    CircularProgressIndicator()
                    Text("Reading the picture", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "This happens on the device. Nothing is uploaded.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                else -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    message?.let { line ->
                        Text(
                            line,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (text.isNotEmpty()) {
                        OutlinedTextField(
                            value = text,
                            onValueChange = { text = it },
                            label = { Text("The words in the picture") },
                            minLines = 12,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            "${text.trim().split(Regex("\\s+")).count { it.isNotEmpty() }} words. " +
                                "Correct anything the reader got wrong, then keep it.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val clipboard = context.getSystemService(ClipboardManager::class.java)
                                    clipboard?.setPrimaryClip(
                                        ClipData.newPlainText("Text from a picture", text),
                                    )
                                    scope.launch { snackbar.showSnackbar("Copied") }
                                },
                            ) { Text("Copy") }
                            Button(
                                onClick = { onCreateDocument(text) },
                                modifier = Modifier.weight(1f),
                            ) { Text("Save as a document") }
                        }
                    }
                    if (text.isEmpty() && !busy) {
                        OutlinedButton(onClick = onBack) { Text("Try another picture") }
                    }
                }
            }
        }
    }
}
