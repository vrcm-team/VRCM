package io.github.vrcmteam.vrcm.network.api.favorite.data

import kotlinx.serialization.Serializable

@Serializable
data class FavoriteData(
    val favoriteId: String,
    val id: String,
    val tags: List<String>,
    val type: String
)