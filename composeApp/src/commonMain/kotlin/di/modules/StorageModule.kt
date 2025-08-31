package io.github.vrcmteam.vrcm.di.modules

import com.russhwolf.settings.Settings
import io.github.vrcmteam.vrcm.di.supports.PersistentCookiesStorage
import io.github.vrcmteam.vrcm.storage.AccountDao
import io.github.vrcmteam.vrcm.storage.DaoKeys
import io.github.vrcmteam.vrcm.storage.FavoriteLocalDao
import io.github.vrcmteam.vrcm.storage.SettingsDao
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
    singleOf(::PersistentCookiesStorage) bind CookiesStorage::class
}