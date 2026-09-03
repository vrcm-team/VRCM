package io.github.vrcmteam.vrcm.network.api.invite

import io.github.vrcmteam.vrcm.network.api.auth.data.Presence

internal fun Presence.inviteLocationOrNull(): String? {
    val instanceId = if (instance == TRAVELING_LOCATION) {
        travelingToInstance
    } else {
        instance
    }
    return instanceId.takeIf(::isValidInviteLocation)
}

internal fun requireValidInviteLocation(location: String) {
    require(isValidInviteLocation(location)) {
        "instanceId must be a pure active instance ID"
    }
}

private fun isValidInviteLocation(location: String): Boolean {
    return location.isNotBlank() &&
        location !in NON_INSTANCE_LOCATIONS &&
        !location.startsWith(WORLD_ID_PREFIX) &&
        ':' !in location
}

private const val WORLD_ID_PREFIX = "wrld_"
private const val TRAVELING_LOCATION = "traveling"
private val NON_INSTANCE_LOCATIONS = setOf("offline", "private", TRAVELING_LOCATION)
