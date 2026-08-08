package io.github.vrcmteam.vrcm.storage.meetup

import kotlinx.serialization.Serializable

/** Persisted decoration metadata and locally verified asset references. */
@Serializable
data class DecorationTemplateCache(
    val templateId: String,
    val mainAnimationUrl: String = "",
    val baseUrl: String = "",
    val mainAnimationAsset: MeetupAssetRef? = null,
    val baseAsset: MeetupAssetRef? = null,
    val gradientStart: String = "",
    val gradientEnd: String = "",
)
