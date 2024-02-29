package io.github.vrcmteam.vrcm.di

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.disk.DiskCache
import coil3.network.ktor.KtorNetworkFetcherFactory
import coil3.request.crossfade
import coil3.util.DebugLogger
import io.github.vrcmteam.vrcm.data.api.ApiClientDefaultBuilder
import io.github.vrcmteam.vrcm.data.api.PersistentCookiesStorage
import io.github.vrcmteam.vrcm.data.api.auth.AuthApi
import io.github.vrcmteam.vrcm.data.api.file.FileApi
import io.github.vrcmteam.vrcm.data.api.instance.InstanceAPI
import io.github.vrcmteam.vrcm.data.api.users.UsersApi
import io.github.vrcmteam.vrcm.data.dao.AccountDao
import io.github.vrcmteam.vrcm.data.dao.CookiesDao
import io.github.vrcmteam.vrcm.screens.auth.AuthScreenModel
import io.github.vrcmteam.vrcm.screens.home.HomeScreenModel
import io.github.vrcmteam.vrcm.screens.profile.ProfileScreenModel
import io.ktor.client.*
import io.ktor.client.plugins.cookies.*
import io.ktor.client.plugins.logging.*
import okio.FileSystem
import org.koin.core.context.startKoin
import org.koin.core.definition.Definition
import org.koin.core.module.Module
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

fun initKoin() {
    startKoin {
        modules(
            apiModule,
            daoModule,
            screenModelsModule,
        )
    }
}


val daoModule: Module = module {
     singleOf(::AccountDao)
     singleOf(::CookiesDao)
 }
val screenModelsModule : Module = module {
    factoryOf(::AuthScreenModel)
    factoryOf(::HomeScreenModel)
    factoryOf(::ProfileScreenModel)

}
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