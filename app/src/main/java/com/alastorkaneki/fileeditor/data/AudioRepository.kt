package com.alastorkaneki.fileeditor.data

import android.app.PendingIntent
import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.Tag
import org.jaudiotagger.tag.images.ArtworkFactory
import java.io.File
import java.io.FileOutputStream

class WritePermissionRequired(val pendingIntent: PendingIntent) : Exception()

class AudioRepository(private val context: Context) {
    private val resolver = context.contentResolver

    fun scanAudio(): List<AudioTrack> {
        val result = linkedMapOf<String, AudioTrack>()
        collections().forEach { (volume, collection) ->
            runCatching { queryCollection(volume, collection) }
                .getOrDefault(emptyList())
                .forEach { result[it.uri.toString()] = it }
        }
        return result.values.sortedWith(
            compareBy<AudioTrack> { it.title.ifBlank { it.displayName }.lowercase() }
                .thenBy { it.artist.lowercase() },
        )
    }

    fun readTags(track: AudioTrack): TagData {
        val temp = copyToTemp(track)
        return try {
            val audio = AudioFileIO.read(temp)
            val tag = audio.tag
            val artwork = runCatching { tag?.firstArtwork }.getOrNull()

            TagData(
                title = tag.firstOr(track.title, FieldKey.TITLE),
                artist = tag.firstOr(track.artist, FieldKey.ARTIST),
                album = tag.firstOr(track.album, FieldKey.ALBUM),
                albumArtist = tag.firstOr("", FieldKey.ALBUM_ARTIST),
                genre = tag.firstOr("", FieldKey.GENRE),
                year = tag.firstOr("", FieldKey.YEAR),
                trackNumber = tag.firstOr("", FieldKey.TRACK),
                discNumber = tag.firstOr("", FieldKey.DISC_NO),
                composer = tag.firstOr("", FieldKey.COMPOSER),
                comment = tag.firstOr("", FieldKey.COMMENT),
                lyrics = tag.firstOr("", FieldKey.LYRICS),
                publisher = tag.firstOr("", FieldKey.RECORD_LABEL),
                bpm = tag.firstOr("", FieldKey.BPM),
                isrc = tag.firstOr("", FieldKey.ISRC),
                artworkBytes = runCatching { artwork?.binaryData }.getOrNull(),
                artworkMimeType = runCatching { artwork?.mimeType }.getOrNull(),
                artworkDescription = runCatching { artwork?.description }.getOrNull().orEmpty().ifBlank { "Cover" },
            )
        } finally {
            temp.delete()
        }
    }

    fun createWriteRequest(uri: Uri): PendingIntent {
        require(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
        return MediaStore.createWriteRequest(resolver, listOf(uri))
    }

    fun saveTags(track: AudioTrack, draft: EditorDraft): SaveResult {
        val tempAudio = copyToTemp(track)
        val tempArtwork = if (draft.artworkChanged && draft.fields.artworkBytes != null) {
            writeArtworkTemp(draft.fields)
        } else {
            null
        }

        try {
            val audio = AudioFileIO.read(tempAudio)
            val tag = audio.tagOrCreateAndSetDefault
            val warnings = mutableListOf<String>()

            tag.setSafely(FieldKey.TITLE, draft.fields.title, warnings)
            tag.setSafely(FieldKey.ARTIST, draft.fields.artist, warnings)
            tag.setSafely(FieldKey.ALBUM, draft.fields.album, warnings)
            tag.setSafely(FieldKey.ALBUM_ARTIST, draft.fields.albumArtist, warnings)
            tag.setSafely(FieldKey.GENRE, draft.fields.genre, warnings)
            tag.setSafely(FieldKey.YEAR, draft.fields.year, warnings)
            tag.setSafely(FieldKey.TRACK, draft.fields.trackNumber, warnings)
            tag.setSafely(FieldKey.DISC_NO, draft.fields.discNumber, warnings)
            tag.setSafely(FieldKey.COMPOSER, draft.fields.composer, warnings)
            tag.setSafely(FieldKey.COMMENT, draft.fields.comment, warnings)
            tag.setSafely(FieldKey.LYRICS, draft.fields.lyrics, warnings)
            tag.setSafely(FieldKey.RECORD_LABEL, draft.fields.publisher, warnings)
            tag.setSafely(FieldKey.BPM, draft.fields.bpm, warnings)
            tag.setSafely(FieldKey.ISRC, draft.fields.isrc, warnings)

            if (draft.artworkChanged) {
                runCatching {
                    tag.deleteArtworkField()
                    if (tempArtwork != null) {
                        val artwork = ArtworkFactory.createArtworkFromFile(tempArtwork)
                        artwork.description = draft.fields.artworkDescription.ifBlank { "Cover" }
                        tag.setField(artwork)
                    }
                }.onFailure { warnings += "Artwork: ${it.message ?: it::class.java.simpleName}" }
            }

            audio.commit()
            overwriteUri(track.uri, tempAudio)
            updateMediaStoreColumns(track.uri, draft.fields)
            refreshMediaStore(track)
            return SaveResult(warnings)
        } catch (security: RecoverableSecurityException) {
            throw WritePermissionRequired(security.userAction.actionIntent)
        } finally {
            tempAudio.delete()
            tempArtwork?.delete()
        }
    }

    private fun queryCollection(volume: String, collection: Uri): List<AudioTrack> {
        val projection = buildList {
            add(MediaStore.Audio.Media._ID)
            add(MediaStore.Audio.Media.DISPLAY_NAME)
            add(MediaStore.Audio.Media.TITLE)
            add(MediaStore.Audio.Media.ARTIST)
            add(MediaStore.Audio.Media.ALBUM)
            add(MediaStore.Audio.Media.DURATION)
            add(MediaStore.Audio.Media.SIZE)
            add(MediaStore.Audio.Media.MIME_TYPE)
            add(MediaStore.Audio.Media.DATE_MODIFIED)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                add(MediaStore.MediaColumns.DATA)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.MediaColumns.RELATIVE_PATH)
            }
        }.toTypedArray()

