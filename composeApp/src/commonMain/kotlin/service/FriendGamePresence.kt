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

internal data class FriendPresenceTransition(val userId: String, val displayName: String, val inGame: Boolean, val friend: FriendData?)

/** Emits only real in-game transitions for favorited friends; website-only presence is offline. */
internal class FavoriteFriendPresenceTracker {
    private var favorites: Set<String> = emptySet()
    private val knownPresence = mutableMapOf<String, Boolean>()
    private val names = mutableMapOf<String, String>()

    fun reset() { favorites = emptySet(); knownPresence.clear(); names.clear() }

    fun updateFavorites(ids: Set<String>, friends: Map<String, FriendData>) {
        favorites = ids
        knownPresence.keys.retainAll(ids)
        names.keys.retainAll(ids)
        ids.forEach { id -> friends[id]?.let { friend ->
            knownPresence.putIfAbsent(id, isInGameLocation(friend.location))
            friend.displayName.takeIf(String::isNotBlank)?.let { names[id] = it }
        } }
    }

    fun observe(friends: Map<String, FriendData>): List<FriendPresenceTransition> = buildList {
        favorites.forEach { id ->
            val friend = friends[id]
            if (friend == null) {
                if (knownPresence[id] == true) {
                    knownPresence[id] = false
                    add(FriendPresenceTransition(id, names[id] ?: id, false, null))
                }
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
