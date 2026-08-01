package com.alastorkaneki.fileeditor.media

/**
 * Describes one FFmpeg audio output target. Presets cover common formats and the
 * custom target lets advanced users request any muxer/encoder bundled with FFmpeg.
 */
data class AudioExportTarget(
    val id: String,
    val label: String,
    val extension: String,
    val mimeType: String,
    val ffmpegArguments: List<String>,
    val description: String,
)

object AudioExportFormats {
    val M4A_AAC = AudioExportTarget(
        id = "m4a-aac",
        label = "M4A",
        extension = "m4a",
        mimeType = "audio/mp4",
        ffmpegArguments = listOf("-c:a", "aac", "-b:a", "256k", "-movflags", "+faststart"),
        description = "AAC in an M4A container",
    )
    val MP3 = AudioExportTarget(
        id = "mp3",
        label = "MP3",
        extension = "mp3",
        mimeType = "audio/mpeg",
        ffmpegArguments = listOf("-c:a", "libmp3lame", "-q:a", "2"),
        description = "High-quality variable-bitrate MP3",
    )
    val WAV = AudioExportTarget(
        id = "wav",
        label = "WAV",
        extension = "wav",
        mimeType = "audio/wav",
        ffmpegArguments = listOf("-c:a", "pcm_s16le"),
        description = "Uncompressed 16-bit PCM",
    )
    val FLAC = AudioExportTarget(
        id = "flac",
        label = "FLAC",
        extension = "flac",
        mimeType = "audio/flac",
        ffmpegArguments = listOf("-c:a", "flac", "-compression_level", "8"),
        description = "Lossless FLAC",
    )
    val OGG_VORBIS = AudioExportTarget(
        id = "ogg-vorbis",
        label = "OGG",
        extension = "ogg",
        mimeType = "audio/ogg",
        ffmpegArguments = listOf("-c:a", "libvorbis", "-q:a", "6"),
        description = "Ogg Vorbis",
    )
    val OPUS = AudioExportTarget(
        id = "opus",
        label = "OPUS",
        extension = "opus",
        mimeType = "audio/ogg",
        ffmpegArguments = listOf("-c:a", "libopus", "-b:a", "160k", "-vbr", "on"),
        description = "Ogg Opus",
    )
    val AAC = AudioExportTarget(
        id = "aac",
        label = "AAC",
        extension = "aac",
        mimeType = "audio/aac",
        ffmpegArguments = listOf("-c:a", "aac", "-b:a", "256k", "-f", "adts"),
        description = "Raw AAC with ADTS headers",
    )
    val ALAC = AudioExportTarget(
        id = "alac",
        label = "ALAC",
        extension = "m4a",
        mimeType = "audio/mp4",
        ffmpegArguments = listOf("-c:a", "alac", "-movflags", "+faststart"),
        description = "Apple Lossless in M4A",
    )
    val AIFF = AudioExportTarget(
        id = "aiff",
        label = "AIFF",
        extension = "aiff",
        mimeType = "audio/aiff",
        ffmpegArguments = listOf("-c:a", "pcm_s16be"),
        description = "Uncompressed big-endian PCM",
    )
    val AMR_NB = AudioExportTarget(
        id = "amr-nb",
        label = "AMR-NB",
        extension = "amr",
        mimeType = "audio/amr",
        ffmpegArguments = listOf(
            "-c:a", "libopencore_amrnb",
            "-ar", "8000",
            "-ac", "1",
            "-b:a", "12.2k",
        ),
        description = "Narrowband speech audio",
    )
    val AMR_WB = AudioExportTarget(
        id = "amr-wb",
        label = "AMR-WB",
        extension = "awb",
        mimeType = "audio/amr-wb",
        ffmpegArguments = listOf(
            "-c:a", "libvo_amrwbenc",
            "-ar", "16000",
            "-ac", "1",
            "-b:a", "23.85k",
        ),
        description = "Wideband speech audio",
    )
    val WMA = AudioExportTarget(
        id = "wma",
        label = "WMA",
        extension = "wma",
        mimeType = "audio/x-ms-wma",
        ffmpegArguments = listOf("-c:a", "wmav2", "-b:a", "192k"),
        description = "Windows Media Audio",
    )
    val AC3 = AudioExportTarget(
        id = "ac3",
        label = "AC3",
        extension = "ac3",
        mimeType = "audio/ac3",
        ffmpegArguments = listOf("-c:a", "ac3", "-b:a", "384k"),
        description = "Dolby Digital compatible AC-3",
    )
    val EAC3 = AudioExportTarget(
        id = "eac3",
        label = "E-AC3",
        extension = "eac3",
        mimeType = "audio/eac3",
        ffmpegArguments = listOf("-c:a", "eac3", "-b:a", "448k"),
        description = "Enhanced AC-3",
    )
    val CAF = AudioExportTarget(
        id = "caf",
        label = "CAF",
        extension = "caf",
        mimeType = "audio/x-caf",
        ffmpegArguments = listOf("-c:a", "pcm_s16le"),
        description = "Core Audio Format with PCM",
    )
    val WAVPACK = AudioExportTarget(
        id = "wavpack",
        label = "WavPack",
        extension = "wv",
        mimeType = "audio/x-wavpack",
        ffmpegArguments = listOf("-c:a", "wavpack"),
        description = "Lossless WavPack",
    )

    val presets: List<AudioExportTarget> = listOf(
        M4A_AAC,
        MP3,
        WAV,
        FLAC,
        OGG_VORBIS,
        OPUS,
        AAC,
        ALAC,
        AIFF,
        AMR_NB,
        AMR_WB,
        WMA,
        AC3,
        EAC3,
        CAF,
        WAVPACK,
    )

    const val CUSTOM_ID = "custom"

    fun custom(
        extension: String,
        encoder: String,
        muxer: String,
    ): AudioExportTarget {
        val safeExtension = extension
            .trim()
            .lowercase()
            .removePrefix(".")
            .replace(Regex("[^a-z0-9]"), "")
        require(safeExtension.isNotBlank()) { "Enter a custom file extension" }

        val arguments = buildList {
            encoder.trim().takeIf(String::isNotBlank)?.let {
                add("-c:a")
                add(it)
            }
            muxer.trim().takeIf(String::isNotBlank)?.let {
                add("-f")
                add(it)
            }
        }

        return AudioExportTarget(
            id = CUSTOM_ID,
            label = safeExtension.uppercase(),
            extension = safeExtension,
            mimeType = mimeForExtension(safeExtension),
            ffmpegArguments = arguments,
            description = "Custom FFmpeg encoder and container",
        )
    }

    private fun mimeForExtension(extension: String): String = when (extension) {
        "mp3" -> "audio/mpeg"
        "m4a", "mp4" -> "audio/mp4"
        "wav" -> "audio/wav"
        "flac" -> "audio/flac"
        "ogg", "oga", "opus" -> "audio/ogg"
        "aac" -> "audio/aac"
        "aif", "aiff" -> "audio/aiff"
        "amr" -> "audio/amr"
        "awb" -> "audio/amr-wb"
        "wma" -> "audio/x-ms-wma"
        "ac3" -> "audio/ac3"
        "eac3" -> "audio/eac3"
        "caf" -> "audio/x-caf"
        "wv" -> "audio/x-wavpack"
        else -> "audio/$extension"
    }
}
