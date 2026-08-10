package io.github.vrcmteam.vrcm.service

import io.github.vrcmteam.vrcm.core.shared.AccountSessionToken
import io.github.vrcmteam.vrcm.core.shared.AuthenticatedAccount
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.network.api.friends.date.FriendData
import io.github.vrcmteam.vrcm.network.api.worlds.WorldsApi
import io.github.vrcmteam.vrcm.storage.FriendActivityEventEntity
import io.github.vrcmteam.vrcm.storage.FriendActivitySummaryEntity
import io.github.vrcmteam.vrcm.storage.RoomFriendActivityStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.IO
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.koin.core.logger.Logger
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

data class FriendActivitySummary(
    val friendUserId: String,
    val displayName: String,
    val profileImageUrl: String,
    val lastSeenTogetherAtMillis: Long?,
    val meetingCount: Int,
    val togetherDurationMillis: Long,
    val lastOnlineAtMillis: Long?,
    val lastOfflineAtMillis: Long?,
    val lastActivityAtMillis: Long?,
)

data class FriendActivityEvent(
    val id: Long,
    val friendUserId: String,
    val type: FriendActivityEventType,
    val occurredAtMillis: Long,
    val previousValue: String?,
    val currentValue: String?,
    val worldId: String?,
    val worldName: String?,
    val accessType: FriendActivityAccessType?,
)

internal data class FriendActivitySourceSnapshot(
    val token: AccountSessionToken,
    val friends: Collection<FriendData>,
    val selfLocation: String?,
)

internal data class FriendActivityInputSnapshot(
    val token: AccountSessionToken,
    val friends: Collection<FriendActivityObservation>,
    val selfLocation: String?,
    val observedAtMillis: Long,
    val socketPresenceEvent: FriendSocketPresenceEvent? = null,
    val trackingControl: FriendActivityTrackingControl? = null,
    val updateLastActivityOnly: Boolean = false,
)

internal enum class FriendActivityTrackingControl { Stop, Resume }

/**
 * Combines every lifecycle owner that can keep friend activity tracking alive.
 * Tracking changes only when the aggregate state crosses between zero and one active source.
 */
internal class FriendActivityTrackingState {
    private val activeSources = MutableStateFlow(0)

    /** Replays the current aggregate state whenever an account collector starts. */
    val controls: Flow<FriendActivityTrackingControl> = activeSources
        .map { sources ->
            if (sources == 0) FriendActivityTrackingControl.Stop
            else FriendActivityTrackingControl.Resume
        }
        .distinctUntilChanged()

    fun setAppForeground(active: Boolean) =
        setSourceActive(APP_FOREGROUND_SOURCE, active)

    fun setBackgroundMonitoring(active: Boolean) =
        setSourceActive(BACKGROUND_MONITORING_SOURCE, active)

    fun isEnabled(): Boolean = activeSources.value != 0

    private fun setSourceActive(source: Int, active: Boolean) {
        activeSources.update { current ->
            if (active) current or source else current and source.inv()
        }
    }

