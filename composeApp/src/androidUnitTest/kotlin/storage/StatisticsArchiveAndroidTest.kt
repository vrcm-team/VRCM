package io.github.vrcmteam.vrcm.storage

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24, 35])
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class StatisticsArchiveAndroidTest {
    @Test
    fun importsLargeVersionOneSnapshotWithoutLosingUnicodeOrRecords() {
        val owner = "usr_owner"
        val original = StatisticsSnapshot(
            ownerUserId = owner,
            events = List(4_000) { index ->
                FriendActivityEventEntity(
                    ownerUserId = owner,
                    friendUserId = "usr_friend",
                    displayName = "好友🙂",
                    profileImageUrl = "",
                    type = "BioChanged",
                    occurredAtMillis = index.toLong(),
                    currentValue = "$index：" + "好友🙂🌍".repeat(64),
                )
            },
        )
        val file = File.createTempFile("large-statistics-", ".sqlite3", RuntimeEnvironment.getApplication().cacheDir)
        try {
            val driver = statisticsSQLiteDriver()
            val archive = StatisticsArchive(driver)
            archive.write(file.absolutePath, original)
            driver.open(file.absolutePath).use { db ->
                db.prepare("SELECT length(CAST(payload AS BLOB)) FROM vrcm_statistics").use { query ->
                    assertTrue(query.step())
                    assertTrue(query.getLong(0) > 2 * 1024 * 1024)
                }
            }
            assertEquals(original, archive.read(file.absolutePath, owner))
        } finally {
            file.delete()
        }
    }
}
