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
import androidx.compose.material.icons.filled.Casino
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
import com.alastorkaneki.fileeditor.media.GlitchEngine
import com.alastorkaneki.fileeditor.media.GlitchSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import kotlin.random.Random

private enum class GlitchOutputFormat(val label: String) {
    PNG("PNG"),
    JPEG("JPEG"),
    WEBP("WebP"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlitchScreen(
    initialUri: Uri?,
    onExit: () -> Unit,
) {
    val context = LocalContext.current
    val repository = remember { VisualMediaRepository(context) }
    val scope = rememberCoroutineScope()
    var sourceUri by remember(initialUri) { mutableStateOf(initialUri) }
    var source by remember { mutableStateOf<Bitmap?>(null) }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var settings by remember { mutableStateOf(GlitchSettings()) }
    var loading by remember { mutableStateOf(false) }
    var rendering by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var showOriginal by remember { mutableStateOf(false) }
    var outputFormat by remember { mutableStateOf(GlitchOutputFormat.PNG) }
    var quality by remember { mutableStateOf(94f) }
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
        runCatching {
            withContext(Dispatchers.IO) {
                RobustMediaLoader.loadBitmap(context, uri, 2800)
            }
        }.onSuccess { bitmap ->
            source?.takeIf { it !== bitmap }?.recycle()
            source = bitmap
        }.onFailure {
            message = it.message ?: "Unable to open image"
        }
        loading = false
    }

    LaunchedEffect(source, settings) {
        val bitmap = source ?: return@LaunchedEffect
        rendering = true
        delay(65)
        runCatching {
            withContext(Dispatchers.Default) {
                val largest = maxOf(bitmap.width, bitmap.height)
                val previewSource = if (largest > 1200) {
                    val scale = 1200f / largest
                    Bitmap.createScaledBitmap(
                        bitmap,
                        (bitmap.width * scale).roundToInt().coerceAtLeast(1),
                        (bitmap.height * scale).roundToInt().coerceAtLeast(1),
                        true,
                    )
                } else {
                    bitmap
                }
                try {
                    GlitchEngine.render(previewSource, settings)
                } finally {
                    if (previewSource !== bitmap) previewSource.recycle()
                }
            }
        }.onSuccess { result ->
            preview?.takeIf { it !== result }?.recycle()
            preview = result
        }.onFailure { message = "Preview failed: ${it.message ?: it::class.java.simpleName}" }
        rendering = false
    }

    fun applyPreset(name: String) {
        settings = when (name) {
            "VHS" -> GlitchSettings(
                intensity = 0.58f,
                rgbShift = 14,
                verticalRgbShift = 2,
                sliceCount = 10,
                blockAmount = 0.12f,
                noise = 0.11f,
                scanlines = 0.48f,
                scanlineSpacing = 3,
                ghosting = 0.35f,
                posterize = 48,
                seed = settings.seed,
            )
            "Datamosh" -> GlitchSettings(
                intensity = 0.78f,
                rgbShift = 8,
                sliceCount = 30,
                sliceHeight = 0.17f,
                blockAmount = 0.75f,
                noise = 0.03f,
                scanlines = 0.08f,
                pixelSort = 0.25f,
                smear = 0.78f,
                ghosting = 0.22f,
                posterize = 22,
                seed = settings.seed,
            )
            "Cyberpunk" -> GlitchSettings(
                intensity = 0.66f,
                rgbShift = 26,
                verticalRgbShift = 8,
                sliceCount = 18,
                blockAmount = 0.42f,
                noise = 0.09f,
                scanlines = 0.3f,
                pixelSort = 0.38f,
                posterize = 18,
                invertBlocks = 0.34f,
                seed = settings.seed,
            )
            "Broken LCD" -> GlitchSettings(
                intensity = 0.72f,
                rgbShift = 5,
                verticalRgbShift = 16,
                sliceCount = 40,
                sliceHeight = 0.04f,
                blockAmount = 0.55f,
                noise = 0.17f,
                scanlines = 0.62f,
                scanlineSpacing = 2,
                mosaic = 3,
                verticalTearing = true,
                seed = settings.seed,
            )
            "Pixel Melt" -> GlitchSettings(
                intensity = 0.84f,
                rgbShift = 12,
                sliceCount = 22,
                blockAmount = 0.25f,
                noise = 0.02f,
                scanlines = 0f,
                pixelSort = 0.85f,
                smear = 0.7f,
                posterize = 12,
                mosaic = 2,
                seed = settings.seed,
            )
            else -> GlitchSettings(seed = settings.seed)
        }
    }

    fun save() {
        val bitmap = source ?: return
        if (saving) return
        scope.launch {
            saving = true
            message = null
            val format = when (outputFormat) {
                GlitchOutputFormat.PNG -> Bitmap.CompressFormat.PNG
                GlitchOutputFormat.JPEG -> Bitmap.CompressFormat.JPEG
                GlitchOutputFormat.WEBP -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Bitmap.CompressFormat.WEBP_LOSSY
                } else {
                    @Suppress("DEPRECATION")
                    Bitmap.CompressFormat.WEBP
                }
            }
            val extension = when (outputFormat) {
                GlitchOutputFormat.PNG -> "png"
                GlitchOutputFormat.JPEG -> "jpg"
                GlitchOutputFormat.WEBP -> "webp"
            }
            val name = "Glitch-${System.currentTimeMillis()}.$extension"
            runCatching {
                val rendered = withContext(Dispatchers.Default) {
                    GlitchEngine.render(bitmap, settings)
                }
                try {
                    withContext(Dispatchers.IO) {
                        repository.saveBitmapToGallery(
                            rendered,
                            name,
                            format,
                            if (outputFormat == GlitchOutputFormat.PNG) 100 else quality.roundToInt(),
                        )
                    }
                } finally {
                    rendered.recycle()
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
                        Text("Glitch Lab")
                        Text(
                            "Live preview • full-resolution export",
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
                    IconButton(
                        onClick = { settings = GlitchSettings(seed = settings.seed) },
                        enabled = source != null,
                    ) {
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
                        .height(390.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        loading -> CircularProgressIndicator()
                        showOriginal && source != null -> Image(
                            bitmap = requireNotNull(source).asImageBitmap(),
                            contentDescription = "Original",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                        )
                        preview != null -> Image(
                            bitmap = requireNotNull(preview).asImageBitmap(),
                            contentDescription = "Glitch preview",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                        )
                        else -> Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(58.dp))
                            Text("Choose an image to start")
                        }
                    }
                    if (rendering && preview != null) {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.TopEnd).padding(12.dp).size(26.dp),
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
                    ) { Text(if (showOriginal) "Show glitch" else "Show original") }
                }
            }

            item { Text("Presets", style = MaterialTheme.typography.headlineSmall) }
            item { GlitchChoices(listOf("Reset", "VHS", "Datamosh"), ::applyPreset) }
            item { GlitchChoices(listOf("Cyberpunk", "Broken LCD", "Pixel Melt"), ::applyPreset) }

            item { Text("Core distortion", style = MaterialTheme.typography.headlineSmall) }
            item { GlitchSlider("Intensity", pct(settings.intensity), settings.intensity, 0f..1f) { settings = settings.copy(intensity = it) } }
            item { GlitchSlider("Horizontal RGB split", "${settings.rgbShift}px", settings.rgbShift.toFloat(), 0f..72f) { settings = settings.copy(rgbShift = it.roundToInt()) } }
            item { GlitchSlider("Vertical RGB split", "${settings.verticalRgbShift}px", settings.verticalRgbShift.toFloat(), -48f..48f) { settings = settings.copy(verticalRgbShift = it.roundToInt()) } }
            item { GlitchSlider("Tear count", settings.sliceCount.toString(), settings.sliceCount.toFloat(), 0f..100f) { settings = settings.copy(sliceCount = it.roundToInt()) } }
            item { GlitchSlider("Tear thickness", pct(settings.sliceHeight), settings.sliceHeight, 0.005f..0.35f) { settings = settings.copy(sliceHeight = it) } }
            item { GlitchToggle("Vertical tearing", settings.verticalTearing) { settings = settings.copy(verticalTearing = it) } }
            item { GlitchSlider("Corrupted blocks", pct(settings.blockAmount), settings.blockAmount, 0f..1f) { settings = settings.copy(blockAmount = it) } }
            item { GlitchSlider("Inverted blocks", pct(settings.invertBlocks), settings.invertBlocks, 0f..1f) { settings = settings.copy(invertBlocks = it) } }

            item { Text("Data damage", style = MaterialTheme.typography.headlineSmall) }
            item { GlitchSlider("Pixel sorting", pct(settings.pixelSort), settings.pixelSort, 0f..1f) { settings = settings.copy(pixelSort = it) } }
            item { GlitchSlider("Datamosh smear", pct(settings.smear), settings.smear, 0f..1f) { settings = settings.copy(smear = it) } }
            item { GlitchSlider("Ghost image", pct(settings.ghosting), settings.ghosting, 0f..1f) { settings = settings.copy(ghosting = it) } }
            item { GlitchSlider("Digital noise", pct(settings.noise), settings.noise, 0f..0.6f) { settings = settings.copy(noise = it) } }
            item { GlitchToggle("Monochrome static", settings.monochromeNoise) { settings = settings.copy(monochromeNoise = it) } }
            item { GlitchSlider("Posterize", "${settings.posterize} levels", settings.posterize.toFloat(), 2f..64f) { settings = settings.copy(posterize = it.roundToInt()) } }
            item { GlitchSlider("Mosaic pixels", "${settings.mosaic}x", settings.mosaic.toFloat(), 1f..32f) { settings = settings.copy(mosaic = it.roundToInt()) } }

            item { Text("Display damage", style = MaterialTheme.typography.headlineSmall) }
            item { GlitchSlider("Scanlines", pct(settings.scanlines), settings.scanlines, 0f..1f) { settings = settings.copy(scanlines = it) } }
            item { GlitchSlider("Scanline spacing", "${settings.scanlineSpacing}px", settings.scanlineSpacing.toFloat(), 1f..12f) { settings = settings.copy(scanlineSpacing = it.roundToInt()) } }
            item {
                FilledTonalButton(
                    onClick = { settings = settings.copy(seed = Random.nextInt()) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Casino, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Randomize pattern")
                }
            }

            item { Text("Export", style = MaterialTheme.typography.headlineSmall) }
            item {
                GlitchChoices(GlitchOutputFormat.entries.map { it.label }) { label ->
                    outputFormat = GlitchOutputFormat.entries.first { it.label == label }
                }
            }
            if (outputFormat != GlitchOutputFormat.PNG) {
                item { GlitchSlider("Quality", "${quality.roundToInt()}%", quality, 30f..100f) { quality = it } }
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
                    onClick = ::save,
                    enabled = source != null && preview != null && !saving,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (saving) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Default.Save, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (saving) "Rendering full image…" else "Save glitched image")
                }
            }
        }
    }
}

@Composable
private fun GlitchChoices(labels: List<String>, onClick: (String) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.forEach { label ->
            FilledTonalButton(onClick = { onClick(label) }, modifier = Modifier.weight(1f)) {
                Text(label)
            }
        }
    }
}

@Composable
private fun GlitchToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    FilledTonalButton(onClick = { onChange(!checked) }, modifier = Modifier.fillMaxWidth()) {
        Text(label)
        Spacer(Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun GlitchSlider(
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
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValueChange,
            valueRange = range,
        )
    }
}

private fun pct(value: Float): String = "${(value * 100).roundToInt()}%"
