package io.github.vrcmteam.vrcm.storage

import androidx.room.Room
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import io.github.vrcmteam.vrcm.service.FriendActivityEventType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.io.path.absolutePathString
import kotlin.test.Test
import kotlin.test.assertEquals

class VrcmDatabaseMigrationTest {
    @Test
    fun migrationFrom4To5PreservesEventsAndReplacesTimelineIndexes() = runTest {
        val directory = Files.createTempDirectory("vrcm-migration-")
        val databaseFile = directory.resolve("vrcm.db")
        val expectedEvent = FriendActivityEventEntity(
            id = 7L,
            ownerUserId = "usr_owner",
            friendUserId = "usr_friend",
            displayName = "Friend",
            profileImageUrl = "https://example.com/friend.png",
            type = FriendActivityEventType.Online.name,
            occurredAtMillis = 1_234_567L,
            previousValue = "offline",
            currentValue = "online",
            worldId = "wrld_test",
            worldName = "Test World",
            accessType = "Public",
        )

        try {
            createVersion4Database(databaseFile.absolutePathString(), expectedEvent)

            val database = Room.databaseBuilder<VrcmDatabase>(name = databaseFile.absolutePathString())
                .setDriver(BundledSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.IO)
                .build()
            try {
                assertEquals(
                    listOf(expectedEvent),
                    database.friendActivityDao().observeAllEvents(
                        ownerUserId = expectedEvent.ownerUserId,
                        limit = 10,
                        offset = 0,
                    ).first(),
                )
            } finally {
                database.close()
            }

            BundledSQLiteDriver().open(databaseFile.absolutePathString()).use { connection ->
                assertEquals(5L, connection.queryLong("PRAGMA user_version"))
                assertEquals(
                    mapOf(
                        "index_friend_activity_events_ownerUserId_friendUserId_occurredAtMillis" to
                            listOf("ownerUserId", "friendUserId", "occurredAtMillis"),
                        "index_friend_activity_events_ownerUserId_occurredAtMillis_id" to
                            listOf("ownerUserId", "occurredAtMillis", "id"),
                        "index_friend_activity_events_ownerUserId_type_occurredAtMillis_id" to
                            listOf("ownerUserId", "type", "occurredAtMillis", "id"),
                    ),
                    connection.friendActivityEventIndexes(),
                )
            }
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    private fun createVersion4Database(
        databasePath: String,
        event: FriendActivityEventEntity,
    ) {
        BundledSQLiteDriver().open(databasePath).use { connection ->
            connection.execSQL("PRAGMA foreign_keys = ON")
            VERSION_4_SCHEMA.forEach(connection::execSQL)
            connection.prepare(
                "INSERT INTO friend_activity_generations (ownerUserId, generation) VALUES (?, ?)"
            ).use { statement ->
                statement.bindText(1, event.ownerUserId)
                statement.bindLong(2, 3L)
                statement.step()
            }
            connection.prepare(
                "INSERT INTO friend_activity_events " +
                    "(id, ownerUserId, friendUserId, displayName, profileImageUrl, type, occurredAtMillis, " +
                    "previousValue, currentValue, worldId, worldName, accessType) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
            ).use { statement ->
                statement.bindLong(1, event.id)
                statement.bindText(2, event.ownerUserId)
                statement.bindText(3, event.friendUserId)
                statement.bindText(4, event.displayName)
                statement.bindText(5, event.profileImageUrl)
                statement.bindText(6, event.type)
                statement.bindLong(7, event.occurredAtMillis)
                statement.bindText(8, checkNotNull(event.previousValue))
                statement.bindText(9, checkNotNull(event.currentValue))
                statement.bindText(10, checkNotNull(event.worldId))
                statement.bindText(11, checkNotNull(event.worldName))
                statement.bindText(12, checkNotNull(event.accessType))
                statement.step()
            }
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS room_master_table " +
                    "(id INTEGER PRIMARY KEY, identity_hash TEXT)"
            )
            connection.execSQL(
                "INSERT OR REPLACE INTO room_master_table (id, identity_hash) " +
                    "VALUES (42, '$VERSION_4_IDENTITY_HASH')"
            )
            connection.execSQL("PRAGMA user_version = 4")
        }
    }

    private fun SQLiteConnection.queryLong(sql: String): Long = prepare(sql).use { statement ->
        check(statement.step()) { "Query returned no rows: $sql" }
        statement.getLong(0)
    }

    private fun SQLiteConnection.friendActivityEventIndexes(): Map<String, List<String>> {
        val indexNames = prepare(
            "SELECT name FROM sqlite_master " +
                "WHERE type = 'index' AND tbl_name = 'friend_activity_events' " +
                "AND name NOT LIKE 'sqlite_%' ORDER BY name"
        ).use { statement ->
            buildList {
                while (statement.step()) add(statement.getText(0))
            }
        }
        return indexNames.associateWith { indexName ->
            prepare("PRAGMA index_info(`$indexName`)").use { statement ->
                buildList {
                    while (statement.step()) add(statement.getText(2))
                }
            }
        }
    }

    private companion object {
        const val VERSION_4_IDENTITY_HASH = "a834f82f640fe40d4170f619f62ce48c"

        val VERSION_4_SCHEMA = listOf(
            """CREATE TABLE IF NOT EXISTS `friend_activity_generations` (`ownerUserId` TEXT NOT NULL, `generation` INTEGER NOT NULL, PRIMARY KEY(`ownerUserId`))""",
            """CREATE TABLE IF NOT EXISTS `friend_activity_summaries` (`ownerUserId` TEXT NOT NULL, `friendUserId` TEXT NOT NULL, `displayName` TEXT NOT NULL, `profileImageUrl` TEXT NOT NULL, `lastSeenTogetherAtMillis` INTEGER, `meetingCount` INTEGER NOT NULL, `togetherDurationMillis` INTEGER NOT NULL, `lastOnlineAtMillis` INTEGER, `lastOfflineAtMillis` INTEGER, `lastActivityAtMillis` INTEGER, PRIMARY KEY(`ownerUserId`, `friendUserId`), FOREIGN KEY(`ownerUserId`) REFERENCES `friend_activity_generations`(`ownerUserId`) ON UPDATE NO ACTION ON DELETE CASCADE )""",
            """CREATE INDEX IF NOT EXISTS `index_friend_activity_summaries_ownerUserId` ON `friend_activity_summaries` (`ownerUserId`)""",
            """CREATE INDEX IF NOT EXISTS `index_friend_activity_summaries_ownerUserId_lastSeenTogetherAtMillis` ON `friend_activity_summaries` (`ownerUserId`, `lastSeenTogetherAtMillis`)""",
            """CREATE TABLE IF NOT EXISTS `friend_activity_events` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `ownerUserId` TEXT NOT NULL, `friendUserId` TEXT NOT NULL, `displayName` TEXT NOT NULL, `profileImageUrl` TEXT NOT NULL, `type` TEXT NOT NULL, `occurredAtMillis` INTEGER NOT NULL, `previousValue` TEXT, `currentValue` TEXT, `worldId` TEXT, `worldName` TEXT, `accessType` TEXT, FOREIGN KEY(`ownerUserId`) REFERENCES `friend_activity_generations`(`ownerUserId`) ON UPDATE NO ACTION ON DELETE CASCADE )""",
            """CREATE INDEX IF NOT EXISTS `index_friend_activity_events_ownerUserId` ON `friend_activity_events` (`ownerUserId`)""",
            """CREATE INDEX IF NOT EXISTS `index_friend_activity_events_ownerUserId_friendUserId_occurredAtMillis` ON `friend_activity_events` (`ownerUserId`, `friendUserId`, `occurredAtMillis`)""",
            """CREATE TABLE IF NOT EXISTS `friend_activity_sessions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `ownerUserId` TEXT NOT NULL, `friendUserId` TEXT NOT NULL, `displayName` TEXT NOT NULL, `profileImageUrl` TEXT NOT NULL, `startedAtMillis` INTEGER NOT NULL, `endedAtMillis` INTEGER, `durationMillis` INTEGER NOT NULL, `worldId` TEXT NOT NULL, `worldName` TEXT, `accessType` TEXT NOT NULL, `announced` INTEGER NOT NULL, FOREIGN KEY(`ownerUserId`) REFERENCES `friend_activity_generations`(`ownerUserId`) ON UPDATE NO ACTION ON DELETE CASCADE )""",
            """CREATE INDEX IF NOT EXISTS `index_friend_activity_sessions_ownerUserId` ON `friend_activity_sessions` (`ownerUserId`)""",
            """CREATE INDEX IF NOT EXISTS `index_friend_activity_sessions_ownerUserId_friendUserId_startedAtMillis` ON `friend_activity_sessions` (`ownerUserId`, `friendUserId`, `startedAtMillis`)""",
            """CREATE INDEX IF NOT EXISTS `index_friend_activity_sessions_ownerUserId_endedAtMillis` ON `friend_activity_sessions` (`ownerUserId`, `endedAtMillis`)""",
            """CREATE TABLE IF NOT EXISTS `cached_blobs` (`scope` TEXT NOT NULL, `cacheKey` TEXT NOT NULL, `groupKey` TEXT NOT NULL, `payload` TEXT NOT NULL, `updatedAtMillis` INTEGER NOT NULL, PRIMARY KEY(`scope`, `cacheKey`))""",
            """CREATE INDEX IF NOT EXISTS `index_cached_blobs_scope_groupKey_updatedAtMillis` ON `cached_blobs` (`scope`, `groupKey`, `updatedAtMillis`)""",
        )
    }
}
