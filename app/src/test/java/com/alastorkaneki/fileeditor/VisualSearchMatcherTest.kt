package com.alastorkaneki.fileeditor

import com.alastorkaneki.fileeditor.data.VisualSearchMatcher
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualSearchMatcherTest {
    private val fields = listOf(
        "Purple Glitch.png",
        "OC Portrait",
        "image/png",
        "Pictures/FileEditor/",
        "1080x1920",
    )

    @Test
    fun allTokensMayMatchDifferentFields() {
        assertTrue(VisualSearchMatcher.matches("portrait fileeditor png", fields))
    }

    @Test
    fun dimensionsAreSearchable() {
        assertTrue(VisualSearchMatcher.matches("1080x1920", fields))
    }

    @Test
    fun missingTokenRejectsItem() {
        assertFalse(VisualSearchMatcher.matches("purple video", fields))
    }
}
