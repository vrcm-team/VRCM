package io.github.vrcmteam.vrcm.storage

import com.russhwolf.settings.Settings
import com.russhwolf.settings.set
import io.github.vrcmteam.vrcm.service.data.AccountDto
import io.github.vrcmteam.vrcm.storage.DaoKeys.Account.AUTH_KEY
import io.github.vrcmteam.vrcm.storage.DaoKeys.Account.ICON_URL_KEY
import io.github.vrcmteam.vrcm.storage.DaoKeys.Account.PASSWORD_KEY
import io.github.vrcmteam.vrcm.storage.DaoKeys.Account.TWO_FACTOR_AUTH_KEY
import io.github.vrcmteam.vrcm.storage.DaoKeys.Account.USERNAME_KEY
import io.github.vrcmteam.vrcm.storage.DaoKeys.CURRENT_KEY
import io.ktor.util.*
import okio.ByteString.Companion.decodeBase64

class AccountDao(
    private val accountSettings: Settings,
    private val secureStorage: SecureStorage,
) {

    fun saveAccountInfo(accountDto: AccountDto) {
        accountSettings.keys
            .filter { it.startsWith(CURRENT_KEY) }
            .forEach { accountSettings[it] = false }
        accountSettings["${USERNAME_KEY}|${accountDto.userId}"] = accountDto.username
        accountDto.password?.let {
            secureStorage.put(secretKey(PASSWORD_KEY, accountDto.userId), it)
            accountSettings.remove("${PASSWORD_KEY}|${accountDto.userId}")
        }
        accountDto.iconUrl?.let { accountSettings["${ICON_URL_KEY}|${accountDto.userId}"] = it }
        accountSettings["${CURRENT_KEY}|${accountDto.userId}"] = true
        accountDto.authCookie?.let {
            secureStorage.put(secretKey(AUTH_KEY, accountDto.userId), it)
            accountSettings.remove("${AUTH_KEY}|${accountDto.userId}")
        }
        accountDto.twoFactorAuthCookie?.let {
            secureStorage.put(secretKey(TWO_FACTOR_AUTH_KEY, accountDto.userId), it)
            accountSettings.remove("${TWO_FACTOR_AUTH_KEY}|${accountDto.userId}")
        }
    }

    fun accountDtoList(): List<AccountDto> =
        accountSettings.keys
            .asSequence()
            .filter { it.startsWith("${DaoKeys.PREFIX}.") }
            .map { it.removePrefix("${DaoKeys.PREFIX}.") }
            .groupBy { it.substringAfter('|') }
            .filter { it.key.isNotEmpty() }
            .map { (userId, _) ->
                AccountDto(
                    userId = userId,
                    username = accountSettings.getStringOrNull("${USERNAME_KEY}|${userId}").orEmpty(),
                    password = readSecret(PASSWORD_KEY, userId) {
                        accountSettings.getStringOrNull("${PASSWORD_KEY}|${userId}")?.decodeBase64()?.utf8()
                    },
                    iconUrl = accountSettings.getStringOrNull("${ICON_URL_KEY}|${userId}"),
                    current = accountSettings.getBoolean("${CURRENT_KEY}|${userId}", false),
                    authCookie = readSecret(AUTH_KEY, userId) {
                        accountSettings.getStringOrNull("${AUTH_KEY}|${userId}")
                    },
                    twoFactorAuthCookie = readSecret(TWO_FACTOR_AUTH_KEY, userId) {
                        accountSettings.getStringOrNull("${TWO_FACTOR_AUTH_KEY}|${userId}")
                    }
                )
            }

    fun currentAccountDto(): AccountDto =
        currentAccountDtoOrNull() ?: AccountDto()

    fun currentAccountDtoOrNull(): AccountDto? =
        accountDtoList().firstOrNull {
            it.current
        }

    fun accountDtoByUserName(userName: String): AccountDto? =
        accountDtoList().firstOrNull {
            it.username.lowercase() == userName.lowercase()
        }

    fun clearAccount() {
        accountSettings.clear()
        secureStorage.clear()
    }

    fun logout(userId: String) {
        accountSettings.keys
            .firstOrNull { it == "${AUTH_KEY}|${userId}" }
            ?.let(accountSettings::remove)
        secureStorage.remove(secretKey(AUTH_KEY, userId))
    }

    fun removeAccount(userId: String) {
        accountSettings.keys.asSequence()
            .filter { it.contains("|${userId}") }
            .forEach {
                accountSettings.remove(it)
            }
        listOf(PASSWORD_KEY, AUTH_KEY, TWO_FACTOR_AUTH_KEY).forEach { type ->
            secureStorage.remove(secretKey(type, userId))
        }
    }

    private fun secretKey(type: String, userId: String) = "$userId|${type.substringAfterLast('.')}"

    private fun readSecret(type: String, userId: String, legacy: () -> String?): String? {
        secureStorage.get(secretKey(type, userId))?.let { return it }
        val oldValue = legacy() ?: return null
        secureStorage.put(secretKey(type, userId), oldValue)
        accountSettings.remove("$type|$userId")
        return oldValue
    }

}
