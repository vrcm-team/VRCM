package io.github.vrcmteam.vrcm.storage

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.Foundation.NSData
import platform.Foundation.dataWithBytes
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.SecItemUpdate
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData
import platform.posix.memcpy

@OptIn(ExperimentalForeignApi::class)
class IosKeychainSecureStorage(
    private val service: String,
) : SecureStorage {
    override fun get(key: String): String? = memScoped {
        val result = alloc<CFTypeRefVar>()
        val status = SecItemCopyMatching(
            query(key).plus(
                mapOf(
                    kSecReturnData to kCFBooleanTrue,
                    kSecMatchLimit to kSecMatchLimitOne,
                )
            ).asDictionary(),
            result.ptr,
        )
        if (status != errSecSuccess) return@memScoped null
        (result.value as? NSData)?.toByteArray()?.decodeToString()
    }

    override fun put(key: String, value: String) {
        val data = value.encodeToByteArray().toNSData()
        val attributes = mapOf(kSecValueData to data).asDictionary()
        val updateStatus = SecItemUpdate(query(key).asDictionary(), attributes)
        val finalStatus = if (updateStatus == errSecItemNotFound) {
            SecItemAdd(query(key).plus(mapOf<Any?, Any?>(kSecValueData to data)).asDictionary(), null)
        } else updateStatus
        check(finalStatus == errSecSuccess) { "Failed to persist credential in Keychain ($finalStatus)" }
    }

    override fun remove(key: String) {
        SecItemDelete(query(key).asDictionary())
    }

    override fun clear() {
        SecItemDelete(
            mapOf(
                kSecClass to kSecClassGenericPassword,
                kSecAttrService to service,
            ).asDictionary()
        )
    }

    private fun query(key: String): Map<Any?, Any?> = mapOf(
        kSecClass to kSecClassGenericPassword,
        kSecAttrService to service,
        kSecAttrAccount to key,
    )
}

@OptIn(ExperimentalForeignApi::class)
@Suppress("UNCHECKED_CAST")
private fun Map<*, *>.asDictionary(): CFDictionaryRef = this as CFDictionaryRef

@OptIn(ExperimentalForeignApi::class)
private fun ByteArray.toNSData(): NSData = if (isEmpty()) {
    NSData.dataWithBytes(bytes = null, length = 0u)
} else usePinned { pinned ->
    NSData.dataWithBytes(bytes = pinned.addressOf(0), length = size.toULong())
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray = ByteArray(length.toInt()).also { output ->
    output.usePinned { pinned -> memcpy(pinned.addressOf(0), bytes, length) }
}
