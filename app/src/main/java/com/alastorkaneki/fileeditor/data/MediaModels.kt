package com.alastorkaneki.fileeditor.data

import android.net.Uri

enum class VisualMediaKind {
    IMAGE,
    VIDEO,
}

data class VisualMediaItem(
    val id: Long,
    val uri: Uri,
    val kind: VisualMediaKind,
    val displayName: String,
    val title: String,
    val mimeType: String,
    val relativePath: String,
    val width: Int,
    val height: Int,
    val durationMs: Long,
    val sizeBytes: Long,
    val dateModifiedSeconds: Long,
    val volumeName: String,
)

data class ImageTagData(
    val description: String = "",
    val artist: String = "",
    val copyright: String = "",
    val userComment: String = "",
    val dateTimeOriginal: String = "",
    val make: String = "",
    val model: String = "",
    val software: String = "",
)

data class VideoTagData(
    val displayName: String = "",
    val tags: String = "",
    val category: String = "",
    val language: String = "",
)

object VisualSearchMatcher {
    fun matches(item: VisualMediaItem, rawQuery: String): Boolean = matches(
        rawQuery = rawQuery,
        fields = listOf(
            item.displayName,
            item.title,
            item.mimeType,
            item.relativePath,
            "${item.width}x${item.height}",
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
