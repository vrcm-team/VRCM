package io.github.vrcmteam.vrcm.presentation.screens.gallery

import io.github.vrcmteam.vrcm.network.api.prints.data.PrintData

internal data class PrintFallbackMetadata(
    val authorName: String?,
    val timestamp: String?,
)

internal fun PrintData.fallbackMetadata(): PrintFallbackMetadata? {
    if (!worldName.isNullOrBlank()) return null

    val metadata = PrintFallbackMetadata(
        authorName = authorName.nonBlankOrNull(),
        timestamp = timestamp.nonBlankOrNull() ?: createdAt.nonBlankOrNull(),
    )
    return metadata.takeIf { it.authorName != null || it.timestamp != null }
}

private fun String?.nonBlankOrNull(): String? = this?.trim()?.takeIf(String::isNotEmpty)
