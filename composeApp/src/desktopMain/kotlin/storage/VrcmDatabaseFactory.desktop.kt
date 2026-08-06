package io.github.vrcmteam.vrcm.storage

import androidx.room.Room
import androidx.room.RoomDatabase
import io.github.vrcmteam.vrcm.AppPlatform
import io.github.vrcmteam.vrcm.di.modules.desktopSettingsDirectory

internal actual fun platformVrcmDatabaseBuilder(
    appPlatform: AppPlatform,
): RoomDatabase.Builder<VrcmDatabase> = Room.databaseBuilder(
    name = (desktopSettingsDirectory() / VRCM_DATABASE_NAME).toString(),
)
