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
import com.alastorkaneki.fileeditor.data.RobustMediaLoader
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs
import kotlin.math.roundToInt
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
        target: AudioExportTarget,
    ): Uri {
        val output = File.createTempFile(
            "file_editor_audio_",
            ".${target.extension}",
            context.cacheDir,
        )
        return try {
            renderAudioWithFfmpeg(
                input = input,
                settings = settings,
                target = target,
                output = output,
            )
            withContext(Dispatchers.IO) {
                saveExport(
                    source = output,
                    displayName = ensureExtension(displayName, target.extension),
                    mimeType = target.mimeType,
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
            renderAudioWithFfmpeg(
                input = input,
                settings = settings.copy(startMs = startMs, endMs = previewEndMs),
                target = AudioExportFormats.M4A_AAC,
                output = output,
            )
            output
        } catch (error: Throwable) {
            output.delete()
            throw error
        }
    }

    private suspend fun renderAudioWithFfmpeg(
        input: Uri,
        settings: AudioExportSettings,
        target: AudioExportTarget,
        output: File,
    ) = withContext(Dispatchers.IO) {
        val cachedInput = cacheAudioInput(input)
        try {
            val startMs = settings.startMs.coerceAtLeast(0L)
            val endMs = settings.endMs
            val arguments = buildList {
                add("-hide_banner")
                add("-loglevel")
                add("error")
                add("-nostdin")
                add("-y")
                if (startMs > 0L) {
                    add("-ss")
                    add(seconds(startMs))
                }
                add("-i")
                add(cachedInput.absolutePath)
                if (endMs != Long.MAX_VALUE) {
                    add("-t")
                    add(seconds((endMs - startMs).coerceAtLeast(1L)))
                }
                add("-map")
                add("0:a:0")
                add("-vn")

                val filters = buildAudioFilters(settings)
                if (filters.isNotEmpty()) {
                    add("-af")
                    add(filters.joinToString(","))
                }

                val outputRate = settings.sampleRateHz.takeIf { it > 0 }
                if (outputRate != null) {
                    add("-ar")
                    add(outputRate.toString())
                }

                addAll(target.ffmpegArguments)
                add(output.absolutePath)
            }

            val session = try {
                FFmpegKit.executeWithArguments(arguments.toTypedArray())
            } catch (error: NoClassDefFoundError) {
                throw IllegalStateException(
                    "FFmpeg runtime dependency is missing: ${error.message.orEmpty()}",
                    error,
                )
            } catch (error: UnsatisfiedLinkError) {
                throw IllegalStateException(
                    "FFmpeg native libraries could not load on this device: ${error.message.orEmpty()}",
                    error,
                )
            } catch (error: ExceptionInInitializerError) {
                throw IllegalStateException(
                    "FFmpeg failed to initialize on this device: ${error.cause?.message ?: error.message.orEmpty()}",
                    error,
                )
            }

            if (!ReturnCode.isSuccess(session.returnCode)) {
                val details = buildString {
                    append(session.allLogsAsString.orEmpty().takeLast(3_000))
                    if (isBlank()) append(session.failStackTrace.orEmpty())
                }.trim()
                error(
                    if (details.isBlank()) {
                        "FFmpeg could not encode ${target.label}; return code ${session.returnCode}"
                    } else {
                        details
                    },
                )
            }
            check(output.isFile && output.length() > 0L) {
                "FFmpeg completed without creating an output file"
            }
        } finally {
            cachedInput.delete()
        }
    }

    /**
     * Some document providers expose content URIs that FFmpeg cannot seek or open
     * reliably. The shared robust loader tries a descriptor, typed asset and stream
     * before returning a normal private file that FFmpeg can seek.
     */
    private fun cacheAudioInput(input: Uri): File {
        val extension = extensionForMime(context.contentResolver.getType(input))
        return RobustMediaLoader.copyUriToCache(context, input, extension).also { cached ->
            check(cached.length() > 0L) { "The selected audio file is empty or unavailable" }
        }
    }

    private fun extensionForMime(mimeType: String?): String = when (mimeType?.lowercase()) {
        "audio/mpeg" -> ".mp3"
        "audio/mp4", "audio/x-m4a" -> ".m4a"
        "audio/wav", "audio/x-wav" -> ".wav"
        "audio/flac" -> ".flac"
        "audio/ogg" -> ".ogg"
        "audio/aac" -> ".aac"
        "audio/amr" -> ".amr"
        else -> ".audio"
    }

    private fun buildAudioFilters(settings: AudioExportSettings): List<String> {
        val speed = settings.speed.coerceIn(0.25f, 4f).toDouble()
        val pitch = settings.pitch.coerceIn(0.25f, 4f).toDouble()
        return buildList {
            if (abs(pitch - 1.0) > 0.0005) {
                val shiftedRate = (48_000.0 * pitch).roundToInt().coerceAtLeast(1_000)
                add("aresample=48000")
                add("asetrate=$shiftedRate")
            }
            addAll(atempoChain(speed / pitch))
            if (abs(pitch - 1.0) > 0.0005) {
                add("aresample=${settings.sampleRateHz.takeIf { it > 0 } ?: 48_000}")
            }
        }
    }

    private fun atempoChain(requestedFactor: Double): List<String> {
        var factor = requestedFactor.coerceIn(0.0625, 16.0)
        val filters = mutableListOf<String>()
        while (factor < 0.5) {
            filters += "atempo=0.5"
            factor /= 0.5
        }
        while (factor > 2.0) {
            filters += "atempo=2.0"
            factor /= 2.0
        }
        if (abs(factor - 1.0) > 0.0005) {
            filters += "atempo=${decimal(factor)}"
        }
        return filters
    }

    private fun seconds(milliseconds: Long): String =
        String.format(Locale.US, "%.6f", milliseconds / 1_000.0)

    private fun decimal(value: Double): String =
        String.format(Locale.US, "%.6f", value)

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
                resolver.openOutputStream(uri, "w")?.use { destination ->
                    source.inputStream().use { input -> input.copyTo(destination) }
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
