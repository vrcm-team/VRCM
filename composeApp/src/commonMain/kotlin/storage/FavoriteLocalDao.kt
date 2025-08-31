package io.github.vrcmteam.vrcm.storage

import com.russhwolf.settings.Settings
import io.github.vrcmteam.vrcm.network.api.attributes.FavoriteType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 使用 Settings 持久化的本地收藏分组
 */
class FavoriteLocalDao(
    private val settings: Settings,
) {
    @Serializable
    data class LocalGroups(
        val groups: List<String> = emptyList()
    )

    private fun keyOf(type: FavoriteType): String = when (type) {
        FavoriteType.World -> DaoKeys.FavoriteLocal.WORLD_KEY
        FavoriteType.Friend -> DaoKeys.FavoriteLocal.FRIEND_KEY
        FavoriteType.Avatar -> DaoKeys.FavoriteLocal.AVATAR_KEY
    }

    fun load(type: FavoriteType): List<String> {
        val key = keyOf(type)
        val json = settings.getStringOrNull(key) ?: return emptyList()
        return runCatching {
            Json.decodeFromString<LocalGroups>(json).groups
        }.getOrElse { emptyList() }
    }

    fun save(type: FavoriteType, groups: List<String>) {
        val key = keyOf(type)
        val json = Json.encodeToString(LocalGroups(groups))
        settings.putString(key, json)
    }
}
