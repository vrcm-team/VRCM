package io.github.vrcmteam.vrcm.service

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OfficialLinkServiceTest {
    @Test
    fun parsesSupportedOfficialIds() {
        val ids = mapOf(
            "usr_abc-123" to OfficialLinkTarget(OfficialLinkType.User, "usr_abc-123"),
            "wrld_abc-123" to OfficialLinkTarget(OfficialLinkType.World, "wrld_abc-123"),
            "grp_abc-123" to OfficialLinkTarget(OfficialLinkType.Group, "grp_abc-123"),
            "avtr_abc-123" to OfficialLinkTarget(OfficialLinkType.Avatar, "avtr_abc-123"),
        )

        ids.forEach { (id, expected) ->
            assertEquals(expected, parseOfficialId("  $id\n"))
        }
    }

    @Test
    fun parsesSupportedOfficialLinks() {
        val links = mapOf(
            "https://vrchat.com/home/user/usr_abc-123" to
                OfficialLinkTarget(OfficialLinkType.User, "usr_abc-123"),
            "https://vrchat.com/home/world/wrld_abc-123?ref=clipboard" to
                OfficialLinkTarget(OfficialLinkType.World, "wrld_abc-123"),
            "https://www.vrchat.com/home/group/grp_abc-123/" to
                OfficialLinkTarget(OfficialLinkType.Group, "grp_abc-123"),
            "https://vrchat.com/home/avatar/avtr_abc-123#details" to
                OfficialLinkTarget(OfficialLinkType.Avatar, "avtr_abc-123"),
        )

        links.forEach { (url, expected) ->
            assertEquals(expected, parseOfficialLink(url))
        }
    }

    @Test
    fun rejectsForeignMalformedAndMismatchedLinks() {
        val links = listOf(
            "http://vrchat.com/home/user/usr_abc-123",
            "https://vrchat.com.example/home/user/usr_abc-123",
            "https://vrchat.com/home/user/wrld_abc-123",
            "https://vrchat.com/home/user/usr_abc-123/extra",
            "https://vrchat.com/home/user/usr_abc_123",
            "not a link",
        )

        links.forEach { assertNull(parseOfficialLink(it), it) }
    }

    @Test
    fun rejectsMalformedOfficialIds() {
        val ids = listOf(
            "usr_abc_123",
            "usr_abc-123 extra",
            "file_abc-123",
            "wrld_abc-123:instance",
            "not-an-id",
        )

        ids.forEach { assertNull(parseOfficialId(it), it) }
    }
}
