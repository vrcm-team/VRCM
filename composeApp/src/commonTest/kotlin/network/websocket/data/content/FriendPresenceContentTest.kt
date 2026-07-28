package io.github.vrcmteam.vrcm.network.websocket.data.content

import io.github.vrcmteam.vrcm.network.api.attributes.UserStatus
import io.github.vrcmteam.vrcm.network.api.friends.date.FriendData
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FriendPresenceContentTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun onlineEventWithoutUserMergesPresenceFieldsIntoCachedFriend() {
        val content = json.decodeFromString<FriendOnlineContent>(
            """{"location":"private","platform":"android","travelingToLocation":"","userId":"usr_a","worldId":""}"""
        )

        val merged = content.mergeWith(cachedFriend())

        assertNull(content.user)
        assertEquals("private", merged?.location)
        assertEquals("android", merged?.lastPlatform)
        assertEquals("Alice", merged?.displayName)
        assertEquals(UserStatus.Active, merged?.status)
    }

    @Test
    fun locationEventWithoutUserPreservesProfileAndUpdatesTravelDestination() {
        val content = json.decodeFromString<FriendLocationContent>(
            """{"canRequestInvite":false,"location":"traveling","travelingToLocation":"wrld_target:1","userId":"usr_a","worldId":"wrld_target"}"""
        )

        val merged = content.mergeWith(cachedFriend())

        assertEquals("traveling", merged?.location)
        assertEquals("wrld_target:1", merged?.travelingToLocation)
        assertEquals("Alice", merged?.displayName)
    }

    @Test
    fun friendUpdateDecodesTheUserEnvelope() {
        val content = json.decodeFromString<FriendUpdateContent>(
            """
            {
              "user": {
                "allowAvatarCopying": false,
                "bio": null,
                "bioLinks": [],
                "currentAvatarImageUrl": "avatar",
                "currentAvatarTags": [],
                "currentAvatarThumbnailImageUrl": "thumbnail",
                "date_joined": "2020-01-01",
                "developerType": "none",
                "displayName": "Alice Updated",
                "friendKey": "",
                "id": "usr_a",
                "isFriend": true,
                "last_activity": "2026-01-01",
                "last_login": "2026-01-01",
                "last_platform": "android",
                "profilePicOverride": "",
                "state": "online",
                "status": "active",
                "statusDescription": "updated",
                "tags": [],
                "userIcon": "",
                "pronouns": null
              }
            }
            """.trimIndent()
        )

        assertEquals("usr_a", content.user.id)
        assertEquals("Alice Updated", content.user.displayName)
    }

    private fun cachedFriend() = FriendData(
        bio = "bio",
        currentAvatarImageUrl = "avatar",
        currentAvatarThumbnailImageUrl = "thumbnail",
        developerType = "none",
        displayName = "Alice",
        friendKey = "",
        id = "usr_a",
        imageUrl = "profile",
        isFriend = true,
        lastLogin = "",
        lastPlatform = "standalonewindows",
        location = "wrld_old:1",
        profilePicOverride = "",
        status = UserStatus.Offline,
        statusDescription = "",
        userIcon = "",
        pronouns = null,
    )
}
