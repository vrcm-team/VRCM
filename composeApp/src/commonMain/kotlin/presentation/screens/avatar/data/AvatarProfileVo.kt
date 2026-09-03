package io.github.vrcmteam.vrcm.presentation.screens.avatar.data

import io.github.vrcmteam.vrcm.network.api.avatars.data.AvatarData
import kotlinx.serialization.Serializable

@Serializable
data class AvatarProfileVo(
    val avatarId: String,
    val avatarName: String = "",
    val avatarImageUrl: String? = null,
    val thumbnailImageUrl: String? = null,
    val avatarDescription: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val releaseStatus: String = "",
    val tags: List<String> = emptyList(),
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val version: Int? = null,
    val hasImpostor: Boolean = false,
    val platformInfos: List<AvatarPlatformInfo> = emptyList(),
) {
    constructor(avatar: AvatarData) : this(
        avatarId = avatar.id,
        avatarName = avatar.name,
        avatarImageUrl = avatar.imageUrl,
        thumbnailImageUrl = avatar.thumbnailImageUrl,
        avatarDescription = avatar.description.orEmpty(),
        authorId = avatar.authorId,
        authorName = avatar.authorName,
        releaseStatus = avatar.releaseStatus,
        tags = avatar.tags,
        createdAt = avatar.createdAt,
        updatedAt = avatar.updatedAt,
        version = avatar.version,
        hasImpostor = avatar.unityPackages.any { pkg ->
            pkg.variant.equals("impostor", ignoreCase = true) ||
                !pkg.impostorUrl.isNullOrBlank()
        },
        platformInfos = avatar.unityPackages.filterNot { pkg ->
            pkg.variant.equals("impostor", ignoreCase = true)
        }.map { pkg ->
            AvatarPlatformInfo(
                platform = pkg.platform ?: "unknown",
                unityVersion = pkg.unityVersion,
                performanceRating = pkg.performanceRating,
            )
        }.distinctBy { "${it.platform}/${it.unityVersion}" }
    )
}

@Serializable
data class AvatarPlatformInfo(
    val platform: String,
    val unityVersion: String? = null,
    val performanceRating: String? = null,
) {
    val displayName: String
        get() = buildString {
            append(platform.replaceFirstChar { it.uppercase() })
            unityVersion?.let { append("/$it") }
        }
    val ratingDisplay: String
        get() = performanceRating?.replaceFirstChar { it.uppercase() } ?: "Unknown"
}
