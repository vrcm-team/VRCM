package io.github.vrcmteam.vrcm.storage

import androidx.room.RoomDatabase
import io.github.vrcmteam.vrcm.AppPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

internal const val VRCM_DATABASE_NAME = "vrcm.db"

internal expect fun platformVrcmDatabaseBuilder(
    appPlatform: AppPlatform,
): RoomDatabase.Builder<VrcmDatabase>

internal fun buildVrcmDatabase(builder: RoomDatabase.Builder<VrcmDatabase>): VrcmDatabase =
    builder
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
