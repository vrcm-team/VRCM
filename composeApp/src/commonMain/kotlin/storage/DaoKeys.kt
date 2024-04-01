package io.github.vrcmteam.vrcm.storage

object DaoKeys{

    object Account {
        const val NAME = "account"

        /**
         * 用户名
         */
        const val USERNAME_KEY = "username"

        /**
         * 密码
         */
        const val PASSWORD_KEY = "password"
    }

    object Cookies {
        const val NAME = "cookies"
        /**
         * authCookie Token
         */
        const val AUTH_KEY = "auth"

        /**
         * 二步验证Token
         */
        const val TWO_FACTOR_AUTH_KEY = "twoFactorAuth"
    }

}
