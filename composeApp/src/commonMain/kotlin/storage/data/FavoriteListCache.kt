package io.github.vrcmteam.vrcm.storage.data

import io.github.vrcmteam.vrcm.network.api.avatars.data.AvatarData
import kotlinx.serialization.Serializable

/** 当前账号收藏页的可恢复列表快照。 */
@Serializable
data class FavoriteListCache(
    val favoritedWorlds: List<FavoritedWorldGroup> = emptyList(),
    val favoritedAvatars: List<AvatarData> = emptyList(),
    val worldsLoaded: Boolean = false,
    val avatarsLoaded: Boolean = false,
)
