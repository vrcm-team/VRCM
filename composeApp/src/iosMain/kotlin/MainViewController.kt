package io.github.vrcmteam.vrcm

import androidx.compose.ui.window.ComposeUIViewController
import io.github.vrcmteam.vrcm.di.commonModules
import io.github.vrcmteam.vrcm.di.platformModule
import org.koin.core.context.startKoin

fun MainViewController() = run {
    startKoin {
        printLogger()
        modules(commonModules + platformModule)
    }
    ComposeUIViewController { App() }
}