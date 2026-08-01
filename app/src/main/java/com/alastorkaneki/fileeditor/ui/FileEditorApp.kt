package com.alastorkaneki.fileeditor.ui

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LibraryMusic
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alastorkaneki.fileeditor.EditorSession
import com.alastorkaneki.fileeditor.FileEditorViewModel
import com.alastorkaneki.fileeditor.data.AudioTrack
import com.alastorkaneki.fileeditor.data.EditorDraft
import com.alastorkaneki.fileeditor.data.TagData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.ln
import kotlin.math.pow

private val AppColors = darkColorScheme(
    primary = Color(0xFFB99CFF),
    secondary = Color(0xFFFF6B8B),
    background = Color(0xFF0C0A12),
    surface = Color(0xFF15111F),
    surfaceVariant = Color(0xFF211A2F),
)

@Composable
fun FileEditorApp(
    viewModel: FileEditorViewModel,
    onRequestPermission: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    MaterialTheme(colorScheme = AppColors) {
        when (val editor = state.editor) {
            null -> LibraryScreen(
                hasPermission = state.hasPermission,
                isScanning = state.isScanning,
                tracks = state.visibleTracks,
                totalCount = state.allTracks.size,
                query = state.query,
                snackbarHostState = snackbarHostState,
                onQueryChange = viewModel::setQuery,
                onRequestPermission = onRequestPermission,
                onRefresh = viewModel::scan,
                onTrackClick = viewModel::openEditor,
            )
            else -> EditorScreen(
                session = editor,
                isSaving = state.isSaving,
                snackbarHostState = snackbarHostState,
                onBack = viewModel::closeEditor,
                onSave = { draft -> viewModel.save(editor.track, draft) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryScreen(
    hasPermission: Boolean,
    isScanning: Boolean,
    tracks: List<AudioTrack>,
    totalCount: Int,
    query: String,
    snackbarHostState: SnackbarHostState,
    onQueryChange: (String) -> Unit,
    onRequestPermission: () -> Unit,
    onRefresh: () -> Unit,
    onTrackClick: (AudioTrack) -> Unit,
) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("File Editor")
                        Text(
                            text = if (hasPermission) "$totalCount audio files" else "Audio metadata editor",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh, enabled = hasPermission && !isScanning) {
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
            PermissionPanel(
                modifier = Modifier.padding(padding),
                onRequestPermission = onRequestPermission,
            )
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                singleLine = true,
                label = { Text("Search title, artist, album, filename or folder") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            )

            when {
                isScanning && tracks.isEmpty() -> LoadingPanel("Scanning all device audio…")
                tracks.isEmpty() -> EmptyPanel(query)
                else -> LazyColumn(
                    contentPadding = PaddingValues(bottom = 24.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(tracks, key = { it.uri.toString() }) { track ->
                        AudioRow(track = track, onClick = { onTrackClick(track) })
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionPanel(modifier: Modifier, onRequestPermission: () -> Unit) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Card(
            modifier = Modifier.padding(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(Icons.Default.LibraryMusic, contentDescription = null, modifier = Modifier.size(52.dp))
                Text("Audio access required", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "File Editor scans Android's shared audio library across internal storage, SD cards and attached media volumes. Private app folders remain protected by Android.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = onRequestPermission) { Text("Grant audio access") }
            }
        }
    }
}

@Composable
private fun LoadingPanel(label: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(12.dp))
            Text(label)
        }
    }
}

@Composable
private fun EmptyPanel(query: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(if (query.isBlank()) "No audio files found" else "No matches for “$query”")
    }
}

@Composable
private fun AudioRow(track: AudioTrack, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.AudioFile, contentDescription = null)
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title.ifBlank { track.displayName },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = listOf(track.artist, track.album).filter(String::isNotBlank).joinToString(" • ")
                    .ifBlank { track.displayName },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "${formatDuration(track.durationMs)} • ${formatBytes(track.sizeBytes)} • ${track.mimeType}",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(Icons.Default.Edit, contentDescription = "Edit metadata")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorScreen(
    session: EditorSession,
    isSaving: Boolean,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onSave: (EditorDraft) -> Unit,
) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        session.track.title.ifBlank { session.track.displayName },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !isSaving) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (session.tags != null) {
                        IconButton(onClick = {}, enabled = false) {
                            Icon(Icons.Default.Save, contentDescription = null)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        when {
            session.isLoading -> Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            session.error != null -> Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Unable to edit this file", style = MaterialTheme.typography.headlineSmall)
                    Text(session.error, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = onBack) { Text("Back") }
                }
            }
            session.tags != null -> LoadedEditor(
                modifier = Modifier.padding(padding),
                track = session.track,
                initialTags = session.tags,
                isSaving = isSaving,
                onSave = onSave,
            )
        }
    }
}

@Composable
private fun LoadedEditor(
    modifier: Modifier,
    track: AudioTrack,
    initialTags: TagData,
    isSaving: Boolean,
    onSave: (EditorDraft) -> Unit,
) {
    var draft by remember(track.uri.toString(), initialTags) {
        mutableStateOf(EditorDraft(initialTags))
    }
    var artworkError by remember { mutableStateOf<String?>(null) }
    var artworkLoading by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val artworkPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            artworkLoading = true
            artworkError = null
            runCatching {
                withContext(Dispatchers.IO) {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: error("Could not open the selected image")
                    require(bytes.size <= 15 * 1024 * 1024) { "Artwork must be 15 MB or smaller" }
                    require(BitmapFactory.decodeByteArray(bytes, 0, bytes.size) != null) {
                        "The selected file is not a supported image"
                    }
                    bytes to context.contentResolver.getType(uri)
                }
            }.onSuccess { (bytes, mime) ->
                draft = draft.copy(
                    fields = draft.fields.copy(
                        artworkBytes = bytes,
                        artworkMimeType = mime ?: "image/jpeg",
                    ),
                    artworkChanged = true,
                )
            }.onFailure { artworkError = it.message ?: "Unable to load artwork" }
            artworkLoading = false
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ArtworkEditor(
                bytes = draft.fields.artworkBytes,
                loading = artworkLoading,
                error = artworkError,
                onPick = { artworkPicker.launch("image/*") },
                onRemove = {
                    draft = draft.copy(
                        fields = draft.fields.copy(artworkBytes = null, artworkMimeType = null),
                        artworkChanged = true,
                    )
                },
            )
        }

        item { SectionTitle("Core tags") }
        item { MetadataField("Title", draft.fields.title) { draft = draft.withFields { copy(title = it) } } }
        item { MetadataField("Artist", draft.fields.artist) { draft = draft.withFields { copy(artist = it) } } }
        item { MetadataField("Album", draft.fields.album) { draft = draft.withFields { copy(album = it) } } }
        item { MetadataField("Album artist", draft.fields.albumArtist) { draft = draft.withFields { copy(albumArtist = it) } } }
        item { MetadataField("Genre", draft.fields.genre) { draft = draft.withFields { copy(genre = it) } } }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetadataField(
                    label = "Year",
                    value = draft.fields.year,
                    modifier = Modifier.weight(1f),
                    keyboardType = KeyboardType.Number,
                ) { draft = draft.withFields { copy(year = it) } }
                MetadataField(
                    label = "Track",
                    value = draft.fields.trackNumber,
                    modifier = Modifier.weight(1f),
                ) { draft = draft.withFields { copy(trackNumber = it) } }
                MetadataField(
                    label = "Disc",
                    value = draft.fields.discNumber,
                    modifier = Modifier.weight(1f),
                ) { draft = draft.withFields { copy(discNumber = it) } }
            }
        }

        item { SectionTitle("Credits and identifiers") }
        item { MetadataField("Composer", draft.fields.composer) { draft = draft.withFields { copy(composer = it) } } }
        item { MetadataField("Publisher / label", draft.fields.publisher) { draft = draft.withFields { copy(publisher = it) } } }
        item { MetadataField("ISRC", draft.fields.isrc) { draft = draft.withFields { copy(isrc = it) } } }
        item { MetadataField("BPM", draft.fields.bpm, keyboardType = KeyboardType.Number) { draft = draft.withFields { copy(bpm = it) } } }
        item { MetadataField("Copyright", draft.fields.copyright) { draft = draft.withFields { copy(copyright = it) } } }

        item { SectionTitle("Text") }
        item {
            MetadataField(
                label = "Comment",
                value = draft.fields.comment,
                singleLine = false,
                minLines = 3,
            ) { draft = draft.withFields { copy(comment = it) } }
        }
        item {
            MetadataField(
                label = "Lyrics",
                value = draft.fields.lyrics,
                singleLine = false,
                minLines = 10,
            ) { draft = draft.withFields { copy(lyrics = it) } }
        }

        item {
            Text(
                text = "${track.displayName}\n${track.relativePath.ifBlank { track.volumeName }}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        item {
            Button(
                onClick = { onSave(draft) },
                enabled = !isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding(),
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Saving…")
                } else {
                    Icon(Icons.Default.Save, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Save metadata")
                }
            }
        }
    }
}

