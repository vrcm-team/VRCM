package io.github.vrcmteam.vrcm.storage.meetup

import io.github.vrcmteam.vrcm.AppPlatform
import kotlinx.coroutines.CoroutineDispatcher
import okio.Path

internal expect fun meetupCardAssetRoot(appPlatform: AppPlatform): Path

internal expect val meetupCardAssetIoDispatcher: CoroutineDispatcher
