package io.github.vrcmteam.vrcm.di.modules

import coil3.PlatformContext
import com.russhwolf.settings.PropertiesSettings
import com.russhwolf.settings.Settings
import io.github.vrcmteam.vrcm.AppPlatform
import io.github.vrcmteam.vrcm.DesktopAppPlatform
import io.github.vrcmteam.vrcm.presentation.screens.gallery.editor.DesktopPlatformImageCodec
import io.github.vrcmteam.vrcm.presentation.screens.gallery.editor.PlatformImageCodec
import io.github.vrcmteam.vrcm.presentation.notifications.FriendOnlineNotifier
import io.github.vrcmteam.vrcm.presentation.notifications.NoOpFriendOnlineNotifier
import io.github.vrcmteam.vrcm.presentation.screens.meetup.animation.AnimatedWebpDecoder
import io.github.vrcmteam.vrcm.presentation.screens.meetup.animation.SkiaAnimatedWebpDecoder
import io.github.vrcmteam.vrcm.storage.DaoKeys
import io.github.vrcmteam.vrcm.storage.DesktopSecureStorage
import io.github.vrcmteam.vrcm.storage.SecureStorage
import io.github.vrcmteam.vrcm.storage.moveReplacing
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import org.koin.core.logger.Logger
import org.koin.core.logger.PrintLogger
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module
import java.util.*

internal const val MAX_SETTINGS_FILE_SIZE = 64L * 1024L * 1024L

internal fun desktopSettingsDirectory(
    environment: Map<String, String> = System.getenv(),
    osName: String = System.getProperty("os.name"),
    userHome: String = System.getProperty("user.home"),
): Path {
    val directory = when {
        osName.startsWith("Windows", ignoreCase = true) -> {
            val applicationData = environment["APPDATA"]
                ?: "${userHome.trimEnd('/', '\\')}\\AppData\\Roaming"
            "${applicationData.trimEnd('/', '\\')}\\VRCM"
        }
        osName.startsWith("Mac", ignoreCase = true) ->
            "${userHome.trimEnd('/', '\\')}/Library/Application Support/VRCM"
        else -> {
            val configHome = environment["XDG_CONFIG_HOME"]
                ?: "${userHome.trimEnd('/', '\\')}/.config"
            "${configHome.trimEnd('/', '\\')}/vrcm"
        }
    }
    return directory.toPath()
}

internal fun migrateLegacySettingsFile(fileSystem: FileSystem, legacyFile: Path, targetFile: Path) {
    if (fileSystem.exists(targetFile) || fileSystem.metadataOrNull(legacyFile)?.isRegularFile != true) return
    fileSystem.createDirectories(requireNotNull(targetFile.parent))
    fileSystem.moveReplacing(legacyFile, targetFile)
}

internal fun loadSettingsProperties(
    fileSystem: FileSystem,
    file: Path,
    maxFileSize: Long = MAX_SETTINGS_FILE_SIZE,
): Properties {
    if ((fileSystem.metadataOrNull(file)?.size ?: 0L) > maxFileSize) {
        val quarantine = requireNotNull(file.parent) / "${file.name}.corrupt-${System.currentTimeMillis()}"
        fileSystem.moveReplacing(file, quarantine)
    }
    fileSystem.createDirectories(requireNotNull(file.parent))
    if (!fileSystem.exists(file)) fileSystem.write(file) {}
    return Properties().apply {
        fileSystem.read(file) { load(inputStream().reader(Charsets.UTF_8)) }
    }
}

internal fun storeSettingsProperties(
    fileSystem: FileSystem,
    file: Path,
    properties: Properties,
    comment: String,
) {
    val parent = requireNotNull(file.parent)
    fileSystem.createDirectories(parent)
    val temporary = parent / "${file.name}.${UUID.randomUUID()}.tmp"
    try {
        fileSystem.write(temporary, mustCreate = true) {
            outputStream().writer(Charsets.UTF_8).also { writer ->
                properties.store(writer, comment)
                writer.flush()
            }
        }
        fileSystem.moveReplacing(temporary, file)
    } finally {
        fileSystem.delete(temporary, mustExist = false)
    }
}

actual val platformModule: Module = module {
    singleOf<Logger>(::PrintLogger)
    singleOf<PlatformContext>(PlatformContext::INSTANCE)
    single<Settings.Factory> {
        object : Settings.Factory {
            override fun create(name: String?): Settings {
                val fileSystem = FileSystem.SYSTEM
                val directory = desktopSettingsDirectory()
                fileSystem.createDirectories(directory)
                val file = directory / "$name-settings.properties"
                val legacyFile = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "$name-settings.properties"
                migrateLegacySettingsFile(fileSystem, legacyFile, file)
                val delegate = loadSettingsProperties(fileSystem, file)
                return PropertiesSettings(delegate){
                    storeSettingsProperties(fileSystem, file, it, "$name-settings")
                }
            }
        }
    }
    singleOf<AppPlatform>(::DesktopAppPlatform)
    singleOf(::DesktopPlatformImageCodec) bind PlatformImageCodec::class
    singleOf(::NoOpFriendOnlineNotifier) bind FriendOnlineNotifier::class
    singleOf(::SkiaAnimatedWebpDecoder) bind AnimatedWebpDecoder::class
    single<SecureStorage> {
        DesktopSecureStorage(FileSystem.SYSTEM, desktopSettingsDirectory(), DaoKeys.Account.NAME)
    }
}
