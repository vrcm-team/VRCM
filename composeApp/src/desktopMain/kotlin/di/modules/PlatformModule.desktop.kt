package io.github.vrcmteam.vrcm.di.modules

import coil3.PlatformContext
import com.russhwolf.settings.PropertiesSettings
import com.russhwolf.settings.Settings
import io.github.vrcmteam.vrcm.AppPlatform
import io.github.vrcmteam.vrcm.DesktopAppPlatform
import io.github.vrcmteam.vrcm.presentation.screens.gallery.editor.DesktopPlatformImageCodec
import io.github.vrcmteam.vrcm.presentation.screens.gallery.editor.PlatformImageCodec
import io.github.vrcmteam.vrcm.storage.DaoKeys
import io.github.vrcmteam.vrcm.storage.DesktopSecureStorage
import io.github.vrcmteam.vrcm.storage.SecureStorage
import okio.FileSystem
import org.koin.core.logger.Logger
import org.koin.core.logger.PrintLogger
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.*

internal const val MAX_SETTINGS_FILE_SIZE = 64L * 1024L * 1024L

internal fun desktopSettingsDirectory(
    environment: Map<String, String> = System.getenv(),
    osName: String = System.getProperty("os.name"),
    userHome: String = System.getProperty("user.home"),
): File = when {
    osName.startsWith("Windows", ignoreCase = true) ->
        File(environment["APPDATA"] ?: File(userHome, "AppData/Roaming").path, "VRCM")
    osName.startsWith("Mac", ignoreCase = true) ->
        File(userHome, "Library/Application Support/VRCM")
    else -> File(environment["XDG_CONFIG_HOME"] ?: File(userHome, ".config").path, "vrcm")
}

internal fun migrateLegacySettingsFile(legacyFile: File, targetFile: File) {
    if (targetFile.exists() || !legacyFile.isFile) return
    targetFile.parentFile.mkdirs()
    try {
        Files.move(legacyFile.toPath(), targetFile.toPath(), StandardCopyOption.ATOMIC_MOVE)
    } catch (_: AtomicMoveNotSupportedException) {
        Files.move(legacyFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }
}

internal fun loadSettingsProperties(file: File): Properties {
    if (file.exists() && file.length() > MAX_SETTINGS_FILE_SIZE) {
        val quarantine = file.parentFile.resolve("${file.name}.corrupt-${System.currentTimeMillis()}")
        Files.move(file.toPath(), quarantine.toPath())
    }
    if (!file.exists()) file.createNewFile()
    return Properties().apply {
        Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8).use(::load)
    }
}

internal fun storeSettingsProperties(file: File, properties: Properties, comment: String) {
    val temporary = Files.createTempFile(file.parentFile.toPath(), "${file.name}.", ".tmp")
    try {
        Files.newBufferedWriter(temporary, StandardCharsets.UTF_8).use { properties.store(it, comment) }
        try {
            Files.move(
                temporary,
                file.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temporary, file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    } finally {
        Files.deleteIfExists(temporary)
    }
}

actual val platformModule: Module = module {
    singleOf<Logger>(::PrintLogger)
    singleOf<PlatformContext>(PlatformContext::INSTANCE)
    single<Settings.Factory> {
        object : Settings.Factory {
            override fun create(name: String?): Settings {
                val file = FileSystem.SYSTEM_TEMPORARY_DIRECTORY.resolve("$name-settings.properties").toFile()
                if (!file.exists()) {
                    file.createNewFile()
                }
                val delegate = loadSettingsProperties(file)
                return PropertiesSettings(delegate){
                    storeSettingsProperties(file, it, "$name-settings")
                }
            }
        }
    }
    singleOf<AppPlatform>(::DesktopAppPlatform)
    singleOf(::DesktopPlatformImageCodec) bind PlatformImageCodec::class
    single<SecureStorage> { DesktopSecureStorage(desktopSettingsDirectory(), DaoKeys.Account.NAME) }
}
