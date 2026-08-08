package io.github.vrcmteam.vrcm.storage.meetup

import com.russhwolf.settings.Settings
import io.github.vrcmteam.vrcm.storage.DaoKeys
import kotlinx.serialization.json.Json

/** Stores decoration template caches under validated, template-specific settings keys. */
class DecorationTemplateCacheDao(
    private val settings: Settings,
) {
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    fun load(templateId: String): DecorationTemplateCache? {
        val key = key(templateId)
        return settings.getStringOrNull(key)
            ?.let(::decode)
            ?.takeIf { it.templateId == templateId }
    }

    fun save(cache: DecorationTemplateCache) {
        settings.putString(
            key(cache.templateId),
            json.encodeToString(DecorationTemplateCache.serializer(), cache),
        )
    }

    fun clearAll() {
        val prefix = "${DaoKeys.MeetupDecoration.KEY_PREFIX}."
        settings.keys
            .filter { it.startsWith(prefix) }
            .forEach(settings::remove)
    }

    private fun key(templateId: String): String {
        require(ID_PATTERN.matches(templateId)) { "Invalid decoration template ID" }
        return "${DaoKeys.MeetupDecoration.KEY_PREFIX}.$templateId"
    }

    private fun decode(raw: String): DecorationTemplateCache? =
        runCatching { json.decodeFromString<DecorationTemplateCache>(raw) }.getOrNull()

    private companion object {
        val ID_PATTERN = Regex("[A-Za-z0-9_-]+")
    }
}
