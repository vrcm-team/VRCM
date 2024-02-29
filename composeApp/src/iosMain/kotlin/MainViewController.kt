package io.github.vrcmteam.vrcm

import androidx.compose.ui.window.ComposeUIViewController
import coil3.PlatformContext
import io.github.vrcmteam.vrcm.di.apiModule
import io.github.vrcmteam.vrcm.di.daoModule
import io.github.vrcmteam.vrcm.di.screenModelsModule
import org.koin.core.annotation.KoinInternalApi
import org.koin.core.context.startKoin
import org.koin.core.logger.Logger
import org.koin.dsl.module

@OptIn(KoinInternalApi::class)
fun MainViewController() = run {
    startKoin {
        printLogger()
        modules(
            apiModule,
            daoModule,
            screenModelsModule,
            module{
                single<PlatformContext> { PlatformContext.INSTANCE }
                single<Logger>{ koin.logger }
            }
        )
    }

    ComposeUIViewController { App() }
}