package com.alastorkaneki.fileeditor.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.alastorkaneki.fileeditor.data.VisualMediaRepository
import com.squareup.gifencoder.FloydSteinbergDitherer
import com.squareup.gifencoder.GifEncoder
import com.squareup.gifencoder.ImageOptions
import com.squareup.gifencoder.KMeansQuantizer
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class GifOptions(
    val delayMs: Int = 180,
    val maxFrames: Int = 24,
    val maxDimension: Int = 720,
    val glitch: GlitchSettings? = null,
)

class GifEngine(private val context: Context) {
    private val repository = VisualMediaRepository(context)

    fun createFromImages(uris: List<Uri>, options: GifOptions): File {
        require(uris.isNotEmpty()) { "Choose at least one image" }
        val loaded = uris.take(options.maxFrames.coerceIn(2, 60)).map {
            repository.loadBitmap(it, options.maxDimension.coerceIn(128, 1440))
        }
        return try {
            encodeFrames(loaded, options)
        } finally {
            loaded.forEach(Bitmap::recycle)
        }
    }

    fun createFromVideo(uri: Uri, options: GifOptions): File {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(context, uri)
        return try {
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?.coerceAtLeast(1L)
                ?: error("Unable to read video duration")
            val requestedFrames = options.maxFrames.coerceIn(2, 60)
            val minimumFrameDelay = options.delayMs.coerceAtLeast(40)
            val durationLimited = max(2, (durationMs / minimumFrameDelay).toInt())
            val frameCount = min(requestedFrames, durationLimited)
            val frames = mutableListOf<Bitmap>()

            for (index in 0 until frameCount) {
                val fraction = if (frameCount == 1) 0.0 else index.toDouble() / (frameCount - 1).toDouble()
                val timeUs = (durationMs * 1000L * fraction).toLong()
                retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)?.let(frames::add)
            }
            require(frames.size >= 2) { "The video did not provide enough frames" }
            try {
                encodeFrames(frames, options)
            } finally {
                frames.forEach(Bitmap::recycle)
            }
        } finally {
            retriever.release()
        }
    }

    private fun encodeFrames(frames: List<Bitmap>, options: GifOptions): File {
        require(frames.isNotEmpty())
        val (targetWidth, targetHeight) = targetSize(
            frames.first().width,
            frames.first().height,
            options.maxDimension.coerceIn(128, 1440),
        )
        val output = File.createTempFile("file_editor_", ".gif", context.cacheDir)
        output.outputStream().use { stream ->
            val encoder = GifEncoder(stream, targetWidth, targetHeight, 0)
            val imageOptions = ImageOptions().apply {
                setDelay(options.delayMs.coerceIn(40, 5000).toLong(), TimeUnit.MILLISECONDS)
                setColorQuantizer(KMeansQuantizer.INSTANCE)
                setDitherer(FloydSteinbergDitherer.INSTANCE)
            }

            frames.forEachIndexed { index, source ->
                val normalized = scaleAndCrop(source, targetWidth, targetHeight)
                val rendered = options.glitch?.let {
                    GlitchEngine.render(normalized, it.copy(seed = it.seed + index * 7919))
                } ?: normalized

                try {
                    encoder.addImage(toArgbArray(rendered), imageOptions)
                } finally {
                    if (rendered !== normalized) rendered.recycle()
                    if (normalized !== source) normalized.recycle()
                }
            }
            encoder.finishEncoding()
        }
        return output
    }

    private fun targetSize(width: Int, height: Int, maxDimension: Int): Pair<Int, Int> {
        val largest = max(width, height)
        if (largest <= maxDimension) return width.coerceAtLeast(2) to height.coerceAtLeast(2)
        val scale = maxDimension.toFloat() / largest.toFloat()
        return (width * scale).roundToInt().coerceAtLeast(2) to
            (height * scale).roundToInt().coerceAtLeast(2)
    }

    private fun scaleAndCrop(source: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
        if (source.width == targetWidth && source.height == targetHeight) return source
        val output = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(Color.BLACK)

        val sourceRatio = source.width.toFloat() / source.height.toFloat()
        val targetRatio = targetWidth.toFloat() / targetHeight.toFloat()
        val sourceRect = if (sourceRatio > targetRatio) {
            val cropWidth = (source.height * targetRatio).roundToInt()
            val left = (source.width - cropWidth) / 2
            Rect(left, 0, left + cropWidth, source.height)
        } else {
            val cropHeight = (source.width / targetRatio).roundToInt()
            val top = (source.height - cropHeight) / 2
            Rect(0, top, source.width, top + cropHeight)
        }
        canvas.drawBitmap(
            source,
            sourceRect,
            Rect(0, 0, targetWidth, targetHeight),
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
        )
        return output
    }

    private fun toArgbArray(bitmap: Bitmap): Array<IntArray> {
        val width = bitmap.width
        val height = bitmap.height
        val flat = IntArray(width * height)
        bitmap.getPixels(flat, 0, width, 0, 0, width, height)
        return Array(width) { x ->
            IntArray(height) { y -> flat[y * width + x] }
        }
    }
}
