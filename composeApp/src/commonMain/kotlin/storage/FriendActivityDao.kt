package io.github.vrcmteam.vrcm.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import io.github.vrcmteam.vrcm.service.FriendActivityBatch
import io.github.vrcmteam.vrcm.service.FriendActivityEventDraft
import io.github.vrcmteam.vrcm.service.FriendActivityEventType
import io.github.vrcmteam.vrcm.service.FriendActivityObservation
import io.github.vrcmteam.vrcm.service.FriendMeetingChange
import kotlinx.coroutines.flow.Flow

internal data class FriendActivityWriteToken(
    val ownerUserId: String,
    val generation: Long,
)

@Dao
internal interface FriendActivityDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertGeneration(entity: FriendActivityGenerationEntity)

    @Query("SELECT generation FROM friend_activity_generations WHERE ownerUserId = :ownerUserId")
    suspend fun generation(ownerUserId: String): Long?

    @Query("UPDATE friend_activity_generations SET generation = generation + 1 WHERE ownerUserId = :ownerUserId")
    suspend fun incrementGeneration(ownerUserId: String)

    @Query("UPDATE friend_activity_generations SET generation = generation + 1")
    suspend fun incrementAllGenerations()

    @Query("SELECT * FROM friend_activity_summaries WHERE ownerUserId = :ownerUserId")
    suspend fun summaries(ownerUserId: String): List<FriendActivitySummaryEntity>

    @Query("SELECT * FROM friend_activity_summaries WHERE ownerUserId = :ownerUserId AND friendUserId = :friendUserId")
    suspend fun summary(ownerUserId: String, friendUserId: String): FriendActivitySummaryEntity?

    @Query("SELECT * FROM friend_activity_summaries WHERE ownerUserId = :ownerUserId AND friendUserId = :friendUserId")
    fun observeSummary(ownerUserId: String, friendUserId: String): Flow<FriendActivitySummaryEntity?>

    @Query(
        "SELECT * FROM friend_activity_summaries " +
            "WHERE ownerUserId = :ownerUserId AND lastSeenTogetherAtMillis >= :sinceMillis " +
            "ORDER BY lastSeenTogetherAtMillis DESC LIMIT :limit"
    )
    fun observeRecentTogether(
        ownerUserId: String,
        sinceMillis: Long,
        limit: Int,
    ): Flow<List<FriendActivitySummaryEntity>>

    @Upsert
    suspend fun upsertSummaries(summaries: List<FriendActivitySummaryEntity>)

    @Insert
    suspend fun insertEvents(events: List<FriendActivityEventEntity>)

    @Query(
        "SELECT * FROM friend_activity_events " +
            "WHERE ownerUserId = :ownerUserId AND friendUserId = :friendUserId " +
            "ORDER BY occurredAtMillis DESC, id DESC LIMIT :limit"
    )
    fun observeEvents(
        ownerUserId: String,
        friendUserId: String,
        limit: Int,
    ): Flow<List<FriendActivityEventEntity>>

    @Query(
        "SELECT * FROM friend_activity_events " +
            "WHERE ownerUserId = :ownerUserId " +
            "ORDER BY occurredAtMillis DESC, id DESC LIMIT :limit OFFSET :offset"
    )
    fun observeAllEvents(
        ownerUserId: String,
        limit: Int,
        offset: Int,
    ): Flow<List<FriendActivityEventEntity>>

    @Query(
        "SELECT * FROM friend_activity_events " +
            "WHERE ownerUserId = :ownerUserId AND type IN (:types) " +
            "ORDER BY occurredAtMillis DESC, id DESC LIMIT :limit OFFSET :offset"
    )
    fun observeAllEventsByTypes(
        ownerUserId: String,
        types: List<String>,
        limit: Int,
        offset: Int,
    ): Flow<List<FriendActivityEventEntity>>

    @Query(
        "SELECT * FROM friend_activity_events " +
            "WHERE ownerUserId = :ownerUserId AND " +
            "(occurredAtMillis < :beforeOccurredAtMillis OR " +
            "(occurredAtMillis = :beforeOccurredAtMillis AND id < :beforeId)) " +
            "ORDER BY occurredAtMillis DESC, id DESC LIMIT :limit"
    )
    fun observeAllEventsBefore(
        ownerUserId: String,
        beforeOccurredAtMillis: Long,
        beforeId: Long,
        limit: Int,
    ): Flow<List<FriendActivityEventEntity>>

    @Query(
        "SELECT * FROM friend_activity_events " +
            "WHERE ownerUserId = :ownerUserId AND type IN (:types) AND " +
            "(occurredAtMillis < :beforeOccurredAtMillis OR " +
            "(occurredAtMillis = :beforeOccurredAtMillis AND id < :beforeId)) " +
            "ORDER BY occurredAtMillis DESC, id DESC LIMIT :limit"
    )
    fun observeAllEventsByTypesBefore(
        ownerUserId: String,
        types: List<String>,
        beforeOccurredAtMillis: Long,
        beforeId: Long,
        limit: Int,
    ): Flow<List<FriendActivityEventEntity>>

    @Query(
        "SELECT * FROM friend_activity_events " +
            "WHERE ownerUserId = :ownerUserId AND " +
            "(occurredAtMillis > :oldestOccurredAtMillis OR " +
            "(occurredAtMillis = :oldestOccurredAtMillis AND id >= :oldestId)) " +
            "ORDER BY occurredAtMillis DESC, id DESC LIMIT :limit"
    )
    fun observeAllEventsThrough(
        ownerUserId: String,
        oldestOccurredAtMillis: Long,
        oldestId: Long,
        limit: Int,
    ): Flow<List<FriendActivityEventEntity>>

    @Query(
        "SELECT * FROM friend_activity_events " +
            "WHERE ownerUserId = :ownerUserId AND type IN (:types) AND " +
            "(occurredAtMillis > :oldestOccurredAtMillis OR " +
            "(occurredAtMillis = :oldestOccurredAtMillis AND id >= :oldestId)) " +
            "ORDER BY occurredAtMillis DESC, id DESC LIMIT :limit"
    )
    fun observeAllEventsByTypesThrough(
        ownerUserId: String,
        types: List<String>,
        oldestOccurredAtMillis: Long,
        oldestId: Long,
        limit: Int,
    ): Flow<List<FriendActivityEventEntity>>

    @Query(
        "SELECT worldName FROM friend_activity_events " +
            "WHERE ownerUserId = :ownerUserId AND worldId = :worldId AND worldName IS NOT NULL " +
            "ORDER BY id DESC LIMIT 1"
    )
    suspend fun cachedEventWorldName(ownerUserId: String, worldId: String): String?

    @Query(
        "SELECT worldName FROM friend_activity_sessions " +
            "WHERE ownerUserId = :ownerUserId AND worldId = :worldId AND worldName IS NOT NULL " +
            "ORDER BY id DESC LIMIT 1"
    )
    suspend fun cachedSessionWorldName(ownerUserId: String, worldId: String): String?

    @Query(
        "UPDATE friend_activity_events SET worldName = :worldName " +
            "WHERE ownerUserId = :ownerUserId AND worldId = :worldId"
    )
    suspend fun updateEventWorldNames(ownerUserId: String, worldId: String, worldName: String)

    @Query(
        "UPDATE friend_activity_sessions SET worldName = :worldName " +
            "WHERE ownerUserId = :ownerUserId AND worldId = :worldId"
    )
    suspend fun updateSessionWorldNames(ownerUserId: String, worldId: String, worldName: String)

    @Query(
        "DELETE FROM friend_activity_events WHERE ownerUserId = :ownerUserId " +
            "AND occurredAtMillis < :cutoffMillis"
    )
    suspend fun deleteEventsBefore(ownerUserId: String, cutoffMillis: Long)

    @Query(
        "DELETE FROM friend_activity_events WHERE ownerUserId = :ownerUserId AND friendUserId = :friendUserId " +
            "AND id NOT IN (SELECT id FROM friend_activity_events " +
            "WHERE ownerUserId = :ownerUserId AND friendUserId = :friendUserId " +
            "ORDER BY occurredAtMillis DESC, id DESC LIMIT :limit)"
    )
    suspend fun trimFriendEvents(ownerUserId: String, friendUserId: String, limit: Int)

    @Insert
    suspend fun insertSession(session: FriendActivitySessionEntity): Long

    @Query(
        "SELECT * FROM friend_activity_sessions " +
            "WHERE ownerUserId = :ownerUserId AND friendUserId = :friendUserId AND endedAtMillis IS NULL " +
            "ORDER BY id DESC LIMIT 1"
    )
    suspend fun openSession(ownerUserId: String, friendUserId: String): FriendActivitySessionEntity?

    @Query(
        "SELECT * FROM friend_activity_sessions " +
            "WHERE ownerUserId = :ownerUserId AND endedAtMillis IS NULL"
    )
    suspend fun incompleteSessions(ownerUserId: String): List<FriendActivitySessionEntity>

    @Query(
        "UPDATE friend_activity_sessions SET endedAtMillis = :endedAtMillis, durationMillis = :durationMillis " +
            "WHERE id = :sessionId"
    )
    suspend fun completeSession(sessionId: Long, endedAtMillis: Long, durationMillis: Long)

    @Query("UPDATE friend_activity_sessions SET durationMillis = :durationMillis WHERE id = :sessionId")
    suspend fun checkpointSession(sessionId: Long, durationMillis: Long)

    @Query(
        "SELECT * FROM friend_activity_sessions " +
            "WHERE ownerUserId = :ownerUserId AND friendUserId = :friendUserId " +
            "ORDER BY startedAtMillis DESC, id DESC"
    )
    suspend fun sessions(ownerUserId: String, friendUserId: String): List<FriendActivitySessionEntity>

    @Query("DELETE FROM friend_activity_sessions WHERE ownerUserId = :ownerUserId AND endedAtMillis IS NULL")
    suspend fun deleteIncompleteSessions(ownerUserId: String)

    @Transaction
    suspend fun discardIncompleteSessions(ownerUserId: String) {
        val summariesByUserId = summaries(ownerUserId)
            .associateByTo(mutableMapOf(), FriendActivitySummaryEntity::friendUserId)
        incompleteSessions(ownerUserId).forEach { session ->
            if (session.durationMillis <= 0L) return@forEach
            val summary = summariesByUserId[session.friendUserId] ?: return@forEach
            summariesByUserId[session.friendUserId] = summary.copy(
                lastSeenTogetherAtMillis = latest(
                    summary.lastSeenTogetherAtMillis,
                    session.startedAtMillis + session.durationMillis,
                ),
            )
        }
        if (summariesByUserId.isNotEmpty()) upsertSummaries(summariesByUserId.values.toList())
        deleteIncompleteSessions(ownerUserId)
    }

    @Query(
        "DELETE FROM friend_activity_sessions WHERE ownerUserId = :ownerUserId " +
            "AND endedAtMillis IS NOT NULL AND endedAtMillis < :cutoffMillis"
    )
    suspend fun deleteSessionsBefore(ownerUserId: String, cutoffMillis: Long)

    @Query("DELETE FROM friend_activity_events WHERE ownerUserId = :ownerUserId")
    suspend fun deleteEvents(ownerUserId: String)

    @Query("DELETE FROM friend_activity_sessions WHERE ownerUserId = :ownerUserId")
    suspend fun deleteSessions(ownerUserId: String)

    @Query("DELETE FROM friend_activity_summaries WHERE ownerUserId = :ownerUserId")
    suspend fun deleteSummaries(ownerUserId: String)

    @Query("DELETE FROM friend_activity_events")
    suspend fun deleteAllEvents()

    @Query("DELETE FROM friend_activity_sessions")
    suspend fun deleteAllSessions()

    @Query("DELETE FROM friend_activity_summaries")
    suspend fun deleteAllSummaries()

    @Transaction
    suspend fun activateAccount(ownerUserId: String): FriendActivityWriteToken {
        insertGeneration(FriendActivityGenerationEntity(ownerUserId, generation = 0L))
        return FriendActivityWriteToken(ownerUserId, checkNotNull(generation(ownerUserId)))
    }

    @Transaction
    suspend fun clearAccount(ownerUserId: String) {
        insertGeneration(FriendActivityGenerationEntity(ownerUserId, generation = 0L))
        incrementGeneration(ownerUserId)
        deleteEvents(ownerUserId)
        deleteSessions(ownerUserId)
        deleteSummaries(ownerUserId)
    }

    @Transaction
    suspend fun clearAll() {
        incrementAllGenerations()
        deleteAllEvents()
        deleteAllSessions()
        deleteAllSummaries()
    }

    @Transaction
    suspend fun cachedWorldName(ownerUserId: String, worldId: String): String? =
        cachedEventWorldName(ownerUserId, worldId)
            ?: cachedSessionWorldName(ownerUserId, worldId)

    @Transaction
    suspend fun cacheWorldName(ownerUserId: String, worldId: String, worldName: String) {
        updateEventWorldNames(ownerUserId, worldId, worldName)
        updateSessionWorldNames(ownerUserId, worldId, worldName)
    }

    @Transaction
    suspend fun record(
        token: FriendActivityWriteToken,
        observations: Collection<FriendActivityObservation>,
        batch: FriendActivityBatch,
        nowMillis: Long,
    ): Boolean {
        if (generation(token.ownerUserId) != token.generation) return false

        val summariesByUserId = summaries(token.ownerUserId)
            .associateByTo(mutableMapOf(), FriendActivitySummaryEntity::friendUserId)
        observations.forEach { observation ->
            val current = summariesByUserId[observation.userId]
            summariesByUserId[observation.userId] = (current ?: FriendActivitySummaryEntity(
                ownerUserId = token.ownerUserId,
                friendUserId = observation.userId,
                displayName = observation.displayName,
                profileImageUrl = observation.profileImageUrl,
            )).copy(
                displayName = observation.displayName,
                profileImageUrl = observation.profileImageUrl,
                lastActivityAtMillis = latest(
                    current?.lastActivityAtMillis,
                    observation.lastActivityAtMillis,
                ),
            )
        }

        val storedEvents = batch.events.map { event ->
            val current = summariesByUserId[event.userId] ?: event.toInitialSummary(token.ownerUserId)
            summariesByUserId[event.userId] = current.copy(
                lastOnlineAtMillis = if (event.type == FriendActivityEventType.Online) {
                    event.occurredAtMillis
                } else current.lastOnlineAtMillis,
                lastOfflineAtMillis = if (event.type == FriendActivityEventType.Offline) {
                    event.occurredAtMillis
                } else current.lastOfflineAtMillis,
            )
            event.toEntity(token.ownerUserId)
        }.toMutableList()

        batch.meetings.forEach { meeting ->
            val observation = observations.firstOrNull { it.userId == meeting.userId }
            val current = summariesByUserId[meeting.userId] ?: FriendActivitySummaryEntity(
                ownerUserId = token.ownerUserId,
                friendUserId = meeting.userId,
                displayName = observation?.displayName.orEmpty(),
                profileImageUrl = observation?.profileImageUrl.orEmpty(),
            )
            when (meeting) {
                is FriendMeetingChange.Started -> {
                    insertSession(
                        FriendActivitySessionEntity(
                            ownerUserId = token.ownerUserId,
                            friendUserId = meeting.userId,
                            displayName = observation?.displayName ?: current.displayName,
                            profileImageUrl = observation?.profileImageUrl ?: current.profileImageUrl,
                            startedAtMillis = meeting.occurredAtMillis,
                            worldId = meeting.worldId,
                            accessType = meeting.accessType.name,
                            announced = meeting.announce,
                        )
                    )
                    summariesByUserId[meeting.userId] = current.copy(
                        meetingCount = current.meetingCount + if (meeting.announce) 1 else 0,
                    )
                    if (meeting.announce) {
                        storedEvents += meeting.toEvent(
                            summary = current,
                            type = FriendActivityEventType.Met,
                            worldId = meeting.worldId,
                            accessType = meeting.accessType.name,
                        )
                    }
                }
                is FriendMeetingChange.Ended -> {
                    val session = openSession(token.ownerUserId, meeting.userId) ?: return@forEach
                    val duration = maxOf(session.durationMillis, meeting.durationMillis)
                    val additionalDuration = duration - session.durationMillis
                    completeSession(session.id, meeting.occurredAtMillis, duration)
                    summariesByUserId[meeting.userId] = current.copy(
                        lastSeenTogetherAtMillis = meeting.occurredAtMillis,
                        togetherDurationMillis = current.togetherDurationMillis + additionalDuration,
                    )
                    if (session.announced) {
                        storedEvents += meeting.toEvent(
                            summary = current,
                            type = FriendActivityEventType.Left,
                            worldId = session.worldId,
                            accessType = session.accessType,
                        )
                    }
                }
                is FriendMeetingChange.Checkpoint -> {
                    val session = openSession(token.ownerUserId, meeting.userId) ?: return@forEach
                    val duration = maxOf(session.durationMillis, meeting.durationMillis)
                    val additionalDuration = duration - session.durationMillis
                    checkpointSession(session.id, duration)
                    summariesByUserId[meeting.userId] = current.copy(
                        togetherDurationMillis = current.togetherDurationMillis + additionalDuration,
                    )
                }
            }
        }

        if (summariesByUserId.isNotEmpty()) upsertSummaries(summariesByUserId.values.toList())
        if (storedEvents.isNotEmpty()) insertEvents(storedEvents)

        val cutoff = nowMillis - EVENT_RETENTION_MILLIS
        deleteEventsBefore(token.ownerUserId, cutoff)
        deleteSessionsBefore(token.ownerUserId, cutoff)
        (batch.events.map { it.userId } + batch.meetings.map { it.userId })
            .distinct()
            .forEach { trimFriendEvents(token.ownerUserId, it, MAX_EVENTS_PER_FRIEND) }
        return true
    }

    private companion object {
        const val EVENT_RETENTION_MILLIS = 180L * 24L * 60L * 60L * 1_000L
        const val MAX_EVENTS_PER_FRIEND = 500
    }
}

