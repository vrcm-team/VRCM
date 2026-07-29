package io.github.vrcmteam.vrcm.network.api.attributes

fun IUser.lastSeenAt(): String? =
    lastActivity?.takeIf(String::isNotBlank) ?: lastLogin?.takeIf(String::isNotBlank)
