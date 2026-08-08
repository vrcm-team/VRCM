package io.github.vrcmteam.vrcm.storage.meetup

import com.russhwolf.settings.MapSettings
import io.github.vrcmteam.vrcm.storage.DaoKeys
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MeetupCardConfigDaoTest {
    @Test
    fun configurationsAreForwardCompatibleAndIsolatedByOwner() {
        val settings = MapSettings()
        val dao = MeetupCardConfigDao(settings)
        dao.save(defaultMeetupCardConfig("usr_a").copy(shortText = "A"))
        dao.save(defaultMeetupCardConfig("usr_b").copy(shortText = "B"))

        val ownerAKey = "${DaoKeys.MeetupCard.KEY_PREFIX}.usr_a"
        val ownerARaw = assertNotNull(settings.getStringOrNull(ownerAKey))
        settings.putString(ownerAKey, ownerARaw.dropLast(1) + ",\"future\":true}")

        assertEquals("A", dao.load("usr_a")?.shortText)
        assertEquals("B", dao.load("usr_b")?.shortText)

        dao.clear("usr_a")

        assertNull(dao.load("usr_a"))
        assertNotNull(dao.load("usr_b"))
    }

    @Test
    fun malformedConfigurationIsIgnoredWithoutAffectingOtherOwners() {
        val settings = MapSettings()
        val dao = MeetupCardConfigDao(settings)
        dao.save(defaultMeetupCardConfig("usr_b").copy(shortText = "B"))
        settings.putString("${DaoKeys.MeetupCard.KEY_PREFIX}.usr_a", "not-json")

        assertNull(dao.load("usr_a"))
        assertEquals("B", dao.load("usr_b")?.shortText)
        assertEquals(listOf("usr_b"), dao.all().map(MeetupCardConfig::ownerUserId))
    }

    @Test
    fun keyOwnerMustMatchPayloadOwner() {
        val settings = MapSettings()
        val dao = MeetupCardConfigDao(settings)
        dao.save(defaultMeetupCardConfig("usr_b").copy(shortText = "B"))
        val ownerBKey = "${DaoKeys.MeetupCard.KEY_PREFIX}.usr_b"
        val ownerBRaw = assertNotNull(settings.getStringOrNull(ownerBKey))
        val ownerAKey = "${DaoKeys.MeetupCard.KEY_PREFIX}.usr_a"
        val emptyOwnerKey = "${DaoKeys.MeetupCard.KEY_PREFIX}."
        settings.putString(ownerAKey, ownerBRaw)
        settings.putString(emptyOwnerKey, ownerBRaw)

        assertNull(dao.load("usr_a"))
        assertEquals("B", dao.load("usr_b")?.shortText)
        assertEquals(listOf("usr_b"), dao.all().map(MeetupCardConfig::ownerUserId))
        assertEquals(ownerBRaw, settings.getStringOrNull(ownerAKey))
        assertEquals(ownerBRaw, settings.getStringOrNull(emptyOwnerKey))
    }

    @Test
    fun outdatedSchemaIsTreatedAsAbsentWithoutAffectingOtherOwners() {
        val settings = MapSettings()
        val dao = MeetupCardConfigDao(settings)
        dao.save(defaultMeetupCardConfig("usr_a").copy(shortText = "A"))
        dao.save(defaultMeetupCardConfig("usr_b").copy(shortText = "B"))
        // 模拟旧版本写下的配置：不做字段级迁移，直接当作没配置过。
        val ownerAKey = "${DaoKeys.MeetupCard.KEY_PREFIX}.usr_a"
        val ownerARaw = assertNotNull(settings.getStringOrNull(ownerAKey))
        settings.putString(
            ownerAKey,
            ownerARaw.replace(
                "\"schemaVersion\":$MEETUP_CARD_SCHEMA_VERSION",
                "\"schemaVersion\":${MEETUP_CARD_SCHEMA_VERSION - 1}",
            ),
        )

        assertNull(dao.load("usr_a"))
        assertEquals("B", dao.load("usr_b")?.shortText)
        assertEquals(listOf("usr_b"), dao.all().map(MeetupCardConfig::ownerUserId))
    }

    @Test
    fun savedJsonIncludesSchemaVersionAndDefaultFields() {
        val settings = MapSettings()
        val dao = MeetupCardConfigDao(settings)
        dao.save(defaultMeetupCardConfig("usr_a"))
        val raw = assertNotNull(
            settings.getStringOrNull("${DaoKeys.MeetupCard.KEY_PREFIX}.usr_a"),
        )

        val saved = Json.parseToJsonElement(raw).jsonObject

        assertEquals(MEETUP_CARD_SCHEMA_VERSION, saved["schemaVersion"]?.jsonPrimitive?.int)
        assertEquals(false, saved["showAvatar"]?.jsonPrimitive?.boolean)
    }

    @Test
    fun saveRejectsBlankOwnerUserIdBeforeWriting() {
        val settings = MapSettings()
        val dao = MeetupCardConfigDao(settings)

        assertFailsWith<IllegalArgumentException> {
            dao.save(defaultMeetupCardConfig("   "))
        }

        assertTrue(settings.keys.isEmpty())
    }
}