@Composable
private fun ArtworkEditor(
    bytes: ByteArray?,
    loading: Boolean,
    error: String?,
    onPick: () -> Unit,
    onRemove: () -> Unit,
) {
    val bitmap = remember(bytes) {
        bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }
    }

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Artwork", style = MaterialTheme.typography.titleLarge)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    loading -> CircularProgressIndicator()
                    bitmap != null -> Image(
                        bitmap = bitmap,
                        contentDescription = "Embedded artwork",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                    else -> Icon(
                        Icons.Default.AddPhotoAlternate,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onPick, enabled = !loading) {
                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (bytes == null) "Choose photo" else "Replace photo")
                }
                TextButton(onClick = onRemove, enabled = bytes != null && !loading) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Remove")
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun MetadataField(
    label: String,
    value: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    singleLine: Boolean = true,
    minLines: Int = 1,
    keyboardType: KeyboardType = KeyboardType.Text,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier,
        singleLine = singleLine,
        minLines = minLines,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
    )
}

private inline fun EditorDraft.withFields(transform: TagData.() -> TagData): EditorDraft =
    copy(fields = fields.transform())

private fun formatDuration(milliseconds: Long): String {
    val totalSeconds = milliseconds.coerceAtLeast(0) / 1_000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(Locale.US, minutes, seconds)
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    val group = (ln(bytes.toDouble()) / ln(1024.0)).toInt().coerceIn(0, units.lastIndex)
    return "%.1f %s".format(Locale.US, bytes / 1024.0.pow(group.toDouble()), units[group])
}
