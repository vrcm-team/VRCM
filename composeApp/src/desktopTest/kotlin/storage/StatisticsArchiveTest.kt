package io.github.vrcmteam.vrcm.storage

import androidx.room.Room
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import io.github.vrcmteam.vrcm.service.FriendActivityAccessType
import io.github.vrcmteam.vrcm.service.FriendActivityBatch
import io.github.vrcmteam.vrcm.service.FriendActivityEventType
import io.github.vrcmteam.vrcm.service.FriendMeetingChange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StatisticsArchiveTest {
    private val driver = BundledSQLiteDriver()
    private val archive = StatisticsArchive(driver)

    @Test
    fun nativeRoundTripKeepsLifetimeTotalsAndRepeatedImportsKeepNewLocalMeetings() = runTest {
        withDatabase { database ->
            withFile { path ->
                val original = snapshot()
                archive.write(path, original)
                assertEquals(original, archive.read(path, OWNER))
                val store = RoomFriendActivityStore(database.friendActivityDao())
                val token = store.activateAccount(OWNER)
                repeat(2) { assertTrue(store.mergeStatistics(token, archive.read(path, OWNER))) }
                assertEquals(original.summaries, store.statisticsSnapshot(OWNER).summaries)
                assertEquals(1, store.sessions(OWNER, FRIEND).size)
                val initial = store.statisticsSnapshot(OWNER)
                store.record(token, emptyList(), FriendActivityBatch(meetings = listOf(
                    FriendMeetingChange.Started(FRIEND, 100_000L, "wrld_other", FriendActivityAccessType.Public,
                        announce = true, location = "wrld_other:1234"),
                )), 100_000L)
                store.record(token, emptyList(), FriendActivityBatch(meetings = listOf(
                    FriendMeetingChange.Ended(FRIEND, 105_000L, 5_000L),
                )), 105_000L)
                repeat(2) { assertTrue(store.mergeStatistics(token, archive.read(path, OWNER))) }
                val merged = store.statisticsSnapshot(OWNER)
                assertEquals(13, merged.summaries.single().meetingCount)
                assertEquals(95_000L, merged.summaries.single().togetherDurationMillis)
                assertEquals(2, merged.sessions.size)
                assertTrue(merged.events.containsAll(initial.events))
                val staleToken = token
                store.clearAccount(OWNER)
                assertFalse(store.mergeStatistics(staleToken, original))
                assertTrue(store.statisticsSnapshot(OWNER).isEmpty)
            }
        }
    }

    @Test
    fun vrcxDatabaseImportsStatisticsAndReexportsOriginalFieldsWithoutCredentials() = runTest {
        withDatabase { database ->
            withFile { input ->
                createVrcxDatabase(input)
                val imported = archive.read(input, OWNER)
                val store = RoomFriendActivityStore(database.friendActivityDao())
                val token = store.activateAccount(OWNER)
                repeat(2) { store.mergeStatistics(token, imported) }
                val snapshot = store.statisticsSnapshot(OWNER)
                val summary = snapshot.summaries.single()
                assertEquals(2, summary.meetingCount)
                assertEquals(1_800_000L, summary.togetherDurationMillis)
                assertEquals(setOf("Online", "Offline", "BioChanged", "AvatarChanged", "DisplayNameChanged", "Met", "Left"),
                    snapshot.events.map { it.type }.toSet())
                assertTrue(snapshot.sessions.all { it.location == LOCATION })
                withFile { output ->
                    archive.write(output, snapshot)
                    driver.open(output).use { db ->
                        // 原样使用 VRCX getTimeSpent / getJoinCount 的查询语义；time 的单位是毫秒。
                        assertEquals("1800000", db.scalar("SELECT SUM(time) FROM gamelog_join_leave WHERE type = 'OnPlayerLeft'"))
                        assertEquals("1", db.scalar("SELECT COUNT(DISTINCT location) FROM gamelog_join_leave WHERE type = 'OnPlayerJoined'"))
                        assertEquals("4", db.scalar("SELECT COUNT(*) FROM gamelog_join_leave"))
                        assertEquals("2", db.scalar("SELECT COUNT(*) FROM ${PREFIX}_feed_online_offline"))
                        assertEquals("avatar-owner", db.scalar("SELECT owner_id FROM ${PREFIX}_feed_avatar"))
                        assertEquals("[\"https://example.com\"]", db.scalar("SELECT bio_links FROM ${PREFIX}_feed_bio"))
                        assertEquals("900000", db.scalar("SELECT time FROM gamelog_location"))
                        assertEquals("0", db.scalar("SELECT COUNT(*) FROM sqlite_master WHERE name IN ('cookies', 'configs', 'usrother_feed_bio')"))
                    }
                    val roundTrip = archive.read(output, OWNER)
                    assertEquals(snapshot.summaries, roundTrip.summaries)
                    assertEquals(snapshot.sessions, roundTrip.sessions)
                    assertEquals(snapshot.events, roundTrip.events)
                }
            }
        }
    }

    @Test
    fun laterVrcxRecordsAreMergedWithAnOlderNativeExtension() = runTest {
        withFile { path ->
            archive.write(path, snapshot())
            driver.open(path).use { db ->
                db.execSQL("INSERT INTO gamelog_join_leave (created_at, type, display_name, location, user_id, time) VALUES " +
                    "('1970-01-01T00:02:00.000Z', 'OnPlayerJoined', 'Friend', 'wrld_other:99', '$FRIEND', NULL)," +
                    "('1970-01-01T00:02:10.000Z', 'OnPlayerLeft', 'Friend', 'wrld_other:99', '$FRIEND', 10000)")
                db.execSQL("INSERT INTO ${PREFIX}_feed_bio (created_at, user_id, display_name, bio, previous_bio) VALUES " +
                    "('1970-01-01T00:03:00.000Z', '$FRIEND', 'Friend', 'New bio', 'Old bio')")
            }
            val merged = archive.read(path, OWNER)
            assertEquals(13, merged.summaries.single().meetingCount)
            assertEquals(100_000L, merged.summaries.single().togetherDurationMillis)
            assertEquals(2, merged.sessions.size)
            assertEquals(1, merged.events.count { it.type == "BioChanged" })
            withFile { next ->
                archive.write(next, merged)
                assertEquals(merged, archive.read(next, OWNER))
            }
        }
    }

    @Test
    fun wrongAccountFutureVersionAndMalformedTailAreRejectedBeforeDatabaseChanges() = runTest {
        withDatabase { database ->
            val store = RoomFriendActivityStore(database.friendActivityDao())
            val token = store.activateAccount(OWNER)
            store.mergeStatistics(token, snapshot())
            val before = store.statisticsSnapshot(OWNER)
            withFile { path ->
                archive.write(path, snapshot())
                assertEquals(StatisticsTransferFailure.WrongAccount,
                    assertFailsWith<StatisticsTransferException> { archive.read(path, "usr_other") }.failure)
                driver.open(path).use { it.execSQL("UPDATE vrcm_statistics SET version = 999") }
                assertEquals(StatisticsTransferFailure.UnsupportedVersion,
                    assertFailsWith<StatisticsTransferException> { archive.read(path, OWNER) }.failure)
            }
            withFile { path ->
                createVrcxDatabase(path)
                driver.open(path).use { it.execSQL("INSERT INTO ${PREFIX}_feed_bio (created_at, user_id, display_name, bio) " +
                    "VALUES ('invalid-date', '$FRIEND', 'Friend', 'Invalid tail')") }
                assertEquals(StatisticsTransferFailure.InvalidFile,
                    assertFailsWith<StatisticsTransferException> { archive.read(path, OWNER) }.failure)
            }
            assertEquals(before, store.statisticsSnapshot(OWNER))
        }
    }

    @Test
    fun importingWhileAMeetingIsActivePreservesTheLiveCheckpoint() = runTest {
        withDatabase { database ->
            val store = RoomFriendActivityStore(database.friendActivityDao())
            val token = store.activateAccount(OWNER)
            store.record(token, emptyList(), FriendActivityBatch(meetings = listOf(
                FriendMeetingChange.Started(FRIEND, 1_000L, "wrld_world", FriendActivityAccessType.Public,
                    announce = true, location = LOCATION),
                FriendMeetingChange.Checkpoint(FRIEND, 16_000L, 15_000L),
            )), 16_000L)
            val checkpoint = store.statisticsSnapshot(OWNER)
            store.record(token, emptyList(), FriendActivityBatch(meetings = listOf(
                FriendMeetingChange.Checkpoint(FRIEND, 21_000L, 20_000L),
            )), 21_000L)
            repeat(2) { store.mergeStatistics(token, checkpoint) }
            assertEquals(null, store.sessions(OWNER, FRIEND).single().endedAtMillis)
            store.record(token, emptyList(), FriendActivityBatch(meetings = listOf(
                FriendMeetingChange.Ended(FRIEND, 31_000L, 30_000L),
            )), 31_000L)
            assertEquals(1, store.summary(OWNER, FRIEND)?.meetingCount)
            assertEquals(30_000L, store.summary(OWNER, FRIEND)?.togetherDurationMillis)
        }
    }

    @Test
    fun oldStatisticsSurviveRecordingAfterTimelineDisplayLimits() = runTest {
        withDatabase { database ->
            val store = RoomFriendActivityStore(database.friendActivityDao())
            val token = store.activateAccount(OWNER)
            val original = snapshot().copy(events = (1L..600L).map { time -> snapshot().events.single().copy(
                id = 0L, occurredAtMillis = time,
            ) })
            store.mergeStatistics(token, original)
            store.record(token, emptyList(), FriendActivityBatch(), 365L * 24 * 60 * 60 * 1_000)
            val retained = store.statisticsSnapshot(OWNER)
            assertEquals(600, retained.events.size)
            assertEquals(1, retained.sessions.size)
            assertEquals(original.summaries, retained.summaries)
        }
    }

    @Test
    fun vrcxUnfinishedJoinIsCountedWithoutInventingALeaveRecord() {
        withFile { input ->
            createVrcxDatabase(input)
            driver.open(input).use { db -> db.execSQL("INSERT INTO gamelog_join_leave VALUES " +
                "(5, '2026-11-01T13:00:00.000Z', 'OnPlayerJoined', 'Friend', 'wrld_other:999', '$FRIEND', NULL)") }
            val imported = archive.read(input, OWNER)
            assertEquals(3, imported.summaries.single().meetingCount)
            assertEquals(1_800_000L, imported.summaries.single().togetherDurationMillis)
            withFile { output ->
                archive.write(output, imported)
                driver.open(output).use { db ->
                    assertEquals("5", db.scalar("SELECT COUNT(*) FROM gamelog_join_leave"))
                    assertEquals("2", db.scalar("SELECT COUNT(*) FROM gamelog_join_leave WHERE type = 'OnPlayerLeft'"))
                }
            }
        }
    }

    @Test
    fun vrcxCompletionUpdatesAPreviouslyExportedOpenMeeting() {
        withFile { input ->
            val original = snapshot().let { it.copy(sessions = it.sessions.map { session -> session.copy(endedAtMillis = null) }) }
            archive.write(input, original)
            driver.open(input).use { db -> db.execSQL("INSERT INTO gamelog_join_leave " +
                "(created_at, type, display_name, location, user_id, time) VALUES " +
                "('1970-01-01T00:00:36.000Z', 'OnPlayerLeft', 'Friend', '$LOCATION', '$FRIEND', 35000)") }
            val merged = archive.read(input, OWNER)
            assertEquals(35_000L, merged.sessions.single().durationMillis)
            assertEquals(36_000L, merged.sessions.single().endedAtMillis)
            assertEquals(100_000L, merged.summaries.single().togetherDurationMillis)
            withFile { output ->
                archive.write(output, merged)
                driver.open(output).use { db ->
                    assertEquals("35000", db.scalar("SELECT SUM(time) FROM gamelog_join_leave WHERE type = 'OnPlayerLeft'"))
                }
            }
        }
    }

    @Test
    fun unrelatedNativeAccountDoesNotBlockImportingOwnVrcxTables() {
        withFile { input ->
            createVrcxDatabase(input)
            driver.open(input).use { db ->
                db.execSQL("CREATE TABLE vrcm_statistics (owner_user_id TEXT PRIMARY KEY, version INTEGER, payload TEXT)")
                db.execSQL("INSERT INTO vrcm_statistics VALUES ('usr_other', 999, '{}')")
            }
            assertEquals(1_800_000L, archive.read(input, OWNER).summaries.single().togetherDurationMillis)
        }
    }

    @Test
    fun nativeExportKeepsPerUserLocationDurationsInVrcxFeedColumns() {
        withFile { output ->
            val original = snapshot()
            val online = original.events.single().copy(occurredAtMillis = 1_000L)
            archive.write(output, original.copy(events = listOf(
                online,
                online.copy(type = "LocationChanged", occurredAtMillis = 11_000L, previousValue = LOCATION,
                    location = "wrld_other:99", worldId = "wrld_other"),
                online.copy(friendUserId = "usr_other", occurredAtMillis = 12_000L),
                online.copy(type = "Offline", occurredAtMillis = 20_000L, location = "wrld_other:99", worldId = "wrld_other"),
            )))
            driver.open(output).use { db ->
                assertEquals(LOCATION, db.scalar("SELECT previous_location FROM ${PREFIX}_feed_gps"))
                assertEquals("10000", db.scalar("SELECT time FROM ${PREFIX}_feed_gps"))
                assertEquals("9000", db.scalar("SELECT time FROM ${PREFIX}_feed_online_offline WHERE type = 'Offline'"))
            }
        }
    }

    @Test
    fun differentVrcxRecordsAtTheSameTimestampSurviveExport() {
        withFile { input ->
            createVrcxDatabase(input)
            driver.open(input).use { db -> db.execSQL("INSERT INTO ${PREFIX}_feed_bio VALUES " +
                "(2, '2026-11-01T10:01:00.000Z', '$FRIEND', 'Friend', 'Another bio', 'New bio', '[\"https://another.example.com\"]', '[\"https://example.com\"]')") }
            withFile { output ->
                archive.write(output, archive.read(input, OWNER))
                driver.open(output).use { db ->
                    assertEquals("2", db.scalar("SELECT COUNT(*) FROM ${PREFIX}_feed_bio"))
                    assertEquals("[\"https://another.example.com\"]", db.scalar("SELECT bio_links FROM ${PREFIX}_feed_bio WHERE bio = 'Another bio'"))
                }
            }
        }
    }

    private fun snapshot() = StatisticsSnapshot(
        ownerUserId = OWNER,
        summaries = listOf(FriendActivitySummaryEntity(OWNER, FRIEND, "Friend", "image", 26_000L,
            meetingCount = 12, togetherDurationMillis = 90_000L, lastOnlineAtMillis = 900L,
            lastOfflineAtMillis = 50_000L, lastActivityAtMillis = 51_000L)),
        events = listOf(FriendActivityEventEntity(id = 42L, ownerUserId = OWNER, friendUserId = FRIEND,
            displayName = "Friend", profileImageUrl = "image", type = FriendActivityEventType.Online.name,
            occurredAtMillis = 900L, currentValue = "active\nHello", worldId = "wrld_world", location = LOCATION)),
        sessions = listOf(FriendActivitySessionEntity(id = 52L, ownerUserId = OWNER, friendUserId = FRIEND,
            displayName = "Friend", profileImageUrl = "image", startedAtMillis = 1_000L, endedAtMillis = 26_000L,
            durationMillis = 25_000L, worldId = "wrld_world", worldName = "World", accessType = "Friends",
            announced = true, location = LOCATION)),
    )

    private fun createVrcxDatabase(path: String) {
        driver.open(path).use { db ->
            // 来自 VRCX src/services/database/index.js 的公开数据库列顺序。
            db.execSQL("CREATE TABLE ${PREFIX}_feed_online_offline (id INTEGER PRIMARY KEY, created_at TEXT, user_id TEXT, display_name TEXT, type TEXT, location TEXT, world_name TEXT, time INTEGER, group_name TEXT)")
            db.execSQL("INSERT INTO ${PREFIX}_feed_online_offline VALUES " +
                "(1, '2026-11-01T10:00:00.000Z', '$FRIEND', 'Friend', 'Online', '$LOCATION', 'World', NULL, 'Group')," +
                "(2, '2026-11-01T12:00:00.000Z', '$FRIEND', 'Friend', 'Offline', '$LOCATION', 'World', 7200000, 'Group')")
            db.execSQL("CREATE TABLE ${PREFIX}_feed_bio (id INTEGER PRIMARY KEY, created_at TEXT, user_id TEXT, display_name TEXT, bio TEXT, previous_bio TEXT, bio_links TEXT, previous_bio_links TEXT)")
            db.execSQL("INSERT INTO ${PREFIX}_feed_bio VALUES (1, '2026-11-01T10:01:00.000Z', '$FRIEND', 'Friend', 'New bio', 'Old bio', '[\"https://example.com\"]', '[]')")
            db.execSQL("CREATE TABLE ${PREFIX}_feed_avatar (id INTEGER PRIMARY KEY, created_at TEXT, user_id TEXT, display_name TEXT, owner_id TEXT, avatar_name TEXT, current_avatar_image_url TEXT, current_avatar_thumbnail_image_url TEXT, previous_current_avatar_image_url TEXT, previous_current_avatar_thumbnail_image_url TEXT)")
            db.execSQL("INSERT INTO ${PREFIX}_feed_avatar VALUES (1, '2026-11-01T10:02:00.000Z', '$FRIEND', 'Friend', 'avatar-owner', 'Avatar', 'new-image', 'new-thumb', 'old-image', 'old-thumb')")
            db.execSQL("CREATE TABLE ${PREFIX}_friend_log_history (id INTEGER PRIMARY KEY, created_at TEXT, type TEXT, user_id TEXT, display_name TEXT, previous_display_name TEXT, trust_level TEXT, previous_trust_level TEXT, friend_number INTEGER)")
            db.execSQL("INSERT INTO ${PREFIX}_friend_log_history VALUES (1, '2026-11-01T10:03:00.000Z', 'DisplayName', '$FRIEND', 'Friend', 'Old Friend', NULL, NULL, 5)")
            db.execSQL("CREATE TABLE gamelog_join_leave (id INTEGER PRIMARY KEY, created_at TEXT, type TEXT, display_name TEXT, location TEXT, user_id TEXT, time INTEGER, UNIQUE(created_at, type, display_name))")
            db.execSQL("INSERT INTO gamelog_join_leave VALUES " +
                "(1, '2026-11-01T10:00:00.000Z', 'OnPlayerJoined', 'Old Friend', '$LOCATION', '$FRIEND', NULL)," +
                "(2, '2026-11-01T10:15:00.000Z', 'OnPlayerLeft', 'Friend', '$LOCATION', '$FRIEND', 900000)," +
                "(3, '2026-11-01T11:00:00.000Z', 'OnPlayerJoined', 'Friend', '$LOCATION', '$FRIEND', NULL)," +
                "(4, '2026-11-01T11:15:00.000Z', 'OnPlayerLeft', 'Friend', '$LOCATION', '$FRIEND', 900000)")
            db.execSQL("CREATE TABLE gamelog_location (id INTEGER PRIMARY KEY, created_at TEXT, location TEXT, world_id TEXT, world_name TEXT, time INTEGER, group_name TEXT)")
            db.execSQL("INSERT INTO gamelog_location VALUES (1, '2026-11-01T10:00:00.000Z', '$LOCATION', 'wrld_world', 'World', 900000, 'Group')")
            db.execSQL("CREATE TABLE cookies (value TEXT)")
            db.execSQL("INSERT INTO cookies VALUES ('secret-cookie')")
            db.execSQL("CREATE TABLE configs (key TEXT, value TEXT)")
            db.execSQL("CREATE TABLE usrother_feed_bio (created_at TEXT)")
        }
    }

    private suspend fun withDatabase(block: suspend (VrcmDatabase) -> Unit) {
        val database = Room.inMemoryDatabaseBuilder<VrcmDatabase>().setDriver(driver)
            .setQueryCoroutineContext(Dispatchers.IO).build()
        try { block(database) } finally { database.close() }
    }

    private inline fun withFile(block: (String) -> Unit) {
        val directory = Files.createTempDirectory("vrcm-statistics-test-")
        try { block(directory.resolve("statistics.sqlite3").toString()) }
        finally { directory.toFile().deleteRecursively() }
    }

    private fun SQLiteConnection.scalar(sql: String): String? = prepare(sql).use { query ->
        if (query.step() && !query.isNull(0)) query.getText(0) else null
    }

    private companion object {
        const val OWNER = "usr_11111111-1111-1111-1111-111111111111"
        const val PREFIX = "usr11111111111111111111111111111111"
        const val FRIEND = "usr_22222222-2222-2222-2222-222222222222"
        const val LOCATION = "wrld_world:1234~friends($OWNER)"
    }
}
