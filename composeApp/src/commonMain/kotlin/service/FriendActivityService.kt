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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
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
)

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)
class FriendActivityService internal constructor(
    friendService: FriendService,
    private val store: RoomFriendActivityStore,
    private val worldsApi: WorldsApi,
    private val logger: Logger,
) {
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
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
                        snapshots = friendService.friendActivitySource
                            .filterNotNull()
                            .map { it.toInputSnapshot() },
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
    }
}

internal suspend fun trackFriendActivity(
    session: AuthenticatedAccount,
    snapshots: Flow<FriendActivityInputSnapshot>,
    store: RoomFriendActivityStore,
) {
    var writeToken = store.activateAccount(session.account.userId)
    store.discardIncompleteSessions(session.account.userId)
    var tracker = FriendActivityTracker()
    snapshots.collect { snapshot ->
        if (snapshot.token != session.token) return@collect
        val batch = tracker.observe(
            friends = snapshot.friends,
            selfLocation = snapshot.selfLocation,
            nowMillis = snapshot.observedAtMillis,
        )
        val recorded = store.record(
            token = writeToken,
            observations = snapshot.friends,
            batch = batch,
            nowMillis = snapshot.observedAtMillis,
        )
        if (!recorded) {
            writeToken = store.activateAccount(session.account.userId)
            tracker = FriendActivityTracker()
            val baseline = tracker.observe(
                friends = snapshot.friends,
                selfLocation = snapshot.selfLocation,
                nowMillis = snapshot.observedAtMillis,
            )
            store.record(
                token = writeToken,
                observations = snapshot.friends,
                batch = baseline,
                nowMillis = snapshot.observedAtMillis,
            )
        }
    }
}

@OptIn(ExperimentalTime::class)
private fun FriendActivitySourceSnapshot.toInputSnapshot() = FriendActivityInputSnapshot(
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
            lastActivityAtMillis = friend.lastActivity.toEpochMillisOrNull(),
        )
    },
    selfLocation = selfLocation,
    observedAtMillis = Clock.System.now().toEpochMilliseconds(),
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
