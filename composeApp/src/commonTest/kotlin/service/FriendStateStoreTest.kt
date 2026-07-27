package io.github.vrcmteam.vrcm.service

import io.github.vrcmteam.vrcm.network.api.attributes.LocationType
import io.github.vrcmteam.vrcm.network.api.attributes.UserStatus
import io.github.vrcmteam.vrcm.network.api.friends.date.FriendData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class FriendStateStoreTest {
    @Test
    fun locationEventDuringFullRefreshWinsOverOldPageWhileUntouchedFriendsRefresh() {
        val store = FriendStateStore()
        store.putFromEvent(friend("usr_a", "wrld_old:1"))
        store.putFromEvent(friend("usr_b", "wrld_old:1"))
        val token = store.beginRefresh()

        store.putFromEvent(friend("usr_a", "wrld_new:2"))
        store.mergeRefresh(
            token,
            listOf(friend("usr_a", "wrld_old:1"), friend("usr_b", "wrld_fresh:3")),
            replaceUntouched = true,
        )

        assertEquals("wrld_new:2", store.snapshot.getValue("usr_a").location)
        assertEquals("wrld_fresh:3", store.snapshot.getValue("usr_b").location)
    }

    @Test
    fun offlineEventDuringFullRefreshWinsOverOldOnlinePage() {
        val store = FriendStateStore()
        store.putFromEvent(friend("usr_a", "wrld_old:1"))
        val token = store.beginRefresh()

        store.updateFromEvent("usr_a") {
            it?.copy(location = LocationType.Offline.value, status = UserStatus.Offline)
        }
        store.mergeRefresh(token, listOf(friend("usr_a", "wrld_old:1")), replaceUntouched = true)

        assertEquals(LocationType.Offline.value, store.snapshot.getValue("usr_a").location)
        assertEquals(UserStatus.Offline, store.snapshot.getValue("usr_a").status)
    }

    @Test
    fun deleteEventDuringFullRefreshLeavesATombstoneAgainstOldPage() {
        val store = FriendStateStore()
        store.putFromEvent(friend("usr_a", "wrld_old:1"))
        val token = store.beginRefresh()

        store.removeFromEvent("usr_a")
        store.mergeRefresh(token, listOf(friend("usr_a", "wrld_old:1")), replaceUntouched = true)

        assertFalse("usr_a" in store.snapshot)
    }

    private fun friend(id: String, location: String) = FriendData(
        bio = null,
        currentAvatarImageUrl = "",
        currentAvatarThumbnailImageUrl = "",
        developerType = "none",
        displayName = id,
        friendKey = "",
        id = id,
        imageUrl = "",
        isFriend = true,
        lastLogin = "",
        lastPlatform = "standalonewindows",
        location = location,
        profilePicOverride = "",
        status = UserStatus.Active,
        statusDescription = "",
        userIcon = "",
        pronouns = null,
    )
}
