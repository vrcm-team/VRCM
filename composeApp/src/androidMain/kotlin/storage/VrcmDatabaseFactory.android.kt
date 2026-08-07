package io.github.vrcmteam.vrcm.storage

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.driver.AndroidSQLiteDriver
import io.github.vrcmteam.vrcm.AndroidAppPlatform
import io.github.vrcmteam.vrcm.AppPlatform

internal actual fun platformVrcmDatabaseBuilder(
    appPlatform: AppPlatform,
): RoomDatabase.Builder<VrcmDatabase> {
    val context = (appPlatform as AndroidAppPlatform).context
    return Room.databaseBuilder(
        context = context,
        klass = VrcmDatabase::class.java,
        name = context.getDatabasePath(VRCM_DATABASE_NAME).absolutePath,
    ).setDriver(AndroidSQLiteDriver())
}
