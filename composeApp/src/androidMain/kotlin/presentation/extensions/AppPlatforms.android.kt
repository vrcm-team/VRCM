package io.github.vrcmteam.vrcm.presentation.extensions

import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Build
import io.github.vrcmteam.vrcm.AndroidAppPlatform
import io.github.vrcmteam.vrcm.AppPlatform

actual fun AppPlatform.openUrl(url: String) {
    with(this as AndroidAppPlatform) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}

actual val AppPlatform.supportsSystemShare: Boolean
    get() = true

actual fun AppPlatform.shareUrl(url: String): Boolean = runCatching {
    with(this as AndroidAppPlatform) {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, url)
        }
        context.startActivity(
            Intent.createChooser(sendIntent, null)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}.isSuccess

/** 复制时间只在剪贴板描述里（API 26+），读描述不会像 getPrimaryClip 那样触发 Android 12+ 的读取提示。 */
actual fun AppPlatform.clipboardChangeToken(): Long? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
    val clipboard = (this as AndroidAppPlatform).context.getSystemService(ClipboardManager::class.java)
    return clipboard?.primaryClipDescription?.timestamp
}

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
