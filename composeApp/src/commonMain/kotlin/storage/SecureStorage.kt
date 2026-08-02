package io.github.vrcmteam.vrcm.storage

interface SecureStorage {
    fun get(key: String): String?
    fun put(key: String, value: String)
    fun remove(key: String)
    fun clear()
}

internal class InMemorySecureStorage : SecureStorage {
    private val values = mutableMapOf<String, String>()

    override fun get(key: String): String? = values[key]
    override fun put(key: String, value: String) { values[key] = value }
    override fun remove(key: String) { values.remove(key) }
    override fun clear() { values.clear() }
}
