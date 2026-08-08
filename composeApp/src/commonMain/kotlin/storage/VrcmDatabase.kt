package io.github.vrcmteam.vrcm.storage

import androidx.room.ConstructedBy
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor

@Database(
    entities = [
        FriendActivityGenerationEntity::class,
        FriendActivitySummaryEntity::class,
        FriendActivityEventEntity::class,
        FriendActivitySessionEntity::class,
        UserProfileCacheEntity::class,
    ],
    version = 3,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3)],
    exportSchema = true,
)
@ConstructedBy(VrcmDatabaseConstructor::class)
internal abstract class VrcmDatabase : RoomDatabase() {
    abstract fun friendActivityDao(): FriendActivityDao

    abstract fun userProfileCacheDao(): UserProfileCacheRoomDao
}

@Suppress("NO_ACTUAL_FOR_EXPECT")
internal expect object VrcmDatabaseConstructor : RoomDatabaseConstructor<VrcmDatabase> {
    override fun initialize(): VrcmDatabase
}
