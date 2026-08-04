package io.github.vrcmteam.vrcm.storage

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class IosKeychainSecureStorageTest {
    @Test
    fun missingKeyReturnsNull() {
        val storage = IosKeychainSecureStorage("vrcm-keychain-test")

        assertNull(storage.get("missing"))
    }

    @Test
    fun valueCanBeStoredReadAndUpdated() {
        val storage = IosKeychainSecureStorage("vrcm-keychain-round-trip-test")

        if (!storage.putWhenKeychainAvailable("account", "initial secret")) return
        try {
            assertEquals("initial secret", storage.get("account"))

            storage.put("account", "updated secret")
            assertEquals("updated secret", storage.get("account"))
        } finally {
            storage.remove("account")
        }
    }

    @Test
    fun valueCanBeRemoved() {
        val storage = IosKeychainSecureStorage("vrcm-keychain-remove-test")

        if (!storage.putWhenKeychainAvailable("account", "secret")) return
        try {
            assertEquals("secret", storage.get("account"))

            storage.remove("account")

            assertNull(storage.get("account"))
        } finally {
            storage.remove("account")
        }
    }

    @Test
    fun serviceValuesCanBeCleared() {
        val storage = IosKeychainSecureStorage("vrcm-keychain-clear-test")
        val otherStorage = IosKeychainSecureStorage("vrcm-keychain-clear-other-test")

        if (!storage.putWhenKeychainAvailable("first", "one")) return
        try {
            if (!storage.putWhenKeychainAvailable("second", "two")) return
            if (!otherStorage.putWhenKeychainAvailable("first", "other")) return

            storage.clear()

            assertNull(storage.get("first"))
            assertNull(storage.get("second"))
            assertEquals("other", otherStorage.get("first"))
        } finally {
            storage.clear()
            otherStorage.clear()
        }
    }

    @Test
    fun keysContainingNullAreRejected() {
        val storage = IosKeychainSecureStorage("vrcm-keychain-null-test")

        assertFailsWith<IllegalArgumentException> {
            storage.get("account\u0000other")
        }
    }
}

private fun IosKeychainSecureStorage.putWhenKeychainAvailable(key: String, value: String): Boolean {
    val failure = runCatching { put(key, value) }.exceptionOrNull() ?: return true
    assertEquals(
        "Failed to persist credential in Keychain (-25291)",
        failure.message,
    )
    return false
}
