package io.github.vrcmteam.vrcm

import android.content.Context
import android.os.Build


class AndroidAppPlatform(val context: Context) : AppPlatform {
    override val name: String = "Android"
    override val version: String = Build.VERSION.SDK_INT.toString()
    override val type: AppPlatformType = AppPlatformType.Android
}
