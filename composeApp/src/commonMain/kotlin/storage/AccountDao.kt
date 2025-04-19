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
) {

    fun saveAccountInfo(accountDto: AccountDto) {
        accountSettings.keys
            .filter { it.startsWith(CURRENT_KEY) }
            .forEach { accountSettings[it] = false }
        accountSettings["${USERNAME_KEY}|${accountDto.userId}"] = accountDto.username
        accountDto.password?.let { accountSettings["${PASSWORD_KEY}|${accountDto.userId}"] = it.encodeBase64() }
        accountDto.iconUrl?.let { accountSettings["${ICON_URL_KEY}|${accountDto.userId}"] = it }
        accountSettings["${CURRENT_KEY}|${accountDto.userId}"] = true
        accountDto.authCookie?.let { accountSettings["${AUTH_KEY}|${accountDto.userId}"] = it }
        accountDto.twoFactorAuthCookie?.let { accountSettings["${TWO_FACTOR_AUTH_KEY}|${accountDto.userId}"] = it }
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
                    password = accountSettings.getStringOrNull("${PASSWORD_KEY}|${userId}")?.decodeBase64()?.utf8(),
                    iconUrl = accountSettings.getStringOrNull("${ICON_URL_KEY}|${userId}"),
                    current = accountSettings.getBoolean("${CURRENT_KEY}|${userId}", false),
                    authCookie = accountSettings.getStringOrNull("${AUTH_KEY}|${userId}"),
                    twoFactorAuthCookie = accountSettings.getStringOrNull("${TWO_FACTOR_AUTH_KEY}|${userId}")
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
    }

    fun logout(userId: String) {
        accountSettings.keys
            .firstOrNull { it.contains("${AUTH_KEY}|${userId}") }
            ?.let { accountSettings.remove(it) }
    }

    fun removeAccount(userId: String) {
        accountSettings.keys.asSequence()
            .filter { it.contains("|${userId}") }
            .forEach {
                accountSettings.remove(it)
            }
    }

}
