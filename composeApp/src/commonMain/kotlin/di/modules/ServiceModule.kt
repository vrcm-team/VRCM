package io.github.vrcmteam.vrcm.di.modules

import io.github.vrcmteam.vrcm.service.*
import io.github.vrcmteam.vrcm.network.websocket.WebSocketSessionRecovery
import io.github.vrcmteam.vrcm.service.meetup.DecorationResolver
import io.github.vrcmteam.vrcm.service.meetup.DecorationTemplateSource
import io.github.vrcmteam.vrcm.service.meetup.DefaultMeetupCardRemoteDataSource
import io.github.vrcmteam.vrcm.service.meetup.DefaultMeetupCardRepository
import io.github.vrcmteam.vrcm.service.meetup.HttpMeetupRemoteBytesLoader
import io.github.vrcmteam.vrcm.service.meetup.InventoryDecorationTemplateSource
import io.github.vrcmteam.vrcm.service.meetup.MeetupCardRemoteDataSource
import io.github.vrcmteam.vrcm.service.meetup.MeetupCardRepository
import io.github.vrcmteam.vrcm.service.meetup.MeetupCurrentUserSnapshotProvider
import io.github.vrcmteam.vrcm.service.meetup.MeetupRemoteBytesLoader
import io.github.vrcmteam.vrcm.storage.meetup.MeetupCardAssetStore
import io.github.vrcmteam.vrcm.storage.meetup.MeetupProfileSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

val serviceModule: Module = module {
    singleOf(::VersionService)
    singleOf(::AuthService) bind WebSocketSessionRecovery::class
    single { UserProfileEnrichmentService(get()) }
    singleOf(::FavoriteService)
    singleOf(::FriendService)
    singleOf(::FriendActivityService)
    singleOf(::FriendOnlineNotificationService)
    singleOf(::VrchatStatusNotificationService)
    singleOf(::NetworkBoopRequest) bind BoopRequest::class
    singleOf(::BoopService)
    singleOf(::WorldPlatformService)
    singleOf(::OfficialLinkService)
    singleOf(::AuthenticatedPlayerModerationCleanupSource) bind PlayerModerationCleanupSource::class
    singleOf(::HttpMeetupRemoteBytesLoader) bind MeetupRemoteBytesLoader::class
    singleOf(::InventoryDecorationTemplateSource) bind DecorationTemplateSource::class
    singleOf(::DecorationResolver)
    singleOf(::DefaultMeetupCardRemoteDataSource) bind MeetupCardRemoteDataSource::class
    single<MeetupCardRepository> {
        val authService = get<AuthService>()
        val featureScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val assetStore = get<MeetupCardAssetStore>()
        // 上次写素材时被杀进程会留下 .tmp 孤儿，卡片功能首次装配时扫一遍。
        featureScope.launch { assetStore.sweepAbandonedTemporaryFiles() }
        DefaultMeetupCardRepository(
            configDao = get(),
            assetStore = assetStore,
            remote = get(),
            decorationResolver = get(),
            accountCacheManager = get(),
            currentUserSnapshotProvider = MeetupCurrentUserSnapshotProvider { ownerId ->
                authService.currentUserState.value
                    ?.takeIf { it.id == ownerId }
                    ?.let { user ->
                        MeetupProfileSnapshot(
                            displayName = user.displayName,
                            avatarUrl = user.iconUrl,
                            pronouns = user.pronouns.orEmpty(),
                            languages = user.speakLanguages,
                            status = user.status.value,
                            statusDescription = user.statusDescription,
                            links = user.bioLinks,
                        )
                    }
            },
            scope = featureScope,
        )
    }
}
