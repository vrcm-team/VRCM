package io.github.vrcmteam.vrcm

import android.app.Application
import io.github.vrcmteam.vrcm.di.commonModules
import io.github.vrcmteam.vrcm.di.platformModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class VRCMApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger()
            androidContext(this@VRCMApplication)
            modules(commonModules + platformModule)
        }
    }
}