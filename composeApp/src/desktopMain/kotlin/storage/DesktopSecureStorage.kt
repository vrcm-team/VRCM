package io.github.vrcmteam.vrcm.storage

import com.sun.jna.platform.win32.Crypt32Util
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toOkioPath
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.Base64
import java.util.Properties
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class DesktopSecureStorage(
    private val fileSystem: FileSystem,
    directory: Path,
    name: String,
    private val isWindows: Boolean = System.getProperty("os.name").startsWith("Windows", ignoreCase = true),
) : SecureStorage {
    constructor(directory: File, name: String) : this(FileSystem.SYSTEM, directory.toOkioPath(), name)

    private val secretsFile = directory / "$name-secrets.properties"
    private val keyFile = directory / "$name-secrets.key"
    private val lock = Any()

    init { fileSystem.createDirectories(directory) }

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
        fileSystem.delete(secretsFile, mustExist = false)
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
        if (!fileSystem.exists(keyFile)) {
            val key = KeyGenerator.getInstance("AES").apply { init(256, SecureRandom()) }.generateKey().encoded
            fileSystem.write(keyFile, mustCreate = true) { write(key) }
            if (fileSystem === FileSystem.SYSTEM) restrictKeyFilePermissions(keyFile.toFile())
        }
        return SecretKeySpec(fileSystem.read(keyFile) { readByteArray() }, "AES")
    }

    private fun load() = Properties().apply {
        if (fileSystem.metadataOrNull(secretsFile)?.isRegularFile == true) {
            fileSystem.read(secretsFile) { load(inputStream().reader(StandardCharsets.UTF_8)) }
        }
    }

    private fun store(properties: Properties) {
        val temporary = requireNotNull(secretsFile.parent) / "${secretsFile.name}.${UUID.randomUUID()}.tmp"
        try {
            fileSystem.write(temporary, mustCreate = true) {
                outputStream().writer(StandardCharsets.UTF_8).also { writer ->
                    properties.store(writer, null)
                    writer.flush()
                }
            }
            fileSystem.moveReplacing(temporary, secretsFile)
        } finally {
            fileSystem.delete(temporary, mustExist = false)
        }
    }

    private fun encode(bytes: ByteArray) = Base64.getEncoder().encodeToString(bytes)
    private fun decode(value: String) = Base64.getDecoder().decode(value)

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"

        fun restrictKeyFilePermissions(file: File) {
            file.setReadable(false, false)
            file.setWritable(false, false)
            file.setReadable(true, true)
            file.setWritable(true, true)
        }
    }
}
