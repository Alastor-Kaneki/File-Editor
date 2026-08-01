package com.alastorkaneki.fileeditor.ui

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
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
import com.alastorkaneki.fileeditor.data.VisualMediaRepository
import com.alastorkaneki.fileeditor.media.GifEngine
import com.alastorkaneki.fileeditor.media.GifOptions
import com.alastorkaneki.fileeditor.media.GlitchEngine
import com.alastorkaneki.fileeditor.media.GlitchSettings
import kotlinx.coroutines.Dispatchers
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
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var loadingPreview by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    var delayMs by remember { mutableStateOf(180f) }
    var maxFrames by remember { mutableStateOf(24f) }
    var maxDimension by remember { mutableStateOf(720f) }
    var glitchEnabled by remember { mutableStateOf(false) }
    var glitchIntensity by remember { mutableStateOf(0.48f) }
    var glitchShift by remember { mutableStateOf(12f) }
    var glitchSlices by remember { mutableStateOf(16f) }
    var message by remember { mutableStateOf<String?>(null) }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isNotEmpty()) {
            imageUris = uris
            videoUri = null
            message = null
        }
    }

    val videoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            videoUri = uri
            imageUris = emptyList()
            message = null
        }
    }

    val previewKey = videoUri?.toString() ?: imageUris.firstOrNull()?.toString()
    LaunchedEffect(previewKey, glitchEnabled, glitchIntensity, glitchShift, glitchSlices) {
        val uri = videoUri ?: imageUris.firstOrNull() ?: run {
            preview?.recycle()
            preview = null
            return@LaunchedEffect
        }
        loadingPreview = true
        runCatching {
            withContext(Dispatchers.IO) {
                val source = if (videoUri != null) {
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(context, uri)
                        retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                            ?: error("Unable to decode video preview")
                    } finally {
                        retriever.release()
                    }
                } else {
                    repository.loadBitmap(uri, 900)
                }

                if (glitchEnabled) {
                    try {
                        GlitchEngine.render(
                            source,
                            GlitchSettings(
                                intensity = glitchIntensity,
                                rgbShift = glitchShift.roundToInt(),
                                sliceCount = glitchSlices.roundToInt(),
                                noise = 0.09f,
                                scanlines = 0.3f,
                            ),
                        )
                    } finally {
                        source.recycle()
                    }
                } else {
                    source
                }
            }
        }.onSuccess {
            preview?.takeIf { old -> old !== it }?.recycle()
            preview = it
        }.onFailure { message = "Preview failed: ${it.message ?: it::class.java.simpleName}" }
        loadingPreview = false
    }

    fun createGif() {
        if (creating || (videoUri == null && imageUris.isEmpty())) return
        scope.launch {
            creating = true
            message = null
            val glitch = if (glitchEnabled) {
                GlitchSettings(
                    intensity = glitchIntensity,
                    rgbShift = glitchShift.roundToInt(),
                    sliceCount = glitchSlices.roundToInt(),
                    noise = 0.09f,
                    scanlines = 0.3f,
                )
            } else {
                null
            }
            val options = GifOptions(
                delayMs = delayMs.roundToInt(),
                maxFrames = maxFrames.roundToInt(),
                maxDimension = maxDimension.roundToInt(),
                glitch = glitch,
            )
            val name = if (glitchEnabled) {
                "Glitch-GIF-${System.currentTimeMillis()}.gif"
            } else {
                "GIF-${System.currentTimeMillis()}.gif"
            }

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
                        Text(
                            sourceDescription,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onExit, enabled = !creating) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
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
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(360.dp)
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
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

            item {
                GifSlider(
                    label = "Frame delay",
                    valueLabel = "${delayMs.roundToInt()} ms",
                    value = delayMs,
                    range = 40f..1000f,
                ) { delayMs = it }
            }
            item {
                GifSlider(
                    label = "Maximum frames",
                    valueLabel = maxFrames.roundToInt().toString(),
                    value = maxFrames,
                    range = 2f..60f,
                ) { maxFrames = it }
            }
            item {
                GifSlider(
                    label = "Maximum dimension",
                    valueLabel = "${maxDimension.roundToInt()} px",
                    value = maxDimension,
                    range = 240f..1080f,
                ) { maxDimension = it }
            }

            item {
                FilledTonalButton(
                    onClick = { glitchEnabled = !glitchEnabled },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.AutoFixHigh, contentDescription = null)
                    Spacer(Modifier.width(10.dp))
                    Text("Animated glitch")
                    Spacer(Modifier.weight(1f))
                    Switch(checked = glitchEnabled, onCheckedChange = null)
                }
            }

            if (glitchEnabled) {
                item {
                    GifSlider(
                        label = "Glitch intensity",
                        valueLabel = "${(glitchIntensity * 100).roundToInt()}%",
                        value = glitchIntensity,
                        range = 0f..1f,
                    ) { glitchIntensity = it }
                }
                item {
                    GifSlider(
                        label = "RGB shift",
                        valueLabel = "${glitchShift.roundToInt()} px",
                        value = glitchShift,
                        range = 0f..48f,
                    ) { glitchShift = it }
                }
                item {
                    GifSlider(
                        label = "Slices per frame",
                        valueLabel = glitchSlices.roundToInt().toString(),
                        value = glitchSlices,
                        range = 0f..60f,
                    ) { glitchSlices = it }
                }
            }

            message?.let { text ->
                item {
                    Text(
                        text,
                        color = if (text.startsWith("Saved")) {
                            MaterialTheme.colorScheme.tertiary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )
                }
            }

            item {
                Button(
                    onClick = ::createGif,
                    enabled = !creating && (videoUri != null || imageUris.isNotEmpty()),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (creating) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Save, contentDescription = null)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(if (creating) "Encoding GIF…" else "Create and save GIF")
                }
            }

            item {
                Text(
                    "GIF encoding uses a pure Java GIF89a engine suitable for Android. Video frames are extracted locally; no uploads or cloud processing are used.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            Text(valueLabel, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValueChange,
            valueRange = range,
        )
    }
}
