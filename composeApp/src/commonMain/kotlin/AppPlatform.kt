package io.github.vrcmteam.vrcm

import androidx.compose.runtime.Composable
import org.koin.compose.getKoin

interface AppPlatform {
    val name: String
    val version: String
    val type: AppPlatformType
}

enum class AppPlatformType {
    Android,
    Desktop,
    Ios,
    Web
}

@Composable
fun getAppPlatform(): AppPlatform = getKoin().get()