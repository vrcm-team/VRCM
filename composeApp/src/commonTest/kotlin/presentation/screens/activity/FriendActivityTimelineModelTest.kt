package io.github.vrcmteam.vrcm.presentation.screens.activity

import io.github.vrcmteam.vrcm.core.shared.AccountSessionToken
import io.github.vrcmteam.vrcm.testing.MainDispatcherTest
import io.github.vrcmteam.vrcm.service.FriendActivityAccessType
import io.github.vrcmteam.vrcm.service.FriendActivityEvent
import io.github.vrcmteam.vrcm.service.FriendActivityEventType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class FriendActivityTimelineModelTest : MainDispatcherTest() {
    @Test
    fun newHeadEventsDoNotMoveTheAppendCursorOrDuplicateLoadedEvents() = runTest {
        val source = FakeTimelineSource(
            initialHead = listOf(event(5), event(4), event(3), event(2)),
        )
        source.appendResponses += flowOf(listOf(event(2), event(1)))
        val model = FriendActivityTimelineModel(source, pageSize = 3)
        advanceUntilIdle()

        val first = assertIs<FriendActivityTimelineState.Content>(model.state.value)
        assertEquals(listOf(5L, 4L, 3L), first.events.map(FriendActivityEvent::id))
        assertTrue(first.hasMore)

        model.loadMore()
        advanceUntilIdle()
        source.head.value = listOf(event(6), event(5), event(4), event(3))
        advanceUntilIdle()

        val loaded = assertIs<FriendActivityTimelineState.Content>(model.state.value)
        assertEquals(listOf(6L, 5L, 4L, 3L, 2L, 1L), loaded.events.map(FriendActivityEvent::id))
        assertFalse(loaded.hasMore)
        assertEquals(FriendActivityTimelineCursor(3_000L, 3L), source.requestedCursors.single())
    }

    @Test
    fun appendFailureKeepsContentAndRetryContinuesFromTheSameCursor() = runTest {
        val source = FakeTimelineSource(
            initialHead = listOf(event(4), event(3), event(2)),
        )
        source.appendResponses += flow { throw IllegalStateException("read failed") }
        source.appendResponses += flowOf(listOf(event(1)))
        val model = FriendActivityTimelineModel(source, pageSize = 2)
        advanceUntilIdle()

        model.loadMore()
        advanceUntilIdle()
        val failed = assertIs<FriendActivityTimelineState.Content>(model.state.value)
        assertEquals(listOf(4L, 3L), failed.events.map(FriendActivityEvent::id))
        assertTrue(failed.loadMoreError)
        assertFalse(failed.isLoadingMore)

        model.retryLoadMore()
        advanceUntilIdle()
        val recovered = assertIs<FriendActivityTimelineState.Content>(model.state.value)
        assertEquals(listOf(4L, 3L, 1L), recovered.events.map(FriendActivityEvent::id))
        assertFalse(recovered.loadMoreError)
        assertFalse(recovered.hasMore)
        assertEquals(2, source.requestedCursors.size)
        assertEquals(source.requestedCursors.first(), source.requestedCursors.last())
    }

    @Test
    fun filterChangeResetsOldItemsBeforeTheNewQueryCompletes() = runTest {
        val source = FakeTimelineSource(listOf(event(2), event(1)))
        val locationHead = MutableStateFlow(listOf(event(8, FriendActivityEventType.LocationChanged)))
        source.filteredHeads[setOf(FriendActivityEventType.LocationChanged)] = locationHead
        val model = FriendActivityTimelineModel(source, pageSize = 2)
        advanceUntilIdle()

        model.selectFilter(FriendActivityTimelineFilter.Location)
        advanceUntilIdle()

        val filtered = assertIs<FriendActivityTimelineState.Content>(model.state.value)
        assertEquals(listOf(8L), filtered.events.map(FriendActivityEvent::id))
        assertEquals(FriendActivityTimelineFilter.Location, model.filter.value)
    }

    @Test
    fun initialFailureCanRetryWithoutKeepingTheFailedGeneration() = runTest {
        val source = FakeTimelineSource(emptyList())
        source.filteredHeads[emptySet()] = flow { throw IllegalStateException("initial read failed") }
        val model = FriendActivityTimelineModel(source, pageSize = 2)
        advanceUntilIdle()
        assertIs<FriendActivityTimelineState.Error>(model.state.value)

        source.filteredHeads[emptySet()] = flowOf(listOf(event(2), event(1)))
        model.retry()
        advanceUntilIdle()

        val recovered = assertIs<FriendActivityTimelineState.Content>(model.state.value)
        assertEquals(listOf(2L, 1L), recovered.events.map(FriendActivityEvent::id))
        assertFalse(recovered.hasMore)
    }

    @Test
    fun duplicateEventsAreSortedThenCollapsedByBusinessIdentity() {
        val newest = event(4, FriendActivityEventType.Online, 2_000L)
        val duplicate = newest.copy(id = 3L)
        val distinctAtSameTime = newest.copy(id = 2L, type = FriendActivityEventType.LocationChanged)
        val oldest = newest.copy(id = 1L, occurredAtMillis = 1_000L)

        assertEquals(
            listOf(4L, 2L, 1L),
            listOf(oldest, duplicate, distinctAtSameTime, newest)
                .deduplicateActivityEvents()
                .map(FriendActivityEvent::id),
        )
    }

    @Test
    fun worldNavigationOnlyAllowsPublicValidWorldIds() {
        val public = event(1).copy(worldId = "wrld_public-world", accessType = FriendActivityAccessType.Public)
        assertEquals("wrld_public-world", public.navigableWorldId())

        listOf(
            FriendActivityAccessType.FriendsPlus,
            FriendActivityAccessType.Friends,
            FriendActivityAccessType.InvitePlus,
            FriendActivityAccessType.Invite,
            FriendActivityAccessType.Group,
            FriendActivityAccessType.Unknown,
        ).forEach { access ->
            assertNull(public.copy(accessType = access).navigableWorldId())
        }
        assertNull(public.copy(accessType = null).navigableWorldId())
        assertNull(public.copy(worldId = "usr_not-a-world").navigableWorldId())
        assertNull(public.copy(worldId = "wrld_bad value").navigableWorldId())
    }

    private class FakeTimelineSource(
        initialHead: List<FriendActivityEvent>,
    ) : FriendActivityTimelineSource {
        override val sessionTokens = MutableStateFlow<AccountSessionToken?>(
            AccountSessionToken("usr_owner", 1L)
        )
        val head = MutableStateFlow(initialHead)
        val filteredHeads = mutableMapOf<Set<FriendActivityEventType>, Flow<List<FriendActivityEvent>>>()
        val appendResponses = ArrayDeque<Flow<List<FriendActivityEvent>>>()
        val requestedCursors = mutableListOf<FriendActivityTimelineCursor>()

        override fun observeHead(types: Set<FriendActivityEventType>, limit: Int) =
            filteredHeads[types] ?: head

        override fun observeBefore(
            types: Set<FriendActivityEventType>,
            cursor: FriendActivityTimelineCursor,
            limit: Int,
        ): Flow<List<FriendActivityEvent>> {
            requestedCursors += cursor
            return appendResponses.removeFirst()
        }
    }

    private fun event(
        id: Long,
        type: FriendActivityEventType = FriendActivityEventType.Online,
        occurredAtMillis: Long = id * 1_000L,
    ) = FriendActivityEvent(
        id = id,
        friendUserId = "usr_friend_$id",
        displayName = "Friend $id",
        profileImageUrl = "https://example.com/friend.png",
        type = type,
        occurredAtMillis = occurredAtMillis,
        previousValue = null,
        currentValue = null,
        worldId = "wrld_world-$id",
        worldName = "World $id",
        accessType = FriendActivityAccessType.Public,
    )
}
