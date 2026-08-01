package com.alastorkaneki.fileeditor.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.alastorkaneki.fileeditor.data.RobustMediaLoader
import com.alastorkaneki.fileeditor.media.AudioExportFormats
import com.alastorkaneki.fileeditor.media.AudioExportSettings
import com.alastorkaneki.fileeditor.media.AudioExportTarget
import com.alastorkaneki.fileeditor.media.MediaExportEngine
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioEffectsScreen(
    onExit: () -> Unit,
) {
    val context = LocalContext.current
    val exporter = remember { MediaExportEngine(context) }
    val player = remember { ExoPlayer.Builder(context).build() }
    val scope = rememberCoroutineScope()
    var sourceUri by remember { mutableStateOf<Uri?>(null) }
    var durationMs by remember { mutableStateOf(0L) }
    var settings by remember { mutableStateOf(AudioExportSettings()) }
    var outputName by remember { mutableStateOf("Edited-Audio") }
    var selectedFormatId by remember { mutableStateOf(AudioExportFormats.M4A_AAC.id) }
    var customExtension by remember { mutableStateOf("mp3") }
    var customEncoder by remember { mutableStateOf("libmp3lame") }
    var customMuxer by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    var previewFile by remember { mutableStateOf<File?>(null) }
    var previewRendering by remember { mutableStateOf(false) }
    var previewReady by remember { mutableStateOf(false) }
    var previewPlaying by remember { mutableStateOf(false) }
    var previewPositionMs by remember { mutableStateOf(0L) }
    var previewDurationMs by remember { mutableStateOf(0L) }
    var previewRevision by remember { mutableIntStateOf(0) }
    val latestPreviewFile by rememberUpdatedState(previewFile)

    fun clearPreview() {
        player.pause()
        player.stop()
        player.clearMediaItems()
        previewFile?.delete()
        previewFile = null
        previewReady = false
        previewPlaying = false
        previewPositionMs = 0L
        previewDurationMs = 0L
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                previewPlaying = isPlaying
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    previewDurationMs = player.duration.takeIf { it > 0L } ?: 0L
                } else if (playbackState == Player.STATE_ENDED) {
                    previewPlaying = false
                    previewPositionMs = previewDurationMs
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                previewPlaying = false
                message = "Preview playback failed: ${error.message ?: error.errorCodeName}"
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
            latestPreviewFile?.delete()
        }
    }

    LaunchedEffect(previewPlaying) {
        while (previewPlaying) {
            previewPositionMs = player.currentPosition.coerceAtLeast(0L)
            previewDurationMs = player.duration.takeIf { it > 0L } ?: previewDurationMs
            delay(150L)
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            RobustMediaLoader.persistReadPermission(context, uri)
            sourceUri = uri
            message = null
        }
    }

    LaunchedEffect(sourceUri) {
        val uri = sourceUri ?: return@LaunchedEffect
        loading = true
        runCatching { withContext(Dispatchers.IO) { RobustMediaLoader.readDurationMs(context, uri) } }
            .onSuccess { duration ->
                durationMs = duration
                settings = AudioExportSettings(endMs = duration)
            }
            .onFailure { message = "Unable to read audio: ${it.message ?: it::class.java.simpleName}" }
        loading = false
    }

    LaunchedEffect(sourceUri, settings) {
        previewRevision += 1
        clearPreview()
    }

    fun normalizedSettings(): AudioExportSettings {
        val safeDuration = durationMs.coerceAtLeast(1L)
        val start = settings.startMs.coerceIn(0L, safeDuration - 1L)
        val end = settings.endMs.coerceIn(start + 1L, safeDuration)
        return settings.copy(startMs = start, endMs = end)
    }

    fun currentExportTarget(): AudioExportTarget =
        AudioExportFormats.presets.firstOrNull { it.id == selectedFormatId }
            ?: AudioExportFormats.custom(
                extension = customExtension,
                encoder = customEncoder,
                muxer = customMuxer,
            )

    fun preset(name: String) {
        settings = when (name) {
            "Nightcore" -> settings.copy(speed = 1.28f, pitch = 1.22f, sampleRateHz = 48000)
            "Slowed" -> settings.copy(speed = 0.78f, pitch = 0.86f, sampleRateHz = 44100)
            "Vaporwave" -> settings.copy(speed = 0.72f, pitch = 0.76f, sampleRateHz = 44100)
            "Chipmunk" -> settings.copy(speed = 1f, pitch = 1.65f, sampleRateHz = 48000)
            "Deep voice" -> settings.copy(speed = 1f, pitch = 0.62f, sampleRateHz = 44100)
            "Telephone" -> settings.copy(speed = 1f, pitch = 1f, sampleRateHz = 16000)
            "Podcast" -> settings.copy(speed = 1.08f, pitch = 1f, sampleRateHz = 48000)
            else -> AudioExportSettings(endMs = durationMs.coerceAtLeast(1L))
        }
    }

    fun renderPreview() {
        val uri = sourceUri ?: return
        if (previewRendering || durationMs <= 0L) return
        val requestedSettings = normalizedSettings()
        val requestRevision = previewRevision + 1
        previewRevision = requestRevision
        clearPreview()

        scope.launch {
            previewRendering = true
            message = null
            runCatching {
                exporter.renderAudioPreview(
                    input = uri,
                    settings = requestedSettings,
                    maxSourceDurationMs = 15_000L,
                )
            }.onSuccess { file ->
                if (previewRevision != requestRevision) {
                    file.delete()
                } else {
                    previewFile = file
                    previewReady = true
                    player.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
                    player.prepare()
                    player.playWhenReady = true
                    message = "Preview ready: exact effects rendered from the first 15 seconds of the selected range"
                }
            }.onFailure { error ->
                if (previewRevision == requestRevision) {
                    message = "Preview render failed: ${error.message ?: error::class.java.simpleName}"
                }
            }
            previewRendering = false
        }
    }

    fun export() {
        val uri = sourceUri ?: return
        if (exporting) return
        val target = runCatching(::currentExportTarget).getOrElse { error ->
            message = error.message ?: "Invalid output format"
            return
        }
        scope.launch {
            exporting = true
            message = null
            runCatching {
                exporter.exportAudio(
                    input = uri,
                    settings = normalizedSettings(),
                    displayName = outputName,
                    target = target,
                )
            }.onSuccess {
                message = "Saved ${audioOutputName(outputName, target.extension)} to Music/FileEditor"
            }.onFailure {
                message = "Audio export failed: ${it.message ?: it::class.java.simpleName}"
            }
            exporting = false
        }
    }

    val selectedTarget = runCatching(::currentExportTarget).getOrNull()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Audio Effects")
                        Text(
                            "Trim • speed • pitch • preview • export formats",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onExit, enabled = !exporting && !previewRendering) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { preset("Reset") }, enabled = sourceUri != null) {
                        Icon(Icons.Default.RestartAlt, contentDescription = "Reset")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (loading) CircularProgressIndicator()
                        else Icon(Icons.Default.GraphicEq, contentDescription = null, modifier = Modifier.size(90.dp))
                        Text(
                            when {
                                loading -> "Reading audio…"
                                sourceUri == null -> "Choose an audio file"
                                else -> "${formatAudioTime(durationMs)} selected"
                            },
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                }
            }
            item {
                Button(
                    onClick = { picker.launch(arrayOf("audio/*")) },
                    enabled = !exporting && !previewRendering,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.AudioFile, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (sourceUri == null) "Choose audio" else "Choose another audio file")
                }
            }

            if (durationMs > 0L) {
                item {
                    Text("Trim", style = MaterialTheme.typography.headlineSmall)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(formatAudioTime(settings.startMs))
                        Text(formatAudioTime(settings.endMs), color = MaterialTheme.colorScheme.primary)
                    }
                    RangeSlider(
                        value = settings.startMs.toFloat()..settings.endMs.toFloat(),
                        onValueChange = { range ->
                            settings = settings.copy(
                                startMs = range.start.roundToInt().toLong(),
                                endMs = range.endInclusive.roundToInt().toLong(),
                            )
                        },
                        valueRange = 0f..durationMs.toFloat().coerceAtLeast(1f),
                        enabled = !previewRendering,
                    )
                    Text(
                        "Output length before speed change: ${formatAudioTime((settings.endMs - settings.startMs).coerceAtLeast(0L))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item { Text("Presets", style = MaterialTheme.typography.headlineSmall) }
            item { AudioChoices(listOf("Reset", "Nightcore", "Slowed"), !previewRendering, ::preset) }
            item { AudioChoices(listOf("Vaporwave", "Chipmunk", "Deep voice"), !previewRendering, ::preset) }
            item { AudioChoices(listOf("Telephone", "Podcast", "Original"), !previewRendering, ::preset) }

            item { Text("Time and tone", style = MaterialTheme.typography.headlineSmall) }
            item {
                AudioSlider(
                    "Speed",
                    "${"%.2f".format(settings.speed)}×",
                    settings.speed,
                    0.25f..4f,
                    !previewRendering,
                ) { settings = settings.copy(speed = it) }
            }
            item {
                AudioSlider(
                    "Pitch",
                    "${"%.2f".format(settings.pitch)}×",
                    settings.pitch,
                    0.25f..4f,
                    !previewRendering,
                ) { settings = settings.copy(pitch = it) }
            }
            item {
                Text(
                    "Speed and pitch are independent: make slowed audio without lowering pitch, or alter the voice without changing length.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            item { Text("Output sample rate", style = MaterialTheme.typography.headlineSmall) }
            item {
                AudioChoices(listOf("Original", "8 kHz", "16 kHz"), !previewRendering) { value ->
                    settings = settings.copy(sampleRateHz = when (value) {
                        "8 kHz" -> 8000
                        "16 kHz" -> 16000
                        else -> 0
                    })
                }
            }
            item {
                AudioChoices(listOf("22 kHz", "44.1 kHz", "48 kHz"), !previewRendering) { value ->
                    settings = settings.copy(sampleRateHz = when (value) {
                        "22 kHz" -> 22050
                        "44.1 kHz" -> 44100
                        else -> 48000
                    })
                }
            }
            item {
                AudioChoices(listOf("88.2 kHz", "96 kHz", "192 kHz"), !previewRendering) { value ->
                    settings = settings.copy(sampleRateHz = when (value) {
                        "88.2 kHz" -> 88200
                        "96 kHz" -> 96000
                        else -> 192000
                    })
                }
            }

            item { Text("Effect preview", style = MaterialTheme.typography.headlineSmall) }
            item {
                Button(
                    onClick = ::renderPreview,
                    enabled = sourceUri != null && durationMs > 0L && !previewRendering && !exporting,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (previewRendering) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(if (previewRendering) "Rendering preview…" else "Render and play 15-second preview")
                }
            }

            if (previewReady) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilledTonalButton(
                            onClick = {
                                if (player.isPlaying) {
                                    player.pause()
                                } else {
                                    if (player.playbackState == Player.STATE_ENDED) player.seekTo(0L)
                                    player.play()
                                }
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(
                                if (previewPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = null,
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(if (previewPlaying) "Pause" else "Play")
                        }
                        FilledTonalButton(
                            onClick = {
                                player.seekTo(0L)
                                player.play()
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Default.Replay, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("Replay")
                        }
                        FilledTonalButton(
                            onClick = {
                                player.pause()
                                player.seekTo(0L)
                                previewPositionMs = 0L
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("Stop")
                        }
                    }
                }
                item {
                    val safePreviewDuration = previewDurationMs.coerceAtLeast(1L)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(formatAudioTime(previewPositionMs))
                        Text(formatAudioTime(previewDurationMs), color = MaterialTheme.colorScheme.primary)
                    }
                    Slider(
                        value = previewPositionMs.coerceIn(0L, safePreviewDuration).toFloat(),
                        onValueChange = { value ->
                            previewPositionMs = value.toLong()
                            player.seekTo(value.toLong())
                        },
                        valueRange = 0f..safePreviewDuration.toFloat(),
                    )
                }
            }

            item {
                Text(
                    "Preview and export use the same FFmpeg trim, speed, pitch and sample-rate filter chain. The selected output codec can still add its own compression characteristics.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            item { Text("Output format", style = MaterialTheme.typography.headlineSmall) }
            AudioExportFormats.presets.chunked(3).forEach { formats ->
                item {
                    AudioFormatChoices(
                        formats = formats,
                        selectedId = selectedFormatId,
                        enabled = !exporting && !previewRendering,
                    ) { selectedFormatId = it.id }
                }
            }
            item {
                FilledTonalButton(
                    onClick = { selectedFormatId = AudioExportFormats.CUSTOM_ID },
                    enabled = !exporting && !previewRendering,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (selectedFormatId == AudioExportFormats.CUSTOM_ID) "✓ Custom FFmpeg format" else "Custom FFmpeg format")
                }
            }

            if (selectedFormatId == AudioExportFormats.CUSTOM_ID) {
                item {
                    OutlinedTextField(
                        value = customExtension,
                        onValueChange = { customExtension = it },
                        label = { Text("File extension, for example ape or mka") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !exporting,
                    )
                }
                item {
                    OutlinedTextField(
                        value = customEncoder,
                        onValueChange = { customEncoder = it },
                        label = { Text("FFmpeg audio encoder; blank = automatic") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !exporting,
                    )
                }
                item {
                    OutlinedTextField(
                        value = customMuxer,
                        onValueChange = { customMuxer = it },
                        label = { Text("FFmpeg muxer; blank = infer from extension") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !exporting,
                    )
                }
            }

            selectedTarget?.let { target ->
                item {
                    Text(
                        "Selected: ${target.label} — ${target.description}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item { Text("Export", style = MaterialTheme.typography.headlineSmall) }
            item {
                OutlinedTextField(
                    value = outputName,
                    onValueChange = { outputName = it },
                    label = { Text("Output filename") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !exporting,
                )
            }
            message?.let { text ->
                item {
                    Text(
                        text,
                        color = if (text.startsWith("Saved") || text.startsWith("Preview ready")) {
                            MaterialTheme.colorScheme.tertiary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )
                }
            }
            item {
                Button(
                    onClick = ::export,
                    enabled = sourceUri != null && durationMs > 0L && !exporting && !previewRendering,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (exporting) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Default.Save, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (exporting) {
                            "Encoding audio…"
                        } else {
                            "Export ${selectedTarget?.label ?: "custom audio"}"
                        },
                    )
                }
            }
            item {
                Text(
                    "Common formats are configured automatically. Custom mode can request any encoder and container included in the bundled FFmpeg build; unsupported combinations return the FFmpeg error instead of silently changing formats.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AudioChoices(
    labels: List<String>,
    enabled: Boolean,
    onClick: (String) -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.forEach { label ->
            FilledTonalButton(
                onClick = { onClick(label) },
                modifier = Modifier.weight(1f),
                enabled = enabled,
            ) {
                Text(label)
            }
        }
    }
}

@Composable
private fun AudioFormatChoices(
    formats: List<AudioExportTarget>,
    selectedId: String,
    enabled: Boolean,
    onClick: (AudioExportTarget) -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        formats.forEach { format ->
            FilledTonalButton(
                onClick = { onClick(format) },
                modifier = Modifier.weight(1f),
                enabled = enabled,
            ) {
                Text(if (selectedId == format.id) "✓ ${format.label}" else format.label)
            }
        }
        repeat((3 - formats.size).coerceAtLeast(0)) {
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun AudioSlider(
    label: String,
    valueLabel: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    onChange: (Float) -> Unit,
) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            Text(valueLabel, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange,
            valueRange = range,
            enabled = enabled,
        )
    }
}

private fun audioOutputName(name: String, extension: String): String {
    val safe = name.trim().ifBlank { "Edited-Audio" }
    return if (safe.endsWith(".$extension", ignoreCase = true)) safe else "$safe.$extension"
}

private fun formatAudioTime(milliseconds: Long): String {
    val totalSeconds = milliseconds.coerceAtLeast(0L) / 1000L
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%d:%02d".format(minutes, seconds)
}
