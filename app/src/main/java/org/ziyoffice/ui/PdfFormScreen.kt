package org.ziyoffice.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ziyoffice.model.RecentFile
import org.ziyoffice.pdf.AcroForm
import org.ziyoffice.pdf.PdfFieldKind
import org.ziyoffice.pdf.PdfFile
import org.ziyoffice.pdf.PdfForm
import org.ziyoffice.pdf.PdfFormField
import org.ziyoffice.pdf.PdfFormWriter
import org.ziyoffice.pdf.PdfPage as CorePage

/**
 * A PDF opened for filling in.
 *
 * The pages are drawn from the file and the fields are laid over them where
 * they sit in the file, so the person filling it in sees the document and
 * the boxes on it at the same time. What they type goes into the file's own
 * fields, so the copy that is saved is the same document with the answers
 * in it, not a picture of one.
 */
private class FillSession(
    /** The file, as the object layer reads it. */
    val file: PdfFile,
    /** The fields of the form. */
    val form: PdfForm,
    /** The platform renderer, which draws the pages. */
    val renderer: org.ziyoffice.pdf.PdfDocument,
    /** The pages, drawn. */
    val pages: List<Bitmap?>,
)

/**
 * Filling a form in a PDF.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfFormScreen(
    file: RecentFile,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val configuration = LocalConfiguration.current
    val pageWidthPx = remember(configuration.screenWidthDp) {
        (configuration.screenWidthDp * configuration.densityDpi / 160f * 0.94f).toInt()
    }

    var session by remember(file.uri) { mutableStateOf<FillSession?>(null) }
    var failure by remember(file.uri) { mutableStateOf<String?>(null) }
    var loading by remember(file.uri) { mutableStateOf(true) }
    var values by remember(file.uri) { mutableStateOf<Map<String, String>>(emptyMap()) }
    var focused by remember(file.uri) { mutableStateOf<String?>(null) }
    var saving by remember(file.uri) { mutableStateOf(false) }

    /** The name the filled copy is saved under. */
    val filledName = remember(file.displayName) {
        file.displayName.substringBeforeLast('.', file.displayName) + "-filled.pdf"
    }

    DisposableEffect(file.uri) {
        onDispose {
            session?.renderer?.close()
            session = null
        }
    }

    LaunchedEffect(file.uri) {
        loading = true
        val opened = withContext(Dispatchers.IO) {
            runCatching {
                val input = context.contentResolver.openInputStream(Uri.parse(file.uri))
                    ?: error("the file couldn't be opened")
                val bytes = input.use { it.readBytes() }
                val parsed = PdfFile.open(bytes) ?: error("this PDF couldn't be parsed")
                val form = AcroForm.read(parsed)
                    ?: error("this PDF has no form to fill in")
                if (form.isEmpty) error("this PDF has no fields to fill in")
                val cache = File(context.cacheDir, "pdf-form-${file.uri.hashCode()}.pdf")
                val renderer = org.ziyoffice.pdf.PdfDocument.open(
                    java.io.ByteArrayInputStream(bytes),
                    cache,
                ) ?: error("this PDF couldn't be drawn")
                val pages = parsed.pages.map { page ->
                    renderer.renderPage(page.index, pageWidthPx)
                }
                FillSession(parsed, form, renderer, pages)
            }
        }
        loading = false
        opened.fold(
            onSuccess = { loaded ->
                session = loaded
                // The fields the file already has filled in start out filled in.
                values = loaded.form.fields.associate { field ->
                    field.name to field.value
                }
            },
            onFailure = { error ->
                failure = error.message ?: error.javaClass.simpleName
            },
        )
    }

    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf"),
    ) { uri ->
        val current = session ?: return@rememberLauncherForActivityResult
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            saving = true
            val name = filledName
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = PdfFormWriter(current.file).write(values)
                    context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                        out.write(bytes)
                    } ?: error("the file couldn't be written")
                    val size = context.contentResolver.openFileDescriptor(uri, "r")
                        ?.use { it.statSize }
                        ?: error("the saved file couldn't be read back")
                    if (size <= 0L) error("nothing was written")
                    bytes
                }
            }
            saving = false
            if (!isActive) return@launch
            result.fold(
                onSuccess = {
                    snackbarHostState.showSnackbar("Saved $name")
                },
                onFailure = {
                    snackbarHostState.showSnackbar(
                        "Couldn't save that: ${it.message ?: "unknown error"}",
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
                title = {
                    Column {
                        Text("Fill the form", style = MaterialTheme.typography.titleMedium)
                        val filled = session?.form?.fields?.count { field ->
                            (values[field.name] ?: "").isNotEmpty()
                        } ?: 0
                        val total = session?.form?.size ?: 0
                        if (total > 0) {
                            Text(
                                "$filled of $total filled in",
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { saveLauncher.launch(filledName) },
                        enabled = session != null && values.isNotEmpty() && !saving,
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(Icons.Filled.Save, contentDescription = "Save the filled form")
                    }
                },
            )
        },
        bottomBar = {
            val current = session
            if (current != null) {
                FormNavigation(
                    form = current.form,
                    values = values,
                    focused = focused,
                    onFocus = { focused = it },
                )
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val current = session
            when {
                loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
                failure != null -> {
                    Column(
                        Modifier.fillMaxSize().padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            failure ?: "",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                current != null -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        itemsIndexed(current.pages) { index, bitmap ->
                            val page = current.file.pages.getOrNull(index)
                            Column(
                                Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    "Page ${index + 1}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 4.dp),
                                )
                                if (bitmap != null && page != null) {
                                    Box(
                                        Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(
                                                bitmap.width.toFloat() / bitmap.height.toFloat(),
                                            ),
                                    ) {
                                        androidx.compose.foundation.Image(
                                            bitmap = bitmap.asImageBitmap(),
                                            contentDescription = "Page ${index + 1}",
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                        FormFields(
                                            form = current.form,
                                            page = page,
                                            pageIndex = index,
                                            values = values,
                                            focused = focused,
                                            onFocus = { focused = it },
                                            onChange = { name, value ->
                                                values = values + (name to value)
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * The boxes of the fields, laid over the page as they sit in the file.
 */
@Composable
private fun FormFields(
    form: PdfForm,
    page: CorePage,
    pageIndex: Int,
    values: Map<String, String>,
    focused: String?,
    onFocus: (String) -> Unit,
    onChange: (String, String) -> Unit,
) {
    val fields = form.fieldsOn(pageIndex)
    if (fields.isEmpty()) return
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val width = maxWidth
        val height = maxHeight
        fields.forEach { field ->
            val widget = field.widgets.firstOrNull { it.pageIndex == pageIndex } ?: return@forEach
            val fraction = widget.rect.fractionsOn(page)
            val left = width * fraction.left.toFloat()
            val top = height * fraction.top.toFloat()
            val fieldWidth = (width * fraction.width.toFloat()).coerceAtLeast(4.dp)
            val fieldHeight = (height * fraction.height.toFloat()).coerceAtLeast(4.dp)
            Box(
                Modifier
                    .offset(x = left, y = top)
                    .size(width = fieldWidth, height = fieldHeight),
            ) {
                FieldBox(
                    field = field,
                    value = values[field.name] ?: "",
                    focused = focused == field.name,
                    onFocus = { onFocus(field.name) },
                    onChange = { onChange(field.name, it) },
                )
            }
        }
    }
}

/**
 * One field, drawn the way it is drawn on the page.
 */
@Composable
private fun FieldBox(
    field: PdfFormField,
    value: String,
    focused: Boolean,
    onFocus: () -> Unit,
    onChange: (String) -> Unit,
) {
    val border = if (focused) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline
    }
    when (field.kind) {
        PdfFieldKind.TEXT -> {
            BasicTextField(
                value = value,
                onValueChange = { changed ->
                    onFocus()
                    onChange(changed)
                },
                singleLine = !field.multiline,
                textStyle = TextStyle(
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = 0.92f))
                    .border(1.dp, border)
                    .semantics {
                        contentDescription = field.name
                    },
            )
        }
        PdfFieldKind.CHECKBOX, PdfFieldKind.RADIO -> {
            val on = value.isNotEmpty() && value != "Off"
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = 0.9f))
                    .border(1.dp, border)
                    .clickable {
                        onFocus()
                        onChange(if (on) "Off" else (field.widgets.firstOrNull()?.onState ?: "Yes"))
                    }
                    .semantics {
                        contentDescription = field.name
                        role = Role.Checkbox
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (on) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
        PdfFieldKind.DROPDOWN, PdfFieldKind.LISTBOX -> {
            var menuOpen by remember { mutableStateOf(false) }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = 0.92f))
                    .border(1.dp, border)
                    .clickable {
                        onFocus()
                        menuOpen = true
                    }
                    .semantics {
                        contentDescription = field.name
                        role = Role.Button
                    },
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    value.ifEmpty { "Choose" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    field.options.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option) },
                            onClick = {
                                menuOpen = false
                                onChange(option)
                            },
                        )
                    }
                }
            }
        }
        else -> {
            // A button or a signature. ZiyOffice does not press buttons and
            // draws signatures of its own, so the box is only outlined.
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = 0.9f))
                    .border(1.dp, border)
                    .semantics { contentDescription = field.name },
            )
        }
    }
}

/**
 * The strip at the bottom: which field is being filled in, and the way to
 * the next one.
 */
@Composable
private fun FormNavigation(
    form: PdfForm,
    values: Map<String, String>,
    focused: String?,
    onFocus: (String) -> Unit,
) {
    var listOpen by remember { mutableStateOf(false) }
    val fields = form.fields
    val index = fields.indexOfFirst { it.name == focused }
    Surface(tonalElevation = 3.dp) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = {
                    if (index > 0) onFocus(fields[index - 1].name)
                },
                enabled = index > 0,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Previous field")
            }
            Column(Modifier.weight(1f).clickable { listOpen = true }) {
                Text(
                    if (index >= 0) fields[index].name else "Choose a field",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                val value = if (index >= 0) values[fields[index].name].orEmpty() else ""
                Text(
                    value.ifEmpty { "Not filled in" },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            IconButton(
                onClick = { listOpen = true },
                modifier = Modifier.size(48.dp),
            ) {
                Icon(Icons.Filled.List, contentDescription = "All the fields")
            }
            IconButton(
                onClick = {
                    if (index in 0 until fields.lastIndex) onFocus(fields[index + 1].name)
                },
                enabled = index in 0 until fields.lastIndex,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Next field")
            }
            DropdownMenu(expanded = listOpen, onDismissRequest = { listOpen = false }) {
                fields.forEach { field ->
                    val value = values[field.name].orEmpty()
                    DropdownMenuItem(
                        text = {
                            Text(
                                if (value.isEmpty()) field.name else "${field.name}: $value",
                                maxLines = 1,
                            )
                        },
                        onClick = {
                            listOpen = false
                            onFocus(field.name)
                        },
                    )
                }
            }
        }
    }
}
