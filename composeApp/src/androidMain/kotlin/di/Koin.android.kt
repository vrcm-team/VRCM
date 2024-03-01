package io.github.vrcmteam.vrcm.di

import com.russhwolf.settings.Settings
import com.russhwolf.settings.SharedPreferencesSettings
import org.koin.android.logger.AndroidLogger
import org.koin.core.logger.Logger
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

actual val platformModule: Module = module {
        singleOf<Logger>(::AndroidLogger)
        singleOf(SharedPreferencesSettings::Factory) bind Settings.Factory::class
    }