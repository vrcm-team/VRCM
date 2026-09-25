package io.github.vrcmteam.vrcm.storage

import androidx.room.ConstructedBy
import androidx.room.DeleteTable
import androidx.room.migration.AutoMigrationSpec
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
        CachedBlobEntity::class,
    ],
    version = 5,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        // 3 引入过 user_profile_caches 专表，4 统一到 cached_blobs，专表删除。
        AutoMigration(from = 3, to = 4, spec = DropUserProfileCacheTable::class),
        AutoMigration(from = 4, to = 5),
    ],
    exportSchema = true,
)
@ConstructedBy(VrcmDatabaseConstructor::class)
internal abstract class VrcmDatabase : RoomDatabase() {
    abstract fun friendActivityDao(): FriendActivityDao

    abstract fun cachedBlobDao(): CachedBlobDao
}

@DeleteTable(tableName = "user_profile_caches")
internal class DropUserProfileCacheTable : AutoMigrationSpec

@Suppress("NO_ACTUAL_FOR_EXPECT")
internal expect object VrcmDatabaseConstructor : RoomDatabaseConstructor<VrcmDatabase> {
    override fun initialize(): VrcmDatabase
}
