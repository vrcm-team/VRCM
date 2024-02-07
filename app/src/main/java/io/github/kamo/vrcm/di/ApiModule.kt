package io.github.kamo.vrcm.di

import coil3.ImageLoader
import io.github.kamo.vrcm.data.api.PersistentCookiesStorage
import io.github.kamo.vrcm.data.api.auth.AuthAPI
import io.github.kamo.vrcm.data.api.file.FileAPI
import io.github.kamo.vrcm.data.api.instance.InstanceAPI
import io.ktor.client.HttpClient
import io.ktor.client.plugins.UserAgent
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.cookies.CookiesStorage
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.DEFAULT
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.URLProtocol
import io.ktor.http.path
import io.ktor.serialization.gson.gson
import org.koin.core.definition.Definition
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val apiModule = module {
    singleOf(::PersistentCookiesStorage) { bind<CookiesStorage>() }
    singleOf(::AuthAPI)
    singleOf(::FileAPI)
    singleOf(::InstanceAPI)
    single { apiClientDefinition(it) }
    single {
        ImageLoader.Builder(get())
            .build()
    }
}

internal val apiClientDefinition: Definition<HttpClient> = {
    HttpClient {
        followRedirects = false
        defaultRequest {
            url {
                protocol = URLProtocol.HTTPS
                host = "api.vrchat.cloud"
                path("api/1", "")
            }
        }
        install(Logging) {
            logger = Logger.DEFAULT
            level = LogLevel.ALL
        }
        install(ContentNegotiation) { gson() }
        install(UserAgent)
        install(HttpCookies) {
            this.storage = get()
        }
    }
}