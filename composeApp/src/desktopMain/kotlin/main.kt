package io.github.vrcmteam.vrcm

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.runtime.Composable
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import io.github.vrcmteam.vrcm.di.commonModules
import io.github.vrcmteam.vrcm.di.platformModule
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.painterResource
import org.koin.core.context.startKoin
import vrcm.composeapp.generated.resources.Res
import vrcm.composeapp.generated.resources.logo

@OptIn(ExperimentalResourceApi::class)
fun main() = run {
    startKoin {
        printLogger()
        modules(commonModules + platformModule)
    }
    application {
        Window(onCloseRequest = ::exitApplication, title = "VRCM", icon =  painterResource(Res.drawable.logo)) {
            App()
        }
    }
}

@Preview
@Composable
fun AppDesktopPreview() {
    App()
}