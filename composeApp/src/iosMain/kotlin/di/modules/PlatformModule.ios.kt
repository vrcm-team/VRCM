package io.github.vrcmteam.vrcm.di.modules

import coil3.PlatformContext
import com.russhwolf.settings.NSUserDefaultsSettings
import com.russhwolf.settings.Settings
import io.github.vrcmteam.vrcm.AppPlatform
import io.github.vrcmteam.vrcm.IosAppPlatform
import io.github.vrcmteam.vrcm.presentation.screens.gallery.editor.IosPlatformImageCodec
import io.github.vrcmteam.vrcm.presentation.screens.gallery.editor.PlatformImageCodec
import io.github.vrcmteam.vrcm.presentation.notifications.FriendOnlineNotifier
import io.github.vrcmteam.vrcm.presentation.notifications.NoOpFriendOnlineNotifier
import io.github.vrcmteam.vrcm.storage.DaoKeys
import io.github.vrcmteam.vrcm.storage.IosKeychainSecureStorage
import io.github.vrcmteam.vrcm.storage.SecureStorage
import org.koin.core.logger.Logger
import org.koin.core.logger.PrintLogger
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module


actual val platformModule: Module = module {
    singleOf<Logger>(::PrintLogger)
    singleOf<PlatformContext>(PlatformContext::INSTANCE)
    singleOf<Settings.Factory>(NSUserDefaultsSettings::Factory)
    singleOf<AppPlatform>(::IosAppPlatform)
    singleOf(::IosPlatformImageCodec) bind PlatformImageCodec::class
    singleOf(::NoOpFriendOnlineNotifier) bind FriendOnlineNotifier::class
    single<SecureStorage> { IosKeychainSecureStorage(DaoKeys.Account.NAME) }
}
