package io.github.vrcmteam.vrcm.network.api.users

import io.github.vrcmteam.vrcm.network.supports.VRCApiException

fun Throwable.isBoopCooldown(): Boolean = this is VRCApiException && code == 429
