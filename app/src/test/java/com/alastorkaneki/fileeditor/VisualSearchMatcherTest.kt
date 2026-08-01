package com.alastorkaneki.fileeditor

import android.net.Uri
import com.alastorkaneki.fileeditor.data.VisualMediaItem
import com.alastorkaneki.fileeditor.data.VisualMediaKind
import com.alastorkaneki.fileeditor.data.VisualSearchMatcher
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualSearchMatcherTest {
    private val item = VisualMediaItem(
        id = 7L,
        uri = Uri.parse("content://media/7"),
        kind = VisualMediaKind.IMAGE,
        displayName = "Purple Glitch.png",
        title = "OC Portrait",
        mimeType = "image/png",
        relativePath = "Pictures/FileEditor/",
        width = 1080,
        height = 1920,
        durationMs = 0L,
        sizeBytes = 42L,
        dateModifiedSeconds = 1L,
        volumeName = "external_primary",
    )

    @Test
    fun allTokensMayMatchDifferentFields() {
        assertTrue(VisualSearchMatcher.matches(item, "portrait fileeditor png"))
    }

    @Test
    fun dimensionsAreSearchable() {
        assertTrue(VisualSearchMatcher.matches(item, "1080x1920"))
    }

    @Test
    fun missingTokenRejectsItem() {
        assertFalse(VisualSearchMatcher.matches(item, "purple video"))
    }
}
