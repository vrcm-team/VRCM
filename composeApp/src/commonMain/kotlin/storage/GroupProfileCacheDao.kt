package io.github.vrcmteam.vrcm.storage

import com.russhwolf.settings.Settings
import io.github.vrcmteam.vrcm.storage.data.GroupProfileCache
import kotlinx.serialization.json.Json

class GroupProfileCacheDao(
    private val settings: Settings,
) {
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    private fun key(groupId: String) = "${DaoKeys.GroupProfileCache.KEY_PREFIX}.$groupId"

    fun load(groupId: String): GroupProfileCache? =
        settings.getStringOrNull(key(groupId))?.let { raw ->
            runCatching { json.decodeFromString<GroupProfileCache>(raw) }.getOrNull()
        }

    fun save(cache: GroupProfileCache) {
        settings.putString(
            key(cache.group.id),
            json.encodeToString(GroupProfileCache.serializer(), cache),
        )
    }

    fun clearAll() {
        settings.keys
            .filter { it.startsWith("${DaoKeys.GroupProfileCache.KEY_PREFIX}.") }
            .forEach(settings::remove)
    }
}
