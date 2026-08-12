package io.github.vrcmteam.vrcm.presentation.extensions

import io.github.vrcmteam.vrcm.AppPlatform
import java.awt.Desktop
import java.net.URI

actual fun AppPlatform.openUrl(url: String) {
    Desktop.getDesktop().browse(URI(url))
}

actual val AppPlatform.supportsSystemShare: Boolean
    get() = false

actual fun AppPlatform.shareUrl(url: String): Boolean = false

actual val AppPlatform.isSupportBlur: Boolean
    get() = true
