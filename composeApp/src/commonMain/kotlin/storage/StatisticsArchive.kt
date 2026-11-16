package io.github.vrcmteam.vrcm.storage

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.execSQL
import io.github.vrcmteam.vrcm.service.FriendActivityEventType
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

internal enum class StatisticsTransferFailure { InvalidFile, WrongAccount, UnsupportedVersion, Empty, SessionChanged, Io }

internal class StatisticsTransferException(val failure: StatisticsTransferFailure) : Exception(failure.name)

/** VRCX 的数据库交换格式。仅复制统计白名单，永远不读写 cookies、configs 或凭据。 */
@OptIn(ExperimentalTime::class)
internal class StatisticsArchive(private val driver: SQLiteDriver) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun write(path: String, snapshot: StatisticsSnapshot) {
        validate(snapshot)
        driver.open(path).use { db ->
            db.execSQL("PRAGMA journal_mode = DELETE")
            db.execSQL("BEGIN")
            try {
                db.execSQL("CREATE TABLE vrcm_statistics (owner_user_id TEXT PRIMARY KEY, version INTEGER, payload TEXT)")
                db.insert("vrcm_statistics", listOf("owner_user_id", "version", "payload"),
                    listOf(snapshot.ownerUserId, "1", json.encodeToString(snapshot)))
                val rows = rowsForExport(snapshot)
                TABLES.forEach { table ->
                    val name = table.name(snapshot.ownerUserId)
                    db.execSQL("CREATE TABLE $name (id INTEGER PRIMARY KEY, ${table.definition})")
                    rows[table.suffix].orEmpty().forEach { row ->
                        db.insert(name, table.columns, table.columns.map(row::get))
                    }
                }
                db.execSQL("COMMIT")
            } catch (error: Throwable) {
                db.execSQL("ROLLBACK")
                throw error
            }
        }
    }

    fun read(path: String, ownerUserId: String): StatisticsSnapshot {
        userPrefix(ownerUserId)
        return driver.open(path).use { db ->
            db.execSQL("PRAGMA query_only = ON")
            db.execSQL("PRAGMA trusted_schema = OFF")
            val names = buildSet {
                db.prepare("SELECT name FROM sqlite_master WHERE type = 'table'").use { query ->
                    while (query.step()) add(query.getText(0))
                }
            }
            val native = if ("vrcm_statistics" in names) {
                db.prepare("SELECT version, payload FROM vrcm_statistics WHERE owner_user_id = ?").use { query ->
                    query.bindText(1, ownerUserId)
                    if (!query.step()) null else {
                        if (query.getLong(0) != 1L) fail(StatisticsTransferFailure.UnsupportedVersion)
                        json.decodeFromString<StatisticsSnapshot>(query.getText(1)).also(::validate)
                    }
                }
            } else null
            if (native?.ownerUserId?.let { it != ownerUserId } == true) fail(StatisticsTransferFailure.WrongAccount)
            val hasOwnTables = TABLES.any { it.accountScoped && it.name(ownerUserId) in names }
            if (native == null && !hasOwnTables) {
                fail(if (names.any { it.endsWith("_feed_online_offline") || it.endsWith("_feed_gps") })
                    StatisticsTransferFailure.WrongAccount else StatisticsTransferFailure.InvalidFile)
            }
            val rows = TABLES.associate { table ->
                val name = table.name(ownerUserId)
                table.suffix to if (name in names) db.readRows(name, table) else emptyList()
            }
            // 自带快照已包含这些行；只转换 VRCX 后来新增的行，避免再生成一套相遇事件。
            val existing = native?.let(::rowsForExport).orEmpty()
            val added = rows.mapValues { (table, values) -> values.toSet() - existing[table].orEmpty().toSet() }
            val converted = convertRows(ownerUserId, added.mapValues { it.value.toList() })
            val importedRows = (if (native == null) rows else added.mapValues { it.value.toList() }).flatMap { (table, values) -> values.map { row ->
                ImportedStatisticsRowEntity(ownerUserId, table, json.encodeToString(row))
            } }
            val result = if (native == null) converted.copy(importedRows = importedRows) else {
                val sessions = mergeStatisticsSessions(native.sessions, converted.sessions, preserveLocalOpenSessions = false)
                native.copy(
                    summaries = mergeStatisticsSummaries(native, converted, sessions),
                    sessions = sessions,
                    events = (native.events + converted.events).distinctBy { it.statisticsKey() },
                    importedRows = (native.importedRows + importedRows).distinct(),
                )
            }
            validate(result)
            if (result.isEmpty) fail(StatisticsTransferFailure.Empty)
            result
        }
    }

    private fun rowsForExport(snapshot: StatisticsSnapshot): Map<String, List<Map<String, String?>>> {
        val rows = TABLES.associate { it.suffix to linkedSetOf<Map<String, String?>>() }
        snapshot.importedRows.forEach { row ->
            val table = TABLES.firstOrNull { it.suffix == row.tableName } ?: fail(StatisticsTransferFailure.InvalidFile)
            val values = json.decodeFromString<Map<String, String?>>(row.payload)
            rows.getValue(table.suffix).add(table.columns.associateWith(values::get))
        }
        val originalIdentities = rows.mapValues { (table, values) -> values.mapTo(mutableSetOf()) { rowIdentity(table, it) } }
        val legacyIdentities = rows.mapValues { (table, values) -> values.filter { it["user_id"].isNullOrBlank() }
            .mapTo(mutableSetOf()) { rowIdentity(table, it) } }
        fun add(tableName: String, values: Map<String, String?>) {
            val table = TABLES.first { it.suffix == tableName }
            val row = table.columns.associateWith(values::get)
            // 原始表保留同一时间戳下的所有不同记录；映射出的 UI 事件无需再写一份。
            if (rowIdentity(tableName, row) !in originalIdentities.getValue(tableName) &&
                rowIdentity(tableName, row + ("user_id" to null)) !in legacyIdentities.getValue(tableName)) {
                rows.getValue(tableName).add(row)
            }
        }
        val locationEnteredAt = mutableMapOf<String, Pair<String, Long>>()
        snapshot.events.sortedBy { it.occurredAtMillis }.forEach { event ->
            val common = mapOf("created_at" to iso(event.occurredAtMillis), "user_id" to event.friendUserId,
                "display_name" to event.displayName)
            val location = event.location.ifBlank { event.worldId.orEmpty() }
            val previous = locationEnteredAt[event.friendUserId]
            val previousLocation = if (event.type == "LocationChanged") event.previousValue else location
            val elapsed = previous?.takeIf { it.first == previousLocation }
                ?.let { (event.occurredAtMillis - it.second).coerceAtLeast(0L).toString() }
            when (event.type) {
                "Online", "Offline" -> add("feed_online_offline", common + mapOf(
                    "type" to event.type, "location" to location, "world_name" to event.worldName,
                    "time" to elapsed.takeIf { event.type == "Offline" }))
                "LocationChanged" -> add("feed_gps", common + mapOf(
                    "location" to location, "world_name" to event.worldName,
                    "previous_location" to event.previousValue, "time" to elapsed))
                "StatusChanged" -> add("feed_status", common + mapOf(
                    "status" to event.currentValue?.substringBefore('\n'),
                    "status_description" to event.currentValue?.substringAfter('\n', ""),
                    "previous_status" to event.previousValue?.substringBefore('\n'),
                    "previous_status_description" to event.previousValue?.substringAfter('\n', "")))
                "BioChanged" -> add("feed_bio", common + mapOf("bio" to event.currentValue,
                    "previous_bio" to event.previousValue, "bio_links" to "[]", "previous_bio_links" to "[]"))
                "DisplayNameChanged" -> add("friend_log_history", common + mapOf("type" to "DisplayName",
                    "previous_display_name" to event.previousValue))
                "AvatarChanged" -> add("feed_avatar", common + mapOf(
                    "current_avatar_image_url" to event.currentValue,
                    "current_avatar_thumbnail_image_url" to event.currentValue,
                    "previous_current_avatar_image_url" to event.previousValue,
                    "previous_current_avatar_thumbnail_image_url" to event.previousValue))
            }
            when (event.type) {
                "Online", "LocationChanged" -> locationEnteredAt[event.friendUserId] = location to event.occurredAtMillis
                "Offline" -> locationEnteredAt.remove(event.friendUserId)
            }
        }
        val originalMeetings = rows.getValue("gamelog_join_leave")
            .filter { it["type"] == "OnPlayerJoined" }
            .mapTo(mutableSetOf()) { row -> listOf(
                row["user_id"]?.takeIf { it.isNotBlank() } ?: "name:${row["display_name"]}",
                millis(row["created_at"] ?: fail(StatisticsTransferFailure.InvalidFile)),
                row["location"].orEmpty(),
            ) }
        snapshot.sessions.forEach { session ->
            // 原日志可能在相遇途中改名，或只有加入记录；不能再生成一组不同姓名的进出行。
            if (session.statisticsKey() in originalMeetings || listOf(
                    "name:${session.displayName}", session.startedAtMillis, session.location,
                ) in originalMeetings) return@forEach
            val common = mapOf("user_id" to session.friendUserId, "display_name" to session.displayName,
                "location" to session.location.ifBlank { session.worldId })
            add("gamelog_join_leave", common + mapOf("type" to "OnPlayerJoined",
                "created_at" to iso(session.startedAtMillis)))
            val end = session.endedAtMillis ?: (session.startedAtMillis + session.durationMillis)
            add("gamelog_join_leave", common + mapOf("type" to "OnPlayerLeft", "created_at" to iso(end),
                "time" to session.durationMillis.toString()))
        }
        return rows.mapValues { (_, values) -> values.sortedBy { it["created_at"] } }
    }

    private fun rowIdentity(table: String, row: Map<String, String?>): List<String?> =
        listOf(table, row["created_at"]?.let(::millis)?.toString(),
            row["user_id"]?.takeIf { it.isNotBlank() } ?: row["display_name"], row["type"], row["location"])

    private fun convertRows(owner: String, rows: Map<String, List<Map<String, String?>>>): StatisticsSnapshot {
        val events = mutableListOf<FriendActivityEventEntity>()
        val sessions = mutableListOf<FriendActivitySessionEntity>()
        val summaries = linkedMapOf<String, FriendActivitySummaryEntity>()
        val usersByName = rows.values.flatten().filter { !it["user_id"].isNullOrBlank() }
            .groupBy { it["display_name"] }.mapValues { (_, values) -> values.mapNotNull { it["user_id"] }.distinct() }
        fun user(row: Map<String, String?>): String? = row["user_id"]?.takeIf { it.startsWith("usr_") }
            ?: usersByName[row["display_name"]]?.singleOrNull()
        fun summary(row: Map<String, String?>, id: String): FriendActivitySummaryEntity =
            summaries.getOrPut(id) { FriendActivitySummaryEntity(owner, id, row["display_name"].orEmpty(), "") }
        fun event(row: Map<String, String?>, type: FriendActivityEventType, previous: String? = null, current: String? = null) {
            val id = user(row) ?: return
            val time = millis(row["created_at"] ?: fail(StatisticsTransferFailure.InvalidFile))
            val location = row["location"].orEmpty()
            val original = summary(row, id)
            summaries[id] = original.copy(
                displayName = row["display_name"].orEmpty().ifBlank { original.displayName },
                lastOnlineAtMillis = if (type == FriendActivityEventType.Online)
                    maxOf(original.lastOnlineAtMillis ?: 0L, time) else original.lastOnlineAtMillis,
                lastOfflineAtMillis = if (type == FriendActivityEventType.Offline)
                    maxOf(original.lastOfflineAtMillis ?: 0L, time) else original.lastOfflineAtMillis,
            )
            events += FriendActivityEventEntity(ownerUserId = owner, friendUserId = id,
                displayName = row["display_name"].orEmpty(), profileImageUrl = "", type = type.name,
                occurredAtMillis = time, previousValue = previous, currentValue = current,
                worldId = location.substringBefore(':').takeIf { it.startsWith("wrld_") },
                worldName = row["world_name"], accessType = accessType(location), location = location)
        }
        rows["feed_online_offline"].orEmpty().forEach { row ->
            when (row["type"]) {
                "Online" -> event(row, FriendActivityEventType.Online)
                "Offline" -> event(row, FriendActivityEventType.Offline)
            }
        }
        rows["feed_gps"].orEmpty().forEach { event(it, FriendActivityEventType.LocationChanged, it["previous_location"]) }
        rows["feed_status"].orEmpty().forEach { row ->
            fun status(prefix: String) = listOfNotNull(row["${prefix}status"],
                row["${prefix}status_description"]?.takeIf { it.isNotEmpty() }).joinToString("\n")
            event(row, FriendActivityEventType.StatusChanged, status("previous_"), status(""))
        }
        rows["feed_bio"].orEmpty().forEach { event(it, FriendActivityEventType.BioChanged, it["previous_bio"], it["bio"]) }
        rows["feed_avatar"].orEmpty().forEach { event(it, FriendActivityEventType.AvatarChanged,
            it["previous_current_avatar_image_url"], it["current_avatar_image_url"]) }
        rows["friend_log_history"].orEmpty().filter { it["type"] == "DisplayName" }.forEach {
            event(it, FriendActivityEventType.DisplayNameChanged, it["previous_display_name"], it["display_name"])
        }
        // VRCX 的 time 是毫秒；它的游戏日志按进出事件保存，共处时长记在离开行上。
        val active = mutableMapOf<Pair<String, String>, Map<String, String?>>()
        rows["gamelog_join_leave"].orEmpty().sortedBy { millis(it["created_at"]
            ?: fail(StatisticsTransferFailure.InvalidFile)) }.forEach { row ->
            val id = user(row) ?: return@forEach
            if (id == owner) return@forEach
            val location = row["location"].orEmpty()
            val time = millis(row.getValue("created_at") ?: fail(StatisticsTransferFailure.InvalidFile))
            val key = id to location
            val original = summary(row, id)
            summaries[id] = original.copy(lastSeenTogetherAtMillis = maxOf(original.lastSeenTogetherAtMillis ?: 0L, time))
            when (row["type"]) {
                "OnPlayerJoined" -> active[key] = row
                "OnPlayerLeft" -> {
                    val recorded = row["time"]?.let { it.toLongOrNull() ?: fail(StatisticsTransferFailure.InvalidFile) }
                    if (recorded != null && recorded < 0L) fail(StatisticsTransferFailure.InvalidFile)
                    val start = active.remove(key)?.get("created_at")?.let(::millis) ?: (time - (recorded ?: 0L))
                    if (start < 0L || start > time) fail(StatisticsTransferFailure.InvalidFile)
                    val duration = recorded ?: (time - start)
                    sessions += FriendActivitySessionEntity(ownerUserId = owner, friendUserId = id,
                        displayName = row["display_name"].orEmpty(), profileImageUrl = "", startedAtMillis = start,
                        endedAtMillis = time, durationMillis = duration, worldId = location.substringBefore(':'),
                        accessType = accessType(location), announced = true, location = location)
                    event(row, FriendActivityEventType.Left)
                    event(row + ("created_at" to iso(start)), FriendActivityEventType.Met)
                }
            }
        }
        active.forEach { (key, row) ->
            val start = millis(row["created_at"] ?: fail(StatisticsTransferFailure.InvalidFile))
            sessions += FriendActivitySessionEntity(ownerUserId = owner, friendUserId = key.first,
                displayName = row["display_name"].orEmpty(), profileImageUrl = "", startedAtMillis = start,
                endedAtMillis = start, durationMillis = 0L, worldId = key.second.substringBefore(':'),
                accessType = accessType(key.second), announced = true, location = key.second)
            event(row, FriendActivityEventType.Met)
        }
        val byUser = sessions.groupBy { it.friendUserId }
        return StatisticsSnapshot(owner, summaries.values.map { summary ->
            val meetings = byUser[summary.friendUserId].orEmpty()
            summary.copy(meetingCount = meetings.size, togetherDurationMillis = meetings.sumOf { it.durationMillis })
        }, events, sessions)
    }

    private fun validate(snapshot: StatisticsSnapshot) {
        userPrefix(snapshot.ownerUserId)
        if (snapshot.summaries.any { it.ownerUserId != snapshot.ownerUserId || it.meetingCount < 0 || it.togetherDurationMillis < 0 } ||
            snapshot.events.any { it.ownerUserId != snapshot.ownerUserId || it.occurredAtMillis < 0 ||
                FriendActivityEventType.entries.none { type -> type.name == it.type } } ||
            snapshot.sessions.any { it.ownerUserId != snapshot.ownerUserId || it.startedAtMillis < 0 ||
                it.durationMillis < 0 || it.durationMillis > Long.MAX_VALUE - it.startedAtMillis ||
                (it.endedAtMillis != null && it.endedAtMillis < it.startedAtMillis) } ||
            snapshot.importedRows.any { it.ownerUserId != snapshot.ownerUserId || TABLES.none { table -> table.suffix == it.tableName } }) {
            fail(StatisticsTransferFailure.InvalidFile)
        }
    }

    private fun SQLiteConnection.readRows(name: String, table: StatisticsTable): List<Map<String, String?>> {
        val available = buildSet {
            prepare("PRAGMA table_info($name)").use { query -> while (query.step()) add(query.getText(1)) }
        }
        if ("created_at" !in available) fail(StatisticsTransferFailure.InvalidFile)
        val columns = table.columns.joinToString(", ") { if (it in available) it else "NULL AS $it" }
        return buildList {
            prepare("SELECT $columns FROM $name ORDER BY created_at").use { query ->
                while (query.step()) {
                    val row = table.columns.mapIndexed { index, column ->
                        column to if (query.isNull(index)) null else query.getText(index)
                    }.toMap().toMutableMap()
                    row["created_at"] = iso(millis(row["created_at"] ?: fail(StatisticsTransferFailure.InvalidFile)))
                    add(row)
                }
            }
        }
    }

    private fun SQLiteConnection.insert(table: String, columns: List<String>, values: List<String?>) {
        prepare("INSERT INTO $table (${columns.joinToString()}) VALUES (${columns.joinToString { "?" }})").use { query ->
            values.forEachIndexed { index, value -> if (value == null) query.bindNull(index + 1) else query.bindText(index + 1, value) }
            query.step()
        }
    }

    private fun millis(text: String): Long = try {
        Instant.parse(text.replace(' ', 'T').let { if (it.endsWith('Z') || '+' in it.substringAfter('T') ||
            '-' in it.substringAfter('T')) it else "${it}Z" }).toEpochMilliseconds()
    } catch (_: IllegalArgumentException) { fail(StatisticsTransferFailure.InvalidFile) }

    private fun iso(time: Long): String {
        val value = Instant.fromEpochMilliseconds(time).toString().removeSuffix("Z")
        return if ('.' in value) "${value.substringBefore('.')}.${value.substringAfter('.').padEnd(3, '0')}Z"
            else "$value.000Z"
    }

    private fun accessType(location: String): String = when {
        "~private(" in location && "~canRequestInvite" in location -> "InvitePlus"
        "~private(" in location -> "Invite"
        "~friends(" in location -> "Friends"
        "~hidden(" in location -> "FriendsPlus"
        "~group(" in location -> "Group"
        location.startsWith("wrld_") -> "Public"
        else -> "Unknown"
    }

    private fun fail(failure: StatisticsTransferFailure): Nothing = throw StatisticsTransferException(failure)
}

