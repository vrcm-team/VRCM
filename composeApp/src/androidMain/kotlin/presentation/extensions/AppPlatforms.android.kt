package io.github.vrcmteam.vrcm.presentation.extensions

import android.content.Intent
import android.net.Uri
import io.github.vrcmteam.vrcm.AndroidAppPlatform
import io.github.vrcmteam.vrcm.AppPlatform

actual fun AppPlatform.openUrl(url: String) {
    with(this as AndroidAppPlatform) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}