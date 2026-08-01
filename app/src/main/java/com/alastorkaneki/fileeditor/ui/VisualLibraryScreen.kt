package com.alastorkaneki.fileeditor.ui

import android.app.Activity
import android.content.IntentSender
import android.graphics.Bitmap
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alastorkaneki.fileeditor.data.ImageTagData
import com.alastorkaneki.fileeditor.data.VideoTagData
import com.alastorkaneki.fileeditor.data.VisualMediaItem
import com.alastorkaneki.fileeditor.data.VisualMediaKind
import com.alastorkaneki.fileeditor.data.VisualMediaRepository
import com.alastorkaneki.fileeditor.data.VisualSearchMatcher
import com.alastorkaneki.fileeditor.data.VisualWritePermissionRequired
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.ln
import kotlin.math.pow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisualLibraryScreen(
    kind: VisualMediaKind,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    onExit: () -> Unit,
    onOpenGlitch: (android.net.Uri) -> Unit,
    onOpenGif: (android.net.Uri) -> Unit,
) {
    val context = LocalContext.current
    val repository = remember { VisualMediaRepository(context) }
    val scope = rememberCoroutineScope()
    var items by remember(kind) { mutableStateOf<List<VisualMediaItem>>(emptyList()) }
    var query by remember(kind) { mutableStateOf("") }
    var loading by remember(kind) { mutableStateOf(false) }
    var message by remember(kind) { mutableStateOf<String?>(null) }
    var selected by remember(kind) { mutableStateOf<VisualMediaItem?>(null) }

    fun scan() {
        if (!hasPermission || loading) return
        scope.launch {
            loading = true
            message = null
            runCatching { withContext(Dispatchers.IO) { repository.scan(kind) } }
                .onSuccess {
                    items = it
                    message = "Found ${it.size} ${if (kind == VisualMediaKind.IMAGE) "photos" else "videos"}"
                }
                .onFailure { message = "Scan failed: ${it.message ?: it::class.java.simpleName}" }
            loading = false
        }
    }

    LaunchedEffect(hasPermission, kind) {
        if (hasPermission) scan()
    }

    selected?.let { item ->
        if (kind == VisualMediaKind.IMAGE) {
            ImageMetadataEditor(
                item = item,
                repository = repository,
                onBack = { selected = null },
                onSaved = {
                    selected = null
                    scan()
                },
                onOpenGlitch = onOpenGlitch,
            )
        } else {
            VideoMetadataEditor(
                item = item,
                repository = repository,
                onBack = { selected = null },
                onSaved = {
                    selected = null
                    scan()
                },
                onOpenGif = onOpenGif,
            )
        }
        return
    }

    val visible = remember(items, query) { items.filter { VisualSearchMatcher.matches(it, query) } }
    val label = if (kind == VisualMediaKind.IMAGE) "Photos" else "Videos"

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(label)
                        Text(
                            "${items.size} files",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onExit) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = ::scan, enabled = hasPermission && !loading) {
                        Icon(Icons.Default.Refresh, contentDescription = "Scan again")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        if (!hasPermission) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Card(
                    modifier = Modifier.padding(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            if (kind == VisualMediaKind.IMAGE) Icons.Default.Image else Icons.Default.Movie,
                            contentDescription = null,
                            modifier = Modifier.size(52.dp),
                        )
                        Text("$label access required", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "Grant media access to scan shared storage. Glitch Lab and GIF Maker can still use Android's file picker without this permission.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(onClick = onRequestPermission) { Text("Grant media access") }
                    }
                }
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                singleLine = true,
                label = { Text("Search filename, title, folder or format") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            )

            message?.let {
                Text(
                    it,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            when {
                loading && items.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                visible.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(if (query.isBlank()) "No $label found" else "No matches for “$query”")
                }
                else -> LazyColumn(
                    contentPadding = PaddingValues(bottom = 24.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(visible, key = { it.uri.toString() }) { item ->
                        VisualMediaRow(item, repository) { selected = item }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                    }
                }
            }
        }
    }
}

