package com.alastorkaneki.fileeditor.media

import org.junit.Assert.assertEquals
import org.junit.Test

class GifPixelLayoutTest {
    @Test
    fun portraitFramesUseHeightByWidthLayout() {
        val width = 3
        val height = 5
        val rows = Array(height) { y -> IntArray(width) { x -> y * width + x } }

        assertEquals(height, rows.size)
        rows.forEach { row -> assertEquals(width, row.size) }
        assertEquals(14, rows[4][2])
    }
}
