package io.github.vrcmteam.vrcm.di.modules

import com.russhwolf.settings.Settings
import io.github.vrcmteam.vrcm.di.supports.PersistentCookiesStorage
import io.github.vrcmteam.vrcm.storage.AccountDao
import io.github.vrcmteam.vrcm.storage.AccountCacheManager
import io.github.vrcmteam.vrcm.storage.DaoKeys
import io.github.vrcmteam.vrcm.storage.FavoriteLocalDao
import io.github.vrcmteam.vrcm.storage.FriendListCacheDao
import io.github.vrcmteam.vrcm.storage.FriendActivityCacheStore
import io.github.vrcmteam.vrcm.storage.FriendNetworkCacheDao
import io.github.vrcmteam.vrcm.storage.RoomFriendActivityStore
import io.github.vrcmteam.vrcm.storage.GroupProfileCacheDao
import io.github.vrcmteam.vrcm.storage.SettingsDao
import io.github.vrcmteam.vrcm.storage.SecureStorage
import io.github.vrcmteam.vrcm.storage.RoomUserProfileCacheStore
import io.github.vrcmteam.vrcm.storage.UserProfileCacheStore
import io.github.vrcmteam.vrcm.storage.WorldProfileCacheDao
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
import org.koin.dsl.bind
import org.koin.dsl.module

@OptIn(ExperimentalTime::class)
internal val storageModule: Module = module {
    factory<Settings> { (name: String) -> get<Settings.Factory>().create(name) }
    single { AccountDao(get { parametersOf(DaoKeys.Account.NAME) }, get<SecureStorage>()) }
    single { SettingsDao(get { parametersOf(DaoKeys.Settings.NAME) }) }
    single { FavoriteLocalDao(get { parametersOf(DaoKeys.FavoriteLocal.NAME) }) }
    single { FriendListCacheDao(get { parametersOf(DaoKeys.FriendListCache.NAME) }) }
    single { FriendNetworkCacheDao(get { parametersOf(DaoKeys.FriendNetwork.NAME) }) }
    single { GroupProfileCacheDao(get { parametersOf(DaoKeys.GroupProfileCache.NAME) }) }
    single { WorldProfileCacheDao(get { parametersOf(DaoKeys.WorldProfileCache.NAME) }) }
    single { MeetupCardConfigDao(get { parametersOf(DaoKeys.MeetupCard.NAME) }) }
    single { DecorationTemplateCacheDao(get { parametersOf(DaoKeys.MeetupDecoration.NAME) }) }
    single { MeetupCardAssetStore(FileSystem.SYSTEM, meetupCardAssetRoot(get())) }
    single { buildVrcmDatabase(platformVrcmDatabaseBuilder(get())) }
    single { get<io.github.vrcmteam.vrcm.storage.VrcmDatabase>().friendActivityDao() }
    single { get<io.github.vrcmteam.vrcm.storage.VrcmDatabase>().userProfileCacheDao() }
    single<UserProfileCacheStore> {
        // 迁移到 Room 前这份缓存写在 Settings 里，单条可达 1.5 MB，
        // 在 iOS 上会撞到 NSUserDefaults 的 ~4 MB 上限；启动时把旧域整个清掉。
        get<Settings> { parametersOf(DaoKeys.UserProfileCache.NAME) }.clear()
        RoomUserProfileCacheStore(
            dao = get(),
            nowMillis = { Clock.System.now().toEpochMilliseconds() },
        )
    }
    singleOf(::RoomFriendActivityStore) bind FriendActivityCacheStore::class
    singleOf(::AccountCacheManager)
    singleOf(::PersistentCookiesStorage) bind CookiesStorage::class
}
