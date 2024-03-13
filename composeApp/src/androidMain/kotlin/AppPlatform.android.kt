package io.github.vrcmteam.vrcm

import android.content.Context


class AndroidAppPlatform(val context: Context) : AppPlatform {
    override val name: String = "Android"
    override val version: String = "1.0.0"
}
