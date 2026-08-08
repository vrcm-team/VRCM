package io.github.vrcmteam.vrcm.storage.meetup

import com.russhwolf.settings.Settings
import io.github.vrcmteam.vrcm.storage.DaoKeys
import kotlinx.serialization.json.Json

/** 按账号存取会面身份卡配置，并隔离无法解码的配置项。 */
class MeetupCardConfigDao(
    private val settings: Settings,
) {
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    private fun key(ownerUserId: String) = "${DaoKeys.MeetupCard.KEY_PREFIX}.$ownerUserId"

    fun load(ownerUserId: String): MeetupCardConfig? =
        settings.getStringOrNull(key(ownerUserId))
            ?.let(::decode)
            ?.takeIf { it.ownerUserId == ownerUserId }

    fun save(config: MeetupCardConfig) {
        require(config.ownerUserId.isNotBlank()) { "ownerUserId must not be blank" }
        settings.putString(
            key(config.ownerUserId),
            json.encodeToString(MeetupCardConfig.serializer(), config),
        )
    }

    fun all(): List<MeetupCardConfig> {
        val keyPrefix = "${DaoKeys.MeetupCard.KEY_PREFIX}."
        return settings.keys.mapNotNull { storedKey ->
            val ownerUserId = storedKey
                .takeIf { it.startsWith(keyPrefix) }
                ?.removePrefix(keyPrefix)
                ?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            settings.getStringOrNull(storedKey)
                ?.let(::decode)
                ?.takeIf { it.ownerUserId == ownerUserId }
        }
    }

    fun clear(ownerUserId: String) {
        settings.remove(key(ownerUserId))
    }

    private fun decode(raw: String): MeetupCardConfig? =
        runCatching { json.decodeFromString<MeetupCardConfig>(raw) }.getOrNull()
}
