package io.github.vrcmteam.vrcm.storage

import okio.ForwardingFileSystem
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopSecureStorageTest {
    @Test
    fun credentialsAndEncryptionKeyPersistWithoutPlaintext() {
        val fileSystem = FakeFileSystem()
        val directory = "/secure-storage".toPath()
        val storage = DesktopSecureStorage(fileSystem, directory, "account", isWindows = false)

        storage.put("usr_a|password", "highly-sensitive-password")

        val keyFile = directory / "account-secrets.key"
        val credentialsFile = directory / "account-secrets.properties"
        assertTrue(fileSystem.exists(keyFile))
        assertTrue(fileSystem.exists(credentialsFile))
        assertFalse(fileSystem.read(credentialsFile) { readUtf8() }.contains("highly-sensitive-password"))
        assertEquals(
            "highly-sensitive-password",
            DesktopSecureStorage(fileSystem, directory, "account", isWindows = false).get("usr_a|password"),
        )
        fileSystem.checkNoOpenFiles()
    }

    @Test
    fun credentialsPersistWhenAtomicMovesAreUnavailable() {
        val backingFileSystem = FakeFileSystem()
        val fileSystem = object : ForwardingFileSystem(backingFileSystem) {
            override fun atomicMove(source: Path, target: Path): Nothing {
                throw IOException("atomic moves unavailable")
            }
        }
        val directory = "/secure-storage".toPath()
        val storage = DesktopSecureStorage(fileSystem, directory, "account", isWindows = false)

        storage.put("usr_a|password", "highly-sensitive-password")

        assertEquals("highly-sensitive-password", storage.get("usr_a|password"))
        backingFileSystem.checkNoOpenFiles()
    }
}
