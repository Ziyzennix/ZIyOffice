package org.ziyoffice.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ziyoffice.model.RecentFile
import org.ziyoffice.vault.Vault
import org.ziyoffice.vault.VaultEntry
import org.ziyoffice.vault.VaultStore

/**
 * The encrypted vault.
 *
 * The files in it are encrypted before they touch the disk, and the names
 * are encrypted too, so a folder full of them says nothing about the person
 * who put them there.
 *
 * The passphrase is never stored. Whoever opens the vault types it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultScreen(
    onBack: () -> Unit,
    onOpenFile: (RecentFile) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val store = remember { VaultStore(File(context.filesDir, "vault")) }

    var passphrase by remember { mutableStateOf("") }
    var vault by remember { mutableStateOf<Vault?>(null) }
    var entries by remember { mutableStateOf<List<VaultEntry>>(emptyList()) }
    var wrong by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<VaultEntry?>(null) }

    fun refresh(open: Vault) {
        vault = open
        entries = open.list()
    }

    val addLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        val current = vault ?: return@rememberLauncherForActivityResult
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: error("the file couldn't be read")
                    val name = displayNameOf(context, uri)
                    current.put(name, bytes)                }
            }
            busy = false
            result.fold(
                onSuccess = { refresh(current) },
                onFailure = {
                    snackbarHostState.showSnackbar(
                        "Couldn't put that in the vault: ${it.message ?: "unknown error"}",
                    )
                },
            )
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text("Encrypted vault") },
                actions = {
                    if (vault != null) {
                        IconButton(
                            onClick = { addLauncher.launch(arrayOf("*/*")) },
                            enabled = !busy,
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = "Put a file in the vault")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val current = vault
            if (current == null) {
                VaultPassphrase(
                    title = if (store.exists) "Unlock the vault" else "Create a vault",
                    explanation = if (store.exists) {
                        "The passphrase is the key. ZiyOffice does not keep it, so there is " +
                            "nothing to forget and nothing to reset."
                    } else {
                        "Choose a passphrase. It is the only way in, and ZiyOffice does not " +
                            "keep it: if it is lost, the files are lost."
                    },
                    passphrase = passphrase,
                    wrong = wrong,
                    onPassphraseChange = {
                        passphrase = it
                        wrong = false
                    },
                    onUnlock = {
                        busy = true
                        scope.launch {
                            val opened = withContext(Dispatchers.IO) {
                                if (store.exists) {
                                    store.open(passphrase)
                                } else {
                                    store.create(passphrase).also { it.put("read me.txt", VAULT_README) }
                                }
                            }
                            busy = false
                            if (opened == null) {
                                wrong = true
                            } else {
                                wrong = false
                                passphrase = ""
                                refresh(opened)
                            }
                        }
                    },
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(entries, key = { it.id }) { entry ->
                        VaultRow(
                            entry = entry,
                            onOpen = {
                                scope.launch {
                                    val opened = withContext(Dispatchers.IO) {
                                        val name = current.nameOf(entry.id) ?: entry.name
                                        val target = File(context.cacheDir, "vault-open/$name")
                                        target.parentFile?.mkdirs()
                                        if (current.extractTo(entry.id, target)) {
                                            target
                                        } else {
                                            null
                                        }
                                    }
                                    if (opened == null) {
                                        snackbarHostState.showSnackbar("Couldn't open that file")
                                        return@launch
                                    }
                                    onOpenFile(
                                        RecentFile(
                                            uri = Uri.fromFile(opened).toString(),
                                            displayName = entry.name,
                                            sizeBytes = entry.size.toLong(),
                                            lastOpenedAtEpochMs = entry.createdAt,
                                        ),
                                    )
                                }
                            },
                            onRemove = { removing = entry },
                        )
                    }
                }
            }
        }
    }

    removing?.let { entry ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("Take it out of the vault?") },
            text = { Text("${entry.name} will be deleted. It cannot be brought back.") },
            confirmButton = {
                TextButton(onClick = {
                    val current = vault
                    removing = null
                    if (current == null) return@TextButton
                    scope.launch {
                        withContext(Dispatchers.IO) { current.delete(entry.id) }
                        refresh(current)
                    }
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { removing = null }) { Text("Keep") }
            },
        )
    }
}

/** One file in the vault. */
@Composable
private fun VaultRow(
    entry: VaultEntry,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .clickable(onClick = onOpen)
            .semantics(mergeDescendants = true) {
                contentDescription = "${entry.name}, ${formatSize(entry.size.toLong())}"
                role = Role.Button
            }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Lock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(entry.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
            Text(
                formatSize(entry.size.toLong()),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onRemove, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Filled.Delete, contentDescription = "Take it out of the vault")
        }
    }
}

/** The screen that asks for a passphrase. */
@Composable
private fun VaultPassphrase(
    title: String,
    explanation: String,
    passphrase: String,
    wrong: Boolean,
    onPassphraseChange: (String) -> Unit,
    onUnlock: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Text(
            explanation,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = passphrase,
            onValueChange = onPassphraseChange,
            label = { Text("Passphrase") },
            singleLine = true,
            isError = wrong,
            supportingText = if (wrong) {
                { Text("That passphrase does not open this vault") }
            } else {
                null
            },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = onUnlock,
            enabled = passphrase.isNotEmpty(),
        ) { Text("Unlock") }
    }
}

/** The file a new vault starts with, so it is not an empty folder. */
private val VAULT_README = (
    "This is an encrypted vault.\n\n" +
        "The files in it are encrypted before they reach the disk, and so are their names. " +
        "The passphrase is the key: ZiyOffice does not keep it, and there is no way to reset it. " +
        "If the passphrase is lost, the files are gone.\n\n" +
        "Take the files out of the vault to open them, and put them back when you are done."
    ).encodeToByteArray()

/** The name the provider reports for a file, or the last piece of the path. */
private fun displayNameOf(context: android.content.Context, uri: Uri): String {
    var name: String? = null
    runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && !cursor.isNull(index)) name = cursor.getString(index)
            }
        }
    }
    return name ?: uri.lastPathSegment ?: "file"
}
