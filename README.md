# File Editor

A native Android app that scans the device's shared media library for audio files, provides fast search, and edits embedded audio metadata.

## Features

- Scans every MediaStore audio volume Android exposes, including internal shared storage, SD cards, and attached media volumes.
- Searches by title, artist, album, filename, folder, and MIME type.
- Edits common tags: title, artist, album, album artist, genre, year, track, disc, composer, publisher/label, ISRC, BPM, copyright, comment, and lyrics.
- Replaces or removes embedded cover artwork.
- Handles Android scoped-storage write consent with `MediaStore.createWriteRequest()`.
- Supports the formats handled by the Android jaudiotagger fork, including MP3 and several common tagged audio containers.
- Uses a temporary working copy so tag editing works with `content://` MediaStore URIs.

## Android storage behavior

"Whole device" means all audio in Android's shared media collections. Android intentionally blocks third-party apps from scanning other apps' private sandboxes and parts of `Android/data`. File Editor does not request the highly restricted `MANAGE_EXTERNAL_STORAGE` permission.

On Android 13 and newer, the app requests `READ_MEDIA_AUDIO`. On Android 12 and older, it requests the corresponding legacy storage permission. Android 11 and newer show a system confirmation before an existing media file is modified.

## Build

Open the project in a current Android Studio release with Android SDK 36 installed, or run:

```bash
gradle assembleDebug
```

Requirements:

- JDK 17
- Gradle 8.13
- Android SDK 36

## Architecture

- Kotlin + Jetpack Compose
- MediaStore for discovery
- jaudiotagger-android for embedded tag read/write
- A single activity and lifecycle-aware ViewModel

## Notes

Tag support varies by container. If a format does not support a requested field, File Editor saves the supported fields and reports how many were skipped. Editing DRM-protected, cloud-placeholder, read-only, or system-owned audio may be blocked by the provider.
