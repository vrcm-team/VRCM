package io.github.vrcmteam.vrcm

import androidx.compose.runtime.Composable
import org.koin.compose.getKoin

interface AppPlatform {
    val name: String
    val version: String
}

@Composable
fun getAppPlatform(): AppPlatform = getKoin().get()