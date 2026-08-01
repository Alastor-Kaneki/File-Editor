package com.alastorkaneki.fileeditor.media

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

data class GlitchSettings(
    val intensity: Float = 0.45f,
    val rgbShift: Int = 10,
    val sliceCount: Int = 14,
    val noise: Float = 0.08f,
    val scanlines: Float = 0.25f,
    val seed: Int = 1337,
)

object GlitchEngine {
    fun render(source: Bitmap, settings: GlitchSettings): Bitmap {
        val width = source.width
        val height = source.height
        require(width > 0 && height > 0)

        val original = IntArray(width * height)
        source.getPixels(original, 0, width, 0, 0, width, height)
        val shifted = original.copyOf()
        val random = Random(settings.seed)
        val strength = settings.intensity.coerceIn(0f, 1f)

        applyRgbSplit(
            input = original,
            output = shifted,
            width = width,
            height = height,
            offset = (settings.rgbShift * (0.35f + strength)).toInt().coerceAtLeast(0),
        )

        applySlices(
            pixels = shifted,
            width = width,
            height = height,
            count = (settings.sliceCount * (0.5f + strength)).toInt().coerceAtLeast(0),
            strength = strength,
            random = random,
        )

        applyBlocks(
            pixels = shifted,
            width = width,
            height = height,
            strength = strength,
            random = random,
        )

        applyNoise(
            pixels = shifted,
            amount = (settings.noise * (0.35f + strength)).coerceIn(0f, 0.75f),
            random = random,
        )

        applyScanlines(
            pixels = shifted,
            width = width,
            height = height,
            amount = settings.scanlines.coerceIn(0f, 1f) * strength,
        )

        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            setPixels(shifted, 0, width, 0, 0, width, height)
        }
    }

    private fun applyRgbSplit(
        input: IntArray,
        output: IntArray,
        width: Int,
        height: Int,
        offset: Int,
    ) {
        if (offset <= 0) return
        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until width) {
                val redPixel = input[row + wrap(x - offset, width)]
                val greenPixel = input[row + x]
                val bluePixel = input[row + wrap(x + offset, width)]
                output[row + x] = Color.argb(
                    Color.alpha(greenPixel),
                    Color.red(redPixel),
                    Color.green(greenPixel),
                    Color.blue(bluePixel),
                )
            }
        }
    }

    private fun applySlices(
        pixels: IntArray,
        width: Int,
        height: Int,
        count: Int,
        strength: Float,
        random: Random,
    ) {
        if (count <= 0 || width < 2 || height < 2) return
        repeat(count.coerceAtMost(80)) {
            val sliceHeight = random.nextInt(1, max(2, (height * (0.01f + strength * 0.08f)).toInt()))
            val startY = random.nextInt(0, max(1, height - sliceHeight))
            val maximumOffset = max(1, (width * (0.02f + strength * 0.22f)).toInt())
            val offset = random.nextInt(-maximumOffset, maximumOffset + 1)
            val rowCopy = IntArray(width)

            for (y in startY until min(height, startY + sliceHeight)) {
                val row = y * width
                System.arraycopy(pixels, row, rowCopy, 0, width)
                for (x in 0 until width) {
                    pixels[row + x] = rowCopy[wrap(x - offset, width)]
                }
            }
        }
    }

    private fun applyBlocks(
        pixels: IntArray,
        width: Int,
        height: Int,
        strength: Float,
        random: Random,
    ) {
        val count = (strength * 18f).toInt()
        repeat(count) {
            val blockWidth = random.nextInt(4, max(5, (width * 0.18f).toInt()))
            val blockHeight = random.nextInt(2, max(3, (height * 0.08f).toInt()))
            val startX = random.nextInt(0, max(1, width - blockWidth))
            val startY = random.nextInt(0, max(1, height - blockHeight))
            val mode = random.nextInt(3)

            for (y in startY until min(height, startY + blockHeight)) {
                for (x in startX until min(width, startX + blockWidth)) {
                    val index = y * width + x
                    val color = pixels[index]
                    pixels[index] = when (mode) {
                        0 -> Color.argb(
                            Color.alpha(color),
                            Color.blue(color),
                            Color.red(color),
                            Color.green(color),
                        )
                        1 -> Color.argb(
                            Color.alpha(color),
                            255 - Color.red(color),
                            255 - Color.green(color),
                            255 - Color.blue(color),
                        )
                        else -> color and 0xFFF0F0F0.toInt()
                    }
                }
            }
        }
    }

    private fun applyNoise(pixels: IntArray, amount: Float, random: Random) {
        if (amount <= 0f) return
        val affected = (pixels.size * amount).toInt().coerceAtMost(pixels.size)
        repeat(affected) {
            val index = random.nextInt(pixels.size)
            val color = pixels[index]
            val delta = random.nextInt(-48, 49)
            pixels[index] = Color.argb(
                Color.alpha(color),
                clamp(Color.red(color) + delta),
                clamp(Color.green(color) - delta / 2),
                clamp(Color.blue(color) + delta / 3),
            )
        }
    }

    private fun applyScanlines(
        pixels: IntArray,
        width: Int,
        height: Int,
        amount: Float,
    ) {
        if (amount <= 0f) return
        val multiplier = 1f - amount.coerceIn(0f, 0.72f)
        for (y in 1 until height step 3) {
            val row = y * width
            for (x in 0 until width) {
                val color = pixels[row + x]
                pixels[row + x] = Color.argb(
                    Color.alpha(color),
                    (Color.red(color) * multiplier).toInt(),
                    (Color.green(color) * multiplier).toInt(),
                    (Color.blue(color) * multiplier).toInt(),
                )
            }
        }
    }

    private fun wrap(value: Int, size: Int): Int {
        val remainder = value % size
        return if (remainder < 0) remainder + size else remainder
    }

    private fun clamp(value: Int): Int = min(255, max(0, value))
}
