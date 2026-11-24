package io.github.vrcmteam.vrcm.service

import io.github.vrcmteam.vrcm.core.shared.AccountSessionToken
import io.github.vrcmteam.vrcm.network.api.attributes.UserStatus
import io.github.vrcmteam.vrcm.network.api.friends.date.FriendData
import io.github.vrcmteam.vrcm.network.websocket.data.content.FriendUpdateContent
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AvatarActivityTest {
    private val token = AccountSessionToken("usr_owner", 1)
    private val friend = FriendData(
        developerType = "none",
        displayName = "Alice",
        friendKey = "",
        id = "usr_friend",
        isFriend = true,
        location = "private",
        status = UserStatus.Active,
        statusDescription = "Exploring",
        bannerType = "avatarBanner",
        bannerUrl = "https://api.vrchat.cloud/api/1/image/file_old/1/256",
        profileIconUrl = "https://api.vrchat.cloud/api/1/image/file_icon/1/256",
    )

    @Test
    fun partialAvatarBannerUpdateDetectsSwapAndPreservesPresence() {
        val update = Json.decodeFromString<FriendUpdateContent>(
            """{"user":{"id":"usr_friend","bannerUrl":"https://api.vrchat.cloud/api/1/image/file_new/1/256"}}""",
        )
        val merged = update.user.mergeWith(friend)
        val activity = assertNotNull(merged.avatarActivitySince(friend, token, 100L))

        assertFalse(activity.needsIconValidation)
        assertEquals(friend.bannerUrl, activity.event.previousValue)
        assertEquals(merged.bannerUrl, activity.event.currentValue)
        assertEquals(friend.statusDescription, merged.statusDescription)
        assertNull(update.user.mergeWith(merged).avatarActivitySince(merged, token, 200L))
    }

    @Test
    fun customBannersAndImageRenditionsAreIgnoredButAChangedIconIsChecked() {
        val customBanner = friend.copy(
            bannerType = "customImage",
            bannerUrl = "https://api.vrchat.cloud/api/1/image/file_custom/1/256",
            profileIconUrl = "https://api.vrchat.cloud/api/1/file/file_icon/1/file",
        )
        assertNull(customBanner.avatarActivitySince(friend, token, 100L))
        assertNull(friend.avatarActivitySince(null, token, 100L))

        val icon = customBanner.copy(profileIconUrl = "https://api.vrchat.cloud/api/1/image/file_other/1/256")
        val activity = assertNotNull(icon.avatarActivitySince(customBanner, token, 200L))
        assertTrue(activity.needsIconValidation)
        assertEquals(icon.profileIconUrl, activity.event.currentValue)
    }
}
