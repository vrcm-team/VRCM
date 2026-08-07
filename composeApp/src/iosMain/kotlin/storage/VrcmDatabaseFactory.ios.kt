package io.github.vrcmteam.vrcm.storage

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import io.github.vrcmteam.vrcm.AppPlatform
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

@OptIn(ExperimentalForeignApi::class)
internal actual fun platformVrcmDatabaseBuilder(
    appPlatform: AppPlatform,
): RoomDatabase.Builder<VrcmDatabase> {
    val applicationSupport = NSSearchPathForDirectoriesInDomains(
        directory = NSApplicationSupportDirectory,
        domainMask = NSUserDomainMask,
        expandTilde = true,
    ).first() as String
    val directory = "$applicationSupport/VRCM"
    NSFileManager.defaultManager.createDirectoryAtPath(
        path = directory,
        withIntermediateDirectories = true,
        attributes = null,
        error = null,
    )
    return Room.databaseBuilder<VrcmDatabase>(name = "$directory/$VRCM_DATABASE_NAME")
        .setDriver(BundledSQLiteDriver())
}
