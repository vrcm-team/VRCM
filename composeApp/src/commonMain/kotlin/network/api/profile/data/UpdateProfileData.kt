package io.github.vrcmteam.vrcm.network.api.profile.data

import kotlinx.serialization.Serializable

/** Public profile fields written through `/profile/{userId}`. Null leaves a field unchanged. */
@Serializable
data class UpdateProfileData(
    val bio: String? = null,
    val bioLinks: List<String>? = null,
    val languages: List<String>? = null,
)
