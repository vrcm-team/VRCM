package io.github.kamo.vrcm.di

import io.github.kamo.vrcm.data.dao.AccountDao
import io.github.kamo.vrcm.data.dao.CookiesDao
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val daoModule = module {
    singleOf(::AccountDao)
    singleOf(::CookiesDao)
}