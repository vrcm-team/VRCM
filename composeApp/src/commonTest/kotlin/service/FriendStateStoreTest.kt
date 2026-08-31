package io.github.vrcmteam.vrcm.service

import io.github.vrcmteam.vrcm.network.api.attributes.LocationType
import io.github.vrcmteam.vrcm.network.api.attributes.UserStatus
import io.github.vrcmteam.vrcm.network.api.friends.date.FriendData
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
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
    fun newerOnlineLocationEventWinsOverInFlightActiveFriendsSnapshot() {
        val store = FriendStateStore()
        store.putFromEvent(friend("usr_a", LocationType.Offline.value))
        val activeFriendsToken = store.beginRefresh()

        store.setActiveFromEvent("usr_a", false)
        store.updateFromEvent("usr_a") { current ->
            current?.copy(location = LocationType.Private.value)
        }
        store.mergeActiveFriends(activeFriendsToken, listOf("usr_a"))

        assertEquals(LocationType.Private.value, store.snapshot.getValue("usr_a").location)
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

    @Test
    fun offlineEventForUnknownFriendLeavesATombstoneAgainstInFlightPage() {
        val store = FriendStateStore()
        val token = store.beginRefresh()

        store.updateOrRemoveFromEvent("usr_a") { existing ->
            existing?.copy(location = LocationType.Offline.value, status = UserStatus.Offline)
        }
        store.mergeRefresh(token, listOf(friend("usr_a", "wrld_old:1")), replaceUntouched = true)

        assertFalse("usr_a" in store.snapshot)
    }

    @Test
    fun fullRefreshCompletesBeforeQueuedOnlineRefreshAndKeepsOfflineFriends() = runTest {
        val store = FriendStateStore()
        val coordinator = FriendRefreshCoordinator()
        val fullRefreshStarted = CompletableDeferred<Unit>()
        val releaseFullRefresh = CompletableDeferred<Unit>()

        val fullRefresh = launch {
            coordinator.runRefresh {
                val token = store.beginRefresh()
                fullRefreshStarted.complete(Unit)
                releaseFullRefresh.await()
                store.mergeRefresh(
                    token,
                    listOf(
                        friend("usr_online", "wrld_initial:1"),
                        friend("usr_offline", LocationType.Offline.value),
                    ),
                    replaceUntouched = true,
                )
            }
        }
        fullRefreshStarted.await()

        val onlineRefreshStarted = CompletableDeferred<Unit>()
        val onlineRefresh = launch {
            coordinator.runRefresh {
                onlineRefreshStarted.complete(Unit)
                val token = store.beginRefresh()
                store.mergeRefresh(
                    token,
                    listOf(friend("usr_online", "wrld_updated:2")),
                    replaceUntouched = false,
                )
            }
        }
        runCurrent()

        assertFalse(onlineRefreshStarted.isCompleted)
        releaseFullRefresh.complete(Unit)
        fullRefresh.join()
        onlineRefresh.join()

        assertEquals("wrld_updated:2", store.snapshot.getValue("usr_online").location)
        assertEquals(LocationType.Offline.value, store.snapshot.getValue("usr_offline").location)
    }

    @Test
    fun sameAccountReauthenticationDoesNotInvalidateInFlightRefresh() {
        val store = FriendStateStore()
        val accountTracker = FriendAccountTracker()
        assertFalse(accountTracker.onAuthenticated("usr_account"))
        val token = store.beginRefresh()

        if (accountTracker.onAuthenticated("usr_account")) store.clear()

        assertTrue(
            store.mergeRefresh(
                token,
                listOf(friend("usr_friend", "wrld_fresh:1")),
                replaceUntouched = true,
            )
        )
        assertEquals("wrld_fresh:1", store.snapshot.getValue("usr_friend").location)
    }

    @Test
    fun accountSwitchInvalidatesInFlightRefresh() {
        val store = FriendStateStore()
        val accountTracker = FriendAccountTracker()
        assertFalse(accountTracker.onAuthenticated("usr_first"))
        val token = store.beginRefresh()

        if (accountTracker.onAuthenticated("usr_second")) store.clear()

        assertFalse(
            store.mergeRefresh(
                token,
                listOf(friend("usr_friend", "wrld_stale:1")),
                replaceUntouched = true,
            )
        )
        assertTrue(store.snapshot.isEmpty())
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
