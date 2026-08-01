package com.alastorkaneki.fileeditor.media

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Brightness
import androidx.media3.effect.Contrast
import androidx.media3.effect.HslAdjustment
import androidx.media3.effect.Presentation
import androidx.media3.effect.RgbAdjustment
import androidx.media3.effect.RgbFilter
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

data class VideoExportSettings(
    val startMs: Long = 0L,
    val endMs: Long = Long.MAX_VALUE,
    val mute: Boolean = false,
    val rotationDegrees: Float = 0f,
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false,
    val outputHeight: Int = 0,
    val frameRate: Int = 0,
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val saturation: Float = 0f,
    val hue: Float = 0f,
    val lightness: Float = 0f,
    val redScale: Float = 1f,
    val greenScale: Float = 1f,
    val blueScale: Float = 1f,
    val grayscale: Boolean = false,
    val invert: Boolean = false,
)

data class AudioExportSettings(
    val startMs: Long = 0L,
    val endMs: Long = Long.MAX_VALUE,
    val speed: Float = 1f,
    val pitch: Float = 1f,
    val sampleRateHz: Int = 0,
)

@OptIn(UnstableApi::class)
class MediaExportEngine(private val context: Context) {
    suspend fun exportVideo(
        input: Uri,
        settings: VideoExportSettings,
        displayName: String,
    ): Uri {
        val output = File.createTempFile("file_editor_video_", ".mp4", context.cacheDir)
        try {
            val mediaItem = clippedMediaItem(input, settings.startMs, settings.endMs)
            val videoEffects = buildVideoEffects(settings)
            val edited = EditedMediaItem.Builder(mediaItem)
                .setRemoveAudio(settings.mute)
                .setEffects(Effects(emptyList(), videoEffects))
                .apply {
                    if (settings.frameRate > 0) setFrameRate(settings.frameRate)
                }
                .build()

            runTransformer(
                editedMediaItem = edited,
                output = output,
                configure = {
                    setVideoMimeType(MimeTypes.VIDEO_H264)
                    if (!settings.mute) setAudioMimeType(MimeTypes.AUDIO_AAC)
                },
            )
            return withContext(Dispatchers.IO) {
                saveExport(
                    source = output,
                    displayName = ensureExtension(displayName, "mp4"),
                    mimeType = "video/mp4",
                    collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                    } else {
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    },
                    relativePath = "${Environment.DIRECTORY_MOVIES}/FileEditor",
                )
            }
        } finally {
            output.delete()
        }
    }

    suspend fun exportAudio(
        input: Uri,
        settings: AudioExportSettings,
        displayName: String,
    ): Uri {
        val output = File.createTempFile("file_editor_audio_", ".m4a", context.cacheDir)
        try {
            runTransformer(
                editedMediaItem = buildAudioEditedMediaItem(input, settings),
                output = output,
                configure = { setAudioMimeType(MimeTypes.AUDIO_AAC) },
            )
            return withContext(Dispatchers.IO) {
                saveExport(
                    source = output,
                    displayName = ensureExtension(displayName, "m4a"),
                    mimeType = "audio/mp4",
                    collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                    } else {
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                    },
                    relativePath = "${Environment.DIRECTORY_MUSIC}/FileEditor",
                )
            }
        } finally {
            output.delete()
        }
    }

    suspend fun renderAudioPreview(
        input: Uri,
        settings: AudioExportSettings,
        maxSourceDurationMs: Long = 15_000L,
    ): File {
        val startMs = settings.startMs.coerceAtLeast(0L)
        val selectedEndMs = if (settings.endMs == Long.MAX_VALUE) {
            startMs + maxSourceDurationMs
        } else {
            settings.endMs
        }
        val previewEndMs = minOf(
            selectedEndMs,
            startMs + maxSourceDurationMs.coerceAtLeast(1_000L),
        ).coerceAtLeast(startMs + 1L)

        val output = File.createTempFile("file_editor_audio_preview_", ".m4a", context.cacheDir)
        return try {
            runTransformer(
                editedMediaItem = buildAudioEditedMediaItem(
                    input,
                    settings.copy(startMs = startMs, endMs = previewEndMs),
                ),
                output = output,
                configure = { setAudioMimeType(MimeTypes.AUDIO_AAC) },
            )
            output
        } catch (error: Throwable) {
            output.delete()
            throw error
        }
    }

    private fun buildAudioEditedMediaItem(
        input: Uri,
        settings: AudioExportSettings,
    ): EditedMediaItem {
        val sonic = SonicAudioProcessor().apply {
            setSpeed(settings.speed.coerceIn(0.25f, 4f))
            setPitch(settings.pitch.coerceIn(0.25f, 4f))
            if (settings.sampleRateHz > 0) setOutputSampleRateHz(settings.sampleRateHz)
        }
        return EditedMediaItem.Builder(
            clippedMediaItem(input, settings.startMs, settings.endMs),
        )
            .setRemoveVideo(true)
            .setEffects(Effects(listOf<AudioProcessor>(sonic), emptyList()))
            .build()
    }

    private fun clippedMediaItem(uri: Uri, startMs: Long, endMs: Long): MediaItem {
        val clipping = MediaItem.ClippingConfiguration.Builder()
            .setStartPositionMs(startMs.coerceAtLeast(0L))
            .apply {
                if (endMs != Long.MAX_VALUE) {
                    setEndPositionMs(endMs.coerceAtLeast(startMs + 1L))
                }
            }
            .build()
        return MediaItem.Builder()
            .setUri(uri)
            .setClippingConfiguration(clipping)
            .build()
    }

    private fun buildVideoEffects(settings: VideoExportSettings): List<Effect> = buildList {
        if (
            settings.rotationDegrees != 0f ||
            settings.flipHorizontal ||
            settings.flipVertical
        ) {
            add(
                ScaleAndRotateTransformation.Builder()
                    .setRotationDegrees(settings.rotationDegrees)
                    .setScale(
                        if (settings.flipHorizontal) -1f else 1f,
                        if (settings.flipVertical) -1f else 1f,
                    )
                    .build(),
            )
        }
        if (settings.outputHeight > 0) add(Presentation.createForHeight(settings.outputHeight))
        if (settings.brightness != 0f) add(Brightness(settings.brightness.coerceIn(-1f, 1f)))
        if (settings.contrast != 0f) add(Contrast(settings.contrast.coerceIn(-1f, 1f)))
        if (settings.saturation != 0f || settings.hue != 0f || settings.lightness != 0f) {
            add(
                HslAdjustment.Builder()
                    .adjustSaturation(settings.saturation.coerceIn(-100f, 100f))
                    .adjustHue(settings.hue)
                    .adjustLightness(settings.lightness.coerceIn(-100f, 100f))
                    .build(),
            )
        }
        if (
            settings.redScale != 1f ||
            settings.greenScale != 1f ||
            settings.blueScale != 1f
        ) {
            add(
                RgbAdjustment.Builder()
                    .setRedScale(settings.redScale.coerceAtLeast(0f))
                    .setGreenScale(settings.greenScale.coerceAtLeast(0f))
                    .setBlueScale(settings.blueScale.coerceAtLeast(0f))
                    .build(),
            )
        }
        if (settings.grayscale) add(RgbFilter.createGrayscaleFilter())
        if (settings.invert) add(RgbFilter.createInvertedFilter())
    }

    private suspend fun runTransformer(
        editedMediaItem: EditedMediaItem,
        output: File,
        configure: Transformer.Builder.() -> Unit,
    ) = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine<Unit> { continuation ->
            var transformer: Transformer? = null
            val listener = object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    if (continuation.isActive) continuation.resume(Unit)
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException,
                ) {
                    if (continuation.isActive) continuation.resumeWithException(exportException)
                }
            }
            transformer = Transformer.Builder(context)
                .apply(configure)
                .addListener(listener)
                .build()
            continuation.invokeOnCancellation { transformer?.cancel() }
            transformer.start(editedMediaItem, output.absolutePath)
        }
    }

    private fun saveExport(
        source: File,
        displayName: String,
        mimeType: String,
        collection: Uri,
        relativePath: String,
    ): Uri {
        val resolver = context.contentResolver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = resolver.insert(collection, values) ?: error("Unable to create output file")
            try {
                resolver.openOutputStream(uri, "w")?.use { output ->
                    source.inputStream().use { input -> input.copyTo(output) }
                } ?: error("Unable to write output file")
                resolver.update(
                    uri,
                    ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                    null,
                    null,
                )
                return uri
            } catch (error: Throwable) {
                resolver.delete(uri, null, null)
                throw error
            }
        }

        @Suppress("DEPRECATION")
        val root = when {
            mimeType.startsWith("video/") -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
            else -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
        }
        val directory = File(root, "FileEditor").apply { mkdirs() }
        val file = File(directory, displayName)
        source.inputStream().use { input -> FileOutputStream(file).use { input.copyTo(it) } }
        return Uri.fromFile(file)
    }

    private fun ensureExtension(name: String, extension: String): String {
        val safe = name.trim().ifBlank { "FileEditor-${System.currentTimeMillis()}" }
        return if (safe.endsWith(".$extension", ignoreCase = true)) safe else "$safe.$extension"
    }
}