    private companion object {
        const val APP_FOREGROUND_SOURCE = 1
        const val BACKGROUND_MONITORING_SOURCE = 1 shl 1
    }
}

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)
class FriendActivityService internal constructor(
    friendService: FriendService,
    private val store: RoomFriendActivityStore,
    private val worldsApi: WorldsApi,
    private val logger: Logger,
) {
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val trackingState = FriendActivityTrackingState()
    private val worldNameResolver = FriendActivityWorldNameResolver(
        readCachedName = store::cachedWorldName,
        fetchWorldName = { worldId ->
            withTimeout(WORLD_NAME_TIMEOUT_MILLIS) {
                worldsApi.getWorldById(worldId).name
            }
        },
        cacheWorldName = store::cacheWorldName,
        nowMillis = { Clock.System.now().toEpochMilliseconds() },
    )

    init {
        serviceScope.launch {
            SharedFlowCentre.currentSession.collectLatest { session ->
                if (session == null) return@collectLatest
                try {
                    trackFriendActivity(
                        session = session,
                        snapshots = merge(
                            friendService.friendActivitySource.filterNotNull().mapNotNull {
                                it.takeIf { trackingState.isEnabled() }?.toInputSnapshot()
                            },
                            friendService.friendLastActivitySource.filterNotNull().map {
                                it.toInputSnapshot(
                                    includeLastActivity = true,
                                    updateLastActivityOnly = true,
                                )
                            },
                            friendService.friendUpdateFlow.mapNotNull { update ->
                                if (!trackingState.isEnabled()) return@mapNotNull null
                                val type = when (update.event) {
                                    is FriendUpdateEvent.Online -> FriendSocketPresenceType.Online
                                    is FriendUpdateEvent.Offline -> FriendSocketPresenceType.Offline
                                    else -> return@mapNotNull null
                                }
                                val presenceEvent = FriendSocketPresenceEvent(
                                    userId = when (val event = update.event) {
                                        is FriendUpdateEvent.Online -> event.userId
                                        is FriendUpdateEvent.Offline -> event.userId
                                        else -> return@mapNotNull null
                                    },
                                    type = type,
                                    occurredAtMillis = update.occurredAtMillis
                                        ?: Clock.System.now().toEpochMilliseconds(),
                                )
                                friendService.friendActivitySource.value.toSocketPresenceInputSnapshot(
                                    eventToken = update.sessionToken,
                                    presenceEvent = presenceEvent,
                                )
                            },
                            flow {
                                while (true) {
                                    delay(MEETING_CHECKPOINT_MILLIS)
                                    if (trackingState.isEnabled()) {
                                        friendService.friendActivitySource.value?.let { emit(it.toInputSnapshot()) }
                                    }
                                }
                            },
                            trackingState.controls.map { control ->
                                val observedAtMillis = Clock.System.now().toEpochMilliseconds()
                                val source = friendService.friendActivitySource.value
                                    ?.takeIf { it.token == session.token }
                                source?.toInputSnapshot(
                                    trackingControl = control,
                                    observedAtMillis = observedAtMillis,
                                ) ?: FriendActivityInputSnapshot(
                                    token = session.token,
                                    friends = emptyList(),
                                    selfLocation = null,
                                    observedAtMillis = observedAtMillis,
                                    trackingControl = control,
                                )
                            },
                        ),
                        store = store,
                    )
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Throwable) {
                    logger.error("Friend activity tracking failed: ${error.message}")
                }
            }
        }
    }

    fun observeSummary(friendUserId: String): Flow<FriendActivitySummary?> =
        SharedFlowCentre.currentSession.flatMapLatest { session ->
            if (session == null) {
                flowOf(null)
            } else {
                store.observeSummary(session.account.userId, friendUserId)
                    .map { it?.toSummary() }
            }
        }

    fun observeEvents(
        friendUserId: String,
        limit: Int = 50,
    ): Flow<List<FriendActivityEvent>> = SharedFlowCentre.currentSession.flatMapLatest { session ->
        if (session == null) {
            flowOf(emptyList())
        } else {
            store.observeEvents(session.account.userId, friendUserId, limit)
                .onEach { events ->
                    events.asSequence()
                        .filter { it.worldName == null }
                        .mapNotNull(FriendActivityEventEntity::worldId)
                        .distinct()
                        .forEach { worldId -> resolveWorldName(session.account.userId, worldId) }
                }
                .map { events -> events.mapNotNull(FriendActivityEventEntity::toEventOrNull) }
        }
    }

    fun observeRecentTogether(
        sinceMillis: Long,
        limit: Int = 20,
    ): Flow<List<FriendActivitySummary>> = SharedFlowCentre.currentSession.flatMapLatest { session ->
        if (session == null) {
            flowOf(emptyList())
        } else {
            store.observeRecentTogether(session.account.userId, sinceMillis, limit)
                .map { summaries -> summaries.map(FriendActivitySummaryEntity::toSummary) }
        }
    }

    internal fun dispose() {
        serviceScope.cancel()
    }

    fun onAppStopped() {
        trackingState.setAppForeground(false)
    }

    fun onAppResumed() {
        trackingState.setAppForeground(true)
    }

    fun onBackgroundMonitoringStarted() {
        trackingState.setBackgroundMonitoring(true)
    }

    fun onBackgroundMonitoringStopped() {
        trackingState.setBackgroundMonitoring(false)
    }

    private fun resolveWorldName(ownerUserId: String, worldId: String) {
        serviceScope.launch {
            try {
                worldNameResolver.resolve(ownerUserId, worldId)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                logger.error("Friend activity world lookup failed for $worldId: ${error.message}")
            }
        }
    }

    private companion object {
        const val WORLD_NAME_TIMEOUT_MILLIS = 5_000L
        const val MEETING_CHECKPOINT_MILLIS = 15_000L
    }
}

