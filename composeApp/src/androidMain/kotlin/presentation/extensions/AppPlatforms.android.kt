package io.github.vrcmteam.vrcm.presentation.extensions

import android.app.Activity
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

actual val AppPlatform.isSupportBlur: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
