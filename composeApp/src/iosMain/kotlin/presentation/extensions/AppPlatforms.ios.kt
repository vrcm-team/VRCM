package io.github.vrcmteam.vrcm.presentation.extensions

import io.github.vrcmteam.vrcm.AppPlatform
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIViewController
import platform.UIKit.popoverPresentationController

actual fun AppPlatform.openUrl(url: String) {
    UIApplication.sharedApplication.openURL(NSURL(string = url), emptyMap<Any?, Any>()) {
    }
}

actual val AppPlatform.supportsSystemShare: Boolean
    get() = true

@OptIn(ExperimentalForeignApi::class)
actual fun AppPlatform.shareUrl(url: String): Boolean {
    val presenter = UIApplication.sharedApplication.keyWindow
        ?.rootViewController
        ?.topPresentedViewController()
        ?: return false
    val activityController = UIActivityViewController(
        activityItems = listOf(NSURL(string = url)),
        applicationActivities = null,
    )
    activityController.popoverPresentationController?.apply {
        sourceView = presenter.view
        sourceRect = presenter.view.bounds
        permittedArrowDirections = 0u
    }
    presenter.presentViewController(activityController, animated = true, completion = null)
    return true
}

private tailrec fun UIViewController.topPresentedViewController(): UIViewController =
    presentedViewController?.topPresentedViewController() ?: this

actual val AppPlatform.isSupportBlur: Boolean
    get() = true
