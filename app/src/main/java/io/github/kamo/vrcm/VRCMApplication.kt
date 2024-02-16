package io.github.kamo.vrcm

import android.app.Application
import io.github.kamo.vrcm.di.apiModule
import io.github.kamo.vrcm.di.viewModeModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class VRCMApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger()
            androidContext(this@VRCMApplication)
            modules(viewModeModule, apiModule)
        }
    }
}