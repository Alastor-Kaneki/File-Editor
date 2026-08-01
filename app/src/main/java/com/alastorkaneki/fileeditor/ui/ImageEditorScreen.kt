package com.alastorkaneki.fileeditor.ui

import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
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
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CropRotate
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.RestartAlt
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
import com.alastorkaneki.fileeditor.data.RobustMediaLoader
import com.alastorkaneki.fileeditor.data.VisualMediaRepository
import com.alastorkaneki.fileeditor.media.ImageEditEngine
import com.alastorkaneki.fileeditor.media.ImageEditSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import kotlin.random.Random

private enum class PhotoOutputFormat(val label: String) {
    PNG("PNG"),
    JPEG("JPEG"),
    WEBP("WebP"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageEditorScreen(
    initialUri: Uri?,
    onExit: () -> Unit,
) {
    val context = LocalContext.current
    val repository = remember { VisualMediaRepository(context) }
    val scope = rememberCoroutineScope()
    var sourceUri by remember(initialUri) { mutableStateOf(initialUri) }
    var source by remember { mutableStateOf<Bitmap?>(null) }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var settings by remember { mutableStateOf(ImageEditSettings()) }
    var loading by remember { mutableStateOf(false) }
    var rendering by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var showOriginal by remember { mutableStateOf(false) }
    var format by remember { mutableStateOf(PhotoOutputFormat.PNG) }
    var quality by remember { mutableStateOf(94f) }
    var exportDimension by remember { mutableStateOf(2400f) }
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
        message = null
        runCatching { withContext(Dispatchers.IO) { RobustMediaLoader.loadBitmap(context, uri, 2800) } }
            .onSuccess { bitmap ->
                source?.takeIf { it !== bitmap }?.recycle()
                source = bitmap
                settings = ImageEditSettings()
            }
            .onFailure { message = it.message ?: "Unable to open image" }
        loading = false
    }

    LaunchedEffect(source, settings) {
        val bitmap = source ?: return@LaunchedEffect
        rendering = true
        delay(70)
        runCatching {
            withContext(Dispatchers.Default) {
                ImageEditEngine.render(bitmap, settings, 1200)
            }
        }.onSuccess { bitmapPreview ->
            preview?.takeIf { it !== bitmapPreview }?.recycle()
            preview = bitmapPreview
        }.onFailure { message = "Preview failed: ${it.message ?: it::class.java.simpleName}" }
        rendering = false
    }

    fun applyPreset(name: String) {
        settings = when (name) {
            "Cinematic" -> settings.copy(
                contrast = 1.22f,
                saturation = 0.86f,
                temperature = -0.12f,
                highlights = -0.18f,
                shadows = 0.16f,
                vignette = 0.38f,
                fade = 0.06f,
            )
            "Neon" -> settings.copy(
                contrast = 1.38f,
                saturation = 1.7f,
                exposure = 0.15f,
                tint = 0.24f,
                sharpness = 0.35f,
            )
            "Vintage" -> settings.copy(
                contrast = 0.92f,
                saturation = 0.72f,
                temperature = 0.24f,
                fade = 0.22f,
                grain = 0.18f,
                vignette = 0.3f,
            )
            "Noir" -> settings.copy(
                grayscale = true,
                contrast = 1.45f,
                shadows = -0.12f,
                grain = 0.12f,
                vignette = 0.45f,
            )
            "Dream" -> settings.copy(
                exposure = 0.22f,
                contrast = 0.82f,
                saturation = 1.12f,
                blur = 0.08f,
                fade = 0.16f,
                tint = 0.12f,
            )
            else -> ImageEditSettings()
        }
    }

    fun save() {
        val bitmap = source ?: return
        if (saving) return
        scope.launch {
            saving = true
            message = null
            val outputFormat = when (format) {
                PhotoOutputFormat.PNG -> Bitmap.CompressFormat.PNG
                PhotoOutputFormat.JPEG -> Bitmap.CompressFormat.JPEG
                PhotoOutputFormat.WEBP -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Bitmap.CompressFormat.WEBP_LOSSY
                } else {
                    @Suppress("DEPRECATION")
                    Bitmap.CompressFormat.WEBP
                }
            }
            val extension = when (format) {
                PhotoOutputFormat.PNG -> "png"
                PhotoOutputFormat.JPEG -> "jpg"
                PhotoOutputFormat.WEBP -> "webp"
            }
            val name = "Edited-${System.currentTimeMillis()}.$extension"
            runCatching {
                withContext(Dispatchers.Default) {
                    ImageEditEngine.render(bitmap, settings, exportDimension.roundToInt())
                }.let { rendered ->
                    try {
                        withContext(Dispatchers.IO) {
                            repository.saveBitmapToGallery(
                                rendered,
                                name,
                                outputFormat,
                                quality.roundToInt().coerceIn(1, 100),
                            )
                        }
                    } finally {
                        rendered.recycle()
                    }
                }
            }.onSuccess { message = "Saved $name to Pictures/FileEditor" }
                .onFailure { message = "Save failed: ${it.message ?: it::class.java.simpleName}" }
            saving = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Photo Studio")
                        Text(
                            "Adjust • transform • filter • export",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onExit, enabled = !saving) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { settings = ImageEditSettings() }, enabled = source != null) {
                        Icon(Icons.Default.RestartAlt, contentDescription = "Reset edits")
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
                        .height(390.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        loading -> CircularProgressIndicator()
                        showOriginal && source != null -> Image(
                            bitmap = requireNotNull(source).asImageBitmap(),
                            contentDescription = "Original image",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                        )
                        preview != null -> Image(
                            bitmap = requireNotNull(preview).asImageBitmap(),
                            contentDescription = "Edited preview",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                        )
                        else -> Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(60.dp))
                            Text("Choose an image to edit")
                        }
                    }
                    if (rendering && preview != null) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(12.dp)
                                .size(26.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { picker.launch(arrayOf("image/*")) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (source == null) "Choose image" else "Replace")
                    }
                    FilledTonalButton(
                        onClick = { showOriginal = !showOriginal },
                        enabled = source != null,
                        modifier = Modifier.weight(1f),
                    ) { Text(if (showOriginal) "Show edited" else "Hold original") }
                }
            }

            item { SectionLabel("Presets") }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoiceButtons(listOf("Reset", "Cinematic", "Neon")) { applyPreset(it) }
                    ChoiceButtons(listOf("Vintage", "Noir", "Dream")) { applyPreset(it) }
                }
            }

            item { SectionLabel("Light and color") }
            item { PhotoSlider("Brightness", percent(settings.brightness), settings.brightness, -1f..1f) { settings = settings.copy(brightness = it) } }
            item { PhotoSlider("Contrast", "${(settings.contrast * 100).roundToInt()}%", settings.contrast, 0.2f..2.4f) { settings = settings.copy(contrast = it) } }
            item { PhotoSlider("Saturation", "${(settings.saturation * 100).roundToInt()}%", settings.saturation, 0f..2.5f) { settings = settings.copy(saturation = it) } }
            item { PhotoSlider("Exposure", "${"%.1f".format(settings.exposure)} EV", settings.exposure, -2f..2f) { settings = settings.copy(exposure = it) } }
            item { PhotoSlider("Temperature", percent(settings.temperature), settings.temperature, -1f..1f) { settings = settings.copy(temperature = it) } }
            item { PhotoSlider("Tint", percent(settings.tint), settings.tint, -1f..1f) { settings = settings.copy(tint = it) } }
            item { PhotoSlider("Hue", "${settings.hue.roundToInt()}°", settings.hue, -180f..180f) { settings = settings.copy(hue = it) } }
            item { PhotoSlider("Highlights", percent(settings.highlights), settings.highlights, -1f..1f) { settings = settings.copy(highlights = it) } }
            item { PhotoSlider("Shadows", percent(settings.shadows), settings.shadows, -1f..1f) { settings = settings.copy(shadows = it) } }

            item { SectionLabel("Texture and style") }
            item { PhotoSlider("Fade", percent(settings.fade), settings.fade, 0f..1f) { settings = settings.copy(fade = it) } }
            item { PhotoSlider("Vignette", percent(settings.vignette), settings.vignette, 0f..1f) { settings = settings.copy(vignette = it) } }
            item { PhotoSlider("Film grain", percent(settings.grain), settings.grain, 0f..0.6f) { settings = settings.copy(grain = it) } }
            item { PhotoSlider("Sharpen", percent(settings.sharpness), settings.sharpness, 0f..1f) { settings = settings.copy(sharpness = it) } }
            item { PhotoSlider("Soft blur", percent(settings.blur), settings.blur, 0f..1f) { settings = settings.copy(blur = it) } }
            item { PhotoSlider("Pixelate", "${settings.pixelate}px", settings.pixelate.toFloat(), 1f..40f) { settings = settings.copy(pixelate = it.roundToInt()) } }
            item { PhotoSlider("Posterize", "${settings.posterize} levels", settings.posterize.toFloat(), 2f..64f) { settings = settings.copy(posterize = it.roundToInt()) } }
            item { ToggleSetting("Black and white", settings.grayscale) { settings = settings.copy(grayscale = it) } }
            item { ToggleSetting("Sepia", settings.sepia) { settings = settings.copy(sepia = it) } }
            item { ToggleSetting("Invert colors", settings.invert) { settings = settings.copy(invert = it) } }

            item { SectionLabel("Transform and crop") }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = { settings = settings.copy(rotationDegrees = settings.rotationDegrees - 90) },
                        modifier = Modifier.weight(1f),
                    ) { Icon(Icons.Default.CropRotate, null); Spacer(Modifier.width(5.dp)); Text("Left") }
                    FilledTonalButton(
                        onClick = { settings = settings.copy(rotationDegrees = settings.rotationDegrees + 90) },
                        modifier = Modifier.weight(1f),
                    ) { Icon(Icons.Default.CropRotate, null); Spacer(Modifier.width(5.dp)); Text("Right") }
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
                Text("Crop aspect", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(6.dp))
                ChoiceButtons(listOf("Free", "1:1", "4:5")) {
                    settings = settings.copy(cropAspect = when (it) {
                        "1:1" -> 1f
                        "4:5" -> 4f / 5f
                        else -> 0f
                    })
                }
                Spacer(Modifier.height(8.dp))
                ChoiceButtons(listOf("16:9", "9:16", "3:2")) {
                    settings = settings.copy(cropAspect = when (it) {
                        "16:9" -> 16f / 9f
                        "9:16" -> 9f / 16f
                        else -> 3f / 2f
                    })
                }
            }
            item {
                FilledTonalButton(
                    onClick = { settings = settings.copy(seed = Random.nextInt()) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.AutoFixHigh, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Regenerate film grain")
                }
            }

            item { SectionLabel("Export") }
            item {
                ChoiceButtons(PhotoOutputFormat.entries.map { it.label }) { label ->
                    format = PhotoOutputFormat.entries.first { it.label == label }
                }
            }
            if (format != PhotoOutputFormat.PNG) {
                item { PhotoSlider("Quality", "${quality.roundToInt()}%", quality, 30f..100f) { quality = it } }
            }
            item { PhotoSlider("Maximum output size", "${exportDimension.roundToInt()}px", exportDimension, 720f..4096f) { exportDimension = it } }

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
                    onClick = ::save,
                    enabled = source != null && !saving,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (saving) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Default.Save, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (saving) "Rendering full image…" else "Save edited image")
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.headlineSmall)
}

@Composable
private fun ChoiceButtons(labels: List<String>, onClick: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        labels.forEach { label ->
            FilledTonalButton(onClick = { onClick(label) }, modifier = Modifier.weight(1f)) {
                Text(label)
            }
        }
    }
}

@Composable
private fun ToggleSetting(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    FilledTonalButton(
        onClick = { onChange(!checked) },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(label)
        Spacer(Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun PhotoSlider(
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
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange,
            valueRange = range,
        )
    }
}

private fun percent(value: Float): String = "${(value * 100).roundToInt()}%"
