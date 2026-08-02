package io.github.vrcmteam.vrcm.storage

import com.sun.jna.platform.win32.Crypt32Util
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.SecureRandom
import java.util.Base64
import java.util.Properties
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class DesktopSecureStorage(directory: File, name: String) : SecureStorage {
    private val secretsFile = directory.resolve("$name-secrets.properties")
    private val keyFile = directory.resolve("$name-secrets.key")
    private val isWindows = System.getProperty("os.name").startsWith("Windows", ignoreCase = true)
    private val lock = Any()

    init { directory.mkdirs() }

    override fun get(key: String): String? = synchronized(lock) {
        load()[key]?.toString()?.let(::decrypt)
    }

    override fun put(key: String, value: String) = synchronized(lock) {
        store(load().apply { setProperty(key, encrypt(value)) })
    }

    override fun remove(key: String) = synchronized(lock) {
        val properties = load()
        if (properties.remove(key) != null) store(properties)
    }

    override fun clear() = synchronized(lock) {
        if (secretsFile.exists()) secretsFile.delete()
    }

    private fun encrypt(value: String): String = if (isWindows) {
        "dpapi:${encode(Crypt32Util.cryptProtectData(value.toByteArray(StandardCharsets.UTF_8)))}"
    } else {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        "aes:${encode(cipher.iv)}:${encode(cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8)))}"
    }

    private fun decrypt(value: String): String? = runCatching {
        when {
            value.startsWith("dpapi:") ->
                String(Crypt32Util.cryptUnprotectData(decode(value.substringAfter(':'))), StandardCharsets.UTF_8)
            value.startsWith("aes:") -> {
                val parts = value.split(':', limit = 3)
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, decode(parts[1])))
                String(cipher.doFinal(decode(parts[2])), StandardCharsets.UTF_8)
            }
            else -> null
        }
    }.getOrNull()

    private fun secretKey(): SecretKey {
        if (!keyFile.exists()) {
            val key = KeyGenerator.getInstance("AES").apply { init(256, SecureRandom()) }.generateKey().encoded
            Files.write(keyFile.toPath(), key)
            keyFile.setReadable(false, false)
            keyFile.setWritable(false, false)
            keyFile.setReadable(true, true)
            keyFile.setWritable(true, true)
        }
        return SecretKeySpec(Files.readAllBytes(keyFile.toPath()), "AES")
    }

    private fun load() = Properties().apply {
        if (secretsFile.isFile) Files.newBufferedReader(secretsFile.toPath(), StandardCharsets.UTF_8).use(::load)
    }

    private fun store(properties: Properties) {
        val temporary = Files.createTempFile(secretsFile.parentFile.toPath(), secretsFile.name, ".tmp")
        try {
            Files.newBufferedWriter(temporary, StandardCharsets.UTF_8).use { properties.store(it, null) }
            Files.move(temporary, secretsFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    private fun encode(bytes: ByteArray) = Base64.getEncoder().encodeToString(bytes)
    private fun decode(value: String) = Base64.getDecoder().decode(value)

    private companion object { const val TRANSFORMATION = "AES/GCM/NoPadding" }
}
