package io.github.vrcmteam.vrcm.presentation.extensions

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import io.github.vrcmteam.vrcm.AndroidAppPlatform
import io.github.vrcmteam.vrcm.AppPlatform

actual fun AppPlatform.openUrl(url: String) {
    with(this as AndroidAppPlatform) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}

actual val AppPlatform.isSupportBlur: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

// https://issuetracker.google.com/issues/362539765#comment3
@Composable
actual fun AppPlatform.ChangeStatusBarDarkTheme(isDark: Boolean) {
    val view = LocalView.current
    LaunchedEffect(isDark) {
        val window = (view.parent as? DialogWindowProvider)?.window
            ?: view.context.findActivity()?.window

        window?.let {
            val insetsController = WindowCompat.getInsetsController(it, it.decorView)
            insetsController.isAppearanceLightStatusBars = !isDark
        }
    }
}
