package io.github.vrcmteam.vrcm.di.modules

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.disk.DiskCache
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.crossfade
import coil3.util.DebugLogger
import io.github.vrcmteam.vrcm.presentation.screens.auth.AuthScreenModel
import io.github.vrcmteam.vrcm.presentation.screens.gallery.GalleryScreenModel
import io.github.vrcmteam.vrcm.presentation.screens.home.HomeScreenModel
import io.github.vrcmteam.vrcm.presentation.screens.home.pager.FriendListPagerModel
import io.github.vrcmteam.vrcm.presentation.screens.home.pager.FriendLocationPagerModel
import io.github.vrcmteam.vrcm.presentation.screens.home.pager.SearchListPagerModel
import io.github.vrcmteam.vrcm.presentation.screens.user.UserProfileScreenModel
import io.github.vrcmteam.vrcm.presentation.screens.world.WorldProfileScreenModel
import io.github.vrcmteam.vrcm.presentation.settings.SettingsModel
import io.github.vrcmteam.vrcm.presentation.settings.theme.ThemeColor
import io.github.vrcmteam.vrcm.presentation.theme.blue.BlueThemeColor
import io.github.vrcmteam.vrcm.presentation.theme.green.GreenThemeColor
import io.github.vrcmteam.vrcm.presentation.theme.pink.PinkThemeColor
import io.ktor.client.*
import okio.FileSystem
import org.koin.core.definition.Definition
import org.koin.core.module.Module
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
import org.koin.dsl.module

val presentationModule: Module = module {
    factoryOf (::SettingsModel)
    factory{ SettingsModel(get(),getAll()) }
    factoryOf(::AuthScreenModel)
    factoryOf(::HomeScreenModel)
    factoryOf (::UserProfileScreenModel)
    singleOf(::GalleryScreenModel)
    singleOf (::FriendLocationPagerModel)
    singleOf (::FriendListPagerModel)
    singleOf(::SearchListPagerModel)
    singleOf(::WorldProfileScreenModel)
    single<ImageLoader> { imageLoaderDefinition(it) }
    configThemeColor()
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

private fun Module.configThemeColor() {
    single(named(ThemeColor.Default.name)){ ThemeColor.Default }
    single(named(BlueThemeColor.name)){ BlueThemeColor }
    single(named(PinkThemeColor.name)){ PinkThemeColor }
    single (named(GreenThemeColor.name)){ GreenThemeColor}
}