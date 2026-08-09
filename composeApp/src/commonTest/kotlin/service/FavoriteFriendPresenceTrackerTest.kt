package io.github.vrcmteam.vrcm.service

import io.github.vrcmteam.vrcm.network.api.attributes.UserStatus
import io.github.vrcmteam.vrcm.network.api.friends.date.FriendData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FavoriteFriendPresenceTrackerTest {
    /**
     * Cold start order: the locally restored snapshot arrives first with presence forced to
     * offline, then the real friend list lands. Taking a baseline from the restored snapshot would
     * report every favorited friend as newly online.
     */
    @Test
    fun restoredOfflineSnapshotDoesNotBecomeThePresenceBaseline() {
        val tracker = FavoriteFriendPresenceTracker()
        val favorites = setOf("usr_friend")
        val restored = mapOf("usr_friend" to friend(location = "offline"))

        tracker.updateFavorites(favorites, restored, presenceTrusted = false)

        val refreshed = mapOf("usr_friend" to friend(location = "wrld_world:instance_a"))
        assertTrue(
            tracker.observe(refreshed).isEmpty(),
            "a snapshot restored from cache must not produce online transitions",
        )
    }

    /**
     * Once the first full refresh has landed the snapshot is trusted, so the baseline is taken from
     * it and only later changes are reported.
     */
    @Test
    fun transitionsAreReportedOnceTheBaselineComesFromARefreshedSnapshot() {
        val tracker = FavoriteFriendPresenceTracker()
        val favorites = setOf("usr_friend")
        val offline = mapOf("usr_friend" to friend(location = "offline"))

        tracker.updateFavorites(favorites, offline, presenceTrusted = true)

        val online = mapOf("usr_friend" to friend(location = "wrld_world:instance_a"))
        val transitions = tracker.observe(online)

        assertEquals(1, transitions.size)
        assertEquals("usr_friend", transitions.single().userId)
        assertTrue(transitions.single().inGame)
    }

    private fun friend(location: String, displayName: String = "Friend") = FriendData(
        bio = null,
        currentAvatarImageUrl = "",
        currentAvatarThumbnailImageUrl = "",
        developerType = "none",
        displayName = displayName,
        friendKey = "",
        id = "usr_friend",
        imageUrl = "",
        isFriend = true,
        lastLogin = "",
        lastPlatform = "standalonewindows",
        location = location,
        travelingToLocation = "",
        profilePicOverride = "",
        status = UserStatus.Active,
        statusDescription = "",
        userIcon = "",
        pronouns = null,
    )
}
