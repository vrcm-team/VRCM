package io.github.vrcmteam.vrcm

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.runtime.Composable
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import io.github.vrcmteam.vrcm.di.commonModules
import io.github.vrcmteam.vrcm.di.platformModule
import org.koin.core.context.startKoin

fun main() = run {
    startKoin {
        printLogger()
        modules(commonModules + platformModule)
    }
    application {
        Window(onCloseRequest = ::exitApplication, title = "KotlinProject") {
            App()
        }
    }
}

@Preview
@Composable
fun AppDesktopPreview() {
    App()
}