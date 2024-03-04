package io.github.vrcmteam.vrcm.di

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.disk.DiskCache
import coil3.network.ktor.KtorNetworkFetcherFactory
import coil3.request.crossfade
import coil3.util.DebugLogger
import com.russhwolf.settings.Settings
import io.github.vrcmteam.vrcm.network.api.ApiClientDefaultBuilder
import io.github.vrcmteam.vrcm.network.api.PersistentCookiesStorage
import io.github.vrcmteam.vrcm.network.api.auth.AuthApi
import io.github.vrcmteam.vrcm.network.api.file.FileApi
import io.github.vrcmteam.vrcm.network.api.instance.InstanceAPI
import io.github.vrcmteam.vrcm.network.api.users.UsersApi
import io.github.vrcmteam.vrcm.presentation.screens.auth.AuthScreenModel
import io.github.vrcmteam.vrcm.presentation.screens.home.HomeScreenModel
import io.github.vrcmteam.vrcm.presentation.screens.profile.ProfileScreenModel
import io.github.vrcmteam.vrcm.storage.AccountDao
import io.github.vrcmteam.vrcm.storage.CookiesDao
import io.github.vrcmteam.vrcm.storage.DaoKeys
import io.ktor.client.*
import io.ktor.client.plugins.cookies.*
import io.ktor.client.plugins.logging.*
import okio.FileSystem
import org.koin.core.definition.Definition
import org.koin.core.module.Module
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.parameter.parametersOf
import org.koin.dsl.bind
import org.koin.dsl.module

expect val platformModule: Module


private val daoModule: Module = module {
    factory<Settings> { (name: String) -> get<Settings.Factory>().create(name) }
    single { AccountDao(get { parametersOf(DaoKeys.Account.NAME) }) }
    single { CookiesDao(get { parametersOf(DaoKeys.Cookies.NAME) }) }
}

private val screenModelsModule: Module = module {
    factoryOf(::AuthScreenModel)
    factoryOf(::HomeScreenModel)
    factoryOf(::ProfileScreenModel)
}

private val apiModule = module {
    singleOf(::PersistentCookiesStorage) bind CookiesStorage::class
    singleOf(::AuthApi)
    singleOf(::FileApi)
    singleOf(::InstanceAPI)
    singleOf(::UsersApi)
    single<HttpClient> { apiClientDefinition(it) }
    single<ImageLoader> { imageLoaderDefinition(it) }
}

val commonModules = listOf(daoModule, screenModelsModule, apiModule)

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