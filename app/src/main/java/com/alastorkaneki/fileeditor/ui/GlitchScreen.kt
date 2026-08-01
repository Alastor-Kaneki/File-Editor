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
import androidx.compose.material.icons.filled.Casino
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
import com.alastorkaneki.fileeditor.media.GlitchEngine
import com.alastorkaneki.fileeditor.media.GlitchSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import kotlin.random.Random

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
    var savePng by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) sourceUri = uri
    }

    LaunchedEffect(sourceUri) {
        val uri = sourceUri ?: return@LaunchedEffect
        loading = true
        message = null
        runCatching { withContext(Dispatchers.IO) { repository.loadBitmap(uri, 1400) } }
            .onSuccess {
                source?.takeIf { old -> old !== it }?.recycle()
                source = it
            }
            .onFailure { message = "Unable to open image: ${it.message ?: it::class.java.simpleName}" }
        loading = false
    }

    LaunchedEffect(source, settings) {
        val bitmap = source ?: return@LaunchedEffect
        rendering = true
        delay(60)
        runCatching { withContext(Dispatchers.Default) { GlitchEngine.render(bitmap, settings) } }
            .onSuccess {
                preview?.takeIf { old -> old !== it }?.recycle()
                preview = it
            }
            .onFailure { message = "Preview failed: ${it.message ?: it::class.java.simpleName}" }
        rendering = false
    }

    fun save() {
        val bitmap = preview ?: return
        if (saving) return
        scope.launch {
            saving = true
            message = null
            val extension = if (savePng) "png" else "jpg"
            val format = if (savePng) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
            val name = "Glitch-${System.currentTimeMillis()}.$extension"
            runCatching {
                withContext(Dispatchers.IO) {
                    repository.saveBitmapToGallery(bitmap, name, format, if (savePng) 100 else 94)
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
                            "Realtime preview",
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
                        .height(420.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        loading -> CircularProgressIndicator()
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
                Button(
                    onClick = { picker.launch("image/*") },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (source == null) "Choose image" else "Choose another image")
                }
            }

            item {
                SettingSlider(
                    label = "Intensity",
                    valueLabel = "${(settings.intensity * 100).roundToInt()}%",
                    value = settings.intensity,
                    range = 0f..1f,
                ) { settings = settings.copy(intensity = it) }
            }
            item {
                SettingSlider(
                    label = "RGB channel split",
                    valueLabel = "${settings.rgbShift}px",
                    value = settings.rgbShift.toFloat(),
                    range = 0f..48f,
                ) { settings = settings.copy(rgbShift = it.roundToInt()) }
            }
            item {
                SettingSlider(
                    label = "Horizontal slices",
                    valueLabel = settings.sliceCount.toString(),
                    value = settings.sliceCount.toFloat(),
                    range = 0f..60f,
                ) { settings = settings.copy(sliceCount = it.roundToInt()) }
            }
            item {
                SettingSlider(
                    label = "Digital noise",
                    valueLabel = "${(settings.noise * 100).roundToInt()}%",
                    value = settings.noise,
                    range = 0f..0.45f,
                ) { settings = settings.copy(noise = it) }
            }
            item {
                SettingSlider(
                    label = "Scanlines",
                    valueLabel = "${(settings.scanlines * 100).roundToInt()}%",
                    value = settings.scanlines,
                    range = 0f..1f,
                ) { settings = settings.copy(scanlines = it) }
            }

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

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    FilledTonalButton(
                        onClick = { savePng = true },
                        modifier = Modifier.weight(1f),
                    ) { Text(if (savePng) "✓ PNG" else "PNG") }
                    FilledTonalButton(
                        onClick = { savePng = false },
                        modifier = Modifier.weight(1f),
                    ) { Text(if (!savePng) "✓ JPEG" else "JPEG") }
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
                    onClick = ::save,
                    enabled = preview != null && !saving,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (saving) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Save, contentDescription = null)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text("Save glitched image")
                }
            }
        }
    }
}

@Composable
private fun SettingSlider(
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