@OptIn(ExperimentalTime::class)
internal suspend fun trackFriendActivity(
    session: AuthenticatedAccount,
    snapshots: Flow<FriendActivityInputSnapshot>,
    store: RoomFriendActivityStore,
    cancellationTimeMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    var writeToken = store.activateAccount(session.account.userId)
    store.discardIncompleteSessions(session.account.userId)
    var tracker = FriendActivityTracker(derivePresenceEvents = false)
    var trackingEnabled = true
    var latestObservations: Collection<FriendActivityObservation> = emptyList()
    try {
        snapshots.collect { snapshot ->
            if (snapshot.token != session.token) return@collect
            if (!snapshot.updateLastActivityOnly) latestObservations = snapshot.friends
            val batch = when (snapshot.trackingControl) {
                FriendActivityTrackingControl.Stop -> {
                    trackingEnabled = false
                    tracker.finish(snapshot.observedAtMillis)
                }
                FriendActivityTrackingControl.Resume -> {
                    trackingEnabled = true
                    tracker.observe(snapshot.friends, snapshot.selfLocation, snapshot.observedAtMillis)
                }
                null -> if (snapshot.updateLastActivityOnly) {
                    FriendActivityBatch()
                } else if (!trackingEnabled) {
                    FriendActivityBatch()
                } else snapshot.socketPresenceEvent?.let {
                    tracker.observeSocketPresence(snapshot.friends, it)
                } ?: tracker.observe(snapshot.friends, snapshot.selfLocation, snapshot.observedAtMillis)
            }
            val observations = if (snapshot.updateLastActivityOnly) {
                snapshot.friends
            } else {
                snapshot.friends.map { it.copy(lastActivityAtMillis = null) }
            }
            val recorded = store.record(
                token = writeToken,
                observations = observations,
                batch = batch,
                nowMillis = snapshot.observedAtMillis,
            )
            if (!recorded) {
                writeToken = store.activateAccount(session.account.userId)
                tracker = FriendActivityTracker(derivePresenceEvents = false)
                val baseline = if (trackingEnabled && !snapshot.updateLastActivityOnly) {
                    tracker.observe(
                        friends = snapshot.friends,
                        selfLocation = snapshot.selfLocation,
                        nowMillis = snapshot.observedAtMillis,
                    )
                } else {
                    FriendActivityBatch()
                }
                store.record(
                    token = writeToken,
                    observations = observations,
                    batch = baseline,
                    nowMillis = snapshot.observedAtMillis,
                )
            }
        }
    } catch (error: CancellationException) {
        if (trackingEnabled) {
            withContext(NonCancellable) {
                val stoppedAtMillis = cancellationTimeMillis()
                store.record(
                    token = writeToken,
                    observations = latestObservations.map { it.copy(lastActivityAtMillis = null) },
                    batch = tracker.finish(stoppedAtMillis),
                    nowMillis = stoppedAtMillis,
                )
            }
        }
        throw error
    }
}

@OptIn(ExperimentalTime::class)
private fun FriendActivitySourceSnapshot.toInputSnapshot(
        socketPresenceEvent: FriendSocketPresenceEvent? = null,
        trackingControl: FriendActivityTrackingControl? = null,
        includeLastActivity: Boolean = false,
        updateLastActivityOnly: Boolean = false,
        observedAtMillis: Long = Clock.System.now().toEpochMilliseconds(),
) = FriendActivityInputSnapshot(
    token = token,
    friends = friends.map { friend ->
        FriendActivityObservation(
            userId = friend.id,
            displayName = friend.displayName,
            profileImageUrl = friend.profileImageUrl,
            location = friend.location,
            status = friend.status.value,
            statusDescription = friend.statusDescription,
            bio = friend.bio.orEmpty(),
            lastActivityAtMillis = friend.lastActivity.toEpochMillisOrNull()
                ?.takeIf { includeLastActivity }
                ?.takeIf { friend.status == io.github.vrcmteam.vrcm.network.api.attributes.UserStatus.Offline },
        )
    },
    selfLocation = selfLocation,
    observedAtMillis = observedAtMillis,
    socketPresenceEvent = socketPresenceEvent,
    trackingControl = trackingControl,
    updateLastActivityOnly = updateLastActivityOnly,
)

/** Keeps the event token authoritative and only enriches it from the same session snapshot. */
internal fun FriendActivitySourceSnapshot?.toSocketPresenceInputSnapshot(
    eventToken: AccountSessionToken,
    presenceEvent: FriendSocketPresenceEvent,
): FriendActivityInputSnapshot = this?.takeIf { it.token == eventToken }?.toInputSnapshot(presenceEvent)
    ?: FriendActivityInputSnapshot(
        token = eventToken,
        friends = emptyList(),
        selfLocation = null,
        observedAtMillis = presenceEvent.occurredAtMillis,
        socketPresenceEvent = presenceEvent,
    )

@OptIn(ExperimentalTime::class)
private fun String.toEpochMillisOrNull(): Long? =
    takeIf(String::isNotBlank)?.let { raw ->
        runCatching { Instant.parse(raw).toEpochMilliseconds() }.getOrNull()
    }

private fun FriendActivitySummaryEntity.toSummary() = FriendActivitySummary(
    friendUserId = friendUserId,
    displayName = displayName,
    profileImageUrl = profileImageUrl,
    lastSeenTogetherAtMillis = lastSeenTogetherAtMillis,
    meetingCount = meetingCount,
    togetherDurationMillis = togetherDurationMillis,
    lastOnlineAtMillis = lastOnlineAtMillis,
    lastOfflineAtMillis = lastOfflineAtMillis,
    lastActivityAtMillis = lastActivityAtMillis,
)

private fun FriendActivityEventEntity.toEventOrNull(): FriendActivityEvent? {
    val eventType = FriendActivityEventType.entries.firstOrNull { it.name == type } ?: return null
    val eventAccessType = accessType?.let { stored ->
        FriendActivityAccessType.entries.firstOrNull { it.name == stored }
    }
    return FriendActivityEvent(
        id = id,
        friendUserId = friendUserId,
        type = eventType,
        occurredAtMillis = occurredAtMillis,
        previousValue = previousValue,
        currentValue = currentValue,
        worldId = worldId,
        worldName = worldName,
        accessType = eventAccessType,
    )
}
