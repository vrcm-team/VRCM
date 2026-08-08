package io.github.vrcmteam.vrcm.storage.meetup

import io.github.vrcmteam.vrcm.AppPlatform
import io.github.vrcmteam.vrcm.di.modules.desktopSettingsDirectory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import okio.Path

internal actual fun meetupCardAssetRoot(appPlatform: AppPlatform): Path =
    desktopSettingsDirectory() / "meetup-card"

internal actual val meetupCardAssetIoDispatcher: CoroutineDispatcher = Dispatchers.IO
