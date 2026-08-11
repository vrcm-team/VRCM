package io.github.vrcmteam.vrcm.service

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FriendActivityTrackerTest {
    @Test
    fun socketPresenceIsRecordedBeforeFriendSnapshotIsAvailable() {
        val result = FriendActivityTracker().observeSocketPresence(
            friends = emptyList(),
            event = FriendSocketPresenceEvent(
                userId = "usr_friend",
                type = FriendSocketPresenceType.Offline,
                occurredAtMillis = 1_000L,
            ),
        )

        assertEquals(1, result.events.size)
        assertEquals("usr_friend", result.events.single().userId)
        assertEquals(FriendActivityEventType.Offline, result.events.single().type)
    }

    @Test
    fun snapshotPresenceChangesDoNotCreateSocketOnlyEvents() {
        val tracker = FriendActivityTracker(derivePresenceEvents = false)
        val friend = FriendActivityObservation(
            userId = "usr_friend",
            displayName = "Friend",
            profileImageUrl = "",
            location = "offline",
            status = "offline",
            statusDescription = "",
            bio = "",
            lastActivityAtMillis = 1_000L,
        )
        tracker.observe(listOf(friend), null, 1_000L)

        val refreshed = tracker.observe(
            listOf(friend.copy(location = "wrld_world:instance_a", status = "active")),
            null,
            2_000L,
        )
        val socket = tracker.observeSocketPresence(
            friends = listOf(friend.copy(location = "wrld_world:instance_a", status = "active")),
            event = FriendSocketPresenceEvent(
                userId = friend.userId,
                type = FriendSocketPresenceType.Online,
                occurredAtMillis = 3_000L,
            ),
        )

        assertTrue(refreshed.events.none { it.type == FriendActivityEventType.Online })
        assertEquals(listOf(FriendActivityEventType.Online), socket.events.map { it.type })
    }

    @Test
    fun comingOnlineKeepsLocationAndCurrentStatusInOneEvent() {
        val tracker = FriendActivityTracker()
        val offlineFriend = FriendActivityObservation(
            userId = "usr_friend",
            displayName = "Friend",
            profileImageUrl = "",
            location = "offline",
            status = "offline",
            statusDescription = "",
            bio = "",
            lastActivityAtMillis = null,
        )
        tracker.observe(
            friends = listOf(offlineFriend),
            selfLocation = null,
            nowMillis = 1_000L,
        )

        val onlineFriend = offlineFriend.copy(
            location = "wrld_world:instance_a~friends(usr_owner)",
            status = "join me",
            statusDescription = "Come over",
        )
        tracker.observe(listOf(onlineFriend), null, 2_000L)
        val result = tracker.observeSocketPresence(
            friends = listOf(onlineFriend),
            event = FriendSocketPresenceEvent(
                userId = onlineFriend.userId,
                type = FriendSocketPresenceType.Online,
                occurredAtMillis = 2_000L,
            ),
        )

        assertEquals(1, result.events.size)
        assertEquals(FriendActivityEventType.Online, result.events.single().type)
        assertEquals("wrld_world", result.events.single().worldId)
        assertEquals(FriendActivityAccessType.Friends, result.events.single().accessType)
        assertEquals("join me\nCome over", result.events.single().currentValue)
    }

    @Test
    fun goingOfflineKeepsPreviousLocation() {
        val tracker = FriendActivityTracker()
        val onlineFriend = FriendActivityObservation(
            userId = "usr_friend",
            displayName = "Friend",
            profileImageUrl = "",
            location = "wrld_world:instance_a~hidden(usr_owner)",
            status = "active",
            statusDescription = "Available",
            bio = "",
            lastActivityAtMillis = null,
        )
        tracker.observe(
            friends = listOf(onlineFriend),
            selfLocation = null,
            nowMillis = 1_000L,
        )

        val offlineFriend = onlineFriend.copy(
            location = "offline",
            status = "offline",
            statusDescription = "",
        )
        tracker.observe(listOf(offlineFriend), null, 2_000L)
        val result = tracker.observeSocketPresence(
            friends = listOf(offlineFriend),
            event = FriendSocketPresenceEvent(
                userId = offlineFriend.userId,
                type = FriendSocketPresenceType.Offline,
                occurredAtMillis = 2_000L,
            ),
        )

        assertEquals(1, result.events.size)
        assertEquals(FriendActivityEventType.Offline, result.events.single().type)
        assertEquals("wrld_world", result.events.single().worldId)
        assertEquals(FriendActivityAccessType.FriendsPlus, result.events.single().accessType)
    }

    @Test
    fun baselineSharedInstanceStartsUnannouncedSession() {
        val tracker = FriendActivityTracker()

        val baseline = tracker.observe(
            friends = listOf(
                FriendActivityObservation(
                    userId = "usr_friend",
                    displayName = "Friend",
                    profileImageUrl = "",
                    location = "wrld_world:instance_a",
                    status = "active",
                    statusDescription = "",
                    bio = "",
                    lastActivityAtMillis = null,
                )
            ),
            selfLocation = "wrld_world:instance_a",
            nowMillis = 1_000L,
        )

        assertTrue(baseline.events.isEmpty())
        assertEquals(
            listOf(
                FriendMeetingChange.Started(
                    userId = "usr_friend",
                    occurredAtMillis = 1_000L,
                    worldId = "wrld_world",
                    accessType = FriendActivityAccessType.Public,
                    announce = false,
                )
            ),
            baseline.meetings,
        )
    }

    @Test
    fun locationAndStatusChangesShareOneTimelineEvent() {
        val tracker = FriendActivityTracker()
        val baselineFriend = FriendActivityObservation(
            userId = "usr_friend",
            displayName = "Friend",
            profileImageUrl = "https://example.com/friend.png",
            location = "wrld_first:instance_a",
            status = "active",
            statusDescription = "Available",
            bio = "First line\nShared line",
            lastActivityAtMillis = 900L,
        )
        tracker.observe(
            friends = listOf(baselineFriend),
            selfLocation = null,
            nowMillis = 1_000L,
        )

        val changes = tracker.observe(
            friends = listOf(
                baselineFriend.copy(
                    location = "wrld_second:private_instance~private(usr_owner)",
                    status = "join me",
                    statusDescription = "Come over",
                    bio = "Second line\nShared line",
                )
            ),
            selfLocation = null,
            nowMillis = 2_000L,
        )

        assertEquals(
            listOf(
                FriendActivityEventType.LocationChanged,
                FriendActivityEventType.BioChanged,
            ),
            changes.events.map(FriendActivityEventDraft::type),
        )
        assertEquals("wrld_second", changes.events.first().worldId)
        assertEquals(FriendActivityAccessType.Invite, changes.events.first().accessType)
        assertEquals("wrld_first", changes.events.first().previousValue)
        assertEquals("join me\nCome over", changes.events.first().currentValue)
        assertEquals("First line\nShared line", changes.events[1].previousValue)
        assertEquals("Second line\nShared line", changes.events[1].currentValue)
    }

    @Test
    fun bioChangesAreRecordedWithoutAnInGameLocation() {
        val tracker = FriendActivityTracker()
        val baselineFriend = FriendActivityObservation(
            userId = "usr_friend",
            displayName = "Friend",
            profileImageUrl = "",
            location = "offline",
            status = "offline",
            statusDescription = "",
            bio = "Old bio",
            lastActivityAtMillis = null,
        )
        tracker.observe(
            friends = listOf(baselineFriend),
            selfLocation = null,
            nowMillis = 1_000L,
        )

        val changes = tracker.observe(
            friends = listOf(baselineFriend.copy(bio = "New bio")),
            selfLocation = null,
            nowMillis = 2_000L,
        )

        assertEquals(listOf(FriendActivityEventType.BioChanged), changes.events.map(FriendActivityEventDraft::type))
        assertEquals("Old bio", changes.events.single().previousValue)
        assertEquals("New bio", changes.events.single().currentValue)
    }

    @Test
    fun onlyExactSharedInstanceProducesCompletedTogetherSession() {
        val tracker = FriendActivityTracker()
        val friend = FriendActivityObservation(
            userId = "usr_friend",
            displayName = "Friend",
            profileImageUrl = "https://example.com/friend.png",
            location = "wrld_world:instance_b",
            status = "active",
            statusDescription = "",
            bio = "Hello",
            lastActivityAtMillis = 900L,
        )

        val baseline = tracker.observe(
            friends = listOf(friend),
            selfLocation = "wrld_world:instance_a",
            nowMillis = 1_000L,
        )

        assertTrue(baseline.events.isEmpty())
        assertTrue(baseline.meetings.isEmpty())

        val met = tracker.observe(
            friends = listOf(friend.copy(location = "wrld_world:instance_a")),
            selfLocation = "wrld_world:instance_a",
            nowMillis = 2_000L,
        )

        assertEquals(
            listOf(
                FriendMeetingChange.Started(
                    userId = "usr_friend",
                    occurredAtMillis = 2_000L,
                    worldId = "wrld_world",
                    accessType = FriendActivityAccessType.Public,
                    announce = true,
                )
            ),
            met.meetings,
        )

        val left = tracker.observe(
            friends = listOf(friend.copy(location = "wrld_world:instance_a")),
            selfLocation = "wrld_world:instance_c",
            nowMillis = 5_000L,
        )

        assertEquals(
            listOf(
                FriendMeetingChange.Ended(
                    userId = "usr_friend",
                    occurredAtMillis = 5_000L,
                    durationMillis = 3_000L,
                )
            ),
            left.meetings,
        )
    }

    @Test
    fun missingFriendEndsMeetingAndClearsRuntimeBaseline() {
        val tracker = FriendActivityTracker()
        val friend = FriendActivityObservation(
            userId = "usr_friend",
            displayName = "Friend",
            profileImageUrl = "",
            location = "wrld_world:instance_a",
            status = "active",
            statusDescription = "",
            bio = "",
            lastActivityAtMillis = null,
        )
        tracker.observe(
            friends = listOf(friend),
            selfLocation = friend.location,
            nowMillis = 1_000L,
        )

        val missing = tracker.observe(
            friends = emptyList(),
            selfLocation = friend.location,
            nowMillis = 4_000L,
        )
        val returned = tracker.observe(
            friends = listOf(friend),
            selfLocation = friend.location,
            nowMillis = 5_000L,
        )

        assertEquals(
            listOf(
                FriendMeetingChange.Ended(
                    userId = friend.userId,
                    occurredAtMillis = 4_000L,
                    durationMillis = 3_000L,
                )
            ),
            missing.meetings,
        )
        assertEquals(
            listOf(
                FriendMeetingChange.Started(
                    userId = friend.userId,
                    occurredAtMillis = 5_000L,
                    worldId = "wrld_world",
                    accessType = FriendActivityAccessType.Public,
                    announce = false,
                )
            ),
            returned.meetings,
        )
        assertTrue(returned.events.isEmpty())
    }
}
