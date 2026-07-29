package io.github.vrcmteam.vrcm.storage

import com.russhwolf.settings.Settings
import io.github.vrcmteam.vrcm.storage.data.UserProfileCache
import kotlinx.serialization.json.Json

class UserProfileCacheDao(
    private val settings: Settings,
) {
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    private fun key(ownerUserId: String, userId: String) =
        "${DaoKeys.UserProfileCache.KEY_PREFIX}.$ownerUserId.$userId"

    fun load(ownerUserId: String, userId: String): UserProfileCache? =
        settings.getStringOrNull(key(ownerUserId, userId))?.let { raw ->
            runCatching { json.decodeFromString<UserProfileCache>(raw) }.getOrNull()
        }

    fun save(ownerUserId: String, userId: String, cache: UserProfileCache) {
        settings.putString(
            key(ownerUserId, userId),
            json.encodeToString(UserProfileCache.serializer(), cache),
        )
    }
}
