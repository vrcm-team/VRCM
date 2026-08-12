package io.github.vrcmteam.vrcm.network.websocket.data.content

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

class NotificationContentTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun decodesPipelinePayloadWithoutApiOnlyFields() {
        val content = json.decodeFromString<NotificationContent>(
            """{
                "id":"notification_test",
                "type":"boop",
                "senderUserId":"usr_sender",
                "senderUsername":"Sender",
                "details":{"emojiId":"default_heart"},
                "seen":false
            }""",
        )

        assertEquals("notification_test", content.id)
        assertEquals("usr_sender", content.senderUserId)
        assertEquals("default_heart", content.details?.emojiId)
    }

    @Test
    fun decodesNotificationV2BoopPayload() {
        val content = json.decodeFromString<NotificationContent>(
            """{
                "id":"not_v2",
                "version":4,
                "type":"boop",
                "relatedNotificationsId":"not_legacy",
                "data":{"boopingUserDisplayName":"Sender"},
                "details":{"emojiId":"default_hand_wave"}
            }""",
        )

        assertEquals(4, content.version)
        assertEquals("not_legacy", content.relatedNotificationsId)
        assertEquals("Sender", content.data.boopingUserDisplayName)
        assertEquals("default_hand_wave", content.details?.emojiId)
    }

    @Test
    fun decodesNotificationV2UpdatePayload() {
        val content = json.decodeFromString<NotificationV2UpdateContent>(
            """{
                "id":"not_v2",
                "version":5,
                "updates":{"relatedNotificationsId":"not_next"}
            }""",
        )

        assertEquals("not_v2", content.id)
        assertEquals(5, content.version)
        assertEquals("not_next", content.updates["relatedNotificationsId"]?.jsonPrimitive?.content)
    }

    @Test
    fun decodesGroupIdAndImageFromNotificationPayload() {
        val content = json.decodeFromString<NotificationContent>(
            """{
                "id":"group_event",
                "type":"group.announcement",
                "groupId":"grp_test",
                "link":"event:grp_link",
                "data":{"groupId":"grp_data","ownerId":"grp_owner","imageUrl":"https://example/icon.png"}
            }""",
        )

        assertEquals("grp_test", content.groupId)
        assertEquals("grp_data", content.data.groupId)
        assertEquals("grp_owner", content.data.ownerId)
        assertEquals("https://example/icon.png", content.data.imageUrl)
    }
}
