package io.github.vrcmteam.vrcm

import andorid.di.viewModeModule
import android.app.Application
import io.github.vrcmteam.vrcm.andorid.di.daoModule
import io.github.vrcmteam.vrcm.di.apiModule
import io.github.vrcmteam.vrcm.di.daoModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class VRCMApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger()
            androidContext(this@VRCMApplication)
            modules(viewModeModule, apiModule, daoModule)
        }
    }
}