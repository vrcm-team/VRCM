package io.github.vrcmteam.vrcm.storage

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class AndroidSecureStorage(
    context: Context,
    name: String,
) : SecureStorage {
    private val preferences = context.getSharedPreferences("$name.secure", Context.MODE_PRIVATE)
    private val keyAlias = "io.github.vrcmteam.vrcm.$name"

    override fun get(key: String): String? = preferences.getString(key, null)?.let(::decrypt)

    override fun put(key: String, value: String) {
        check(preferences.edit().putString(key, encrypt(value)).commit()) {
            "Failed to persist encrypted credential"
        }
    }

    override fun remove(key: String) {
        preferences.edit().remove(key).commit()
    }

    override fun clear() {
        preferences.edit().clear().commit()
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        return "${encode(cipher.iv)}:${encode(cipher.doFinal(value.encodeToByteArray()))}"
    }

    private fun decrypt(value: String): String? = runCatching {
        val (iv, ciphertext) = value.split(':', limit = 2)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, decode(iv)))
        cipher.doFinal(decode(ciphertext)).decodeToString()
    }.getOrNull()

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(keyAlias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(
                    keyAlias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build()
            )
            generateKey()
        }
    }

    private fun encode(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)
    private fun decode(value: String) = Base64.decode(value, Base64.NO_WRAP)

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
