package io.github.vrcmteam.vrcm.storage

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.cinterop.COpaquePointer
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFDataGetBytePtr
import platform.CoreFoundation.CFDataGetLength
import platform.CoreFoundation.CFDataGetTypeID
import platform.CoreFoundation.CFDataRef
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionarySetValue
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFGetTypeID
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringCreateWithCString
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFStringEncodingUTF8
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
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
        val status = withQueryDictionary(
            key,
            kSecReturnData to kCFBooleanTrue,
            kSecMatchLimit to kSecMatchLimitOne,
        ) { query -> SecItemCopyMatching(query, result.ptr) }
        if (status != errSecSuccess) return@memScoped null
        val data = result.value ?: return@memScoped null
        try {
            data.toDataByteArray()?.decodeToString()
        } finally {
            CFRelease(data)
        }
    }

    override fun put(key: String, value: String) {
        val data = value.encodeToByteArray().toCFData()
        try {
            val updateStatus = withQueryDictionary(key) { query ->
                withDictionary(kSecValueData to data) { attributes ->
                    SecItemUpdate(query, attributes)
                }
            }
            val finalStatus = if (updateStatus == errSecItemNotFound) {
                withQueryDictionary(key, kSecValueData to data) { attributes ->
                    SecItemAdd(attributes, null)
                }
            } else updateStatus
            check(finalStatus == errSecSuccess) { "Failed to persist credential in Keychain ($finalStatus)" }
        } finally {
            CFRelease(data)
        }
    }

    override fun remove(key: String) {
        withQueryDictionary(key) { query -> SecItemDelete(query) }
    }

    override fun clear() {
        val serviceValue = service.toCFString()
        try {
            withDictionary(
                kSecClass to kSecClassGenericPassword,
                kSecAttrService to serviceValue,
            ) { query -> SecItemDelete(query) }
        } finally {
            CFRelease(serviceValue)
        }
    }

    private fun <T> withQueryDictionary(
        key: String,
        vararg extraEntries: Pair<COpaquePointer?, COpaquePointer?>,
        block: (CFDictionaryRef) -> T,
    ): T {
        val serviceValue = service.toCFString()
        return try {
            val accountValue = key.toCFString()
            try {
                withDictionary(
                    kSecClass to kSecClassGenericPassword,
                    kSecAttrService to serviceValue,
                    kSecAttrAccount to accountValue,
                    *extraEntries,
                    block = block,
                )
            } finally {
                CFRelease(accountValue)
            }
        } finally {
            CFRelease(serviceValue)
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun <T> withDictionary(
    vararg entries: Pair<COpaquePointer?, COpaquePointer?>,
    block: (CFDictionaryRef) -> T,
): T {
    val dictionary = CFDictionaryCreateMutable(
        allocator = null,
        capacity = entries.size.toLong(),
        keyCallBacks = kCFTypeDictionaryKeyCallBacks.ptr,
        valueCallBacks = kCFTypeDictionaryValueCallBacks.ptr,
    ) ?: error("Unable to create Keychain query")
    return try {
        entries.forEach { (key, value) -> CFDictionarySetValue(dictionary, key, value) }
        block(dictionary)
    } finally {
        CFRelease(dictionary)
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun String.toCFString(): platform.CoreFoundation.CFStringRef {
    require('\u0000' !in this) { "Keychain identifiers cannot contain NUL" }
    return CFStringCreateWithCString(null, this, kCFStringEncodingUTF8)
        ?: error("Unable to encode Keychain value")
}

@OptIn(ExperimentalForeignApi::class)
private fun kotlinx.cinterop.CPointer<out kotlinx.cinterop.CPointed>.toDataByteArray(): ByteArray? {
    if (CFGetTypeID(this) != CFDataGetTypeID()) return null
    val data: CFDataRef = reinterpret()
    val length = CFDataGetLength(data)
    return ByteArray(length.toInt()).also { output ->
        if (output.isNotEmpty()) {
            val bytes = checkNotNull(CFDataGetBytePtr(data))
            output.usePinned { pinned -> memcpy(pinned.addressOf(0), bytes, length.toULong()) }
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun ByteArray.toCFData(): CFDataRef = (if (isEmpty()) {
    CFDataCreate(null, null, 0)
} else usePinned { pinned ->
    CFDataCreate(null, pinned.addressOf(0).reinterpret(), size.toLong())
}) ?: error("Unable to encode Keychain value")
