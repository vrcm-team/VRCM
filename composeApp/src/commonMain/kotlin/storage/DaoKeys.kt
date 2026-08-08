package io.github.vrcmteam.vrcm.storage

import io.github.vrcmteam.vrcm.network.api.attributes.CookieNames

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
        const val AUTH_KEY = "${PREFIX}.${CookieNames.AUTH}"

        /**
         * 二步验证Token
         */
        const val TWO_FACTOR_AUTH_KEY = "${PREFIX}.${CookieNames.TWO_FACTOR_AUTH}"
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

    /**
     * 本地收藏分组设置
     */
    object FavoriteLocal {
        const val NAME = "${PREFIX}.favorite.local"
        const val WORLD_KEY = "${PREFIX}.favorite.local.world"
        const val FRIEND_KEY = "${PREFIX}.favorite.local.friend"
        const val AVATAR_KEY = "${PREFIX}.favorite.local.avatar"
    }

    object FriendNetwork {
        const val NAME = "${PREFIX}.friend.network"
        const val KEY_PREFIX = "${PREFIX}.friend.network.cache"
    }

    object UserProfileCache {
        const val NAME = "${PREFIX}.user.profile.cache"
        const val KEY_PREFIX = "${PREFIX}.user.profile.cache"
    }

    object WorldProfileCache {
        const val NAME = "${PREFIX}.world.profile.cache"
        const val KEY_PREFIX = "${PREFIX}.world.profile.cache"
    }

    object GroupProfileCache {
        const val NAME = "${PREFIX}.group.profile.cache"
        const val KEY_PREFIX = "${PREFIX}.group.profile.cache"
    }

    object FriendListCache {
        const val NAME = "${PREFIX}.friend.list.cache"
        const val KEY_PREFIX = "${PREFIX}.friend.list.cache"
    }

    object MeetupCard {
        const val NAME = "${PREFIX}.meetup.card"
        const val KEY_PREFIX = "${PREFIX}.meetup.card.config"
    }

    /** 会面身份卡装饰模板缓存的 Settings 命名空间。 */
    object MeetupDecoration {
        const val NAME = "${PREFIX}.meetup.decoration"
        const val KEY_PREFIX = "${PREFIX}.meetup.decoration.template"
    }
}
