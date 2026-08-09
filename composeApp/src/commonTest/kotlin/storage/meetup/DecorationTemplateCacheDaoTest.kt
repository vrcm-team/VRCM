package io.github.vrcmteam.vrcm.storage.meetup

import com.russhwolf.settings.MapSettings
import io.github.vrcmteam.vrcm.storage.DaoKeys
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class DecorationTemplateCacheDaoTest {
    @Test
    fun cachesRoundTripAndStayIsolatedPerTemplate() {
        val settings = MapSettings()
        val dao = DecorationTemplateCacheDao(settings)
        val animated = DecorationTemplateCache(
            templateId = "inv_animated",
            mainAnimationUrl = "https://cdn/main.webp",
            baseUrl = "https://cdn/base.webp",
            mainAnimationAsset = MeetupAssetRef("decorations/inv_animated/$SHA_A/main-animation.webp", SHA_A),
            baseAsset = MeetupAssetRef("decorations/inv_animated/$SHA_B/base.webp", SHA_B),
            gradientStart = "#112233",
            gradientEnd = "#445566",
            failedMainAnimationUrl = "https://cdn/previously-broken.webp",
        )
        val minimal = DecorationTemplateCache(templateId = "inv_minimal")

        dao.save(animated)
        dao.save(minimal)

        assertEquals(animated, dao.load("inv_animated"))
        assertEquals(minimal, dao.load("inv_minimal"))
        assertNull(dao.load("inv_never_saved"))
    }

    @Test
    fun storedPayloadForAnotherTemplateIsRejectedInsteadOfServedUnderTheWrongId() {
        val settings = MapSettings()
        val dao = DecorationTemplateCacheDao(settings)
        dao.save(DecorationTemplateCache(templateId = "inv_real", gradientStart = "#ff0000"))
        val realRaw = assertNotNull(
            settings.getStringOrNull("${DaoKeys.MeetupDecoration.KEY_PREFIX}.inv_real"),
        )
        settings.putString("${DaoKeys.MeetupDecoration.KEY_PREFIX}.inv_impostor", realRaw)

        // 键与载荷里的 templateId 对不上时，宁可当作没有缓存，也不能把别人的素材引用交出去。
        assertNull(dao.load("inv_impostor"))
        assertEquals("#ff0000", dao.load("inv_real")?.gradientStart)
    }

    @Test
    fun malformedOrForwardCompatiblePayloadsDoNotThrow() {
        val settings = MapSettings()
        val dao = DecorationTemplateCacheDao(settings)
        settings.putString("${DaoKeys.MeetupDecoration.KEY_PREFIX}.inv_broken", "not-json")
        // 旧版本写下的记录没有 failedMainAnimationUrl，新版本加的字段也应能被旧解析器忽略。
        settings.putString(
            "${DaoKeys.MeetupDecoration.KEY_PREFIX}.inv_legacy",
            """{"templateId":"inv_legacy","baseUrl":"https://cdn/base.webp","future":true}""",
        )

        assertNull(dao.load("inv_broken"))
        val legacy = assertNotNull(dao.load("inv_legacy"))
        assertEquals("https://cdn/base.webp", legacy.baseUrl)
        assertEquals("", legacy.failedMainAnimationUrl)
    }

    @Test
    fun templateIdsThatCouldEscapeTheKeyNamespaceAreRejected() {
        val dao = DecorationTemplateCacheDao(MapSettings())

        listOf("", "inv/../other", "inv.other", "inv other").forEach { invalid ->
            assertFailsWith<IllegalArgumentException>("load 接受了非法 ID：$invalid") {
                dao.load(invalid)
            }
            assertFailsWith<IllegalArgumentException>("save 接受了非法 ID：$invalid") {
                dao.save(DecorationTemplateCache(templateId = invalid))
            }
        }
    }

    @Test
    fun clearAllRemovesOnlyDecorationKeys() {
        val settings = MapSettings()
        val dao = DecorationTemplateCacheDao(settings)
        dao.save(DecorationTemplateCache(templateId = "inv_one"))
        dao.save(DecorationTemplateCache(templateId = "inv_two"))
        // 同一个 Settings 域里的邻居不该被殃及。
        settings.putString("${DaoKeys.MeetupDecoration.NAME}.unrelated", "keep me")

        dao.clearAll()

        assertNull(dao.load("inv_one"))
        assertNull(dao.load("inv_two"))
        assertEquals("keep me", settings.getStringOrNull("${DaoKeys.MeetupDecoration.NAME}.unrelated"))
    }

    private companion object {
        val SHA_A = "a".repeat(64)
        val SHA_B = "b".repeat(64)
    }
}
