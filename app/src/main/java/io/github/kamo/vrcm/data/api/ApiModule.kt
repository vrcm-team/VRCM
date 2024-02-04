package io.github.kamo.vrcm.data.api

import io.github.kamo.vrcm.data.api.auth.AuthAPI
import io.github.kamo.vrcm.data.api.file.FileAPI
import io.ktor.client.plugins.cookies.*
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val apiModule = module {
    singleOf(::PersistentCookiesStorage) { bind<CookiesStorage>() }
    singleOf(::APIClient)
    singleOf(::AuthAPI)
    singleOf(::FileAPI)
}