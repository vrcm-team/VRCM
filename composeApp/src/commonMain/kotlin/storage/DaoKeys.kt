package io.github.vrcmteam.vrcm.storage

object DaoKeys{

    const val PREFIX = "vrcm"

    /**
     * 是否是当前的
     */
    const val CURRENT_KEY = "${PREFIX}.current"

    object Account {


        const val NAME = "${PREFIX}.account"

        /**
         * 用户名
         */
        const val USERNAME_KEY = "${PREFIX}.username"

        /**
         * 密码
         */
        const val PASSWORD_KEY = "${PREFIX}.password"

        /**
         * 头像链接
         */
        const val ICON_URL_KEY = "${PREFIX}.iconUrl"

        /**
         * authCookie Token
         */
        const val AUTH_KEY = "${PREFIX}.auth"

        /**
         * 二步验证Token
         */
        const val TWO_FACTOR_AUTH_KEY = "${PREFIX}.twoFactorAuth"
    }

    object Cookies {
        const val NAME = "${PREFIX}.cookies"
        /**
         * authCookie Token
         */
        const val AUTH_KEY = "${PREFIX}.auth"

        /**
         * 二步验证Token
         */
        const val TWO_FACTOR_AUTH_KEY = "${PREFIX}.twoFactorAuth"
    }

    object Settings {

        const val NAME = "${PREFIX}.settings"

        /**
         * 是否是暗黑模式,为null则为更随系统
         */
        const val IS_DARK_THEME_KEY = "${PREFIX}.isDarkTheme"

        /**
         * 主题颜色名
         */
        const val THEME_COLOR_KEY = "${PREFIX}.themeColor"

        /**
         * 语言
         */
        const val LANGUAGE_TAG_KEY = "${PREFIX}.languageTag"

        /**
         * 记住的版本
         */
        const val REMEMBER_VERSION_KEY = "${PREFIX}.rememberVersion"

    }
}
