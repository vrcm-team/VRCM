package io.github.kamo.vrcm.di

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.disk.DiskCache
import coil3.network.ktor.KtorNetworkFetcherFactory
import coil3.request.crossfade
import coil3.util.DebugLogger
import io.github.kamo.vrcm.data.api.ApiClientDefaultBuilder
import io.github.kamo.vrcm.data.api.PersistentCookiesStorage
import io.github.kamo.vrcm.data.api.auth.AuthApi
import io.github.kamo.vrcm.data.api.file.FileApi
import io.github.kamo.vrcm.data.api.instance.InstanceAPI
import io.github.kamo.vrcm.data.api.users.UsersApi
import io.ktor.client.HttpClient
import io.ktor.client.plugins.cookies.CookiesStorage
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.plugins.logging.DEFAULT
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import okio.FileSystem
import org.koin.core.definition.Definition
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module


val apiModule = module {
    singleOf(::PersistentCookiesStorage) { bind<CookiesStorage>() }
    singleOf(::AuthApi)
    singleOf(::FileApi)
    singleOf(::InstanceAPI)
    singleOf(::UsersApi)
    single<HttpClient> { apiClientDefinition(it) }
    single<ImageLoader> { imageLoaderDefinition(it) }
}

private val imageLoaderDefinition: Definition<ImageLoader> = {
    val context = get<PlatformContext>()
    ImageLoader.Builder(context)
        .components {
            add(KtorNetworkFetcherFactory(get<HttpClient>()))
        }
        .diskCache {
            DiskCache.Builder()
                .maxSizePercent(0.03)
                .directory(FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "vrcm_coil_disk_cache")
                .build()
        }
        .crossfade(500)
        .logger(DebugLogger())
        .build()
}

private val apiClientDefinition: Definition<HttpClient> = {
    HttpClient {
        ApiClientDefaultBuilder()
        install(Logging) {
            logger = Logger.DEFAULT
            level = LogLevel.ALL
        }
        install(HttpCookies) {
            this.storage = get()
        }
    }
}