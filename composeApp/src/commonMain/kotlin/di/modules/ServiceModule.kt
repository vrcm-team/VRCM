package io.github.vrcmteam.vrcm.di.modules

import io.github.vrcmteam.vrcm.service.AuthService
import io.github.vrcmteam.vrcm.service.FavoriteService
import io.github.vrcmteam.vrcm.service.VersionService
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val serviceModule: Module = module {
    singleOf(::VersionService)
    singleOf(::AuthService)
    singleOf(::FavoriteService)
}