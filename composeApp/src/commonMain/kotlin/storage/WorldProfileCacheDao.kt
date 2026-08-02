package io.github.vrcmteam.vrcm.storage

import com.russhwolf.settings.Settings
import io.github.vrcmteam.vrcm.storage.data.WorldProfileCache
import kotlinx.serialization.json.Json

class WorldProfileCacheDao(
    private val settings: Settings,
) {
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    private fun key(worldId: String) = "${DaoKeys.WorldProfileCache.KEY_PREFIX}.$worldId"

    fun load(worldId: String): WorldProfileCache? =
        settings.getStringOrNull(key(worldId))?.let { raw ->
            runCatching { json.decodeFromString<WorldProfileCache>(raw) }.getOrNull()
        }

    fun save(cache: WorldProfileCache) {
        settings.putString(
            key(cache.world.id),
            json.encodeToString(WorldProfileCache.serializer(), cache),
        )
    }

    fun clearAll() {
        settings.keys
            .filter { it.startsWith("${DaoKeys.WorldProfileCache.KEY_PREFIX}.") }
            .forEach(settings::remove)
    }
}
