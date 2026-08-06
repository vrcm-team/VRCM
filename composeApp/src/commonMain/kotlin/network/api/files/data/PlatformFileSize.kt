package io.github.vrcmteam.vrcm.network.api.files.data

import kotlinx.serialization.Serializable

/**
 * Represents the file size of a world for a specific platform.
 */
@Serializable
data class PlatformFileSize(
    val platform: PlatformType,
    val sizeInBytes: Long,
    val displayName: String
) {

    val formattedSize: String
        get() = when {
            sizeInBytes < 1024 -> "$sizeInBytes B"
            sizeInBytes < 1024 * 1024 -> "${sizeInBytes / 1024} KB"
            sizeInBytes < 1024 * 1024 * 1024 -> "${sizeInBytes / (1024 * 1024)} MB"
            else -> "${sizeInBytes / (1024 * 1024 * 1024)} GB"
        }
}