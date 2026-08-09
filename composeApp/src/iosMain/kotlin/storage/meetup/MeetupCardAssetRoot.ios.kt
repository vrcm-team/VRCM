package io.github.vrcmteam.vrcm.storage.meetup

import io.github.vrcmteam.vrcm.AppPlatform
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import okio.Path
import okio.Path.Companion.toPath
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

@OptIn(ExperimentalForeignApi::class)
internal actual fun meetupCardAssetRoot(appPlatform: AppPlatform): Path {
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
    return "$directory/meetup-card".toPath()
}

internal actual val meetupCardAssetIoDispatcher: CoroutineDispatcher = Dispatchers.IO
