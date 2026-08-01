package com.alastorkaneki.fileeditor.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alastorkaneki.fileeditor.FileEditorViewModel
import com.alastorkaneki.fileeditor.data.VisualMediaKind

private enum class StudioMode {
    HOME,
    AUDIO,
    PHOTOS,
    VIDEOS,
    GLITCH,
    GIF,
}

private data class StudioTool(
    val mode: StudioMode,
    val title: String,
    val description: String,
    val icon: ImageVector,
)

private val StudioColors = darkColorScheme(
    primary = Color(0xFFB99CFF),
    secondary = Color(0xFFFF6B8B),
    tertiary = Color(0xFF6CE5D8),
    background = Color(0xFF0C0A12),
    surface = Color(0xFF15111F),
    surfaceVariant = Color(0xFF211A2F),
)

@Composable
fun StudioApp(
    viewModel: FileEditorViewModel,
    hasAudioPermission: Boolean,
    hasImagePermission: Boolean,
    hasVideoPermission: Boolean,
    onRequestAllPermissions: () -> Unit,
) {
    var mode by remember { mutableStateOf(StudioMode.HOME) }
    var initialGlitchUri by remember { mutableStateOf<Uri?>(null) }
    var initialGifVideoUri by remember { mutableStateOf<Uri?>(null) }
    val audioState by viewModel.state.collectAsStateWithLifecycle()

    BackHandler(enabled = mode != StudioMode.HOME) {
        if (mode == StudioMode.AUDIO && audioState.editor != null) {
            viewModel.closeEditor()
        } else {
            mode = StudioMode.HOME
        }
    }

    MaterialTheme(colorScheme = StudioColors) {
        when (mode) {
            StudioMode.HOME -> StudioHomeScreen(onOpen = { mode = it })
            StudioMode.AUDIO -> FileEditorApp(
                viewModel = viewModel,
                onRequestPermission = onRequestAllPermissions,
            )
            StudioMode.PHOTOS -> VisualLibraryScreen(
                kind = VisualMediaKind.IMAGE,
                hasPermission = hasImagePermission,
                onRequestPermission = onRequestAllPermissions,
                onExit = { mode = StudioMode.HOME },
                onOpenGlitch = { uri ->
                    initialGlitchUri = uri
                    mode = StudioMode.GLITCH
                },
                onOpenGif = {},
            )
            StudioMode.VIDEOS -> VisualLibraryScreen(
                kind = VisualMediaKind.VIDEO,
                hasPermission = hasVideoPermission,
                onRequestPermission = onRequestAllPermissions,
                onExit = { mode = StudioMode.HOME },
                onOpenGlitch = {},
                onOpenGif = { uri ->
                    initialGifVideoUri = uri
                    mode = StudioMode.GIF
                },
            )
            StudioMode.GLITCH -> GlitchScreen(
                initialUri = initialGlitchUri,
                onExit = {
                    initialGlitchUri = null
                    mode = StudioMode.HOME
                },
            )
            StudioMode.GIF -> GifMakerScreen(
                initialVideoUri = initialGifVideoUri,
                onExit = {
                    initialGifVideoUri = null
                    mode = StudioMode.HOME
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StudioHomeScreen(onOpen: (StudioMode) -> Unit) {
    val tools = remember {
        listOf(
            StudioTool(
                StudioMode.AUDIO,
                "Audio metadata",
                "Search your music and edit tags, lyrics and cover art.",
                Icons.Default.AudioFile,
            ),
            StudioTool(
                StudioMode.PHOTOS,
                "Photo editor",
                "Browse photos, edit EXIF metadata and send them to Glitch Lab.",
                Icons.Default.PhotoLibrary,
            ),
            StudioTool(
                StudioMode.VIDEOS,
                "Video editor",
                "Browse videos, rename and edit catalog metadata, or turn them into GIFs.",
                Icons.Default.Movie,
            ),
            StudioTool(
                StudioMode.GLITCH,
                "Glitch Lab",
                "Realtime RGB split, slice, block, noise and scanline previews.",
                Icons.Default.AutoFixHigh,
            ),
            StudioTool(
                StudioMode.GIF,
                "GIF maker",
                "Build looping GIFs from images or video, with optional animated glitches.",
                Icons.Default.Animation,
            ),
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("File Editor")
                        Text(
                            "Audio • Photos • Videos • Glitch • GIF",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "Media Studio",
                    style = MaterialTheme.typography.headlineMedium,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Everything stays on-device. Pick a tool to begin.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
            }
            items(tools, key = { it.mode.name }) { tool ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpen(tool.mode) },
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                ) {
                    Row(
                        modifier = Modifier.padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            tool.icon,
                            contentDescription = null,
                            modifier = Modifier.size(38.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 16.dp),
                        ) {
                            Text(tool.title, style = MaterialTheme.typography.titleLarge)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                tool.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null)
                    }
                }
            }
        }
    }
}
