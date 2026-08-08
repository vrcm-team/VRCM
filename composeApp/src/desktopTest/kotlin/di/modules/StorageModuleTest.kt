package io.github.vrcmteam.vrcm.di.modules

import com.russhwolf.settings.MapSettings
import com.russhwolf.settings.Settings
import io.github.vrcmteam.vrcm.AppPlatform
import io.github.vrcmteam.vrcm.DesktopAppPlatform
import io.github.vrcmteam.vrcm.storage.AccountCacheManager
import io.github.vrcmteam.vrcm.storage.FriendActivityCacheStore
import org.koin.core.logger.EmptyLogger
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertNotNull

class StorageModuleTest {
    @Test
    fun accountCacheManagerCanBeResolvedFromProductionStorageModule() {
        val application = koinApplication {
            logger(EmptyLogger())
            modules(
                storageModule,
                module {
                    single<Settings.Factory> { InMemorySettingsFactory }
                    single<AppPlatform> { DesktopAppPlatform() }
                    single<FriendActivityCacheStore> { NoOpFriendActivityCacheStore }
                },
            )
        }

        try {
            assertNotNull(application.koin.get<AccountCacheManager>())
        } finally {
            application.close()
        }
    }
}

private data object InMemorySettingsFactory : Settings.Factory {
    override fun create(name: String?): Settings = MapSettings()
}

private data object NoOpFriendActivityCacheStore : FriendActivityCacheStore {
    override suspend fun clearAccount(ownerUserId: String) = Unit

    override suspend fun clearAll() = Unit
}
