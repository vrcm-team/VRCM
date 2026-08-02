package io.github.vrcmteam.vrcm.di.modules

import java.nio.file.Files
import java.io.RandomAccessFile
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
            ).path,
        )
        assertEquals(
            "/Users/alice/Library/Application Support/VRCM",
            desktopSettingsDirectory(emptyMap(), "Mac OS X", "/Users/alice").path.replace('\\', '/'),
        )
        assertEquals(
            "/home/alice/.config/vrcm",
            desktopSettingsDirectory(emptyMap(), "Linux", "/home/alice").path.replace('\\', '/'),
        )
    }

    @Test
    fun legacyTemporarySettingsAreMovedOnce() {
        val directory = Files.createTempDirectory("vrcm-settings-migration").toFile()
        try {
            val legacy = directory.resolve("legacy.properties").apply { writeText("key=value") }
            val target = directory.resolve("config/settings.properties")

            migrateLegacySettingsFile(legacy, target)

            assertFalse(legacy.exists())
            assertEquals("key=value", target.readText())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun settingsRoundTripUsesUtf8() {
        val directory = Files.createTempDirectory("vrcm-settings-test").toFile()
        try {
            val file = directory.resolve("settings.properties")
            val expected = Properties().apply { setProperty("profile", "中文状态") }

            storeSettingsProperties(file, expected, "test")

            assertEquals("中文状态", loadSettingsProperties(file).getProperty("profile"))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun oversizedSettingsFileIsQuarantined() {
        val directory = Files.createTempDirectory("vrcm-settings-test").toFile()
        try {
            val file = directory.resolve("settings.properties")
            RandomAccessFile(file, "rw").use { it.setLength(MAX_SETTINGS_FILE_SIZE + 1) }

            val loaded = loadSettingsProperties(file)

            assertTrue(loaded.isEmpty())
            assertEquals(0, file.length())
            assertTrue(directory.listFiles().orEmpty().any { it.name.startsWith("settings.properties.corrupt-") })
            assertFalse(directory.listFiles().orEmpty().any { it.name.endsWith(".tmp") })
        } finally {
            directory.deleteRecursively()
        }
    }
}
