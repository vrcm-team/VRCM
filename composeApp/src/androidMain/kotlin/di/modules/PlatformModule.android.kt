package io.github.vrcmteam.vrcm.di.modules

import com.russhwolf.settings.Settings
import com.russhwolf.settings.SharedPreferencesSettings
import io.github.vrcmteam.vrcm.AndroidAppPlatform
import io.github.vrcmteam.vrcm.AppPlatform
import io.github.vrcmteam.vrcm.presentation.screens.gallery.editor.AndroidPlatformImageCodec
import io.github.vrcmteam.vrcm.presentation.screens.gallery.editor.PlatformImageCodec
import io.github.vrcmteam.vrcm.presentation.screens.meetup.animation.AndroidAnimatedWebpDecoder
import io.github.vrcmteam.vrcm.presentation.screens.meetup.animation.AnimatedWebpDecoder
import io.github.vrcmteam.vrcm.storage.AndroidSecureStorage
import io.github.vrcmteam.vrcm.storage.DaoKeys
import io.github.vrcmteam.vrcm.storage.SecureStorage
import org.koin.android.logger.AndroidLogger
import org.koin.core.logger.Logger
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

actual val platformModule: Module = module {
    singleOf<Logger>(::AndroidLogger)
    singleOf(SharedPreferencesSettings::Factory) bind Settings.Factory::class
    singleOf(::AndroidAppPlatform) bind AppPlatform::class
    singleOf(::AndroidPlatformImageCodec) bind PlatformImageCodec::class
    singleOf(::AndroidAnimatedWebpDecoder) bind AnimatedWebpDecoder::class
    single<SecureStorage> { AndroidSecureStorage(get(), DaoKeys.Account.NAME) }
}
