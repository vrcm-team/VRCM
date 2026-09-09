package io.github.vrcmteam.vrcm.network.api.invite

import io.github.vrcmteam.vrcm.network.api.auth.data.Presence
import io.github.vrcmteam.vrcm.network.api.auth.data.presenceLocation

/** Both invite APIs require a full world:instance location in their instanceId field. */
internal fun Presence.inviteLocationOrNull(): String? {
    val location = if (instance == TRAVELING_LOCATION) {
        presenceLocation(travelingToWorld, travelingToInstance)
    } else {
        presenceLocation(world, instance)
    }
    return location.takeIf(::isValidInviteLocation)
}

internal fun requireValidInviteLocation(location: String) {
    require(isValidInviteLocation(location)) {
        "instanceId must be a full active world:instance location"
    }
}

private fun isValidInviteLocation(location: String): Boolean {
    val separatorIndex = location.indexOf(':')
    if (separatorIndex <= WORLD_ID_PREFIX.length || location.any(Char::isWhitespace)) {
        return false
    }
    val worldId = location.substring(0, separatorIndex)
    val instanceId = location.substring(separatorIndex + 1)
    return worldId.startsWith(WORLD_ID_PREFIX) &&
        instanceId.isNotEmpty() &&
        instanceId !in NON_INSTANCE_LOCATIONS &&
        ':' !in instanceId
}

private const val WORLD_ID_PREFIX = "wrld_"
private const val TRAVELING_LOCATION = "traveling"
private val NON_INSTANCE_LOCATIONS = setOf("offline", "private", TRAVELING_LOCATION)
