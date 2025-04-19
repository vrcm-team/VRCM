package io.github.vrcmteam.vrcm.network.api.files.data

import cafe.adriel.voyager.core.lifecycle.JavaSerializable
import kotlinx.serialization.Serializable

/**
 * Represents the file size of a world for a specific platform.
 */
@Serializable
data class PlatformFileSize(
    val platform: PlatformType,
    val sizeInBytes: Long,
    val displayName: String
) : JavaSerializable {

    val formattedSize: String
        get() = when {
            sizeInBytes < 1024 -> "$sizeInBytes B"
            sizeInBytes < 1024 * 1024 -> "${sizeInBytes / 1024} KB"
            sizeInBytes < 1024 * 1024 * 1024 -> "${sizeInBytes / (1024 * 1024)} MB"
            else -> "${sizeInBytes / (1024 * 1024 * 1024)} GB"
        }
}