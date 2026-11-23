package io.github.vrcmteam.vrcm.storage

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.WRITE

/** Checks the cache table on Room's first open and keeps the original database if recovery is needed. */
internal class RecoveringDesktopSQLiteDriver : SQLiteDriver {
    private val driver = BundledSQLiteDriver()
    private var databasePath: Path? = null

    override fun open(fileName: String): SQLiteConnection = driver.open(resolvePath(Path.of(fileName)).toString())

    @Synchronized
    private fun resolvePath(original: Path): Path = databasePath ?: recoverIfNeeded(original).also { databasePath = it }

    private fun recoverIfNeeded(original: Path): Path {
        val recovered = original.resolveSibling("${original.fileName}.recovered")
        if (Files.exists(recovered)) return recovered
        if (!Files.exists(original) || cacheIsHealthy(original)) return original

        val lock = original.resolveSibling("${original.fileName}.recovery.lock")
        synchronized(recoveryLock) {
            FileChannel.open(lock, CREATE, WRITE).use { channel ->
                channel.lock().use {
                    if (!Files.exists(recovered)) {
                        val staged = Files.createTempFile(original.toAbsolutePath().parent, "vrcm-recovery-", ".db")
                        try {
                            rebuildWithoutCaches(original, staged)
                            Files.move(staged, recovered, ATOMIC_MOVE)
                        } finally {
                            Files.deleteIfExists(staged)
                        }
                    }
                }
            }
        }
        return recovered
    }

    private fun cacheIsHealthy(path: Path): Boolean =
        // A read-write open lets SQLite finish rolling back a hot journal from an interrupted run.
        driver.open(path.toString(), SQLITE_OPEN_READWRITE).use { db ->
            db.execSQL("PRAGMA busy_timeout = 5000")
            // Room migrates versions that predate cached_blobs on its normal open path.
            val hasCache = db.prepare("SELECT 1 FROM sqlite_schema WHERE type = 'table' AND name = 'cached_blobs'")
                .use { it.step() }
            !hasCache || db.singleText("PRAGMA integrity_check('cached_blobs')") == "ok"
        }

    private fun rebuildWithoutCaches(original: Path, staged: Path) {
        driver.open(staged.toString(), SQLITE_OPEN_READWRITE or SQLITE_OPEN_URI).use { db ->
            db.execSQL("PRAGMA trusted_schema = OFF")
            db.execSQL("PRAGMA busy_timeout = 5000")
            db.prepare("ATTACH DATABASE ? AS damaged").use {
                it.bindText(1, "${original.toUri()}?mode=ro")
                it.step()
            }
            db.execSQL("BEGIN")
            // Copy the existing Room schema and version so older databases can still migrate normally.
            val version = db.prepare("PRAGMA damaged.user_version").use {
                check(it.step())
                it.getLong(0)
            }
            val tables = db.prepare(
                "SELECT name, sql FROM damaged.sqlite_schema WHERE type = 'table' AND name NOT LIKE 'sqlite_%'"
            ).use { statement ->
                buildList { while (statement.step()) add(statement.getText(0) to statement.getText(1)) }
            }
            for ((table, createSql) in tables) {
                db.execSQL(createSql)
                if (table != "cached_blobs") {
                    val name = "\"${table.replace("\"", "\"\"")}\""
                    db.execSQL("INSERT INTO main.$name SELECT * FROM damaged.$name NOT INDEXED")
                }
            }
            db.execSQL("DELETE FROM main.sqlite_sequence")
            db.execSQL("INSERT INTO main.sqlite_sequence SELECT * FROM damaged.sqlite_sequence")
            db.prepare(
                "SELECT sql FROM damaged.sqlite_schema WHERE type = 'index' AND sql IS NOT NULL"
            ).use { statement ->
                while (statement.step()) db.execSQL(statement.getText(0))
            }
            db.execSQL("PRAGMA main.user_version = $version")
            check(db.singleText("PRAGMA main.integrity_check") == "ok") { "Recovered VRCM database failed integrity check" }
            db.prepare("PRAGMA main.foreign_key_check").use {
                check(!it.step()) { "Recovered VRCM database has invalid foreign keys" }
            }
            db.execSQL("COMMIT")
        }
    }

    private fun SQLiteConnection.singleText(sql: String): String = prepare(sql).use {
        check(it.step()) { "No SQLite result for $sql" }
        it.getText(0)
    }

    private companion object {
        val recoveryLock = Any()
        const val SQLITE_OPEN_READWRITE = 0x00000002
        const val SQLITE_OPEN_URI = 0x00000040
    }
}
