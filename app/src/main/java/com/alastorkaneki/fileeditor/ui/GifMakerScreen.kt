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
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.alastorkaneki.fileeditor.media.GifEngine
import com.alastorkaneki.fileeditor.media.GifOptions
import com.alastorkaneki.fileeditor.media.GlitchEngine
import com.alastorkaneki.fileeditor.media.GlitchSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GifMakerScreen(
    initialVideoUri: Uri?,
    onExit: () -> Unit,
) {
    val context = LocalContext.current
    val repository = remember { VisualMediaRepository(context) }
    val engine = remember { GifEngine(context) }
    val scope = rememberCoroutineScope()
    var imageUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var videoUri by remember(initialVideoUri) { mutableStateOf(initialVideoUri) }
    var videoDurationMs by remember { mutableStateOf(0L) }
    var trimStartMs by remember { mutableStateOf(0L) }
    var trimEndMs by remember { mutableStateOf(Long.MAX_VALUE) }
    var previewPosition by remember { mutableStateOf(0f) }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var loadingPreview by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    var delayMs by remember { mutableStateOf(120f) }
    var maxFrames by remember { mutableStateOf(36f) }
    var maxDimension by remember { mutableStateOf(720f) }
    var loopCount by remember { mutableStateOf(0f) }
    var reverse by remember { mutableStateOf(false) }
    var pingPong by remember { mutableStateOf(false) }
    var fitInside by remember { mutableStateOf(false) }
    var glitchEnabled by remember { mutableStateOf(false) }
    var glitchIntensity by remember { mutableStateOf(0.48f) }
    var glitchShift by remember { mutableStateOf(12f) }
    var glitchSlices by remember { mutableStateOf(16f) }
    var glitchNoise by remember { mutableStateOf(0.09f) }
    var glitchPixelSort by remember { mutableStateOf(0f) }
    var glitchSmear by remember { mutableStateOf(0f) }
    var message by remember { mutableStateOf<String?>(null) }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            uris.forEach { RobustMediaLoader.persistReadPermission(context, it) }
            imageUris = uris
            videoUri = null
            videoDurationMs = 0L
            message = null
        }
    }

    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            RobustMediaLoader.persistReadPermission(context, uri)
            videoUri = uri
            imageUris = emptyList()
            message = null
        }
    }

    LaunchedEffect(videoUri) {
        val uri = videoUri ?: run {
            videoDurationMs = 0L
            return@LaunchedEffect
        }
        runCatching { withContext(Dispatchers.IO) { RobustMediaLoader.readDurationMs(context, uri) } }
            .onSuccess {
                videoDurationMs = it
                trimStartMs = 0L
                trimEndMs = it
                previewPosition = 0f
            }
            .onFailure { message = "Unable to read video: ${it.message ?: it::class.java.simpleName}" }
    }

    val previewKey = videoUri?.toString() ?: imageUris.firstOrNull()?.toString()
    LaunchedEffect(
        previewKey,
        previewPosition,
        glitchEnabled,
        glitchIntensity,
        glitchShift,
        glitchSlices,
        glitchNoise,
        glitchPixelSort,
        glitchSmear,
    ) {
        val uri = videoUri ?: imageUris.firstOrNull() ?: run {
            preview?.recycle()
            preview = null
            return@LaunchedEffect
        }
        loadingPreview = true
        delay(60)
        runCatching {
            withContext(Dispatchers.IO) {
                val source = if (videoUri != null) {
                    RobustMediaLoader.loadVideoFrame(
                        context,
                        uri,
                        timeUs = (previewPosition * videoDurationMs * 1000L).toLong(),
                        maxDimension = 900,
                    )
                } else {
                    RobustMediaLoader.loadBitmap(context, uri, 900)
                }

                if (glitchEnabled) {
                    try {
                        GlitchEngine.render(source, currentGifGlitch(
                            glitchIntensity,
                            glitchShift,
                            glitchSlices,
                            glitchNoise,
                            glitchPixelSort,
                            glitchSmear,
                        ))
                    } finally {
                        source.recycle()
                    }
                } else {
                    source
                }
            }
        }.onSuccess { result ->
            preview?.takeIf { it !== result }?.recycle()
            preview = result
        }.onFailure { message = "Preview failed: ${it.message ?: it::class.java.simpleName}" }
        loadingPreview = false
    }

    fun createGif() {
        if (creating || (videoUri == null && imageUris.isEmpty())) return
        scope.launch {
            creating = true
            message = null
            val options = GifOptions(
                delayMs = delayMs.roundToInt(),
                maxFrames = maxFrames.roundToInt(),
                maxDimension = maxDimension.roundToInt(),
                loopCount = loopCount.roundToInt(),
                reverse = reverse,
                pingPong = pingPong,
                fitInside = fitInside,
                startMs = trimStartMs,
                endMs = trimEndMs,
                glitch = if (glitchEnabled) currentGifGlitch(
                    glitchIntensity,
                    glitchShift,
                    glitchSlices,
                    glitchNoise,
                    glitchPixelSort,
                    glitchSmear,
                ) else null,
            )
            val name = "${if (glitchEnabled) "Glitch-" else ""}GIF-${System.currentTimeMillis()}.gif"

            runCatching {
                withContext(Dispatchers.IO) {
                    val temp = videoUri?.let { engine.createFromVideo(it, options) }
                        ?: engine.createFromImages(imageUris, options)
                    try {
                        repository.saveGifToGallery(temp, name)
                    } finally {
                        temp.delete()
                    }
                }
            }.onSuccess { message = "Saved $name to Pictures/FileEditor" }
                .onFailure { message = "GIF failed: ${it.message ?: it::class.java.simpleName}" }
            creating = false
        }
    }

    val sourceDescription = when {
        videoUri != null -> "Video selected"
        imageUris.isNotEmpty() -> "${imageUris.size} image${if (imageUris.size == 1) "" else "s"} selected"
        else -> "No source selected"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("GIF Maker")
                        Text(sourceDescription, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onExit, enabled = !creating) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
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
                        .height(340.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        loadingPreview -> CircularProgressIndicator()
                        preview != null -> Image(
                            bitmap = requireNotNull(preview).asImageBitmap(),
                            contentDescription = "GIF frame preview",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                        )
                        else -> Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(Icons.Default.Animation, contentDescription = null, modifier = Modifier.size(58.dp))
                            Text("Choose images or a video")
                        }
                    }
                }
            }

            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { imagePicker.launch(arrayOf("image/*")) },
                        modifier = Modifier.weight(1f),
                        enabled = !creating,
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Images")
                    }
                    Button(
                        onClick = { videoPicker.launch(arrayOf("video/*")) },
                        modifier = Modifier.weight(1f),
                        enabled = !creating,
                    ) {
                        Icon(Icons.Default.Movie, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Video")
                    }
                }
            }

            if (videoUri != null && videoDurationMs > 0L) {
                item {
                    GifSlider("Preview position", formatGifTime((previewPosition * videoDurationMs).toLong()), previewPosition, 0f..1f) {
                        previewPosition = it
                    }
                }
                item {
                    Text("Video trim", style = MaterialTheme.typography.headlineSmall)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(formatGifTime(trimStartMs))
                        Text(formatGifTime(trimEndMs), color = MaterialTheme.colorScheme.primary)
                    }
                    RangeSlider(
                        value = trimStartMs.toFloat()..trimEndMs.toFloat(),
                        onValueChange = {
                            trimStartMs = it.start.roundToInt().toLong()
                            trimEndMs = it.endInclusive.roundToInt().toLong()
                        },
                        valueRange = 0f..videoDurationMs.toFloat().coerceAtLeast(1f),
                    )
                }
            }

            item { Text("Animation", style = MaterialTheme.typography.headlineSmall) }
            item { GifSlider("Frame delay", "${delayMs.roundToInt()} ms", delayMs, 20f..1500f) { delayMs = it } }
            item { GifSlider("Maximum frames", maxFrames.roundToInt().toString(), maxFrames, 2f..120f) { maxFrames = it } }
            item { GifSlider("Maximum dimension", "${maxDimension.roundToInt()} px", maxDimension, 160f..2160f) { maxDimension = it } }
            item { GifSlider("Loop count", if (loopCount < 0.5f) "Infinite" else loopCount.roundToInt().toString(), loopCount, 0f..20f) { loopCount = it } }
            item { GifToggle("Reverse frame order", reverse) { reverse = it } }
            item { GifToggle("Ping-pong loop", pingPong) { pingPong = it } }
            item { GifToggle("Fit entire image (letterbox)", fitInside) { fitInside = it } }

            item { Text("Animated glitch", style = MaterialTheme.typography.headlineSmall) }
            item {
                FilledTonalButton(onClick = { glitchEnabled = !glitchEnabled }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.AutoFixHigh, contentDescription = null)
                    Spacer(Modifier.width(10.dp))
                    Text("Glitch every frame")
                    Spacer(Modifier.weight(1f))
                    Switch(checked = glitchEnabled, onCheckedChange = null)
                }
            }
            if (glitchEnabled) {
                item { GifSlider("Glitch intensity", "${(glitchIntensity * 100).roundToInt()}%", glitchIntensity, 0f..1f) { glitchIntensity = it } }
                item { GifSlider("RGB shift", "${glitchShift.roundToInt()} px", glitchShift, 0f..72f) { glitchShift = it } }
                item { GifSlider("Slices per frame", glitchSlices.roundToInt().toString(), glitchSlices, 0f..100f) { glitchSlices = it } }
                item { GifSlider("Static noise", "${(glitchNoise * 100).roundToInt()}%", glitchNoise, 0f..0.6f) { glitchNoise = it } }
                item { GifSlider("Pixel sorting", "${(glitchPixelSort * 100).roundToInt()}%", glitchPixelSort, 0f..1f) { glitchPixelSort = it } }
                item { GifSlider("Datamosh smear", "${(glitchSmear * 100).roundToInt()}%", glitchSmear, 0f..1f) { glitchSmear = it } }
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
                    onClick = ::createGif,
                    enabled = !creating && (videoUri != null || imageUris.isNotEmpty()),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (creating) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Default.Save, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (creating) "Encoding GIF…" else "Create and save GIF")
                }
            }
            item {
                Text(
                    "All decoding, frame extraction, glitching and GIF89a encoding happen locally on your device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun currentGifGlitch(
    intensity: Float,
    shift: Float,
    slices: Float,
    noise: Float,
    pixelSort: Float,
    smear: Float,
): GlitchSettings = GlitchSettings(
    intensity = intensity,
    rgbShift = shift.roundToInt(),
    sliceCount = slices.roundToInt(),
    noise = noise,
    scanlines = 0.25f,
    pixelSort = pixelSort,
    smear = smear,
)

@Composable
private fun GifToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    FilledTonalButton(onClick = { onChange(!checked) }, modifier = Modifier.fillMaxWidth()) {
        Text(label)
        Spacer(Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun GifSlider(
    label: String,
    valueLabel: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            Text(valueLabel, color = MaterialTheme.colorScheme.primary)
        }
        Slider(value = value.coerceIn(range.start, range.endInclusive), onValueChange = onValueChange, valueRange = range)
    }
}

private fun formatGifTime(milliseconds: Long): String {
    val totalSeconds = milliseconds.coerceAtLeast(0L) / 1000L
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
