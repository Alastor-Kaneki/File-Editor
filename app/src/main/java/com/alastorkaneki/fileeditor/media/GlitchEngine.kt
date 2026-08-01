package com.alastorkaneki.fileeditor.media

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

data class GlitchSettings(
    val intensity: Float = 0.45f,
    val rgbShift: Int = 10,
    val verticalRgbShift: Int = 0,
    val sliceCount: Int = 14,
    val sliceHeight: Float = 0.08f,
    val blockAmount: Float = 0.35f,
    val noise: Float = 0.08f,
    val scanlines: Float = 0.25f,
    val scanlineSpacing: Int = 3,
    val pixelSort: Float = 0f,
    val smear: Float = 0f,
    val ghosting: Float = 0f,
    val posterize: Int = 64,
    val mosaic: Int = 1,
    val invertBlocks: Float = 0.18f,
    val monochromeNoise: Boolean = false,
    val verticalTearing: Boolean = false,
    val seed: Int = 1337,
)

object GlitchEngine {
    fun render(source: Bitmap, settings: GlitchSettings): Bitmap {
        val width = source.width
        val height = source.height
        require(width > 0 && height > 0)

        val original = IntArray(width * height)
        source.getPixels(original, 0, width, 0, 0, width, height)
        val output = original.copyOf()
        val random = Random(settings.seed)
        val strength = settings.intensity.coerceIn(0f, 1f)

        applyRgbSplit(
            input = original,
            output = output,
            width = width,
            height = height,
            horizontalOffset = (settings.rgbShift * (0.3f + strength)).roundToInt().coerceAtLeast(0),
            verticalOffset = (settings.verticalRgbShift * (0.3f + strength)).roundToInt(),
        )
        applySlices(
            pixels = output,
            width = width,
            height = height,
            count = (settings.sliceCount * (0.45f + strength)).roundToInt().coerceAtLeast(0),
            maxHeightFraction = settings.sliceHeight.coerceIn(0.005f, 0.4f),
            strength = strength,
            vertical = settings.verticalTearing,
            random = random,
        )
        applyBlocks(
            pixels = output,
            width = width,
            height = height,
            amount = settings.blockAmount.coerceIn(0f, 1f),
            inversionChance = settings.invertBlocks.coerceIn(0f, 1f),
            strength = strength,
            random = random,
        )
        applyPixelSort(
            pixels = output,
            width = width,
            height = height,
            amount = settings.pixelSort.coerceIn(0f, 1f) * strength,
            random = random,
        )
        applySmear(
            pixels = output,
            width = width,
            height = height,
            amount = settings.smear.coerceIn(0f, 1f) * strength,
            random = random,
        )
        applyGhosting(
            original = original,
            pixels = output,
            width = width,
            height = height,
            amount = settings.ghosting.coerceIn(0f, 1f) * strength,
        )
        applyNoise(
            pixels = output,
            amount = (settings.noise * (0.35f + strength)).coerceIn(0f, 0.85f),
            monochrome = settings.monochromeNoise,
            random = random,
        )
        applyPosterize(output, settings.posterize.coerceIn(2, 64))
        applyScanlines(
            pixels = output,
            width = width,
            height = height,
            amount = settings.scanlines.coerceIn(0f, 1f) * strength,
            spacing = settings.scanlineSpacing.coerceIn(1, 12),
        )

        var bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            setPixels(output, 0, width, 0, 0, width, height)
        }
        if (settings.mosaic > 1) {
            val factor = settings.mosaic.coerceIn(2, 48)
            val small = Bitmap.createScaledBitmap(
                bitmap,
                (width / factor).coerceAtLeast(1),
                (height / factor).coerceAtLeast(1),
                false,
            )
            val pixelated = Bitmap.createScaledBitmap(small, width, height, false)
            small.recycle()
            bitmap.recycle()
            bitmap = pixelated
        }
        return bitmap
    }

    private fun applyRgbSplit(
        input: IntArray,
        output: IntArray,
        width: Int,
        height: Int,
        horizontalOffset: Int,
        verticalOffset: Int,
    ) {
        if (horizontalOffset == 0 && verticalOffset == 0) return
        for (y in 0 until height) {
            for (x in 0 until width) {
                val redX = wrap(x - horizontalOffset, width)
                val redY = wrap(y - verticalOffset, height)
                val blueX = wrap(x + horizontalOffset, width)
                val blueY = wrap(y + verticalOffset, height)
                val center = input[y * width + x]
                val red = input[redY * width + redX]
                val blue = input[blueY * width + blueX]
                output[y * width + x] = Color.argb(
                    Color.alpha(center),
                    Color.red(red),
                    Color.green(center),
                    Color.blue(blue),
                )
            }
        }
    }

    private fun applySlices(
        pixels: IntArray,
        width: Int,
        height: Int,
        count: Int,
        maxHeightFraction: Float,
        strength: Float,
        vertical: Boolean,
        random: Random,
    ) {
        if (count <= 0 || width < 2 || height < 2) return
        repeat(count.coerceAtMost(120)) {
            if (vertical) {
                val sliceWidth = random.nextInt(1, max(2, (width * maxHeightFraction).roundToInt()))
                val startX = random.nextInt(0, max(1, width - sliceWidth))
                val maxOffset = max(1, (height * (0.02f + strength * 0.22f)).roundToInt())
                val offset = random.nextInt(-maxOffset, maxOffset + 1)
                val column = IntArray(height)
                for (x in startX until min(width, startX + sliceWidth)) {
                    for (y in 0 until height) column[y] = pixels[y * width + x]
                    for (y in 0 until height) pixels[y * width + x] = column[wrap(y - offset, height)]
                }
            } else {
                val sliceHeight = random.nextInt(1, max(2, (height * maxHeightFraction).roundToInt()))
                val startY = random.nextInt(0, max(1, height - sliceHeight))
                val maxOffset = max(1, (width * (0.02f + strength * 0.22f)).roundToInt())
                val offset = random.nextInt(-maxOffset, maxOffset + 1)
                val rowCopy = IntArray(width)
                for (y in startY until min(height, startY + sliceHeight)) {
                    val row = y * width
                    System.arraycopy(pixels, row, rowCopy, 0, width)
                    for (x in 0 until width) pixels[row + x] = rowCopy[wrap(x - offset, width)]
                }
            }
        }
    }

    private fun applyBlocks(
        pixels: IntArray,
        width: Int,
        height: Int,
        amount: Float,
        inversionChance: Float,
        strength: Float,
        random: Random,
    ) {
        val count = (amount * (8f + strength * 42f)).roundToInt()
        repeat(count) {
            val blockWidth = random.nextInt(3, max(4, (width * (0.03f + 0.2f * amount)).roundToInt()))
            val blockHeight = random.nextInt(2, max(3, (height * (0.01f + 0.1f * amount)).roundToInt()))
            val startX = random.nextInt(0, max(1, width - blockWidth))
            val startY = random.nextInt(0, max(1, height - blockHeight))
            val mode = if (random.nextFloat() < inversionChance) 1 else random.nextInt(4)
            for (y in startY until min(height, startY + blockHeight)) {
                for (x in startX until min(width, startX + blockWidth)) {
                    val index = y * width + x
                    val color = pixels[index]
                    pixels[index] = when (mode) {
                        0 -> Color.argb(Color.alpha(color), Color.blue(color), Color.red(color), Color.green(color))
                        1 -> Color.argb(
                            Color.alpha(color),
                            255 - Color.red(color),
                            255 - Color.green(color),
                            255 - Color.blue(color),
                        )
                        2 -> color and 0xFFF0E0F0.toInt()
                        else -> Color.argb(
                            Color.alpha(color),
                            clamp(Color.red(color) + random.nextInt(-80, 81)),
                            clamp(Color.green(color) + random.nextInt(-80, 81)),
                            clamp(Color.blue(color) + random.nextInt(-80, 81)),
                        )
                    }
                }
            }
        }
    }

    private fun applyPixelSort(
        pixels: IntArray,
        width: Int,
        height: Int,
        amount: Float,
        random: Random,
    ) {
        if (amount <= 0f) return
        val rows = (height * amount).roundToInt().coerceAtLeast(1)
        repeat(rows.coerceAtMost(height)) {
            val y = random.nextInt(height)
            val start = random.nextInt(width)
            val length = random.nextInt(2, max(3, (width * (0.08f + amount * 0.7f)).roundToInt()))
            val end = min(width, start + length)
            val segment = IntArray(end - start) { x -> pixels[y * width + start + x] }
            segment.sortByBrightness()
            for (x in segment.indices) pixels[y * width + start + x] = segment[x]
        }
    }

    private fun applySmear(
        pixels: IntArray,
        width: Int,
        height: Int,
        amount: Float,
        random: Random,
    ) {
        if (amount <= 0f) return
        val streaks = (amount * 55f).roundToInt()
        repeat(streaks) {
            val y = random.nextInt(height)
            val startX = random.nextInt(width)
            val length = random.nextInt(2, max(3, (width * amount * 0.75f).roundToInt()))
            val color = pixels[y * width + startX]
            for (x in startX until min(width, startX + length)) {
                val old = pixels[y * width + x]
                val blend = (0.3f + amount * 0.65f).coerceIn(0f, 1f)
                pixels[y * width + x] = blend(old, color, blend)
            }
        }
    }

    private fun applyGhosting(
        original: IntArray,
        pixels: IntArray,
        width: Int,
        height: Int,
        amount: Float,
    ) {
        if (amount <= 0f) return
        val offset = max(1, (width * amount * 0.12f).roundToInt())
        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until width) {
                val ghost = original[row + wrap(x - offset, width)]
                pixels[row + x] = blend(pixels[row + x], ghost, amount * 0.45f)
            }
        }
    }

    private fun applyNoise(
        pixels: IntArray,
        amount: Float,
        monochrome: Boolean,
        random: Random,
    ) {
        if (amount <= 0f) return
        val affected = (pixels.size * amount).roundToInt().coerceAtMost(pixels.size)
        repeat(affected) {
            val index = random.nextInt(pixels.size)
            val color = pixels[index]
            if (monochrome) {
                val value = random.nextInt(256)
                pixels[index] = Color.argb(Color.alpha(color), value, value, value)
            } else {
                pixels[index] = Color.argb(
                    Color.alpha(color),
                    clamp(Color.red(color) + random.nextInt(-72, 73)),
                    clamp(Color.green(color) + random.nextInt(-72, 73)),
                    clamp(Color.blue(color) + random.nextInt(-72, 73)),
                )
            }
        }
    }

    private fun applyPosterize(pixels: IntArray, levels: Int) {
        if (levels >= 64) return
        val step = 255f / (levels - 1).coerceAtLeast(1)
        for (index in pixels.indices) {
            val color = pixels[index]
            fun quantize(value: Int): Int = ((value / step).roundToInt() * step).roundToInt().coerceIn(0, 255)
            pixels[index] = Color.argb(
                Color.alpha(color),
                quantize(Color.red(color)),
                quantize(Color.green(color)),
                quantize(Color.blue(color)),
            )
        }
    }

    private fun applyScanlines(
        pixels: IntArray,
        width: Int,
        height: Int,
        amount: Float,
        spacing: Int,
    ) {
        if (amount <= 0f) return
        val multiplier = 1f - amount.coerceIn(0f, 0.82f)
        for (y in spacing - 1 until height step spacing) {
            val row = y * width
            for (x in 0 until width) {
                val color = pixels[row + x]
                pixels[row + x] = Color.argb(
                    Color.alpha(color),
                    (Color.red(color) * multiplier).roundToInt(),
                    (Color.green(color) * multiplier).roundToInt(),
                    (Color.blue(color) * multiplier).roundToInt(),
                )
            }
        }
    }

    private fun IntArray.sortByBrightness() {
        val boxed = toTypedArray()
        boxed.sortBy { Color.red(it) + Color.green(it) + Color.blue(it) }
        for (index in indices) this[index] = boxed[index]
    }

    private fun blend(first: Int, second: Int, amount: Float): Int {
        val t = amount.coerceIn(0f, 1f)
        return Color.argb(
            (Color.alpha(first) * (1f - t) + Color.alpha(second) * t).roundToInt(),
            (Color.red(first) * (1f - t) + Color.red(second) * t).roundToInt(),
            (Color.green(first) * (1f - t) + Color.green(second) * t).roundToInt(),
            (Color.blue(first) * (1f - t) + Color.blue(second) * t).roundToInt(),
        )
    }

    private fun wrap(value: Int, size: Int): Int {
        val remainder = value % size
        return if (remainder < 0) remainder + size else remainder
    }

    private fun clamp(value: Int): Int = min(255, max(0, value))
}
