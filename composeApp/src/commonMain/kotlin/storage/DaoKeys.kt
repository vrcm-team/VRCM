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

    object Settings {

        const val NAME = "settings"

        /**
         * 是否是暗黑模式,为null则为更随系统
         */
        const val IS_DARK_THEME_KEY = "isDarkTheme"

        /**
         * 主题颜色名
         */
        const val THEME_COLOR_KEY = "themeColor"

        /**
         * 语言
         */
        const val LANGUAGE_TAG_KEY = "languageTag"

        /**
         * 记住的版本
         */
        const val REMEMBER_VERSION_KEY = "rememberVersion"

    }
}
