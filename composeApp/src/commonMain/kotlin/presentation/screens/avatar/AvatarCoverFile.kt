package io.github.vrcmteam.vrcm.presentation.screens.avatar

internal object AvatarCoverLimits {
    const val MAX_FILE_BYTES: Long = 10L * 1024L * 1024L
    val ALLOWED_EXTENSIONS = listOf("jpg", "jpeg", "png", "webp")
}

internal data class AvatarCoverFile(
    val bytes: ByteArray,
    val fileName: String,
    val mimeType: String,
)

internal sealed interface AvatarCoverValidation {
    data class Valid(val cover: AvatarCoverFile) : AvatarCoverValidation
    data object FileTooLarge : AvatarCoverValidation
    data object UnsupportedFormat : AvatarCoverValidation
}

private enum class AvatarCoverFormat(
    val extensions: Set<String>,
    val mimeType: String,
) {
    Jpeg(setOf("jpg", "jpeg"), "image/jpeg"),
    Png(setOf("png"), "image/png"),
    WebP(setOf("webp"), "image/webp"),
}

internal fun validateAvatarCover(
    fileName: String,
    bytes: ByteArray,
): AvatarCoverValidation {
    if (bytes.size.toLong() > AvatarCoverLimits.MAX_FILE_BYTES) {
        return AvatarCoverValidation.FileTooLarge
    }

    val extension = fileName.substringAfterLast('.', missingDelimiterValue = "").lowercase()
    val declaredFormat = AvatarCoverFormat.entries.firstOrNull { extension in it.extensions }
        ?: return AvatarCoverValidation.UnsupportedFormat
    val detectedFormat = when {
        bytes.hasPrefix(0xFF, 0xD8, 0xFF) -> AvatarCoverFormat.Jpeg
        bytes.hasPrefix(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) ->
            AvatarCoverFormat.Png
        bytes.hasAsciiAt(0, "RIFF") && bytes.hasAsciiAt(8, "WEBP") -> AvatarCoverFormat.WebP
        else -> null
    }
    if (detectedFormat != declaredFormat) return AvatarCoverValidation.UnsupportedFormat

    return AvatarCoverValidation.Valid(
        AvatarCoverFile(
            bytes = bytes,
            fileName = fileName,
            mimeType = detectedFormat.mimeType,
        )
    )
}

private fun ByteArray.hasPrefix(vararg expected: Int): Boolean =
    size >= expected.size && expected.indices.all { index ->
        this[index].toInt() and 0xFF == expected[index]
    }

private fun ByteArray.hasAsciiAt(offset: Int, expected: String): Boolean =
    size >= offset + expected.length && expected.indices.all { index ->
        this[offset + index].toInt() and 0xFF == expected[index].code
    }
