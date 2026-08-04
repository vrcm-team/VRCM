package io.github.vrcmteam.vrcm.di.modules

import okio.ForwardingFileSystem
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlatformSettingsTest {
    @Test
    fun settingsDirectoryUsesPlatformApplicationDataLocation() {
        assertEquals(
            "C:\\Users\\alice\\AppData\\Roaming\\VRCM",
            desktopSettingsDirectory(
                environment = mapOf("APPDATA" to "C:\\Users\\alice\\AppData\\Roaming"),
                osName = "Windows 11",
                userHome = "C:\\Users\\alice",
            ).toString(),
        )
        assertEquals(
            "/Users/alice/Library/Application Support/VRCM",
            desktopSettingsDirectory(emptyMap(), "Mac OS X", "/Users/alice").toString(),
        )
        assertEquals(
            "/home/alice/.config/vrcm",
            desktopSettingsDirectory(emptyMap(), "Linux", "/home/alice").toString(),
        )
    }

    @Test
    fun legacyTemporarySettingsAreMovedOnce() {
        val fileSystem = FakeFileSystem()
        val legacy = "/temporary/legacy.properties".toPath()
        val target = "/config/settings.properties".toPath()
        fileSystem.createDirectories(legacy.parent!!)
        fileSystem.write(legacy) { writeUtf8("key=value") }

        migrateLegacySettingsFile(fileSystem, legacy, target)

        assertFalse(fileSystem.exists(legacy))
        assertEquals("key=value", fileSystem.read(target) { readUtf8() })
        fileSystem.checkNoOpenFiles()
    }

    @Test
    fun legacyTemporarySettingsFallBackWhenAtomicMovesAreUnavailable() {
        val backingFileSystem = FakeFileSystem()
        val fileSystem = withoutAtomicMoves(backingFileSystem)
        val legacy = "/temporary/legacy.properties".toPath()
        val target = "/config/settings.properties".toPath()
        backingFileSystem.createDirectories(legacy.parent!!)
        backingFileSystem.write(legacy) { writeUtf8("key=value") }

        migrateLegacySettingsFile(fileSystem, legacy, target)

        assertFalse(backingFileSystem.exists(legacy))
        assertEquals("key=value", backingFileSystem.read(target) { readUtf8() })
        backingFileSystem.checkNoOpenFiles()
    }

    @Test
    fun settingsRoundTripUsesUtf8() {
        val fileSystem = FakeFileSystem()
        val file = "/settings/settings.properties".toPath()
        val expected = Properties().apply { setProperty("profile", "中文状态") }

        storeSettingsProperties(fileSystem, file, expected, "test")

        assertEquals("中文状态", loadSettingsProperties(fileSystem, file).getProperty("profile"))
        fileSystem.checkNoOpenFiles()
    }

    @Test
    fun settingsRoundTripFallsBackWhenAtomicMovesAreUnavailable() {
        val backingFileSystem = FakeFileSystem()
        val fileSystem = withoutAtomicMoves(backingFileSystem)
        val file = "/settings/settings.properties".toPath()
        val expected = Properties().apply { setProperty("profile", "中文状态") }

        storeSettingsProperties(fileSystem, file, expected, "test")

        assertEquals("中文状态", loadSettingsProperties(fileSystem, file).getProperty("profile"))
        backingFileSystem.checkNoOpenFiles()
    }

    @Test
    fun oversizedSettingsFileIsQuarantined() {
        val fileSystem = FakeFileSystem()
        val directory = "/settings".toPath()
        val file = directory / "settings.properties"
        fileSystem.createDirectories(directory)
        fileSystem.write(file) { writeUtf8("oversized") }

        val loaded = loadSettingsProperties(fileSystem, file, maxFileSize = 4L)

        assertTrue(loaded.isEmpty())
        assertEquals(0L, fileSystem.metadata(file).size)
        assertTrue(fileSystem.list(directory).any { it.name.startsWith("settings.properties.corrupt-") })
        assertFalse(fileSystem.list(directory).any { it.name.endsWith(".tmp") })
        fileSystem.checkNoOpenFiles()
    }
}

private fun withoutAtomicMoves(fileSystem: FakeFileSystem) = object : ForwardingFileSystem(fileSystem) {
    override fun atomicMove(source: Path, target: Path): Nothing {
        throw IOException("atomic moves unavailable")
    }
}
