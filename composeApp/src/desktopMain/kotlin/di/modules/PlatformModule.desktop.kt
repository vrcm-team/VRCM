package io.github.vrcmteam.vrcm.di.modules

import coil3.PlatformContext
import com.russhwolf.settings.PropertiesSettings
import com.russhwolf.settings.Settings
import okio.FileSystem
import org.koin.core.logger.Logger
import org.koin.core.logger.PrintLogger
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import java.io.FileInputStream
import java.io.FileWriter
import java.util.*

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
                val delegate = Properties().apply { FileInputStream(file).use { this.load(it)}}
                return PropertiesSettings(delegate){
                    it.store(FileWriter(file),"$name-settings")
                }
            }
        }
    }
}