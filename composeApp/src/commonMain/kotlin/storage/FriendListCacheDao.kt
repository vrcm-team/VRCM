package io.github.vrcmteam.vrcm.storage

import com.russhwolf.settings.Settings
import io.github.vrcmteam.vrcm.storage.data.FriendListCache
import kotlinx.serialization.json.Json

class FriendListCacheDao(
    private val settings: Settings,
) {
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    private fun key(userId: String) = "${DaoKeys.FriendListCache.KEY_PREFIX}.$userId"

    fun load(userId: String): FriendListCache? =
        settings.getStringOrNull(key(userId))?.let { raw ->
            runCatching { json.decodeFromString<FriendListCache>(raw) }.getOrNull()
        }

    fun save(userId: String, cache: FriendListCache) {
        settings.putString(key(userId), json.encodeToString(FriendListCache.serializer(), cache))
    }
}
