package io.github.vrcmteam.vrcm.di.modules

import coil3.PlatformContext
import com.russhwolf.settings.PropertiesSettings
import com.russhwolf.settings.Settings
import io.github.vrcmteam.vrcm.AppPlatform
import io.github.vrcmteam.vrcm.DesktopAppPlatform
import io.github.vrcmteam.vrcm.presentation.screens.gallery.editor.DesktopPlatformImageCodec
import io.github.vrcmteam.vrcm.presentation.screens.gallery.editor.PlatformImageCodec
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
                // TODO：保存到非临时文件夹避免误删
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
}
