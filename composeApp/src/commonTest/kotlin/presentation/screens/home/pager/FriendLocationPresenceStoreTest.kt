package io.github.vrcmteam.vrcm.presentation.screens.home.pager

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import io.github.vrcmteam.vrcm.core.shared.AccountSessionToken
import io.github.vrcmteam.vrcm.network.api.attributes.LocationType
import io.github.vrcmteam.vrcm.network.api.attributes.UserStatus
import io.github.vrcmteam.vrcm.network.api.friends.date.FriendData
import io.github.vrcmteam.vrcm.presentation.screens.home.data.FriendLocation
import io.github.vrcmteam.vrcm.service.AccountFriendUpdateEvent
import io.github.vrcmteam.vrcm.service.FriendUpdateEvent
import io.github.vrcmteam.vrcm.service.FriendPresence
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class FriendLocationPresenceStoreTest {
    @Test
    fun queuedUpdateFromPreviousSessionIsRejectedAfterAccountSwitch() {
        val store = FriendLocationPresenceStore()
        val publishedState = FriendLocationPublishedState()
        val gate = FriendUpdateSessionGate()
        val firstSession = AccountSessionToken("usr_account_a", 1)
        val secondSession = AccountSessionToken("usr_account_b", 2)
        gate.activate(firstSession)
        val oldFriend = friend("usr_a", "wrld_old:1")
        publishedState.locationMap[LocationType.Instance] = mutableStateListOf(
            FriendLocation(
                location = oldFriend.location,
                friends = mutableStateMapOf(oldFriend.id to mutableStateOf(oldFriend)),
            )
        )
        publishedState.publishIndex()
        val delayedUpdate = AccountFriendUpdateEvent(
            sessionToken = firstSession,
            event = FriendUpdateEvent.LocationChanged(oldFriend),
        )

        gate.activate(secondSession)
        store.clear()
        publishedState.clear()
        if (gate.accepts(delayedUpdate.sessionToken)) {
            store.apply(delayedUpdate.event)
            publishedState.locationMap[LocationType.Instance] = mutableStateListOf(
                FriendLocation(
                    location = oldFriend.location,
                    friends = mutableStateMapOf(oldFriend.id to mutableStateOf(oldFriend)),
                )
            )
            publishedState.publishIndex()
        }

        val snapshot = store.snapshot()
        assertTrue(snapshot.offline.isEmpty())
        assertTrue(snapshot.web.isEmpty())
        assertTrue(snapshot.private.isEmpty())
        assertTrue(snapshot.instances.isEmpty())
        assertTrue(publishedState.locationMap.isEmpty())
        assertTrue(publishedState.locationsByUser.value.isEmpty())
    }

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

    @Test
    fun onlineEventBeforeOldActiveSnapshotStillWins() {
        val store = FriendLocationPresenceStore()
        store.apply(FriendUpdateEvent.Active(friend("usr_a", LocationType.Offline.value)))
        store.beginRefresh()

        store.apply(FriendUpdateEvent.Online(friend("usr_a", LocationType.Private.value)))
        store.setActiveFriends(listOf("usr_a"))
        store.finishRefresh(setOf("usr_a"), reconcile = true)

        val snapshot = store.snapshot()
        assertTrue(snapshot.offline.isEmpty())
        assertEquals(listOf("usr_a"), snapshot.private.map(FriendData::id))
    }

    @Test
    fun privateOwnPresenceIsEligibleForPrivateCategory() {
        assertEquals(
            LocationType.Private.value,
            ownEffectiveLocation(FriendPresence(LocationType.Private.value)),
        )
        assertEquals(null, ownEffectiveLocation(FriendPresence("", "")))
    }

    @Test
    fun statusUpdateReusesPrivateCategoryCardAndUserState() {
        val publishedState = FriendLocationPublishedState()
        val active = friend("usr_self", LocationType.Private.value)
        publishedState.syncSimpleLocation(LocationType.Private, listOf(active))
        val card = publishedState.locationMap.getValue(LocationType.Private).first()
        val userState = card.friends.getValue(active.id)

        publishedState.syncSimpleLocation(
            LocationType.Private,
            listOf(active.copy(status = UserStatus.Busy)),
        )

        assertSame(card, publishedState.locationMap.getValue(LocationType.Private).first())
        assertSame(userState, card.friends.getValue(active.id))
        assertEquals(UserStatus.Busy, userState.value.status)
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
