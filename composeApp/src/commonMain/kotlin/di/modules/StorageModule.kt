package io.github.vrcmteam.vrcm.di.modules

import com.russhwolf.settings.Settings
import io.github.vrcmteam.vrcm.di.supports.PersistentCookiesStorage
import io.github.vrcmteam.vrcm.storage.AccountDao
import io.github.vrcmteam.vrcm.storage.AccountCacheManager
import io.github.vrcmteam.vrcm.storage.DaoKeys
import io.github.vrcmteam.vrcm.storage.FavoriteLocalDao
import io.github.vrcmteam.vrcm.storage.FriendActivityCacheStore
import io.github.vrcmteam.vrcm.storage.RoomFriendActivityStore
import io.github.vrcmteam.vrcm.storage.SettingsDao
import io.github.vrcmteam.vrcm.storage.SecureStorage
import io.github.vrcmteam.vrcm.storage.FriendListCacheStore
import io.github.vrcmteam.vrcm.storage.FriendNetworkCacheStore
import io.github.vrcmteam.vrcm.storage.GroupProfileCacheStore
import io.github.vrcmteam.vrcm.storage.RoomFriendListCacheStore
import io.github.vrcmteam.vrcm.storage.RoomFriendNetworkCacheStore
import io.github.vrcmteam.vrcm.storage.RoomGroupProfileCacheStore
import io.github.vrcmteam.vrcm.storage.RoomUserProfileCacheStore
import io.github.vrcmteam.vrcm.storage.RoomWorldProfileCacheStore
import io.github.vrcmteam.vrcm.storage.UserProfileCacheStore
import io.github.vrcmteam.vrcm.storage.WorldProfileCacheStore
import io.github.vrcmteam.vrcm.storage.buildVrcmDatabase
import io.github.vrcmteam.vrcm.storage.meetup.DecorationTemplateCacheDao
import io.github.vrcmteam.vrcm.storage.meetup.MeetupCardAssetStore
import io.github.vrcmteam.vrcm.storage.meetup.MeetupCardConfigDao
import io.github.vrcmteam.vrcm.storage.meetup.meetupCardAssetRoot
import io.github.vrcmteam.vrcm.storage.platformVrcmDatabaseBuilder
import io.ktor.client.plugins.cookies.*
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import okio.FileSystem
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.core.parameter.parametersOf
import org.koin.core.scope.Scope
import org.koin.dsl.bind
import org.koin.dsl.module

@OptIn(ExperimentalTime::class)
private val appClock: () -> Long = { Clock.System.now().toEpochMilliseconds() }

/**
 * 这些缓存迁到 Room 之前分别写在自己的 Settings 域里，其中好友列表与用户资料
 * 能到 MB 级，在 iOS 上会撞到 NSUserDefaults 的 ~4 MB 上限。启动时把旧域整个
 * 清掉释放空间；缓存本身联网后会自动重建。
 */
private fun Scope.purgeLegacySettingsCaches() {
    listOf(
        DaoKeys.UserProfileCache.NAME,
        DaoKeys.FriendListCache.NAME,
        DaoKeys.FriendNetwork.NAME,
        DaoKeys.GroupProfileCache.NAME,
        DaoKeys.WorldProfileCache.NAME,
    ).forEach { name -> get<Settings> { parametersOf(name) }.clear() }
}

internal val storageModule: Module = module {
    factory<Settings> { (name: String) -> get<Settings.Factory>().create(name) }
    single { AccountDao(get { parametersOf(DaoKeys.Account.NAME) }, get<SecureStorage>()) }
    single { SettingsDao(get { parametersOf(DaoKeys.Settings.NAME) }) }
    single { FavoriteLocalDao(get { parametersOf(DaoKeys.FavoriteLocal.NAME) }) }
    single { MeetupCardConfigDao(get { parametersOf(DaoKeys.MeetupCard.NAME) }) }
    single { DecorationTemplateCacheDao(get { parametersOf(DaoKeys.MeetupDecoration.NAME) }) }
    single { MeetupCardAssetStore(FileSystem.SYSTEM, meetupCardAssetRoot(get())) }
    single { buildVrcmDatabase(platformVrcmDatabaseBuilder(get())) }
    single { get<io.github.vrcmteam.vrcm.storage.VrcmDatabase>().friendActivityDao() }
    single { get<io.github.vrcmteam.vrcm.storage.VrcmDatabase>().cachedBlobDao() }
    single<UserProfileCacheStore> { RoomUserProfileCacheStore(get(), appClock) }
    single<FriendListCacheStore> { RoomFriendListCacheStore(get(), appClock) }
    single<FriendNetworkCacheStore> { RoomFriendNetworkCacheStore(get(), appClock) }
    single<WorldProfileCacheStore> { RoomWorldProfileCacheStore(get(), appClock) }
    single<GroupProfileCacheStore> { RoomGroupProfileCacheStore(get(), appClock) }
    singleOf(::RoomFriendActivityStore) bind FriendActivityCacheStore::class
    single { AccountCacheManager(get(), get(), get(), get(), get()).also { purgeLegacySettingsCaches() } }
    singleOf(::PersistentCookiesStorage) bind CookiesStorage::class
}
