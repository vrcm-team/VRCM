package io.github.vrcmteam.vrcm.storage

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class DesktopSecureStorageTest {
    @Test
    fun secretsRoundTripWithoutPlaintextOnDisk() {
        val directory = Files.createTempDirectory("vrcm-secrets-test").toFile()
        try {
            val storage = DesktopSecureStorage(directory, "account")

            storage.put("usr_a|password", "highly-sensitive-password")

            assertEquals("highly-sensitive-password", storage.get("usr_a|password"))
            assertFalse(
                directory.walkTopDown()
                    .filter(File::isFile)
                    .any { it.readBytes().decodeToString().contains("highly-sensitive-password") }
            )
        } finally {
            directory.deleteRecursively()
        }
    }
}
