package io.github.vrcmteam.vrcm.presentation.screens.activity

import io.github.vrcmteam.vrcm.core.shared.AccountSessionToken
import io.github.vrcmteam.vrcm.testing.MainDispatcherTest
import io.github.vrcmteam.vrcm.service.FriendActivityAccessType
import io.github.vrcmteam.vrcm.service.FriendActivityEvent
import io.github.vrcmteam.vrcm.service.FriendActivityEventType
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
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
    fun newHeadEventsKeepLoadedWindowBoundedAndMoveAppendCursorWithoutGaps() = runTest {
        val source = FakeTimelineSource(
            initialHead = listOf(event(5), event(4), event(3), event(2)),
        )
        source.appendResponses += flowOf(listOf(event(2), event(1)))
        source.appendResponses += flowOf(listOf(event(1)))
        val model = FriendActivityTimelineModel(source, pageSize = 3)
        advanceUntilIdle()

        val first = assertIs<FriendActivityTimelineState.Content>(model.state.value)
        assertEquals(listOf(5L, 4L, 3L), first.events.map(FriendActivityEvent::id))
        assertTrue(first.hasMore)

        model.loadMore()
        advanceUntilIdle()
        source.database.value = listOf(event(6)) + source.database.value
        advanceUntilIdle()

        val loaded = assertIs<FriendActivityTimelineState.Content>(model.state.value)
        assertEquals(listOf(6L, 5L, 4L, 3L, 2L), loaded.events.map(FriendActivityEvent::id))
        assertTrue(loaded.hasMore)

        model.loadMore()
        advanceUntilIdle()

        val completed = assertIs<FriendActivityTimelineState.Content>(model.state.value)
        assertEquals(listOf(6L, 5L, 4L, 3L, 2L, 1L), completed.events.map(FriendActivityEvent::id))
        assertFalse(completed.hasMore)
        assertEquals(
            listOf(
                FriendActivityTimelineCursor(3_000L, 3L),
                FriendActivityTimelineCursor(2_000L, 2L),
            ),
            source.requestedCursors,
        )
    }

    @Test
    fun loadedSnapshotDropsItemsDeletedByRetention() = runTest {
        val source = FakeTimelineSource(listOf(event(5), event(4), event(3), event(2)))
        source.appendResponses += flowOf(listOf(event(2), event(1)))
        val model = FriendActivityTimelineModel(source, pageSize = 3)
        advanceUntilIdle()
        model.loadMore()
        advanceUntilIdle()
        assertEquals(
            listOf(5L, 4L, 3L, 2L, 1L),
            assertIs<FriendActivityTimelineState.Content>(model.state.value)
                .events.map(FriendActivityEvent::id),
        )

        source.database.value = source.database.value.filterNot { it.id == 2L }
        advanceUntilIdle()

        assertEquals(
            listOf(5L, 4L, 3L, 1L),
            assertIs<FriendActivityTimelineState.Content>(model.state.value)
                .events.map(FriendActivityEvent::id),
        )
    }

    @Test
    fun loadedSnapshotReplacesTheInitialHeadCollector() = runTest {
        val source = FakeTimelineSource(listOf(event(3), event(2), event(1)))
        val model = FriendActivityTimelineModel(source, pageSize = 2)
        advanceUntilIdle()
        assertEquals(1, source.headEmissionCount)

        repeat(5) { index ->
            source.database.value = listOf(event(4L + index)) + source.database.value
            advanceUntilIdle()
        }

        assertEquals(1, source.headEmissionCount)
        assertEquals(
            8L,
            assertIs<FriendActivityTimelineState.Content>(model.state.value).events.first().id,
        )
    }

    @Test
    fun emptyAppendFromStaleCursorKeepsTheDisplacedBoundaryLoadable() = runTest {
        val source = FakeTimelineSource(listOf(event(4), event(3), event(2)))
        val appendStarted = CompletableDeferred<Unit>()
        val releaseAppend = CompletableDeferred<Unit>()
        source.appendResponses += flow {
            appendStarted.complete(Unit)
            releaseAppend.await()
            emit(emptyList())
        }
        source.appendResponses += flowOf(listOf(event(3), event(2)))
        val model = FriendActivityTimelineModel(source, pageSize = 2)
        advanceUntilIdle()

        model.loadMore()
        appendStarted.await()
        source.database.value = listOf(event(5)) + source.database.value
        advanceUntilIdle()
        releaseAppend.complete(Unit)
        advanceUntilIdle()

        val completed = assertIs<FriendActivityTimelineState.Content>(model.state.value)
        assertEquals(listOf(5L, 4L), completed.events.map(FriendActivityEvent::id))
        assertTrue(completed.hasMore)
        assertFalse(completed.isLoadingMore)
        assertFalse(completed.loadMoreError)

        model.loadMore()
        advanceUntilIdle()

        val reloaded = assertIs<FriendActivityTimelineState.Content>(model.state.value)
        assertEquals(listOf(5L, 4L, 3L, 2L), reloaded.events.map(FriendActivityEvent::id))
        assertFalse(reloaded.hasMore)
    }

    @Test
    fun lateOldAccountSnapshotCannotOverwriteTheNewAccount() = runTest {
        val tokenA = AccountSessionToken("usr_a", 1L)
        val tokenB = AccountSessionToken("usr_b", 2L)
        val source = FakeTimelineSource(listOf(event(10)), initialToken = tokenA)
        val accountB = MutableStateFlow(listOf(event(20)))
        source.accountDatabases[tokenB] = accountB
        val model = FriendActivityTimelineModel(source, pageSize = 2)
        advanceUntilIdle()

        source.sessionTokens.value = tokenB
        source.accountDatabases.getValue(tokenA).value = listOf(event(99))
        advanceUntilIdle()

        val current = assertIs<FriendActivityTimelineState.Content>(model.state.value)
        assertEquals(listOf(20L), current.events.map(FriendActivityEvent::id))
        assertTrue(source.requestedTokens.containsAll(listOf(tokenA, tokenB)))
    }

    @Test
    fun appendFailureKeepsContentAndRetryContinuesFromTheSameCursor() = runTest {
        val source = FakeTimelineSource(
            initialHead = listOf(event(4), event(3), event(2)),
        )
        source.appendResponses += flow { throw IllegalStateException("read failed") }
        source.appendResponses += flowOf(listOf(event(2), event(1)))
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
        assertEquals(listOf(4L, 3L, 2L, 1L), recovered.events.map(FriendActivityEvent::id))
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
        initialToken: AccountSessionToken = AccountSessionToken("usr_owner", 1L),
    ) : FriendActivityTimelineSource {
        override val sessionTokens = MutableStateFlow<AccountSessionToken?>(initialToken)
        val database = MutableStateFlow(initialHead)
        val accountDatabases = mutableMapOf(initialToken to database)
        val filteredHeads = mutableMapOf<Set<FriendActivityEventType>, Flow<List<FriendActivityEvent>>>()
        val appendResponses = ArrayDeque<Flow<List<FriendActivityEvent>>>()
        val requestedCursors = mutableListOf<FriendActivityTimelineCursor>()
        val requestedTokens = mutableListOf<AccountSessionToken>()
        var headEmissionCount = 0

        override fun observeHead(
            token: AccountSessionToken,
            types: Set<FriendActivityEventType>,
            limit: Int,
        ): Flow<List<FriendActivityEvent>> {
            requestedTokens += token
            val accountDatabase = accountDatabases.getValue(token)
            val overridden = filteredHeads[types]
            val source = if (overridden != null) {
                overridden.onEach { page ->
                    accountDatabase.value = if (types.isEmpty()) {
                        page
                    } else {
                        (accountDatabase.value.filterNot { it.type in types } + page)
                            .normalizeActivityEvents()
                    }
                }
            } else {
                accountDatabase.map { events ->
                    events.filter { types.isEmpty() || it.type in types }.take(limit)
                }
            }
            return source.onEach { headEmissionCount++ }
        }

        override fun observeBefore(
            token: AccountSessionToken,
            types: Set<FriendActivityEventType>,
            cursor: FriendActivityTimelineCursor,
            limit: Int,
        ): Flow<List<FriendActivityEvent>> {
            requestedTokens += token
            requestedCursors += cursor
            return appendResponses.removeFirst().onEach { page ->
                val accountDatabase = accountDatabases.getValue(token)
                accountDatabase.value = (accountDatabase.value + page).normalizeActivityEvents()
            }
        }

        override fun observeThrough(
            token: AccountSessionToken,
            types: Set<FriendActivityEventType>,
            cursor: FriendActivityTimelineCursor,
            limit: Int,
        ): Flow<List<FriendActivityEvent>> {
            requestedTokens += token
            return accountDatabases.getValue(token).map { events ->
                events.filter { event ->
                    (types.isEmpty() || event.type in types) &&
                        (event.occurredAtMillis > cursor.occurredAtMillis ||
                            (event.occurredAtMillis == cursor.occurredAtMillis && event.id >= cursor.id))
                }.take(limit)
            }
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
