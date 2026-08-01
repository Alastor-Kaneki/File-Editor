package com.alastorkaneki.fileeditor

import com.alastorkaneki.fileeditor.data.SearchMatcher
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchMatcherTest {
    private val fields = listOf(
        "Night Drive.mp3",
        "Midnight Run",
        "Alastor",
        "Neon Ruins",
        "Music/Soundtracks/",
        "audio/mpeg",
    )

    @Test
    fun matchesMultipleFieldsAndTokens() {
        assertTrue(SearchMatcher.matches("alastor ruins", fields))
        assertTrue(SearchMatcher.matches("night mp3", fields))
        assertTrue(SearchMatcher.matches("soundtracks", fields))
    }

    @Test
    fun rejectsMissingTokens() {
        assertFalse(SearchMatcher.matches("alastor acoustic", fields))
    }
}
