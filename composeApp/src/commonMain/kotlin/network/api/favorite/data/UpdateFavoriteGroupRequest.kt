package io.github.vrcmteam.vrcm.network.api.favorite.data

import io.github.vrcmteam.vrcm.network.api.attributes.FavoriteGroupVisibility
import kotlinx.serialization.Serializable

/** Editable metadata sent for an official favorite group. */
@Serializable
data class UpdateFavoriteGroupRequest(
    val displayName: String,
    val visibility: FavoriteGroupVisibility,
)