private data class StatisticsTable(val suffix: String, val definition: String, val accountScoped: Boolean = true) {
    val columns = definition.split(',').map { it.trim().substringBefore(' ') }
    fun name(owner: String): String = if (accountScoped) "${userPrefix(owner)}_$suffix" else suffix
}

private fun userPrefix(owner: String): String {
    if (!Regex("usr_[A-Za-z0-9_-]+").matches(owner)) throw StatisticsTransferException(StatisticsTransferFailure.InvalidFile)
    return owner.replace("-", "").replace("_", "")
}

// 与 VRCX src/services/database/index.js 的列序一致，DBMerger 依赖此顺序。
private val TABLES = listOf(
    StatisticsTable("feed_gps", "created_at TEXT, user_id TEXT, display_name TEXT, location TEXT, world_name TEXT, previous_location TEXT, time INTEGER, group_name TEXT"),
    StatisticsTable("feed_status", "created_at TEXT, user_id TEXT, display_name TEXT, status TEXT, status_description TEXT, previous_status TEXT, previous_status_description TEXT"),
    StatisticsTable("feed_bio", "created_at TEXT, user_id TEXT, display_name TEXT, bio TEXT, previous_bio TEXT, bio_links TEXT, previous_bio_links TEXT"),
    StatisticsTable("feed_avatar", "created_at TEXT, user_id TEXT, display_name TEXT, owner_id TEXT, avatar_name TEXT, current_avatar_image_url TEXT, current_avatar_thumbnail_image_url TEXT, previous_current_avatar_image_url TEXT, previous_current_avatar_thumbnail_image_url TEXT"),
    StatisticsTable("feed_online_offline", "created_at TEXT, user_id TEXT, display_name TEXT, type TEXT, location TEXT, world_name TEXT, time INTEGER, group_name TEXT"),
    StatisticsTable("friend_log_history", "created_at TEXT, type TEXT, user_id TEXT, display_name TEXT, previous_display_name TEXT, trust_level TEXT, previous_trust_level TEXT, friend_number INTEGER"),
    StatisticsTable("gamelog_location", "created_at TEXT, location TEXT, world_id TEXT, world_name TEXT, time INTEGER, group_name TEXT", false),
    StatisticsTable("gamelog_join_leave", "created_at TEXT, type TEXT, display_name TEXT, location TEXT, user_id TEXT, time INTEGER", false),
)
