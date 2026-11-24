package io.github.vrcmteam.vrcm.service

import io.github.vrcmteam.vrcm.core.shared.AccountSessionToken
import io.github.vrcmteam.vrcm.core.shared.AuthenticatedAccount
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.network.api.attributes.UserStatus
import io.github.vrcmteam.vrcm.network.api.friends.date.FriendData
import io.github.vrcmteam.vrcm.network.api.files.FileApi
import io.github.vrcmteam.vrcm.network.api.files.data.FileTagType
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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
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
    val displayName: String,
    val profileImageUrl: String,
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
    val avatarEvent: FriendActivityEventDraft? = null,
)

internal enum class FriendActivityTrackingControl { Stop, Resume }

internal data class FriendActivityTrackingTransition(
    val sequence: Long,
    val control: FriendActivityTrackingControl,
    val occurredAtMillis: Long,
)

/**
 * Combines every lifecycle owner that can keep friend activity tracking alive.
 * The current control initializes a new account collector at subscription time, while the channel
 * preserves every later transition in occurrence order if database work suspends that collector.
 */
@OptIn(ExperimentalTime::class)
internal class FriendActivityTrackingState(
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    private val lock = SynchronizedObject()
    private var activeSources = 0
    private var sequence = 0L
    private val transitionEvents = Channel<FriendActivityTrackingTransition>(Channel.UNLIMITED)
    private val currentTransition = MutableStateFlow(
        FriendActivityTrackingTransition(
            sequence = sequence,
            control = FriendActivityTrackingControl.Stop,
            occurredAtMillis = nowMillis(),
        )
    )

    val controls: Flow<FriendActivityTrackingTransition> = flow {
        val initial = synchronized(lock) {
            currentTransition.value.copy(occurredAtMillis = nowMillis())
        }
        emit(initial)
        transitionEvents.receiveAsFlow().collect { transition ->
            if (transition.sequence > initial.sequence) {
                emit(transition)
            }
        }
    }

    fun setAppForeground(active: Boolean) =
        setSourceActive(APP_FOREGROUND_SOURCE, active)

    fun setBackgroundMonitoring(active: Boolean) =
        setSourceActive(BACKGROUND_MONITORING_SOURCE, active)

    fun isEnabled(): Boolean =
        currentTransition.value.control == FriendActivityTrackingControl.Resume

    private fun setSourceActive(source: Int, active: Boolean) {
        synchronized(lock) {
            val updatedSources = if (active) {
                activeSources or source
            } else {
                activeSources and source.inv()
            }
            if (updatedSources == activeSources) return@synchronized

            val wasEnabled = activeSources != 0
            activeSources = updatedSources
            val isEnabled = activeSources != 0
            if (wasEnabled == isEnabled) return@synchronized

            val transition = FriendActivityTrackingTransition(
                sequence = ++sequence,
                control = if (isEnabled) {
                    FriendActivityTrackingControl.Resume
                } else {
                    FriendActivityTrackingControl.Stop
                },
                occurredAtMillis = nowMillis(),
            )
            currentTransition.value = transition
            check(transitionEvents.trySend(transition).isSuccess) {
                "Friend activity tracking transition channel is unavailable"
            }
        }
    }

    private companion object {
        const val APP_FOREGROUND_SOURCE = 1
        const val BACKGROUND_MONITORING_SOURCE = 1 shl 1
    }
}

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)
class FriendActivityService internal constructor(
    private val friendService: FriendService,
    private val authService: AuthService,
    private val fileApi: FileApi,
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
                    val avatarFiles = mutableMapOf<String, Boolean>()
                    trackFriendActivity(
                        session = session,
                        snapshots = merge(
                            authService.ownAvatarActivity.mapNotNull { change ->
                                change.takeIf { it.token == session.token && trackingState.isEnabled() }
                                    ?.toInputSnapshot()
                            },
                            friendService.friendAvatarActivity.mapNotNull { change ->
                                if (change.token != session.token || !trackingState.isEnabled()) return@mapNotNull null
                                if (change.needsIconValidation &&
                                    (!isAvatarImage(change.event.currentValue, avatarFiles) ||
                                        !isAvatarImage(change.event.previousValue, avatarFiles))
                                ) return@mapNotNull null
                                change.toInputSnapshot()
                            },
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
                            trackingState.controls.map { transition ->
                                val source = friendService.friendActivitySource.value
                                    ?.takeIf { it.token == session.token }
                                source?.toInputSnapshot(
                                    trackingControl = transition.control,
                                    observedAtMillis = transition.occurredAtMillis,
                                ) ?: FriendActivityInputSnapshot(
                                    token = session.token,
                                    friends = emptyList(),
                                    selfLocation = null,
                                    observedAtMillis = transition.occurredAtMillis,
                                    trackingControl = transition.control,
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

    private suspend fun isAvatarImage(url: String?, cache: MutableMap<String, Boolean>): Boolean {
        val fileId = FileApi.findFileId(url.orEmpty())
        if (fileId.isBlank()) return false
        cache[fileId]?.let { return it }
        val result = withTimeoutOrNull(AVATAR_FILE_TIMEOUT_MILLIS) { fileApi.getFileInfo(fileId) }
        if (result == null) {
            logger.warn("Timed out checking activity avatar file $fileId")
            return false
        }
        val file = result.getOrElse { error ->
            if (error is CancellationException) throw error
            logger.warn("Failed to check activity avatar file $fileId: ${error.message}")
            return false
        }
        val isAvatar = FileTagType.Icon.value !in file.tags &&
            (FileTagType.AvatarImage.value in file.tags ||
                (file.name.startsWith("Avatar - ", ignoreCase = true) && file.name.contains(" - Image - ")))
        cache[fileId] = isAvatar
        return isAvatar
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
                .mapToEventsWithCurrentFriendIcons()
        }
    }

    fun observeAllEvents(
        token: AccountSessionToken,
        types: Set<FriendActivityEventType> = emptySet(),
        limit: Int = 200,
        offset: Int = 0,
    ): Flow<List<FriendActivityEvent>> =
        store.observeAllEvents(token.userId, types, limit, offset)
            .onEach { events ->
                if (SharedFlowCentre.isCurrentSession(token)) {
                    events.asSequence()
                        .filter { it.worldName == null }
                        .mapNotNull(FriendActivityEventEntity::worldId)
                        .distinct()
                        .forEach { worldId -> resolveWorldName(token.userId, worldId) }
                }
            }.mapToEventsWithCurrentFriendIcons().mapNotNull { events ->
                events.takeIf { SharedFlowCentre.isCurrentSession(token) }
            }

    fun observeAllEventsBefore(
        token: AccountSessionToken,
        types: Set<FriendActivityEventType> = emptySet(),
        beforeOccurredAtMillis: Long,
        beforeId: Long,
        limit: Int = 200,
    ): Flow<List<FriendActivityEvent>> =
        store.observeAllEventsBefore(
                ownerUserId = token.userId,
                types = types,
                beforeOccurredAtMillis = beforeOccurredAtMillis,
                beforeId = beforeId,
                limit = limit,
            ).onEach { events ->
                if (SharedFlowCentre.isCurrentSession(token)) {
                    events.asSequence()
                        .filter { it.worldName == null }
                        .mapNotNull(FriendActivityEventEntity::worldId)
                        .distinct()
                        .forEach { worldId -> resolveWorldName(token.userId, worldId) }
                }
            }.mapToEventsWithCurrentFriendIcons().mapNotNull { events ->
                events.takeIf { SharedFlowCentre.isCurrentSession(token) }
            }

    fun observeAllEventsThrough(
        token: AccountSessionToken,
        types: Set<FriendActivityEventType> = emptySet(),
        oldestOccurredAtMillis: Long,
        oldestId: Long,
        limit: Int,
    ): Flow<List<FriendActivityEvent>> =
        store.observeAllEventsThrough(
            ownerUserId = token.userId,
            types = types,
            oldestOccurredAtMillis = oldestOccurredAtMillis,
            oldestId = oldestId,
            limit = limit,
        ).onEach { events ->
            if (SharedFlowCentre.isCurrentSession(token)) {
                events.asSequence()
                    .filter { it.worldName == null }
                    .mapNotNull(FriendActivityEventEntity::worldId)
                    .distinct()
                    .forEach { worldId -> resolveWorldName(token.userId, worldId) }
            }
        }.mapToEventsWithCurrentFriendIcons().mapNotNull { events ->
            events.takeIf { SharedFlowCentre.isCurrentSession(token) }
        }

    private fun Flow<List<FriendActivityEventEntity>>.mapToEventsWithCurrentFriendIcons(): Flow<List<FriendActivityEvent>> =
        combine(friendService.friendState) { entities, friends ->
            entities.mapNotNull { entity ->
                entity.toEventOrNull()?.let { event ->
                    val iconUrl = friends[event.friendUserId]?.iconUrl
                    if (iconUrl.isNullOrBlank()) event else event.copy(profileImageUrl = iconUrl)
                }
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
        worldNameResolver.request(
            scope = serviceScope,
            ownerUserId = ownerUserId,
            worldId = worldId,
            onFailure = { error ->
                logger.error("Friend activity world lookup failed for $worldId: ${error.message}")
            },
        )
    }

    private companion object {
        const val AVATAR_FILE_TIMEOUT_MILLIS = 5_000L
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
    store.finalizeIncompleteSessions(session.account.userId)
    var tracker = FriendActivityTracker(derivePresenceEvents = false)
    var trackingEnabled = true
    var latestObservations: Collection<FriendActivityObservation> = emptyList()
    try {
        snapshots.collect { snapshot ->
            if (snapshot.token != session.token) return@collect
            if (!snapshot.updateLastActivityOnly && snapshot.avatarEvent == null) {
                latestObservations = snapshot.friends
            }
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
                } else if (snapshot.avatarEvent != null) {
                    FriendActivityBatch(events = listOf(snapshot.avatarEvent))
                } else snapshot.socketPresenceEvent?.let {
                    tracker.observeSocketPresence(snapshot.friends, it)
                } ?: tracker.observe(snapshot.friends, snapshot.selfLocation, snapshot.observedAtMillis)
            }
            val observations = snapshot.friends.map { observation ->
                if (snapshot.updateLastActivityOnly) {
                    observation
                } else {
                    observation.copy(
                        lastActivityAtMillis = snapshot.observedAtMillis
                            .takeIf { observation.status != UserStatus.Offline.value },
                    )
                }
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
                val baseline = if (trackingEnabled && !snapshot.updateLastActivityOnly && snapshot.avatarEvent == null) {
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
                    observations = latestObservations.map { observation ->
                        observation.copy(
                            lastActivityAtMillis = stoppedAtMillis
                                .takeIf { observation.status != UserStatus.Offline.value },
                        )
                    },
                    batch = tracker.finish(stoppedAtMillis),
                    nowMillis = stoppedAtMillis,
                )
            }
        }
        throw error
    }
}

private fun AccountAvatarActivity.toInputSnapshot() = FriendActivityInputSnapshot(
    token = token,
    friends = emptyList(),
    selfLocation = null,
    observedAtMillis = event.occurredAtMillis,
    avatarEvent = event,
)

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
            // Keep activity avatars aligned with the friend location cards, which use iconUrl.
            profileImageUrl = friend.iconUrl,
            location = friend.location,
            status = friend.status.value,
            statusDescription = friend.statusDescription,
            bio = friend.bio.orEmpty(),
            lastActivityAtMillis = friend.lastActivity.orEmpty().toEpochMillisOrNull()
                ?.takeIf { includeLastActivity }
                ?.takeIf { friend.status == UserStatus.Offline },
            travelingToLocation = friend.travelingToLocation,
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
        displayName = displayName,
        profileImageUrl = profileImageUrl,
        type = eventType,
        occurredAtMillis = occurredAtMillis,
        previousValue = previousValue,
        currentValue = currentValue,
        worldId = worldId,
        worldName = worldName,
        accessType = eventAccessType,
    )
}
