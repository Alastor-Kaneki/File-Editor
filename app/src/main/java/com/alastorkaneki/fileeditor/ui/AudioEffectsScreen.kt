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
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Save
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
import com.alastorkaneki.fileeditor.data.RobustMediaLoader
import com.alastorkaneki.fileeditor.media.AudioExportSettings
import com.alastorkaneki.fileeditor.media.MediaExportEngine
import kotlinx.coroutines.Dispatchers
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
    val scope = rememberCoroutineScope()
    var sourceUri by remember { mutableStateOf<Uri?>(null) }
    var durationMs by remember { mutableStateOf(0L) }
    var settings by remember { mutableStateOf(AudioExportSettings()) }
    var outputName by remember { mutableStateOf("Edited-Audio") }
    var loading by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

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

    fun export() {
        val uri = sourceUri ?: return
        if (exporting) return
        scope.launch {
            exporting = true
            message = null
            runCatching {
                exporter.exportAudio(
                    input = uri,
                    settings = settings.copy(
                        startMs = settings.startMs.coerceIn(0L, durationMs.coerceAtLeast(1L) - 1L),
                        endMs = settings.endMs.coerceIn(settings.startMs + 1L, durationMs.coerceAtLeast(1L)),
                    ),
                    displayName = outputName,
                )
            }.onSuccess { message = "Saved ${outputName}.m4a to Music/FileEditor" }
                .onFailure { message = "Audio export failed: ${it.message ?: it::class.java.simpleName}" }
            exporting = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Audio Effects")
                        Text(
                            "Trim • speed • pitch • sample rate • convert",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onExit, enabled = !exporting) {
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
                    enabled = !exporting,
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
                    )
                    Text(
                        "Output length before speed change: ${formatAudioTime((settings.endMs - settings.startMs).coerceAtLeast(0L))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item { Text("Presets", style = MaterialTheme.typography.headlineSmall) }
            item { AudioChoices(listOf("Reset", "Nightcore", "Slowed"), ::preset) }
            item { AudioChoices(listOf("Vaporwave", "Chipmunk", "Deep voice"), ::preset) }
            item { AudioChoices(listOf("Telephone", "Podcast", "Original"), ::preset) }

            item { Text("Time and tone", style = MaterialTheme.typography.headlineSmall) }
            item {
                AudioSlider(
                    "Speed",
                    "${"%.2f".format(settings.speed)}×",
                    settings.speed,
                    0.25f..4f,
                ) { settings = settings.copy(speed = it) }
            }
            item {
                AudioSlider(
                    "Pitch",
                    "${"%.2f".format(settings.pitch)}×",
                    settings.pitch,
                    0.25f..4f,
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
                AudioChoices(listOf("Original", "8 kHz", "16 kHz")) { value ->
                    settings = settings.copy(sampleRateHz = when (value) {
                        "8 kHz" -> 8000
                        "16 kHz" -> 16000
                        else -> 0
                    })
                }
            }
            item {
                AudioChoices(listOf("22 kHz", "44.1 kHz", "48 kHz")) { value ->
                    settings = settings.copy(sampleRateHz = when (value) {
                        "22 kHz" -> 22050
                        "44.1 kHz" -> 44100
                        else -> 48000
                    })
                }
            }
            item {
                AudioChoices(listOf("88.2 kHz", "96 kHz", "192 kHz")) { value ->
                    settings = settings.copy(sampleRateHz = when (value) {
                        "88.2 kHz" -> 88200
                        "96 kHz" -> 96000
                        else -> 192000
                    })
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
                )
            }
            message?.let { text ->
                item {
                    Text(
                        text,
                        color = if (text.startsWith("Saved")) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                    )
                }
            }
            item {
                Button(
                    onClick = ::export,
                    enabled = sourceUri != null && durationMs > 0L && !exporting,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (exporting) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Default.Save, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (exporting) "Encoding audio…" else "Export AAC / M4A")
                }
            }
        }
    }
}

@Composable
private fun AudioChoices(labels: List<String>, onClick: (String) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.forEach { label ->
            FilledTonalButton(onClick = { onClick(label) }, modifier = Modifier.weight(1f)) {
                Text(label)
            }
        }
    }
}

@Composable
private fun AudioSlider(
    label: String,
    valueLabel: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            Text(valueLabel, color = MaterialTheme.colorScheme.primary)
        }
        Slider(value = value.coerceIn(range.start, range.endInclusive), onValueChange = onChange, valueRange = range)
    }
}

private fun formatAudioTime(milliseconds: Long): String {
    val totalSeconds = milliseconds.coerceAtLeast(0L) / 1000L
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%d:%02d".format(minutes, seconds)
}
