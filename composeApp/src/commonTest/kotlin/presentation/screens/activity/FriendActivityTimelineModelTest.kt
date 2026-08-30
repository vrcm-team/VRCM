package io.github.vrcmteam.vrcm.presentation.screens.activity

import io.github.vrcmteam.vrcm.service.FriendActivityAccessType
import io.github.vrcmteam.vrcm.service.FriendActivityEvent
import io.github.vrcmteam.vrcm.service.FriendActivityEventType
import kotlin.test.Test
import kotlin.test.assertEquals

class FriendActivityTimelineModelTest {
    @Test
    fun duplicateEventsCollapseWithoutReorderingDistinctEvents() {
        val newest = event(id = 4L, type = FriendActivityEventType.Online, occurredAtMillis = 2_000L)
        val duplicate = newest.copy(id = 3L)
        val distinctAtSameTime = newest.copy(id = 2L, type = FriendActivityEventType.LocationChanged)
        val oldest = newest.copy(id = 1L, occurredAtMillis = 1_000L)

        assertEquals(
            listOf(4L, 2L, 1L),
            listOf(newest, duplicate, distinctAtSameTime, oldest)
                .deduplicateActivityEvents()
                .map(FriendActivityEvent::id),
        )
    }

    private fun event(
        id: Long,
        type: FriendActivityEventType,
        occurredAtMillis: Long,
    ) = FriendActivityEvent(
        id = id,
        friendUserId = "usr_friend",
        displayName = "Friend",
        profileImageUrl = "https://example.com/friend.png",
        type = type,
        occurredAtMillis = occurredAtMillis,
        previousValue = null,
        currentValue = null,
        worldId = "wrld_world",
        worldName = "World",
        accessType = FriendActivityAccessType.Public,
    )
}
