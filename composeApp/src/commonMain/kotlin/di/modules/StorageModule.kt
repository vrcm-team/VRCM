package io.github.vrcmteam.vrcm.di.modules

import com.russhwolf.settings.Settings
import io.github.vrcmteam.vrcm.di.supports.PersistentCookiesStorage
import io.github.vrcmteam.vrcm.storage.AccountDao
import io.github.vrcmteam.vrcm.storage.AccountCacheManager
import io.github.vrcmteam.vrcm.storage.DaoKeys
import io.github.vrcmteam.vrcm.storage.FavoriteLocalDao
import io.github.vrcmteam.vrcm.storage.FriendListCacheDao
import io.github.vrcmteam.vrcm.storage.FriendNetworkCacheDao
import io.github.vrcmteam.vrcm.storage.SettingsDao
import io.github.vrcmteam.vrcm.storage.UserProfileCacheDao
import io.github.vrcmteam.vrcm.storage.WorldProfileCacheDao
import io.ktor.client.plugins.cookies.*
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.core.parameter.parametersOf
import org.koin.dsl.bind
import org.koin.dsl.module

internal val storageModule: Module = module {
    factory<Settings> { (name: String) -> get<Settings.Factory>().create(name) }
    single { AccountDao(get { parametersOf(DaoKeys.Account.NAME) }) }
    single { SettingsDao(get { parametersOf(DaoKeys.Settings.NAME) }) }
    single { FavoriteLocalDao(get { parametersOf(DaoKeys.FavoriteLocal.NAME) }) }
    single { FriendListCacheDao(get { parametersOf(DaoKeys.FriendListCache.NAME) }) }
    single { FriendNetworkCacheDao(get { parametersOf(DaoKeys.FriendNetwork.NAME) }) }
    single { UserProfileCacheDao(get { parametersOf(DaoKeys.UserProfileCache.NAME) }) }
    single { WorldProfileCacheDao(get { parametersOf(DaoKeys.WorldProfileCache.NAME) }) }
    singleOf(::AccountCacheManager)
    singleOf(::PersistentCookiesStorage) bind CookiesStorage::class
}
