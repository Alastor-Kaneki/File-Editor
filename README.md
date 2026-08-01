# File Editor

A native Android media studio that scans shared storage for audio, photos and videos, edits metadata, creates glitched images with realtime previews, builds GIFs, and exports edited media entirely on-device.

## Features

### Audio

- Scans every MediaStore audio volume Android exposes, including internal shared storage, SD cards and attached media volumes.
- Searches by title, artist, album, filename, folder and MIME type.
- Edits title, artist, album, album artist, genre, year, track, disc, composer, publisher/label, ISRC, BPM, comments and lyrics.
- Replaces or removes embedded cover artwork.
- Applies trim, independent speed and pitch, sample-rate conversion, and presets including Nightcore, Vaporwave, Telephone and Deep Voice.
- Renders an exact 15-second preview through the same Media3 processing pipeline used for final AAC/M4A export.

### Photos and videos

- Scans and searches shared photo and video libraries.
- Edits embedded EXIF fields in JPEG, PNG and WebP images: description, creator, copyright, comments, capture time, camera make/model and software.
- Includes a Photo Studio with live color, light, crop, rotation, texture, blur, sharpening and format-export controls.
- Renames videos and edits MediaStore tags, category and language.
- Includes a Video Studio with trim, rotation, mirroring, muting, resizing, frame-rate conversion, HSL/RGB grading, grayscale, inversion and MP4 export.
- Uses Android scoped-storage write consent before modifying existing media.

### Glitch Lab

- Realtime preview while changing effect settings.
- VHS, datamosh, cyberpunk, broken-LCD and pixel-melt presets.
- RGB channel splitting, horizontal and vertical displacement, corrupted blocks, pixel sorting, smear, ghosting, digital noise, posterization, mosaic and scanlines.
- Deterministic seed controls with one-tap randomization.
- Exports PNG, JPEG or WebP copies to `Pictures/FileEditor`.

### GIF Maker

- Builds GIF89a animations from multiple images.
- Extracts frames from videos to create GIFs.
- Optional per-frame advanced glitch rendering for animated glitch GIFs.
- Controls video trim, reverse, ping-pong, loop count, fit/crop layout, frame delay, frame count and output resolution.
- Saves finished GIFs to `Pictures/FileEditor`.

The GIF pipeline uses Square's pure Java GIF encoder, which is designed for Android. Image effects use an on-device bitmap pipeline instead of bundling the full native ImageMagick distribution, avoiding large ABI-specific native libraries while keeping the preview and conversion workflow offline.

## Persistent signing

Version 1.3.0 and later use the repository's stable development signing certificate. APKs built from this version onward can update one another instead of receiving a new certificate on every GitHub Actions runner.

Builds older than 1.3.0 used runner-generated debug keys. Android cannot update an installed app when its signing certificate changes, so moving from an older build requires uninstalling it once. After installing 1.3.0, later builds should install as normal updates.

Because this repository is public, the included key is intentionally a public development and sideloading key. It is not suitable as a private production or app-store signing key. See `signing/README.md`.

## Android storage behavior

"Whole device" means all media exposed through Android's shared MediaStore collections. Android intentionally blocks third-party apps from scanning other apps' private sandboxes and protected parts of `Android/data`. File Editor does not request the highly restricted `MANAGE_EXTERNAL_STORAGE` permission.

On Android 13 and newer, the app requests `READ_MEDIA_AUDIO`, `READ_MEDIA_IMAGES` and `READ_MEDIA_VIDEO`. On Android 12 and older, it requests the corresponding legacy shared-storage permission. Glitch Lab and GIF Maker also support Android's system picker, so individual files can be chosen without full-library permission.

Exports are written to:

- `Pictures/FileEditor`
- `Movies/FileEditor`
- `Music/FileEditor`

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

- Kotlin and Jetpack Compose
- MediaStore for discovery and gallery output
- AndroidX Media3 Transformer and ExoPlayer for audio/video processing and preview playback
- AndroidX ExifInterface for photo metadata
- jaudiotagger-android for audio metadata
- Square GIF Encoder for GIF89a output
- Android Bitmap, ImageDecoder and MediaMetadataRetriever for image effects and frame extraction

## Notes

Metadata and codec support varies by file format, storage provider and device. Photo EXIF writes are supported for JPEG, PNG and WebP. DRM-protected, cloud-placeholder, read-only or system-owned files may reject modifications or exports.
