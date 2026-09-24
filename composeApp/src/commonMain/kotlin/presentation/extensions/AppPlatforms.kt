package io.github.vrcmteam.vrcm.presentation.extensions

import io.github.vrcmteam.vrcm.AppPlatform

expect fun AppPlatform.openUrl(url: String)

/** Whether this platform can present a native system share sheet. */
expect val AppPlatform.supportsSystemShare: Boolean

/** Presents the native share sheet for [url], returning whether it was opened. */
expect fun AppPlatform.shareUrl(url: String): Boolean

/**
 * 剪贴板的变化标记：每次有 App 复制新内容都会变。只读系统的元数据、不碰内容，
 * 不会触发 iOS 的"允许粘贴"或 Android 的剪贴板读取提示；平台给不出时返回 null。
 */
expect fun AppPlatform.clipboardChangeToken(): Long?
