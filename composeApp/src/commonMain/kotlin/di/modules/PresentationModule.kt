package io.github.vrcmteam.vrcm.di.modules

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.disk.DiskCache
import coil3.network.ktor.KtorNetworkFetcherFactory
import coil3.request.crossfade
import coil3.util.DebugLogger
import io.github.vrcmteam.vrcm.presentation.screens.auth.AuthScreenModel
import io.github.vrcmteam.vrcm.presentation.screens.home.HomeScreenModel
import io.github.vrcmteam.vrcm.presentation.screens.home.tab.FriendListPagerModel
import io.github.vrcmteam.vrcm.presentation.screens.home.tab.FriendLocationPagerModel
import io.github.vrcmteam.vrcm.presentation.screens.profile.UserProfileScreenModel
import io.github.vrcmteam.vrcm.presentation.supports.AuthSupporter
import io.ktor.client.HttpClient
import okio.FileSystem
import org.koin.core.definition.Definition
import org.koin.core.module.Module
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val presentationModule: Module = module {
    factoryOf(::AuthScreenModel)
    factoryOf(::HomeScreenModel)
    factoryOf (::UserProfileScreenModel)
    singleOf (::FriendLocationPagerModel)
    singleOf (::FriendListPagerModel)
    singleOf (::AuthSupporter)
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