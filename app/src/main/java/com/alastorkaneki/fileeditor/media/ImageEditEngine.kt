package com.alastorkaneki.fileeditor.media

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

data class ImageEditSettings(
    val brightness: Float = 0f,
    val contrast: Float = 1f,
    val saturation: Float = 1f,
    val exposure: Float = 0f,
    val temperature: Float = 0f,
    val tint: Float = 0f,
    val hue: Float = 0f,
    val highlights: Float = 0f,
    val shadows: Float = 0f,
    val fade: Float = 0f,
    val vignette: Float = 0f,
    val grain: Float = 0f,
    val sharpness: Float = 0f,
    val blur: Float = 0f,
    val pixelate: Int = 1,
    val posterize: Int = 32,
    val grayscale: Boolean = false,
    val sepia: Boolean = false,
    val invert: Boolean = false,
    val rotationDegrees: Int = 0,
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false,
    val cropAspect: Float = 0f,
    val seed: Int = 2026,
)

object ImageEditEngine {
    fun render(source: Bitmap, settings: ImageEditSettings, maxDimension: Int = 1600): Bitmap {
        var working = scaleDown(source, maxDimension)
        working = crop(working, settings.cropAspect)
        working = transform(
            working,
            settings.rotationDegrees,
            settings.flipHorizontal,
            settings.flipVertical,
        )
        working = pixelate(working, settings.pixelate)

        val width = working.width
        val height = working.height
        val pixels = IntArray(width * height)
        working.getPixels(pixels, 0, width, 0, 0, width, height)
        val random = Random(settings.seed)
        val exposureMultiplier = 2.0.pow(settings.exposure.coerceIn(-3f, 3f).toDouble()).toFloat()
        val contrast = settings.contrast.coerceIn(0.2f, 3f)
        val saturation = settings.saturation.coerceIn(0f, 3f)
        val brightness = settings.brightness.coerceIn(-1f, 1f) * 255f
        val hueRadians = settings.hue.coerceIn(-180f, 180f) * PI.toFloat() / 180f
        val cosHue = cos(hueRadians)
        val sinHue = sin(hueRadians)
        val levels = settings.posterize.coerceIn(2, 64)
        val grainAmount = settings.grain.coerceIn(0f, 1f) * 80f

        for (y in 0 until height) {
            val ny = (y - height / 2f) / (height / 2f).coerceAtLeast(1f)
            for (x in 0 until width) {
                val index = y * width + x
                val color = pixels[index]
                val alpha = Color.alpha(color)
                var r = Color.red(color).toFloat()
                var g = Color.green(color).toFloat()
                var b = Color.blue(color).toFloat()

                r = (r * exposureMultiplier - 128f) * contrast + 128f + brightness
                g = (g * exposureMultiplier - 128f) * contrast + 128f + brightness
                b = (b * exposureMultiplier - 128f) * contrast + 128f + brightness

                val luminance = 0.2126f * r + 0.7152f * g + 0.0722f * b
                r = luminance + (r - luminance) * saturation
                g = luminance + (g - luminance) * saturation
                b = luminance + (b - luminance) * saturation

                val temp = settings.temperature.coerceIn(-1f, 1f) * 46f
                val tint = settings.tint.coerceIn(-1f, 1f) * 34f
                r += temp
                b -= temp
                g += tint
                r -= tint * 0.35f
                b -= tint * 0.35f

                val currentLum = ((r + g + b) / 3f).coerceIn(0f, 255f)
                val shadowWeight = (1f - currentLum / 255f).pow(2)
                val highlightWeight = (currentLum / 255f).pow(2)
                val shadowDelta = settings.shadows.coerceIn(-1f, 1f) * 95f * shadowWeight
                val highlightDelta = settings.highlights.coerceIn(-1f, 1f) * 95f * highlightWeight
                r += shadowDelta + highlightDelta
                g += shadowDelta + highlightDelta
                b += shadowDelta + highlightDelta

                if (settings.hue != 0f) {
                    val hr = (0.213f + cosHue * 0.787f - sinHue * 0.213f) * r +
                        (0.715f - cosHue * 0.715f - sinHue * 0.715f) * g +
                        (0.072f - cosHue * 0.072f + sinHue * 0.928f) * b
                    val hg = (0.213f - cosHue * 0.213f + sinHue * 0.143f) * r +
                        (0.715f + cosHue * 0.285f + sinHue * 0.140f) * g +
                        (0.072f - cosHue * 0.072f - sinHue * 0.283f) * b
                    val hb = (0.213f - cosHue * 0.213f - sinHue * 0.787f) * r +
                        (0.715f - cosHue * 0.715f + sinHue * 0.715f) * g +
                        (0.072f + cosHue * 0.928f + sinHue * 0.072f) * b
                    r = hr
                    g = hg
                    b = hb
                }

                if (settings.grayscale) {
                    val gray = 0.299f * r + 0.587f * g + 0.114f * b
                    r = gray
                    g = gray
                    b = gray
                }
                if (settings.sepia) {
                    val sr = r * 0.393f + g * 0.769f + b * 0.189f
                    val sg = r * 0.349f + g * 0.686f + b * 0.168f
                    val sb = r * 0.272f + g * 0.534f + b * 0.131f
                    r = sr
                    g = sg
                    b = sb
                }
                if (settings.invert) {
                    r = 255f - r
                    g = 255f - g
                    b = 255f - b
                }

                val fade = settings.fade.coerceIn(0f, 1f)
                r = r * (1f - fade) + 214f * fade
                g = g * (1f - fade) + 207f * fade
                b = b * (1f - fade) + 197f * fade

                if (levels < 64) {
                    val step = 255f / (levels - 1).coerceAtLeast(1)
                    r = (r / step).roundToInt() * step
                    g = (g / step).roundToInt() * step
                    b = (b / step).roundToInt() * step
                }

                if (grainAmount > 0f) {
                    val grain = (random.nextFloat() - 0.5f) * grainAmount
                    r += grain
                    g += grain
                    b += grain
                }

                val vignette = settings.vignette.coerceIn(0f, 1f)
                if (vignette > 0f) {
                    val nx = (x - width / 2f) / (width / 2f).coerceAtLeast(1f)
                    val distance = sqrt(nx * nx + ny * ny).coerceIn(0f, 1.5f)
                    val factor = 1f - vignette * (distance * distance).coerceIn(0f, 0.88f)
                    r *= factor
                    g *= factor
                    b *= factor
                }

                pixels[index] = Color.argb(alpha, clamp(r), clamp(g), clamp(b))
            }
        }

        var output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, width, 0, 0, width, height)
        }
        if (working !== source) working.recycle()

        if (settings.blur > 0.01f) {
            output = softBlur(output, settings.blur)
        }
        if (settings.sharpness > 0.01f) {
            output = sharpen(output, settings.sharpness)
        }
        return output
    }

    private fun scaleDown(source: Bitmap, maxDimension: Int): Bitmap {
        val largest = max(source.width, source.height)
        if (largest <= maxDimension) return source.copy(Bitmap.Config.ARGB_8888, true)
        val scale = maxDimension.toFloat() / largest.toFloat()
        return Bitmap.createScaledBitmap(
            source,
            (source.width * scale).roundToInt().coerceAtLeast(1),
            (source.height * scale).roundToInt().coerceAtLeast(1),
            true,
        )
    }

    private fun crop(source: Bitmap, aspect: Float): Bitmap {
        if (aspect <= 0f) return source
        val current = source.width.toFloat() / source.height.toFloat()
        if (kotlin.math.abs(current - aspect) < 0.01f) return source
        val result = if (current > aspect) {
            val width = (source.height * aspect).roundToInt().coerceAtLeast(1)
            Bitmap.createBitmap(source, (source.width - width) / 2, 0, width, source.height)
        } else {
            val height = (source.width / aspect).roundToInt().coerceAtLeast(1)
            Bitmap.createBitmap(source, 0, (source.height - height) / 2, source.width, height)
        }
        if (result !== source) source.recycle()
        return result
    }

    private fun transform(source: Bitmap, rotation: Int, flipH: Boolean, flipV: Boolean): Bitmap {
        val normalizedRotation = ((rotation % 360) + 360) % 360
        if (normalizedRotation == 0 && !flipH && !flipV) return source
        val matrix = Matrix().apply {
            postScale(if (flipH) -1f else 1f, if (flipV) -1f else 1f)
            postRotate(normalizedRotation.toFloat())
        }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true).also {
            if (it !== source) source.recycle()
        }
    }

    private fun pixelate(source: Bitmap, amount: Int): Bitmap {
        if (amount <= 1) return source
        val smallWidth = (source.width / amount).coerceAtLeast(1)
        val smallHeight = (source.height / amount).coerceAtLeast(1)
        val small = Bitmap.createScaledBitmap(source, smallWidth, smallHeight, false)
        val result = Bitmap.createScaledBitmap(small, source.width, source.height, false)
        small.recycle()
        source.recycle()
        return result
    }

    private fun softBlur(source: Bitmap, amount: Float): Bitmap {
        val divisor = (1f + amount.coerceIn(0f, 1f) * 18f).roundToInt().coerceAtLeast(2)
        val small = Bitmap.createScaledBitmap(
            source,
            (source.width / divisor).coerceAtLeast(1),
            (source.height / divisor).coerceAtLeast(1),
            true,
        )
        val result = Bitmap.createScaledBitmap(small, source.width, source.height, true)
        small.recycle()
        source.recycle()
        return result
    }

    private fun sharpen(source: Bitmap, amount: Float): Bitmap {
        val width = source.width
        val height = source.height
        if (width < 3 || height < 3) return source
        val input = IntArray(width * height)
        source.getPixels(input, 0, width, 0, 0, width, height)
        val output = input.copyOf()
        val strength = amount.coerceIn(0f, 1.5f) * 1.7f
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val i = y * width + x
                val center = input[i]
                val left = input[i - 1]
                val right = input[i + 1]
                val top = input[i - width]
                val bottom = input[i + width]
                fun channel(selector: (Int) -> Int): Int {
                    val c = selector(center)
                    val blurred = (selector(left) + selector(right) + selector(top) + selector(bottom)) / 4f
                    return clamp(c + (c - blurred) * strength)
                }
                output[i] = Color.argb(
                    Color.alpha(center),
                    channel(Color::red),
                    channel(Color::green),
                    channel(Color::blue),
                )
            }
        }
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            setPixels(output, 0, width, 0, 0, width, height)
            source.recycle()
        }
    }

    private fun clamp(value: Float): Int = min(255f, max(0f, value)).roundToInt()
}
