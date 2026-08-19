package io.github.vrcmteam.vrcm.storage.data

import io.github.vrcmteam.vrcm.network.api.avatars.data.AvatarData
import io.github.vrcmteam.vrcm.network.api.users.data.LimitedUserGroup
import io.github.vrcmteam.vrcm.network.api.users.data.UserData
import io.github.vrcmteam.vrcm.network.api.worlds.data.FavoritedWorld
import io.github.vrcmteam.vrcm.network.api.worlds.data.WorldData
import kotlinx.serialization.Serializable

@Serializable
data class UserProfileCache(
    val user: UserData,
    val groups: List<LimitedUserGroup> = emptyList(),
    val mutualGroups: List<LimitedUserGroup> = emptyList(),
    val createdWorlds: List<WorldData> = emptyList(),
    val createdWorldDetailRevisions: Map<String, WorldDetailRevision> = emptyMap(),
    val createdAvatars: List<AvatarData> = emptyList(),
    val favoritedWorlds: List<FavoritedWorldGroup> = emptyList(),
)

@Serializable
data class WorldDetailRevision(
    val updatedAt: String?,
    val version: Int?,
)

@Serializable
data class FavoritedWorldGroup(
    val name: String,
    val worlds: List<FavoritedWorld>,
    val groupKey: String = name,
)
