package io.github.vrcmteam.vrcm.di

import io.github.vrcmteam.vrcm.di.modules.networkModule
import io.github.vrcmteam.vrcm.di.modules.presentationModule
import io.github.vrcmteam.vrcm.di.modules.storageModule


val commonModules = listOf(storageModule, presentationModule, networkModule)