        val selection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "${MediaStore.MediaColumns.IS_PENDING}=0"
        } else {
            null
        }

        val tracks = mutableListOf<AudioTrack>()
        resolver.query(
            collection,
            projection,
            selection,
            null,
            "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC",
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val nameIndex = cursor.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME)
            val titleIndex = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE)
            val artistIndex = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST)
            val albumIndex = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM)
            val durationIndex = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION)
            val sizeIndex = cursor.getColumnIndex(MediaStore.Audio.Media.SIZE)
            val mimeIndex = cursor.getColumnIndex(MediaStore.Audio.Media.MIME_TYPE)
            val dataIndex = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
            } else {
                -1
            }
            val relativeIndex = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
            } else {
                -1
            }

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idIndex)
                tracks += AudioTrack(
                    id = id,
                    uri = ContentUris.withAppendedId(collection, id),
                    displayName = cursor.stringAt(nameIndex).ifBlank { "audio-$id" },
                    title = cursor.stringAt(titleIndex),
                    artist = cursor.stringAt(artistIndex).replace("<unknown>", ""),
                    album = cursor.stringAt(albumIndex).replace("<unknown>", ""),
                    durationMs = cursor.longAt(durationIndex),
                    sizeBytes = cursor.longAt(sizeIndex),
                    mimeType = cursor.stringAt(mimeIndex).ifBlank { "audio/*" },
                    relativePath = cursor.stringAt(relativeIndex),
                    absolutePath = cursor.stringAt(dataIndex).takeIf(String::isNotBlank),
                    volumeName = volume,
                )
            }
        }
        return tracks
    }

    private fun collections(): List<Pair<String, Uri>> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            buildSet {
                addAll(MediaStore.getExternalVolumeNames(context))
                add(MediaStore.VOLUME_INTERNAL)
            }.map { it to MediaStore.Audio.Media.getContentUri(it) }
        } else {
            listOf(
                "external" to MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                "internal" to MediaStore.Audio.Media.INTERNAL_CONTENT_URI,
            )
        }
    }

    private fun copyToTemp(track: AudioTrack): File {
        val extension = track.displayName.substringAfterLast('.', "audio")
            .lowercase()
            .replace(Regex("[^a-z0-9]"), "")
            .ifBlank { "audio" }
        val temp = File.createTempFile("file_editor_", ".$extension", context.cacheDir)
        resolver.openInputStream(track.uri)?.use { input ->
            FileOutputStream(temp).use(input::copyTo)
        } ?: error("Unable to open ${track.displayName}")
        return temp
    }

    private fun writeArtworkTemp(fields: TagData): File {
        val extension = when (fields.artworkMimeType?.lowercase()) {
            "image/png" -> ".png"
            "image/webp" -> ".webp"
            else -> ".jpg"
        }
        return File.createTempFile("file_editor_cover_", extension, context.cacheDir).apply {
            writeBytes(requireNotNull(fields.artworkBytes))
        }
    }

    private fun overwriteUri(uri: Uri, source: File) {
        val output = runCatching { resolver.openOutputStream(uri, "rwt") }.getOrNull()
            ?: resolver.openOutputStream(uri, "w")
            ?: error("Unable to open the audio file for writing")
        output.use { destination -> source.inputStream().use { it.copyTo(destination) } }
    }

    private fun updateMediaStoreColumns(uri: Uri, fields: TagData) {
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.TITLE, fields.title)
            put(MediaStore.Audio.Media.ARTIST, fields.artist)
            put(MediaStore.Audio.Media.ALBUM, fields.album)
        }
        runCatching { resolver.update(uri, values, null, null) }
        resolver.notifyChange(uri, null)
    }

    private fun refreshMediaStore(track: AudioTrack) {
        val path = track.absolutePath ?: return
        MediaScannerConnection.scanFile(context, arrayOf(path), arrayOf(track.mimeType), null)
    }

    private fun Tag?.firstOr(fallback: String, key: FieldKey): String {
        val value = runCatching { this?.getFirst(key) }.getOrNull().orEmpty()
        return value.ifBlank { fallback }
    }

    private fun Tag.setSafely(key: FieldKey, value: String, warnings: MutableList<String>) {
        runCatching {
            if (value.isBlank()) deleteField(key) else setField(key, value.trim())
        }.onFailure { warnings += "${key.name}: ${it.message ?: it::class.java.simpleName}" }
    }

    private fun android.database.Cursor.stringAt(index: Int): String =
        if (index >= 0 && !isNull(index)) getString(index).orEmpty() else ""

    private fun android.database.Cursor.longAt(index: Int): Long =
        if (index >= 0 && !isNull(index)) getLong(index) else 0L
}
