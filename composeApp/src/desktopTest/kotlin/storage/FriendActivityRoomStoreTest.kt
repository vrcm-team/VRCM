package io.github.vrcmteam.vrcm.storage

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.russhwolf.settings.MapSettings
import io.github.vrcmteam.vrcm.core.shared.AccountSessionToken
import io.github.vrcmteam.vrcm.core.shared.AuthenticatedAccount
import io.github.vrcmteam.vrcm.service.FriendActivityAccessType
import io.github.vrcmteam.vrcm.service.FriendActivityBatch
import io.github.vrcmteam.vrcm.service.FriendActivityEventType
import io.github.vrcmteam.vrcm.service.FriendActivityEventDraft
import io.github.vrcmteam.vrcm.service.FriendActivityInputSnapshot
import io.github.vrcmteam.vrcm.service.FriendActivityObservation
import io.github.vrcmteam.vrcm.service.FriendActivitySourceSnapshot
import io.github.vrcmteam.vrcm.service.FriendActivityTrackingControl
import io.github.vrcmteam.vrcm.service.FriendActivityTrackingState
import io.github.vrcmteam.vrcm.service.FriendSocketPresenceEvent
import io.github.vrcmteam.vrcm.service.FriendSocketPresenceType
import io.github.vrcmteam.vrcm.service.FriendMeetingChange
import io.github.vrcmteam.vrcm.service.data.AccountDto
import io.github.vrcmteam.vrcm.service.toSocketPresenceInputSnapshot
import io.github.vrcmteam.vrcm.service.trackFriendActivity
import io.github.vrcmteam.vrcm.storage.meetup.MeetupCardAssetStore
import io.github.vrcmteam.vrcm.storage.meetup.MeetupCardConfigDao
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FriendActivityRoomStoreTest {
    @Test
    fun globalTimelineIsAccountScopedStablySortedAndFilterable() = runTest {
        withStore { store ->
            val ownerA = store.activateAccount("usr_owner_a")
            val ownerB = store.activateAccount("usr_owner_b")
            val friendA = observation().copy(userId = "usr_friend_a", displayName = "Friend A")
            val friendB = observation().copy(userId = "usr_friend_b", displayName = "Friend B")

            store.record(
                token = ownerA,
                observations = listOf(friendA),
                batch = FriendActivityBatch(
                    events = listOf(
                        FriendActivityEventDraft(
                            userId = friendA.userId,
                            displayName = friendA.displayName,
                            profileImageUrl = friendA.profileImageUrl,
                            type = FriendActivityEventType.Online,
                            occurredAtMillis = 2_000L,
                        )
                    )
                ),
                nowMillis = 2_000L,
            )
            store.record(
                token = ownerA,
                observations = listOf(friendB),
                batch = FriendActivityBatch(
                    events = listOf(
                        FriendActivityEventDraft(
                            userId = friendB.userId,
                            displayName = friendB.displayName,
                            profileImageUrl = friendB.profileImageUrl,
                            type = FriendActivityEventType.BioChanged,
                            occurredAtMillis = 2_000L,
                            previousValue = "Old",
                            currentValue = "New",
                        ),
                        FriendActivityEventDraft(
                            userId = friendB.userId,
                            displayName = friendB.displayName,
                            profileImageUrl = friendB.profileImageUrl,
                            type = FriendActivityEventType.Offline,
                            occurredAtMillis = 1_000L,
                        ),
                    )
                ),
                nowMillis = 2_000L,
            )
            store.record(
                token = ownerB,
                observations = listOf(friendA),
                batch = FriendActivityBatch(
                    events = listOf(
                        FriendActivityEventDraft(
                            userId = friendA.userId,
                            displayName = "Other account friend",
                            profileImageUrl = friendA.profileImageUrl,
                            type = FriendActivityEventType.LocationChanged,
                            occurredAtMillis = 3_000L,
                        )
                    )
                ),
                nowMillis = 3_000L,
            )

            val ownerEvents = store.observeAllEvents("usr_owner_a").first()
            assertEquals(
                listOf("Friend B", "Friend A", "Friend B"),
                ownerEvents.map { it.displayName },
            )
            assertEquals(listOf(2_000L, 2_000L, 1_000L), ownerEvents.map { it.occurredAtMillis })
            assertEquals(
                ownerEvents.take(2).map { it.id }.sortedDescending(),
                ownerEvents.take(2).map { it.id },
            )

            val filteredPage = store.observeAllEvents(
                ownerUserId = "usr_owner_a",
                types = setOf(FriendActivityEventType.Online, FriendActivityEventType.Offline),
                limit = 1,
                offset = 1,
            ).first()
            assertEquals(listOf(FriendActivityEventType.Offline.name), filteredPage.map { it.type })
            assertEquals(
                listOf("Other account friend"),
                store.observeAllEvents("usr_owner_b").first().map { it.displayName },
            )
        }
    }

    @Test
    fun restoreFinalizesIncompleteSessionAtLastCheckpoint() = runTest {
        withStore { store ->
            val token = store.activateAccount("usr_owner")
            val observation = observation()
            store.record(
                token = token,
                observations = listOf(observation),
                batch = FriendActivityBatch(
                    meetings = listOf(
                        FriendMeetingChange.Started(
                            userId = observation.userId,
                            occurredAtMillis = 1_000L,
                            worldId = "wrld_world",
                            accessType = FriendActivityAccessType.Invite,
                            announce = false,
                        )
                    )
                ),
                nowMillis = 1_000L,
            )
            store.record(
                token = token,
                observations = listOf(observation),
                batch = FriendActivityBatch(
                    meetings = listOf(
                        FriendMeetingChange.Checkpoint(
                            userId = observation.userId,
                            occurredAtMillis = 31_000L,
                            durationMillis = 30_000L,
                        )
                    )
                ),
                nowMillis = 31_000L,
            )

            store.discardIncompleteSessions("usr_owner")

            assertEquals(emptyList(), store.sessions("usr_owner", observation.userId))
            val summary = store.summary("usr_owner", observation.userId)
            assertEquals(0, summary?.meetingCount)
            assertEquals(30_000L, summary?.togetherDurationMillis)
            assertEquals(31_000L, summary?.lastSeenTogetherAtMillis)
        }
    }

    @Test
    fun completedSessionUpdatesSummaryAndTimeline() = runTest {
        withStore { store ->
            val token = store.activateAccount("usr_owner")
            val observation = observation()
            store.record(
                token = token,
                observations = listOf(observation),
                batch = FriendActivityBatch(
                    meetings = listOf(
                        FriendMeetingChange.Started(
                            userId = observation.userId,
                            occurredAtMillis = 1_000L,
                            worldId = "wrld_world",
                            accessType = FriendActivityAccessType.Invite,
                            announce = true,
                        )
                    )
                ),
                nowMillis = 1_000L,
            )
            assertNull(store.summary("usr_owner", observation.userId)?.lastSeenTogetherAtMillis)
            store.record(
                token = token,
                observations = listOf(observation),
                batch = FriendActivityBatch(
                    meetings = listOf(
                        FriendMeetingChange.Checkpoint(
                            userId = observation.userId,
                            occurredAtMillis = 31_000L,
                            durationMillis = 30_000L,
                        )
                    )
                ),
                nowMillis = 31_000L,
            )
            assertNull(store.summary("usr_owner", observation.userId)?.lastSeenTogetherAtMillis)
            store.record(
                token = token,
                observations = listOf(observation),
                batch = FriendActivityBatch(
                    meetings = listOf(
                        FriendMeetingChange.Ended(
                            userId = observation.userId,
                            occurredAtMillis = 61_000L,
                            durationMillis = 60_000L,
                        )
                    )
                ),
                nowMillis = 61_000L,
            )

            val summary = store.summary("usr_owner", observation.userId)
            assertEquals(1, summary?.meetingCount)
            assertEquals(60_000L, summary?.togetherDurationMillis)
            assertEquals(61_000L, summary?.lastSeenTogetherAtMillis)

            val session = store.sessions("usr_owner", observation.userId).single()
            assertEquals(61_000L, session.endedAtMillis)
            assertEquals(60_000L, session.durationMillis)

            val events = store.observeEvents("usr_owner", observation.userId).first()
            assertEquals(
                listOf(FriendActivityEventType.Left.name, FriendActivityEventType.Met.name),
                events.map { it.type },
            )
            assertEquals(listOf("wrld_world", "wrld_world"), events.map { it.worldId })
            assertEquals(
                listOf(FriendActivityAccessType.Invite.name, FriendActivityAccessType.Invite.name),
                events.map { it.accessType },
            )
        }
    }

    @Test
    fun worldNameBackfillUpdatesEventsAndSessionsForOneAccount() = runTest {
        withStore { store ->
            val token = store.activateAccount("usr_owner")
            val observation = observation()
            store.record(
                token = token,
                observations = listOf(observation),
                batch = FriendActivityBatch(
                    meetings = listOf(
                        FriendMeetingChange.Started(
                            userId = observation.userId,
                            occurredAtMillis = 1_000L,
                            worldId = "wrld_world",
                            accessType = FriendActivityAccessType.Invite,
                            announce = true,
                        )
                    )
                ),
                nowMillis = 1_000L,
            )

            store.cacheWorldName("usr_owner", "wrld_world", "The Black Cat")

            assertEquals(
                "The Black Cat",
                store.observeEvents("usr_owner", observation.userId).first().single().worldName,
            )
            assertEquals(
                "The Black Cat",
                store.sessions("usr_owner", observation.userId).single().worldName,
            )
            assertEquals("The Black Cat", store.cachedWorldName("usr_owner", "wrld_world"))
            assertNull(store.cachedWorldName("usr_other", "wrld_world"))
        }
    }

    @Test
    fun recentTogetherOnlyReturnsFriendsInsideWindowInLatestOrder() = runTest {
        withStore { store ->
            val token = store.activateAccount("usr_owner")
            val older = observation().copy(userId = "usr_older", displayName = "Older")
            val newer = observation().copy(userId = "usr_newer", displayName = "Newer")
            store.record(
                token = token,
                observations = listOf(older),
                batch = FriendActivityBatch(
                    meetings = listOf(
                        FriendMeetingChange.Started(
                            userId = older.userId,
                            occurredAtMillis = 1_000L,
                            worldId = "wrld_old",
                            accessType = FriendActivityAccessType.Public,
                            announce = false,
                        )
                    )
                ),
                nowMillis = 1_000L,
            )
            store.record(
                token = token,
                observations = listOf(older),
                batch = FriendActivityBatch(
                    meetings = listOf(
                        FriendMeetingChange.Ended(
                            userId = older.userId,
                            occurredAtMillis = 1_500L,
                            durationMillis = 500L,
                        )
                    )
                ),
                nowMillis = 1_500L,
            )
            store.record(
                token = token,
                observations = listOf(newer),
                batch = FriendActivityBatch(
                    meetings = listOf(
                        FriendMeetingChange.Started(
                            userId = newer.userId,
                            occurredAtMillis = 3_000L,
                            worldId = "wrld_new",
                            accessType = FriendActivityAccessType.Friends,
                            announce = false,
                        )
                    )
                ),
                nowMillis = 3_000L,
            )
            store.record(
                token = token,
                observations = listOf(newer),
                batch = FriendActivityBatch(
                    meetings = listOf(
                        FriendMeetingChange.Ended(
                            userId = newer.userId,
                            occurredAtMillis = 3_500L,
                            durationMillis = 500L,
                        )
                    )
                ),
                nowMillis = 3_500L,
            )

            val recent = store.observeRecentTogether(
                ownerUserId = "usr_owner",
                sinceMillis = 2_000L,
                limit = 20,
            ).first()

            assertEquals(listOf("usr_newer"), recent.map { it.friendUserId })
        }
    }

    @Test
    fun activityCollectorIgnoresSnapshotsFromAnotherSession() = runTest {
        withStore { store ->
            val session = AuthenticatedAccount(
                account = AccountDto(userId = "usr_owner"),
                token = AccountSessionToken(userId = "usr_owner", generation = 2L),
            )
            val otherSessionToken = AccountSessionToken(userId = "usr_other", generation = 1L)
            val friend = observation().copy(location = "wrld_world:instance_b")

            trackFriendActivity(
                session = session,
                snapshots = flowOf(
                    FriendActivityInputSnapshot(
                        token = otherSessionToken,
                        friends = listOf(friend.copy(userId = "usr_wrong")),
                        selfLocation = "wrld_world:instance_b",
                        observedAtMillis = 500L,
                    ),
                    FriendActivityInputSnapshot(
                        token = session.token,
                        friends = listOf(friend),
                        selfLocation = "wrld_world:instance_a",
                        observedAtMillis = 1_000L,
                    ),
                    FriendActivityInputSnapshot(
                        token = session.token,
                        friends = listOf(friend.copy(location = "wrld_world:instance_a")),
                        selfLocation = "wrld_world:instance_a",
                        observedAtMillis = 2_000L,
                    ),
                    FriendActivityInputSnapshot(
                        token = session.token,
                        friends = listOf(friend.copy(location = "wrld_world:instance_a")),
                        selfLocation = "wrld_world:instance_a",
                        observedAtMillis = 3_000L,
                        trackingControl = FriendActivityTrackingControl.Stop,
                    ),
                ),
                store = store,
            )

            assertNull(store.summary("usr_owner", "usr_wrong"))
            assertEquals(
                3_000L,
                store.summary("usr_owner", friend.userId)?.lastSeenTogetherAtMillis,
            )
        }
    }

    @Test
    fun delayedSocketPresenceFromPreviousSessionIsRejectedAfterAccountSwitch() = runTest {
        withStore { store ->
            val currentSession = AuthenticatedAccount(
                account = AccountDto(userId = "usr_current_owner"),
                token = AccountSessionToken(userId = "usr_current_owner", generation = 2L),
            )
            val previousToken = AccountSessionToken(userId = "usr_previous_owner", generation = 1L)
            val delayedInput = FriendActivitySourceSnapshot(
                token = currentSession.token,
                friends = emptyList(),
                selfLocation = null,
            ).toSocketPresenceInputSnapshot(
                eventToken = previousToken,
                presenceEvent = FriendSocketPresenceEvent(
                    userId = "usr_previous_friend",
                    type = FriendSocketPresenceType.Offline,
                    occurredAtMillis = 1_000L,
                ),
            )

            trackFriendActivity(
                session = currentSession,
                snapshots = flowOf(delayedInput),
                store = store,
            )

            assertNull(store.summary(currentSession.account.userId, "usr_previous_friend"))
        }
    }

    @Test
    fun stoppedCollectorDoesNotRestartUntilResume() = runTest {
        withStore { store ->
            val session = AuthenticatedAccount(
                account = AccountDto(userId = "usr_owner"),
                token = AccountSessionToken(userId = "usr_owner", generation = 1L),
            )
            val friend = observation().copy(location = "wrld_world:instance_a")

            trackFriendActivity(
                session = session,
                snapshots = flowOf(
                    FriendActivityInputSnapshot(session.token, listOf(friend), friend.location, 1_000L),
                    FriendActivityInputSnapshot(
                        session.token,
                        listOf(friend),
                        friend.location,
                        2_000L,
                        trackingControl = FriendActivityTrackingControl.Stop,
                    ),
                    FriendActivityInputSnapshot(session.token, listOf(friend), friend.location, 3_000L),
                    FriendActivityInputSnapshot(session.token, listOf(friend), friend.location, 4_000L),
                    FriendActivityInputSnapshot(
                        session.token,
                        listOf(friend),
                        friend.location,
                        5_000L,
                        trackingControl = FriendActivityTrackingControl.Resume,
                    ),
                    FriendActivityInputSnapshot(session.token, listOf(friend), friend.location, 6_000L),
                    FriendActivityInputSnapshot(
                        session.token,
                        listOf(friend),
                        friend.location,
                        7_000L,
                        trackingControl = FriendActivityTrackingControl.Stop,
                    ),
                ),
                store = store,
            )

            val summary = store.summary("usr_owner", friend.userId)
            assertEquals(3_000L, summary?.togetherDurationMillis)
            assertEquals(7_000L, summary?.lastSeenTogetherAtMillis)
            assertEquals(0, summary?.meetingCount)
            assertEquals(
                listOf(5_000L, 1_000L),
                store.sessions("usr_owner", friend.userId).map { it.startedAtMillis },
            )
        }
    }

    @Test
    fun firstKnownSelfLocationStartsUnannouncedMeeting() = runTest {
        withStore { store ->
            val session = AuthenticatedAccount(
                account = AccountDto(userId = "usr_owner"),
                token = AccountSessionToken(userId = "usr_owner", generation = 1L),
            )
            val friend = observation().copy(location = "wrld_world:instance_a")

            trackFriendActivity(
                session = session,
                snapshots = flowOf(
                    FriendActivityInputSnapshot(session.token, listOf(friend), null, 1_000L),
                    FriendActivityInputSnapshot(session.token, listOf(friend), friend.location, 2_000L),
                    FriendActivityInputSnapshot(
                        session.token,
                        listOf(friend),
                        friend.location,
                        3_000L,
                        trackingControl = FriendActivityTrackingControl.Stop,
                    ),
                ),
                store = store,
            )

            val summary = store.summary(session.account.userId, friend.userId)
            assertEquals(0, summary?.meetingCount)
            assertEquals(
                emptyList(),
                store.observeEvents(session.account.userId, friend.userId).first().map { it.type },
            )
            assertEquals(
                listOf(false),
                store.sessions(session.account.userId, friend.userId).map { it.announced },
            )
        }
    }

    @Test
    fun replayedResumeStartsTrackingAtCollectorSubscriptionTime() = runTest {
        withStore { store ->
            val session = AuthenticatedAccount(
                account = AccountDto(userId = "usr_owner"),
                token = AccountSessionToken(userId = "usr_owner", generation = 1L),
            )
            val friend = observation().copy(location = "wrld_world:instance_a")
            var nowMillis = 1_000L
            val trackingState = FriendActivityTrackingState { nowMillis }
            trackingState.setBackgroundMonitoring(true)
            nowMillis = 5_000L

            trackFriendActivity(
                session = session,
                snapshots = trackingState.controls.take(1).map { transition ->
                    FriendActivityInputSnapshot(
                        token = session.token,
                        friends = listOf(friend),
                        selfLocation = friend.location,
                        observedAtMillis = transition.occurredAtMillis,
                        trackingControl = transition.control,
                    )
                },
                store = store,
            )

            assertEquals(
                listOf(5_000L),
                store.sessions(session.account.userId, friend.userId).map { it.startedAtMillis },
            )
        }
    }

    @Test
    fun trackingTransitionsRetainOccurredTimesWhileCollectorIsBusy() = runTest {
        withStore { store ->
            val session = AuthenticatedAccount(
                account = AccountDto(userId = "usr_owner"),
                token = AccountSessionToken(userId = "usr_owner", generation = 1L),
            )
            val friend = observation().copy(location = "wrld_world:instance_a")
            val baselineRecorded = CompletableDeferred<Unit>()
            val stopDequeued = CompletableDeferred<Unit>()
            val releaseStop = CompletableDeferred<Unit>()
            var nowMillis = 0L
            val trackingState = FriendActivityTrackingState { nowMillis }

            nowMillis = 1_000L
            trackingState.setAppForeground(true)
            val collector = launch {
                trackFriendActivity(
                    session = session,
                    snapshots = flow {
                        trackingState.controls.take(4).collect { transition ->
                            if (transition.sequence == 2L) {
                                stopDequeued.complete(Unit)
                                releaseStop.await()
                            }
                            emit(
                                FriendActivityInputSnapshot(
                                    token = session.token,
                                    friends = listOf(friend),
                                    selfLocation = friend.location,
                                    observedAtMillis = transition.occurredAtMillis,
                                    trackingControl = transition.control,
                                )
                            )
                            if (transition.sequence == 1L) {
                                baselineRecorded.complete(Unit)
                            }
                        }
                    },
                    store = store,
                )
            }

            baselineRecorded.await()
            nowMillis = 2_000L
            trackingState.setAppForeground(false)
            stopDequeued.await()
            nowMillis = 5_000L
            trackingState.setAppForeground(true)
            nowMillis = 7_000L
            trackingState.setAppForeground(false)
            releaseStop.complete(Unit)
            collector.join()

            val sessions = store.sessions(session.account.userId, friend.userId)
            assertEquals(
                3_000L,
                store.summary(session.account.userId, friend.userId)?.togetherDurationMillis,
            )
            assertEquals(listOf(5_000L, 1_000L), sessions.map { it.startedAtMillis })
            assertEquals(listOf(7_000L, 2_000L), sessions.map { it.endedAtMillis })
        }
    }

    @Test
    fun cancellingCollectorCompletesActiveMeetingAtCancellationTime() = runTest {
        withStore { store ->
            val session = AuthenticatedAccount(
                account = AccountDto(userId = "usr_owner"),
                token = AccountSessionToken(userId = "usr_owner", generation = 1L),
            )
            val friend = observation().copy(location = "wrld_world:instance_a")
            val baselineRecorded = CompletableDeferred<Unit>()

            val collector = launch {
                trackFriendActivity(
                    session = session,
                    snapshots = flow {
                        emit(FriendActivityInputSnapshot(session.token, listOf(friend), friend.location, 1_000L))
                        baselineRecorded.complete(Unit)
                        awaitCancellation()
                    },
                    store = store,
                    cancellationTimeMillis = { 5_000L },
                )
            }
            baselineRecorded.await()
            collector.cancelAndJoin()

            val summary = store.summary("usr_owner", friend.userId)
            assertEquals(4_000L, summary?.togetherDurationMillis)
            assertEquals(5_000L, summary?.lastSeenTogetherAtMillis)
            assertEquals(5_000L, summary?.lastActivityAtMillis)
            assertEquals(5_000L, store.sessions("usr_owner", friend.userId).single().endedAtMillis)
        }
    }

    @Test
    fun onlineSnapshotsAdvanceLastActivityAndOfflineSnapshotUsesApiTime() = runTest {
        withStore { store ->
            val session = AuthenticatedAccount(
                account = AccountDto(userId = "usr_owner"),
                token = AccountSessionToken(userId = "usr_owner", generation = 1L),
            )
            val friend = observation().copy(lastActivityAtMillis = null)

            trackFriendActivity(
                session = session,
                snapshots = flow {
                    emit(FriendActivityInputSnapshot(session.token, listOf(friend), null, 1_000L))
                    assertEquals(
                        1_000L,
                        store.summary(session.account.userId, friend.userId)?.lastActivityAtMillis,
                    )
                    emit(FriendActivityInputSnapshot(session.token, listOf(friend), null, 2_000L))
                    assertEquals(
                        2_000L,
                        store.summary(session.account.userId, friend.userId)?.lastActivityAtMillis,
                    )
                    emit(
                        FriendActivityInputSnapshot(
                            session.token,
                            listOf(
                                friend.copy(
                                    location = "offline",
                                    status = "offline",
                                    lastActivityAtMillis = 2_500L,
                                )
                            ),
                            null,
                            3_000L,
                            updateLastActivityOnly = true,
                        )
                    )
                },
                store = store,
            )

            assertEquals(2_500L, store.summary("usr_owner", friend.userId)?.lastActivityAtMillis)
        }
    }

    @Test
    fun accountCacheClearAlsoRemovesRoomActivity() = runTest {
        withStore { store ->
            val token = store.activateAccount("usr_owner")
            store.record(
                token = token,
                observations = listOf(observation()),
                batch = FriendActivityBatch(),
                nowMillis = 1_000L,
            )
            val manager = AccountCacheManager(
                friendListCacheStore = InMemoryFriendListCacheStore(),
                userProfileCacheStore = InMemoryUserProfileCacheStore(),
                friendActivityStore = store,
                meetupCardConfigDao = MeetupCardConfigDao(MapSettings()),
                meetupCardAssetStore = MeetupCardAssetStore(
                    FakeFileSystem(),
                    "/meetup-assets".toPath(),
                ),
            )

            manager.clearAccount("usr_owner")

            assertNull(store.summary("usr_owner", "usr_friend"))
        }
    }

    @Test
    fun clearAccountRejectsWritesCapturedByOlderGeneration() = runTest {
        withStore { store ->
            val token = store.activateAccount("usr_owner")
            val observation = observation()
            store.record(
                token = token,
                observations = listOf(observation),
                batch = FriendActivityBatch(),
                nowMillis = 1_000L,
            )
            store.clearAccount("usr_owner")

            store.record(
                token = token,
                observations = listOf(observation.copy(displayName = "Stale")),
                batch = FriendActivityBatch(),
                nowMillis = 2_000L,
            )

            assertNull(store.summary("usr_owner", observation.userId))
        }
    }

    @Test
    fun clearAllRejectsWritesFromEveryPreviouslyActiveAccount() = runTest {
        withStore { store ->
            val tokenA = store.activateAccount("usr_owner_a")
            val tokenB = store.activateAccount("usr_owner_b")
            val observation = observation()
            store.record(tokenA, listOf(observation), FriendActivityBatch(), 1_000L)
            store.record(tokenB, listOf(observation), FriendActivityBatch(), 1_000L)

            store.clearAll()
            store.record(tokenA, listOf(observation), FriendActivityBatch(), 2_000L)
            store.record(tokenB, listOf(observation), FriendActivityBatch(), 2_000L)

            assertNull(store.summary("usr_owner_a", observation.userId))
            assertNull(store.summary("usr_owner_b", observation.userId))
        }
    }

    @Test
    fun activityCollectorRebuildsItsBaselineAfterCacheClear() = runTest {
        withStore { store ->
            val session = AuthenticatedAccount(
                account = AccountDto(userId = "usr_owner"),
                token = AccountSessionToken(userId = "usr_owner", generation = 1L),
            )
            val baseline = observation().copy(location = "wrld_world:instance_a")

            trackFriendActivity(
                session = session,
                snapshots = flow {
                    emit(
                        FriendActivityInputSnapshot(
                            token = session.token,
                            friends = listOf(baseline),
                            selfLocation = null,
                            observedAtMillis = 1_000L,
                        )
                    )
                    store.clearAll()
                    emit(
                        FriendActivityInputSnapshot(
                            token = session.token,
                            friends = listOf(baseline.copy(statusDescription = "After clear")),
                            selfLocation = null,
                            observedAtMillis = 2_000L,
                        )
                    )
                    emit(
                        FriendActivityInputSnapshot(
                            token = session.token,
                            friends = listOf(baseline.copy(
                                statusDescription = "After clear",
                                bio = "Changed after baseline",
                            )),
                            selfLocation = null,
                            observedAtMillis = 3_000L,
                        )
                    )
                },
                store = store,
            )

            assertEquals(
                listOf(FriendActivityEventType.BioChanged.name),
                store.observeEvents("usr_owner", baseline.userId).first().map { it.type },
            )
        }
    }

    private suspend fun withStore(block: suspend (RoomFriendActivityStore) -> Unit) {
        val database = Room.inMemoryDatabaseBuilder<VrcmDatabase>()
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
        try {
            block(RoomFriendActivityStore(database.friendActivityDao()))
        } finally {
            database.close()
        }
    }

    private fun observation() = FriendActivityObservation(
        userId = "usr_friend",
        displayName = "Friend",
        profileImageUrl = "https://example.com/friend.png",
        location = "wrld_world:private_instance~private(usr_owner)",
        status = "active",
        statusDescription = "",
        bio = "",
        lastActivityAtMillis = 900L,
    )
}
