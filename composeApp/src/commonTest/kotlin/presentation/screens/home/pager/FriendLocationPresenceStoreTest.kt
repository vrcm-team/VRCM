package io.github.vrcmteam.vrcm.presentation.screens.home.pager

import io.github.vrcmteam.vrcm.network.api.attributes.LocationType
import io.github.vrcmteam.vrcm.network.api.attributes.UserStatus
import io.github.vrcmteam.vrcm.network.api.friends.date.FriendData
import io.github.vrcmteam.vrcm.service.FriendUpdateEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FriendLocationPresenceStoreTest {
    @Test
    fun pagesAndIncrementalUpdatesPreserveOtherFriendsInTheRoom() {
        val store = FriendLocationPresenceStore()
        val room = "wrld_room:1"
        store.addPage(listOf(friend("usr_a", room)))
        store.addPage(listOf(friend("usr_b", room)))

        store.apply(FriendUpdateEvent.LocationChanged(friend("usr_a", "wrld_other:2")))

        val snapshot = store.snapshot()
        assertEquals(listOf("usr_b"), snapshot.instances.getValue(room).friends.map(FriendData::id))
        assertEquals(listOf("usr_a"), snapshot.instances.getValue("wrld_other:2").friends.map(FriendData::id))
    }

    @Test
    fun reconciliationHappensAgainstIdsFromAllPages() {
        val store = FriendLocationPresenceStore()
        store.addPage(listOf(friend("usr_stale", "wrld_old:1")))
        store.addPage(listOf(friend("usr_a", "wrld_room:1")))
        store.addPage(listOf(friend("usr_b", "wrld_room:1")))

        store.reconcile(setOf("usr_a", "usr_b"))

        assertEquals(
            setOf("usr_a", "usr_b"),
            store.snapshot().instances.getValue("wrld_room:1").friends.map(FriendData::id).toSet(),
        )
        assertTrue("wrld_old:1" !in store.snapshot().instances)
    }

    @Test
    fun eventReceivedDuringRefreshWinsOverOlderPageData() {
        val store = FriendLocationPresenceStore()
        store.beginRefresh()
        store.addPage(listOf(friend("usr_a", "wrld_old:1")))
        store.apply(FriendUpdateEvent.LocationChanged(friend("usr_a", "wrld_new:2")))

        store.addPage(listOf(friend("usr_a", "wrld_old:1")))
        store.finishRefresh(setOf("usr_a"), reconcile = true)

        val snapshot = store.snapshot()
        assertTrue("wrld_old:1" !in snapshot.instances)
        assertEquals(listOf("usr_a"), snapshot.instances.getValue("wrld_new:2").friends.map(FriendData::id))
    }

    @Test
    fun offlineEventDuringRefreshCannotBeReaddedByLaterPage() {
        val store = FriendLocationPresenceStore()
        store.beginRefresh()
        store.apply(FriendUpdateEvent.Offline("usr_a"))

        store.addPage(listOf(friend("usr_a", "wrld_old:1")))
        store.finishRefresh(setOf("usr_a"), reconcile = true)

        assertTrue(store.snapshot().instances.isEmpty())
    }

    @Test
    fun simultaneousTravelersKeepIndependentLoadingState() {
        val store = FriendLocationPresenceStore()
        val destination = "wrld_destination:3"

        store.apply(
            FriendUpdateEvent.LocationChanged(
                friend("usr_a", LocationType.Traveling.value, destination)
            )
        )
        store.apply(
            FriendUpdateEvent.LocationChanged(
                friend("usr_b", LocationType.Traveling.value, destination)
            )
        )

        val group = store.snapshot().instances.getValue(destination)
        assertEquals(setOf("usr_a", "usr_b"), group.friends.map(FriendData::id).toSet())
        assertEquals(setOf("usr_a", "usr_b"), group.travelingIds)
    }

    @Test
    fun onlineEventClearsStaleActiveStateBeforePrivateLocationIsGrouped() {
        val store = FriendLocationPresenceStore()
        store.apply(FriendUpdateEvent.Active(friend("usr_a", LocationType.Offline.value)))

        store.apply(FriendUpdateEvent.Online(friend("usr_a", LocationType.Private.value)))

        val snapshot = store.snapshot()
        assertTrue(snapshot.offline.isEmpty())
        assertEquals(listOf("usr_a"), snapshot.private.map(FriendData::id))
    }

    private fun friend(
        id: String,
        location: String,
        travelingToLocation: String = "",
    ) = FriendData(
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
        travelingToLocation = travelingToLocation,
        profilePicOverride = "",
        status = UserStatus.Active,
        statusDescription = "",
        userIcon = "",
        pronouns = null,
    )
}
