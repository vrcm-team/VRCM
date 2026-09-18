package io.github.vrcmteam.vrcm.presentation.extensions

import io.github.vrcmteam.vrcm.AppPlatform

expect fun AppPlatform.openUrl(url: String)

/** Whether this platform can present a native system share sheet. */
expect val AppPlatform.supportsSystemShare: Boolean

/** Presents the native share sheet for [url], returning whether it was opened. */
expect fun AppPlatform.shareUrl(url: String): Boolean
