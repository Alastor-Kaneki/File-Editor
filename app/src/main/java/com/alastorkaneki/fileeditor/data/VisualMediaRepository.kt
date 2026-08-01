package com.alastorkaneki.fileeditor.data

import android.app.PendingIntent
import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Size
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream

class VisualWritePermissionRequired(val pendingIntent: PendingIntent) : Exception()

class VisualMediaRepository(private val context: Context) {
    private val resolver = context.contentResolver

    fun scan(kind: VisualMediaKind): List<VisualMediaItem> {
        val result = linkedMapOf<String, VisualMediaItem>()
        collections(kind).forEach { (volume, collection) ->
            runCatching { queryCollection(kind, volume, collection) }
                .getOrDefault(emptyList())
                .forEach { result[it.uri.toString()] = it }
        }
        return result.values.sortedWith(
            compareByDescending<VisualMediaItem> { it.dateModifiedSeconds }
                .thenBy { it.displayName.lowercase() },
        )
    }

    fun loadThumbnail(item: VisualMediaItem, sizePx: Int = 384): Bitmap =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            resolver.loadThumbnail(item.uri, Size(sizePx, sizePx), null)
        } else {
            loadBitmap(item.uri, sizePx)
        }

    fun loadBitmap(uri: Uri, maxDimension: Int = 1600): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            ?: error("Unable to open image")

        var sample = 1
        while (bounds.outWidth / sample > maxDimension || bounds.outHeight / sample > maxDimension) {
            sample *= 2
        }

        val options = BitmapFactory.Options().apply {
            inSampleSize = sample.coerceAtLeast(1)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: error("Unable to decode image")

        return rotateForExif(uri, decoded)
    }

    fun readImageTags(item: VisualMediaItem): ImageTagData {
        val temp = copyToTemp(item)
        return try {
            val exif = ExifInterface(temp)
            ImageTagData(
                description = exif.getAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION).orEmpty(),
                artist = exif.getAttribute(ExifInterface.TAG_ARTIST).orEmpty(),
                copyright = exif.getAttribute(ExifInterface.TAG_COPYRIGHT).orEmpty(),
                userComment = exif.getAttribute(ExifInterface.TAG_USER_COMMENT).orEmpty(),
                dateTimeOriginal = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL).orEmpty(),
                make = exif.getAttribute(ExifInterface.TAG_MAKE).orEmpty(),
                model = exif.getAttribute(ExifInterface.TAG_MODEL).orEmpty(),
                software = exif.getAttribute(ExifInterface.TAG_SOFTWARE).orEmpty(),
            )
        } finally {
            temp.delete()
        }
    }

    fun saveImageTags(item: VisualMediaItem, tags: ImageTagData) {
        val temp = copyToTemp(item)
        try {
            val exif = ExifInterface(temp)
            exif.setOrRemove(ExifInterface.TAG_IMAGE_DESCRIPTION, tags.description)
            exif.setOrRemove(ExifInterface.TAG_ARTIST, tags.artist)
            exif.setOrRemove(ExifInterface.TAG_COPYRIGHT, tags.copyright)
            exif.setOrRemove(ExifInterface.TAG_USER_COMMENT, tags.userComment)
            exif.setOrRemove(ExifInterface.TAG_DATETIME_ORIGINAL, tags.dateTimeOriginal)
            exif.setOrRemove(ExifInterface.TAG_MAKE, tags.make)
            exif.setOrRemove(ExifInterface.TAG_MODEL, tags.model)
            exif.setOrRemove(ExifInterface.TAG_SOFTWARE, tags.software)
            exif.saveAttributes()
            overwriteUri(item.uri, temp)
            resolver.notifyChange(item.uri, null)
        } catch (security: RecoverableSecurityException) {
            throw VisualWritePermissionRequired(security.userAction.actionIntent)
        } finally {
            temp.delete()
        }
    }

    fun readVideoTags(item: VisualMediaItem): VideoTagData {
        val projection = arrayOf(
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.Video.VideoColumns.TAGS,
            MediaStore.Video.VideoColumns.CATEGORY,
            MediaStore.Video.VideoColumns.LANGUAGE,
        )
        resolver.query(item.uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                return VideoTagData(
                    displayName = cursor.stringAt(cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME))
                        .ifBlank { item.displayName },
                    tags = cursor.stringAt(cursor.getColumnIndex(MediaStore.Video.VideoColumns.TAGS)),
                    category = cursor.stringAt(cursor.getColumnIndex(MediaStore.Video.VideoColumns.CATEGORY)),
                    language = cursor.stringAt(cursor.getColumnIndex(MediaStore.Video.VideoColumns.LANGUAGE)),
                )
            }
        }
        return VideoTagData(displayName = item.displayName)
    }

    fun saveVideoTags(item: VisualMediaItem, tags: VideoTagData): List<String> {
        val warnings = mutableListOf<String>()
        val extension = item.displayName.substringAfterLast('.', "")
        val requestedName = tags.displayName.trim().ifBlank { item.displayName }
        val finalName = when {
            extension.isBlank() -> requestedName
            requestedName.endsWith(".$extension", ignoreCase = true) -> requestedName
            requestedName.contains('.') -> requestedName
            else -> "$requestedName.$extension"
        }

        try {
            updateColumn(item.uri, MediaStore.MediaColumns.DISPLAY_NAME, finalName, "File name", warnings)
            updateColumn(item.uri, MediaStore.Video.VideoColumns.TAGS, tags.tags, "Tags", warnings)
            updateColumn(item.uri, MediaStore.Video.VideoColumns.CATEGORY, tags.category, "Category", warnings)
            updateColumn(item.uri, MediaStore.Video.VideoColumns.LANGUAGE, tags.language, "Language", warnings)
            resolver.notifyChange(item.uri, null)
        } catch (security: RecoverableSecurityException) {
            throw VisualWritePermissionRequired(security.userAction.actionIntent)
        }
        return warnings
    }

    fun createWriteRequest(uri: Uri): PendingIntent {
        require(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
        return MediaStore.createWriteRequest(resolver, listOf(uri))
    }

    fun saveBitmapToGallery(
        bitmap: Bitmap,
        displayName: String,
        format: Bitmap.CompressFormat,
        quality: Int = 95,
    ): Uri {
        val mime = when (format) {
            Bitmap.CompressFormat.JPEG -> "image/jpeg"
            Bitmap.CompressFormat.WEBP,
            Bitmap.CompressFormat.WEBP_LOSSLESS,
            Bitmap.CompressFormat.WEBP_LOSSY,
            -> "image/webp"
            else -> "image/png"
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Images.Media.MIME_TYPE, mime)
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/FileEditor")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                values,
            ) ?: error("Unable to create output image")
            try {
                resolver.openOutputStream(uri, "w")?.use { output ->
                    check(bitmap.compress(format, quality, output)) { "Image encoding failed" }
                } ?: error("Unable to open output image")
                resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
                return uri
            } catch (error: Throwable) {
                resolver.delete(uri, null, null)
                throw error
            }
        }

        @Suppress("DEPRECATION")
        val directory = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            "FileEditor",
        ).apply { mkdirs() }
        val file = File(directory, displayName)
        FileOutputStream(file).use { output ->
            check(bitmap.compress(format, quality, output)) { "Image encoding failed" }
        }
        MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf(mime), null)
        return Uri.fromFile(file)
    }

    fun saveGifToGallery(source: File, displayName: String): Uri {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/gif")
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/FileEditor")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                values,
            ) ?: error("Unable to create output GIF")
            try {
                resolver.openOutputStream(uri, "w")?.use { output ->
                    source.inputStream().use { it.copyTo(output) }
                } ?: error("Unable to open output GIF")
                resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
                return uri
            } catch (error: Throwable) {
                resolver.delete(uri, null, null)
                throw error
            }
        }

        @Suppress("DEPRECATION")
        val directory = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            "FileEditor",
        ).apply { mkdirs() }
        val file = File(directory, displayName)
        source.copyTo(file, overwrite = true)
        MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf("image/gif"), null)
        return Uri.fromFile(file)
    }

    private fun queryCollection(
        kind: VisualMediaKind,
        volume: String,
        collection: Uri,
    ): List<VisualMediaItem> {
        val projection = buildList {
            add(MediaStore.MediaColumns._ID)
            add(MediaStore.MediaColumns.DISPLAY_NAME)
            add(MediaStore.MediaColumns.TITLE)
            add(MediaStore.MediaColumns.MIME_TYPE)
            add(MediaStore.MediaColumns.SIZE)
            add(MediaStore.MediaColumns.DATE_MODIFIED)
            add(MediaStore.MediaColumns.WIDTH)
            add(MediaStore.MediaColumns.HEIGHT)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) add(MediaStore.MediaColumns.RELATIVE_PATH)
            if (kind == VisualMediaKind.VIDEO) add(MediaStore.Video.Media.DURATION)
        }.toTypedArray()

        val selection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "${MediaStore.MediaColumns.IS_PENDING}=0"
        } else {
            null
        }

        val items = mutableListOf<VisualMediaItem>()
        resolver.query(
            collection,
            projection,
            selection,
            null,
            "${MediaStore.MediaColumns.DATE_MODIFIED} DESC",
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
            val titleIndex = cursor.getColumnIndex(MediaStore.MediaColumns.TITLE)
            val mimeIndex = cursor.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
            val sizeIndex = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
            val modifiedIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
            val widthIndex = cursor.getColumnIndex(MediaStore.MediaColumns.WIDTH)
            val heightIndex = cursor.getColumnIndex(MediaStore.MediaColumns.HEIGHT)
            val relativeIndex = cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
            val durationIndex = if (kind == VisualMediaKind.VIDEO) {
                cursor.getColumnIndex(MediaStore.Video.Media.DURATION)
            } else {
                -1
            }

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idIndex)
                items += VisualMediaItem(
                    id = id,
                    uri = ContentUris.withAppendedId(collection, id),
                    kind = kind,
                    displayName = cursor.stringAt(nameIndex).ifBlank { "media-$id" },
                    title = cursor.stringAt(titleIndex),
                    mimeType = cursor.stringAt(mimeIndex).ifBlank {
                        if (kind == VisualMediaKind.IMAGE) "image/*" else "video/*"
                    },
                    relativePath = cursor.stringAt(relativeIndex),
                    width = cursor.intAt(widthIndex),
                    height = cursor.intAt(heightIndex),
                    durationMs = cursor.longAt(durationIndex),
                    sizeBytes = cursor.longAt(sizeIndex),
                    dateModifiedSeconds = cursor.longAt(modifiedIndex),
                    volumeName = volume,
                )
            }
        }
        return items
    }

    private fun collections(kind: VisualMediaKind): List<Pair<String, Uri>> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            buildSet {
                addAll(MediaStore.getExternalVolumeNames(context))
                add(MediaStore.VOLUME_INTERNAL)
            }.map { volume ->
                volume to when (kind) {
                    VisualMediaKind.IMAGE -> MediaStore.Images.Media.getContentUri(volume)
                    VisualMediaKind.VIDEO -> MediaStore.Video.Media.getContentUri(volume)
                }
            }
        } else {
            when (kind) {
                VisualMediaKind.IMAGE -> listOf(
                    "external" to MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    "internal" to MediaStore.Images.Media.INTERNAL_CONTENT_URI,
                )
                VisualMediaKind.VIDEO -> listOf(
                    "external" to MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    "internal" to MediaStore.Video.Media.INTERNAL_CONTENT_URI,
                )
            }
        }
    }

    private fun copyToTemp(item: VisualMediaItem): File {
        val extension = item.displayName.substringAfterLast('.', "media")
            .lowercase()
            .replace(Regex("[^a-z0-9]"), "")
            .ifBlank { "media" }
        val temp = File.createTempFile("file_editor_visual_", ".$extension", context.cacheDir)
        resolver.openInputStream(item.uri)?.use { input ->
            FileOutputStream(temp).use(input::copyTo)
        } ?: error("Unable to open ${item.displayName}")
        return temp
    }

    private fun overwriteUri(uri: Uri, source: File) {
        val output = runCatching { resolver.openOutputStream(uri, "rwt") }.getOrNull()
            ?: resolver.openOutputStream(uri, "w")
            ?: error("Unable to open media for writing")
        output.use { destination -> source.inputStream().use { it.copyTo(destination) } }
    }

    private fun rotateForExif(uri: Uri, bitmap: Bitmap): Bitmap {
        val orientation = runCatching {
            resolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                ExifInterface(descriptor.fileDescriptor).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true).also {
            if (it !== bitmap) bitmap.recycle()
        }
    }

    private fun ExifInterface.setOrRemove(tag: String, value: String) {
        setAttribute(tag, value.trim().takeIf(String::isNotBlank))
    }

    private fun updateColumn(
        uri: Uri,
        column: String,
        value: String,
        label: String,
        warnings: MutableList<String>,
    ) {
        runCatching {
            resolver.update(uri, ContentValues().apply { put(column, value.trim()) }, null, null)
        }.onFailure { warnings += "$label: ${it.message ?: it::class.java.simpleName}" }
    }

    private fun android.database.Cursor.stringAt(index: Int): String =
        if (index >= 0 && !isNull(index)) getString(index).orEmpty() else ""

    private fun android.database.Cursor.longAt(index: Int): Long =
        if (index >= 0 && !isNull(index)) getLong(index) else 0L

    private fun android.database.Cursor.intAt(index: Int): Int =
        if (index >= 0 && !isNull(index)) getInt(index) else 0
}
