package io.github.vrcmteam.vrcm.network.api.friends

import io.github.vrcmteam.vrcm.network.api.friends.date.FriendData
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class FriendDataSerializationTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun limitedFriendResponseMayOmitProfileFields() {
        val friend = json.decodeFromString<FriendData>(
            """
            {
              "developerType":"none",
              "displayName":"Friend",
              "friendKey":"friend-key",
              "id":"usr_friend",
              "imageUrl":"https://example.invalid/friend.png",
              "iconUrl":"https://example.invalid/icon.png",
              "isFriend":true,
              "last_activity":null,
              "last_login":null,
              "last_platform":"standalonewindows",
              "location":"offline",
              "status":"active",
              "statusDescription":""
            }
            """.trimIndent(),
        )

        assertEquals(null, friend.bio)
        assertEquals("", friend.currentAvatarImageUrl)
        assertEquals(null, friend.currentAvatarThumbnailImageUrl)
        assertEquals(null, friend.lastActivity)
        assertEquals(null, friend.lastLogin)
        assertEquals("", friend.profilePicOverride)
        assertEquals("", friend.userIcon)
        assertEquals(null, friend.pronouns)
        assertEquals("https://example.invalid/icon.png", friend.iconUrl)
    }
}
