package io.github.vrcmteam.vrcm.storage

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(tableName = "friend_activity_generations")
internal data class FriendActivityGenerationEntity(
    @androidx.room.PrimaryKey
    val ownerUserId: String,
    val generation: Long,
)

@Entity(
    tableName = "friend_activity_summaries",
    primaryKeys = ["ownerUserId", "friendUserId"],
    foreignKeys = [
        ForeignKey(
            entity = FriendActivityGenerationEntity::class,
            parentColumns = ["ownerUserId"],
            childColumns = ["ownerUserId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("ownerUserId"), Index(value = ["ownerUserId", "lastSeenTogetherAtMillis"])],
)
internal data class FriendActivitySummaryEntity(
    val ownerUserId: String,
    val friendUserId: String,
    val displayName: String,
    val profileImageUrl: String,
    val lastSeenTogetherAtMillis: Long? = null,
    val meetingCount: Int = 0,
    val togetherDurationMillis: Long = 0L,
    val lastOnlineAtMillis: Long? = null,
    val lastOfflineAtMillis: Long? = null,
    val lastActivityAtMillis: Long? = null,
)

@Entity(
    tableName = "friend_activity_events",
    foreignKeys = [
        ForeignKey(
            entity = FriendActivityGenerationEntity::class,
            parentColumns = ["ownerUserId"],
            childColumns = ["ownerUserId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [
        Index("ownerUserId"),
        Index(value = ["ownerUserId", "friendUserId", "occurredAtMillis"]),
    ],
)
internal data class FriendActivityEventEntity(
    @androidx.room.PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val ownerUserId: String,
    val friendUserId: String,
    val displayName: String,
    val profileImageUrl: String,
    val type: String,
    val occurredAtMillis: Long,
    val previousValue: String? = null,
    val currentValue: String? = null,
    val worldId: String? = null,
    val worldName: String? = null,
    val accessType: String? = null,
)

@Entity(
    tableName = "friend_activity_sessions",
    foreignKeys = [
        ForeignKey(
            entity = FriendActivityGenerationEntity::class,
            parentColumns = ["ownerUserId"],
            childColumns = ["ownerUserId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [
        Index("ownerUserId"),
        Index(value = ["ownerUserId", "friendUserId", "startedAtMillis"]),
        Index(value = ["ownerUserId", "endedAtMillis"]),
    ],
)
internal data class FriendActivitySessionEntity(
    @androidx.room.PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val ownerUserId: String,
    val friendUserId: String,
    val displayName: String,
    val profileImageUrl: String,
    val startedAtMillis: Long,
    val endedAtMillis: Long? = null,
    val durationMillis: Long = 0L,
    val worldId: String,
    val worldName: String? = null,
    val accessType: String,
    val announced: Boolean,
)
