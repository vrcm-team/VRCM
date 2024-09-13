package io.github.vrcmteam.vrcm

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.github.vrcmteam.vrcm.core.shared.AppConst.APP_NAME
import io.github.vrcmteam.vrcm.di.commonModules
import io.github.vrcmteam.vrcm.di.modules.platformModule
import org.jetbrains.compose.resources.painterResource
import org.koin.core.context.startKoin
import vrcm.composeapp.generated.resources.Res
import vrcm.composeapp.generated.resources.logo

fun main() = run {
    startKoin {
        printLogger()
        modules(commonModules + platformModule)
    }
    application {
        val windowState = rememberWindowState(
            width = 400.dp,
            height = 800.dp,
            position = WindowPosition.Aligned(Alignment.Center)
        )
        Window(
            state = windowState,
            onCloseRequest = ::exitApplication,
            title = APP_NAME,
            icon =  painterResource(Res.drawable.logo)
        ) {
            App()
        }
    }
}

@Preview
@Composable
fun AppDesktopPreview() {
    App()
}