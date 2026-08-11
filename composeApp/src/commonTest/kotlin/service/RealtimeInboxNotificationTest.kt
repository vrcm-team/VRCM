package io.github.vrcmteam.vrcm.service

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RealtimeInboxNotificationTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun pipelinePayloadRoutesAllSupportedInboxAlertsWithoutAListRefresh() {
        val boop = decode(
            """{"id":"not_boop","type":"boop","senderUsername":"Aoi","details":{"emojiId":"default_heart"}}"""
        ).resolveAlert(
            boopEnabled = true,
            friendRequestEnabled = false,
            groupAnnouncementEnabled = false,
        )
        assertEquals(
            RealtimeInboxAlert.Boop("not_boop", "Aoi", "default_heart"),
            boop,
        )

        val friendRequest = decode(
            """{"id":"not_friend","type":"friendRequest","senderUserId":"usr_friend"}"""
        ).resolveAlert(
            boopEnabled = false,
            friendRequestEnabled = true,
            groupAnnouncementEnabled = false,
        )
        assertEquals(
            RealtimeInboxAlert.FriendRequest("not_friend", "usr_friend"),
            friendRequest,
        )

        val announcement = decode(
            """{"id":"not_group","type":"group.announcement","message":"Fallback","details":{"groupName":"VRCM","announcementTitle":"Meetup"}}"""
        ).resolveAlert(
            boopEnabled = false,
            friendRequestEnabled = false,
            groupAnnouncementEnabled = true,
        )
        assertEquals(
            RealtimeInboxAlert.GroupAnnouncement("not_group", "VRCM", "Meetup"),
            announcement,
        )
    }

    @Test
    fun disabledOrUnsupportedRealtimeAlertsAreNotDelivered() {
        val boop = decode("""{"id":"not_boop","type":"boop"}""")
        assertNull(
            boop.resolveAlert(
                boopEnabled = false,
                friendRequestEnabled = true,
                groupAnnouncementEnabled = true,
            )
        )

        val invite = decode("""{"id":"not_invite","type":"invite"}""")
        assertNull(
            invite.resolveAlert(
                boopEnabled = true,
                friendRequestEnabled = true,
                groupAnnouncementEnabled = true,
            )
        )
    }

    private fun decode(content: String) = decodeRealtimeInboxNotification(json, content)
}
