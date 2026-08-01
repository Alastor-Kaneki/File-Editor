package com.alastorkaneki.fileeditor.data

import android.net.Uri

data class AudioTrack(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val mimeType: String,
    val relativePath: String,
    val absolutePath: String?,
    val volumeName: String,
)

data class TagData(
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val albumArtist: String = "",
    val genre: String = "",
    val year: String = "",
    val trackNumber: String = "",
    val discNumber: String = "",
    val composer: String = "",
    val comment: String = "",
    val lyrics: String = "",
    val publisher: String = "",
    val bpm: String = "",
    val isrc: String = "",
    val artworkBytes: ByteArray? = null,
    val artworkMimeType: String? = null,
    val artworkDescription: String = "Cover",
)

data class EditorDraft(
    val fields: TagData,
    val artworkChanged: Boolean = false,
)

data class SaveResult(
    val warnings: List<String> = emptyList(),
)

object SearchMatcher {
    fun matches(track: AudioTrack, rawQuery: String): Boolean = matches(
        rawQuery = rawQuery,
        fields = listOf(
            track.displayName,
            track.title,
            track.artist,
            track.album,
            track.relativePath,
            track.mimeType,
        ),
    )

    fun matches(rawQuery: String, fields: Iterable<String>): Boolean {
        val tokens = rawQuery
            .trim()
            .lowercase()
            .split(Regex("\\s+"))
            .filter(String::isNotBlank)

        if (tokens.isEmpty()) return true
        val searchable = fields.joinToString(" ").lowercase()
        return tokens.all(searchable::contains)
    }
}
