package io.github.vrcmteam.vrcm

import android.app.Application
import org.koin.core.annotation.KoinInternalApi
class VRCMApplication : Application() {
    @OptIn(KoinInternalApi::class)
    override fun onCreate() {
        super.onCreate()
//        startKoin {
//
//            koin.setupLogger(AndroidLogger())
//            androidContext(this@VRCMApplication)
//            modules(
//                apiModule,
//                daoModule,
//                screenModelsModule,
//            )
//        }
    }
}