@Composable
private fun VisualMediaRow(
    item: VisualMediaItem,
    repository: VisualMediaRepository,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MediaThumbnail(item, repository, Modifier.size(68.dp))
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                item.title.ifBlank { item.displayName },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                item.relativePath.ifBlank { item.displayName },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val details = buildList {
                if (item.width > 0 && item.height > 0) add("${item.width}×${item.height}")
                if (item.durationMs > 0) add(formatVisualDuration(item.durationMs))
                add(formatVisualBytes(item.sizeBytes))
                add(item.mimeType)
            }.joinToString(" • ")
            Text(
                details,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MediaThumbnail(
    item: VisualMediaItem,
    repository: VisualMediaRepository,
    modifier: Modifier = Modifier,
) {
    val bitmap by produceState<Bitmap?>(initialValue = null, item.uri) {
        value = runCatching {
            withContext(Dispatchers.IO) { repository.loadThumbnail(item) }
        }.getOrNull()
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = requireNotNull(bitmap).asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Icon(
                if (item.kind == VisualMediaKind.IMAGE) Icons.Default.Image else Icons.Default.Movie,
                contentDescription = null,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImageMetadataEditor(
    item: VisualMediaItem,
    repository: VisualMediaRepository,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    onOpenGlitch: (android.net.Uri) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var tags by remember(item.uri) { mutableStateOf<ImageTagData?>(null) }
    var error by remember(item.uri) { mutableStateOf<String?>(null) }
    var saving by remember(item.uri) { mutableStateOf(false) }
    var message by remember(item.uri) { mutableStateOf<String?>(null) }

    suspend fun saveNow() {
        val current = tags ?: return
        saving = true
        message = null
        runCatching { withContext(Dispatchers.IO) { repository.saveImageTags(item, current) } }
            .onSuccess {
                saving = false
                onSaved()
            }
            .onFailure { throwable ->
                saving = false
                if (throwable is VisualWritePermissionRequired) {
                    throw throwable
                }
                message = "Save failed: ${throwable.message ?: throwable::class.java.simpleName}"
            }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            scope.launch {
                runCatching { saveNow() }
                    .onFailure { message = "Save failed: ${it.message ?: it::class.java.simpleName}" }
            }
        } else {
            message = "Write access was not granted"
        }
    }

    fun requestSave() {
        if (saving || tags == null) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching { repository.createWriteRequest(item.uri) }
                .onSuccess { pending ->
                    permissionLauncher.launch(IntentSenderRequest.Builder(pending.intentSender).build())
                }
                .onFailure { message = "Unable to request write access: ${it.message}" }
        } else {
            scope.launch {
                runCatching { saveNow() }
                    .onFailure { throwable ->
                        if (throwable is VisualWritePermissionRequired) {
                            launchPendingIntent(permissionLauncher, throwable.pendingIntent.intentSender)
                        } else {
                            message = "Save failed: ${throwable.message ?: throwable::class.java.simpleName}"
                        }
                    }
            }
        }
    }

    LaunchedEffect(item.uri) {
        runCatching { withContext(Dispatchers.IO) { repository.readImageTags(item) } }
            .onSuccess { tags = it }
            .onFailure { error = it.message ?: it::class.java.simpleName }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(item.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !saving) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        when {
            error != null -> Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) { Text("Unable to read EXIF metadata: $error") }
            tags == null -> Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            else -> {
                val current = requireNotNull(tags)
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item { MediaThumbnail(item, repository, Modifier.fillMaxWidth().height(260.dp)) }
                    item {
                        Text(
                            "EXIF metadata",
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        Text(
                            "Embedded writes are supported for JPEG, PNG and WebP. Other formats may be read-only.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    item { StudioTextField("Description", current.description) { tags = current.copy(description = it) } }
                    item { StudioTextField("Artist / creator", current.artist) { tags = current.copy(artist = it) } }
                    item { StudioTextField("Copyright", current.copyright) { tags = current.copy(copyright = it) } }
                    item { StudioTextField("User comment", current.userComment, minLines = 3) { tags = current.copy(userComment = it) } }
                    item { StudioTextField("Date taken (YYYY:MM:DD HH:MM:SS)", current.dateTimeOriginal) { tags = current.copy(dateTimeOriginal = it) } }
                    item { StudioTextField("Camera make", current.make) { tags = current.copy(make = it) } }
                    item { StudioTextField("Camera model", current.model) { tags = current.copy(model = it) } }
                    item { StudioTextField("Software", current.software) { tags = current.copy(software = it) } }
                    message?.let { text -> item { Text(text, color = MaterialTheme.colorScheme.error) } }
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Button(
                                onClick = { onOpenGlitch(item.uri) },
                                modifier = Modifier.weight(1f),
                                enabled = !saving,
                            ) {
                                Icon(Icons.Default.AutoFixHigh, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Glitch")
                            }
                            Button(
                                onClick = ::requestSave,
                                modifier = Modifier.weight(1f),
                                enabled = !saving,
                            ) {
                                if (saving) {
                                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Default.Save, contentDescription = null)
                                }
                                Spacer(Modifier.width(8.dp))
                                Text("Save")
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VideoMetadataEditor(
    item: VisualMediaItem,
    repository: VisualMediaRepository,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    onOpenGif: (android.net.Uri) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var tags by remember(item.uri) { mutableStateOf<VideoTagData?>(null) }
    var error by remember(item.uri) { mutableStateOf<String?>(null) }
    var saving by remember(item.uri) { mutableStateOf(false) }
    var message by remember(item.uri) { mutableStateOf<String?>(null) }

    suspend fun saveNow() {
        val current = tags ?: return
        saving = true
        message = null
        runCatching { withContext(Dispatchers.IO) { repository.saveVideoTags(item, current) } }
            .onSuccess { warnings ->
                saving = false
                if (warnings.isEmpty()) onSaved() else message = warnings.joinToString("\n")
            }
            .onFailure { throwable ->
                saving = false
                if (throwable is VisualWritePermissionRequired) throw throwable
                message = "Save failed: ${throwable.message ?: throwable::class.java.simpleName}"
            }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            scope.launch {
                runCatching { saveNow() }
                    .onFailure { message = "Save failed: ${it.message ?: it::class.java.simpleName}" }
            }
        } else {
            message = "Write access was not granted"
        }
    }

    fun requestSave() {
        if (saving || tags == null) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching { repository.createWriteRequest(item.uri) }
                .onSuccess { pending ->
                    permissionLauncher.launch(IntentSenderRequest.Builder(pending.intentSender).build())
                }
                .onFailure { message = "Unable to request write access: ${it.message}" }
        } else {
            scope.launch {
                runCatching { saveNow() }
                    .onFailure { throwable ->
                        if (throwable is VisualWritePermissionRequired) {
                            launchPendingIntent(permissionLauncher, throwable.pendingIntent.intentSender)
                        } else {
                            message = "Save failed: ${throwable.message ?: throwable::class.java.simpleName}"
                        }
                    }
            }
        }
    }

    LaunchedEffect(item.uri) {
        runCatching { withContext(Dispatchers.IO) { repository.readVideoTags(item) } }
            .onSuccess { tags = it }
            .onFailure { error = it.message ?: it::class.java.simpleName }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(item.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !saving) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        when {
            error != null -> Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) { Text("Unable to read video metadata: $error") }
            tags == null -> Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            else -> {
                val current = requireNotNull(tags)
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item { MediaThumbnail(item, repository, Modifier.fillMaxWidth().height(260.dp)) }
                    item {
                        Text("Video metadata", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "Android providers allow safe rename, tags, category and language edits. Codec, duration and resolution remain read-only.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    item { StudioTextField("File name", current.displayName) { tags = current.copy(displayName = it) } }
                    item { StudioTextField("Tags", current.tags, minLines = 2) { tags = current.copy(tags = it) } }
                    item { StudioTextField("Category", current.category) { tags = current.copy(category = it) } }
                    item { StudioTextField("Language", current.language) { tags = current.copy(language = it) } }
                    message?.let { text -> item { Text(text, color = MaterialTheme.colorScheme.error) } }
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Button(
                                onClick = { onOpenGif(item.uri) },
                                modifier = Modifier.weight(1f),
                                enabled = !saving,
                            ) {
                                Icon(Icons.Default.Movie, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Make GIF")
                            }
                            Button(
                                onClick = ::requestSave,
                                modifier = Modifier.weight(1f),
                                enabled = !saving,
                            ) {
                                if (saving) {
                                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Default.Save, contentDescription = null)
                                }
                                Spacer(Modifier.width(8.dp))
                                Text("Save")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StudioTextField(
    label: String,
    value: String,
    minLines: Int = 1,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        minLines = minLines,
        maxLines = if (minLines > 1) 8 else 1,
        singleLine = minLines == 1,
    )
}

private fun launchPendingIntent(
    launcher: androidx.activity.result.ActivityResultLauncher<IntentSenderRequest>,
    sender: IntentSender,
) {
    launcher.launch(IntentSenderRequest.Builder(sender).build())
}

private fun formatVisualDuration(milliseconds: Long): String {
    val totalSeconds = milliseconds / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(Locale.US, minutes, seconds)
}

private fun formatVisualBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    val group = (ln(bytes.toDouble()) / ln(1024.0)).toInt().coerceIn(0, units.lastIndex)
    return "%.1f %s".format(Locale.US, bytes / 1024.0.pow(group.toDouble()), units[group])
}
