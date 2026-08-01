# File Editor

A native Android media utility that scans shared storage for audio, photos and videos, edits metadata, creates glitched images with realtime previews, and builds GIFs from images or video.

## Features

### Audio

- Scans every MediaStore audio volume Android exposes, including internal shared storage, SD cards and attached media volumes.
- Searches by title, artist, album, filename, folder and MIME type.
- Edits title, artist, album, album artist, genre, year, track, disc, composer, publisher/label, ISRC, BPM, comments and lyrics.
- Replaces or removes embedded cover artwork.

### Photos and videos

- Scans and searches shared photo and video libraries.
- Edits embedded EXIF fields in JPEG, PNG and WebP images: description, creator, copyright, comments, capture time, camera make/model and software.
- Renames videos and edits MediaStore tags, category and language.
- Uses Android scoped-storage write consent before modifying existing media.

### Glitch Lab

- Realtime preview while changing effect settings.
- RGB channel splitting, horizontal displacement slices, corrupted blocks, digital noise and scanlines.
- Deterministic seed controls with one-tap randomization.
- Exports PNG or JPEG copies to `Pictures/FileEditor`.

### GIF Maker

- Builds GIF89a animations from multiple images.
- Extracts frames from videos to create GIFs.
- Optional per-frame glitch rendering for animated glitch GIFs.
- Controls frame delay, frame count and output resolution.
- Saves finished GIFs to `Pictures/FileEditor`.

The GIF pipeline uses Square's pure Java GIF encoder, which is designed for Android. Image effects use an on-device bitmap pipeline instead of bundling the full native ImageMagick distribution, avoiding large ABI-specific native libraries while keeping the requested preview and conversion workflow entirely offline.

## Android storage behavior

"Whole device" means all media exposed through Android's shared MediaStore collections. Android intentionally blocks third-party apps from scanning other apps' private sandboxes and protected parts of `Android/data`. File Editor does not request the highly restricted `MANAGE_EXTERNAL_STORAGE` permission.

On Android 13 and newer, the app requests `READ_MEDIA_AUDIO`, `READ_MEDIA_IMAGES` and `READ_MEDIA_VIDEO`. On Android 12 and older, it requests the corresponding legacy shared-storage permission. Glitch Lab and GIF Maker also support Android's system picker, so individual files can be chosen without full-library permission.

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
- MediaStore for discovery and gallery output
- AndroidX ExifInterface for photo metadata
- jaudiotagger-android for audio metadata
- Square GIF Encoder for GIF89a output
- Android Bitmap and MediaMetadataRetriever for effects and video-frame extraction

## Notes

Metadata support varies by file format and storage provider. Photo EXIF writes are supported for JPEG, PNG and WebP. Video codec, duration and resolution fields are read-only; editable catalog fields are saved through MediaStore. DRM-protected, cloud-placeholder, read-only or system-owned files may reject modifications.
