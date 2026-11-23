package io.github.vrcmteam.vrcm.storage

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.SQLiteDriver
import io.github.vrcmteam.vrcm.AppPlatform
import io.github.vrcmteam.vrcm.di.modules.desktopSettingsDirectory

internal actual fun statisticsSQLiteDriver(): SQLiteDriver = BundledSQLiteDriver()

internal actual fun platformVrcmDatabaseBuilder(
    appPlatform: AppPlatform,
): RoomDatabase.Builder<VrcmDatabase> = Room.databaseBuilder<VrcmDatabase>(
    name = (desktopSettingsDirectory() / VRCM_DATABASE_NAME).toString(),
)
    .setDriver(RecoveringDesktopSQLiteDriver())
    // Bundled SQLite 3.50.1 is affected by the WAL reset race. Use a rollback journal for this file.
    .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
