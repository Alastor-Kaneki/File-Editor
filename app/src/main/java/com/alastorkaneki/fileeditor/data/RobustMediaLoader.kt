package com.alastorkaneki.fileeditor.data

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Decodes picker and MediaStore Uris without assuming that openInputStream is available.
 * Some cloud/document providers only expose file descriptors or typed assets.
 */
object RobustMediaLoader {
    fun persistReadPermission(context: Context, uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
    }

    fun loadBitmap(context: Context, uri: Uri, maxDimension: Int = 1600): Bitmap {
        require(maxDimension >= 64) { "Maximum dimension is too small" }
        val resolver = context.contentResolver
        val failures = mutableListOf<Throwable>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching {
                val source = ImageDecoder.createSource(resolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    val width = info.size.width.coerceAtLeast(1)
                    val height = info.size.height.coerceAtLeast(1)
                    val largest = max(width, height)
                    if (largest > maxDimension) {
                        val scale = maxDimension.toFloat() / largest.toFloat()
                        decoder.setTargetSize(
                            (width * scale).roundToInt().coerceAtLeast(1),
                            (height * scale).roundToInt().coerceAtLeast(1),
                        )
                    }
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.isMutableRequired = true
                    decoder.setOnPartialImageListener { true }
                }.ensureArgb()
            }.onSuccess { return it }
                .onFailure(failures::add)
        }

        runCatching {
            decodeWithFileDescriptor(context, uri, maxDimension)
        }.onSuccess { return it }
            .onFailure(failures::add)

        runCatching {
            val temp = copyUriToCache(context, uri)
            try {
                decodeFile(temp, maxDimension)
            } finally {
                temp.delete()
            }
        }.onSuccess { return it }
            .onFailure(failures::add)

