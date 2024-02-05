package io.github.kamo.vrcm.di

import coil.ImageLoader
import io.github.kamo.vrcm.data.api.PersistentCookiesStorage
import io.github.kamo.vrcm.data.api.auth.AuthAPI
import io.github.kamo.vrcm.data.api.file.FileAPI
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.cookies.*
import io.ktor.client.plugins.logging.*
import io.ktor.http.*
import io.ktor.serialization.gson.*
import okhttp3.OkHttpClient
import org.koin.core.definition.Definition
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val apiModule = module {
    singleOf(::PersistentCookiesStorage) { bind<CookiesStorage>() }
    singleOf(::AuthAPI)
    singleOf(::FileAPI)
    single{ apiClientDefinition(it) }
    single {
        ImageLoader.Builder(get())
            .okHttpClient {
                OkHttpClient.Builder()
                    .followRedirects(true) // 设置跟随重定向
                    .followSslRedirects(true) // 设置跟随SSL重定向
                    .build()
            }.build()
    }
}

internal val apiClientDefinition: Definition<HttpClient> = {
    HttpClient {
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