private fun latest(first: Long?, second: Long?): Long? = when {
    first == null -> second
    second == null -> first
    else -> maxOf(first, second)
}

private fun FriendActivityEventDraft.toInitialSummary(ownerUserId: String) =
    FriendActivitySummaryEntity(
        ownerUserId = ownerUserId,
        friendUserId = userId,
        displayName = displayName,
        profileImageUrl = profileImageUrl,
    )

private fun FriendActivityEventDraft.toEntity(ownerUserId: String) = FriendActivityEventEntity(
    ownerUserId = ownerUserId,
    friendUserId = userId,
    displayName = displayName,
    profileImageUrl = profileImageUrl,
    type = type.name,
    occurredAtMillis = occurredAtMillis,
    previousValue = previousValue,
    currentValue = currentValue,
    worldId = worldId,
    worldName = null,
    accessType = accessType?.name,
)

private fun FriendMeetingChange.toEvent(
    summary: FriendActivitySummaryEntity,
    type: FriendActivityEventType,
    worldId: String,
    accessType: String,
) = FriendActivityEventEntity(
    ownerUserId = summary.ownerUserId,
    friendUserId = userId,
    displayName = summary.displayName,
    profileImageUrl = summary.profileImageUrl,
    type = type.name,
    occurredAtMillis = occurredAtMillis,
    worldId = worldId,
    accessType = accessType,
)
