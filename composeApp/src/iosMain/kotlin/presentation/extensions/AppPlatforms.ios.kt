package io.github.vrcmteam.vrcm.presentation.extensions

import io.github.vrcmteam.vrcm.AppPlatform
import platform.Foundation.NSURL
import platform.UIKit.UIApplication

actual fun AppPlatform.openUrl(url: String) {
    UIApplication.sharedApplication.openURL(NSURL(string = url))
}