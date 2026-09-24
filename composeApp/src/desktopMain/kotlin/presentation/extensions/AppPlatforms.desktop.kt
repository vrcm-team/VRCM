package io.github.vrcmteam.vrcm.presentation.extensions

import io.github.vrcmteam.vrcm.AppPlatform
import java.awt.Desktop
import java.net.URI

actual fun AppPlatform.openUrl(url: String) {
    val uri = URI(url)
    // mailto 交给系统邮件客户端；browse 只保证 http(s)，部分桌面环境会拒绝其他 scheme
    if (uri.scheme.equals("mailto", ignoreCase = true)) {
        Desktop.getDesktop().mail(uri)
    } else {
        Desktop.getDesktop().browse(uri)
    }
}

actual val AppPlatform.supportsSystemShare: Boolean
    get() = false

actual fun AppPlatform.shareUrl(url: String): Boolean = false

/** 桌面读剪贴板没有系统提示，不需要标记，直接读取；同一个链接由弹窗逻辑去重。 */
actual fun AppPlatform.clipboardChangeToken(): Long? = null
