package io.github.vrcmteam.vrcm.network.api.users.data

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class UserIconUrlSerializationTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun searchUserUsesIconUrlWhenLegacyIconFieldsAreMissing() {
        val user = json.decodeFromString<SearchUserData>(
            """
            {
              "developerType":"none",
              "displayName":"Search user",
              "iconUrl":"https://example.invalid/search-icon.png",
              "id":"usr_search",
              "isFriend":false,
              "last_platform":"standalonewindows",
              "status":"offline",
              "statusDescription":"",
              "tags":[]
            }
            """.trimIndent(),
        )

        assertEquals("https://example.invalid/search-icon.png", user.iconUrl)
    }

    @Test
    fun mutualFriendUsesIconUrlBeforeImageUrl() {
        val user = json.decodeFromString<MutualFriendData>(
            """
            {
              "currentAvatarImageUrl":"",
              "displayName":"Mutual friend",
              "iconUrl":"https://example.invalid/mutual-icon.png",
              "id":"usr_mutual",
              "imageUrl":"https://example.invalid/legacy-image.png",
              "status":"offline",
              "statusDescription":""
            }
            """.trimIndent(),
        )

        assertEquals("https://example.invalid/mutual-icon.png", user.iconUrl)
    }
}
