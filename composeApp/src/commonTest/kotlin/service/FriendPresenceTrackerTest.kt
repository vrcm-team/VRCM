package io.github.vrcmteam.vrcm.service

import io.github.vrcmteam.vrcm.network.api.attributes.UserStatus
import io.github.vrcmteam.vrcm.network.api.friends.date.FriendData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FriendPresenceTrackerTest {
    @Test
    fun firstTrustedSnapshotEstablishesBaselineWithoutNotification() {
        val tracker = FriendPresenceTracker()
        val refreshed = mapOf("usr_friend" to friend(location = "wrld_world:instance_a"))

        assertTrue(
            tracker.observe(refreshed).isEmpty(),
            "the first trusted full-list snapshot must only establish the presence baseline",
        )
    }

    /**
     * Once the first full refresh has landed the snapshot is trusted, so the baseline is taken from
     * it and only later changes are reported.
     */
    @Test
    fun transitionsAreReportedOnceTheBaselineComesFromARefreshedSnapshot() {
        val tracker = FriendPresenceTracker()
        val offline = mapOf("usr_friend" to friend(location = "offline"))

        tracker.observe(offline)

        val online = mapOf("usr_friend" to friend(location = "wrld_world:instance_a"))
        val transitions = tracker.observe(online)

        assertEquals(1, transitions.size)
        assertEquals("usr_friend", transitions.single().userId)
        assertTrue(transitions.single().inGame)
    }

    @Test
    fun trustedBaselineTracksGroupedAndUngroupedFriendsBeforeFiltering() {
        val tracker = FriendPresenceTracker()
        val groupedUserId = "usr_grouped"
        val ungroupedUserId = "usr_ungrouped"
        val baseline = mapOf(
            groupedUserId to friend(groupedUserId, location = "wrld_world:instance_a"),
            ungroupedUserId to friend(ungroupedUserId, location = "offline"),
        )

        tracker.observe(baseline)

        val transitions = tracker.observe(
            mapOf(
                groupedUserId to friend(groupedUserId, location = "offline"),
                ungroupedUserId to friend(ungroupedUserId, location = "wrld_world:instance_b"),
            )
        )
        val groupIdsByUser = mapOf(groupedUserId to setOf("grp_muted"))
        val defaultAlerts = transitions.filter { transition ->
            FriendPresenceFilter.Default.allows(
                transition.userId,
                groupIdsByUser[transition.userId].orEmpty(),
            )
        }
        assertEquals(
            setOf(groupedUserId, ungroupedUserId),
            defaultAlerts.mapTo(mutableSetOf()) { it.userId },
        )

        val groupBlacklist = FriendPresenceFilter(
            mode = PresenceFilterMode.Blacklist,
            groupIds = setOf("grp_muted"),
        )
        val blacklistAlerts = transitions.filter { transition ->
            groupBlacklist.allows(
                transition.userId,
                groupIdsByUser[transition.userId].orEmpty(),
            )
        }
        assertEquals(listOf(ungroupedUserId), blacklistAlerts.map { it.userId })
    }

    private fun friend(
        userId: String = "usr_friend",
        location: String,
        displayName: String = "Friend",
    ) = FriendData(
        bio = null,
        currentAvatarImageUrl = "",
        currentAvatarThumbnailImageUrl = "",
        developerType = "none",
        displayName = displayName,
        friendKey = "",
        id = userId,
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
