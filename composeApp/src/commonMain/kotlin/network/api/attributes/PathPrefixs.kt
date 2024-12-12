package io.github.vrcmteam.vrcm.network.api.attributes

import io.ktor.http.*

internal const val AUTH_API_PREFIX = "auth"

internal const val INSTANCES_API_SUFFIX = "instances"

internal const val USERS_API_PREFIX = "users"

internal const val USER_API_PREFIX = "user"

internal const val FRIENDS_API_PREFIX = "friends"

internal const val GROUPS_API_PREFIX = "groups"

internal const val FILES_API_PREFIX = "files"

internal const val FILE_API_PREFIX = "file"

internal const val WORLDS_API_PREFIX = "worlds"

internal const val INVITE_API_PREFIX = "invite"

internal const val NOTIFICATIONS_API_PREFIX = "notifications"

internal const val FAVORITE_API_PREFIX = "favorites"

internal const val VRC_API_URL = "https://api.vrchat.cloud/api/1/"

internal const val VRC_WSS_URL = "wss://vrchat.com/?"

internal  val VRC_API_HOST = Url(VRC_API_URL).host