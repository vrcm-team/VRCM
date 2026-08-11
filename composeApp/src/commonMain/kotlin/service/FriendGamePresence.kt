package io.github.vrcmteam.vrcm.service

import io.github.vrcmteam.vrcm.network.api.attributes.LocationType
import io.github.vrcmteam.vrcm.network.api.friends.date.FriendData

internal fun isInGameLocation(location: String?): Boolean {
    val value = location?.trim().orEmpty()
    return when {
        value.isEmpty() -> false
        value.equals(LocationType.Offline.value, true) -> false
        value.equals(LocationType.Web.value, true) -> false
        value.equals(LocationType.Private.value, true) -> true
        value.equals(LocationType.Traveling.value, true) -> true
        value.startsWith(LocationType.Instance.value, true) -> true
        else -> false
    }
}

internal data class FriendPresenceTransition(
    val userId: String,
    val displayName: String,
    val inGame: Boolean,
    val friend: FriendData?,
)

/** Emits real in-game transitions for every friend after a trusted full-list baseline. */
internal class FriendPresenceTracker {
    private var baselineEstablished = false
    private val knownPresence = mutableMapOf<String, Boolean>()
    private val names = mutableMapOf<String, String>()

    fun reset() {
        baselineEstablished = false
        knownPresence.clear()
        names.clear()
    }

    /**
     * Observes a full friend snapshot whose presence has passed FriendService's initial-refresh
     * gate. The first snapshot establishes a baseline; later snapshots emit only actual changes.
     */
    fun observe(friends: Map<String, FriendData>): List<FriendPresenceTransition> = buildList {
        friends.forEach { (id, friend) ->
            friend.displayName.takeIf(String::isNotBlank)?.let { names[id] = it }
        }
        if (!baselineEstablished) {
            baselineEstablished = true
            friends.forEach { (id, friend) ->
                knownPresence[id] = isInGameLocation(friend.location)
            }
            return@buildList
        }

        (knownPresence.keys + friends.keys).forEach { id ->
            val friend = friends[id]
            if (friend == null) {
                // Real offline updates retain the friend with an offline location. A missing entry
                // means the friendship was removed, so only discard its tracking baseline.
                knownPresence.remove(id)
                names.remove(id)
                return@forEach
            }
            val now = isInGameLocation(friend.location)
            val name = friend.displayName.takeIf(String::isNotBlank) ?: names[id] ?: id
            val previous = knownPresence.put(id, now)
            names[id] = name
            if (previous != null && previous != now) add(FriendPresenceTransition(id, name, now, friend))
        }
    }
}
