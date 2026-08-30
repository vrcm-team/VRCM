package io.github.vrcmteam.vrcm.storage

import io.github.vrcmteam.vrcm.service.FriendActivityBatch
import io.github.vrcmteam.vrcm.service.FriendActivityEventType
import io.github.vrcmteam.vrcm.service.FriendActivityObservation
import kotlinx.coroutines.flow.Flow

interface FriendActivityCacheStore {
    suspend fun clearAccount(ownerUserId: String)

    suspend fun clearAll()
}

internal class RoomFriendActivityStore(
    private val dao: FriendActivityDao,
) : FriendActivityCacheStore {
    suspend fun activateAccount(ownerUserId: String): FriendActivityWriteToken =
        dao.activateAccount(ownerUserId)

    suspend fun record(
        token: FriendActivityWriteToken,
        observations: Collection<FriendActivityObservation>,
        batch: FriendActivityBatch,
        nowMillis: Long,
    ): Boolean = dao.record(token, observations, batch, nowMillis)

    suspend fun discardIncompleteSessions(ownerUserId: String) =
        dao.discardIncompleteSessions(ownerUserId)

    override suspend fun clearAccount(ownerUserId: String) = dao.clearAccount(ownerUserId)

    override suspend fun clearAll() = dao.clearAll()

    suspend fun summary(ownerUserId: String, friendUserId: String): FriendActivitySummaryEntity? =
        dao.summary(ownerUserId, friendUserId)

    fun observeSummary(
        ownerUserId: String,
        friendUserId: String,
    ): Flow<FriendActivitySummaryEntity?> = dao.observeSummary(ownerUserId, friendUserId)

    fun observeRecentTogether(
        ownerUserId: String,
        sinceMillis: Long,
        limit: Int = 20,
    ): Flow<List<FriendActivitySummaryEntity>> = dao.observeRecentTogether(
        ownerUserId = ownerUserId,
        sinceMillis = sinceMillis,
        limit = limit.coerceAtLeast(0),
    )

    fun observeEvents(
        ownerUserId: String,
        friendUserId: String,
        limit: Int = 50,
    ): Flow<List<FriendActivityEventEntity>> = dao.observeEvents(ownerUserId, friendUserId, limit)

    fun observeAllEvents(
        ownerUserId: String,
        types: Set<FriendActivityEventType> = emptySet(),
        limit: Int = 200,
        offset: Int = 0,
    ): Flow<List<FriendActivityEventEntity>> {
        val safeLimit = limit.coerceAtLeast(0)
        val safeOffset = offset.coerceAtLeast(0)
        return if (types.isEmpty()) {
            dao.observeAllEvents(ownerUserId, safeLimit, safeOffset)
        } else {
            dao.observeAllEventsByTypes(
                ownerUserId = ownerUserId,
                types = types.map(FriendActivityEventType::name),
                limit = safeLimit,
                offset = safeOffset,
            )
        }
    }

    fun observeAllEventsBefore(
        ownerUserId: String,
        types: Set<FriendActivityEventType> = emptySet(),
        beforeOccurredAtMillis: Long,
        beforeId: Long,
        limit: Int = 200,
    ): Flow<List<FriendActivityEventEntity>> {
        val safeLimit = limit.coerceAtLeast(0)
        return if (types.isEmpty()) {
            dao.observeAllEventsBefore(
                ownerUserId = ownerUserId,
                beforeOccurredAtMillis = beforeOccurredAtMillis,
                beforeId = beforeId,
                limit = safeLimit,
            )
        } else {
            dao.observeAllEventsByTypesBefore(
                ownerUserId = ownerUserId,
                types = types.map(FriendActivityEventType::name),
                beforeOccurredAtMillis = beforeOccurredAtMillis,
                beforeId = beforeId,
                limit = safeLimit,
            )
        }
    }

    fun observeAllEventsThrough(
        ownerUserId: String,
        types: Set<FriendActivityEventType> = emptySet(),
        oldestOccurredAtMillis: Long,
        oldestId: Long,
    ): Flow<List<FriendActivityEventEntity>> = if (types.isEmpty()) {
        dao.observeAllEventsThrough(ownerUserId, oldestOccurredAtMillis, oldestId)
    } else {
        dao.observeAllEventsByTypesThrough(
            ownerUserId = ownerUserId,
            types = types.map(FriendActivityEventType::name),
            oldestOccurredAtMillis = oldestOccurredAtMillis,
            oldestId = oldestId,
        )
    }

    suspend fun cachedWorldName(ownerUserId: String, worldId: String): String? =
        dao.cachedWorldName(ownerUserId, worldId)

    suspend fun cacheWorldName(ownerUserId: String, worldId: String, worldName: String) =
        dao.cacheWorldName(ownerUserId, worldId, worldName)

    suspend fun sessions(
        ownerUserId: String,
        friendUserId: String,
    ): List<FriendActivitySessionEntity> = dao.sessions(ownerUserId, friendUserId)
}
