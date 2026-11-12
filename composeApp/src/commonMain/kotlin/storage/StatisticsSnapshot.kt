package io.github.vrcmteam.vrcm.storage

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import kotlinx.serialization.Serializable

/** 保留 VRCX 的原始统计列，未在 VRCM 展示的字段也能再次导出。 */
@Entity(
    tableName = "imported_statistics_rows",
    primaryKeys = ["ownerUserId", "tableName", "payload"],
    foreignKeys = [ForeignKey(
        entity = FriendActivityGenerationEntity::class,
        parentColumns = ["ownerUserId"],
        childColumns = ["ownerUserId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("ownerUserId")],
)
@Serializable
internal data class ImportedStatisticsRowEntity(
    val ownerUserId: String,
    val tableName: String,
    val payload: String,
)

/** 同一事务读取的账户统计；行 ID 只在源数据库内有效，导入时重新分配。 */
@Serializable
internal data class StatisticsSnapshot(
    val ownerUserId: String,
    val summaries: List<FriendActivitySummaryEntity> = emptyList(),
    val events: List<FriendActivityEventEntity> = emptyList(),
    val sessions: List<FriendActivitySessionEntity> = emptyList(),
    val importedRows: List<ImportedStatisticsRowEntity> = emptyList(),
) {
    val isEmpty: Boolean
        get() = summaries.isEmpty() && events.isEmpty() && sessions.isEmpty() && importedRows.isEmpty()
}

internal fun FriendActivityEventEntity.statisticsKey(): List<Any?> = listOf(
    friendUserId, type, occurredAtMillis, previousValue, currentValue, worldId,
)

internal fun FriendActivitySessionEntity.statisticsKey(): List<Any> = listOf(
    friendUserId, startedAtMillis, location.ifBlank { worldId },
)

/** 合并相同相遇的后续检查点；当前设备正在监测的会话仍由本地监测器结束。 */
internal fun mergeStatisticsSessions(
    local: List<FriendActivitySessionEntity>,
    incoming: List<FriendActivitySessionEntity>,
    preserveLocalOpenSessions: Boolean,
): List<FriendActivitySessionEntity> {
    val sessions = local.associateByTo(linkedMapOf()) { it.statisticsKey() }
    incoming.forEach { imported ->
        val key = imported.statisticsKey()
        val existing = sessions[key]
        val importedEnd = imported.endedAtMillis ?: (imported.startedAtMillis + imported.durationMillis)
        sessions[key] = if (existing == null) {
            imported.copy(id = 0L, endedAtMillis = importedEnd)
        } else {
            existing.copy(
                durationMillis = maxOf(existing.durationMillis, imported.durationMillis),
                endedAtMillis = if (preserveLocalOpenSessions && existing.endedAtMillis == null) null else
                    maxOf(existing.endedAtMillis ?: (existing.startedAtMillis + existing.durationMillis), importedEnd),
                worldName = existing.worldName ?: imported.worldName,
                announced = existing.announced || imported.announced,
            )
        }
    }
    return sessions.values.toList()
}

/**
 * 合并累计值中已经没有对应明细的旧数据，避免重复导入时再次累加。
 * 缺少明细时无法推断两个旧备份的重叠范围，未归属明细的累计值保守取较大值。
 */
internal fun mergeStatisticsSummaries(
    local: StatisticsSnapshot,
    incoming: StatisticsSnapshot,
    sessions: List<FriendActivitySessionEntity>,
): List<FriendActivitySummaryEntity> {
    val localSessions = local.sessions.groupBy { it.friendUserId }
    val incomingSessions = incoming.sessions.groupBy { it.friendUserId }
    val mergedSessions = sessions.groupBy { it.friendUserId }
    val localSummaries = local.summaries.associateBy { it.friendUserId }
    val incomingSummaries = incoming.summaries.associateBy { it.friendUserId }
    return (localSummaries.keys + incomingSummaries.keys).map { userId ->
        val a = localSummaries[userId]
        val b = incomingSummaries[userId]
        val base = a ?: checkNotNull(b)
        fun residualDuration(summary: FriendActivitySummaryEntity?, rows: List<FriendActivitySessionEntity>) =
            ((summary?.togetherDurationMillis ?: 0L) - rows.sumOf { it.durationMillis }).coerceAtLeast(0L)
        fun residualCount(summary: FriendActivitySummaryEntity?, rows: List<FriendActivitySessionEntity>) =
            ((summary?.meetingCount ?: 0) - rows.count { it.announced }).coerceAtLeast(0)
        val aSessions = localSessions[userId].orEmpty()
        val bSessions = incomingSessions[userId].orEmpty()
        val allSessions = mergedSessions[userId].orEmpty()
        base.copy(
            displayName = a?.displayName?.takeIf { it.isNotBlank() } ?: b?.displayName.orEmpty(),
            profileImageUrl = a?.profileImageUrl?.takeIf { it.isNotBlank() } ?: b?.profileImageUrl.orEmpty(),
            meetingCount = maxOf(residualCount(a, aSessions), residualCount(b, bSessions)) +
                allSessions.count { it.announced },
            togetherDurationMillis = maxOf(residualDuration(a, aSessions), residualDuration(b, bSessions)) +
                allSessions.sumOf { it.durationMillis },
            lastSeenTogetherAtMillis = latestStatistic(
                latestStatistic(a?.lastSeenTogetherAtMillis, b?.lastSeenTogetherAtMillis),
                allSessions.mapNotNull { it.endedAtMillis }.maxOrNull(),
            ),
            lastOnlineAtMillis = latestStatistic(a?.lastOnlineAtMillis, b?.lastOnlineAtMillis),
            lastOfflineAtMillis = latestStatistic(a?.lastOfflineAtMillis, b?.lastOfflineAtMillis),
            lastActivityAtMillis = latestStatistic(a?.lastActivityAtMillis, b?.lastActivityAtMillis),
        )
    }
}

private fun latestStatistic(a: Long?, b: Long?): Long? = listOfNotNull(a, b).maxOrNull()
