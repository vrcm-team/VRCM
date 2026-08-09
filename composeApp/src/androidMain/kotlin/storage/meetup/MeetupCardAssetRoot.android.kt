package io.github.vrcmteam.vrcm.storage.meetup

import io.github.vrcmteam.vrcm.AndroidAppPlatform
import io.github.vrcmteam.vrcm.AppPlatform
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import okio.Path
import okio.Path.Companion.toOkioPath

internal actual fun meetupCardAssetRoot(appPlatform: AppPlatform): Path {
    val context = (appPlatform as AndroidAppPlatform).context
    return context.filesDir.toOkioPath() / "meetup-card"
}

internal actual val meetupCardAssetIoDispatcher: CoroutineDispatcher = Dispatchers.IO
