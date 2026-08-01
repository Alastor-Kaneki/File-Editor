package com.alastorkaneki.fileeditor.data

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Decodes picker and MediaStore Uris without assuming that openInputStream is available.
 * Some cloud/document providers only expose file descriptors or typed assets, which was
 * the cause of Glitch Lab's "Unable to open image" error.
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
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val raw = retriever.getFrameAtTime(timeUs.coerceAtLeast(0L), MediaMetadataRetriever.OPTION_CLOSEST)
                ?: error("The video decoder did not return a frame")
            scaleDown(raw, maxDimension)
        } finally {
            retriever.release()
        }
    }

    fun readDurationMs(context: Context, uri: Uri): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?.coerceAtLeast(1L)
                ?: error("Unable to read media duration")
        } finally {
            retriever.release()
        }
    }

    fun copyUriToCache(context: Context, uri: Uri, suffix: String = ".media"): File {
        val temp = File.createTempFile("file_editor_input_", suffix, context.cacheDir)
        val resolver = context.contentResolver
        val copied = runCatching {
            resolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                descriptor.fileDescriptor.let { fd ->
                    java.io.FileInputStream(fd).use { input ->
                        FileOutputStream(temp).use { output -> input.copyTo(output) }
                    }
                }
                true
            } ?: false
        }.getOrDefault(false)

        if (!copied) {
            resolver.openTypedAssetFileDescriptor(uri, "*/*", null)?.use { asset ->
                java.io.FileInputStream(asset.fileDescriptor).use { input ->
                    FileOutputStream(temp).use { output -> input.copyTo(output) }
                }
            } ?: resolver.openInputStream(uri)?.use { input ->
                FileOutputStream(temp).use { output -> input.copyTo(output) }
            } ?: run {
                temp.delete()
                error("The selected document could not be opened")
            }
        }
        return temp
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
