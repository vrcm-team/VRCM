@file:Suppress("unused", "FunctionName")
package io.github.vrcmteam.vrcm

import androidx.compose.ui.window.ComposeUIViewController
import io.github.vrcmteam.vrcm.di.commonModules
import io.github.vrcmteam.vrcm.di.modules.platformModule
import io.github.vrcmteam.vrcm.presentation.screens.meetup.display.MeetupPresentationRootViewController
import org.koin.core.context.startKoin
import platform.UIKit.UIViewController

/** 用薄 root wrapper 承载 Compose，供身份牌展示页切换状态栏/home indicator 偏好。 */
fun MainViewController(): UIViewController =
    MeetupPresentationRootViewController(ComposeUIViewController { App() })

fun StartKoin() = startKoin {
    printLogger()
    modules(commonModules + platformModule)
}
