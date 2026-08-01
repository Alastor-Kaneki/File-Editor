package com.alastorkaneki.fileeditor.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.RotateRight
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
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.alastorkaneki.fileeditor.data.RobustMediaLoader
import com.alastorkaneki.fileeditor.data.VisualMediaRepository
import com.alastorkaneki.fileeditor.media.MediaExportEngine
import com.alastorkaneki.fileeditor.media.VideoExportSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoEditorScreen(
    initialUri: Uri?,
    onExit: () -> Unit,
) {
    val context = LocalContext.current
    val repository = remember { VisualMediaRepository(context) }
    val exporter = remember { MediaExportEngine(context) }
    val scope = rememberCoroutineScope()
    var sourceUri by remember(initialUri) { mutableStateOf(initialUri) }
    var durationMs by remember { mutableStateOf(0L) }
    var previewPosition by remember { mutableStateOf(0f) }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var settings by remember { mutableStateOf(VideoExportSettings()) }
    var loading by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }
    var extracting by remember { mutableStateOf(false) }
    var outputName by remember { mutableStateOf("Edited-Video") }
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
                previewPosition = 0f
                settings = VideoExportSettings(endMs = duration)
            }
            .onFailure { message = "Unable to read video: ${it.message ?: it::class.java.simpleName}" }
        loading = false
    }

    LaunchedEffect(sourceUri, previewPosition, durationMs) {
        val uri = sourceUri ?: return@LaunchedEffect
        if (durationMs <= 0L) return@LaunchedEffect
        delay(90)
        runCatching {
            withContext(Dispatchers.IO) {
                RobustMediaLoader.loadVideoFrame(
                    context,
                    uri,
                    timeUs = (previewPosition * durationMs * 1000L).toLong(),
                    maxDimension = 1000,
                )
            }
        }.onSuccess { frame ->
            preview?.takeIf { it !== frame }?.recycle()
            preview = frame
        }.onFailure { message = "Preview failed: ${it.message ?: it::class.java.simpleName}" }
    }

    fun applyPreset(name: String) {
        settings = when (name) {
            "Cinematic" -> settings.copy(
                contrast = 0.2f,
                saturation = -12f,
                lightness = -4f,
                redScale = 1.06f,
                blueScale = 1.12f,
            )
            "Vivid" -> settings.copy(
                contrast = 0.16f,
                saturation = 28f,
                lightness = 5f,
                redScale = 1.05f,
                greenScale = 1.03f,
            )
            "Cold" -> settings.copy(redScale = 0.88f, greenScale = 1.02f, blueScale = 1.22f)
            "Warm" -> settings.copy(redScale = 1.22f, greenScale = 1.06f, blueScale = 0.86f)
            "Noir" -> settings.copy(grayscale = true, contrast = 0.35f, lightness = -5f)
            else -> VideoExportSettings(endMs = durationMs.coerceAtLeast(1L))
        }
    }

    fun export() {
        val uri = sourceUri ?: return
        if (exporting) return
        scope.launch {
            exporting = true
            message = null
            runCatching {
                exporter.exportVideo(
                    input = uri,
                    settings = settings.copy(
                        startMs = settings.startMs.coerceIn(0L, durationMs.coerceAtLeast(1L) - 1L),
                        endMs = settings.endMs.coerceIn(settings.startMs + 1L, durationMs.coerceAtLeast(1L)),
                    ),
                    displayName = outputName,
                )
            }.onSuccess { message = "Saved ${outputName}.mp4 to Movies/FileEditor" }
                .onFailure { message = "Video export failed: ${it.message ?: it::class.java.simpleName}" }
            exporting = false
        }
    }

    fun extractFrame() {
        val uri = sourceUri ?: return
        if (extracting || durationMs <= 0L) return
        scope.launch {
            extracting = true
            message = null
            val name = "Video-Frame-${System.currentTimeMillis()}.png"
            runCatching {
                val frame = withContext(Dispatchers.IO) {
                    RobustMediaLoader.loadVideoFrame(
                        context,
                        uri,
                        (previewPosition * durationMs * 1000L).toLong(),
                        2400,
                    )
                }
                try {
                    withContext(Dispatchers.IO) {
                        repository.saveBitmapToGallery(frame, name, Bitmap.CompressFormat.PNG, 100)
                    }
                } finally {
                    frame.recycle()
                }
            }.onSuccess { message = "Saved $name to Pictures/FileEditor" }
                .onFailure { message = "Frame extraction failed: ${it.message ?: it::class.java.simpleName}" }
            extracting = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Video Studio")
                        Text(
                            "Trim • transform • color • audio • export",
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
                    IconButton(onClick = { applyPreset("Reset") }, enabled = sourceUri != null) {
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(330.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        loading -> CircularProgressIndicator()
                        preview != null -> Image(
                            bitmap = requireNotNull(preview).asImageBitmap(),
                            contentDescription = "Video frame preview",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                        )
                        else -> Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(Icons.Default.Movie, contentDescription = null, modifier = Modifier.size(62.dp))
                            Text("Choose a video to edit")
                        }
                    }
                }
            }

            item {
                Button(
                    onClick = { picker.launch(arrayOf("video/*")) },
                    enabled = !exporting,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Movie, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (sourceUri == null) "Choose video" else "Choose another video")
                }
            }

            if (durationMs > 0L) {
                item {
                    VideoSlider(
                        "Preview position",
                        formatTime((previewPosition * durationMs).toLong()),
                        previewPosition,
                        0f..1f,
                    ) { previewPosition = it }
                }
                item {
                    Text("Trim", style = MaterialTheme.typography.headlineSmall)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(formatTime(settings.startMs))
                        Text(formatTime(settings.endMs), color = MaterialTheme.colorScheme.primary)
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
                        "Output length: ${formatTime((settings.endMs - settings.startMs).coerceAtLeast(0L))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item { Text("Presets", style = MaterialTheme.typography.headlineSmall) }
            item { VideoChoices(listOf("Reset", "Cinematic", "Vivid"), ::applyPreset) }
            item { VideoChoices(listOf("Cold", "Warm", "Noir"), ::applyPreset) }

            item { Text("Transform", style = MaterialTheme.typography.headlineSmall) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = { settings = settings.copy(rotationDegrees = settings.rotationDegrees - 90f) },
                        modifier = Modifier.weight(1f),
                    ) { Icon(Icons.Default.RotateLeft, null); Spacer(Modifier.width(5.dp)); Text("Rotate left") }
                    FilledTonalButton(
                        onClick = { settings = settings.copy(rotationDegrees = settings.rotationDegrees + 90f) },
                        modifier = Modifier.weight(1f),
                    ) { Icon(Icons.Default.RotateRight, null); Spacer(Modifier.width(5.dp)); Text("Rotate right") }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = { settings = settings.copy(flipHorizontal = !settings.flipHorizontal) },
                        modifier = Modifier.weight(1f),
                    ) { Icon(Icons.Default.Flip, null); Spacer(Modifier.width(5.dp)); Text("Flip H") }
                    FilledTonalButton(
                        onClick = { settings = settings.copy(flipVertical = !settings.flipVertical) },
                        modifier = Modifier.weight(1f),
                    ) { Icon(Icons.Default.Flip, null); Spacer(Modifier.width(5.dp)); Text("Flip V") }
                }
            }
            item {
                Text("Resolution", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(6.dp))
                VideoChoices(listOf("Original", "480p", "720p")) { value ->
                    settings = settings.copy(outputHeight = when (value) {
                        "480p" -> 480
                        "720p" -> 720
                        else -> 0
                    })
                }
                Spacer(Modifier.height(8.dp))
                VideoChoices(listOf("1080p", "1440p", "2160p")) { value ->
                    settings = settings.copy(outputHeight = value.removeSuffix("p").toInt())
                }
            }
            item {
                Text("Frame rate", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(6.dp))
                VideoChoices(listOf("Original", "24 FPS", "30 FPS")) { value ->
                    settings = settings.copy(frameRate = value.substringBefore(' ').toIntOrNull() ?: 0)
                }
                Spacer(Modifier.height(8.dp))
                VideoChoices(listOf("48 FPS", "60 FPS", "120 FPS")) { value ->
                    settings = settings.copy(frameRate = value.substringBefore(' ').toInt())
                }
            }

            item { Text("Color grading", style = MaterialTheme.typography.headlineSmall) }
            item { VideoSlider("Brightness", pctVideo(settings.brightness), settings.brightness, -1f..1f) { settings = settings.copy(brightness = it) } }
            item { VideoSlider("Contrast", pctVideo(settings.contrast), settings.contrast, -1f..1f) { settings = settings.copy(contrast = it) } }
            item { VideoSlider("Saturation", "${settings.saturation.roundToInt()}", settings.saturation, -100f..100f) { settings = settings.copy(saturation = it) } }
            item { VideoSlider("Hue", "${settings.hue.roundToInt()}°", settings.hue, -180f..180f) { settings = settings.copy(hue = it) } }
            item { VideoSlider("Lightness", "${settings.lightness.roundToInt()}", settings.lightness, -100f..100f) { settings = settings.copy(lightness = it) } }
            item { VideoSlider("Red channel", "${(settings.redScale * 100).roundToInt()}%", settings.redScale, 0f..2f) { settings = settings.copy(redScale = it) } }
            item { VideoSlider("Green channel", "${(settings.greenScale * 100).roundToInt()}%", settings.greenScale, 0f..2f) { settings = settings.copy(greenScale = it) } }
            item { VideoSlider("Blue channel", "${(settings.blueScale * 100).roundToInt()}%", settings.blueScale, 0f..2f) { settings = settings.copy(blueScale = it) } }
            item { VideoToggle("Black and white", settings.grayscale) { settings = settings.copy(grayscale = it) } }
            item { VideoToggle("Invert colors", settings.invert) { settings = settings.copy(invert = it) } }

            item { Text("Audio and stills", style = MaterialTheme.typography.headlineSmall) }
            item { VideoToggle("Mute video", settings.mute) { settings = settings.copy(mute = it) } }
            item {
                FilledTonalButton(
                    onClick = ::extractFrame,
                    enabled = sourceUri != null && !extracting,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (extracting) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Default.PhotoCamera, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Save current frame as PNG")
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
                    Text(if (exporting) "Encoding video…" else "Export MP4")
                }
            }
        }
    }
}

@Composable
private fun VideoChoices(labels: List<String>, onClick: (String) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.forEach { label ->
            FilledTonalButton(onClick = { onClick(label) }, modifier = Modifier.weight(1f)) {
                Text(label)
            }
        }
    }
}

@Composable
private fun VideoToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    FilledTonalButton(onClick = { onChange(!checked) }, modifier = Modifier.fillMaxWidth()) {
        Text(label)
        Spacer(Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun VideoSlider(
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

private fun formatTime(milliseconds: Long): String {
    val totalSeconds = (milliseconds.coerceAtLeast(0L) / 1000L)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%d:%02d".format(minutes, seconds)
}

private fun pctVideo(value: Float): String = "${(value * 100).roundToInt()}%"
