package io.github.vrcmteam.vrcm.di.modules

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.disk.DiskCache
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.crossfade
import coil3.util.DebugLogger
import io.github.vrcmteam.vrcm.presentation.screens.auth.AuthScreenModel
import io.github.vrcmteam.vrcm.presentation.screens.avatar.AvatarProfileScreenModel
import io.github.vrcmteam.vrcm.presentation.screens.avatar.AvatarProfileLoader
import io.github.vrcmteam.vrcm.presentation.screens.avatar.NetworkAvatarProfileLoader
import io.github.vrcmteam.vrcm.presentation.screens.gallery.GalleryScreenModel
import io.github.vrcmteam.vrcm.presentation.screens.gallery.GalleryDataSource
import io.github.vrcmteam.vrcm.presentation.screens.gallery.NetworkGalleryDataSource
import io.github.vrcmteam.vrcm.presentation.screens.gallery.editor.CropTransformCalculator
import io.github.vrcmteam.vrcm.presentation.screens.gallery.editor.DefaultPrintImageProcessor
import io.github.vrcmteam.vrcm.presentation.screens.gallery.editor.PrintImageEditorScreenModel
import io.github.vrcmteam.vrcm.presentation.screens.gallery.editor.PrintImageEditorSessionStore
import io.github.vrcmteam.vrcm.presentation.screens.gallery.editor.PrintImageProcessor
import io.github.vrcmteam.vrcm.presentation.screens.group.GroupProfileScreenModel
import io.github.vrcmteam.vrcm.presentation.screens.home.HomeScreenModel
import io.github.vrcmteam.vrcm.presentation.screens.home.pager.FriendListPagerModel
import io.github.vrcmteam.vrcm.presentation.screens.home.pager.FriendLocationPagerModel
import io.github.vrcmteam.vrcm.presentation.screens.home.pager.SearchListPagerModel
import io.github.vrcmteam.vrcm.presentation.screens.user.FriendNetworkScreenModel
import io.github.vrcmteam.vrcm.presentation.screens.user.MutualFriendsScreenModel
import io.github.vrcmteam.vrcm.presentation.screens.user.UserProfileScreenModel
import io.github.vrcmteam.vrcm.presentation.screens.world.RecentWorldsScreenModel
import io.github.vrcmteam.vrcm.presentation.screens.world.WorldProfileScreenModel
import io.github.vrcmteam.vrcm.presentation.settings.SettingsModel
import io.github.vrcmteam.vrcm.presentation.settings.theme.ThemeColor
import io.github.vrcmteam.vrcm.presentation.theme.blue.BlueThemeColor
import io.github.vrcmteam.vrcm.presentation.theme.green.GreenThemeColor
import io.github.vrcmteam.vrcm.presentation.theme.pink.PinkThemeColor
import io.github.vrcmteam.vrcm.service.PrintUploadService
import io.github.vrcmteam.vrcm.service.PrintUploader
import io.ktor.client.*
import okio.FileSystem
import org.koin.core.definition.Definition
import org.koin.core.module.Module
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

val presentationModule: Module = module {
    factoryOf (::SettingsModel)
    factory{ SettingsModel(get(),getAll()) }
    factoryOf(::AuthScreenModel)
    factoryOf(::HomeScreenModel)
    factoryOf (::UserProfileScreenModel)
    factoryOf(::MutualFriendsScreenModel)
    factoryOf(::FriendNetworkScreenModel)
    singleOf(::NetworkGalleryDataSource) bind GalleryDataSource::class
    factory { GalleryScreenModel(get(), get()) }
    single { CropTransformCalculator() }
    singleOf(::PrintImageEditorSessionStore)
    single<PrintImageProcessor> { DefaultPrintImageProcessor(get()) }
    singleOf(::PrintUploadService) bind PrintUploader::class
    factory { parameters ->
        val sessionId = parameters.get<String>()
        val sessionStore = get<PrintImageEditorSessionStore>()
        val session = requireNotNull(sessionStore.get(sessionId)) {
            "Print image editor session is unavailable"
        }
        PrintImageEditorScreenModel(
            source = session.source,
            prepared = session.prepared,
            calculator = get(),
            processor = get(),
            uploader = get(),
            sessionId = sessionId,
            sessionStore = sessionStore,
        )
    }
    singleOf (::FriendLocationPagerModel)
    singleOf (::FriendListPagerModel)
    singleOf(::SearchListPagerModel)
    singleOf(::WorldProfileScreenModel)
    singleOf(::GroupProfileScreenModel)
    singleOf(::NetworkAvatarProfileLoader) bind AvatarProfileLoader::class
    factory { AvatarProfileScreenModel(get()) }
    factoryOf(::RecentWorldsScreenModel)
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
