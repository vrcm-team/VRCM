package io.github.vrcmteam.vrcm.presentation.screens.auth.data

enum class AuthCardPage {
    Loading,
    Login,
    EmailCode,
    TFACode,
    Authed,
    TTFACode
}

private const val ONE_TIME_CODE_LENGTH = 6
private const val RECOVERY_CODE_LENGTH = 8

/**
 * 把输入整理成当前验证方式要提交的验证码，格式不完整时返回 null。
 *
 * 邮箱 / 验证器验证码是 6 位数字；[AuthCardPage.TFACode] 对应 VRChat 的一次性恢复码：8 位字母数字，
 * 接口要求 `xxxx-xxxx` 形式，用户照着恢复码清单输入时带不带连字符、空格都接受。
 */
fun AuthCardPage.normalizeVerifyCode(input: String): String? = when (this) {
    AuthCardPage.EmailCode, AuthCardPage.TTFACode ->
        input.takeIf { code -> code.length == ONE_TIME_CODE_LENGTH && code.all { it in '0'..'9' } }

    AuthCardPage.TFACode -> input
        .filterNot { it.isWhitespace() || it == '-' }
        .takeIf { code -> code.length == RECOVERY_CODE_LENGTH && code.all { it.isAsciiLetterOrDigit() } }
        ?.let { code -> "${code.take(4)}-${code.drop(4)}" }

    AuthCardPage.Loading, AuthCardPage.Login, AuthCardPage.Authed -> null
}

private fun Char.isAsciiLetterOrDigit(): Boolean = this in '0'..'9' || this in 'a'..'z' || this in 'A'..'Z'
