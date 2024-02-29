package io.github.vrcmteam.vrcm

import android.app.Application
import io.github.vrcmteam.vrcm.di.apiModule
import io.github.vrcmteam.vrcm.di.daoModule
import io.github.vrcmteam.vrcm.di.screenModelsModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.annotation.KoinInternalApi
import org.koin.core.context.startKoin
import org.koin.dsl.module

class VRCMApplication : Application() {
    @OptIn(KoinInternalApi::class)
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger()
            androidContext(this@VRCMApplication)
            modules(
                apiModule,
                daoModule,
                screenModelsModule,
                module { single { koin.logger } }
            )
        }
    }
}