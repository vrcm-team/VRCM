@file:Suppress("unused", "FunctionName")
package io.github.vrcmteam.vrcm

import androidx.compose.ui.window.ComposeUIViewController
import io.github.vrcmteam.vrcm.di.commonModules
import io.github.vrcmteam.vrcm.di.modules.platformModule
import org.koin.core.context.startKoin

fun MainViewController() =run  {
    ComposeUIViewController { App() }
}

fun StartKoin() = startKoin {
    printLogger()
    modules(commonModules + platformModule)
}