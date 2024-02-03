package io.github.kamo.vrcm.domain.api

import io.github.kamo.vrcm.domain.api.auth.AuthAPI
import io.ktor.client.plugins.cookies.*
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val apiModule = module {
    singleOf(::PersistentCookiesStorage) { bind<CookiesStorage>() }
    singleOf(::APIClient)
    singleOf(::AuthAPI)
}