        val detail = failures
            .asReversed()
            .firstNotNullOfOrNull { it.message?.takeIf(String::isNotBlank) }
            ?: "The selected provider did not expose readable image data"
        throw IllegalArgumentException("Unable to open image. $detail", failures.lastOrNull())
    }

    fun loadVideoFrame(
        context: Context,
        uri: Uri,
        timeUs: Long = 0L,
        maxDimension: Int = 1280,
    ): Bitmap {
        val failures = mutableListOf<Throwable>()

        runCatching {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)
                retriever.getFrameAtTime(
                    timeUs.coerceAtLeast(0L),
                    MediaMetadataRetriever.OPTION_CLOSEST,
                ) ?: error("The video decoder did not return a frame")
            } finally {
                retriever.release()
            }
        }.onSuccess { return scaleDown(it, maxDimension) }
            .onFailure(failures::add)

        val cached = copyUriToCache(context, uri, suffixForMime(context, uri))
        return try {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(cached.absolutePath)
                val raw = retriever.getFrameAtTime(
                    timeUs.coerceAtLeast(0L),
                    MediaMetadataRetriever.OPTION_CLOSEST,
                ) ?: error("The cached video decoder did not return a frame")
                scaleDown(raw, maxDimension)
            } finally {
                retriever.release()
            }
        } catch (error: Throwable) {
            val first = failures.firstOrNull()?.message.orEmpty()
            throw IllegalArgumentException(
                "Unable to read video${if (first.isBlank()) "" else ": $first"}",
                error,
            )
        } finally {
            cached.delete()
        }
    }

    /**
     * Reads duration through several Android paths because document providers vary:
     * some support URI access, some only descriptors, and some require a local copy.
     */
    fun readDurationMs(context: Context, uri: Uri): Long {
        val failures = mutableListOf<Throwable>()

        runCatching {
            durationWithRetriever { retriever -> retriever.setDataSource(context, uri) }
        }.onSuccess { return it }
            .onFailure(failures::add)

        runCatching {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { asset ->
                durationWithRetriever { retriever ->
                    if (asset.declaredLength >= 0L) {
                        retriever.setDataSource(
                            asset.fileDescriptor,
                            asset.startOffset,
                            asset.declaredLength,
                        )
                    } else {
                        retriever.setDataSource(asset.fileDescriptor)
                    }
                }
            } ?: error("The provider did not expose an audio file descriptor")
        }.onSuccess { return it }
            .onFailure(failures::add)

        runCatching {
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(context, uri, null)
                durationFromExtractor(extractor)
            } finally {
                extractor.release()
            }
        }.onSuccess { return it }
            .onFailure(failures::add)

        val cached = runCatching {
            copyUriToCache(context, uri, suffixForMime(context, uri))
        }.onFailure(failures::add).getOrNull()

        if (cached != null) {
            try {
                runCatching {
                    durationWithRetriever { retriever ->
                        retriever.setDataSource(cached.absolutePath)
                    }
                }.onSuccess { return it }
                    .onFailure(failures::add)

                runCatching {
                    val extractor = MediaExtractor()
                    try {
                        extractor.setDataSource(cached.absolutePath)
                        durationFromExtractor(extractor)
                    } finally {
                        extractor.release()
                    }
                }.onSuccess { return it }
                    .onFailure(failures::add)
            } finally {
                cached.delete()
            }
        }

        val detail = failures
            .asReversed()
            .firstNotNullOfOrNull { it.message?.takeIf(String::isNotBlank) }
            ?: "No Android media decoder recognized the selected file"
        throw IllegalArgumentException("Unable to read audio. $detail", failures.lastOrNull())
    }

    fun copyUriToCache(context: Context, uri: Uri, suffix: String = ".media"): File {
        val safeSuffix = suffix.takeIf { it.startsWith('.') && it.length <= 16 } ?: ".media"
        val temp = File.createTempFile("file_editor_input_", safeSuffix, context.cacheDir)
        val resolver = context.contentResolver
        val failures = mutableListOf<Throwable>()

        val copied = runCatching {
            resolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                FileInputStream(descriptor.fileDescriptor).use { input ->
                    FileOutputStream(temp).use { output -> input.copyTo(output) }
                }
                temp.length() > 0L
            } ?: false
        }.onFailure(failures::add).getOrDefault(false)

        if (!copied || temp.length() <= 0L) {
            temp.outputStream().use { /* truncate a partial first attempt */ }
            val typedCopied = runCatching {
                resolver.openTypedAssetFileDescriptor(uri, "*/*", null)?.use { asset ->
                    asset.createInputStream().use { input ->
                        FileOutputStream(temp).use { output -> input.copyTo(output) }
                    }
                    temp.length() > 0L
                } ?: false
            }.onFailure(failures::add).getOrDefault(false)

            if (!typedCopied || temp.length() <= 0L) {
                temp.outputStream().use { /* truncate a partial second attempt */ }
                val streamCopied = runCatching {
                    resolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(temp).use { output -> input.copyTo(output) }
                    }
                    temp.length() > 0L
                }.onFailure(failures::add).getOrDefault(false)

                if (!streamCopied || temp.length() <= 0L) {
                    temp.delete()
                    val detail = failures
                        .asReversed()
                        .firstNotNullOfOrNull { it.message?.takeIf(String::isNotBlank) }
                        ?: "The selected document could not be opened"
                    error(detail)
                }
            }
        }
        return temp
    }

    private fun durationWithRetriever(
        configure: (MediaMetadataRetriever) -> Unit,
    ): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            configure(retriever)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?.coerceAtLeast(1L)
                ?: error("MediaMetadataRetriever did not report a duration")
        } finally {
            retriever.release()
        }
    }

    private fun durationFromExtractor(extractor: MediaExtractor): Long {
        var longestDurationUs = 0L
        for (index in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(index)
            val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
            if (!mime.startsWith("audio/") && !mime.startsWith("video/")) continue
            if (format.containsKey(MediaFormat.KEY_DURATION)) {
                longestDurationUs = max(longestDurationUs, format.getLong(MediaFormat.KEY_DURATION))
            }
        }
        require(longestDurationUs > 0L) { "MediaExtractor did not report a duration" }
        return (longestDurationUs / 1_000L).coerceAtLeast(1L)
    }

    private fun suffixForMime(context: Context, uri: Uri): String =
        when (context.contentResolver.getType(uri)?.lowercase()) {
            "audio/mpeg" -> ".mp3"
            "audio/mp4", "audio/x-m4a" -> ".m4a"
            "audio/wav", "audio/x-wav" -> ".wav"
            "audio/flac" -> ".flac"
            "audio/ogg" -> ".ogg"
            "audio/aac" -> ".aac"
            "audio/amr" -> ".amr"
            "video/mp4" -> ".mp4"
            "video/webm" -> ".webm"
            else -> ".media"
        }

    private fun decodeWithFileDescriptor(context: Context, uri: Uri, maxDimension: Int): Bitmap {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openFileDescriptor(uri, "r")?.use { descriptor ->
            BitmapFactory.decodeFileDescriptor(descriptor.fileDescriptor, null, bounds)
        } ?: error("No readable file descriptor was provided")
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Unsupported or damaged image" }

        var sample = 1
        while (bounds.outWidth / sample > maxDimension || bounds.outHeight / sample > maxDimension) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample.coerceAtLeast(1)
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inMutable = true
        }
        return resolver.openFileDescriptor(uri, "r")?.use { descriptor ->
            BitmapFactory.decodeFileDescriptor(descriptor.fileDescriptor, null, options)
        }?.ensureArgb() ?: error("Unable to decode image data")
    }

    private fun decodeFile(file: File, maxDimension: Int): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Unsupported or damaged image" }
        var sample = 1
        while (bounds.outWidth / sample > maxDimension || bounds.outHeight / sample > maxDimension) {
            sample *= 2
        }
        return BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inMutable = true
            },
        )?.ensureArgb() ?: error("Unable to decode cached image")
    }

    private fun scaleDown(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val largest = max(bitmap.width, bitmap.height)
        if (largest <= maxDimension) return bitmap.ensureArgb()
        val scale = maxDimension.toFloat() / largest.toFloat()
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).roundToInt().coerceAtLeast(1),
            (bitmap.height * scale).roundToInt().coerceAtLeast(1),
            true,
        ).also { if (it !== bitmap) bitmap.recycle() }
    }

    private fun Bitmap.ensureArgb(): Bitmap {
        if (config == Bitmap.Config.ARGB_8888 && isMutable) return this
        return copy(Bitmap.Config.ARGB_8888, true).also { if (it !== this) recycle() }
    }
}
